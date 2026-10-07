package dev.b3monitor.schedule;

import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Set;

/**
 * Default {@link TradingCalendarImporter}: validates a dataset (non-null version, coherent coverage
 * window, holidays within coverage) and, only on success, builds a {@link TradingSessionCalendar} that
 * derives the market date via America/Sao_Paulo. It ships NO data; a validated {@link Dataset} must be
 * supplied (e.g. by an operator import or a test fixture). An invalid dataset is refused and never
 * becomes a calendar.
 */
@Component
public class DefaultTradingCalendarImporter implements TradingCalendarImporter {

    private static final LocalTime OPEN = LocalTime.of(10, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);

    @Override
    public Validation validate(Dataset d) {
        if (d == null || d.version() == null || d.version().isBlank())
            return new Validation(false, "missing version");
        if (d.coverageStart() == null || d.coverageEnd() == null || d.coverageEnd().isBefore(d.coverageStart()))
            return new Validation(false, "incoherent coverage window");
        if (d.holidays() == null || d.specialSessions() == null)
            return new Validation(false, "null holiday/special sets");
        for (LocalDate h : d.holidays()) {
            if (h.isBefore(d.coverageStart()) || h.isAfter(d.coverageEnd()))
                return new Validation(false, "holiday outside coverage: " + h);
        }
        return new Validation(true, "ok");
    }

    @Override
    public TradingSessionCalendar build(Dataset d) {
        Validation v = validate(d);
        if (!v.valid()) throw new IllegalArgumentException("invalid dataset: " + v.reason());
        return new DatasetCalendar(d);
    }

    /** A calendar backed by a validated dataset. Outside coverage → UNKNOWN (fail-closed). */
    static final class DatasetCalendar implements TradingSessionCalendar {
        private final Dataset d;
        DatasetCalendar(Dataset d) { this.d = d; }

        @Override public String datasetVersion() { return d.version(); }

        @Override public SessionStatus statusAt(Instant at) {
            ZonedDateTime z = at.atZone(zone());
            LocalDate date = z.toLocalDate();
            if (date.isBefore(d.coverageStart()) || date.isAfter(d.coverageEnd())) {
                return SessionStatus.UNKNOWN;                 // outside validated coverage
            }
            if (d.holidays().contains(date)) return SessionStatus.HOLIDAY;
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return SessionStatus.CLOSED;
            LocalTime t = z.toLocalTime();
            boolean inHours = !t.isBefore(OPEN) && t.isBefore(CLOSE);
            if (d.specialSessions().contains(date)) return inHours ? SessionStatus.SPECIAL : SessionStatus.CLOSED;
            return inHours ? SessionStatus.OPEN : SessionStatus.CLOSED;
        }
    }
}
