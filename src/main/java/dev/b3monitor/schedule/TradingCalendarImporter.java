package dev.b3monitor.schedule;

import java.time.LocalDate;
import java.util.Set;

/**
 * Contract for importing + VALIDATING an audited B3/ANBIMA holiday/session dataset into a usable
 * {@link TradingSessionCalendar} (cycle-7 item G). This is the ONLY sanctioned path from raw data to a
 * non-UNKNOWN calendar: a dataset must pass {@link #validate} (which fails on gaps, duplicates, or an
 * out-of-range coverage window) before a calendar is built. No implementation ships an actual dataset
 * — holidays are never invented. A test fixture may supply a small validated dataset to exercise the
 * taxonomy deterministically.
 */
public interface TradingCalendarImporter {

    /** The parsed dataset: a version tag, its coverage window, and the holiday / special-session days. */
    record Dataset(String version, LocalDate coverageStart, LocalDate coverageEnd,
                   Set<LocalDate> holidays, Set<LocalDate> specialSessions) {}

    /** Outcome of validation: whether the dataset may be used, with a reason when rejected. */
    record Validation(boolean valid, String reason) {}

    /** Validate a dataset before use. A rejected dataset must NOT be turned into a calendar. */
    Validation validate(Dataset dataset);

    /** Build a calendar from a dataset that has passed {@link #validate}. Throws if it has not. */
    TradingSessionCalendar build(Dataset dataset);
}
