package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reconciliation surface + the explicit transition matrix (cycle-7 item C). Metrics; dead-letter
 * listing; proof-gated requeue; and adversarial transition tests proving `abandon()` is legal ONLY
 * from UNKNOWN_OUTCOME/FAILED (never clobbers ACCEPTED/PENDING/SENDING), with a separate pre-send
 * cancel for PENDING.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({OutboxReconciliationService.class, OutboxReconciliationServiceTest.Beans.class})
class OutboxReconciliationServiceTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    static class Beans { @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }

    @Autowired OutboxReconciliationService svc;
    @Autowired OutboxRepository repo;

    private OutboxEntity row(String key, OutboxState state) {
        OutboxEntity e = new OutboxEntity(key, "r1", "WEGE3", "msg", 1L, 1L, NOW.minusSeconds(60), NOW, null);
        e.setState(state);
        return repo.saveAndFlush(e);
    }

    @Test
    void metricsCountStatesAndOldestAge() {
        row("k-pending", OutboxState.PENDING);
        row("k-sending", OutboxState.SENDING);
        row("k-unknown", OutboxState.UNKNOWN_OUTCOME);
        row("k-failed", OutboxState.FAILED);
        var m = svc.metrics();
        assertEquals(1, m.pending());
        assertEquals(1, m.sending());
        assertEquals(1, m.unknownOutcome());
        assertEquals(1, m.failed());
        assertFalse(m.oldestPendingAge().isNegative());
    }

    @Test
    void deadLettersListsUnknownAndFailedOnly() {
        row("k-ok", OutboxState.ACCEPTED);
        row("k-unknown", OutboxState.UNKNOWN_OUTCOME);
        row("k-failed", OutboxState.FAILED);
        var dl = svc.deadLetters();
        assertEquals(2, dl.size());
        assertTrue(dl.stream().noneMatch(r -> r.getState() == OutboxState.ACCEPTED));
    }

    @Test
    void unknownOutcomeIsNotRequeuedWithoutProof() {
        OutboxEntity r = row("k-unknown", OutboxState.UNKNOWN_OUTCOME);
        assertFalse(svc.markFailedAfterProofOfNonDelivery(r.getId(), false));
        assertEquals(OutboxState.UNKNOWN_OUTCOME, repo.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    void unknownOutcomeRequeuedOnlyWithProof() {
        OutboxEntity r = row("k-unknown", OutboxState.UNKNOWN_OUTCOME);
        assertTrue(svc.markFailedAfterProofOfNonDelivery(r.getId(), true));
        assertEquals(OutboxState.PENDING, repo.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    void abandonLegalFromUnknownOutcome() {
        OutboxEntity r = row("k-unknown", OutboxState.UNKNOWN_OUTCOME);
        assertTrue(svc.abandon(r.getId()));
        assertEquals(OutboxState.FAILED, repo.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    void abandonIdempotentFromFailed() {
        OutboxEntity r = row("k-failed", OutboxState.FAILED);
        assertTrue(svc.abandon(r.getId()));
        assertEquals(OutboxState.FAILED, repo.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    void abandonRefusedFromAcceptedPreservesEvidence() {
        OutboxEntity r = row("k-accepted", OutboxState.ACCEPTED);
        assertFalse(svc.abandon(r.getId()), "ACCEPTED → FAILED is forbidden (evidence preserved)");
        assertEquals(OutboxState.ACCEPTED, repo.findById(r.getId()).orElseThrow().getState());
    }

    @Test
    void abandonRefusedFromPendingAndSending() {
        OutboxEntity p = row("k-pending", OutboxState.PENDING);
        OutboxEntity s = row("k-sending", OutboxState.SENDING);
        assertFalse(svc.abandon(p.getId()), "PENDING must use pre-send cancel, not abandon");
        assertFalse(svc.abandon(s.getId()), "SENDING cannot be declared definitely-not-sent");
        assertEquals(OutboxState.PENDING, repo.findById(p.getId()).orElseThrow().getState());
        assertEquals(OutboxState.SENDING, repo.findById(s.getId()).orElseThrow().getState());
    }

    @Test
    void cancelPendingOnlyFromPending() {
        OutboxEntity p = row("k-cancel", OutboxState.PENDING);
        assertTrue(svc.cancelPending(p.getId(), "operator-cancel"));
        assertEquals(OutboxState.CANCELLED, repo.findById(p.getId()).orElseThrow().getState());

        OutboxEntity a = row("k-accepted2", OutboxState.ACCEPTED);
        assertFalse(svc.cancelPending(a.getId(), "x"), "cancelPending refuses a non-PENDING row");
        assertEquals(OutboxState.ACCEPTED, repo.findById(a.getId()).orElseThrow().getState());
    }
}
