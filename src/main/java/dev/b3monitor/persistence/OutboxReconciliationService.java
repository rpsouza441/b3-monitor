package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.OutboxTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Local reconciliation + observability surface for ambiguous ({@code UNKNOWN_OUTCOME}) and failed
 * ({@code FAILED}) outbox rows (cycle-5 review P1). It is a SERVICE, invoked by an operator or an
 * authenticated internal caller — there is deliberately NO HTTP endpoint, so nothing is exposed
 * insecurely.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>{@link #metrics()} is read-only counts for a dashboard/log.</li>
 *   <li>{@link #deadLetters()} lists rows needing attention, oldest first.</li>
 *   <li>{@link #markFailedAfterProofOfNonDelivery} requeues an UNKNOWN_OUTCOME row to PENDING ONLY
 *       when the caller asserts verified proof the message was NOT delivered. Without that proof the
 *       row is never resent — an ACCEPTED/UNKNOWN send may already have reached WhatsApp, and WAHA
 *       exposes no verified idempotency contract.</li>
 *   <li>{@link #abandon} moves a row to {@code FAILED} (terminal, no resend) when the operator
 *       decides it must not be retried.</li>
 * </ul>
 */
@Service
public class OutboxReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciliationService.class);

    private final OutboxRepository repo;
    private final Clock clock;

    public OutboxReconciliationService(OutboxRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Read-only outbox counts across the full lifecycle + oldest-PENDING age, for observability (OPS-01/NOT-04). */
    public record Metrics(long pending, long inFlight, long sending, long accepted,
                          long unknownOutcome, long failed, long cancelled, long expired,
                          long suppressed, Duration oldestPendingAge) {}

    @Transactional(readOnly = true)
    public Metrics metrics() {
        Instant oldest = repo.oldestPendingCreatedAt();
        Duration age = oldest == null ? Duration.ZERO : Duration.between(oldest, clock.instant());
        return new Metrics(
                repo.countByState(OutboxState.PENDING),
                repo.countByState(OutboxState.IN_FLIGHT),
                repo.countByState(OutboxState.SENDING),
                repo.countByState(OutboxState.ACCEPTED),
                repo.countByState(OutboxState.UNKNOWN_OUTCOME),
                repo.countByState(OutboxState.FAILED),
                repo.countByState(OutboxState.CANCELLED),
                repo.countByState(OutboxState.EXPIRED),
                repo.countByState(OutboxState.SUPPRESSED),
                age);
    }

    /** Rows needing operator attention (UNKNOWN_OUTCOME or FAILED), oldest first. */
    @Transactional(readOnly = true)
    public List<OutboxEntity> deadLetters() {
        return repo.findNeedingReconciliation();
    }

    /**
     * Requeue an {@code UNKNOWN_OUTCOME} row for a fresh send — ONLY with verified proof of
     * non-delivery. The caller is responsible for that proof (e.g. a provider receipt lookup showing
     * the message was never accepted). This is the only path that can resend an ambiguous row, and it
     * is gated, never automatic.
     *
     * @return true if the row was requeued; false if not found or not in UNKNOWN_OUTCOME.
     */
    @Transactional
    public boolean markFailedAfterProofOfNonDelivery(long rowId, boolean verifiedNotDelivered) {
        if (!verifiedNotDelivered) {
            log.warn("refusing to requeue outbox {} without verified proof of non-delivery", rowId);
            return false;
        }
        OutboxEntity row = repo.findById(rowId).orElse(null);
        if (row == null || row.getState() != OutboxState.UNKNOWN_OUTCOME) {
            return false;                              // only UNKNOWN_OUTCOME → PENDING is legal here
        }
        OutboxTransitions.requireLegal(row.getState(), OutboxState.PENDING);
        row.invalidateClaim();                         // fence out any slow worker from the prior claim
        row.setSuppressionReason(null);                // live again
        row.setState(OutboxState.PENDING);             // dispatcher will claim it again on the next drain
        repo.save(row);
        log.info("outbox {} requeued to PENDING after proof of non-delivery", row.getLogicalKey());
        return true;
    }

    /**
     * Operator abandon → terminal FAILED. Enforces the transition matrix (review P1-1): legal ONLY
     * from {@code UNKNOWN_OUTCOME} (or idempotently from {@code FAILED}). An {@code ACCEPTED},
     * {@code SENDING}, {@code IN_FLIGHT}, {@code PENDING} or pre-send-terminal row is REFUSED, so an
     * accepted-submission evidence row can never be clobbered and a mid-attempt row is never declared
     * "definitely not sent".
     */
    @Transactional
    public boolean abandon(long rowId) {
        OutboxEntity row = repo.findById(rowId).orElse(null);
        if (row == null) return false;
        // Operator abandon is legal ONLY from an operator-reconciliation state. SENDING→FAILED is a
        // TRANSPORT outcome (a proven DEFINITE_FAILURE via record()), never an operator decision — an
        // operator cannot declare a mid-attempt row "definitely not sent". ACCEPTED/PENDING/terminals
        // are refused so evidence is preserved and pre-send cancel is a separate operation.
        if (row.getState() != OutboxState.UNKNOWN_OUTCOME && row.getState() != OutboxState.FAILED) {
            log.warn("refusing abandon of outbox {} from state {} (not an operator-abandonable state)",
                    row.getLogicalKey(), row.getState());
            return false;
        }
        row.invalidateClaim();
        row.setSuppressionReason("operator-abandon");
        row.setState(OutboxState.FAILED);
        repo.save(row);
        log.info("outbox {} abandoned → FAILED (terminal, no resend)", row.getLogicalKey());
        return true;
    }

    /**
     * Explicit, audited PRE-SEND cancel of a still-{@code PENDING} intent (distinct from abandon). Only
     * a PENDING row (never sent) may be cancelled this way; anything that reached SENDING/ACCEPTED/
     * UNKNOWN is refused. Returns true if cancelled.
     */
    @Transactional
    public boolean cancelPending(long rowId, String reason) {
        OutboxEntity row = repo.findById(rowId).orElse(null);
        if (row == null || row.getState() != OutboxState.PENDING) {
            return false;
        }
        OutboxTransitions.requireLegal(row.getState(), OutboxState.CANCELLED);
        row.invalidateClaim();
        row.setSuppressionReason(reason == null ? "operator-cancel" : reason);
        row.setState(OutboxState.CANCELLED);
        repo.save(row);
        log.info("outbox {} cancelled pre-send ({})", row.getLogicalKey(), row.getSuppressionReason());
        return true;
    }
}
