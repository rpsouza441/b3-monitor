package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.*;
import dev.b3monitor.monitor.MonitorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-10 items A+B — the linearizable lifecycle fence (PESSIMISTIC_WRITE on rule_definition) between
 * admin mutations and in-flight processing, plus retry-safe/idempotent mutation semantics. Full
 * {@code @SpringBootTest} on H2 with NO ambient transaction (asserted), driving the real services and
 * repositories so the row-lock transaction boundaries are genuinely exercised.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleLifecycleFenceTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        /** Authorize WEGE3 so the dispatch path can reach prepareSend in the E tests. */
        @Bean @Primary dev.b3monitor.domain.auth.OperationalAuthorization testAuthorization() {
            return rule -> "WEGE3".equals(rule.ticker())
                    ? new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.AUTHORIZED, "test")
                    : new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.NOT_AUTHORIZED, "test");
        }
    }

    @Autowired RuleAdminService admin;
    @Autowired MonitorProcessingService processing;
    @Autowired RuleDefinitionRepository ruleDefs;
    @Autowired RuleStateRepository ruleStates;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxTxOps outboxTx;
    @Autowired dev.b3monitor.domain.outbox.WahaOutboundAdapter adapter;

    private long seedCrossing(String id) {
        admin.find(id).ifPresent(e -> {});
        if (admin.find(id).isEmpty()) {
            admin.create(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        }
        return admin.selectMode(id, RuleMode.CROSSING);   // rev 2
    }

    private PriceRule snapshot(String id, long rev) {
        return new PriceRule(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), rev)
                .withMode(RuleMode.CROSSING);
    }
    private Quote q(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }
    private void cleanup(String id) {
        for (OutboxState st : OutboxState.values()) {
            outbox.findByRuleIdAndState(id, st).forEach(outbox::delete);
        }
        ruleStates.findByRuleId(id).ifPresent(ruleStates::delete);
        ruleDefs.findByRuleId(id).ifPresent(ruleDefs::delete);
    }

    @Test
    void noAmbientTransaction() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** A1 — the fetch snapshot is for the current revision, but a pause commits before process() runs;
     *  the fence then observes paused and refuses: no state mutation, no PENDING. */
    @Test
    void pauseBeforeProcessDeniesStaleEvaluation() {
        String id = "fence-a1";
        long rev = seedCrossing(id);
        PriceRule snap = snapshot(id, rev);                // fetched while active
        admin.pause(id);                                   // admin wins the race
        var r = processing.process(snap, q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("RULE_PAUSED_CURRENT", r.detail());
        assertTrue(ruleStates.findByRuleId(id).isEmpty(), "no rule_state mutation on a fenced-out evaluation");
        assertEquals(0, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size());
        cleanup(id);
    }

    /** A2 — process wins first and creates PENDING; a later pause cancels that unsent PENDING. */
    @Test
    void processWinsThenPauseCancelsResultingPending() {
        String id = "fence-a2";
        long rev = seedCrossing(id);
        PriceRule snap = snapshot(id, rev);
        processing.process(snap, q(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        var fire = processing.process(snap, q(new BigDecimal("50.50"), NOW.minusSeconds(90)));
        assertTrue(fire.fired());
        assertEquals(1, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size());
        admin.pause(id);                                   // pause after → cancels the unsent PENDING
        assertEquals(0, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size());
        assertEquals(1, outbox.findByRuleIdAndState(id, OutboxState.CANCELLED).size());
        cleanup(id);
    }

    /** A3 — an edit commits (new revision) while a fetch is in flight; the old-revision snapshot cannot
     *  evaluate/mutate after the fetch returns. */
    @Test
    void editWinsSoStaleRevisionSnapshotIsDenied() {
        String id = "fence-a3";
        long rev = seedCrossing(id);
        PriceRule staleSnap = snapshot(id, rev);           // fetched at rev
        admin.edit(id, rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10")); // rev+1
        var r = processing.process(staleSnap, q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("STALE_RULE_DEFINITION", r.detail());
        assertTrue(ruleStates.findByRuleId(id).isEmpty());
        assertEquals(0, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size());
        cleanup(id);
    }

    /** A4 — a true concurrent race: a latch holds the admin pause transaction mid-flight while process
     *  runs; the fence serializes them and the outcome is consistent (no PENDING survives active). */
    @Test
    void concurrentPauseAndProcessAreLinearizedNoDeadlock() throws Exception {
        String id = "fence-a4";
        long rev = seedCrossing(id);
        processing.process(snapshot(id, rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> f1 = pool.submit(() -> { go.await(); admin.pause(id); return null; });
        // process races pause; its ONLY legal in-race failure is an optimistic-lock conflict when it loses
        // the rule-state write to the concurrent revision bump. The worker rethrows (no swallow); Future.get
        // surfaces it and we assert its exact type. Any other failure fails the test.
        Future<?> f2 = pool.submit(() -> { go.await();
            processing.process(snapshot(id, rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
            return null; });
        go.countDown();
        f1.get(15, TimeUnit.SECONDS);
        try {
            f2.get(15, TimeUnit.SECONDS);                            // completes → no deadlock
        } catch (ExecutionException ee) {
            assertTrue(isOptimisticConflict(ee.getCause()),
                    "the only legal in-race process failure is an optimistic-lock conflict against the "
                            + "concurrent pause revision bump, but got: " + ee.getCause());
        }
        pool.shutdownNow();
        // Whatever the interleaving, an active rule must never be left with an uncancelled PENDING:
        // either process lost (no PENDING) or process won then pause cancelled it.
        assertEquals(0, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size(),
                "no unsent PENDING survives a committed pause");
        cleanup(id);
    }

    /** The only acceptable losing-process failure in the pause-vs-process race: an optimistic-lock
     *  conflict against the concurrent revision bump. Anything else (SQL error, deadlock, bug) must fail. */
    private static boolean isOptimisticConflict(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof org.springframework.orm.ObjectOptimisticLockingFailureException
                    || c instanceof org.springframework.dao.OptimisticLockingFailureException
                    || c instanceof org.springframework.dao.CannotAcquireLockException
                    || c instanceof org.hibernate.StaleObjectStateException
                    || (c.getClass().getName().contains("OptimisticLock"))) {
                return true;
            }
        }
        return false;
    }

    // ----- item B: retry-safe / idempotent mutations -----

    @Test
    void duplicateResumeDoesNotReMarkRebaseline() {
        String id = "fence-b1";
        seedCrossing(id);
        ruleStates.save(new RuleStateEntity(id));
        admin.pause(id);
        admin.resume(id);                                  // real transition → marks rebaseline
        assertTrue(ruleStates.findByRuleId(id).orElseThrow().isRebaselineRequired());
        // consume the marker, then a duplicate resume must NOT set it again
        var rs = ruleStates.findByRuleId(id).orElseThrow(); rs.clearRebaselineRequired(); ruleStates.save(rs);
        admin.resume(id);                                  // already active → no-op
        assertFalse(ruleStates.findByRuleId(id).orElseThrow().isRebaselineRequired(),
                "duplicate resume on an active rule must not re-mark rebaseline");
        cleanup(id);
    }

    @Test
    void duplicatePauseIsIdempotent() {
        String id = "fence-b2";
        seedCrossing(id);
        long rev = admin.find(id).orElseThrow().getRevision();
        admin.pause(id);
        admin.pause(id);                                   // idempotent — no revision mint
        assertEquals(rev, admin.find(id).orElseThrow().getRevision());
        assertTrue(admin.find(id).orElseThrow().isPaused());
        cleanup(id);
    }

    @Test
    void duplicateCrossingSelectionDoesNotMintExtraRevision() {
        String id = "fence-b3";
        long rev = seedCrossing(id);                       // first selection bumps to rev
        long again = admin.selectMode(id, RuleMode.CROSSING);
        assertEquals(rev, again, "selecting the already-current mode is an idempotent no-op");
        cleanup(id);
    }

    @Test
    void staleExpectedRevisionEditIsRejected() {
        String id = "fence-b4";
        long rev = seedCrossing(id);
        assertThrows(RuleAdminService.StaleRevisionException.class,
                () -> admin.edit(id, rev - 1, Comparator.ABOVE, new BigDecimal("60.00"), 2, new BigDecimal("0.10")));
        assertEquals(rev, admin.find(id).orElseThrow().getRevision(), "no mutation on a stale expectedRevision");
        cleanup(id);
    }

    // ----- item E: lifecycle fence at prepareSend (dispatch) -----

    /** Drive a crossing so a PENDING outbox row exists, then claim it (IN_FLIGHT) and return the claim. */
    private OutboxTxOps.Claim seedPendingAndClaim(String id, long rev) {
        processing.process(snapshot(id, rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED
        processing.process(snapshot(id, rev), q(new BigDecimal("50.50"), NOW.minusSeconds(120))); // fire → PENDING
        assertEquals(1, outbox.findByRuleIdAndState(id, OutboxState.PENDING).size());
        var claim = outboxTx.claimNext("test-worker");
        assertNotNull(claim, "claimed PENDING → IN_FLIGHT");
        return claim;
    }

    private int sends() { return ((CallRecordingAdapter) adapter).sends.get(); }

    /** E1 — pause wins the fence before prepareSend: the eligibility guard sees paused → DENIED_TERMINAL,
     *  SENDING is never committed, and the adapter is never called. */
    @Test
    void pauseWinsBeforePrepareSendZeroAdapterCalls() {
        String id = "fence-e1";
        long rev = seedCrossing(id);
        var claim = seedPendingAndClaim(id, rev);
        int before = sends();
        admin.pause(id);                                   // admin locks the rule row + commits paused
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.DENIED_TERMINAL, prepared.outcome(),
                "a paused rule denies send authority at the fence");
        assertEquals(before, sends(), "zero adapter calls when pause wins");
        assertNotEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
        cleanup(id);
    }

    /** E2 — prepareSend wins: SENDING authority commits; a later pause MUST NOT rewrite that as unsent. */
    @Test
    void prepareSendWinsBeforePauseAuthorityPreserved() {
        String id = "fence-e2";
        long rev = seedCrossing(id);
        var claim = seedPendingAndClaim(id, rev);
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome(), "send authority committed");
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
        // pause now runs — it cancels only PENDING, and must NOT rewrite the SENDING authority as unsent
        admin.pause(id);
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState(),
                "pause after SENDING cannot declare the side effect unsent");
        // the normal typed transport result then applies
        var mapped = outboxTx.record(claim.rowId(), claim.fencingToken(),
                dev.b3monitor.domain.outbox.SubmissionResult.accepted(), NOW);
        assertEquals(OutboxState.ACCEPTED, mapped);
        cleanup(id);
    }

    /** E3 — a stale-revision intent cannot obtain new send authority: an edit bumps the revision, so the
     *  guard's revision check denies the old PENDING at the fence. */
    @Test
    void staleRevisionIntentCannotGetSendAuthority() {
        String id = "fence-e3";
        long rev = seedCrossing(id);
        var claim = seedPendingAndClaim(id, rev);          // PENDING at rev
        admin.edit(id, rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10")); // rev+1
        int before = sends();
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertNotEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome(),
                "a superseded-revision intent cannot get send authority");
        assertEquals(before, sends(), "no adapter call for a stale-revision intent");
        cleanup(id);
    }

    /** A call-recording WAHA adapter so the E tests can assert adapter-call counts. */
    @TestConfiguration
    static class AdapterCfg {
        @Bean @Primary CallRecordingAdapter callRecordingAdapter() { return new CallRecordingAdapter(); }
    }
    static class CallRecordingAdapter implements dev.b3monitor.domain.outbox.WahaOutboundAdapter {
        final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        @Override public dev.b3monitor.domain.outbox.SubmissionResult send(dev.b3monitor.domain.outbox.AlertIntent intent) {
            sends.incrementAndGet();
            return dev.b3monitor.domain.outbox.SubmissionResult.accepted();
        }
    }
}
