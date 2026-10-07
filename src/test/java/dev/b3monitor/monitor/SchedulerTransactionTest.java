package dev.b3monitor.monitor;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.WahaOutboundAdapter;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.persistence.*;
import dev.b3monitor.quota.BrapiQuotaManager;
import dev.b3monitor.schedule.MonitorScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * REAL Spring integration test (cycle-4 review P0-1 gate), on H2 — no Docker, runs in the normal
 * {@code mvn test} phase. It drives the ACTUAL path {@code scheduler.tick → pipeline.runOnce →
 * processing.process} with the worker ENABLED and Brapi MOCKED, with NO ambient transaction in the
 * test (asserted via {@link TransactionSynchronizationManager}). It proves the proxy boundary is
 * really applied (not bypassed by self-invocation); atomic commit of observation + rule_state +
 * PENDING outbox; rollback-with-no-orphan when a failure is injected at the outbox enqueue AFTER the
 * rule-state update; no send before commit; concurrent first-creation; and out-of-order replay.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulerTransactionTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class CountingAdapter implements WahaOutboundAdapter {
        final AtomicInteger sends = new AtomicInteger();
        @Override public dev.b3monitor.domain.outbox.SubmissionResult send(AlertIntent intent) {
            sends.incrementAndGet();
            return dev.b3monitor.domain.outbox.SubmissionResult.accepted();
        }
    }

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean @Primary CountingAdapter countingAdapter() { return new CountingAdapter(); }
        @Bean @Primary dev.b3monitor.schedule.TradingSessionCalendar openCalendar() {
            return new dev.b3monitor.schedule.TradingSessionCalendar() {
                @Override public String datasetVersion() { return "test-open"; }
                @Override public SessionStatus statusAt(java.time.Instant at) { return SessionStatus.OPEN; }
            };
        }
        /** Authorize exactly WEGE3 so the worker path can fetch in these tests (the default static
         *  authorization leaves every asset NOT_AUTHORIZED). */
        @Bean @Primary dev.b3monitor.domain.auth.OperationalAuthorization testAuthorization() {
            return rule -> "WEGE3".equals(rule.ticker())
                    ? new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.AUTHORIZED, "test")
                    : new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.NOT_AUTHORIZED, "test");
        }
        @Bean @Primary MonitorScheduler enabledScheduler(MonitorPipeline p, OutboxDispatcher d,
                                BrapiQuotaManager q, dev.b3monitor.schedule.TradingSessionCalendar c,
                                dev.b3monitor.domain.auth.OperationalAuthorization a,
                                dev.b3monitor.domain.rule.RuleSource rs, Clock clock) {
            return new MonitorScheduler(p, d, q, c, a, rs, clock, true);
        }
        /** Allow-all dispatch guard so these transaction-boundary tests exercise the SEND path; the
         *  fail-closed production default (unknown rule) is covered by OutboxDispatcherTest + the
         *  guard's own unit test. */
        @Bean @Primary dev.b3monitor.domain.dispatch.DispatchEligibilityGuard testGuard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
    }

    @MockBean BrapiClient brapi;
    /** Spied so one test can make enqueue() throw mid-transaction, after observation + rule-state writes.
     *  @SpyBean wraps the REAL Spring bean (its repository stays injected). */
    @org.springframework.boot.test.mock.mockito.SpyBean OutboxService outboxServiceSpy;

    @Autowired MonitorScheduler scheduler;
    @Autowired MonitorPipeline pipeline;
    @Autowired MonitorProcessingService processing;
    @Autowired QuoteObservationRepository observations;
    @Autowired RuleStateRepository ruleStates;
    @Autowired OutboxRepository outbox;
    @Autowired CountingAdapter adapter;
    @Autowired BrapiQuotaManager quota;
    @Autowired dev.b3monitor.persistence.RuleAdminService ruleAdmin;
    @Autowired dev.b3monitor.persistence.RuleDefinitionRepository ruleDefs;

    private PriceRule rule() {
        return new PriceRule("rT", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), 2)
                .withMode(dev.b3monitor.domain.rule.RuleMode.CROSSING);
    }
    private Quote wege(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }
    private BrapiClient.FetchResult fr(BigDecimal price, Instant src) {
        return new BrapiClient.FetchResult(wege(price, src), dev.b3monitor.adapter.brapi.QuotaSignal.none());
    }

    @BeforeEach
    void setup() {
        outbox.deleteAll(); ruleStates.deleteAll(); observations.deleteAll(); ruleDefs.deleteAll();
        adapter.sends.set(0);
        reset(brapi);
        org.mockito.Mockito.doCallRealMethod().when(outboxServiceSpy).enqueue(any(AlertIntent.class));
        quota.declareDedicatedQuota();
        // Seed the single rule source with a CROSSING rT/WEGE3 rule so scheduler.tick() collects it.
        ruleAdmin.create("rT", "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        ruleAdmin.selectMode("rT", dev.b3monitor.domain.rule.RuleMode.CROSSING);
    }

    @Test
    void noAmbientTransactionInTest() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "the test holds NO transaction — the proxied process() is what commits");
    }

    @Test
    void workerPathCommitsAtomicallyAndDispatchesPostCommit() throws Exception {
        PriceRule rule = rule();
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("49.00"), NOW.minusSeconds(60)));
        scheduler.tick();
        assertEquals(1, observations.count());
        assertEquals(dev.b3monitor.domain.rule.RuleState.Phase.ARMED,
                ruleStates.findByRuleId("rT").orElseThrow().getPhase());
        assertEquals(0, outbox.count());
        assertEquals(0, adapter.sends.get(), "no send on a non-firing cycle");

        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("50.50"), NOW.minusSeconds(30)));
        var report = scheduler.tick();
        assertEquals(1, report.fired());
        assertEquals(1, outbox.count());
        assertEquals(1, adapter.sends.get(), "exactly one post-commit send");
        assertEquals(OutboxState.ACCEPTED, outbox.findAll().get(0).getState());
    }

    @Test
    void failureAtOutboxEnqueueRollsBackObservationAndStateAndSendsNothing() throws Exception {
        PriceRule rule = rule();
        // baseline committed normally
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("49.00"), NOW.minusSeconds(60)));
        scheduler.tick();
        long obsAfterBaseline = observations.count();
        var phaseAfterBaseline = ruleStates.findByRuleId("rT").orElseThrow().getPhase();
        int sendsBefore = adapter.sends.get();

        // Inject a failure at enqueue(): happens inside process()'s transaction, AFTER the observation
        // save and the rule-state advance. The whole transaction must roll back.
        doThrow(new RuntimeException("injected failure between state update and outbox commit"))
                .when(outboxServiceSpy).enqueue(any(AlertIntent.class));

        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("50.50"), NOW.minusSeconds(30)));
        assertThrows(RuntimeException.class, () -> pipeline.runOnce(rule));

        assertEquals(obsAfterBaseline, observations.count(), "no orphan observation from the rolled-back tx");
        assertEquals(phaseAfterBaseline, ruleStates.findByRuleId("rT").orElseThrow().getPhase(),
                "rule-state advance rolled back (still ARMED from baseline)");
        assertEquals(0, outbox.count(), "no outbox row from the rolled-back tx");
        assertEquals(sendsBefore, adapter.sends.get(), "no send on a rolled-back transaction");
    }

    @Test
    void concurrentFirstCreationYieldsOneConsistentStateNoFire() throws Exception {
        PriceRule rule = rule();
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("49.00"), NOW.minusSeconds(60)));
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            fs.add(pool.submit(() -> { start.await(); try { pipeline.runOnce(rule); } catch (Exception ignored) {} return null; }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(1, ruleStates.findAll().stream().filter(s -> s.getRuleId().equals("rT")).count(),
                "exactly one rule_state row despite concurrent first creation");
        assertEquals(0, outbox.count(), "a concurrent first baseline must not fire");
    }

    @Test
    void outOfOrderReplayDoesNotFireAgainViaWorkerPath() throws Exception {
        PriceRule rule = rule();
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("49.00"), NOW.minusSeconds(120)));
        scheduler.tick();                                   // baseline ARMED
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        scheduler.tick();                                   // fire once
        assertEquals(1, outbox.count());
        // replay an OLDER observation
        when(brapi.fetch("WEGE3")).thenReturn(fr(new BigDecimal("50.90"), NOW.minusSeconds(120)));
        scheduler.tick();
        assertEquals(1, outbox.count(), "out-of-order replay must not fire again");
    }
}
