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

    @Bean
    public dev.b3monitor.domain.analytics.AnalyticsSnapshotValidator analyticsSnapshotValidator(
            dev.b3monitor.domain.auth.AssetCatalog catalog, Clock clock,
            tools.jackson.databind.ObjectMapper mapper) {
        return new dev.b3monitor.domain.analytics.AnalyticsSnapshotValidator(catalog, clock, mapper);
    }

    /**
     * HMAC secret for the analytics preview→commit token (cycle-17). In a local/offline milestone a
     * stable per-process random secret is sufficient and safest: a token is only valid within the process
     * that issued it (preview and commit are the same short-lived admin session), nothing durable depends
     * on it, and no secret is committed to config. An operator MAY pin a secret via
     * {@code b3monitor.admin.analytics.token-secret} (>= 16 chars) for multi-instance stability.
     */
    @Bean
    public dev.b3monitor.admin.AnalyticsPreviewToken analyticsPreviewToken(
            Clock clock,
            @Value("${b3monitor.admin.analytics.token-secret:}") String configuredSecret,
            @Value("${b3monitor.admin.analytics.token-ttl-seconds:900}") long ttlSeconds) {
        byte[] secret;
        if (configuredSecret != null && configuredSecret.length() >= 16) {
            secret = configuredSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            secret = new byte[32];
            new java.security.SecureRandom().nextBytes(secret);   // per-process ephemeral secret
        }
        return new dev.b3monitor.admin.AnalyticsPreviewToken(secret, clock,
                java.time.Duration.ofSeconds(Math.max(60, ttlSeconds)));
    }
}
