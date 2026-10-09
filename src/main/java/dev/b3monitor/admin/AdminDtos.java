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

    /** UI-03 (cycle-12 F; completed cycle-13 P1-A): one logical alert + its transport state + bounded
     *  attempt lineage, across the FULL lifecycle (not only dead-letters). ACCEPTED is NOT delivery;
     *  {@code deliveryConfirmed} is separate; UNKNOWN_OUTCOME is NOT delivered; FAILED/CANCELLED/EXPIRED/
     *  SUPPRESSED are NOT successful transport. */
    public record AlertOutcomeView(
            // logical alert
            String logicalKey, String ruleId, String ticker, long ruleRevision, long episodeEpoch,
            Instant sourceAsOf, Instant intentCreatedAt,
            // transport / channel state
            String state, boolean deliveryConfirmed, Instant acceptedAt, String providerMessageId,
            String suppressionReason, Instant sendStartedAt, Instant attemptFinishedAt, boolean uncertain,
            // attempt lineage (bounded)
            List<AttemptView> attempts) {}

    public record AttemptView(long claimGeneration, long fencingToken, Instant startedAt, Instant finishedAt,
                              String outcome, String sanitizedStatus, String providerMessageId,
                              Instant providerAcceptedAt) {}

    public record AlertOutcomeListView(List<AlertOutcomeView> alerts) {}
    public record AssetFreshnessListView(List<AssetFreshnessView> assets) {}

    /** UI-02 (cycle-16): one bounded import-history row — provenance only, checksum shown as a prefix. */
    public record ImportHistoryView(String snapshotId, String producer, String producerVersion,
                                    String schemaVersion, String marketAsOf, Instant generatedAt,
                                    Instant importedAt, String importedBy, int recordCount,
                                    String checksumPrefix, String status) {}

    public record ImportHistoryListView(List<ImportHistoryView> imports) {}

    /** UI-01 (cycle-16): one asset's analytics context, SEPARATE from quote freshness. {@code present=false}
     *  ⇒ no snapshot imported for this asset (explicit no-data). Indicators carry their own readiness; nothing
     *  is synthesized, and this is CONTEXT only — never a rule input or operational authorization. */
    public record AnalyticsContextView(String ticker, boolean present,
                                       String snapshotId, String producer, String producerVersion,
                                       String schemaVersion, String analyticsAsOf, Instant importedAt,
                                       Long analyticsAgeSeconds, String stalePolicy,
                                       BigDecimal sma20, String sma20Readiness,
                                       BigDecimal sma50, String sma50Readiness,
                                       BigDecimal rsi14, String rsi14Readiness,
                                       BigDecimal ema9, String ema9Readiness,
                                       BigDecimal ema21, String ema21Readiness,
                                       BigDecimal volumeRatio, String volumeRatioReadiness,
                                       String contextMetrics, String quality, String status,
                                       String integrationStatus) {}

    public record AnalyticsContextListView(List<AnalyticsContextView> rows) {}

    /** UI-02 (cycle-14 E; refined cycle-15 D): a READ-ONLY operator readiness snapshot. Each component
     *  reports THREE INDEPENDENT dimensions so code integration, operational authorization and runtime
     *  verification are never conflated:
     *  <ul>
     *    <li>{@code wiringStatus}: READY | PARTIAL | NOT_INTEGRATED — is the code actually wired?</li>
     *    <li>{@code operationalStatus}: AUTHORIZED | BLOCKED_BY_GATE | NOT_APPLICABLE — is it allowed to act?</li>
     *    <li>{@code runtimeStatus}: VERIFIED | NOT_RUN | NOT_VERIFIED | NOT_APPLICABLE — has it been proven at runtime?</li>
     *  </ul>
     *  plus an explicit human-readable {@code detail}. NOTHING here activates, imports, polls or sends — it
     *  only makes operator state and its gaps visible, and never overstates readiness. */
    public record ReadinessComponentView(String component, String wiringStatus, String operationalStatus,
                                         String runtimeStatus, String detail) {}

    public record ReadinessView(
            long catalogAssets,
            long assetsAuthorized, long assetsPartial, long assetsQuarantined, long assetsNotAuthorized,
            boolean workersEnabled,
            String calendarDatasetVersion, String calendarZone, String calendarReadiness,
            long rulesTotal, long rulesOperable, long rulesPaused,
            List<ReadinessComponentView> components) {}

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
