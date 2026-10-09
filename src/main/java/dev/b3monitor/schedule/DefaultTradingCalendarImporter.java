package dev.b3monitor.schedule;

import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Map;

/**
 * Default {@link TradingCalendarImporter}: validates a dataset and, only on success, builds a
 * {@link TradingSessionCalendar} that derives the market date via the dataset's own timezone. It ships
 * NO production data; a validated {@link Dataset} must be supplied (operator import or a test fixture).
 *
 * <h2>Validation (cycle-9 item G — the implementation now matches the documented claims)</h2>
 * Rejects a dataset with: a blank version or source; a null timezone; an incoherent/empty coverage
 * window; a null/invalid regular-session window; a holiday or special date outside coverage; a date
 * that is BOTH a holiday and a special session (conflicting definition); a special session with a null
 * or non-increasing (open ≥ close) window. There are NO hardcoded B3 session hours here — the window
 * comes entirely from the data.
 */
@Component
public class DefaultTradingCalendarImporter implements TradingCalendarImporter {

    @Override
    public Validation validate(Dataset d) {
        if (d == null) return new Validation(false, "null dataset");
        if (d.version() == null || d.version().isBlank()) return new Validation(false, "missing version");
        if (d.source() == null || d.source().isBlank()) return new Validation(false, "missing source/provenance");
        if (d.zone() == null) return new Validation(false, "missing timezone");
        if (d.coverageStart() == null || d.coverageEnd() == null || d.coverageEnd().isBefore(d.coverageStart()))
            return new Validation(false, "incoherent coverage window");
        if (d.regularSession() == null) return new Validation(false, "missing regular session window");
        if (!d.regularSession().isValid())
            return new Validation(false, "invalid regular session window (open >= close)");
        if (d.holidays() == null || d.specialSessions() == null)
            return new Validation(false, "null holiday/special sets");
        for (LocalDate h : d.holidays()) {
            if (outOfCoverage(d, h)) return new Validation(false, "holiday outside coverage: " + h);
        }
        for (Map.Entry<LocalDate, SessionWindow> e : d.specialSessions().entrySet()) {
            LocalDate date = e.getKey();
            SessionWindow w = e.getValue();
            if (outOfCoverage(d, date)) return new Validation(false, "special session outside coverage: " + date);
            if (w == null) return new Validation(false, "special session without window: " + date);
            if (!w.isValid()) return new Validation(false, "invalid special session window (open >= close): " + date);
            if (d.holidays().contains(date))
                return new Validation(false, "date is both holiday and special session: " + date);
        }
        return new Validation(true, "ok");
    }

    private static boolean outOfCoverage(Dataset d, LocalDate date) {
        return date.isBefore(d.coverageStart()) || date.isAfter(d.coverageEnd());
    }

    @Override
    public TradingSessionCalendar build(Dataset d) {
        Validation v = validate(d);
        if (!v.valid()) throw new IllegalArgumentException("invalid dataset: " + v.reason());
        return new DatasetCalendar(d);
    }

    /** A calendar backed by a validated dataset. Outside coverage → UNKNOWN (fail-closed). All session
     *  windows come from the dataset — no hardcoded hours (cycle-9 item G). */
    static final class DatasetCalendar implements TradingSessionCalendar {
        private final Dataset d;
        DatasetCalendar(Dataset d) { this.d = d; }

        @Override public ZoneId zone() { return d.zone(); }
        @Override public String datasetVersion() { return d.version(); }

        @Override public SessionStatus statusAt(Instant at) {
            ZonedDateTime z = at.atZone(zone());
            LocalDate date = z.toLocalDate();
            if (date.isBefore(d.coverageStart()) || date.isAfter(d.coverageEnd())) {
                return SessionStatus.UNKNOWN;                 // outside validated coverage
            }
            if (d.holidays().contains(date)) return SessionStatus.HOLIDAY;
            LocalTime t = z.toLocalTime();
            SessionWindow special = d.specialSessions().get(date);
            if (special != null) {
                // A special date ALWAYS has an explicit window (enforced by validate()).
                return special.contains(t) ? SessionStatus.SPECIAL : SessionStatus.CLOSED;
            }
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return SessionStatus.CLOSED;
            return d.regularSession().contains(t) ? SessionStatus.OPEN : SessionStatus.CLOSED;
        }
    }
}
