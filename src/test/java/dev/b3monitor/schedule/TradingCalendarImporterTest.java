package dev.b3monitor.schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Versioned trading-session calendar groundwork (cycle-7 item G): the importer validates a dataset
 * and only then builds a calendar; the production default invents nothing (UNKNOWN/"none"); market
 * dates derive via America/Sao_Paulo.
 */
class TradingCalendarImporterTest {

    private final DefaultTradingCalendarImporter importer = new DefaultTradingCalendarImporter();

    private TradingCalendarImporter.Dataset validDataset() {
        return new TradingCalendarImporter.Dataset(
                "test-2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                Set.of(LocalDate.of(2026, 12, 25)),           // holiday
                Set.of(LocalDate.of(2026, 12, 24)));          // special session
    }

    @Test
    void productionDefaultIsUnknownAndInventsNothing() {
        var cal = new UnavailableTradingSessionCalendar();
        assertEquals("none", cal.datasetVersion());
        assertEquals(TradingSessionCalendar.SessionStatus.UNKNOWN,
                cal.statusAt(Instant.parse("2026-06-15T13:00:00Z")));
    }

    @Test
    void validationRejectsBadDatasets() {
        assertFalse(importer.validate(new TradingCalendarImporter.Dataset(
                " ", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), Set.of(), Set.of())).valid());
        assertFalse(importer.validate(new TradingCalendarImporter.Dataset(
                "v", LocalDate.of(2026,12,31), LocalDate.of(2026,1,1), Set.of(), Set.of())).valid(),
                "end before start");
        assertFalse(importer.validate(new TradingCalendarImporter.Dataset(
                "v", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
                Set.of(LocalDate.of(2027,1,1)), Set.of())).valid(), "holiday outside coverage");
    }

    @Test
    void buildRejectsInvalidDataset() {
        var bad = new TradingCalendarImporter.Dataset("v", LocalDate.of(2026,12,31),
                LocalDate.of(2026,1,1), Set.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> importer.build(bad));
    }

    @Test
    void builtCalendarClassifiesSessionsViaSaoPaulo() {
        var cal = importer.build(validDataset());
        assertEquals("test-2026", cal.datasetVersion());
        // 2026-06-15 is a Monday; 13:00Z = 10:00 in Sao_Paulo (UTC-3) → OPEN
        assertEquals(TradingSessionCalendar.SessionStatus.OPEN,
                cal.statusAt(Instant.parse("2026-06-15T13:00:00Z")));
        // 2026-06-15 at 21:00Z = 18:00 Sao_Paulo → after close → CLOSED
        assertEquals(TradingSessionCalendar.SessionStatus.CLOSED,
                cal.statusAt(Instant.parse("2026-06-15T21:00:00Z")));
        // Christmas holiday → HOLIDAY (during hours)
        assertEquals(TradingSessionCalendar.SessionStatus.HOLIDAY,
                cal.statusAt(Instant.parse("2026-12-25T14:00:00Z")));
        // special session during hours → SPECIAL
        assertEquals(TradingSessionCalendar.SessionStatus.SPECIAL,
                cal.statusAt(Instant.parse("2026-12-24T14:00:00Z")));
        // weekend → CLOSED (2026-06-13 is Saturday)
        assertEquals(TradingSessionCalendar.SessionStatus.CLOSED,
                cal.statusAt(Instant.parse("2026-06-13T14:00:00Z")));
        // outside coverage → UNKNOWN (fail-closed)
        assertEquals(TradingSessionCalendar.SessionStatus.UNKNOWN,
                cal.statusAt(Instant.parse("2025-06-15T13:00:00Z")));
    }
}
