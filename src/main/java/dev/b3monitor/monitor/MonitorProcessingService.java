package dev.b3monitor.monitor;

import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidation;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleEvaluator;
import dev.b3monitor.domain.rule.RuleOutcome;
import dev.b3monitor.domain.rule.RuleState;
import dev.b3monitor.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.StringJoiner;

/**
 * The DURABLE, atomic half of the monitor cycle, in its OWN Spring-managed bean so the
 * {@code @Transactional} boundary is applied through the proxy (cycle-3 review P0-1: a
 * self-invocation from {@code MonitorPipeline.runOnce} to a {@code @Transactional process} in the
 * SAME object does not intercept the advice — this split fixes that).
 *
 * <p>{@link #process} runs ONE transaction that commits atomically:
 * the quote observation, the advanced {@link RuleStateEntity}, and (on a fresh fire) the idempotent
 * PENDING outbox row. The Brapi fetch is NOT here — it stays outside the transaction, in
 * {@link MonitorPipeline#runOnce}. No send ever happens on this path; dispatch is strictly
 * post-commit ({@link OutboxDispatcher}).
 */
@Service
public class MonitorProcessingService {

    private final QuoteValidator validator;
    private final RuleEvaluator evaluator;
    private final QuoteObservationRepository observations;
    private final RuleStateRepository ruleStates;
    private final OutboxService outbox;
    private final OutboxTxOps outboxTx;
    private final RuleDefinitionRepository ruleDefs;
    private final Clock clock;

    public MonitorProcessingService(QuoteValidator validator, RuleEvaluator evaluator,
                                    QuoteObservationRepository observations, RuleStateRepository ruleStates,
                                    OutboxService outbox, OutboxTxOps outboxTx,
                                    RuleDefinitionRepository ruleDefs, Clock clock) {
        this.validator = validator;
        this.evaluator = evaluator;
        this.observations = observations;
        this.ruleStates = ruleStates;
        this.outbox = outbox;
        this.outboxTx = outboxTx;
        this.ruleDefs = ruleDefs;
        this.clock = clock;
    }

    /**
     * Atomic DB work for one already-fetched quote. Loads/creates persisted rule state, rejects
     * replay/out-of-order, evaluates, persists the observation, advances+saves the rule state, and
     * on a fresh fire enqueues an idempotent PENDING outbox row — all in ONE transaction. If any
     * step throws (including an injected failure between the state update and the outbox enqueue),
     * the whole transaction rolls back: no observation, no state advance, no outbox row, no send.
     */
    @Transactional
    public MonitorPipeline.CycleResult process(PriceRule rule, Quote quote) {
        QuoteValidation v = validator.validate(quote);
        persistObservation(quote, v);   // provenance is persisted even when evaluation is later denied

        // Mode gate (cycle-8 review C.6): a non-operable rule (UNSELECTED, or LEVEL pending Q-19) must
        // not mutate ANY runtime rule state and must never fire. Fail closed before loading/advancing
        // the rule_state. This is defence-in-depth atop the scheduler/registry filters and the
        // RuleEvaluator mode gate.
        if (rule.mode() != dev.b3monitor.domain.rule.RuleMode.CROSSING) {
            return new MonitorPipeline.CycleResult(true, false, false, "NON_OPERABLE_MODE");
        }

        // LIFECYCLE FENCE (cycle-10 item A): the Brapi fetch happened OUTSIDE any transaction against a
        // SNAPSHOT PriceRule. Before mutating rule_state or enqueuing an outbox row, acquire the shared
        // PESSIMISTIC_WRITE lock on the rule_definition row and re-validate the snapshot against the
        // CURRENT committed definition — fail closed on any divergence. An admin mutation that commits
        // during the fetch either (a) locked first, so we observe its result here and refuse, or (b)
        // locks after us and waits, then cancels any PENDING we create. No rule_state mutation, episode
        // consumption, or outbox row is produced on a denied evaluation (the observation above stays, for
        // provenance).
        var defOpt = ruleDefs.findByRuleIdForUpdate(rule.id());
        if (defOpt.isEmpty()) {
            return new MonitorPipeline.CycleResult(true, v.eligible(), false, "STALE_RULE_DEFINITION");
        }
        RuleDefinitionEntity def = defOpt.get();
        String fenceDenial = validateAgainstCurrentDefinition(rule, def);
        if (fenceDenial != null) {
            return new MonitorPipeline.CycleResult(true, v.eligible(), false, fenceDenial);
        }

        RuleStateEntity entity = ruleStates.findByRuleId(rule.id())
                .orElseGet(() -> new RuleStateEntity(rule.id()));

        RuleStateEntity.RevisionVerdict verdict = entity.reconcileRevision(rule.revision());
        if (verdict == RuleStateEntity.RevisionVerdict.STALE) {
            // incoming revision < persisted: do NOT evaluate stale rule params against newer state.
            return new MonitorPipeline.CycleResult(true, v.eligible(), false, "STALE_RULE_REVISION");
        }
        if (verdict == RuleStateEntity.RevisionVerdict.BUMPED) {
            // supersession: cancel all unsent PENDING intents of the superseded revision (keep ACCEPTED/UNKNOWN).
            ruleStates.save(entity);
            outboxTx.cancelSupersededPending(rule.id(), rule.revision(),
                    "superseded-by-rev" + rule.revision());
        }

        if (!entity.isNewerThanProcessed(quote.sourceTime())) {
            // replay / duplicate / out-of-order (or no source time) → no state change, no fire
            ruleStates.save(entity);  // persist a possible revision re-baseline, but not the clock
            return new MonitorPipeline.CycleResult(true, v.eligible(), false, "STALE_OR_REPLAY");
        }

        RuleState state = entity.toDomain();
        var result = evaluator.evaluate(rule, state, quote);

        if (result.outcome() == RuleOutcome.UNKNOWN) {
            ruleStates.save(entity); // revision re-baseline may need persisting; clock NOT advanced
            return new MonitorPipeline.CycleResult(true, false, false, "UNKNOWN");
        }

        // Ordinary-resume rebaseline (cycle-8 review A+B): the FIRST eligible observation after a pause
        // must NOT fire — the FALSE→TRUE transition that may have happened during the unobserved pause
        // gap was never seen, and an existing LATCHED episode is never replayed. The evaluator has
        // already run against the PRESERVED phase and mutated `state` to the correct ARMED/LATCHED; we
        // persist THAT mutated state (cycle-8 review A: the old code rebuilt from the stale UNBASELINED
        // entity and discarded this first eligible comparison), force fired=false, clear the marker, and
        // advance the clock — all atomically.
        if (entity.isRebaselineRequired()) {
            entity.clearRebaselineRequired();
            entity.updateFrom(state, quote.sourceTime(), false, null);
            ruleStates.save(entity);
            return new MonitorPipeline.CycleResult(true, true, false, "REBASELINED_AFTER_RESUME");
        }

        boolean fired = result.fired();
        entity.updateFrom(state, quote.sourceTime(), fired, fired ? clock.instant() : null);
        ruleStates.save(entity);

        if (!fired) {
            return new MonitorPipeline.CycleResult(true, true, false, result.outcome().name());
        }

        String logicalKey = rule.id() + "|rev" + entity.getRuleRevision() + "|ep" + entity.getEpisodeEpoch();
        AlertIntent intent = new AlertIntent(
                logicalKey, rule.id(), rule.ticker(),
                describe(rule, quote), entity.getRuleRevision(), entity.getEpisodeEpoch(),
                quote.sourceTime(), clock.instant(), null /* no hard expiry by default */);
        outbox.enqueue(intent);   // PENDING only, in THIS transaction; dispatch is post-commit
        return new MonitorPipeline.CycleResult(true, true, true, "FIRED");
    }

