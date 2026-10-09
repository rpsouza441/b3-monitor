package dev.b3monitor.adapter.brapi;

import java.time.Instant;

/**
 * Rate-limit telemetry parsed from a Brapi response, carried out-of-band so the quota layer can
 * observe it WITHOUT the adapter depending on the quota manager. Captured on BOTH 2xx and 429.
 *
 * <h2>Cycle-6 review P1 — billing-cycle provenance, not a time heuristic</h2>
 * The audited account responses carry {@code ratelimit-limit=15000}, {@code ratelimit-remaining},
 * {@code ratelimit-reset} (a DELTA), {@code x-ratelimit-window=billing-cycle} and a response
 * {@code Date}; the sandbox exposes a different 20/60s limiter. So the quota layer must decide an
 * account cycle by the WINDOW + LIMIT evidence, never by the reset delta's duration alone. This record
 * preserves all of that provenance; every field is nullable and means "uncertain" when null — a
 * missing/malformed header is never fabricated.
 *
 * @param retryAfterSeconds {@code Retry-After} seconds (usually only on 429), or null
 * @param resetDeltaSeconds {@code ratelimit-reset} DELTA seconds, or null
 * @param remaining         {@code ratelimit-remaining}, or null
 * @param limit             {@code ratelimit-limit}, or null
 * @param window            {@code x-ratelimit-window} (e.g. "billing-cycle"), or null
 * @param serverDate        the response {@code Date} header parsed to an Instant, or null
 * @param requestId         optional provider request id for lineage, or null
 * @param rateLimited       true when parsed from a 429 response
 */
public record QuotaSignal(Long retryAfterSeconds, Long resetDeltaSeconds, Integer remaining,
                          Integer limit, String window, Instant serverDate, String requestId,
                          boolean rateLimited) {

    public boolean hasAnySignal() {
        return retryAfterSeconds != null || resetDeltaSeconds != null || remaining != null
                || limit != null || window != null;
    }

    public static QuotaSignal none() {
        return new QuotaSignal(null, null, null, null, null, null, null, false);
    }
}
