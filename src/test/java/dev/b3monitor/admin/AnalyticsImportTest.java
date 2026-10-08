package dev.b3monitor.admin;

import dev.b3monitor.persistence.AnalyticsSnapshotRepository;
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
 * Cycle-16 FIN-01/FIN-03/FIN-04 — the analytics-snapshot consumer importer. Full {@code @SpringBootTest}
 * on H2 (so Flyway V14 + the real validator/repository/audit run). A fixed clock pins "future" as-of
 * checks. Proves preview→commit, every fail-closed rejection, idempotency, conflict, the preview/commit
 * token binding, the private-portfolio key refusal (FIN-04), and that a synthetic import is never marked
 * runtime-VERIFIED (status stays CONSUMER_VERIFIED_SYNTHETIC).
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsImportTest {

    static final Instant NOW = Instant.parse("2026-10-08T17:00:00Z");

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired AnalyticsImportService imports;
    @Autowired AnalyticsSnapshotRepository snapshots;
    @Autowired dev.b3monitor.admin.AdminAuditRepository auditRepo;

    /** A valid schema-v1 snapshot. {@code checksum} is a placeholder; preview recomputes the canonical one. */
    private String validSnapshot(String id, String ticker, String marketAsOf) {
        return """
            {
              "schemaVersion": "b3-monitor.analytics-snapshot/1",
              "snapshotId": "%s",
              "producer": "projecao-carteira",
              "producerVersion": "0.0.1-synthetic",
              "generatedAt": "2026-10-08T12:00:00Z",
              "marketAsOf": "%s",
              "timezone": "America/Sao_Paulo",
              "sourceId": "COTAHIST-RAW",
              "checksum": "%s",
              "records": [
                { "ticker": "%s", "asOf": "%s",
                  "indicators": { "sma20": 50.10, "sma20Readiness": "READY",
                                  "rsi14": 55.00, "rsi14Readiness": "READY" },
                  "context": [ { "name": "fii_pvp", "value": 1.02, "units": "ratio", "readiness": "PARTIAL" } ],
                  "quality": "RAW close-only", "status": "OK" }
              ]
            }""".formatted(id, marketAsOf, "0".repeat(64), ticker, marketAsOf);
    }

    private byte[] withChecksum(String json, String checksum) {
        return json.replaceFirst("\"checksum\": \"0{64}\"", "\"checksum\": \"" + checksum + "\"")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String checksumOf(String json) {
        var p = imports.preview(json.getBytes(StandardCharsets.UTF_8));
        // preview ignores the declared checksum ONLY if it matches; with a placeholder it fails checksum.
        // So compute via a preview on a body whose declared checksum already equals canonical: do a two-pass.
        return p.canonicalChecksum();
    }

    @Test
    void validSnapshotPreviewsThenCommits() {
        String base = validSnapshot("snap-ok-1", "WEGE3", "2026-10-06");
        // first preview with a correct checksum: compute canonical by previewing a self-consistent body.
        String canonical = canonicalFor(base);
        byte[] body = withChecksum(base, canonical);
        var preview = imports.preview(body);
        assertTrue(preview.wouldImport(), "valid snapshot previews");
        assertEquals("snap-ok-1", preview.snapshotId());
        assertEquals(1, preview.recordCount());

        var commit = imports.commit(body, preview.canonicalChecksum());
        assertEquals("IMPORTED", commit.disposition());
        assertEquals("CONSUMER_VERIFIED_SYNTHETIC",
                snapshots.findBySnapshotId("snap-ok-1").orElseThrow().getStatus(),
                "status is synthetic — never 'real integration'");
        // audit written
        assertTrue(auditRepo.findByOrderByOccurredAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .anyMatch(e -> e.getAction() == AdminAuditEvent.Action.IMPORT_SNAPSHOT
                        && e.getOutcome() == AdminAuditEvent.Outcome.SUCCESS),
                "an IMPORT_SNAPSHOT SUCCESS audit row is written");
    }

    @Test
    void unknownSchemaRejected() {
        byte[] body = validSnapshot("snap-schema", "WEGE3", "2026-10-06")
                .replace("b3-monitor.analytics-snapshot/1", "b3-monitor.analytics-snapshot/2")
                .getBytes(StandardCharsets.UTF_8);
        var p = imports.preview(body);
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("unsupported schemaVersion")));
    }

    @Test
    void malformedPayloadRejected() {
        var p = imports.preview("{ not valid json ".getBytes(StandardCharsets.UTF_8));
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("malformed JSON")));
    }

    @Test
    void unknownTickerRejected() {
        String base = validSnapshot("snap-tick", "ZZZZ9", "2026-10-06");
        var p = imports.preview(withChecksum(base, canonicalFor(base)));
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("not in trusted catalog")));
    }

    @Test
    void futureAsOfRejected() {
        String base = validSnapshot("snap-future", "WEGE3", "2026-10-20");   // after NOW (2026-10-08)
        var p = imports.preview(withChecksum(base, canonicalFor(base)));
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("future")));
    }

    @Test
    void duplicateIdenticalImportIsIdempotent() {
        String base = validSnapshot("snap-idem", "WEGE3", "2026-10-06");
        String canonical = canonicalFor(base);
        byte[] body = withChecksum(base, canonical);
        assertEquals("IMPORTED", imports.commit(body, canonical).disposition());
        var second = imports.commit(body, canonical);
        assertEquals("IDEMPOTENT_NOOP", second.disposition(), "same id + checksum is idempotent");
        assertEquals(1, snapshots.findByOrderByImportedAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(s -> s.getSnapshotId().equals("snap-idem")).count());
    }

    @Test
    void sameIdDifferentChecksumConflict() {
        String base = validSnapshot("snap-conflict", "WEGE3", "2026-10-06");
        assertEquals("IMPORTED", imports.commit(withChecksum(base, canonicalFor(base)), canonicalFor(base)).disposition());
        // a different content (different record values) under the SAME id → conflict
        String changed = validSnapshot("snap-conflict", "WEGE3", "2026-10-06").replace("50.10", "99.99");
        var r = imports.commit(withChecksum(changed, canonicalFor(changed)), canonicalFor(changed));
        assertEquals("REJECTED_CONFLICT", r.disposition());
    }

    @Test
    void previewCommitContentMismatchRejected() {
        String base = validSnapshot("snap-token", "WEGE3", "2026-10-06");
        String canonical = canonicalFor(base);
        byte[] previewed = withChecksum(base, canonical);
        var p = imports.preview(previewed);
        assertTrue(p.wouldImport());
        // commit DIFFERENT content but present the OLD token → rejected
        String changed = validSnapshot("snap-token", "WEGE3", "2026-10-06").replace("55.00", "60.00");
        var r = imports.commit(withChecksum(changed, canonicalFor(changed)), p.canonicalChecksum());
        assertEquals("REJECTED_TOKEN_MISMATCH", r.disposition());
        assertTrue(snapshots.findBySnapshotId("snap-token").isEmpty(), "nothing persisted on token mismatch");
    }

    @Test
    void forbiddenPrivatePortfolioKeyRejected() {
        // FIN-04: smuggling XIRR / holdings must be refused by the validator, not stored.
        String base = validSnapshot("snap-priv", "WEGE3", "2026-10-06")
                .replace("\"quality\": \"RAW close-only\"", "\"quality\": \"RAW close-only\", \"xirr\": 0.12");
        var p = imports.preview(base.getBytes(StandardCharsets.UTF_8));
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("forbidden private-portfolio key")));
    }

    @Test
    void oversizeDocumentRejected() {
        String huge = "{\"schemaVersion\":\"b3-monitor.analytics-snapshot/1\",\"pad\":\""
                + "x".repeat(600 * 1024) + "\"}";
        var p = imports.preview(huge.getBytes(StandardCharsets.UTF_8));
        assertFalse(p.wouldImport());
        assertTrue(p.errors().stream().anyMatch(e -> e.contains("exceeds")));
    }

    @Test
    void emptyDocumentRejected() {
        assertFalse(imports.preview(new byte[0]).wouldImport());
    }

    /** Compute the canonical checksum for a base body by previewing a copy whose declared checksum is
     *  already correct — a two-pass: preview the placeholder body (which fails the checksum but still
     *  returns the canonical in... no), so instead we validate a body whose checksum we fix iteratively. */
    private String canonicalFor(String base) {
        // The validator rejects a checksum mismatch, but preview still needs a valid body. Trick: the
        // canonical checksum is a pure function of the RECORDS, independent of the declared checksum field,
        // so a body with ANY syntactically-valid 64-hex checksum validates far enough to compute it only
        // when they MATCH. We therefore compute it the same way the validator does, by asking the validator
        // on a body whose declared checksum equals the canonical — bootstrapped by trying a fixed value and
        // reading the mismatch error, which contains the canonical.
        byte[] attempt = withChecksum(base, "0".repeat(64));
        var p = imports.preview(attempt);
        if (p.wouldImport()) return p.canonicalChecksum();
        // the mismatch error embeds "canonical <hex>"
        for (String e : p.errors()) {
            int i = e.indexOf("canonical ");
            if (i >= 0) {
                String hex = e.substring(i + "canonical ".length()).trim();
                return hex.length() >= 64 ? hex.substring(0, 64) : hex;
            }
        }
        return "0".repeat(64);
    }
}
