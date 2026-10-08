package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.rule.*;
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
 * REAL-fence PostgreSQL IT (cycle-12 item D). Unlike {@link OutboxPostgresIT} (transport-only, allow-all
 * guard), this exercises the ACTUAL lifecycle/eligibility fence end-to-end on Postgres using the REAL
 * {@code FailClosedDispatchEligibilityGuard} + {@code PersistentRuleRegistry} + {@code RuleAdminService},
 * with WEGE3 authorized so the fence can reach {@code prepareSend}. Proves PESSIMISTIC_WRITE row-lock
 * serialization between admin mutations and dispatch:
 * <ol>
 *   <li>pause wins before prepareSend ⇒ zero send authority;</li>
 *   <li>prepareSend wins before pause ⇒ SENDING authority preserved;</li>
 *   <li>stale edit/mode revision before prepareSend ⇒ no NEW send authority after the bump commits.</li>
 * </ol>
 * Cycle-13 added TRUE concurrent races (CyclicBarrier + 2 threads + independent transactions). Cycle-14
 * hardened them: race 1 no longer swallows exceptions (only an exact optimistic-lock conflict is legal;
 * anything else fails via {@code Future.get}); race 4 proves the lock-wait at the DATABASE level via
 * {@code pg_blocking_pids()} and guards against a never-scheduled competitor with a {@code competitorEntered}
 * latch; all executors are closed in finally. No DB transaction spans adapter I/O. REQUIRES Docker; runs
 * only under {@code mvn verify -Pdocker-it}.
 */
