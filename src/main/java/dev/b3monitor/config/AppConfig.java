package dev.b3monitor.config;

import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.RuleEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Core beans. A single {@link Clock} is injected everywhere freshness is measured, so
 * tests can supply a fixed clock and freshness is never read from {@code Instant.now()}
 * implicitly.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public QuoteValidator quoteValidator(
            Clock clock,
            @Value("${b3monitor.quote.max-age-seconds:2700}") long maxAgeSeconds,     // 45 min hard guard
            @Value("${b3monitor.quote.max-skew-seconds:120}") long maxSkewSeconds) {  // 2 min future skew
        return new QuoteValidator(clock, Duration.ofSeconds(maxAgeSeconds), Duration.ofSeconds(maxSkewSeconds));
    }

    @Bean
    public RuleEvaluator ruleEvaluator(QuoteValidator quoteValidator) {
        return new RuleEvaluator(quoteValidator);
    }
}
