package dev.b3monitor.domain.dispatch;

import dev.b3monitor.persistence.OutboxEntity;

import java.time.Instant;

/**
 * Pre-dispatch eligibility fence (cycle-6 review P0-2). Collection-time authorization in the
 * scheduler is NOT sufficient: a durable intent can outlive the condition that justified it (the
 * asset is deauthorized, the rule is paused or superseded by a newer revision, the intent expired or
 * its source became too stale). This guard is consulted immediately BEFORE any adapter send; a denied
 * intent is transitioned to a terminal no-send state and the adapter is NEVER called for it.
 *
 * <p>Production implementations MUST fail closed: anything not provably eligible is denied. Until a
 * persistent rule/admin registry exists, {@code FailClosedDispatchEligibilityGuard} denies by default
 * and tests inject a fixture that authorizes only the intended synthetic case.
 */
public interface DispatchEligibilityGuard {

    /** Why an intent may not be dispatched; maps to a terminal {@code OutboxState}. */
    enum Denial {
        OK,                        // eligible — may dispatch
        ASSET_NOT_AUTHORIZED,      // asset no longer operationally authorized
        RULE_PAUSED_OR_DISABLED,   // rule paused/disabled
        SUPERSEDED_REVISION,       // intent belongs to a superseded rule revision
        EXPIRED,                   // intent past its expiry
        SOURCE_TOO_STALE,          // source-as-of older than the dispatch-time policy
        CANCELLED_OR_SUPPRESSED,   // already cancelled/suppressed upstream
        UNKNOWN                    // insufficient evidence → fail closed
    }

    record Decision(Denial denial) {
        public boolean mayDispatch() { return denial == Denial.OK; }
    }

    /** Recheck {@code row} against current state at {@code now}. Never performs I/O to a provider. */
    Decision evaluate(OutboxEntity row, Instant now);
}
