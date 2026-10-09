package dev.b3monitor.admin;

import dev.b3monitor.domain.analytics.AnalyticsSnapshotValidator;
import dev.b3monitor.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-17 items 3/4/5/7/8/9 — adversarial hardening of the analytics import boundary:
 * parser/input bounds, canonical-checksum golden cases, time/as-of invariants, current-context selection,
 * import side-effect non-interference, and the synthetic≠runtime-VERIFIED guarantee.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsImportHardeningTest {

    static final Instant NOW = Instant.parse("2026-10-08T17:00:00Z");

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired AnalyticsSnapshotValidator validator;
    @Autowired AnalyticsImportService imports;
    @Autowired AnalyticsSnapshotRepository snapshots;
    @Autowired AnalyticsContextRepository analyticsRows;
    @Autowired AnalyticsContextMetricRepository analyticsMetrics;
    @Autowired AdminQueryService query;
    @Autowired OutboxRepository outbox;
    @Autowired RuleDefinitionRepository ruleDefs;
    @Autowired QuoteObservationRepository observations;

    private byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    /** A snapshot JSON with a given declared checksum and record key order. */
    private String snap(String id, String marketAsOf, boolean indicatorsFirst, String checksum) {
        String ind = "\"indicators\":{\"sma20\":50.10,\"sma20Readiness\":\"READY\"}";
        String rest = "\"ticker\":\"WEGE3\",\"asOf\":\"2026-10-06\",\"status\":\"OK\"";
        String record = indicatorsFirst ? "{" + ind + "," + rest + "}" : "{" + rest + "," + ind + "}";
        return "{\"schemaVersion\":\"b3-monitor.analytics-snapshot/1\",\"snapshotId\":\"" + id + "\","
                + "\"producer\":\"projecao-carteira\",\"producerVersion\":\"0.0.1\","
                + "\"generatedAt\":\"2026-10-08T12:00:00Z\",\"marketAsOf\":\"" + marketAsOf + "\","
                + "\"timezone\":\"America/Sao_Paulo\",\"sourceId\":\"COTAHIST-RAW\",\"checksum\":\"" + checksum + "\","
                + "\"records\":[" + record + "]}";
    }

    private String canonicalFor(String json) {
        var p = imports.preview(bytes(json.replaceFirst("\"checksum\":\"[^\"]*\"", "\"checksum\":\"" + "0".repeat(64) + "\"")));
        if (p.wouldImport()) return p.canonicalChecksum();
        for (String e : p.errors()) {
            int i = e.indexOf("canonical ");
            if (i >= 0) return e.substring(i + "canonical ".length()).trim().substring(0, 64);
        }
        return "0".repeat(64);
    }

    // ---- item 4: canonical checksum golden cases ----

    @Test
    void checksumIsStableAcrossJsonKeyOrder() {
        String a = snap("c-order", "2026-10-06", true, "0".repeat(64));
        String b = snap("c-order", "2026-10-06", false, "0".repeat(64));
        assertEquals(canonicalFor(a), canonicalFor(b), "reordered JSON keys ⇒ same canonical checksum");
    }

    @Test
    void checksumStableAcrossWhitespace() {
        String a = snap("c-ws", "2026-10-06", true, "0".repeat(64));
        String b = a.replace(",", ",\n   ").replace("{", "{\n ");
        assertEquals(canonicalFor(a), canonicalFor(b), "insignificant whitespace ⇒ same checksum");
    }

    @Test
    void checksumChangesWhenSemanticValueChanges() {
        String a = snap("c-val", "2026-10-06", true, "0".repeat(64));
        String b = a.replace("50.10", "51.10");
        assertNotEquals(canonicalFor(a), canonicalFor(b), "changed value ⇒ different checksum");
    }

    @Test
    void checksumChangesWhenAsOfChanges() {
        String a = snap("c-asof", "2026-10-06", true, "0".repeat(64));
        String b = snap("c-asof", "2026-10-05", true, "0".repeat(64)).replace("\"asOf\":\"2026-10-06\"", "\"asOf\":\"2026-10-05\"");
        assertNotEquals(canonicalFor(a), canonicalFor(b), "changed asOf ⇒ different checksum");
    }

    // ---- item 3: parser / input bounds ----

    @Test
    void duplicateJsonKeyRejected() {
        String dup = snap("dupkey", "2026-10-06", true, "0".repeat(64))
                .replace("\"producer\":\"projecao-carteira\"", "\"producer\":\"a\",\"producer\":\"b\"");
        var v = validator.validate(bytes(dup));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.toLowerCase().contains("duplicate")), v.errors().toString());
    }

    @Test
    void unknownTopLevelFieldRejected() {
        String x = snap("unk", "2026-10-06", true, "0".repeat(64)).replaceFirst("\\}$", ",\"surprise\":1}");
        var v = validator.validate(bytes(x));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("unknown top-level field")));
    }

    @Test
    void duplicateTickerRejected() {
        String two = snap("duptick", "2026-10-06", true, "0".repeat(64))
                .replace("\"records\":[", "\"records\":[{\"ticker\":\"WEGE3\",\"asOf\":\"2026-10-06\",\"status\":\"OK\"},");
        var v = validator.validate(bytes(two));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("duplicated in snapshot")));
    }

    @Test
    void nonFiniteNumberRejected() {
        // a literal NaN token must fail the strict parse (ALLOW_NON_NUMERIC_NUMBERS is off)
        String nan = snap("nan", "2026-10-06", true, "0".repeat(64)).replace("50.10", "NaN");
        var v = validator.validate(bytes(nan));
        assertFalse(v.valid());
    }

    @Test
    void oversizeNumericScaleRejected() {
        String big = snap("scale", "2026-10-06", true, "0".repeat(64)).replace("50.10", "50.1234567890123456");
        var v = validator.validate(bytes(big));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("scale") || e.contains("precision")));
    }

    @Test
    void dangerousStringContentRejected() {
        String bad = snap("danger", "2026-10-06", true, "0".repeat(64)).replace("\"OK\"", "\"<script>x\"");
        var v = validator.validate(bytes(bad));
        assertFalse(v.valid());
    }

    // ---- item 5: time / as-of invariants ----

    @Test
    void generatedAtInFutureRejected() {
        String f = snap("gfut", "2026-10-06", true, "0".repeat(64))
                .replace("\"generatedAt\":\"2026-10-08T12:00:00Z\"", "\"generatedAt\":\"2027-01-01T00:00:00Z\"");
        var v = validator.validate(bytes(f));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("generatedAt") && e.contains("future")));
    }

    @Test
    void recordAsOfAfterMarketAsOfRejected() {
        // marketAsOf is the upper bound; a record asOf after it is impossible
        String f = snap("afut", "2026-10-05", true, "0".repeat(64));   // record asOf is 2026-10-06 > 2026-10-05
        var v = validator.validate(bytes(f));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("after the snapshot marketAsOf")));
    }

    @Test
    void importedAtComesFromClockNotPayload() {
        String base = snap("clk", "2026-10-06", true, "0".repeat(64));
        byte[] body = bytes(base.replaceFirst("\"checksum\":\"[^\"]*\"", "\"checksum\":\"" + canonicalFor(base) + "\""));
        var p = imports.preview(body);
        imports.commit(body, p.token());
        var saved = snapshots.findBySnapshotId("clk").orElseThrow();
        assertEquals(NOW, saved.getImportedAt(), "importedAt is the injected server clock, never from the payload");
    }

    // ---- item 7: current-context selection by asOf, not import order ----

    @Test
    void olderAsOfImportedLaterDoesNotBecomeCurrent() {
        importSnap("sel-new", "2026-10-07");   // newer asOf, imported FIRST
        importSnap("sel-old", "2026-10-06");   // older asOf, imported SECOND
        var rows = query.analyticsContext().rows();
        var wege = rows.stream().filter(r -> r.ticker().equals("WEGE3")).findFirst().orElseThrow();
        assertTrue(wege.present());
        assertEquals("sel-new", wege.snapshotId(), "newer-asOf snapshot stays current despite a later older import");
        assertEquals("2026-10-07", wege.analyticsAsOf());
    }

    // ---- item 8: synthetic never becomes runtime VERIFIED ----

    @Test
    void syntheticImportNeverMarksRuntimeVerified() {
        importSnap("syn", "2026-10-06");
        var consumer = query.readiness().components().stream()
                .filter(c -> c.component().equals("analytics_consumer")).findFirst().orElseThrow();
        assertNotEquals("VERIFIED", consumer.runtimeStatus(), "a synthetic import must NEVER be runtime VERIFIED");
        assertEquals("NOT_VERIFIED", consumer.runtimeStatus());
        assertEquals("CONSUMER_VERIFIED_SYNTHETIC", snapshots.findBySnapshotId("syn").orElseThrow().getStatus());
    }

    // ---- item 9: import side-effect non-interference ----

    @Test
    void importDoesNotTouchRulesOutboxOrAssets() {
        long rulesBefore = ruleDefs.count();
        long outboxBefore = outbox.count();
        long obsBefore = observations.count();
        importSnap("noeffect", "2026-10-06");
        assertEquals(rulesBefore, ruleDefs.count(), "import creates/changes no rule");
        assertEquals(outboxBefore, outbox.count(), "import creates no alert_outbox row");
        assertEquals(obsBefore, observations.count(), "import creates no quote observation");
    }

    private void importSnap(String id, String marketAsOf) {
        String base = snap(id, marketAsOf, true, "0".repeat(64));
        byte[] body = bytes(base.replaceFirst("\"checksum\":\"[^\"]*\"", "\"checksum\":\"" + canonicalFor(base) + "\""));
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), "fixture should preview cleanly: " + p.errors());
        var r = imports.commit(body, p.token());
        assertTrue(r.disposition().equals("IMPORTED") || r.disposition().equals("IDEMPOTENT_NOOP"), r.disposition());
    }

    // ---- cycle-18 item A: full-document (envelope) binding ----

    /** A valid body whose canonical checksum is already correct, ready to preview+commit. */
    private byte[] fixed(String json) {
        return bytes(json.replaceFirst("\"checksum\":\"[^\"]*\"", "\"checksum\":\"" + canonicalFor(json) + "\""));
    }

    @Test
    void envelopeOnlyChangeAfterPreviewIsRejected() {
        // Preview the original; then commit a body that differs ONLY in an envelope field (producer) with
        // the SAME records checksum. The full-document digest changed, so the token no longer binds.
        String base = snap("env-bind", "2026-10-06", true, "0".repeat(64));
        byte[] previewed = fixed(base);
        var p = imports.preview(previewed);
        assertTrue(p.wouldImport());
        String envChangedJson = base.replace("\"producer\":\"projecao-carteira\"", "\"producer\":\"someone-else\"");
        byte[] envChanged = fixed(envChangedJson);
        // records checksum is unchanged, but the document digest differs → token DOCUMENT_MISMATCH
        var r = imports.commit(envChanged, p.token());
        assertTrue(r.disposition().startsWith("REJECTED_TOKEN_"), "envelope-only change must reject: " + r.disposition());
        assertTrue(snapshots.findBySnapshotId("env-bind").isEmpty());
    }

    @Test
    void whitespaceChangeAfterPreviewIsRejected() {
        // exact-byte binding: reformatting whitespace after preview invalidates the token
        String base = snap("ws-bind", "2026-10-06", true, "0".repeat(64));
        byte[] previewed = fixed(base);
        var p = imports.preview(previewed);
        assertTrue(p.wouldImport());
        byte[] reformatted = bytes(new String(previewed, StandardCharsets.UTF_8).replace(",", ", "));
        var r = imports.commit(reformatted, p.token());
        assertTrue(r.disposition().startsWith("REJECTED_TOKEN_"), "whitespace change must reject (exact-byte binding)");
    }

    // ---- cycle-18 item B: document-identity idempotency / conflict ----

    @Test
    void sameSnapshotEnvelopeChangeIsConflictNotNoop() {
        String base = snap("doc-id", "2026-10-06", true, "0".repeat(64));
        byte[] body = fixed(base);
        assertEquals("IMPORTED", imports.commit(body, imports.preview(body).token()).disposition());
        // same snapshotId + same RECORDS but changed envelope (sourceId) → different document → CONFLICT
        String envJson = snap("doc-id", "2026-10-06", true, "0".repeat(64)).replace("COTAHIST-RAW", "COTAHIST-ADJ");
        byte[] env = fixed(envJson);
        var r = imports.commit(env, imports.preview(env).token());
        assertEquals("REJECTED_CONFLICT", r.disposition(), "same id + changed envelope is a conflict, not a NO_OP");
    }

    @Test
    void exactSameDocumentReimportIsNoop() {
        String base = snap("doc-noop", "2026-10-06", true, "0".repeat(64));
        byte[] body = fixed(base);
        assertEquals("IMPORTED", imports.commit(body, imports.preview(body).token()).disposition());
        assertEquals("IDEMPOTENT_NOOP", imports.commit(body, imports.preview(body).token()).disposition());
    }

    // ---- cycle-18 item F: numeric round-trip matching the validator (24,12) ----

    @Test
    void maxScalePrecisionRoundTripsExactly() {
        // scale 12, precision within 24 — must be accepted AND persisted without rounding
        String val = "123.123456789012";   // scale 12
        String j = snap("num-rt", "2026-10-06", true, "0".repeat(64)).replace("50.10", val);
        byte[] body = fixed(j);
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), "scale-12 value must be accepted: " + p.errors());
        imports.commit(body, p.token());
        Long sid = snapshots.findBySnapshotId("num-rt").orElseThrow().getId();
        var row = analyticsRows.findBySnapshot_IdAndTicker(sid, "WEGE3").get(0);
        assertEquals(0, new java.math.BigDecimal(val).compareTo(row.getSma20()), "scale-12 value round-trips exactly");
    }

    @Test
    void scaleBeyond12RejectedBeforeDb() {
        String j = snap("num-bad", "2026-10-06", true, "0".repeat(64)).replace("50.10", "1.1234567890123"); // scale 13
        var v = validator.validate(bytes(j));
        assertFalse(v.valid(), "scale-13 must be rejected by the validator, never silently rounded at the DB");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("scale")));
    }

    // ---- cycle-18 item G: lossless FIN-02 context metrics ----

    @Test
    void contextMetricRoundTripsAllFieldsLosslessly() {
        String ctx = "\"context\":[{\"name\":\"graham_fair_value\",\"value\":60.5,\"units\":\"BRL\","
                + "\"readiness\":\"PARTIAL\",\"quality\":\"audited-snapshot\"}]";
        String j = snap("ctx-loss", "2026-10-06", true, "0".repeat(64))
                .replace("\"status\":\"OK\"", ctx + ",\"status\":\"OK\"");
        byte[] body = fixed(j);
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), "" + p.errors());
        imports.commit(body, p.token());
        Long sid = snapshots.findBySnapshotId("ctx-loss").orElseThrow().getId();
        var row = analyticsRows.findBySnapshot_IdAndTicker(sid, "WEGE3").get(0);
        var metrics = analyticsMetrics.findByContext_Id(row.getId());
        assertEquals(1, metrics.size());
        var m = metrics.get(0);
        assertEquals("graham_fair_value", m.getName());
        assertEquals(0, new java.math.BigDecimal("60.5").compareTo(m.getValue()));
        assertEquals("BRL", m.getUnits());
        assertEquals("PARTIAL", m.getReadiness());
        assertEquals("audited-snapshot", m.getQuality(), "context metric quality round-trips (was lost in cycle-16)");
    }

    @Test
    void duplicateContextMetricNameRejected() {
        String ctx = "\"context\":[{\"name\":\"g\",\"value\":1,\"readiness\":\"READY\"},"
                + "{\"name\":\"g\",\"value\":2,\"readiness\":\"READY\"}]";
        String j = snap("ctx-dup", "2026-10-06", true, "0".repeat(64))
                .replace("\"status\":\"OK\"", ctx + ",\"status\":\"OK\"");
        var v = validator.validate(bytes(j));
        assertFalse(v.valid());
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("duplicated in context")));
    }

    // ---- cycle-18 item I: checksum fidelity (quality fields now included) ----

    @Test
    void recordQualityChangeChangesChecksum() {
        String a = snap("ck-rq", "2026-10-06", true, "0".repeat(64));
        String b = a.replace("\"status\":\"OK\"", "\"quality\":\"X\",\"status\":\"OK\"");
        String c = a.replace("\"status\":\"OK\"", "\"quality\":\"Y\",\"status\":\"OK\"");
        assertNotEquals(canonicalFor(b), canonicalFor(c), "record.quality is in the semantic checksum");
    }

    @Test
    void contextQualityChangeChangesChecksum() {
        String base = snap("ck-cq", "2026-10-06", true, "0".repeat(64));
        String b = base.replace("\"status\":\"OK\"",
                "\"context\":[{\"name\":\"g\",\"value\":1,\"readiness\":\"READY\",\"quality\":\"A\"}],\"status\":\"OK\"");
        String c = base.replace("\"status\":\"OK\"",
                "\"context\":[{\"name\":\"g\",\"value\":1,\"readiness\":\"READY\",\"quality\":\"B\"}],\"status\":\"OK\"");
        assertNotEquals(canonicalFor(b), canonicalFor(c), "context metric quality is in the semantic checksum");
    }

    // ---- cycle-18 item D: PER-TICKER current selection across partial snapshots ----

    @Test
    void perTickerPartialSnapshotKeepsEachTickersLatestValidContext() {
        // older snapshot (marketAsOf 2026-10-05) has BPAC11; newer PARTIAL snapshot (2026-10-07) has WEGE3 only.
        String older = twoTicker("sel-older", "2026-10-05", "BPAC11", "WEGE3");
        importFixed(older);
        String newerPartial = oneTicker("sel-newer", "2026-10-07", "WEGE3");
        importFixed(newerPartial);
        var rows = query.analyticsContext().rows();
        var wege = rows.stream().filter(r -> r.ticker().equals("WEGE3")).findFirst().orElseThrow();
        var bpac = rows.stream().filter(r -> r.ticker().equals("BPAC11")).findFirst().orElseThrow();
        assertEquals("sel-newer", wege.snapshotId(), "WEGE3 uses the newer snapshot");
        assertEquals("2026-10-07", wege.analyticsAsOf());
        assertEquals("sel-older", bpac.snapshotId(), "BPAC11 keeps its older valid data (newer snapshot omits it)");
        assertEquals("2026-10-05", bpac.analyticsAsOf());
    }

    @Test
    void analyticsHasNoInventedStaleSla() {
        importSnap("no-sla", "2026-10-06");
        var wege = query.analyticsContext().rows().stream()
                .filter(r -> r.ticker().equals("WEGE3")).findFirst().orElseThrow();
        assertEquals("POLICY_NOT_CONFIGURED", wege.stalePolicy(), "no invented 2-day stale threshold");
        assertNotNull(wege.analyticsAgeSeconds(), "age is still visible");
    }

    // helpers for multi-ticker snapshots
    private String oneTicker(String id, String marketAsOf, String ticker) {
        String rec = "{\"ticker\":\"" + ticker + "\",\"asOf\":\"" + marketAsOf + "\","
                + "\"indicators\":{\"sma20\":50.10,\"sma20Readiness\":\"READY\"},\"status\":\"OK\"}";
        return envelope(id, marketAsOf, rec);
    }
    private String twoTicker(String id, String marketAsOf, String t1, String t2) {
        String r1 = "{\"ticker\":\"" + t1 + "\",\"asOf\":\"" + marketAsOf + "\",\"indicators\":{\"sma20\":10.0,\"sma20Readiness\":\"READY\"},\"status\":\"OK\"}";
        String r2 = "{\"ticker\":\"" + t2 + "\",\"asOf\":\"" + marketAsOf + "\",\"indicators\":{\"sma20\":20.0,\"sma20Readiness\":\"READY\"},\"status\":\"OK\"}";
        return envelope(id, marketAsOf, r1 + "," + r2);
    }
    private String envelope(String id, String marketAsOf, String records) {
        return "{\"schemaVersion\":\"b3-monitor.analytics-snapshot/1\",\"snapshotId\":\"" + id + "\","
                + "\"producer\":\"projecao-carteira\",\"producerVersion\":\"0.0.1\","
                + "\"generatedAt\":\"2026-10-08T12:00:00Z\",\"marketAsOf\":\"" + marketAsOf + "\","
                + "\"timezone\":\"America/Sao_Paulo\",\"sourceId\":\"COTAHIST-RAW\",\"checksum\":\"" + "0".repeat(64) + "\","
                + "\"records\":[" + records + "]}";
    }
    private void importFixed(String json) {
        byte[] body = fixed(json);
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), "fixture must preview cleanly: " + p.errors());
        var r = imports.commit(body, p.token());
        assertTrue(r.disposition().equals("IMPORTED") || r.disposition().equals("IDEMPOTENT_NOOP"), r.disposition());
    }

    // ======================== CYCLE-19 ========================

    private String withValue(String id, String value) {
        String rec = "{\"ticker\":\"WEGE3\",\"asOf\":\"2026-10-06\","
                + "\"indicators\":{\"sma20\":" + value + ",\"sma20Readiness\":\"READY\"},\"status\":\"OK\"}";
        return envelope(id, "2026-10-06", rec);
    }

    // ---- P1-C: numeric boundary must match NUMERIC(24,12) exactly ----

    @Test
    void maxValidNumericRoundTripsExactly() {
        // 12 integer digits + 12 fractional digits = the exact NUMERIC(24,12) ceiling.
        String max = "999999999999.999999999999";
        String json = withValue("num-max", max);
        byte[] body = fixed(json);
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), "max NUMERIC(24,12) value must validate: " + p.errors());
        assertEquals("IMPORTED", imports.commit(body, p.token()).disposition());
        var row = analyticsRows.findBySnapshot_IdAndTicker(
                snapshots.findBySnapshotId("num-max").orElseThrow().getId(), "WEGE3").get(0);
        assertEquals(0, new java.math.BigDecimal(max).compareTo(row.getSma20()), "exact round-trip, no rounding");
    }

    @Test
    void maxValidNegativeNumericRoundTripsExactly() {
        String max = "-999999999999.999999999999";
        String json = withValue("num-min", max);
        byte[] body = fixed(json);
        var p = imports.preview(body);
        assertTrue(p.wouldImport(), p.errors().toString());
        assertEquals("IMPORTED", imports.commit(body, p.token()).disposition());
        var row = analyticsRows.findBySnapshot_IdAndTicker(
                snapshots.findBySnapshotId("num-min").orElseThrow().getId(), "WEGE3").get(0);
        assertEquals(0, new java.math.BigDecimal(max).compareTo(row.getSma20()));
    }

    @Test
    void positive1E12Rejected() {
        // 1E12 needs 13 integer digits — it does NOT fit NUMERIC(24,12). Must reject BEFORE persistence.
        var v = validator.validate(bytes(withValue("e12", "1000000000000")));
        assertFalse(v.valid(), "1E12 (13 integer digits) must be rejected");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("magnitude out of range")), v.errors().toString());
        assertTrue(snapshots.findBySnapshotId("e12").isEmpty(), "nothing persisted");
    }

    @Test
    void scientific1E12NotationRejected() {
        // the exponential literal 1E12 (BigDecimal scale -12) must also be rejected, not silently accepted.
        var v = validator.validate(bytes(withValue("e12sci", "1E12")));
        assertFalse(v.valid(), "1E12 scientific form must be rejected");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("magnitude out of range")), v.errors().toString());
    }

    @Test
    void negative1E12Rejected() {
        var v = validator.validate(bytes(withValue("e12neg", "-1000000000000")));
        assertFalse(v.valid(), "-1E12 must be rejected");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("magnitude out of range")));
    }

    @Test
    void scaleBeyond12Rejected() {
        var v = validator.validate(bytes(withValue("scale13", "1.0000000000001")));   // 13 fractional digits
        assertFalse(v.valid(), "scale 13 must be rejected (NUMERIC(24,12) stores 12)");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("scale")), v.errors().toString());
    }

    @Test
    void contextMetricValueBoundsEnforced() {
        // the SAME numeric fit-check applies to structured context-metric values, not only typed indicators.
        String rec = "{\"ticker\":\"WEGE3\",\"asOf\":\"2026-10-06\","
                + "\"indicators\":{\"sma20\":10.0,\"sma20Readiness\":\"READY\"},"
                + "\"context\":[{\"name\":\"graham\",\"value\":1000000000000,\"readiness\":\"PARTIAL\"}],\"status\":\"OK\"}";
        var v = validator.validate(bytes(envelope("ctx-num", "2026-10-06", rec)));
        assertFalse(v.valid(), "a context-metric value of 1E12 must be rejected too");
        assertTrue(v.errors().stream().anyMatch(e -> e.contains("magnitude out of range")), v.errors().toString());
    }

    // ---- P2: legacy (NULL document_digest) identity is fail-closed ----

    @Test
    void legacyNullDigestReimportIsConflictNotNoop() {
        // Seed a V14-style row directly: a durable snapshot whose document_digest is NULL (never stored),
        // whose records checksum MATCHES the one a re-submission would compute. Records-checksum equality is
        // NOT proof of full-document identity, so the re-import must FAIL-CLOSED as a legacy conflict —
        // never a silent NO_OP, and never a fabricated digest on the old row.
        String json = withValue("legacy-1", "50.10");
        String canonical = canonicalFor(json);
        var legacy = new AnalyticsSnapshotEntity("legacy-1", "b3-monitor.analytics-snapshot/1",
                "projecao-carteira", "0.0.1", NOW, java.time.LocalDate.of(2026, 10, 6),
                "America/Sao_Paulo", "COTAHIST-RAW", canonical, /*documentDigest*/ null,
                1, NOW, "admin", "CONSUMER_VERIFIED_SYNTHETIC");
        snapshots.saveAndFlush(legacy);

        byte[] body = fixed(json);                              // identical records → same records checksum
        var r = imports.commit(body, imports.preview(body).token());
        assertEquals("REJECTED_CONFLICT_LEGACY_NO_DIGEST", r.disposition(),
                "a re-import against a NULL-digest legacy row cannot prove identity → fail-closed conflict");
        // the legacy row is untouched (no back-fill, no overwrite)
        assertNull(snapshots.findBySnapshotId("legacy-1").orElseThrow().getDocumentDigest(),
                "legacy document_digest stays NULL — no fabricated digest");
    }

    @Test
    void v15DigestRowStillGivesExactNoopAndConflict() {
        // A row imported THROUGH the service carries a real document digest, so the normal exact
        // NO_OP / CONFLICT semantics still hold (the legacy policy does not weaken V15+ behavior).
        String json = withValue("v15-id", "50.10");
        byte[] body = fixed(json);
        assertEquals("IMPORTED", imports.commit(body, imports.preview(body).token()).disposition());
        assertEquals("IDEMPOTENT_NOOP", imports.commit(body, imports.preview(body).token()).disposition(),
                "exact same document re-import is a NO_OP");
        String changed = withValue("v15-id", "51.10");
        byte[] cb = fixed(changed);
        assertEquals("REJECTED_CONFLICT", imports.commit(cb, imports.preview(cb).token()).disposition(),
                "same id + different document is a CONFLICT");
    }

    // ---- P1-A: concurrent-commit LOSER disposition (deterministic; the true race is the Postgres IT) ----

    @Test
    void loserOfSameDocumentRaceGetsIdempotentNoop() {
        // Simulate the winner having already committed: a second commit of the EXACT same document resolves
        // to IDEMPOTENT_NOOP via the sound pre-check / post-race re-read path — never a 500, never a dup row.
        String json = withValue("race-same", "50.10");
        byte[] body = fixed(json);
        assertEquals("IMPORTED", imports.commit(body, imports.preview(body).token()).disposition());
        var r = imports.commit(body, imports.preview(body).token());
        assertEquals("IDEMPOTENT_NOOP", r.disposition());
        assertEquals(1, snapshots.findAll().stream()
                .filter(s -> s.getSnapshotId().equals("race-same")).count(), "exactly one durable row");
    }

    @Test
    void loserOfDifferentDocumentRaceGetsConflict() {
        String json = withValue("race-diff", "50.10");
        byte[] body = fixed(json);
        assertEquals("IMPORTED", imports.commit(body, imports.preview(body).token()).disposition());
        String other = withValue("race-diff", "77.77");
        byte[] ob = fixed(other);
        assertEquals("REJECTED_CONFLICT", imports.commit(ob, imports.preview(ob).token()).disposition(),
                "a different document for the same id is a conflict, not a NO_OP");
    }
}
