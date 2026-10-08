package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-17 item 2/13 — adversarial unit tests for the HMAC-bound preview token. Proves the token binds the
 * full authorization context (content checksum, schema, snapshotId, actor, purpose, expiry) and fails
 * closed on tamper / replay-across-context / expiry / wrong purpose / malformed input, with a constant-time
 * MAC compare. No secret or raw payload is in the token.
 */
class AnalyticsPreviewTokenTest {

    static final Instant NOW = Instant.parse("2026-10-08T17:00:00Z");
    static final byte[] SECRET = "unit-test-secret-key-0123456789".getBytes();

    private AnalyticsPreviewToken tok(Instant now) {
        return new AnalyticsPreviewToken(SECRET, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(900));
    }

    @Test
    void validTokenVerifiesForSameContext() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-1", "abc123", "admin");
        assertTrue(t.verify(token, "schema/1", "snap-1", "abc123", "admin").valid());
    }

    @Test
    void tamperedTokenRejected() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-1", "abc123", "admin");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A") + "C";
        var v = t.verify(tampered, "schema/1", "snap-1", "abc123", "admin");
        assertFalse(v.valid());
        assertTrue(v.rejection() == AnalyticsPreviewToken.Rejection.BAD_SIGNATURE
                || v.rejection() == AnalyticsPreviewToken.Rejection.MALFORMED);
    }

    @Test
    void tokenForOneActorCannotBeCommittedByAnother() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-1", "abc123", "alice");
        assertEquals(AnalyticsPreviewToken.Rejection.ACTOR_MISMATCH,
                t.verify(token, "schema/1", "snap-1", "abc123", "bob").rejection());
    }

    @Test
    void tokenForPayloadACannotCommitPayloadB() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-1", "checksumA", "admin");
        assertEquals(AnalyticsPreviewToken.Rejection.CONTENT_MISMATCH,
                t.verify(token, "schema/1", "snap-1", "checksumB", "admin").rejection());
    }

    @Test
    void tokenForSnapshotACannotCommitSnapshotB() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-A", "abc123", "admin");
        assertEquals(AnalyticsPreviewToken.Rejection.SNAPSHOT_MISMATCH,
                t.verify(token, "schema/1", "snap-B", "abc123", "admin").rejection());
    }

    @Test
    void tokenForSchemaACannotCommitSchemaB() {
        var t = tok(NOW);
        String token = t.issue("schema/1", "snap-1", "abc123", "admin");
        assertEquals(AnalyticsPreviewToken.Rejection.SCHEMA_MISMATCH,
                t.verify(token, "schema/2", "snap-1", "abc123", "admin").rejection());
    }

    @Test
    void expiredTokenRejected() {
        String token = tok(NOW).issue("schema/1", "snap-1", "abc123", "admin");
        // verify from a clock 16 minutes later (ttl is 900s = 15 min)
        var later = tok(NOW.plusSeconds(16 * 60));
        assertEquals(AnalyticsPreviewToken.Rejection.EXPIRED,
                later.verify(token, "schema/1", "snap-1", "abc123", "admin").rejection());
    }

    @Test
    void wrongPurposeTokenRejected() {
        // forge a token whose claims use a different purpose but is signed with the SAME secret
        var t = tok(NOW);
        var claims = new AnalyticsPreviewToken.Claims("some-other-purpose", "schema/1", "snap-1", "abc123",
                "admin", NOW.toEpochMilli(), 900);
        // re-issue via reflection-free path: sign the forged claim string with the real secret
        byte[] claimBytes = claims.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claimBytes) + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(claimBytes));
        assertEquals(AnalyticsPreviewToken.Rejection.WRONG_PURPOSE,
                t.verify(forged, "schema/1", "snap-1", "abc123", "admin").rejection());
    }

    @Test
    void malformedTokenRejectedFailClosed() {
        var t = tok(NOW);
        assertFalse(t.verify(null, "s", "id", "c", "a").valid());
        assertFalse(t.verify("not-a-token", "s", "id", "c", "a").valid());
        assertFalse(t.verify("only-one-part", "s", "id", "c", "a").valid());
        assertFalse(t.verify(".", "s", "id", "c", "a").valid());
    }

    @Test
    void constantTimeEqualsIsLengthSafe() {
        assertTrue(AnalyticsPreviewToken.constantTimeEquals(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
        assertFalse(AnalyticsPreviewToken.constantTimeEquals(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
        assertFalse(AnalyticsPreviewToken.constantTimeEquals(new byte[]{1, 2, 3}, new byte[]{1, 2}));
        assertFalse(AnalyticsPreviewToken.constantTimeEquals(null, new byte[]{1}));
    }

    @Test
    void secretTooShortRejectedAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new AnalyticsPreviewToken("short".getBytes(), Clock.systemUTC(), Duration.ofSeconds(900)));
    }

    private static byte[] hmac(byte[] data) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
