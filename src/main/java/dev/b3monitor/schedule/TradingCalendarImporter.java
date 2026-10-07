package dev.b3monitor.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * Contract for importing + VALIDATING an audited B3/ANBIMA holiday/session dataset into a usable
 * {@link TradingSessionCalendar} (cycle-7 item G, completed cycle-9). This is the ONLY sanctioned path
 * from raw data to a non-UNKNOWN calendar: a dataset must pass {@link #validate} before a calendar is
 * built. No implementation ships an actual production dataset — holidays and session windows are NEVER
 * invented. A test fixture may supply a small validated dataset (explicitly labelled test-only) to
 * exercise the taxonomy deterministically.
 *
 * <p>The dataset carries, besides the version:
 * <ul>
 *   <li>a {@code source} / provenance identifier (where the data came from);</li>
 *   <li>a {@code zone} (market timezone used to derive the market date);</li>
 *   <li>a coverage window ({@code coverageStart}..{@code coverageEnd});</li>
 *   <li>the REGULAR session window ({@code regularSession}) — there are NO hardcoded B3 hours in
 *       production code; the window lives in the data;</li>
 *   <li>{@code specialSessions}: a map from a special-session date to its EXPLICIT window (a special
 *       date without a window is invalid — never a silent fallback to regular hours);</li>
 *   <li>{@code holidays}: closed days within coverage.</li>
 * </ul>
 */
public interface TradingCalendarImporter {

    /** A half-open local-time session window [open, close). */
    record SessionWindow(LocalTime open, LocalTime close) {
        public SessionWindow {
            if (open == null || close == null) throw new IllegalArgumentException("window open/close required");
        }
        public boolean isValid() { return open.isBefore(close); }
        public boolean contains(LocalTime t) { return !t.isBefore(open) && t.isBefore(close); }
    }

    /**
     * The parsed dataset. {@code specialSessions} maps each special date to its explicit window;
     * {@code holidays} are closed days. Both must lie within coverage; neither may be null.
     */
    record Dataset(String version, String source, ZoneId zone,
                   LocalDate coverageStart, LocalDate coverageEnd,
                   SessionWindow regularSession,
                   Set<LocalDate> holidays,
                   Map<LocalDate, SessionWindow> specialSessions) {}

    /** Outcome of validation: whether the dataset may be used, with a reason when rejected. */
    record Validation(boolean valid, String reason) {}

    /** Validate a dataset before use. A rejected dataset must NOT be turned into a calendar. */
    Validation validate(Dataset dataset);

    /** Build a calendar from a dataset that has passed {@link #validate}. Throws if it has not. */
    TradingSessionCalendar build(Dataset dataset);
}
