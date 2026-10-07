package dev.b3monitor.admin;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.persistence.OutboxAttemptEntity;
import dev.b3monitor.persistence.OutboxEntity;
import dev.b3monitor.persistence.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-13 P1-A — the UI-03 alert view exposes the FULL transport lifecycle (not only dead-letters),
 * distinguishes ACCEPTED from delivery-confirmed, flags UNKNOWN_OUTCOME uncertain, shows PENDING/FAILED,
 * maps attempt lineage to the right logical alert, and caps the page size. Read-only; no mutation path.
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertOutcomeViewTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    @TestConfiguration
    static class Cfg { @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }

    @Autowired AdminQueryService query;
    @Autowired OutboxRepository outbox;
    @Autowired dev.b3monitor.persistence.OutboxAttemptRepository attempts;

    private OutboxEntity row(String key, OutboxState state) {
        var e = new OutboxEntity(key, "r-" + key, "WEGE3", "msg", 1L, 1L, NOW.minusSeconds(60), NOW, null);
        e.setState(state);
        return e;
    }

    @Test
    void fullLifecycleIsExposedWithAcceptedVsConfirmedAndLineage() {
        outbox.deleteAll(); attempts.deleteAll();
        // seed one row per state
        outbox.save(row("al-pending", OutboxState.PENDING));
        outbox.save(row("al-inflight", OutboxState.IN_FLIGHT));
        outbox.save(row("al-sending", OutboxState.SENDING));
        var accepted = row("al-accepted", OutboxState.ACCEPTED);
        accepted.recordAttemptResult(NOW, "pm-acc", NOW);   // accepted_at + provider id, but NOT delivery-confirmed
        var acceptedId = outbox.save(accepted).getId();
        outbox.save(row("al-unknown", OutboxState.UNKNOWN_OUTCOME));
        outbox.save(row("al-failed", OutboxState.FAILED));
        outbox.save(row("al-cancelled", OutboxState.CANCELLED));
        outbox.save(row("al-expired", OutboxState.EXPIRED));
        outbox.save(row("al-suppressed", OutboxState.SUPPRESSED));
        // an attempt row for the ACCEPTED logical alert
        attempts.save(new OutboxAttemptEntity(acceptedId, "al-accepted", 1L, 1L, NOW.minusSeconds(5)));

        var view = query.alertOutcomes(50);
        var states = view.alerts().stream().map(AdminDtos.AlertOutcomeView::state).toList();
        for (OutboxState st : OutboxState.values()) {
            assertTrue(states.contains(st.name()), "lifecycle view must expose state " + st);
        }

        var acc = view.alerts().stream().filter(a -> a.logicalKey().equals("al-accepted")).findFirst().orElseThrow();
        assertEquals("ACCEPTED", acc.state());
        assertFalse(acc.deliveryConfirmed(), "ACCEPTED is distinguishable from delivery-confirmed");
        assertNotNull(acc.acceptedAt(), "accepted_at is exposed");
        assertEquals("pm-acc", acc.providerMessageId());
        assertEquals(1, acc.attempts().size(), "attempt lineage maps to the correct logical alert");

        var unk = view.alerts().stream().filter(a -> a.logicalKey().equals("al-unknown")).findFirst().orElseThrow();
        assertTrue(unk.uncertain(), "UNKNOWN_OUTCOME is visibly uncertain");
        var failed = view.alerts().stream().filter(a -> a.logicalKey().equals("al-failed")).findFirst().orElseThrow();
        assertFalse(failed.deliveryConfirmed(), "FAILED is visible and not delivered");
        assertTrue(states.contains("PENDING"), "a pre-send state is visible");
    }

    @Test
    void pageSizeIsHardCapped() {
        outbox.deleteAll();
        for (int i = 0; i < 130; i++) outbox.save(row("cap-" + i, OutboxState.PENDING));
        // request way over the cap; service hard-caps at 100
        assertEquals(100, query.alertOutcomes(10_000).alerts().size(), "page size is hard-capped at 100");
    }

    @Test
    void deadLetterReconciliationStaysFocusedOnUnknownAndFailed() {
        outbox.deleteAll();
        outbox.save(row("rc-accepted", OutboxState.ACCEPTED));
        outbox.save(row("rc-unknown", OutboxState.UNKNOWN_OUTCOME));
        outbox.save(row("rc-failed", OutboxState.FAILED));
        var rec = query.reconciliation(50).rows();
        var recStates = rec.stream().map(AdminDtos.ReconciliationRowView::state).distinct().sorted().toList();
        assertEquals(java.util.List.of("FAILED", "UNKNOWN_OUTCOME"), recStates,
                "reconciliation stays focused on dead-letters; ACCEPTED is NOT in it");
    }
}
