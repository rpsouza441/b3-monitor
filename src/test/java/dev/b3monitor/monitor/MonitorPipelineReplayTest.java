package dev.b3monitor.monitor;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleEvaluator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.domain.rule.RuleState;
import dev.b3monitor.domain.outbox.SimulatedWahaAdapter;
import dev.b3monitor.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Independent-review P0-3 regressions: replay / out-of-order / restart.
 * The prior design double-fired on a replayed older observation
 * ({@code out_of_order_replay_fires_again=true}); with persisted
 * {@code last_processed_source_time} it must not.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({OutboxService.class, SimulatedWahaAdapter.class, MonitorPipelineReplayTest.Beans.class})
class MonitorPipelineReplayTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class Beans {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean QuoteValidator quoteValidator(Clock c) {
            return new QuoteValidator(c, Duration.ofMinutes(45), Duration.ofMinutes(2));
        }
        @Bean RuleEvaluator ruleEvaluator(QuoteValidator v) { return new RuleEvaluator(v); }
        @Bean dev.b3monitor.domain.dispatch.DispatchEligibilityGuard guard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
        @Bean OutboxTxOps outboxTxOps(OutboxRepository r, OutboxAttemptRepository ar,
                dev.b3monitor.domain.dispatch.DispatchEligibilityGuard g, RuleDefinitionRepository rd, Clock c) { return new OutboxTxOps(r, ar, g, rd, c); }
        @Bean MonitorProcessingService processing(QuoteValidator v, RuleEvaluator e,
                                                  QuoteObservationRepository o, RuleStateRepository rs,
                                                  OutboxService ob, OutboxTxOps otx, RuleDefinitionRepository rd, Clock c) {
            return new MonitorProcessingService(v, e, o, rs, ob, otx, rd, c);
        }
    }

    @Autowired MonitorProcessingService pipeline;
    @Autowired OutboxRepository outbox;
    @Autowired RuleStateRepository ruleStates;
    @Autowired RuleDefinitionRepository ruleDefs;

    private final PriceRule rule =
            new PriceRule("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), 2)
                    .withMode(RuleMode.CROSSING);

    /** Seed a rule_definition at EXACTLY the snapshot revision + CROSSING, so the cycle-10 fence passes
     *  (create→rev1 UNSELECTED, selectMode→rev2, applyEdit up to target; target must be >= 2). */
    private void seedDef(PriceRule r) {
        ruleDefs.findByRuleId(r.id()).ifPresent(ruleDefs::delete);
        ruleDefs.flush();
        var e = new RuleDefinitionEntity(r.id(), r.ticker(), r.comparator(), r.threshold(), r.precision(), r.hysteresis(), NOW);
        e.selectMode(RuleMode.CROSSING, NOW);
        while (e.getRevision() < r.revision()) { e.applyEdit(r.comparator(), r.threshold(), r.precision(), r.hysteresis(), NOW); }
        ruleDefs.saveAndFlush(e);
    }

    private Quote at(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }

    @Test
    void outOfOrderReplayDoesNotFireAgain() {
        seedDef(rule);
        Instant t1 = NOW.minusSeconds(120);
        Instant t2 = NOW.minusSeconds(60);

        pipeline.process(rule, at(new BigDecimal("49.00"), t1));   // baseline ARMED
        var cross = pipeline.process(rule, at(new BigDecimal("50.50"), t2)); // fire once
        assertTrue(cross.fired());
        assertEquals(1, outbox.count());

        // REPLAY the older FALSE observation (t1) — must be rejected, no rearm, no new fire.
        var replayOld = pipeline.process(rule, at(new BigDecimal("49.00"), t1));
        assertFalse(replayOld.fired());
        assertEquals("STALE_OR_REPLAY", replayOld.detail());

        // Re-send a TRUE at an OLDER time than last processed — must also be rejected.
        var replayTrue = pipeline.process(rule, at(new BigDecimal("50.90"), t1));
        assertFalse(replayTrue.fired(), "out-of-order replay must NOT fire again");
        assertEquals(1, outbox.count(), "still exactly one alert for the episode");
    }

    @Test
    void duplicateSameTimestampIsRejected() {
        seedDef(rule);
        Instant t = NOW.minusSeconds(60);
        pipeline.process(rule, at(new BigDecimal("49.00"), t.minusSeconds(1))); // baseline
        pipeline.process(rule, at(new BigDecimal("50.50"), t));                  // fire
        var dup = pipeline.process(rule, at(new BigDecimal("50.50"), t));        // same source time
        assertFalse(dup.fired());
        assertEquals("STALE_OR_REPLAY", dup.detail(), "equal sourceTime is not strictly newer");
        assertEquals(1, outbox.count());
    }

    @Test
    void stateSurvivesPipelineRestart() {
        seedDef(rule);
        Instant t1 = NOW.minusSeconds(120);
        pipeline.process(rule, at(new BigDecimal("49.00"), t1)); // baseline ARMED, persisted

        // Simulate restart: the in-memory state is gone, only the DB row remains.
        RuleStateEntity reloaded = ruleStates.findByRuleId("r1").orElseThrow();
        assertEquals(RuleState.Phase.ARMED, reloaded.getPhase());
        assertEquals(t1, reloaded.getLastProcessedSourceTime());

        // A crossing AFTER restart fires exactly once (state was recovered, not re-baselined).
        var cross = pipeline.process(rule, at(new BigDecimal("50.50"), NOW.minusSeconds(30)));
        assertTrue(cross.fired(), "recovered ARMED state fires on the post-restart crossing");
        assertEquals(1, outbox.count());
    }

    @Test
    void revisionBumpReBaselinesWithoutFiring() {
        seedDef(rule);                                                              // def rev2
        pipeline.process(rule, at(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        pipeline.process(rule, at(new BigDecimal("50.50"), NOW.minusSeconds(90)));  // fire, LATCHED
        assertEquals(1, outbox.count());

        // New HIGHER revision with a different threshold: re-baseline, must NOT auto-fire even though
        // above the old threshold. The def is advanced to the new revision (fence passes).
        PriceRule rev3 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("55.00"), 2, new BigDecimal("0.10"), 3).withMode(RuleMode.CROSSING);
        seedDef(rev3);
        var afterBump = pipeline.process(rev3, at(new BigDecimal("54.00"), NOW.minusSeconds(60)));
        assertFalse(afterBump.fired(), "revision bump re-baselines, never auto-fires");
        assertEquals(1, outbox.count());
    }

    // ----- cycle-6 P0-3 (reconcile) + cycle-10 fence: stale definition fails closed -----

    @Test
    void lowerIncomingRevisionIsRejectedStaleNoEvaluation() {
        // establish revision 3 state + a matching def
        PriceRule rev3 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("50.00"), 2, new BigDecimal("0.10"), 3).withMode(RuleMode.CROSSING);
        seedDef(rev3);
        pipeline.process(rev3, at(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED @ rev3
        var armedPhase = ruleStates.findByRuleId("r1").orElseThrow().getPhase();

        // a STALE lower revision (rev2) that WOULD cross — the cycle-10 lifecycle fence rejects it
        // (current def is rev3) BEFORE any evaluation: no fire, no mutation, no outbox.
        PriceRule rev2 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("40.00"), 2, new BigDecimal("0.10"), 2).withMode(RuleMode.CROSSING);
        var stale = pipeline.process(rev2, at(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(stale.fired());
        assertEquals("STALE_RULE_DEFINITION", stale.detail(), "fence rejects a snapshot below the current revision");
        assertEquals(3, ruleStates.findByRuleId("r1").orElseThrow().getRuleRevision(), "revision not downgraded");
        assertEquals(armedPhase, ruleStates.findByRuleId("r1").orElseThrow().getPhase(), "state not mutated");
        assertEquals(0, outbox.count());
    }

    @Test
    void bumpCancelsOldPendingButPreservesAcceptedHistory() {
        seedDef(rule);                                                              // def rev2
        // rev2 fires → a PENDING outbox row for rev2
        pipeline.process(rule, at(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        pipeline.process(rule, at(new BigDecimal("50.50"), NOW.minusSeconds(150))); // fire → PENDING rev2
        var oldRow = outbox.findAll().get(0);
        assertEquals(dev.b3monitor.domain.outbox.OutboxState.PENDING, oldRow.getState());
        assertEquals(2, oldRow.getRuleRevision());

        // bump to rev3 → the unsent old PENDING must be CANCELLED (superseded), not erased
        PriceRule rev3 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("55.00"), 2, new BigDecimal("0.10"), 3).withMode(RuleMode.CROSSING);
        seedDef(rev3);
        pipeline.process(rev3, at(new BigDecimal("54.00"), NOW.minusSeconds(120)));
        var afterBump = outbox.findByLogicalKey(oldRow.getLogicalKey()).orElseThrow();
        assertEquals(dev.b3monitor.domain.outbox.OutboxState.CANCELLED, afterBump.getState(),
                "old-revision PENDING is cancelled on supersession");
        assertNotNull(afterBump.getSuppressionReason());
    }
}
