package dev.b3monitor.schedule;

import org.springframework.stereotype.Component;

/**
 * Brazilian B3 trading calendar. The default implementation is deliberately CONSERVATIVE: with no
 * authoritative holiday/session dataset wired in, it returns {@link Status#UNKNOWN} rather than
 * inventing market-open. The review requires: "evaluate the Brazilian calendar/holidays as UNKNOWN
 * if data is not available; do not invent opening, availability or guaranteed latency objectives."
 *
 * <p>A real implementation would load an audited ANBIMA/B3 holiday table and session hours. Until
 * that evidence exists, UNKNOWN is the honest answer and the scheduler fails closed (does not poll).
 */
@Component
public class TradingCalendar {

    public enum Status { OPEN, CLOSED, UNKNOWN }

    /** No authoritative dataset configured → UNKNOWN (fail-closed). */
    public Status isTradingNow() {
        return Status.UNKNOWN;
    }
}
