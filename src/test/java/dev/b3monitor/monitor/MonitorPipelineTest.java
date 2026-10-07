package dev.b3monitor.monitor;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.SimulatedWahaAdapter;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleEvaluator;
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

/**
 * Durable-state proof of the atomic processing step on H2: QuoteValidator → observation persistence
 * → persisted rule_state → RuleEvaluator → idempotent outbox PENDING. Dispatch is NOT exercised here
 * (post-commit; see {@link dev.b3monitor.persistence.OutboxDispatcherTest}). The real
 * fetch→process boundary + transaction advice is covered by {@link SchedulerTransactionIT}.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({OutboxService.class, SimulatedWahaAdapter.class, MonitorPipelineTest.TestBeans.class})
class MonitorPipelineTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class TestBeans {
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
                dev.b3monitor.domain.dispatch.DispatchEligibilityGuard g, Clock c) { return new OutboxTxOps(r, ar, g, c); }
        @Bean MonitorProcessingService processing(QuoteValidator v, RuleEvaluator e,
                                                   QuoteObservationRepository o, RuleStateRepository rs,
                                                   OutboxService ob, OutboxTxOps otx, Clock c) {
            return new MonitorProcessingService(v, e, o, rs, ob, otx, c);
        }
    }

    @Autowired MonitorProcessingService processing;
    @Autowired QuoteObservationRepository observations;
    @Autowired OutboxRepository outbox;
    @Autowired RuleStateRepository ruleStates;

    private PriceRule rule() {
        return new PriceRule("r1", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"))
                .withMode(dev.b3monitor.domain.rule.RuleMode.CROSSING);
    }

    private Quote wege(BigDecimal price, boolean stale, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, stale);
    }

    @Test
    void ineligibleQuoteProducesNoFireButPersistsObservation() {
        var r = processing.process(rule(), wege(new BigDecimal("99.00"), true, NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertFalse(r.eligible());
        assertEquals(1, observations.count(), "observation persisted even when ineligible");
        assertEquals(0, outbox.count(), "no outbox row for ineligible quote");
    }

    @Test
    void baselineThenCrossingFiresOnceAndEnqueuesPending() {
        PriceRule rule = rule();
        assertFalse(processing.process(rule, wege(new BigDecimal("49.00"), false, NOW.minusSeconds(60))).fired());
        assertEquals(dev.b3monitor.domain.rule.RuleState.Phase.ARMED,
                ruleStates.findByRuleId("r1").orElseThrow().getPhase());

        var fire = processing.process(rule, wege(new BigDecimal("50.50"), false, NOW.minusSeconds(30)));
        assertTrue(fire.fired());
        assertEquals(1, outbox.count());
        assertEquals(OutboxState.PENDING, outbox.findAll().get(0).getState(),
                "process enqueues PENDING only; dispatch is post-commit");
        assertEquals(dev.b3monitor.domain.rule.RuleState.Phase.LATCHED,
                ruleStates.findByRuleId("r1").orElseThrow().getPhase());

        var again = processing.process(rule, wege(new BigDecimal("51.00"), false, NOW.minusSeconds(10)));
        assertFalse(again.fired());
        assertEquals(1, outbox.count(), "dedup: no duplicate outbox row while latched");
    }
}
