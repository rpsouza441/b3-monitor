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
    private final Clock clock;

    public MonitorProcessingService(QuoteValidator validator, RuleEvaluator evaluator,
                                    QuoteObservationRepository observations, RuleStateRepository ruleStates,
                                    OutboxService outbox, OutboxTxOps outboxTx, Clock clock) {
        this.validator = validator;
        this.evaluator = evaluator;
        this.observations = observations;
        this.ruleStates = ruleStates;
        this.outbox = outbox;
        this.outboxTx = outboxTx;
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
        persistObservation(quote, v);

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
