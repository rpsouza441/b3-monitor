package dev.b3monitor.domain.auth;

import dev.b3monitor.domain.rule.PriceRule;

/**
 * Runtime egress gate: may THIS rule's asset be queried against Brapi right now?
 *
 * <p>Cycle-5 review P1 — operational activation must be an EXECUTABLE per-asset gate, not a line in a
 * document. Even with the worker accidentally enabled, the calendar OPEN and a dedicated quota, a
 * rule whose asset is {@code NOT_AUTHORIZED}, whose identity is only PARTIAL, which is QUARANTINED, or
 * which is PAUSED must NOT reach the network. The scheduler consults this BEFORE acquiring quota or
 * fetching, and fails closed on anything that is not an explicit, current authorization.
 *
 * <p>Authorization is operator-controlled state; nothing here grants it. The default implementation
 * ({@code StaticOperationalAuthorization}) starts every one of the 23 assets at
 * {@link Status#NOT_AUTHORIZED}, with SNAG11 PARTIAL and KNHY11 QUARANTINED, mirroring the approved
 * identity gate (DP-01). No code path flips an asset to {@link Status#AUTHORIZED} implicitly.
 */
public interface OperationalAuthorization {

    enum Status {
        /** Current identity verified AND rule/asset explicitly authorized AND not paused. May fetch. */
        AUTHORIZED,
        /** Explicitly not authorized for operation (default for every asset). */
        NOT_AUTHORIZED,
        /** Identity only partially verified — never fetch (fail-closed). */
        PARTIAL_IDENTITY,
        /** Asset quarantined (e.g. KNHY11) — never fetch. */
        QUARANTINED,
        /** Authorized asset temporarily paused by the operator — never fetch while paused. */
        PAUSED
    }

    /** The authorization decision for a rule, with a reason for observability. */
    record Decision(Status status, String reason) {
        public boolean mayFetch() { return status == Status.AUTHORIZED; }
    }

    Decision evaluate(PriceRule rule);
}
