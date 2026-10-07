package dev.b3monitor.domain.outbox;

import java.time.Instant;

/**
 * The ONLY values a transport adapter may return (cycle-6 review P0-2). The adapter must NOT know the
 * outbox persistence state machine: it reports a narrow transport classification, and the dispatcher
 * is the single component that maps it to a legal {@link OutboxState}. This makes the old
 * duplicate-send hazard (an adapter returning {@code PENDING}/{@code IN_FLIGHT}) impossible by type.
 *
 * <ul>
 *   <li>{@link Kind#ACCEPTED} — the provider accepted the submission (NOT proof of delivery).</li>
 *   <li>{@link Kind#DEFINITE_FAILURE} — the provider definitively rejected it and NOTHING was sent;
 *       only legal when the adapter can actually prove no side effect occurred.</li>
 *   <li>{@link Kind#UNKNOWN} — ambiguous: the submission may or may not have happened. Never resent
 *       blindly; it maps to {@code UNKNOWN_OUTCOME} for reconciliation.</li>
 * </ul>
 *
 * Metadata fields are nullable and are stored ONLY when a verified adapter supplies them — never
 * fabricated (no invented provider ids / WAHA semantics).
 */
public record SubmissionResult(Kind kind, String providerMessageId, Instant providerAcceptedAt,
                               String sanitizedStatus) {

    public enum Kind { ACCEPTED, DEFINITE_FAILURE, UNKNOWN }

    public SubmissionResult {
        if (kind == null) kind = Kind.UNKNOWN;   // a null kind is treated as ambiguous, fail-safe
    }

    public static SubmissionResult accepted() { return new SubmissionResult(Kind.ACCEPTED, null, null, null); }
    public static SubmissionResult accepted(String providerMessageId, Instant acceptedAt) {
        return new SubmissionResult(Kind.ACCEPTED, providerMessageId, acceptedAt, null);
    }
    public static SubmissionResult definiteFailure(String sanitizedStatus) {
        return new SubmissionResult(Kind.DEFINITE_FAILURE, null, null, sanitizedStatus);
    }
    public static SubmissionResult unknown(String sanitizedStatus) {
        return new SubmissionResult(Kind.UNKNOWN, null, null, sanitizedStatus);
    }
}
