package dev.b3monitor.domain.quote;

/** Structured, explainable reasons a quote is ineligible for signal use. */
public enum QuoteRejectionReason {
    IDENTITY_MISMATCH,     // returned ticker != requested ticker
    WRONG_CURRENCY,        // currency != BRL
    NON_POSITIVE_PRICE,    // price null or <= 0
    MISSING_SOURCE_TIME,   // no market/source timestamp → freshness cannot be assessed
    FUTURE_SOURCE_TIME,    // source timestamp in the future beyond skew tolerance
    STALE_SOURCE_TIME,     // source timestamp older than the configured max age
    PROVIDER_STALE_FLAG    // provider marked the quote stale (x-brapi-stale=1)
}
