package dev.b3monitor.domain.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class QuoteValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final QuoteValidator validator =
            new QuoteValidator(clock, Duration.ofMinutes(45), Duration.ofMinutes(2));

    private Quote quote(String reqTicker, String retTicker, String ccy, BigDecimal price,
                        Instant src, boolean stale) {
        return new Quote(reqTicker, retTicker, false, ccy, price, new BigDecimal("10.00"),
                LocalDate.parse("2026-10-03"), src, NOW, stale);
    }

    @Test
    void eligibleQuotePasses() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("49.75"), NOW.minusSeconds(60), false);
        assertTrue(validator.validate(q).eligible());
    }

    @Test
    void identityMismatchRejected() {
        Quote q = quote("WEGE3", "WEGE4", "BRL", new BigDecimal("49.75"), NOW.minusSeconds(60), false);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.IDENTITY_MISMATCH));
    }

    @Test
    void wrongCurrencyRejected() {
        Quote q = quote("WEGE3", "WEGE3", "USD", new BigDecimal("49.75"), NOW.minusSeconds(60), false);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.WRONG_CURRENCY));
    }

    @Test
    void nonPositivePriceRejected() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("0.00"), NOW.minusSeconds(60), false);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.NON_POSITIVE_PRICE));
    }

    @Test
    void missingSourceTimeFailsClosed() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("49.75"), null, false);
        QuoteValidation v = validator.validate(q);
        assertFalse(v.eligible());
        assertTrue(v.hasReason(QuoteRejectionReason.MISSING_SOURCE_TIME));
    }

    @Test
    void futureSourceTimeRejected() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("49.75"), NOW.plusSeconds(300), false);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.FUTURE_SOURCE_TIME));
    }

    @Test
    void staleSourceTimeRejected() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("49.75"), NOW.minus(Duration.ofMinutes(46)), false);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.STALE_SOURCE_TIME));
    }

    @Test
    void providerStaleFlagRejected() {
        Quote q = quote("WEGE3", "WEGE3", "BRL", new BigDecimal("49.75"), NOW.minusSeconds(60), true);
        assertTrue(validator.validate(q).hasReason(QuoteRejectionReason.PROVIDER_STALE_FLAG));
    }

    @Test
    void multipleReasonsAllReported() {
        Quote q = quote("WEGE3", "PETR4", "USD", new BigDecimal("-1"), null, true);
        QuoteValidation v = validator.validate(q);
        assertAll(
                () -> assertTrue(v.hasReason(QuoteRejectionReason.IDENTITY_MISMATCH)),
                () -> assertTrue(v.hasReason(QuoteRejectionReason.WRONG_CURRENCY)),
                () -> assertTrue(v.hasReason(QuoteRejectionReason.NON_POSITIVE_PRICE)),
                () -> assertTrue(v.hasReason(QuoteRejectionReason.MISSING_SOURCE_TIME)),
                () -> assertTrue(v.hasReason(QuoteRejectionReason.PROVIDER_STALE_FLAG))
        );
    }
}
