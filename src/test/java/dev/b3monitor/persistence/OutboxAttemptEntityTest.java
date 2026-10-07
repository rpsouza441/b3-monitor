package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.SubmissionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-9 item E — the attempt row is a durable per-attempt ledger entry with ONE legal
 * {@code OPEN → CLOSED} transition, immutable thereafter. A second close or a null result is rejected
 * loudly rather than silently overwriting attempt history.
 */
class OutboxAttemptEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    private OutboxAttemptEntity open() {
        return new OutboxAttemptEntity(1L, "k", 1L, 7L, NOW.minusSeconds(1));
    }

    @Test
    void newAttemptIsOpen() {
        var a = open();
        assertTrue(a.isOpen());
        assertNull(a.getFinishedAt());
        assertNull(a.getOutcome());
    }

    @Test
    void closeOnceFreezesTheOutcome() {
        var a = open();
        a.close(NOW, SubmissionResult.accepted("pm-1", NOW));
        assertFalse(a.isOpen());
        assertEquals(SubmissionResult.Kind.ACCEPTED, a.getOutcome());
        assertEquals("pm-1", a.getProviderMessageId());
        assertEquals(NOW, a.getFinishedAt());
    }

    @Test
    void secondCloseIsRejected() {
        var a = open();
        a.close(NOW, SubmissionResult.unknown("timeout"));
        var ex = assertThrows(IllegalStateException.class,
                () -> a.close(NOW.plusSeconds(1), SubmissionResult.accepted()));
        assertTrue(ex.getMessage().contains("already closed"));
        // the original outcome is preserved
        assertEquals(SubmissionResult.Kind.UNKNOWN, a.getOutcome());
    }

    @Test
    void nullResultIsRejected() {
        var a = open();
        assertThrows(IllegalArgumentException.class, () -> a.close(NOW, null));
        assertTrue(a.isOpen(), "a rejected close leaves the attempt open");
    }
}
