package dev.b3monitor.adapter.brapi;

import dev.b3monitor.domain.quote.Quote;

/**
 * Port for fetching a single quote from Brapi. Brapi Free constraints (one ticker
 * per request, concurrency one, quota accounting) are enforced by the scheduling/quota
 * layer, not here. The adapter maps the raw provider payload into a {@link Quote},
 * preserving requested vs. returned identity and the provider stale flag; it performs
 * NO independent change calculation and asserts NO freshness — those are the domain's job.
 *
 * <p>Cycle-5 review P1: the fetch now also returns the {@link QuotaSignal} parsed from the response
 * headers on BOTH success and 429, so the quota layer can OBSERVE reset/remaining telemetry from
 * successful responses too (not only on rate-limit errors).
 */
public interface BrapiClient {

    /** Quote observation plus any rate-limit telemetry from the same response ({@code signal} is never null). */
    record FetchResult(Quote quote, QuotaSignal signal) {}

    /**
     * @param ticker the single ticker to request
     * @return the {@link Quote} observation and the response's {@link QuotaSignal} (never null)
     * @throws BrapiException on transport/parse failure (a 429 carries its own header provenance)
     */
    FetchResult fetch(String ticker) throws BrapiException;

    /** Convenience: just the quote (telemetry discarded). Prefer {@link #fetch} on the polling path. */
    default Quote fetchQuote(String ticker) throws BrapiException {
        return fetch(ticker).quote();
    }
}