@Testcontainers
@SpringBootTest
class LifecycleFencePostgresIT {

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("b3monitor.workers.enabled", () -> "false");
    }

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        /** Authorize WEGE3 so the REAL guard can reach prepareSend; everything else stays fail-closed. */
        @Bean @Primary dev.b3monitor.domain.auth.OperationalAuthorization testAuthorization() {
            return rule -> "WEGE3".equals(rule.ticker())
                    ? new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.AUTHORIZED, "test")
                    : new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.NOT_AUTHORIZED, "test");
        }
        @Bean @Primary CountingAdapter countingAdapter() { return new CountingAdapter(); }
    }

    static class CountingAdapter implements dev.b3monitor.domain.outbox.WahaOutboundAdapter {
        final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        @Override public dev.b3monitor.domain.outbox.SubmissionResult send(dev.b3monitor.domain.outbox.AlertIntent i) {
            sends.incrementAndGet();
            return dev.b3monitor.domain.outbox.SubmissionResult.accepted();
        }
    }

    @Autowired dev.b3monitor.persistence.RuleAdminService admin;
    @Autowired MonitorProcessingService processing;
    @Autowired OutboxTxOps outboxTx;
    @Autowired OutboxRepository outbox;
    @Autowired dev.b3monitor.domain.outbox.WahaOutboundAdapter adapter;

    private long seedCrossing(String id) {
        if (admin.find(id).isEmpty()) {
            admin.create(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        }
        return admin.selectMode(id, RuleMode.CROSSING);
    }
    private PriceRule snap(String id, long rev) {
        return new PriceRule(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), rev)
                .withMode(RuleMode.CROSSING);
    }
    private Quote q(BigDecimal p, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", p, null, null, src, NOW, false);
    }
    private int sends() { return ((CountingAdapter) adapter).sends.get(); }

    private OutboxTxOps.Claim seedPendingAndClaim(String id, long rev) {
        processing.process(snap(id, rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180)));
        processing.process(snap(id, rev), q(new BigDecimal("50.50"), NOW.minusSeconds(120)));
        return outboxTx.claimNext("it-worker");
    }

    @Test
    void pauseWinsBeforePrepareSendZeroAdapterCalls() {
        long rev = seedCrossing("pg-e1");
        var claim = seedPendingAndClaim("pg-e1", rev);
        int before = sends();
        admin.pause("pg-e1");
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.DENIED_TERMINAL, prepared.outcome());
        assertEquals(before, sends());
    }

    @Test
    void prepareSendWinsBeforePauseAuthorityPreserved() {
        long rev = seedCrossing("pg-e2");
        var claim = seedPendingAndClaim("pg-e2", rev);
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome());
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
        admin.pause("pg-e2");
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
    }

    @Test
    void staleRevisionIntentCannotGetSendAuthority() {
        long rev = seedCrossing("pg-e3");
        var claim = seedPendingAndClaim("pg-e3", rev);
        admin.edit("pg-e3", rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10"));
        int before = sends();
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertNotEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome());
        assertEquals(before, sends());
    }

    @Test
    void pauseVsProcessSerializedNoStaleOutbox() {
        long rev = seedCrossing("pg-proc");
        admin.pause("pg-proc");
        var r = processing.process(snap("pg-proc", rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("RULE_PAUSED_CURRENT", r.detail());
        assertEquals(0, outbox.findByRuleIdAndState("pg-proc", OutboxState.PENDING).size());
    }

    // ===== cycle-13 P1-B: TRUE concurrent races (CyclicBarrier + 2 threads + independent tx) =====

    @Autowired RuleDefinitionRepository ruleDefs;
    @Autowired org.springframework.transaction.PlatformTransactionManager txm;

    /** Race 1 — pause vs process from the same pre-race state. Both threads start together; whichever
     *  acquires the PESSIMISTIC_WRITE fence first wins, and the invariant must hold regardless of order:
     *  a committed pause leaves NO valid unsent PENDING behind. The one legal in-race exception is an
     *  OPTIMISTIC-lock conflict (process's rule-state write losing to the pause's revision bump); it is
     *  asserted by its exact type. Any other SQL/deadlock/programming error FAILS the test — nothing is
     *  swallowed, and the process thread's result is observed via {@code Future.get(timeout)}. */
    @Test
    void race_pauseVsProcess_noStalePendingSurvivesPause() throws Exception {
        long rev = seedCrossing("pg-r1");
        processing.process(snap("pg-r1", rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var fProcess = pool.submit(() -> { barrier.await();
                processing.process(snap("pg-r1", rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
                return null; });
            var fPause = pool.submit(() -> { barrier.await(); admin.pause("pg-r1"); return null; });
            try {
                fProcess.get(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException ee) {
                // The ONLY legal in-race failure for the losing process thread is an optimistic-lock
                // conflict against the concurrent revision bump. Assert its exact type; re-throw anything else.
                Throwable cause = ee.getCause();
                boolean optimistic = cause instanceof org.springframework.orm.ObjectOptimisticLockingFailureException
                        || cause instanceof org.springframework.dao.OptimisticLockingFailureException
                        || (cause != null && cause.getClass().getName().contains("OptimisticLock"));
                if (!optimistic) throw ee; // SQL/deadlock/programming errors MUST fail the test
            }
            fPause.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, outbox.findByRuleIdAndState("pg-r1", OutboxState.PENDING).size(),
                "a committed pause leaves no valid unsent PENDING, regardless of race order");
    }

    /** Race 2 — pause vs prepareSend over a real claimed row, started together. The outcome must be one
     *  of the two legal orders: either prepareSend is DENIED (pause won) with zero adapter calls, or it
     *  is AUTHORIZED (SENDING committed) and pause did not rewrite it as unsent. Never both. */
    @Test
    void race_pauseVsPrepareSend_exactlyOneLegalOutcome() throws Exception {
        long rev = seedCrossing("pg-r2");
        var claim = seedPendingAndClaim("pg-r2", rev);
        int before = sends();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        OutboxTxOps.Prepared prepared;
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var fPrep = pool.submit(() -> { barrier.await();
                return outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW); });
            var fPause = pool.submit(() -> { barrier.await(); admin.pause("pg-r2"); return null; });
            prepared = fPrep.get(20, java.util.concurrent.TimeUnit.SECONDS);
            fPause.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        OutboxState finalState = outbox.findById(claim.rowId()).orElseThrow().getState();
        if (prepared.outcome() == OutboxTxOps.PrepareOutcome.AUTHORIZED) {
            assertEquals(OutboxState.SENDING, finalState, "prepareSend won → SENDING authority preserved after pause");
        } else {
            assertEquals(before, sends(), "pause won → zero adapter calls, no SENDING authority");
            assertNotEquals(OutboxState.SENDING, finalState);
        }
    }

    /** Race 3 — a revision bump (edit) vs prepareSend over the OLD-revision claim, started together.
     *  The invariant is NOT that a pre-edit authorization is "stale": if prepareSend wins the lock before
     *  the edit commits, its authority was VALID at that linearization point (the revision it saw was the
     *  then-current one). The real invariant is forward-looking: once the revision bump WINS/commits, the
     *  OLD revision can receive NO NEW send authority — a re-prepare of the old claim is denied. */
    @Test
    void race_editVsPrepareSend_oldRevisionGetsNoNewAuthorityAfterBump() throws Exception {
        long rev = seedCrossing("pg-r3");
        var claim = seedPendingAndClaim("pg-r3", rev);
        int before = sends();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        OutboxTxOps.Prepared prepared;
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var fEdit = pool.submit(() -> { barrier.await();
                admin.edit("pg-r3", rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10")); return null; });
            var fPrep = pool.submit(() -> { barrier.await();
                return outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW); });
            fEdit.get(20, java.util.concurrent.TimeUnit.SECONDS);
            prepared = fPrep.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        if (prepared.outcome() == OutboxTxOps.PrepareOutcome.AUTHORIZED) {
            // prepareSend won the lock first: it authorized the revision that was current AT THAT INSTANT —
            // a legitimate pre-edit authorization, not a stale one. The forward-looking invariant: now that
            // the edit has committed, re-preparing the OLD claim yields NO new authority.
            var after = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
            assertNotEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, after.outcome(),
                    "after the revision bump commits, the old revision gets no NEW send authority");
        } else {
            assertEquals(before, sends(), "the edit won the lock first; the old-revision intent got no send authority");
        }
    }

    /** Race 4 — EXPLICIT locking proof with a false-positive guard. Thread A opens a transaction, takes
     *  the PESSIMISTIC_WRITE row lock and holds it pinned by a latch. Thread B counts down
     *  {@code competitorEntered} IMMEDIATELY before invoking the production pause path, so the main thread
     *  only tests the blocked state AFTER the competitor is known to have started — a competitor that was
     *  never scheduled can no longer produce a false pass. The lock-wait is then proven at the DATABASE
     *  level via {@code pg_blocking_pids()}: the holder's backend pid must appear among the pids blocking
     *  the competitor's backend. The {@code Thread.sleep} is NOT the race coordinator — the latch is; the
     *  sleep only lets the competitor's statement reach the lock-wait queue before we probe it. All
     *  executors are closed in finally and no exception is swallowed. */
    @Autowired javax.sql.DataSource dataSource;

    @Test
    void race_pessimisticLockActuallyBlocksCompetitor() throws Exception {
        seedCrossing("pg-r4");
        var tt = new org.springframework.transaction.support.TransactionTemplate(txm);
        var lockAcquired = new java.util.concurrent.CountDownLatch(1);
        var holderPid = new java.util.concurrent.atomic.AtomicLong(-1);
        var competitorEntered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pauseReturnedAt = new java.util.concurrent.atomic.AtomicReference<Long>();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<?> holder = null, competitor = null;
        try {
            // A: open a tx, capture its backend pid, take the row lock, signal, hold until released.
            holder = pool.submit(() -> tt.execute(s -> {
                try (var c = dataSource.getConnection(); var st = c.createStatement();
                     var rs = st.executeQuery("select pg_backend_pid()")) {
                    rs.next(); holderPid.set(rs.getLong(1));
                } catch (java.sql.SQLException e) { throw new RuntimeException(e); }
                ruleDefs.findByRuleIdForUpdate("pg-r4").orElseThrow();   // PESSIMISTIC_WRITE
                lockAcquired.countDown();
                try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                return null;
            }));
            assertTrue(lockAcquired.await(5, java.util.concurrent.TimeUnit.SECONDS), "holder acquired the lock");

            // B: signal entry IMMEDIATELY before the production pause call, so we never test "blocked"
            // against a competitor that was simply never scheduled.
            competitor = pool.submit(() -> {
                competitorEntered.countDown();
                admin.pause("pg-r4");                 // production path — blocks on the held row lock
                pauseReturnedAt.set(System.nanoTime());
                return null;
            });
            assertTrue(competitorEntered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "competitor thread actually started the production pause path");

            // let the competitor's UPDATE reach the lock-wait queue, then PROVE the block at the DB level.
            Thread.sleep(500);   // not a coordinator — only lets the blocked statement enqueue
            assertFalse(competitor.isDone(), "the competing pause BLOCKS while the fence lock is held");
            boolean blockedByHolder = false;
            try (var c = dataSource.getConnection(); var ps = c.prepareStatement(
                    "select count(*) from pg_stat_activity a " +
                    "where a.wait_event_type = 'Lock' and ? = any(pg_blocking_pids(a.pid))")) {
                ps.setLong(1, holderPid.get());
                try (var rs = ps.executeQuery()) { rs.next(); blockedByHolder = rs.getLong(1) >= 1; }
            }
            assertTrue(blockedByHolder,
                    "pg_blocking_pids proves a backend is lock-blocked by the holder pid " + holderPid.get());

            long releasedAt = System.nanoTime();
            release.countDown();                                 // release the lock
            holder.get(5, java.util.concurrent.TimeUnit.SECONDS);
            competitor.get(5, java.util.concurrent.TimeUnit.SECONDS);   // now it completes
            assertTrue(pauseReturnedAt.get() >= releasedAt, "pause resolved only AFTER the lock was released");
        } finally {
            release.countDown();                                 // ensure the holder never dangles
            pool.shutdownNow();
        }
        assertTrue(admin.find("pg-r4").orElseThrow().isPaused());
    }
}
