package dev.b3monitor.monitor;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleEvaluator;
import dev.b3monitor.domain.rule.RuleState;
import dev.b3monitor.domain.outbox.SimulatedWahaAdapter;
import dev.b3monitor.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
        @Bean OutboxTxOps outboxTxOps(OutboxRepository r,
                dev.b3monitor.domain.dispatch.DispatchEligibilityGuard g, Clock c) { return new OutboxTxOps(r, g, c); }
        @Bean MonitorProcessingService processing(QuoteValidator v, RuleEvaluator e,
                                                  QuoteObservationRepository o, RuleStateRepository rs,
                                                  OutboxService ob, OutboxTxOps otx, Clock c) {
            return new MonitorProcessingService(v, e, o, rs, ob, otx, c);
        }
    }

    @Autowired MonitorProcessingService pipeline;
    @Autowired OutboxRepository outbox;
    @Autowired RuleStateRepository ruleStates;

    private final PriceRule rule =
            new PriceRule("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));

    private Quote at(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }

    @Test
    void outOfOrderReplayDoesNotFireAgain() {
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
        pipeline.process(rule, at(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        pipeline.process(rule, at(new BigDecimal("50.50"), NOW.minusSeconds(90)));  // fire, LATCHED
        assertEquals(1, outbox.count());

        // New revision with a different threshold: re-baseline, must NOT auto-fire even though above old.
        PriceRule rev2 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("55.00"), 2, new BigDecimal("0.10"), 2);
        var afterBump = pipeline.process(rev2, at(new BigDecimal("54.00"), NOW.minusSeconds(60)));
        assertFalse(afterBump.fired(), "revision bump re-baselines, never auto-fires");
        assertEquals(1, outbox.count());
    }

    // ----- cycle-6 P0-3: stale/lower revision fails closed; bump cancels old PENDING -----

    @Test
    void lowerIncomingRevisionIsRejectedStaleNoEvaluation() {
        // establish revision 3 state
        PriceRule rev3 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("50.00"), 2, new BigDecimal("0.10"), 3);
        pipeline.process(rev3, at(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED @ rev3
        var armedPhase = ruleStates.findByRuleId("r1").orElseThrow().getPhase();

        // a STALE lower revision arrives (rev1) that WOULD cross — must be rejected, no fire, no mutation
        PriceRule rev1 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("40.00"), 2, new BigDecimal("0.10"), 1);
        var stale = pipeline.process(rev1, at(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(stale.fired());
        assertEquals("STALE_RULE_REVISION", stale.detail());
        assertEquals(3, ruleStates.findByRuleId("r1").orElseThrow().getRuleRevision(), "revision not downgraded");
        assertEquals(armedPhase, ruleStates.findByRuleId("r1").orElseThrow().getPhase(), "state not mutated");
        assertEquals(0, outbox.count());
    }

    @Test
    void bumpCancelsOldPendingButPreservesAcceptedHistory() {
        // rev1 fires → a PENDING outbox row for rev1
        pipeline.process(rule, at(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        pipeline.process(rule, at(new BigDecimal("50.50"), NOW.minusSeconds(150))); // fire → PENDING rev1
        var rev1Row = outbox.findAll().get(0);
        assertEquals(dev.b3monitor.domain.outbox.OutboxState.PENDING, rev1Row.getState());
        assertEquals(1, rev1Row.getRuleRevision());

        // bump to rev2 → the unsent rev1 PENDING must be CANCELLED (superseded), not erased
        PriceRule rev2 = new PriceRule("r1", "WEGE3", Comparator.ABOVE,
                new BigDecimal("55.00"), 2, new BigDecimal("0.10"), 2);
        pipeline.process(rev2, at(new BigDecimal("54.00"), NOW.minusSeconds(120)));
        var afterBump = outbox.findByLogicalKey(rev1Row.getLogicalKey()).orElseThrow();
        assertEquals(dev.b3monitor.domain.outbox.OutboxState.CANCELLED, afterBump.getState(),
                "old-revision PENDING is cancelled on supersession");
        assertNotNull(afterBump.getSuppressionReason());
    }
}
