package dev.b3monitor.persistence;

import dev.b3monitor.domain.dispatch.DispatchEligibilityGuard;
import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.SubmissionResult;
import dev.b3monitor.domain.outbox.WahaOutboundAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Durable append-only attempt ledger (cycle-8 review P1-high / item C): an external attempt row is
 * created only when SENDING commits; a pre-send claim crash creates none; a proof-gated requeue
 * creates a NEW immutable row while preserving the prior one.
 */
@DataJpaTest
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OutboxService.class, OutboxReconciliationService.class, OutboxDispatcher.class, OutboxAttemptLedgerTest.Beans.class})
class OutboxAttemptLedgerTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class ProgrammableAdapter implements WahaOutboundAdapter {
        volatile SubmissionResult next = SubmissionResult.accepted();
        @Override public SubmissionResult send(AlertIntent intent) { return next; }
    }
    static class Beans {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean WahaOutboundAdapter adapter() { return new ProgrammableAdapter(); }
        @Bean DispatchEligibilityGuard guard() {
            return (row, now) -> new DispatchEligibilityGuard.Decision(DispatchEligibilityGuard.Denial.OK);
        }
        @Bean OutboxTxOps txOps(OutboxRepository r, OutboxAttemptRepository ar, DispatchEligibilityGuard g, Clock c) {
            return new OutboxTxOps(r, ar, g, c);
        }
        @Bean OutboxDispatcher dispatcher(OutboxTxOps tx, WahaOutboundAdapter a, Clock c) {
            return new OutboxDispatcher(tx, a, c, 50);
        }
    }

    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxReconciliationService reconcile;
    @Autowired OutboxTxOps txOps;
    @Autowired OutboxRepository repo;
    @Autowired OutboxAttemptRepository attempts;
    @Autowired WahaOutboundAdapter adapter;
    @Autowired PlatformTransactionManager txm;

    private ProgrammableAdapter prog() { return (ProgrammableAdapter) adapter; }
    private TransactionTemplate tx() { return new TransactionTemplate(txm); }

    @AfterEach
    void clean() { prog().next = SubmissionResult.accepted(); tx().executeWithoutResult(s -> { attempts.deleteAll(); repo.deleteAll(); }); }

    private Long seed(String key) {
        return tx().execute(s -> repo.save(new OutboxEntity(key, "r1", "WEGE3", "msg",
                1L, 1L, NOW.minusSeconds(60), NOW, null)).getId());
    }

    @Test
    void claimCrashBeforeSendingCreatesNoAttempt() {
        Long id = seed("ep-claimcrash");
        // claim only (simulate crash before prepareSend) — directly via txOps.claimNext then nothing
        tx().executeWithoutResult(s -> {
            var row = repo.findById(id).orElseThrow();
            row.claim("w", NOW.plusSeconds(30));
            row.setState(OutboxState.IN_FLIGHT);
            repo.save(row);
        });
        assertEquals(0, attempts.countByOutboxId(id), "a claim is NOT an external attempt");
        assertEquals(0, repo.findById(id).orElseThrow().getAttempts());
    }

    @Test
    void oneAcceptedSendCreatesOneImmutableAttempt() {
        Long id = seed("ep-accept");
        prog().next = SubmissionResult.accepted("pm-1", NOW);
        dispatcher.dispatchOne();
        assertEquals(OutboxState.ACCEPTED, repo.findById(id).orElseThrow().getState());
        var rows = attempts.findByOutboxIdOrderByStartedAtAsc(id);
        assertEquals(1, rows.size());
        assertEquals(SubmissionResult.Kind.ACCEPTED, rows.get(0).getOutcome());
        assertEquals("pm-1", rows.get(0).getProviderMessageId());
        assertNotNull(rows.get(0).getFinishedAt());
        assertEquals(1, repo.findById(id).orElseThrow().getAttempts());
    }

    @Test
    void unknownThenProofRequeueThenAcceptKeepsTwoImmutableAttempts() {
        Long id = seed("ep-requeue");
        prog().next = SubmissionResult.unknown("timeout");
        dispatcher.dispatchOne();                           // attempt 1 → UNKNOWN
        assertEquals(OutboxState.UNKNOWN_OUTCOME, repo.findById(id).orElseThrow().getState());
        long gen1 = repo.findById(id).orElseThrow().getClaimGeneration();

        assertTrue(reconcile.markFailedAfterProofOfNonDelivery(id, true)); // → PENDING, claim invalidated
        prog().next = SubmissionResult.accepted("pm-2", NOW);
        dispatcher.dispatchOne();                           // attempt 2 → ACCEPTED
        assertEquals(OutboxState.ACCEPTED, repo.findById(id).orElseThrow().getState());

        var rows = attempts.findByOutboxIdOrderByStartedAtAsc(id);
        assertEquals(2, rows.size(), "two immutable attempt rows");
        // attempt 1 preserved as UNKNOWN; attempt 2 ACCEPTED with its own provider id
        assertEquals(SubmissionResult.Kind.UNKNOWN, rows.get(0).getOutcome());
        assertEquals(gen1, rows.get(0).getClaimGeneration());
        assertEquals(SubmissionResult.Kind.ACCEPTED, rows.get(1).getOutcome());
        assertEquals("pm-2", rows.get(1).getProviderMessageId());
        assertEquals(2, repo.findById(id).orElseThrow().getAttempts(), "attempts counts external submissions");
    }
}
