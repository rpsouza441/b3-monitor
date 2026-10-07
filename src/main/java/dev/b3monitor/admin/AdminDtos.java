package dev.b3monitor.admin;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Explicit admin DTOs (cycle-10 item F). Controllers return/consume these records ONLY — never JPA
 * entities — so no mutable persistence object or provider secret is serialized. Nothing here carries a
 * Brapi token, WAHA session, payload body or credential.
 */
public final class AdminDtos {

    private AdminDtos() {}

    // ---- read models ----

    public record StatusView(boolean workersEnabled, String calendarDatasetVersion,
                             String calendarSessionStatus, long rulesTotal, long rulesActive,
                             long sessionTimeoutSeconds) {}

    public record RuleView(String ruleId, String ticker, Comparator comparator, BigDecimal threshold,
                           int precision, BigDecimal hysteresis, RuleMode mode, long revision,
                           boolean enabled, boolean paused, Instant createdAt, Instant updatedAt) {}

    public record OutboxMetricsView(long pending, long inFlight, long sending, long accepted,
                                    long unknownOutcome, long failed, long cancelled, long expired,
                                    long suppressed, Long oldestPendingAgeSeconds) {}

    /** Sanitized reconciliation row: logical key / rule / ticker / state / timestamps / attempts /
     *  sanitized status only. No message body, no provider credential. */
    public record ReconciliationRowView(String logicalKey, String ruleId, String ticker, String state,
                                        long ruleRevision, Instant intentCreatedAt, Instant sendStartedAt,
                                        Instant attemptFinishedAt, int attempts, String suppressionReason) {}

    public record QuoteFreshnessView(String requestedTicker, String returnedTicker, boolean remapped,
                                     String currency, BigDecimal price, Instant sourceTime, Instant receiptTime,
                                     boolean providerStale, boolean eligible, Long ageSeconds,
                                     String rejectionReasons, String providerContract) {}

    public record AuditEventView(Instant occurredAt, String actor, String action, String ruleId,
                                 Long beforeRevision, Long afterRevision, String outcome, String detail) {}

    /** UI-01 (cycle-12 E): per-asset freshness. {@code hasObservation=false} ⇒ everything UNKNOWN. The
     *  daily-indicator / Python-context fields are explicit readiness markers, NEVER synthesized values. */
    public record AssetFreshnessView(String ticker, String authorizationStatus, boolean hasObservation,
                                     String requestedTicker, String returnedTicker, boolean remapped,
                                     String currency, BigDecimal price, Instant sourceTime, Instant receiptTime,
                                     Long ageSeconds, boolean providerStale, boolean eligible,
                                     String rejectionReasons, String providerContract,
                                     String dailyIndicators, String pythonContext) {}

    /** UI-03 (cycle-12 F): one logical alert + its transport state + bounded attempt lineage. ACCEPTED is
     *  NOT delivery; UNKNOWN_OUTCOME is NOT delivered; FAILED/dead-letter is NOT delivered. */
    public record AlertOutcomeView(
            // logical alert
            String logicalKey, String ruleId, String ticker, long ruleRevision, long episodeEpoch,
            Instant sourceAsOf, Instant intentCreatedAt,
            // transport / channel state
            String state, boolean deliveryConfirmed, String providerMessageId, String suppressionReason,
            boolean uncertain,
            // attempt lineage (bounded)
            List<AttemptView> attempts) {}

    public record AttemptView(long claimGeneration, long fencingToken, Instant startedAt, Instant finishedAt,
                              String outcome, String sanitizedStatus, String providerMessageId,
                              Instant providerAcceptedAt) {}

    public record AlertOutcomeListView(List<AlertOutcomeView> alerts) {}
    public record AssetFreshnessListView(List<AssetFreshnessView> assets) {}

    // ---- request models ----

    public record CreateRuleRequest(String ruleId, String ticker, Comparator comparator,
                                    BigDecimal threshold, Integer precision, BigDecimal hysteresis) {}

    public record EditRuleRequest(long expectedRevision, Comparator comparator, BigDecimal threshold,
                                  Integer precision, BigDecimal hysteresis) {}

    public record SelectModeRequest(RuleMode mode) {}

    // ---- generic responses ----

    public record RevisionResponse(String ruleId, long revision) {}
    public record MessageResponse(String message) {}
    public record RuleListView(List<RuleView> rules) {}
    public record ReconciliationView(List<ReconciliationRowView> rows) {}
}
