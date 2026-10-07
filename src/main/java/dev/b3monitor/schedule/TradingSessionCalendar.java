package dev.b3monitor.schedule;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Versioned B3 trading-session calendar (cycle-7 item G — CAL-01 groundwork). Unlike the simple
 * {@link TradingCalendar} (OPEN/CLOSED/UNKNOWN), this models the full session taxonomy and carries a
 * dataset VERSION so a calendar derived from an audited holiday/session table is distinguishable from
 * the fail-closed default. Market-date boundaries are derived through {@link #zone()}
 * (America/Sao_Paulo), not the server's local zone.
 *
 * <p>Fail-closed: with no validated dataset, every query is {@link SessionStatus#UNKNOWN} and the
 * {@link #datasetVersion()} is {@code "none"}. Holidays are NEVER invented — see
 * {@code UnavailableTradingSessionCalendar} and the importer contract.
 */
public interface TradingSessionCalendar {

    enum SessionStatus { OPEN, CLOSED, HOLIDAY, SPECIAL, UNKNOWN }

    /** Market timezone used to derive the market date from an instant. */
    default ZoneId zone() { return ZoneId.of("America/Sao_Paulo"); }

    /** Identifier/version of the loaded dataset, or {@code "none"} when unavailable (fail-closed). */
    String datasetVersion();

    /** Session status for the market date/time at {@code at}. UNKNOWN when no validated dataset. */
    SessionStatus statusAt(Instant at);
}
