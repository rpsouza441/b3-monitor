package dev.b3monitor.domain.outbox;

/**
 * Transport lifecycle of an outbound alert (CONTRACTS). Delivery CONFIRMED is a
 * separate dimension and is only ever set with verified provider evidence — an
 * ACCEPTED submission is NOT delivery.
 *
 * <p>Lifecycle (cycle-7 review P0-1):
 * {@code PENDING → IN_FLIGHT (claimed) → SENDING (submission authorized, committed BEFORE network I/O)
 *  → ACCEPTED | UNKNOWN_OUTCOME | FAILED}. Pre-send terminals (CANCELLED/EXPIRED/SUPPRESSED) are only
 * reachable from PENDING/IN_FLIGHT, before SENDING commits. Once SENDING commits, an external side
 * effect may have happened, so the row can never be declared "definitely not sent" without evidence.
 */
public enum OutboxState {
    PENDING,         // committed with the triggering event, not yet dispatched
    IN_FLIGHT,       // claimed by a dispatcher, eligibility not yet re-confirmed
    SENDING,         // submission AUTHORIZED and committed; the adapter call may now occur
    ACCEPTED,        // adapter accepted submission (NOT proof of delivery)
    UNKNOWN_OUTCOME, // adapter result ambiguous (e.g. timeout after send) — never blind-resend
    FAILED,          // definite failure / operator abandon; eligible for policy-bounded retry/dead-letter
    // ----- pre-send supersession / eligibility terminals (never sent) -----
    CANCELLED,       // superseded by a newer rule revision before SENDING committed
    EXPIRED,         // passed expiry / dispatch-time source-age policy before SENDING committed
    SUPPRESSED;      // blocked by the dispatch eligibility guard before SENDING committed

    /** States from which no network send may ever occur. */
    public boolean isTerminalNoSend() {
        return this == FAILED || this == CANCELLED || this == EXPIRED || this == SUPPRESSED;
    }
}
