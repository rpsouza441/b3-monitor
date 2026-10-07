package dev.b3monitor.schedule;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Production {@link TradingSessionCalendar} default: NO validated dataset is wired in, so every query
 * is {@link SessionStatus#UNKNOWN} and the dataset version is {@code "none"} (cycle-7 item G). It
 * invents no holidays or sessions. A real calendar is supplied by importing an audited B3/ANBIMA
 * dataset through {@link TradingCalendarImporter}; until that import is validated, the scheduler fails
 * closed (does not poll).
 */
@Component
public class UnavailableTradingSessionCalendar implements TradingSessionCalendar {

    @Override
    public String datasetVersion() { return "none"; }

    @Override
    public SessionStatus statusAt(Instant at) { return SessionStatus.UNKNOWN; }
}
