package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.*;
import dev.b3monitor.monitor.MonitorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pause/resume lifecycle correctness (cycle-8 review P0-live). Pause cancels unsent PENDING intents
 * and preserves evidence; ordinary resume sets a rebaseline marker so the first eligible post-resume
 * observation re-baselines and cannot fire — no replay of a pre-pause episode, no CROSSING inferred
 * across the unobserved gap.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({OutboxService.class, RuleAdminService.class, PauseResumeLifecycleTest.Beans.class})
class PauseResumeLifecycleTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class Beans {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean QuoteValidator quoteValidator(Clock c) { return new QuoteValidator(c, Duration.ofMinutes(45), Duration.ofMinutes(2)); }
        @Bean RuleEvaluator ruleEvaluator(QuoteValidator v) { return new RuleEvaluator(v); }
        @Bean dev.b3monitor.domain.dispatch.DispatchEligibilityGuard guard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
        @Bean OutboxTxOps outboxTxOps(OutboxRepository r, OutboxAttemptRepository ar,
                dev.b3monitor.domain.dispatch.DispatchEligibilityGuard g, RuleDefinitionRepository rd, Clock c) { return new OutboxTxOps(r, ar, g, rd, c); }
        @Bean MonitorProcessingService processing(QuoteValidator v, RuleEvaluator e,
                QuoteObservationRepository o, RuleStateRepository rs, OutboxService ob, OutboxTxOps otx, RuleDefinitionRepository rd, Clock c) {
            return new MonitorProcessingService(v, e, o, rs, ob, otx, rd, c);
        }
    }

    @Autowired RuleAdminService admin;
    @Autowired MonitorProcessingService processing;
    @Autowired RuleDefinitionRepository ruleDefs;
    @Autowired RuleStateRepository ruleStates;
    @Autowired OutboxRepository outbox;

    private PriceRule crossing(long rev) {
        return new PriceRule("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), rev, RuleMode.CROSSING);
    }
    private Quote q(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }
    private long seedCrossingRule() {
        admin.create("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        return admin.selectMode("r1", RuleMode.CROSSING);   // rev 2
    }

    @Test
    void pendingBeforePauseIsCancelledAndNeverSentAfterResume() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        var fire = processing.process(crossing(rev), q(new BigDecimal("50.50"), NOW.minusSeconds(90))); // fire → PENDING
        assertTrue(fire.fired());
        assertEquals(1, outbox.countByState(OutboxState.PENDING));

        admin.pause("r1");
        assertEquals(0, outbox.countByState(OutboxState.PENDING), "pause cancels unsent PENDING");
        assertEquals(1, outbox.countByState(OutboxState.CANCELLED));

        admin.resume("r1");
        // the cancelled intent stays cancelled (never resurrected)
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
        assertEquals(1, outbox.countByState(OutboxState.CANCELLED));
    }

    @Test
    void armedBeforePauseFirstTruePostResumeDoesNotFire() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        assertEquals(RuleState.Phase.ARMED, ruleStates.findByRuleId("r1").orElseThrow().getPhase());

        admin.pause("r1");
        admin.resume("r1");
        assertTrue(ruleStates.findByRuleId("r1").orElseThrow().isRebaselineRequired());

        // first post-resume observation is TRUE but must only re-baseline, not fire
        var r = processing.process(crossing(rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired(), "first post-resume TRUE must not fire (gap not observed)");
        assertEquals("REBASELINED_AFTER_RESUME", r.detail());
        assertEquals(0, outbox.count());
        assertFalse(ruleStates.findByRuleId("r1").orElseThrow().isRebaselineRequired(), "marker cleared");

        // a subsequent genuine FALSE→TRUE after the fresh baseline DOES fire
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(30))); // back below → ARMED
        var r2 = processing.process(crossing(rev), q(new BigDecimal("50.80"), NOW.minusSeconds(10)));
        assertTrue(r2.fired(), "a real crossing after rebaseline fires");
    }

    @Test
    void latchedBeforePauseFirstTruePostResumeDoesNotReplay() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        processing.process(crossing(rev), q(new BigDecimal("50.50"), NOW.minusSeconds(150))); // fire → LATCHED
        outbox.deleteAll();                                 // ignore the first episode's row for this assertion

        admin.pause("r1");
        admin.resume("r1");
        var r = processing.process(crossing(rev), q(new BigDecimal("51.00"), NOW.minusSeconds(60)));
        assertFalse(r.fired(), "LATCHED + first post-resume TRUE must not replay");
        assertEquals(0, outbox.count());
    }

    @Test
    void rebaselineMarkerSurvivesRestart() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(120)));
        admin.pause("r1");
        admin.resume("r1");
        // "restart": reload the row fresh from the DB
        assertTrue(ruleStates.findByRuleId("r1").orElseThrow().isRebaselineRequired(),
                "rebaseline marker is persisted across restart");
    }

    /**
     * Cycle-9 item A — the exact reproducer the review required. ARMED before pause; resume; first
     * eligible post-resume is FALSE (establishes the fresh ARMED baseline, does not fire); the SECOND
     * eligible is an actually-observed FALSE→TRUE crossing and fires once. The pre-fix bug discarded
     * the first eligible comparison (persisting the stale UNBASELINED entity), so the second only
     * baselined and the crossing was lost.
     */
    @Test
    void armedPauseResume_firstFalseThenTrue_firesOnSecondEligible() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        assertEquals(RuleState.Phase.ARMED, ruleStates.findByRuleId("r1").orElseThrow().getPhase());

        admin.pause("r1");
        admin.resume("r1");

        // first eligible post-resume = FALSE → establishes fresh ARMED baseline, no fire, marker cleared
        var first = processing.process(crossing(rev), q(new BigDecimal("49.50"), NOW.minusSeconds(120)));
        assertFalse(first.fired(), "first post-resume FALSE does not fire");
        assertEquals("REBASELINED_AFTER_RESUME", first.detail());
        var rs = ruleStates.findByRuleId("r1").orElseThrow();
        assertFalse(rs.isRebaselineRequired(), "marker consumed by the first eligible observation");
        assertEquals(RuleState.Phase.ARMED, rs.getPhase(), "fresh baseline is ARMED (price below threshold)");
        assertEquals(0, outbox.count());

        // second eligible = observed FALSE→TRUE crossing → fires exactly once
        var second = processing.process(crossing(rev), q(new BigDecimal("50.60"), NOW.minusSeconds(60)));
        assertTrue(second.fired(), "the second eligible is a real observed crossing and fires");
        assertEquals(1, outbox.countByState(OutboxState.PENDING));
    }

    /**
     * Cycle-9 item B — ordinary resume preserves the LATCHED episode (CONTRACTS: resume keeps
     * latch/episode state). The pre-fix markRebaselineRequired() forced phase=UNBASELINED, destroying
     * the latch; now the phase is preserved and the first eligible observation evaluates against it.
     */
    @Test
    void latchedBeforePause_isNotConvertedToUnbaselinedByResume() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        processing.process(crossing(rev), q(new BigDecimal("50.50"), NOW.minusSeconds(150))); // fire → LATCHED
        assertEquals(RuleState.Phase.LATCHED, ruleStates.findByRuleId("r1").orElseThrow().getPhase());
        long epochBefore = ruleStates.findByRuleId("r1").orElseThrow().getEpisodeEpoch();

        admin.pause("r1");
        admin.resume("r1");
        // latch is NOT destroyed merely by resume (marker set, phase preserved)
        var rs = ruleStates.findByRuleId("r1").orElseThrow();
        assertEquals(RuleState.Phase.LATCHED, rs.getPhase(), "resume preserves the LATCHED episode");
        assertTrue(rs.isRebaselineRequired());

        // first eligible TRUE after resume: stays LATCHED, no replay, no new episode minted
        outbox.deleteAll();
        var r = processing.process(crossing(rev), q(new BigDecimal("51.00"), NOW.minusSeconds(60)));
        assertFalse(r.fired(), "LATCHED + first post-resume TRUE must not replay");
        var after = ruleStates.findByRuleId("r1").orElseThrow();
        assertEquals(RuleState.Phase.LATCHED, after.getPhase());
        assertEquals(epochBefore, after.getEpisodeEpoch(), "resume does not mint a new episode epoch");
        assertEquals(0, outbox.count());
    }

    /** Cycle-9 item B — an UNKNOWN (ineligible) observation after resume does NOT consume the marker
     *  and does NOT touch the latch. */
    @Test
    void unknownAfterResumePreservesMarkerAndLatch() {
        long rev = seedCrossingRule();
        processing.process(crossing(rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        processing.process(crossing(rev), q(new BigDecimal("50.50"), NOW.minusSeconds(150))); // LATCHED
        admin.pause("r1");
        admin.resume("r1");

        // a stale quote (ineligible) → UNKNOWN: marker and latch must survive untouched
        Quote stale = new Quote("WEGE3", "WEGE3", false, "BRL", new BigDecimal("51.00"), null, null,
                NOW.minusSeconds(60), NOW, true /* providerStale */);
        var r = processing.process(crossing(rev), stale);
        assertEquals("UNKNOWN", r.detail());
        var rs = ruleStates.findByRuleId("r1").orElseThrow();
        assertTrue(rs.isRebaselineRequired(), "UNKNOWN must not consume the rebaseline marker");
        assertEquals(RuleState.Phase.LATCHED, rs.getPhase(), "UNKNOWN must not touch the latch");
    }
}
