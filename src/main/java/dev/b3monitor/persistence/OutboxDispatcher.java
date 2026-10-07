package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.SubmissionResult;
import dev.b3monitor.domain.outbox.WahaOutboundAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Post-commit outbox dispatcher. It never shares a transaction with the triggering event and never
 * holds a DB transaction across the adapter call. Cycle-7 flow (review P0-1/P0-2):
 *
 * <ol>
 *   <li><b>claim</b> ({@link OutboxTxOps#claimNext}): PENDING → IN_FLIGHT, committed.</li>
 *   <li><b>prepareSend</b> ({@link OutboxTxOps#prepareSend}): in one short transaction, confirm the
 *       active claim + lease, re-run eligibility, and commit {@code SENDING} BEFORE any I/O. If a
 *       pre-send terminalization won, this returns DENIED/GONE and the adapter is NEVER called.</li>
 *   <li><b>send</b>: only after SENDING commits, call the adapter, which returns a narrow
 *       {@link SubmissionResult} (never an outbox state).</li>
 *   <li><b>record</b> ({@link OutboxTxOps#record}): map the typed result to the one legal state,
 *       fenced by the active SENDING claim. A null/thrown result → UNKNOWN_OUTCOME (never resent).</li>
 * </ol>
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int CLAIM_RETRIES = 5;

    private final OutboxTxOps tx;
    private final WahaOutboundAdapter adapter;
    private final Clock clock;
    private final int defaultBatch;
    private final String workerId;

    public OutboxDispatcher(OutboxTxOps tx, WahaOutboundAdapter adapter, Clock clock,
                            @Value("${b3monitor.outbox.max-batch:50}") int defaultBatch) {
        this.tx = tx;
        this.adapter = adapter;
        this.clock = clock;
        this.defaultBatch = Math.max(1, defaultBatch);
        this.workerId = "dispatcher-" + Long.toHexString(ProcessHandle.current().pid());
    }

    /** Process one claimable PENDING row end-to-end. Returns the final state, or empty if none. */
    public Optional<OutboxState> dispatchOne() {
        OutboxTxOps.Claim claim = claimWithRetry();
        if (claim == null) return Optional.empty();

        // AUTHORITY GATE (review P0-1): commit SENDING before any network I/O. If a pre-send
        // terminalization won, no adapter call happens.
        OutboxTxOps.Prepared prepared;
        try {
            prepared = tx.prepareSend(claim.rowId(), claim.fencingToken(), clock.instant());
        } catch (ObjectOptimisticLockingFailureException race) {
            // Pre-send optimistic race (cycle-8 review F): a concurrent writer won before SENDING
            // committed, so NOTHING was sent and NO UNKNOWN transition was persisted. Reporting
            // UNKNOWN_OUTCOME here would contradict the persisted state. Reload the row and report its
            // ACTUAL persisted state; if it vanished, report empty. No adapter call occurred.
            OutboxEntity actual = tx.loadRow(claim.rowId());
            log.debug("prepareSend race for {}: no send; persisted state now {}",
                    claim.logicalKey(), actual == null ? "GONE" : actual.getState());
            return actual == null ? Optional.empty() : Optional.of(actual.getState());
        }
        if (prepared.outcome() != OutboxTxOps.PrepareOutcome.AUTHORIZED) {
            log.warn("dispatch not authorized for {}: {} → {}",
                    claim.logicalKey(), prepared.outcome(), prepared.terminalState());
            return Optional.ofNullable(prepared.terminalState());
        }

        // SENDING is committed: an external side effect may now occur.
        SubmissionResult result;
        try {
            result = adapter.send(claim.intent());            // OUTSIDE any transaction
            if (result == null) result = SubmissionResult.unknown("null-result");
        } catch (RuntimeException e) {
            log.warn("dispatch send error for {}: {}", claim.logicalKey(), e.getClass().getSimpleName());
            result = SubmissionResult.unknown(e.getClass().getSimpleName());  // ambiguous → UNKNOWN
        }
        OutboxState mapped = tx.record(claim.rowId(), claim.fencingToken(), result, clock.instant());
        return Optional.ofNullable(mapped);
    }

    private OutboxTxOps.Claim claimWithRetry() {
        for (int attempt = 0; attempt < CLAIM_RETRIES; attempt++) {
            try {
                return tx.claimNext(workerId);
            } catch (ObjectOptimisticLockingFailureException race) {
                log.debug("claim race lost (attempt {}), retrying", attempt + 1);
            }
        }
        return null;
    }

    /** Drain up to {@code max} claimable rows (bounded). */
    public int drain(int max) {
        int bound = max <= 0 ? defaultBatch : max;
        int done = 0;
        for (int i = 0; i < bound; i++) {
            if (dispatchOne().isEmpty()) break;
            done++;
        }
        return done;
    }

    /** Drain one bounded batch using the configured default (review F: no Integer.MAX_VALUE). */
    public int drainBatch() { return drain(defaultBatch); }

    /** Reconcile expired-lease rows: IN_FLIGHT→safe PENDING recovery, SENDING→UNKNOWN. Returns
     *  {@code [recovered, quarantined]}. */
    public int[] reconcileExpiredLeases() {
        return tx.reconcileExpiredLeases();
    }
}
