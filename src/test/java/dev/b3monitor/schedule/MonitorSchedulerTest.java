package dev.b3monitor.schedule;

import dev.b3monitor.domain.auth.OperationalAuthorization;
import dev.b3monitor.domain.auth.StaticOperationalAuthorization;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.monitor.MonitorPipeline;
import dev.b3monitor.persistence.OutboxDispatcher;
import dev.b3monitor.quota.BrapiQuotaManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Scheduler tests: disabled by default (no-op, never polls); the per-asset authorization gate blocks
 * fetch FIRST even with the worker enabled, calendar OPEN and a dedicated quota (cycle-5 review P1);
 * UNKNOWN calendar fails closed; on a granted path the fenced quota slot is always released and
 * reset headers are OBSERVED (never reset the budget here); expired leases + stranded reservations
 * are reconciled each tick.
 */
class MonitorSchedulerTest {

    private final MonitorPipeline pipeline = mock(MonitorPipeline.class);
    private final OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
    private final BrapiQuotaManager quota = mock(BrapiQuotaManager.class);
    private final TradingCalendar calendar = mock(TradingCalendar.class);
    /** REAL authorization (not a mock): all assets NOT_AUTHORIZED by default, SNAG11 partial, KNHY11 quarantine. */
    private final OperationalAuthorization authorization = new StaticOperationalAuthorization();

    private PriceRule rule(String ticker) {
        return new PriceRule("r-" + ticker, ticker, Comparator.ABOVE, new BigDecimal("50.00"), 2, BigDecimal.ZERO);
    }

    @Test
    void disabledSchedulerIsNoOpAndNeverTouchesPipelineOrQuota() {
        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, authorization, src(), false);
        var report = sched.tick(List.of(rule("WEGE3")));
        assertFalse(sched.isEnabled());
        assertEquals(0, report.fetched());
        verifyNoInteractions(pipeline, dispatcher, quota, calendar);
    }

    @Test
    void unauthorizedAssetNeverFetchesEvenWithOpenMarketAndDedicatedQuota() {
        // worker ENABLED, calendar OPEN, quota would grant — authorization must still block the fetch.
        lenient().when(calendar.isTradingNow()).thenReturn(TradingCalendar.Status.OPEN);
        lenient().when(quota.tryAcquire()).thenReturn(new BrapiQuotaManager.Granted(1L));
        when(dispatcher.reconcileExpiredLeases()).thenReturn(0);
        when(dispatcher.drainBatch()).thenReturn(0);

        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, authorization, src(), true);
        // WEGE3 is NOT_AUTHORIZED by default; SNAG11 PARTIAL; KNHY11 QUARANTINED — none may fetch.
        var report = sched.tick(List.of(rule("WEGE3"), rule("SNAG11"), rule("KNHY11")));

        assertEquals(3, report.deniedAuth(), "all three denied at the authorization gate");
        assertEquals(0, report.fetched());
        verifyNoInteractions(pipeline);
        verify(quota, never()).tryAcquire();                 // authorization is checked BEFORE quota
        verify(calendar, never()).isTradingNow();            // and before the calendar
    }

    @Test
    void unknownCalendarSkipsWithoutAcquiringQuotaOrFetching() {
        var auth = authorizingOnly("WEGE3");
        when(calendar.isTradingNow()).thenReturn(TradingCalendar.Status.UNKNOWN);
        when(dispatcher.reconcileExpiredLeases()).thenReturn(0);
        when(dispatcher.drainBatch()).thenReturn(0);
        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, auth, src(), true);
        var report = sched.tick(List.of(rule("WEGE3")));
        assertEquals(1, report.skippedCalendar());
        assertEquals(0, report.fetched());
        verifyNoInteractions(pipeline);
        verify(quota, never()).tryAcquire();
        verify(dispatcher).reconcileExpiredLeases();
        verify(dispatcher).drainBatch();
    }

    @Test
    void openCalendarButQuotaDeniedSkipsFetch() {
        var auth = authorizingOnly("WEGE3");
        when(calendar.isTradingNow()).thenReturn(TradingCalendar.Status.OPEN);
        when(quota.tryAcquire()).thenReturn(new BrapiQuotaManager.Denied("CONCURRENCY_BUSY"));
        when(dispatcher.reconcileExpiredLeases()).thenReturn(0);
        when(dispatcher.drainBatch()).thenReturn(0);
        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, auth, src(), true);
        var report = sched.tick(List.of(rule("WEGE3")));
        assertEquals(1, report.deniedQuota());
        assertEquals(0, report.fetched());
        verifyNoInteractions(pipeline);
        verify(quota, never()).release(anyLong());   // nothing to release on denied admission
    }

    @Test
    void grantedPathAlwaysReleasesFencedSlotAndObserves429() {
        var auth = authorizingOnly("WEGE3");
        when(calendar.isTradingNow()).thenReturn(TradingCalendar.Status.OPEN);
        when(quota.tryAcquire()).thenReturn(new BrapiQuotaManager.Granted(77L));
        when(dispatcher.reconcileExpiredLeases()).thenReturn(0);
        when(dispatcher.drainBatch()).thenReturn(0);
        var rl = dev.b3monitor.adapter.brapi.BrapiException.rateLimited("429", 42L, 3600L, 10);
        when(pipeline.runOnce(any()))
                .thenReturn(new MonitorPipeline.CycleResult(false, false, false, "RATE_LIMITED", rl, null));
        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, auth, src(), true);
        sched.tick(List.of(rule("WEGE3")));
        verify(quota).onRateLimited(42L);
        verify(quota).observeResetHeader(3600L, 10, null, null, null);  // 429 path: no window/limit → fail closed
        verify(quota).onResult(false);
        verify(quota).release(77L);                         // released with the fencing token
        verify(quota).confirmCycleRolloverIfElapsed();
        verify(quota).reconcileStranded();
    }

    @Test
    void successPathObservesResetHeadersToo() {
        var auth = authorizingOnly("WEGE3");
        when(calendar.isTradingNow()).thenReturn(TradingCalendar.Status.OPEN);
        when(quota.tryAcquire()).thenReturn(new BrapiQuotaManager.Granted(5L));
        when(dispatcher.reconcileExpiredLeases()).thenReturn(0);
        when(dispatcher.drainBatch()).thenReturn(0);
        var sig = new dev.b3monitor.adapter.brapi.QuotaSignal(null, 7200L, 8000, 15000, "billing-cycle", null, null, false);
        when(pipeline.runOnce(any()))
                .thenReturn(new MonitorPipeline.CycleResult(true, true, false, "ABOVE", null, sig));
        var sched = new MonitorScheduler(pipeline, dispatcher, quota, calendar, auth, src(), true);
        sched.tick(List.of(rule("WEGE3")));
        verify(quota).observeResetHeader(7200L, 8000, 15000, "billing-cycle", null);  // full 2xx provenance
        verify(quota).release(5L);
    }

    /** A test authorization that authorizes exactly one ticker, so calendar/quota gates can be exercised. */
    private static OperationalAuthorization authorizingOnly(String ticker) {
        return rule -> ticker.equals(rule.ticker())
                ? new OperationalAuthorization.Decision(OperationalAuthorization.Status.AUTHORIZED, "test-authorized")
                : new OperationalAuthorization.Decision(OperationalAuthorization.Status.NOT_AUTHORIZED, "test");
    }

    /** Empty rule source — these tests drive tick(List) directly, so the no-arg source is unused. */
    private static dev.b3monitor.domain.rule.RuleSource src() { return () -> java.util.List.of(); }
}
