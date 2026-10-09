package dev.b3monitor.schedule;

import dev.b3monitor.schedule.TradingCalendarImporter.Dataset;
import dev.b3monitor.schedule.TradingCalendarImporter.SessionWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Versioned trading-session calendar (cycle-7 item G, consolidated cycle-9): the importer validates a
 * dataset and only then builds a calendar; the production default invents nothing (UNKNOWN/"none");
 * session windows come from the dataset (NOT hardcoded); market dates derive via the dataset timezone.
 * All session hours here are explicitly TEST-ONLY synthetic values.
 */
class TradingCalendarImporterTest {

    private final DefaultTradingCalendarImporter importer = new DefaultTradingCalendarImporter();

    // TEST-ONLY synthetic session window (10:00–17:00 local) — not an authoritative B3 dataset.
    private static final SessionWindow REGULAR = new SessionWindow(LocalTime.of(10, 0), LocalTime.of(17, 0));
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private Dataset validDataset() {
        return new Dataset(
                "test-2026", "unit-test-fixture", SP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                REGULAR,
                Set.of(LocalDate.of(2026, 12, 25)),                                  // holiday
                Map.of(LocalDate.of(2026, 12, 24), new SessionWindow(LocalTime.of(10, 0), LocalTime.of(13, 0)))); // special half-day
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
        assertFalse(importer.validate(new Dataset(
                " ", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR, Set.of(), Map.of())).valid(),
                "blank version");
        assertFalse(importer.validate(new Dataset(
                "v", " ", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR, Set.of(), Map.of())).valid(),
                "blank source/provenance");
        assertFalse(importer.validate(new Dataset(
                "v", "src", null, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR, Set.of(), Map.of())).valid(),
                "null timezone");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,12,31), LocalDate.of(2026,1,1), REGULAR, Set.of(), Map.of())).valid(),
                "end before start");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), null, Set.of(), Map.of())).valid(),
                "missing regular session window");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
                new SessionWindow(LocalTime.of(17,0), LocalTime.of(10,0)), Set.of(), Map.of())).valid(),
                "regular window open >= close");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR,
                Set.of(LocalDate.of(2027,1,1)), Map.of())).valid(), "holiday outside coverage");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR, Set.of(),
                Map.of(LocalDate.of(2026,6,10), new SessionWindow(LocalTime.of(13,0), LocalTime.of(10,0))))).valid(),
                "special window open >= close");
        assertFalse(importer.validate(new Dataset(
                "v", "src", SP, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), REGULAR,
                Set.of(LocalDate.of(2026,6,10)),
                Map.of(LocalDate.of(2026,6,10), new SessionWindow(LocalTime.of(10,0), LocalTime.of(13,0))))).valid(),
                "date is both holiday and special session");
    }

    @Test
    void buildRejectsInvalidDataset() {
        var bad = new Dataset("v", "src", SP, LocalDate.of(2026,12,31), LocalDate.of(2026,1,1),
                REGULAR, Set.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> importer.build(bad));
    }

    @Test
    void builtCalendarClassifiesSessionsViaDatasetZoneAndWindows() {
        var cal = importer.build(validDataset());
        assertEquals("test-2026", cal.datasetVersion());
        assertEquals(SP, cal.zone());
        // 2026-06-15 is a Monday; 13:00Z = 10:00 in Sao_Paulo (UTC-3) → OPEN (regular window from dataset)
        assertEquals(TradingSessionCalendar.SessionStatus.OPEN,
                cal.statusAt(Instant.parse("2026-06-15T13:00:00Z")));
        // 2026-06-15 at 21:00Z = 18:00 Sao_Paulo → after close → CLOSED
        assertEquals(TradingSessionCalendar.SessionStatus.CLOSED,
                cal.statusAt(Instant.parse("2026-06-15T21:00:00Z")));
        // Christmas holiday → HOLIDAY (during hours)
        assertEquals(TradingSessionCalendar.SessionStatus.HOLIDAY,
                cal.statusAt(Instant.parse("2026-12-25T14:00:00Z")));
        // special half-day (10:00–13:00): 14:00Z = 11:00 SP → inside special window → SPECIAL
        assertEquals(TradingSessionCalendar.SessionStatus.SPECIAL,
                cal.statusAt(Instant.parse("2026-12-24T14:00:00Z")));
        // special half-day but 17:00Z = 14:00 SP → after the special close → CLOSED (not regular hours)
        assertEquals(TradingSessionCalendar.SessionStatus.CLOSED,
                cal.statusAt(Instant.parse("2026-12-24T17:00:00Z")));
        // weekend → CLOSED (2026-06-13 is Saturday)
        assertEquals(TradingSessionCalendar.SessionStatus.CLOSED,
                cal.statusAt(Instant.parse("2026-06-13T14:00:00Z")));
        // outside coverage → UNKNOWN (fail-closed)
        assertEquals(TradingSessionCalendar.SessionStatus.UNKNOWN,
                cal.statusAt(Instant.parse("2025-06-15T13:00:00Z")));
    }
}
