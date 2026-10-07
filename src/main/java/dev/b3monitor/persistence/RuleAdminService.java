package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.admin.AdminAuditEvent.Action;
import dev.b3monitor.admin.AdminAuditEvent.Outcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Local administration of persistent rule definitions (cycle-7/8). A DOMAIN service with NO HTTP
 * endpoint. All mutations are typed/bounded (validation in the entity), use the injected {@link Clock},
 * bump an immutable monotonic revision on edit/mode-change, and rely on {@code @Version} for conflict
 * handling. Nothing here activates an asset.
 *
 * <h2>Cycle-8 review P0-live — pause/resume lifecycle</h2>
 * {@link #pause} atomically marks the rule paused AND cancels its unsent PENDING outbox intents (via
 * {@link OutboxTxOps#cancelPendingForRule}) with an audit reason, preserving ACCEPTED/UNKNOWN/FAILED
 * evidence and the rule_state latch/episode history. {@link #resume} sets a {@code rebaseline_required}
 * marker on the rule_state so the first eligible observation after resume re-baselines and cannot fire
 * (no replay of a pre-pause episode, no CROSSING inferred across the unobserved gap).
 */
@Service
public class RuleAdminService {

    private final RuleDefinitionRepository repo;
    private final RuleStateRepository ruleStates;
    private final OutboxTxOps outboxTx;
    private final dev.b3monitor.admin.AdminAuditService audit;
    private final Clock clock;

    public RuleAdminService(RuleDefinitionRepository repo, RuleStateRepository ruleStates,
                            OutboxTxOps outboxTx, dev.b3monitor.admin.AdminAuditService audit, Clock clock) {
        this.repo = repo;
        this.ruleStates = ruleStates;
        this.outboxTx = outboxTx;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RuleDefinitionEntity create(String ruleId, String ticker, Comparator comparator,
                                       BigDecimal threshold, int precision, BigDecimal hysteresis) {
        if (ruleId == null || ruleId.isBlank() || ruleId.length() > 100)
            throw new IllegalArgumentException("ruleId 1..100 chars required");
        if (repo.existsByRuleId(ruleId)) {
            throw new IllegalStateException("rule already exists: " + ruleId);
        }
        // Created UNSELECTED (fail-closed) — a human must selectMode before it operates.
        RuleDefinitionEntity saved = repo.save(new RuleDefinitionEntity(
                ruleId, ticker, comparator, threshold, precision, hysteresis, clock.instant()));
        audit.record(Action.CREATE_RULE, ruleId, null, saved.getRevision(), Outcome.SUCCESS, "created UNSELECTED");
        return saved;
    }

    /**
     * Typed edit under the lifecycle fence (cycle-10 A) with an {@code expectedRevision} precondition
     * (cycle-10 B / item D): applies exactly once at the expected revision and bumps it; a stale
     * expectedRevision is a {@link StaleRevisionException} conflict with NO mutation (so an HTTP retry
     * cannot blind-replay into another revision). {@code @Version} remains the DB concurrency guard.
     */
    @Transactional
    public long edit(String ruleId, long expectedRevision, Comparator comparator, BigDecimal threshold,
                     int precision, BigDecimal hysteresis) {
        RuleDefinitionEntity e = lock(ruleId);
        if (e.getRevision() != expectedRevision) {
            // audit the rejection in a separate tx (the business tx carries no change)
            audit.recordRejection(Action.EDIT_RULE, ruleId, e.getRevision(), Outcome.REJECTED_CONFLICT,
                    "stale expectedRevision " + expectedRevision + " != " + e.getRevision());
            throw new StaleRevisionException(ruleId, expectedRevision, e.getRevision());
        }
        long before = e.getRevision();
        long rev = e.applyEdit(comparator, threshold, precision, hysteresis, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "superseded-by-edit-rev" + rev);
        audit.record(Action.EDIT_RULE, ruleId, before, rev, Outcome.SUCCESS, "edited typed definition");
        return rev;
    }

    /**
     * Select/change the evaluation mode under the fence. Retry-safe (cycle-10 B): selecting the
     * ALREADY-CURRENT mode is an idempotent no-op that returns the current revision WITHOUT bumping it,
     * so a duplicate HTTP request cannot mint an extra revision. A real mode change bumps the revision
     * and cancels old PENDING. LEVEL is refused (Q-19 pending) by the entity.
     */
    @Transactional
    public long selectMode(String ruleId, RuleMode mode) {
        RuleDefinitionEntity e = lock(ruleId);
        if (mode != null && mode == e.getMode()) {
            audit.record(Action.SELECT_MODE, ruleId, e.getRevision(), e.getRevision(), Outcome.NO_OP,
                    "mode already " + mode);
            return e.getRevision();                     // idempotent desired-state no-op (no revision bump)
        }
        long before = e.getRevision();
        long rev = e.selectMode(mode, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "superseded-by-mode-change-rev" + rev);
        audit.record(Action.SELECT_MODE, ruleId, before, rev, Outcome.SUCCESS, "mode -> " + mode);
        return rev;
    }

    /**
     * Pause under the fence. Retry-safe (cycle-10 B): already-paused is an idempotent no-op (no revision
     * mint, no re-cancel). A real active→paused transition cancels unsent PENDING (evidence preserved).
     */
    @Transactional
    public void pause(String ruleId) {
        RuleDefinitionEntity e = lock(ruleId);
        if (e.isPaused()) {
            audit.record(Action.PAUSE, ruleId, e.getRevision(), e.getRevision(), Outcome.NO_OP, "already paused");
            return;                                     // idempotent
        }
        e.setPaused(true, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "paused");
        audit.record(Action.PAUSE, ruleId, e.getRevision(), e.getRevision(), Outcome.SUCCESS, "paused; unsent PENDING cancelled");
    }

    /**
     * Resume under the fence. Retry-safe (cycle-10 B / review P0): the {@code rebaseline_required} marker
     * is set ONLY on the real paused→active transition — a duplicate/retried resume on an already-active
     * rule is an idempotent no-op and MUST NOT re-mark rebaseline (which would consume a fresh-baseline
     * window and could suppress a later legitimate crossing).
     */
    @Transactional
    public void resume(String ruleId) {
        RuleDefinitionEntity e = lock(ruleId);
        if (!e.isPaused()) {
            audit.record(Action.RESUME, ruleId, e.getRevision(), e.getRevision(), Outcome.NO_OP, "already active");
            return;                                     // idempotent: no second rebaseline marking
        }
        e.setPaused(false, clock.instant());
        repo.save(e);
        ruleStates.findByRuleId(ruleId).ifPresent(rs -> { rs.markRebaselineRequired(); ruleStates.save(rs); });
        audit.record(Action.RESUME, ruleId, e.getRevision(), e.getRevision(), Outcome.SUCCESS, "resumed; rebaseline marker set");
    }

    @Transactional
    public void disable(String ruleId) {
        RuleDefinitionEntity e = lock(ruleId);
        if (!e.isEnabled()) {
            audit.record(Action.DISABLE, ruleId, e.getRevision(), e.getRevision(), Outcome.NO_OP, "already disabled");
            return;                                     // idempotent
        }
        e.setEnabled(false, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "disabled");
        audit.record(Action.DISABLE, ruleId, e.getRevision(), e.getRevision(), Outcome.SUCCESS, "disabled; unsent PENDING cancelled");
    }

    @Transactional(readOnly = true)
    public Optional<RuleDefinitionEntity> find(String ruleId) { return repo.findByRuleId(ruleId); }

    @Transactional(readOnly = true)
    public List<RuleDefinitionEntity> list() { return repo.findAll(); }

    /** Acquire the shared lifecycle fence (PESSIMISTIC_WRITE) on the rule row, or fail if unknown. */
    private RuleDefinitionEntity lock(String ruleId) {
        return repo.findByRuleIdForUpdate(ruleId)
                .orElseThrow(() -> new IllegalStateException("unknown rule: " + ruleId));
    }

    private RuleDefinitionEntity require(String ruleId) {
        return repo.findByRuleId(ruleId)
                .orElseThrow(() -> new IllegalStateException("unknown rule: " + ruleId));
    }

    /** Thrown when an edit's {@code expectedRevision} does not match the current revision (409 conflict). */
    public static class StaleRevisionException extends RuntimeException {
        public final long expected;
        public final long actual;
        public StaleRevisionException(String ruleId, long expected, long actual) {
            super("stale expectedRevision for " + ruleId + ": expected " + expected + ", current " + actual);
            this.expected = expected;
            this.actual = actual;
        }
    }
}
