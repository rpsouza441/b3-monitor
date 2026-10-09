package dev.b3monitor.adapter.brapi;

/**
 * Raised on Brapi transport or payload-parse failure. Carries no secrets.
 *
 * <p>For HTTP 429 it carries a unified {@link QuotaSignal} (cycle-7 review P1-2) — the SAME nullable
 * telemetry model as a 2xx response (reset/remaining/limit/window/serverDate/requestId) — so the
 * quota layer sees identical provenance on success and on rate-limit. Missing/malformed headers are
 * {@code null} (uncertain), never fabricated. Legacy per-field accessors remain for callers that only
 * need retry/reset/remaining.
 */
public class BrapiException extends Exception {

    private final boolean rateLimited;
    private final QuotaSignal signal;

    public BrapiException(String message) {
        super(message);
        this.rateLimited = false;
        this.signal = QuotaSignal.none();
    }

    public BrapiException(String message, Throwable cause) {
        super(message, cause);
        this.rateLimited = false;
        this.signal = QuotaSignal.none();
    }

    public BrapiException(String message, Throwable cause, boolean rateLimited, QuotaSignal signal) {
        super(message, cause);
        this.rateLimited = rateLimited;
        this.signal = signal == null ? QuotaSignal.none() : signal;
    }

    /** Convenience for a rate-limit (429) failure carrying the full unified telemetry. */
    public static BrapiException rateLimited(String message, QuotaSignal signal) {
        return new BrapiException(message, null, true, signal);
    }

    /** Back-compat convenience (retry/reset/remaining only); other provenance null. */
    public static BrapiException rateLimited(String message, Long retryAfterSeconds,
                                             Long resetDeltaSeconds, Integer remaining) {
        return new BrapiException(message, null, true,
                new QuotaSignal(retryAfterSeconds, resetDeltaSeconds, remaining, null, null, null, null, true));
    }

    public boolean isRateLimited() { return rateLimited; }
    /** The full unified quota telemetry parsed from the response (never null). */
    public QuotaSignal signal() { return signal; }
    public Long retryAfterSeconds() { return signal.retryAfterSeconds(); }
    public Long resetDeltaSeconds() { return signal.resetDeltaSeconds(); }
    public Integer remaining() { return signal.remaining(); }
}
