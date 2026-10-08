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
}
