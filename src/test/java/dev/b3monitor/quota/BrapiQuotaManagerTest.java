package dev.b3monitor.quota;

import dev.b3monitor.persistence.BrapiQuotaEntity;
import dev.b3monitor.persistence.BrapiQuotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conservative DURABLE Brapi admission tests (cycle-5 review P0 + P1-high).
 *
 * <p>The headline change versus cycle 4: a {@code ratelimit-reset} DELTA is an OBSERVATION of a
 * predicted deadline, NOT a reset event. {@link #resetHeaderDoesNotZeroBudget_defectFromCycle4Fixed}
 * is the inverted regression — the old test asserted the counter was zeroed on
 * {@code onResetHeader(3600,...)}; it must now be UNCHANGED. The counter is zeroed only by
 * {@link #cycleRollsOverOnlyWhenObservedDeadlineElapsed}.
 *
 * <p>Methods carry no ambient transaction so {@code QuotaTxOps}' {@code REQUIRES_NEW} operations
 * commit and are visible, mirroring production. Rows are cleaned between tests.
 */
@DataJpaTest
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BrapiQuotaManagerTest.Beans.class})
class BrapiQuotaManagerTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    /** A mutable test clock so we can advance time to confirm an elapsed reset deadline. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void set(Instant t) { this.now = t; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
    }

    static final MutableClock CLOCK = new MutableClock(NOW);

    static class Beans {
        @Bean Clock clock() { return CLOCK; }
        @Bean QuotaTxOps quotaTxOps(BrapiQuotaRepository r) { return new QuotaTxOps(r); }
        @Bean BrapiQuotaManager manager(QuotaTxOps tx, Clock c) { return new BrapiQuotaManager(tx, c); }
    }

    @Autowired BrapiQuotaManager manager;
    @Autowired QuotaTxOps tx;
    @Autowired BrapiQuotaRepository repo;

    @org.junit.jupiter.api.BeforeEach
    void resetClock() { CLOCK.set(NOW); }

    @org.junit.jupiter.api.AfterEach
    void clean() { repo.deleteAll(); }

    private long acquireToken() {
        var a = manager.tryAcquire();
        assertInstanceOf(BrapiQuotaManager.Granted.class, a);
        return ((BrapiQuotaManager.Granted) a).token();
    }

    @Test
    void sharedQuotaUnknownBlocksAdmission() {
        var a = manager.tryAcquire();
        assertInstanceOf(BrapiQuotaManager.Denied.class, a);
        assertEquals("SHARED_QUOTA_UNKNOWN", ((BrapiQuotaManager.Denied) a).reason());
    }

    @Test
    void dedicatedQuotaGrantsThenDurableSingleInFlight() {
        manager.declareDedicatedQuota();
        long token = acquireToken();
        assertTrue(manager.snapshot().isInFlight());
        // a second acquire without release is denied — durable single-in-flight, not a JVM semaphore
        var second = manager.tryAcquire();
        assertEquals("CONCURRENCY_BUSY", ((BrapiQuotaManager.Denied) second).reason());
        manager.release(token);
        assertFalse(manager.snapshot().isInFlight());
        long t2 = acquireToken();
        manager.release(t2);
    }

    @Test
    void consumeIsPessimisticOnAcquire() {
        manager.declareDedicatedQuota();
        int before = manager.consumedThisCycle();
        long token = acquireToken();
        assertEquals(before + 1, manager.consumedThisCycle(), "debit happens on reserve, not on success");
        manager.release(token);
        assertEquals(before + 1, manager.consumedThisCycle(), "release does not un-count");
    }

    @Test
    void routineCeilingReachedBlocks() {
        manager.declareDedicatedQuota();
        BrapiQuotaEntity q = tx.load();
        q.recordConsumed(q.getRoutineCeiling());
        repo.saveAndFlush(q);
        var a = manager.tryAcquire();
        assertEquals("ROUTINE_CEILING_REACHED", ((BrapiQuotaManager.Denied) a).reason());
    }

    @Test
    void rateLimitWindowBlocksUntilElapsed() {
        manager.declareDedicatedQuota();
        manager.onRateLimited(30);
        var a = manager.tryAcquire();
        assertEquals("RATE_LIMIT_WINDOW", ((BrapiQuotaManager.Denied) a).reason());
    }

    // ----- P0 + P1-D: reset observation needs billing-cycle evidence, never zeroes the budget ----

    /** Observe a verified billing-cycle header (window=billing-cycle, limit=hardLimit). */
    private void observeBilling(long delta, Integer remaining, java.time.Instant serverDate) {
        manager.observeResetHeader(delta, remaining, 15000, "billing-cycle", serverDate);
    }

    @Test
    void resetHeaderDoesNotZeroBudget_defectFromCycle4Fixed() {
        manager.declareDedicatedQuota();
        long token = acquireToken(); manager.release(token);     // consume 1
        long epochBefore = manager.snapshot().getCycleEpoch();
        int consumedBefore = manager.consumedThisCycle();
        assertTrue(consumedBefore >= 1);

        // CYCLE-4 DEFECT (now fixed): even a VERIFIED billing-cycle 3600s delta only records a
        // deadline — it must NOT zero the counter or bump the epoch an hour early.
        observeBilling(3600, 9000, null);

        assertEquals(epochBefore, manager.snapshot().getCycleEpoch(),
                "observing a reset DELTA must NOT advance the epoch (it is a deadline, not an event)");
        assertEquals(consumedBefore, Math.min(manager.consumedThisCycle(), consumedBefore),
                "observing ratelimit-reset=3600 must NOT lower the counter");
        assertEquals(NOW.plusSeconds(3600), manager.snapshot().getPendingResetAt());
    }

    @Test
    void billingCycleWithMatchingLimitAcceptedAsCycleProvenance() {
        manager.declareDedicatedQuota();
        observeBilling(3600, 9000, null);
        assertEquals(NOW.plusSeconds(3600), manager.snapshot().getPendingResetAt(),
                "billing-cycle + limit=15000 is accepted as the account-cycle deadline");
    }

    @Test
    void sandboxShortWindowNotAcceptedAsCycle() {
        manager.declareDedicatedQuota();
        // sandbox limiter: window not billing-cycle, limit tiny
        manager.observeResetHeader(60L, 5, 60, "sandbox", null);
        assertNull(manager.snapshot().getPendingResetAt(), "sandbox 20/60s window is not an account cycle");
        assertEquals(5, manager.snapshot().getProviderRemaining(), "remaining kept as uncertain evidence");
    }

    @Test
    void nonBillingHourlyWindowNotAcceptedAsCycle() {
        manager.declareDedicatedQuota();
        // a hypothetical 3600s window that is NOT billing-cycle must NOT be accepted (duration ≠ type)
        manager.observeResetHeader(3600L, 9000, 15000, "hourly", null);
        assertNull(manager.snapshot().getPendingResetAt(), "a non-billing 3600s window is not an account cycle");
    }

    @Test
    void mismatchedLimitStaysFailClosed() {
        manager.declareDedicatedQuota();
        // window says billing-cycle but the limit does not match the configured hard limit → reject
        manager.observeResetHeader(3600L, 9000, 99999, "billing-cycle", null);
        assertNull(manager.snapshot().getPendingResetAt(), "mismatched limit → fail closed, no deadline");
    }

    @Test
    void missingWindowOrLimitStaysFailClosed() {
        manager.declareDedicatedQuota();
        manager.observeResetHeader(3600L, 9000, null, null, null);
        assertNull(manager.snapshot().getPendingResetAt(), "missing window/limit → fail closed, no deadline");
    }

    @Test
    void serverDateAnchorsTheDeadline() {
        manager.declareDedicatedQuota();
        java.time.Instant serverDate = NOW.plusSeconds(10);   // server clock slightly ahead
        observeBilling(3600, 9000, serverDate);
        assertEquals(serverDate.plusSeconds(3600), manager.snapshot().getPendingResetAt(),
                "deadline anchored on server Date + delta when present");
    }

    @Test
    void providerRemainingRaisesHighWaterButNeverLowersIt() {
        manager.declareDedicatedQuota();
        // local consumed = 1
        long t = acquireToken(); manager.release(t);
        // provider says remaining=9000 → providerConsumed = 15000-9000 = 6000 > 1 → raise to 6000
        observeBilling(3600, 9000, null);
        assertEquals(6000, manager.consumedThisCycle(), "high-water raised to provider-observed consumption");
        // a later header with remaining=14000 → providerConsumed=1000 < 6000 → must NOT lower
        observeBilling(3600, 14000, null);
        assertEquals(6000, manager.consumedThisCycle(), "high-water never lowered by an external header");
    }

    @Test
    void cycleRollsOverOnlyWhenObservedDeadlineElapsed() {
        manager.declareDedicatedQuota();
        long t = acquireToken(); manager.release(t);
        long epochBefore = manager.snapshot().getCycleEpoch();

        observeBilling(3600, null, null);                    // deadline at NOW+1h, no reset yet
        assertFalse(manager.confirmCycleRolloverIfElapsed(), "deadline not reached → no rollover");
        assertEquals(epochBefore, manager.snapshot().getCycleEpoch());
        assertTrue(manager.consumedThisCycle() >= 1);

        CLOCK.set(NOW.plusSeconds(3601));                    // time passes beyond the observed deadline
        assertTrue(manager.confirmCycleRolloverIfElapsed(), "elapsed deadline → confirmed rollover");
        assertEquals(epochBefore + 1, manager.snapshot().getCycleEpoch());
        assertEquals(0, manager.consumedThisCycle(), "counter zeroed only on confirmed rollover");
        assertEquals(NOW.plusSeconds(3601), manager.snapshot().getLastConfirmedResetAt());
        assertNull(manager.snapshot().getPendingResetAt(), "pending deadline cleared after confirmation");
    }

    @Test
    void clockSkewBackwardDoesNotConfirmRollover() {
        manager.declareDedicatedQuota();
        long t = acquireToken(); manager.release(t);
        observeBilling(3600, null, null);
        CLOCK.set(NOW.minusSeconds(600));                    // clock jumps backward
        assertFalse(manager.confirmCycleRolloverIfElapsed(), "backward skew must not confirm a reset");
        assertEquals(0, manager.snapshot().getCycleEpoch());
    }

    // ----- P1-high: fenced reservation + crash/two-instance recovery -------------------------

    @Test
    void staleTokenReleaseDoesNotClearNewerReservation() {
        manager.declareDedicatedQuota();
        long tokenA = acquireToken();
        // simulate a crash: A never released. Operator reconciles the stranded slot, which frees it.
        CLOCK.set(NOW.plusSeconds(120));                     // beyond the reservation lease
        assertTrue(manager.reconcileStranded(), "a reservation older than the lease is flagged");
        assertTrue(manager.snapshot().isNeedsReconcile());
        // admission is blocked while reconciliation is pending (fail-closed)
        assertEquals("NEEDS_RECONCILE", ((BrapiQuotaManager.Denied) manager.tryAcquire()).reason());
        manager.clearReconcile();                            // operator clears → slot freed, token bumped

        long tokenB = acquireToken();                        // a NEW reservation (newer token)
        assertNotEquals(tokenA, tokenB);
        // the OLD worker A finally returns and releases its stale token — must be a no-op
        manager.release(tokenA);
        assertTrue(manager.snapshot().isInFlight(), "stale token A release must not clear newer reservation B");
        manager.release(tokenB);
        assertFalse(manager.snapshot().isInFlight());
    }

    @Test
    void crashStrandedReservationIsFlaggedNotSilentlyReopened() {
        manager.declareDedicatedQuota();
        acquireToken();                                      // reserve, then "crash" (no release)
        assertTrue(manager.snapshot().isInFlight());
        // before the lease elapses, nothing auto-frees it (fail-closed: a request may be in flight)
        assertFalse(manager.reconcileStranded());
        assertEquals("CONCURRENCY_BUSY", ((BrapiQuotaManager.Denied) manager.tryAcquire()).reason());
        // after the lease, it is FLAGGED (not reopened) for operator reconciliation
        CLOCK.set(NOW.plusSeconds(120));
        assertTrue(manager.reconcileStranded());
        assertTrue(manager.snapshot().isNeedsReconcile());
        assertTrue(manager.snapshot().isInFlight(), "stranded slot stays set until operator reconciles");
    }

    @Test
    void secondInstanceCannotWinTheSameSlot() {
        manager.declareDedicatedQuota();
        BrapiQuotaManager instanceB = new BrapiQuotaManager(tx, CLOCK);  // same durable row, different owner
        long tokenA = acquireToken();                        // instance A wins
        var b = instanceB.tryAcquire();                      // instance B must be refused
        assertInstanceOf(BrapiQuotaManager.Denied.class, b);
        assertEquals("CONCURRENCY_BUSY", ((BrapiQuotaManager.Denied) b).reason());
        manager.release(tokenA);
        assertInstanceOf(BrapiQuotaManager.Granted.class, instanceB.tryAcquire());
    }

    @Test
    void stateSurvivesRestart() {
        manager.declareDedicatedQuota();
        long t = acquireToken(); manager.release(t);
        BrapiQuotaManager restarted = new BrapiQuotaManager(tx, CLOCK);
        assertFalse(restarted.snapshot().isSharedUsageUnknown());
        assertTrue(restarted.consumedThisCycle() >= 1);
        long t2 = ((BrapiQuotaManager.Granted) restarted.tryAcquire()).token();
        restarted.release(t2);
    }
}
