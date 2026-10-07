package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
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
    private final Clock clock;

    public RuleAdminService(RuleDefinitionRepository repo, RuleStateRepository ruleStates,
                            OutboxTxOps outboxTx, Clock clock) {
        this.repo = repo;
        this.ruleStates = ruleStates;
        this.outboxTx = outboxTx;
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
        return repo.save(new RuleDefinitionEntity(
                ruleId, ticker, comparator, threshold, precision, hysteresis, clock.instant()));
    }

    @Transactional
    public long edit(String ruleId, Comparator comparator, BigDecimal threshold,
                     int precision, BigDecimal hysteresis) {
        RuleDefinitionEntity e = require(ruleId);
        long rev = e.applyEdit(comparator, threshold, precision, hysteresis, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "superseded-by-edit-rev" + rev);
        return rev;
    }

    /** Select/change the evaluation mode (CROSSING; LEVEL refused). Bumps revision + cancels old PENDING. */
    @Transactional
    public long selectMode(String ruleId, RuleMode mode) {
        RuleDefinitionEntity e = require(ruleId);
        long rev = e.selectMode(mode, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "superseded-by-mode-change-rev" + rev);
        return rev;
    }

    /** Pause: block collection/dispatch AND cancel unsent PENDING intents (evidence preserved). */
    @Transactional
    public void pause(String ruleId) {
        RuleDefinitionEntity e = require(ruleId);
        e.setPaused(true, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "paused");
    }

    /** Resume: keep revision/history, but require a fresh baseline so the first post-resume
     *  observation cannot fire (no replay / no inferred crossing across the pause gap). */
    @Transactional
    public void resume(String ruleId) {
        RuleDefinitionEntity e = require(ruleId);
        e.setPaused(false, clock.instant());
        repo.save(e);
        ruleStates.findByRuleId(ruleId).ifPresent(rs -> { rs.markRebaselineRequired(); ruleStates.save(rs); });
    }

    @Transactional
    public void disable(String ruleId) {
        RuleDefinitionEntity e = require(ruleId);
        e.setEnabled(false, clock.instant());
        repo.save(e);
        outboxTx.cancelPendingForRule(ruleId, "disabled");
    }

    @Transactional(readOnly = true)
    public Optional<RuleDefinitionEntity> find(String ruleId) { return repo.findByRuleId(ruleId); }

    private RuleDefinitionEntity require(String ruleId) {
        return repo.findByRuleId(ruleId)
                .orElseThrow(() -> new IllegalStateException("unknown rule: " + ruleId));
    }
}
