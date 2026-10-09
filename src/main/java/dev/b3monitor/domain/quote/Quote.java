package dev.b3monitor.domain.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable market-quote observation. Preserves requested vs. returned identity,
 * the original source market timestamp, the receipt instant and previousClose with
 * its own date/basis — per CONTRACTS. Transport receipt NEVER substitutes for market
 * source time. Nothing here is "valid" by construction; eligibility is decided per use
 * by {@link QuoteValidator}.
 *
 * @param requestedTicker  ticker we asked for
 * @param returnedTicker   LITERAL ticker the provider returned (never decorated — the raw symbol)
 * @param remapped         provider {@code changed} flag: the symbol was remapped by the provider
 * @param currency         ISO currency reported by the provider (must be BRL to be eligible)
 * @param price            last/current price (decimal; must be &gt; 0 to be eligible)
 * @param previousClose    provider previousClose (may be null/unknown → change disabled)
 * @param previousCloseDate trading date/basis of previousClose (null → change disabled)
 * @param sourceTime       original market/source timestamp (null → cannot assess freshness)
 * @param receiptTime      instant we received the quote (never a substitute for sourceTime)
 * @param providerStale    provider x-brapi-stale flag (true → degraded, signal-ineligible)
 */
public record Quote(
        String requestedTicker,
        String returnedTicker,
        boolean remapped,
        String currency,
        BigDecimal price,
        BigDecimal previousClose,
        java.time.LocalDate previousCloseDate,
        Instant sourceTime,
        Instant receiptTime,
        boolean providerStale
) {
    public Quote {
        Objects.requireNonNull(requestedTicker, "requestedTicker");
        Objects.requireNonNull(returnedTicker, "returnedTicker");
        Objects.requireNonNull(receiptTime, "receiptTime");
    }

    /**
     * True when the returned identity matches what we requested (case-insensitive, trimmed)
     * AND the provider did NOT remap the symbol. A remap is fail-closed: it never matches,
     * so a remapped quote is ineligible for the requested rule's asset.
     */
    public boolean identityMatches() {
        return !remapped
                && requestedTicker.trim().equalsIgnoreCase(returnedTicker.trim());
    }
}
