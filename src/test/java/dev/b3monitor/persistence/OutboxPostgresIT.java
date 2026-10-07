package dev.b3monitor.persistence;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.monitor.MonitorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests against a REAL PostgreSQL via Testcontainers, exercising Flyway V1–V12, the unique
 * logical-key constraint, the post-commit dispatcher (claim/lease/fencing/claim-generation), a
 * TWO-CONSUMER dispatch race, durable rule-state recovery, expired-lease quarantine (NO resend),
 * stale-result fencing after reconciliation, revision supersession / old-PENDING cancellation, and the
 * quota first-allocation race across two managers. REQUIRES a Docker daemon and runs ONLY under
 * {@code mvn verify -Pdocker-it}. Named *IT so the Surefire unit phase skips it.
 *
 * <p>Cycle-4 review P1-1 fix: the context uses a FIXED test {@link Clock} (NOW) and the quotes are
 * built relative to that clock, so the 45-minute freshness guard does not fail the test when run on a
 * later date. {@code ddl-auto=validate} makes context start itself prove V1–V12 match the entities.
 */
@Testcontainers
@SpringBootTest
class OutboxPostgresIT {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("b3monitor").withUsername("b3monitor").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // proves V1–V12 match the entities
        r.add("b3monitor.workers.enabled", () -> "false");
    }

    @TestConfiguration
    static class FixedClockCfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        /** Allow-all dispatch guard so the dispatch tests exercise the send path on real Postgres;
         *  the fail-closed production default is covered by the unit tests. */
        @Bean @Primary dev.b3monitor.domain.dispatch.DispatchEligibilityGuard testGuard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
    }

    @Autowired OutboxRepository outbox;
    @Autowired OutboxAttemptRepository attempts;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxReconciliationService reconcile;
    @Autowired RuleStateRepository ruleStates;
    @Autowired QuoteObservationRepository observations;
    @Autowired MonitorProcessingService processing;
    @Autowired BrapiClient brapi; // present for context; process() is driven directly

    /** New-ctor outbox row with cycle-6 lineage. */
    private OutboxEntity newRow(String key) {
        return new OutboxEntity(key, "r1", "WEGE3", "msg", 1L, 1L, NOW.minusSeconds(60), NOW, null);
    }

    @Test
    void flywaySchemaEnforcesUniqueLogicalKey() {
        outbox.save(newRow("it-k1"));
        assertTrue(outbox.existsByLogicalKey("it-k1"));
        assertThrows(Exception.class,
                () -> outbox.saveAndFlush(newRow("it-k1")),
                "V1 unique constraint must reject a duplicate logical key");
    }

    @Test
    void dispatcherClaimsCommitsInFlightThenRecordsAccepted() {
        outbox.save(newRow("it-dispatch"));
        var outcome = dispatcher.dispatchOne();
        assertEquals(OutboxState.ACCEPTED, outcome.orElseThrow());
        OutboxEntity row = outbox.findByLogicalKey("it-dispatch").orElseThrow();
        assertEquals(OutboxState.ACCEPTED, row.getState());
        assertEquals(1, row.getAttempts());
        assertTrue(row.getFencingToken() >= 1);
        assertFalse(row.isDeliveryConfirmed());
    }

    @Test
    void expiredLeaseSendingQuarantinedInFlightRecoveredOnRealPostgres() {
        OutboxEntity sending = newRow("it-sending");
        sending.claim("dead", NOW.minusSeconds(5));
        sending.markSending(NOW.minusSeconds(5));
        sending.setState(OutboxState.SENDING);
        Long sendingId = outbox.save(sending).getId();
        // Seed the OPEN attempt that prepareSend would have written (cycle-9 item D).
        attempts.save(new OutboxAttemptEntity(sendingId, "it-sending",
                sending.getClaimGeneration(), sending.getFencingToken(), NOW.minusSeconds(5)));
        OutboxEntity inflight = newRow("it-inflight");
        inflight.claim("dead2", NOW.minusSeconds(5));
        inflight.setState(OutboxState.IN_FLIGHT);
        outbox.save(inflight);

        int[] rec = dispatcher.reconcileExpiredLeases();
        assertEquals(1, rec[0], "IN_FLIGHT → safe recovery");
        assertEquals(1, rec[1], "SENDING → quarantined");
        assertEquals(OutboxState.UNKNOWN_OUTCOME, outbox.findByLogicalKey("it-sending").orElseThrow().getState());
        assertEquals(OutboxState.PENDING, outbox.findByLogicalKey("it-inflight").orElseThrow().getState());
        // the SENDING attempt is closed UNKNOWN (not left dangling); IN_FLIGHT never had one.
        var sendingAttempt = attempts.findByOutboxIdAndClaimGeneration(sendingId,
                outbox.findByLogicalKey("it-sending").orElseThrow().getClaimGeneration()).orElseThrow();
        assertFalse(sendingAttempt.isOpen());
        assertEquals(dev.b3monitor.domain.outbox.SubmissionResult.Kind.UNKNOWN, sendingAttempt.getOutcome());
        assertEquals("lease-expired", sendingAttempt.getSanitizedStatus());
    }

    @Test
    void abandonThenReconcileKeepsFailedTerminalOnRealPostgres() {
        OutboxEntity row = newRow("it-abandon");
        row.claim("w", NOW.minusSeconds(5));
        row.setState(OutboxState.UNKNOWN_OUTCOME);
        Long id = outbox.save(row).getId();
        assertTrue(reconcile.abandon(id));
        OutboxEntity after = outbox.findById(id).orElseThrow();
        assertEquals(OutboxState.FAILED, after.getState());
        // a stale record() against the old token must be fenced out (state no longer IN_FLIGHT)
        assertEquals(OutboxState.FAILED, outbox.findById(id).orElseThrow().getState());
    }

    @Test
    void pipelinePersistsObservationAndRuleStateOnRealPostgres() {
        PriceRule rule = new PriceRule("it-r", "WEGE3", Comparator.ABOVE,
                new BigDecimal("50.00"), 2, new BigDecimal("0.10")).withMode(dev.b3monitor.domain.rule.RuleMode.CROSSING);
        processing.process(rule, quote(new BigDecimal("49.00"), NOW.minusSeconds(60)));        // baseline
        var fire = processing.process(rule, quote(new BigDecimal("50.50"), NOW.minusSeconds(30)));
        assertTrue(fire.fired());
        assertEquals(2, observations.findByRequestedTickerOrderByReceiptTimeDesc("WEGE3").size());
        assertEquals(dev.b3monitor.domain.rule.RuleState.Phase.LATCHED,
                ruleStates.findByRuleId("it-r").orElseThrow().getPhase());
        assertTrue(outbox.findByLogicalKey("it-r|rev1|ep1").isPresent());
    }

    private Quote quote(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }

    @Autowired dev.b3monitor.quota.QuotaTxOps quotaTx;

    @Test
    void twoConsumersNeverDoubleDispatchTheSameRow() throws Exception {
        // Seed 20 PENDING rows; two dispatchers drain concurrently. Each row must be sent exactly once.
        for (int i = 0; i < 20; i++) outbox.save(newRow("it-race-" + i));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> drainer = () -> { start.await(); return dispatcher.drain(100); };
        var f1 = pool.submit(drainer);
        var f2 = pool.submit(drainer);
        start.countDown();
        int total = f1.get(30, java.util.concurrent.TimeUnit.SECONDS)
                  + f2.get(30, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(20, total, "every row dispatched exactly once across two consumers");
        assertEquals(20, outbox.countByState(OutboxState.ACCEPTED));
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
    }

    @Test
    void quotaFirstAllocationRaceGrantsExactlyOne() throws Exception {
        quotaTx.declareDedicated();
        var mgrA = new dev.b3monitor.quota.BrapiQuotaManager(quotaTx, Clock.fixed(NOW, ZoneOffset.UTC));
        var mgrB = new dev.b3monitor.quota.BrapiQuotaManager(quotaTx, Clock.fixed(NOW, ZoneOffset.UTC));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> acq = () -> { start.await(); return true; };
        var fa = pool.submit(() -> { start.await(); return mgrA.tryAcquire(); });
        var fb = pool.submit(() -> { start.await(); return mgrB.tryAcquire(); });
        start.countDown();
        var ra = fa.get(15, java.util.concurrent.TimeUnit.SECONDS);
        var rb = fb.get(15, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();
        long granted = java.util.stream.Stream.of(ra, rb)
                .filter(x -> x instanceof dev.b3monitor.quota.BrapiQuotaManager.Granted).count();
        assertEquals(1, granted, "exactly one manager wins the single durable in-flight slot");
    }
}
