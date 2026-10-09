package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.AlertIntent;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.outbox.SimulatedWahaAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence slice test on H2. Verifies outbox ENQUEUE idempotency (dedup on logicalKey) and
 * that enqueue persists a PENDING row only (no dispatch here — dispatch is post-commit, see
 * {@link OutboxDispatcherTest}). enqueue() requires an active transaction (MANDATORY), so the
 * test methods are {@code @Transactional}.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({OutboxService.class, SimulatedWahaAdapter.class})
class OutboxServiceTest {

    @Autowired OutboxService service;
    @Autowired OutboxRepository repo;

    private AlertIntent intent(String key) {
        return new AlertIntent(key, "r1", "WEGE3",
                "WEGE3 crossed ABOVE 50.00 (observed 50.01; as-of 2026-10-06T17:00Z)",
                1L, 1L,
                Instant.parse("2026-10-06T17:00:00Z"), Instant.parse("2026-10-06T17:00:05Z"), null);
    }

    @Test
    @Transactional
    void enqueueIsIdempotentOnLogicalKey() {
        service.enqueue(intent("WEGE3|r1|ep0"));
        service.enqueue(intent("WEGE3|r1|ep0")); // duplicate episode
        assertEquals(1, repo.count(), "duplicate logical key must not create a second row");
    }

    @Test
    @Transactional
    void enqueuePersistsPendingOnly() {
        OutboxEntity row = service.enqueue(intent("WEGE3|r1|ep1"));
        assertEquals(OutboxState.PENDING, row.getState());
        assertFalse(row.isDeliveryConfirmed());
        assertEquals(1, repo.findByState(OutboxState.PENDING).size());
    }
}
