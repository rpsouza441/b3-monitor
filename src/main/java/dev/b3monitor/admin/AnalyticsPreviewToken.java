package dev.b3monitor.admin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Cycle-17 (item 2): a cryptographically-bound preview→commit token for the analytics importer. The
 * cycle-16 token was the bare canonical checksum — it bound CONTENT but not the authorization CONTEXT, so
 * nothing stopped actor A's preview from being committed by actor B, or a token minted for one schema/
 * snapshot being presented for another. This token is an HMAC-SHA256 over a canonical claim string:
 *
 * <pre>purpose | schemaVersion | snapshotId | checksum | actor | issuedAtEpochMillis | ttlSeconds</pre>
 *
 * and the wire form is {@code base64url(claims) + "." + base64url(mac)}. Verification recomputes the MAC
 * with a CONSTANT-TIME compare and then re-binds every claim to the commit context: the token is accepted
 * only if its purpose, schema, snapshotId, checksum and actor all equal the live commit's, and it has not
 * expired. No secret and no raw payload are carried in the token; only the checksum (a digest) is.
 *
 * <p>Replay policy: a token is reusable ONLY insofar as the commit it authorizes is idempotent for the
 * exact same snapshotId+checksum (the importer enforces IDEMPOTENT_NOOP / CONFLICT). It can never authorize
 * a changed payload, because any change moves the checksum and the MAC no longer verifies. No single-use
 * token store is introduced — it would add durable state for no security gain over content+context binding
 * plus the DB's own uniqueness/idempotency.
 */
public class AnalyticsPreviewToken {

    /** Audience/purpose constant — a token minted for anything else is rejected. */
    public static final String PURPOSE = "b3-monitor:analytics-import:v1";

    private final byte[] secret;
    private final Clock clock;
    private final Duration ttl;

    public AnalyticsPreviewToken(byte[] secret, Clock clock, Duration ttl) {
        if (secret == null || secret.length < 16)
            throw new IllegalStateException("analytics preview-token secret must be >= 16 bytes");
        this.secret = secret.clone();
        this.clock = clock;
        this.ttl = ttl;
    }

    public record Claims(String purpose, String schemaVersion, String snapshotId, String checksum,
                         String documentDigest, String actor, long issuedAtMillis, long ttlSeconds) {
        /** Unambiguous canonical form: each string claim base64url-encoded, so a value containing the
         *  separator ('|') can never confuse the parser (cycle-18 item C). Binds BOTH the records checksum
         *  AND the full-document digest (cycle-18 item A). */
        String canonical() {
            return b64(purpose) + "|" + b64(schemaVersion) + "|" + b64(snapshotId) + "|" + b64(checksum)
                    + "|" + b64(documentDigest) + "|" + b64(actor) + "|" + issuedAtMillis + "|" + ttlSeconds;
        }
        private static String b64(String s) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
        }
    }

    public enum Rejection { OK, MALFORMED, BAD_SIGNATURE, EXPIRED, WRONG_PURPOSE,
                            SCHEMA_MISMATCH, SNAPSHOT_MISMATCH, CONTENT_MISMATCH, DOCUMENT_MISMATCH, ACTOR_MISMATCH }

    public record Verification(boolean valid, Rejection rejection) {
        static Verification ok() { return new Verification(true, Rejection.OK); }
        static Verification no(Rejection r) { return new Verification(false, r); }
    }

    /** Mint a token for the preview's exact authorization context (records checksum + full-document digest). */
    public String issue(String schemaVersion, String snapshotId, String checksum, String documentDigest, String actor) {
        Claims c = new Claims(PURPOSE, schemaVersion, snapshotId, checksum, documentDigest, actor,
                clock.instant().toEpochMilli(), ttl.getSeconds());
        byte[] claimBytes = c.canonical().getBytes(StandardCharsets.UTF_8);
        String claimB64 = b64(claimBytes);
        String macB64 = b64(hmac(claimBytes));
        return claimB64 + "." + macB64;
    }

    /** Verify a token against the LIVE commit context (constant-time MAC + full claim re-binding). */
    public Verification verify(String token, String schemaVersion, String snapshotId, String checksum,
                               String documentDigest, String actor) {
        if (token == null) return Verification.no(Rejection.MALFORMED);
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Verification.no(Rejection.MALFORMED);
        byte[] claimBytes, macBytes;
        try {
            claimBytes = Base64.getUrlDecoder().decode(token.substring(0, dot));
            macBytes = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Verification.no(Rejection.MALFORMED);
        }
        // Verify the signature FIRST (constant-time), before trusting any claim content.
        if (!constantTimeEquals(macBytes, hmac(claimBytes)))
            return Verification.no(Rejection.BAD_SIGNATURE);

        Claims c = parse(new String(claimBytes, StandardCharsets.UTF_8));
        if (c == null) return Verification.no(Rejection.MALFORMED);
        if (!PURPOSE.equals(c.purpose())) return Verification.no(Rejection.WRONG_PURPOSE);

        long now = clock.instant().toEpochMilli();
        // Overflow-safe expiry: ttlSeconds*1000 + issuedAt computed in a way that cannot wrap to a negative.
        long ttlMillis;
        try {
            ttlMillis = Math.multiplyExact(c.ttlSeconds(), 1000L);
        } catch (ArithmeticException overflow) {
            return Verification.no(Rejection.EXPIRED);   // absurd TTL — treat as invalid
        }
        long expiry;
        try {
            expiry = Math.addExact(c.issuedAtMillis(), ttlMillis);
        } catch (ArithmeticException overflow) {
            return Verification.no(Rejection.EXPIRED);
        }
        if (now > expiry || now < c.issuedAtMillis() - 60_000L)   // expired, or issued "in the future" (clock abuse)
            return Verification.no(Rejection.EXPIRED);

        if (!safeEq(c.schemaVersion(), schemaVersion)) return Verification.no(Rejection.SCHEMA_MISMATCH);
        if (!safeEq(c.snapshotId(), snapshotId))       return Verification.no(Rejection.SNAPSHOT_MISMATCH);
        if (!safeEq(c.checksum(), checksum))           return Verification.no(Rejection.CONTENT_MISMATCH);
        if (!safeEq(c.documentDigest(), documentDigest)) return Verification.no(Rejection.DOCUMENT_MISMATCH);
        if (!safeEq(c.actor(), actor))                 return Verification.no(Rejection.ACTOR_MISMATCH);
        return Verification.ok();
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static Claims parse(String s) {
        String[] p = s.split("\\|", -1);
        if (p.length != 8) return null;
        try {
            var dec = Base64.getUrlDecoder();
            String purpose = new String(dec.decode(p[0]), StandardCharsets.UTF_8);
            String schema = new String(dec.decode(p[1]), StandardCharsets.UTF_8);
            String snapshotId = new String(dec.decode(p[2]), StandardCharsets.UTF_8);
            String checksum = new String(dec.decode(p[3]), StandardCharsets.UTF_8);
            String documentDigest = new String(dec.decode(p[4]), StandardCharsets.UTF_8);
            String actor = new String(dec.decode(p[5]), StandardCharsets.UTF_8);
            return new Claims(purpose, schema, snapshotId, checksum, documentDigest, actor,
                    Long.parseLong(p[6]), Long.parseLong(p[7]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static boolean safeEq(String a, String b) { return a != null && a.equals(b); }

    /** Length-independent constant-time comparison (no early-exit on first differing byte). */
    static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
