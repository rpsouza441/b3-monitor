package dev.b3monitor.domain.outbox;

import org.junit.jupiter.api.Test;

import static dev.b3monitor.domain.outbox.OutboxState.*;
import static org.junit.jupiter.api.Assertions.*;

/** The explicit outbox transition matrix (cycle-7 item C). */
class OutboxTransitionsTest {

    @Test
    void legalForwardEdges() {
        assertTrue(OutboxTransitions.isLegal(PENDING, IN_FLIGHT));
        assertTrue(OutboxTransitions.isLegal(IN_FLIGHT, SENDING));
        assertTrue(OutboxTransitions.isLegal(SENDING, ACCEPTED));
        assertTrue(OutboxTransitions.isLegal(SENDING, UNKNOWN_OUTCOME));
        assertTrue(OutboxTransitions.isLegal(SENDING, FAILED));
        assertTrue(OutboxTransitions.isLegal(UNKNOWN_OUTCOME, FAILED));
        assertTrue(OutboxTransitions.isLegal(UNKNOWN_OUTCOME, PENDING));
        assertTrue(OutboxTransitions.isLegal(FAILED, FAILED), "idempotent");
    }

    @Test
    void forbiddenEdges() {
        assertFalse(OutboxTransitions.isLegal(ACCEPTED, FAILED), "evidence must be preserved");
        assertFalse(OutboxTransitions.isLegal(ACCEPTED, PENDING));
        assertFalse(OutboxTransitions.isLegal(CANCELLED, FAILED));
        assertFalse(OutboxTransitions.isLegal(EXPIRED, FAILED));
        assertFalse(OutboxTransitions.isLegal(SUPPRESSED, FAILED));
        assertFalse(OutboxTransitions.isLegal(PENDING, ACCEPTED), "no skipping SENDING");
        assertFalse(OutboxTransitions.isLegal(SENDING, PENDING));
    }

    @Test
    void requireLegalThrowsOnForbidden() {
        assertThrows(IllegalStateException.class, () -> OutboxTransitions.requireLegal(ACCEPTED, FAILED));
    }
}
