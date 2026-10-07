package dev.b3monitor.domain.change;

import dev.b3monitor.domain.quote.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChangeCalculatorTest {

    private Quote q(BigDecimal price, BigDecimal prevClose, LocalDate prevDate) {
        return new Quote("BPAC11", "BPAC11", false, "BRL", price, prevClose, prevDate,
                Instant.parse("2026-10-06T17:00:00Z"), Instant.parse("2026-10-06T17:00:01Z"), false);
    }

    @Test
    void bpac11FixtureMatchesIndependentCalc() {
        // ADR-011 / Phase 3 fixture: price 82.92, previousClose 82.93 → ~ -0.012058%, NOT +25.6%.
        Quote quote = q(new BigDecimal("82.92"), new BigDecimal("82.93"), LocalDate.parse("2026-10-03"));
        BigDecimal pct = ChangeCalculator.percentChange(quote).orElseThrow();
        assertEquals(-0.012058, pct.doubleValue(), 1e-6);
        BigDecimal abs = ChangeCalculator.absoluteChange(quote).orElseThrow();
        assertEquals(-0.01, abs.doubleValue(), 1e-9);
    }

    @Test
    void unknownPreviousCloseFailsClosed() {
        Quote quote = q(new BigDecimal("82.92"), null, LocalDate.parse("2026-10-03"));
        assertEquals(Optional.empty(), ChangeCalculator.percentChange(quote));
        assertEquals(Optional.empty(), ChangeCalculator.absoluteChange(quote));
    }

    @Test
    void missingPreviousCloseDateFailsClosed() {
        Quote quote = q(new BigDecimal("82.92"), new BigDecimal("82.93"), null);
        assertEquals(Optional.empty(), ChangeCalculator.percentChange(quote));
    }

    @Test
    void nonPositivePreviousCloseFailsClosed() {
        Quote quote = q(new BigDecimal("82.92"), new BigDecimal("0.00"), LocalDate.parse("2026-10-03"));
        assertEquals(Optional.empty(), ChangeCalculator.percentChange(quote));
    }

    @Test
    void positiveChangeComputed() {
        Quote quote = q(new BigDecimal("110.00"), new BigDecimal("100.00"), LocalDate.parse("2026-10-03"));
        assertEquals(10.0, ChangeCalculator.percentChange(quote).orElseThrow().doubleValue(), 1e-9);
    }
}