    /**
     * Fail-closed validation of the fetched SNAPSHOT {@code rule} against the CURRENT committed
     * {@link RuleDefinitionEntity} (held under the lifecycle fence). Returns a typed denial reason, or
     * {@code null} when the snapshot still matches the current definition and evaluation may proceed.
     */
    private String validateAgainstCurrentDefinition(PriceRule rule, RuleDefinitionEntity def) {
        if (!def.isEnabled())  return "RULE_DISABLED_CURRENT";
        if (def.isPaused())    return "RULE_PAUSED_CURRENT";
        if (def.getRevision() != rule.revision()) return "STALE_RULE_DEFINITION";
        if (def.getMode() != dev.b3monitor.domain.rule.RuleMode.CROSSING
                || rule.mode() != dev.b3monitor.domain.rule.RuleMode.CROSSING) {
            return "RULE_DEFINITION_MISMATCH";
        }
        // The immutable revision must correspond to the typed definition the snapshot carries.
        boolean matches =
                def.getTicker().equalsIgnoreCase(rule.ticker())
                && def.getComparator() == rule.comparator()
                && def.getThreshold().compareTo(rule.threshold()) == 0
                && def.getPrecision() == rule.precision()
                && def.getHysteresis().compareTo(rule.hysteresis()) == 0;
        return matches ? null : "RULE_DEFINITION_MISMATCH";
    }

    private void persistObservation(Quote q, QuoteValidation v) {
        StringJoiner sj = new StringJoiner(",");
        v.reasons().forEach(r -> sj.add(r.name()));
        observations.save(new QuoteObservationEntity(
                "brapi", q.requestedTicker(), q.returnedTicker(), q.remapped(),
                QuoteObservationEntity.BRAPI_V2_CONTRACT, q.currency(),
                q.price(), q.previousClose(), q.sourceTime(), q.receiptTime(),
                q.providerStale(), v.eligible(), sj.toString()));
    }

    private String describe(PriceRule rule, Quote q) {
        return "%s crossed %s %s (observed %s; source as-of %s)".formatted(
                rule.ticker(), rule.comparator(), rule.threshold().toPlainString(),
                q.price().toPlainString(), String.valueOf(q.sourceTime()));
    }
}
