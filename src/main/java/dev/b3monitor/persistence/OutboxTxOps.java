package dev.b3monitor.persistence;

import dev.b3monitor.domain.dispatch.DispatchEligibilityGuard;
import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.OutboxTransitions;
import dev.b3monitor.domain.outbox.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The transactional steps of outbox dispatch, in a SEPARATE bean so Spring's transactional proxy
 * applies. Each public method runs in its OWN transaction (REQUIRES_NEW); the adapter call happens
 * BETWEEN {@link #prepareSend} and {@link #record}, never inside a DB transaction.
 *
 * <h2>Cycle-7 review P0-1 — submission-authority (no eligibility→send TOCTOU)</h2>
 * {@link #claimNext} only moves PENDING → IN_FLIGHT. {@link #prepareSend} is the authority gate: in one
 * short transaction it reloads the row, confirms the exact active claim, re-checks the lease, RE-RUNS
 * the eligibility guard against current state, and commits {@code SENDING} + the attempt-start stamp
 * BEFORE any network I/O. The adapter is called ONLY after that commit. A pre-send terminalization
 * that wins before this commit means the adapter is never called; once {@code SENDING} commits, the
 * row can never be declared "definitely not sent".
 *
 * <h2>Cycle-7 review P0-2 — typed transport outcome</h2>
 * {@link #record} takes a {@link SubmissionResult} (not an arbitrary state), requires the row to still
 * be {@code SENDING} with the active claim, and maps the narrow transport kind to the one legal
 * outbox state via {@link OutboxTransitions}.
 */
@Service
public class OutboxTxOps {

    private static final Logger log = LoggerFactory.getLogger(OutboxTxOps.class);
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    private final OutboxRepository repo;
    private final OutboxAttemptRepository attempts;
    private final DispatchEligibilityGuard eligibility;
    private final Clock clock;

    public OutboxTxOps(OutboxRepository repo, OutboxAttemptRepository attempts,
                       DispatchEligibilityGuard eligibility, Clock clock) {
        this.repo = repo;
        this.attempts = attempts;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    /** @param rowId db id; @param fencingToken token held; @param logicalKey episode key; @param intent reconstructed intent */
    public record Claim(Long rowId, long fencingToken, String logicalKey, AlertIntent intent) {}

    /** Outcome of {@link #prepareSend}: AUTHORIZED (SENDING committed) or a terminal/denied no-send. */
    public enum PrepareOutcome { AUTHORIZED, DENIED_TERMINAL, GONE }
    public record Prepared(PrepareOutcome outcome, OutboxState terminalState) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claimNext(String workerId) {
        List<OutboxEntity> candidates = repo.findClaimable();
        if (candidates.isEmpty()) return null;
        OutboxEntity row = candidates.get(0);
        OutboxTransitions.requireLegal(row.getState(), OutboxState.IN_FLIGHT);
        long token = row.claim(workerId, clock.instant().plus(DEFAULT_LEASE));
        row.setState(OutboxState.IN_FLIGHT);
        OutboxEntity saved = repo.saveAndFlush(row);  // commits IN_FLIGHT; may throw on race
        AlertIntent intent = new AlertIntent(
                saved.getLogicalKey(), saved.getRuleId(), saved.getTicker(), saved.getMessage(),
                saved.getRuleRevision(), saved.getEpisodeEpoch(), saved.getSourceAsOf(),
                saved.getIntentCreatedAt(), saved.getExpiresAt());
        return new Claim(saved.getId(), token, saved.getLogicalKey(), intent);
    }

    /**
     * Authority gate (review P0-1). In ONE transaction: reload, require the exact active IN_FLIGHT
     * claim + unexpired lease, re-evaluate eligibility, and on success commit {@code SENDING}. Returns
     * AUTHORIZED only after that commit — the caller may then (and only then) call the adapter. On a
     * denial the row is moved to the matching terminal no-send state (no adapter call will follow).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared prepareSend(Long rowId, long expectedToken, Instant now) {
        OutboxEntity row = repo.findById(rowId).orElse(null);
        if (row == null) return new Prepared(PrepareOutcome.GONE, null);
        if (!row.isActiveClaim(expectedToken)) {
            return new Prepared(PrepareOutcome.GONE, row.getState());  // reconciled/abandoned → fenced
        }
        if (row.leaseExpired(now)) {
            return new Prepared(PrepareOutcome.GONE, row.getState());  // lease lost before authority
        }
        var decision = eligibility.evaluate(row, now);
        if (!decision.mayDispatch()) {
            OutboxState terminal = switch (decision.denial()) {
                case EXPIRED, SOURCE_TOO_STALE -> OutboxState.EXPIRED;
                case SUPERSEDED_REVISION       -> OutboxState.CANCELLED;
                default                        -> OutboxState.SUPPRESSED;
            };
            OutboxTransitions.requireLegal(row.getState(), terminal);
            row.invalidateClaim();
            row.setSuppressionReason(decision.denial().name());
            row.setState(terminal);
            repo.save(row);
            return new Prepared(PrepareOutcome.DENIED_TERMINAL, terminal);
        }
        OutboxTransitions.requireLegal(row.getState(), OutboxState.SENDING);
        row.markSending(now);                         // SENDING + send_started_at + attempt count, committed below
        repo.save(row);
        // Append-only external-attempt ledger row (created only now that SENDING committed).
        attempts.save(new OutboxAttemptEntity(
                row.getId(), row.getLogicalKey(), row.getClaimGeneration(), row.getFencingToken(), now));
        return new Prepared(PrepareOutcome.AUTHORIZED, OutboxState.SENDING);
    }

    /**
     * Record a transport result, fenced by the active claim AND requiring the row to still be
     * {@code SENDING}. Maps the narrow {@link SubmissionResult} to the one legal outbox state and
     * stamps attempt provenance. A late result for a row no longer SENDING with this token is ignored.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxState record(Long rowId, long expectedToken, SubmissionResult result, Instant now) {
        OutboxEntity row = repo.findById(rowId).orElse(null);
        if (row == null) return null;
        if (row.getState() != OutboxState.SENDING || row.getFencingToken() != expectedToken) {
            log.debug("fenced out recording {} (state={} token have {} want {})",
                    row.getLogicalKey(), row.getState(), row.getFencingToken(), expectedToken);
            return row.getState();
        }
        OutboxState mapped = switch (result.kind()) {
            case ACCEPTED         -> OutboxState.ACCEPTED;
            case DEFINITE_FAILURE -> OutboxState.FAILED;
            case UNKNOWN          -> OutboxState.UNKNOWN_OUTCOME;
        };
        OutboxTransitions.requireLegal(row.getState(), mapped);
        row.setState(mapped);
        row.recordAttemptResult(now, result.providerMessageId(), result.providerAcceptedAt());
        repo.save(row);
        // Close the open attempt-ledger row for this claim generation (immutable thereafter).
        attempts.findByOutboxIdAndClaimGeneration(rowId, row.getClaimGeneration())
                .ifPresent(a -> { a.close(now, result); attempts.save(a); });
        return mapped;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public OutboxEntity loadRow(Long rowId) {
        return repo.findById(rowId).orElse(null);
    }

    /**
     * Reconcile expired-lease mid-attempt rows (cycle-8 review P1/D). An expired {@code IN_FLIGHT} row
     * never reached submission authority (no adapter call was possible before SENDING committed), so it
     * is SAFE to recover: the claim is invalidated (fencing the slow worker) and the row returns to
     * {@code PENDING} for a fresh claim + eligibility recheck. An expired {@code SENDING} row MAY have
     * reached the provider, so it is AMBIGUOUS → {@code UNKNOWN_OUTCOME}, never retried blindly.
     * Returns {@code [recovered, quarantined]}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] reconcileExpiredLeases() {
        int recovered = 0, quarantined = 0;
        for (OutboxEntity row : repo.findExpiredInFlightOrSending(clock.instant())) {
            if (row.getState() == OutboxState.IN_FLIGHT) {
                OutboxTransitions.requireLegal(OutboxState.IN_FLIGHT, OutboxState.PENDING);
                row.invalidateClaim();                 // no external send happened; safe pre-send recovery
                row.setState(OutboxState.PENDING);     // next claim rechecks eligibility
                repo.save(row);
                recovered++;
                log.warn("outbox {} IN_FLIGHT lease expired (pre-send) → PENDING (safe recovery)", row.getLogicalKey());
            } else { // SENDING
                OutboxTransitions.requireLegal(OutboxState.SENDING, OutboxState.UNKNOWN_OUTCOME);
                row.invalidateClaim();
                row.setState(OutboxState.UNKNOWN_OUTCOME);
                repo.save(row);
                quarantined++;
                log.warn("outbox {} SENDING lease expired (ambiguous) → UNKNOWN_OUTCOME (no resend)", row.getLogicalKey());
            }
        }
        return new int[]{recovered, quarantined};
    }

    /**
     * Cancel unsent PENDING intents for a rule below {@code newRevision} (supersession). Runs in the
     * CALLER's transaction (atomic with the revision bump). Only PENDING rows are touched; a row that
     * already reached SENDING/ACCEPTED/UNKNOWN is preserved as evidence.
     */
    @Transactional
    public int cancelSupersededPending(String ruleId, long newRevision, String reason) {
        int n = 0;
        for (OutboxEntity row : repo.findPendingByRuleBelowRevision(ruleId, newRevision)) {
            OutboxTransitions.requireLegal(row.getState(), OutboxState.CANCELLED);
            row.invalidateClaim();
            row.setSuppressionReason(reason);
            row.setState(OutboxState.CANCELLED);
            repo.save(row);
            n++;
        }
        return n;
    }

    /**
     * Cancel ALL unsent PENDING intents for a rule (any revision) — used on pause/disable/edit/mode
     * change so an old episode cannot be sent after the lifecycle change (cycle-8 review P0-live).
     * ACCEPTED/UNKNOWN/SENDING/terminal rows are preserved as evidence. Runs in the caller's tx.
     */
    @Transactional
    public int cancelPendingForRule(String ruleId, String reason) {
        int n = 0;
        for (OutboxEntity row : repo.findByRuleIdAndState(ruleId, OutboxState.PENDING)) {
            OutboxTransitions.requireLegal(row.getState(), OutboxState.CANCELLED);
            row.invalidateClaim();
            row.setSuppressionReason(reason);
            row.setState(OutboxState.CANCELLED);
            repo.save(row);
            n++;
        }
        return n;
    }
}
