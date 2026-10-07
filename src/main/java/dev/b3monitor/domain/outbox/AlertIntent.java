package dev.b3monitor.domain.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * A durable, idempotent alert intent. The {@code logicalKey} dedups a single logical alert episode
 * so a crash/retry cannot mint duplicates. Carries explainable context (ticker, message,
 * source/as-of) so the message can state WHY it fired and HOW fresh the input was, plus the dispatch
 * lineage the eligibility guard rechecks before any send (cycle-6 review P0-2): the rule revision and
 * episode epoch it was minted for, the intent-created instant (from the injected Clock — never a
 * hidden {@code Instant.now()}), and an optional expiry.
 */
public record AlertIntent(
        String logicalKey,
        String ruleId,
        String ticker,
        String message,
        long ruleRevision,
        long episodeEpoch,
        Instant sourceAsOf,
        Instant createdAt,
        Instant expiresAt
) {
    public AlertIntent {
        Objects.requireNonNull(logicalKey, "logicalKey");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(createdAt, "createdAt");
        if (ruleRevision < 1) throw new IllegalArgumentException("ruleRevision must be >= 1");
    }
}
