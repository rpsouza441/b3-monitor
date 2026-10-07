package dev.b3monitor.persistence;

import dev.b3monitor.domain.dispatch.DispatchEligibilityGuard;
import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.SubmissionResult;
import dev.b3monitor.domain.outbox.WahaOutboundAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-commit dispatcher tests (cycle-7). The adapter returns a narrow {@link SubmissionResult}
 * (review P0-2); the dispatcher runs claim → prepareSend(commit SENDING) → send → record. New P0-1
 * TOCTOU regressions use a latch adapter to prove the authority gate: a pre-send terminalization that
 * wins before SENDING commits yields zero adapter calls; once SENDING commits, an operator cannot mark
 * the row "definitely not sent".
 */
@DataJpaTest
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxService.class, OutboxReconciliationService.class, OutboxDispatcher.class, OutboxDispatcherTest.Beans.class})
class OutboxDispatcherTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class ProgrammableAdapter implements WahaOutboundAdapter {
        final AtomicInteger sends = new AtomicInteger();
        volatile SubmissionResult next = SubmissionResult.accepted();
        volatile boolean throwOnce = false;
        volatile CountDownLatch release = null;
        volatile CountDownLatch entered = null;
        @Override public SubmissionResult send(AlertIntent intent) {
            sends.incrementAndGet();
            if (entered != null) entered.countDown();
            if (release != null) { try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} }
            if (throwOnce) { throwOnce = false; throw new RuntimeException("timeout"); }
            return next;
        }
    }

    static class ToggleGuard implements DispatchEligibilityGuard {
        volatile Denial denial = Denial.OK;
        @Override public Decision evaluate(OutboxEntity row, Instant now) { return new Decision(denial); }
    }

    static class Beans {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean WahaOutboundAdapter adapter() { return new ProgrammableAdapter(); }
        @Bean ToggleGuard guard() { return new ToggleGuard(); }
        @Bean OutboxTxOps txOps(OutboxRepository r, OutboxAttemptRepository ar, ToggleGuard g, Clock c) { return new OutboxTxOps(r, ar, g, c); }
        @Bean OutboxDispatcher dispatcher(OutboxTxOps tx, WahaOutboundAdapter a, Clock c) {
            return new OutboxDispatcher(tx, a, c, 50);
        }
    }

    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxReconciliationService reconcile;
    @Autowired OutboxTxOps txOps;
    @Autowired OutboxRepository repo;
    @Autowired WahaOutboundAdapter adapter;
    @Autowired ToggleGuard guard;
    @Autowired PlatformTransactionManager txm;

    private ProgrammableAdapter prog() { return (ProgrammableAdapter) adapter; }
    private TransactionTemplate tx() { return new TransactionTemplate(txm); }

    @AfterEach
    void clean() {
        guard.denial = DispatchEligibilityGuard.Denial.OK;
        prog().sends.set(0);
        prog().next = SubmissionResult.accepted();
        prog().throwOnce = false; prog().entered = null; prog().release = null;
        tx().executeWithoutResult(s -> repo.deleteAll());
    }

    private OutboxEntity newRow(String key, long revision) {
        return new OutboxEntity(key, "r1", "WEGE3", "msg", revision, 1, NOW.minusSeconds(60), NOW, null);
    }
    private void seedCommitted(String key) { tx().executeWithoutResult(s -> repo.save(newRow(key, 1))); }
    private Long seedReturningId(String key) { return tx().execute(s -> repo.save(newRow(key, 1)).getId()); }

    @Test
    void happyPathClaimsSendingThenAccepts() {
        seedCommitted("ep-accept");
        prog().next = SubmissionResult.accepted("pm-1", NOW);
        var outcome = dispatcher.dispatchOne();
        assertEquals(OutboxState.ACCEPTED, outcome.orElseThrow());
        OutboxEntity row = repo.findByLogicalKey("ep-accept").orElseThrow();
        assertEquals(OutboxState.ACCEPTED, row.getState());
        assertFalse(row.isDeliveryConfirmed());
        assertEquals(1, prog().sends.get());
        assertNotNull(row.getSendStartedAt(), "SENDING committed before the send");
        assertNotNull(row.getAttemptFinishedAt());
        assertEquals("pm-1", row.getProviderMessageId());
    }

    @Test
    void nullResultBecomesUnknownNotResent() {
        seedCommitted("ep-null");
        prog().next = null;
        dispatcher.dispatchOne();
        assertEquals(OutboxState.UNKNOWN_OUTCOME, repo.findByLogicalKey("ep-null").orElseThrow().getState());
        int sends = prog().sends.get();
        dispatcher.drainBatch();
        assertEquals(sends, prog().sends.get(), "UNKNOWN_OUTCOME is never blind-resent");
    }

    @Test
    void thrownSendBecomesUnknown() {
        seedCommitted("ep-throw");
        prog().throwOnce = true;
        dispatcher.dispatchOne();
        assertEquals(OutboxState.UNKNOWN_OUTCOME, repo.findByLogicalKey("ep-throw").orElseThrow().getState());
    }

    @Test
    void definiteFailureMapsToFailed() {
        seedCommitted("ep-fail");
        prog().next = SubmissionResult.definiteFailure("400-bad-recipient");
        var outcome = dispatcher.dispatchOne();
        assertEquals(OutboxState.FAILED, outcome.orElseThrow());
    }

    @Test
    void expiredLeaseSendingIsQuarantinedNotResent() {
        long staleToken = tx().execute(s -> {
            OutboxEntity row = newRow("ep-crash", 1);
            row.claim("dead", NOW.minusSeconds(5));
            row.markSending(NOW.minusSeconds(5));           // crashed mid-send (SENDING)
            row.setState(OutboxState.SENDING);
            return repo.save(row).getFencingToken();
        });
        int before = prog().sends.get();
        int[] rec = dispatcher.reconcileExpiredLeases();
        assertEquals(0, rec[0]); assertEquals(1, rec[1], "SENDING crash → quarantined (ambiguous)");
        OutboxEntity after = repo.findByLogicalKey("ep-crash").orElseThrow();
        assertEquals(OutboxState.UNKNOWN_OUTCOME, after.getState());
        assertEquals(before, prog().sends.get());
        assertTrue(after.getFencingToken() > staleToken, "claim invalidated");
    }

    @Test
    void expiredLeaseInFlightIsSafelyRecoveredToPending() {
        // IN_FLIGHT (pre-SENDING) crash: no external send was possible → safe recovery to PENDING.
        long staleToken = tx().execute(s -> {
            OutboxEntity row = newRow("ep-inflight", 1);
            row.claim("dead", NOW.minusSeconds(5));
            row.setState(OutboxState.IN_FLIGHT);
            return repo.save(row).getFencingToken();
        });
        int[] rec = dispatcher.reconcileExpiredLeases();
        assertEquals(1, rec[0], "IN_FLIGHT crash → safe recovery"); assertEquals(0, rec[1]);
        OutboxEntity after = repo.findByLogicalKey("ep-inflight").orElseThrow();
        assertEquals(OutboxState.PENDING, after.getState(), "recovered to PENDING for a fresh claim");
        assertTrue(after.getFencingToken() > staleToken, "stale worker fenced out");
    }

    // ----- cycle-7 P0-1: eligibility→send TOCTOU closed by the SENDING authority gate -----

    @Test
    void preSendTerminalizationWinsBeforeSendingNoAdapterCall() {
        seedCommitted("ep-toctou");
        // guard denies at prepareSend → row goes terminal, adapter never called
        guard.denial = DispatchEligibilityGuard.Denial.ASSET_NOT_AUTHORIZED;
        int before = prog().sends.get();
        var outcome = dispatcher.dispatchOne();
        assertEquals(OutboxState.SUPPRESSED, outcome.orElseThrow());
        assertEquals(before, prog().sends.get(), "no adapter call when terminalization wins before SENDING");
    }

    @Test
    void onceSendingCommitsOperatorCannotMarkUnsentAndCrashIsAmbiguous() throws Exception {
        Long id = seedReturningId("ep-sending");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        prog().entered = entered; prog().release = release; prog().next = SubmissionResult.accepted();

        var pool = Executors.newSingleThreadExecutor();
        var fut = pool.submit(() -> dispatcher.dispatchOne());   // claims, commits SENDING, blocks in send()
        assertTrue(entered.await(5, TimeUnit.SECONDS), "send() reached → SENDING already committed");

        // The row is SENDING: an operator abandon must be REFUSED (cannot declare 'definitely not sent').
        assertEquals(OutboxState.SENDING, repo.findById(id).orElseThrow().getState());
        assertFalse(reconcile.abandon(id), "a SENDING row cannot be abandoned to FAILED");
        assertEquals(OutboxState.SENDING, repo.findById(id).orElseThrow().getState());

        release.countDown();
        fut.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(OutboxState.ACCEPTED, repo.findById(id).orElseThrow().getState());
    }

    @Test
    void leaseExpiredBeforePrepareSendYieldsNoAdapterCall() {
        // Seed an IN_FLIGHT row whose lease already expired; a prepareSend must not authorize/send.
        Long id = tx().execute(s -> {
            OutboxEntity row = newRow("ep-lease", 1);
            row.claim("old", NOW.minusSeconds(5));
            row.setState(OutboxState.IN_FLIGHT);
            return repo.save(row).getId();
        });
        int before = prog().sends.get();
        long token = repo.findById(id).orElseThrow().getFencingToken();
        var prepared = txOps.prepareSend(id, token, NOW);   // now > leaseUntil
        assertEquals(OutboxTxOps.PrepareOutcome.GONE, prepared.outcome());
        assertEquals(before, prog().sends.get(), "expired lease before prepareSend → no send");
    }

    @Test
    void nothingToDispatchReturnsEmpty() {
        assertTrue(dispatcher.dispatchOne().isEmpty());
    }

    /**
     * Cycle-9 item F — a pre-send optimistic race (the row is terminalized by another writer between
     * claim and prepareSend) must NOT be reported as UNKNOWN_OUTCOME: nothing was sent and no UNKNOWN
     * transition was persisted. The dispatcher reports the ACTUAL persisted state and makes no adapter
     * call. Here the guard denial path exercises the "pre-send loss" semantics deterministically: the
     * row goes terminal with NO send and the reported outcome equals the persisted terminal state.
     */
    @Test
    void preSendRaceReportsPersistedStateNotFabricatedUnknown() {
        seedCommitted("ep-race");
        guard.denial = DispatchEligibilityGuard.Denial.SUPERSEDED_REVISION;  // pre-send terminalization
        int before = prog().sends.get();
        var outcome = dispatcher.dispatchOne();
        // SUPERSEDED_REVISION maps to CANCELLED — the dispatcher reports exactly that, never UNKNOWN.
        assertEquals(OutboxState.CANCELLED, outcome.orElseThrow());
        assertNotEquals(OutboxState.UNKNOWN_OUTCOME, outcome.orElseThrow(),
                "a pre-send loss is never reported as UNKNOWN_OUTCOME");
        assertEquals(OutboxState.CANCELLED, repo.findByLogicalKey("ep-race").orElseThrow().getState(),
                "reported outcome matches the persisted state");
        assertEquals(before, prog().sends.get(), "no adapter call on a pre-send loss");
    }
}
