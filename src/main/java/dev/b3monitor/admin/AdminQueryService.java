package dev.b3monitor.admin;

import dev.b3monitor.admin.AdminDtos.*;
import dev.b3monitor.persistence.*;
import dev.b3monitor.schedule.TradingSessionCalendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Read-side admin queries (cycle-10 items D/F). Builds sanitized DTOs from the domain services and
 * repositories; it never returns a JPA entity and never exposes a provider token / payload body.
 */
@Service
public class AdminQueryService {

    private final RuleAdminService rules;
    private final RuleDefinitionRepository ruleDefs;
    private final OutboxReconciliationService reconciliation;
    private final OutboxRepository outbox;
    private final QuoteObservationRepository observations;
    private final AdminAuditRepository auditRepo;
    private final TradingSessionCalendar calendar;
    private final AdminProperties adminProps;
    private final dev.b3monitor.domain.auth.AssetCatalog catalog;
    private final dev.b3monitor.domain.auth.OperationalAuthorization authorization;
    private final OutboxAttemptRepository attempts;
    private final Clock clock;
    private final boolean workersEnabled;

    public AdminQueryService(RuleAdminService rules, RuleDefinitionRepository ruleDefs,
                             OutboxReconciliationService reconciliation, OutboxRepository outbox,
                             QuoteObservationRepository observations, AdminAuditRepository auditRepo,
                             TradingSessionCalendar calendar, AdminProperties adminProps,
                             dev.b3monitor.domain.auth.AssetCatalog catalog,
                             dev.b3monitor.domain.auth.OperationalAuthorization authorization,
                             OutboxAttemptRepository attempts, Clock clock,
                             @Value("${b3monitor.workers.enabled:false}") boolean workersEnabled) {
        this.rules = rules;
        this.ruleDefs = ruleDefs;
        this.reconciliation = reconciliation;
        this.outbox = outbox;
        this.observations = observations;
        this.auditRepo = auditRepo;
        this.calendar = calendar;
        this.adminProps = adminProps;
        this.catalog = catalog;
        this.authorization = authorization;
        this.attempts = attempts;
        this.clock = clock;
        this.workersEnabled = workersEnabled;
    }

    @Transactional(readOnly = true)
    public StatusView status() {
        long total = ruleDefs.count();
        long active = ruleDefs.findByEnabledTrueAndPausedFalse().stream()
                .filter(e -> e.getMode().isOperable()).count();
        String session = calendar.statusAt(clock.instant()).name();
        return new StatusView(workersEnabled, calendar.datasetVersion(), session, total, active,
                adminProps.effectiveSessionTimeout().getSeconds());
    }

    @Transactional(readOnly = true)
    public RuleListView rules() {
        return new RuleListView(rules.list().stream().map(AdminQueryService::toRuleView).toList());
    }

    @Transactional(readOnly = true)
    public Optional<RuleView> rule(String ruleId) {
        return rules.find(ruleId).map(AdminQueryService::toRuleView);
    }

    @Transactional(readOnly = true)
    public OutboxMetricsView outboxMetrics() {
        var m = reconciliation.metrics();
        Long oldest = m.oldestPendingAge() == null || m.oldestPendingAge().isZero()
                ? null : m.oldestPendingAge().getSeconds();
        return new OutboxMetricsView(m.pending(), m.inFlight(), m.sending(), m.accepted(),
                m.unknownOutcome(), m.failed(), m.cancelled(), m.expired(), m.suppressed(), oldest);
    }

    @Transactional(readOnly = true)
    public ReconciliationView reconciliation(int limit) {
        List<ReconciliationRowView> rows = reconciliation.deadLetters(limit).stream()
                .map(AdminQueryService::toReconciliationRow).toList();
        return new ReconciliationView(rows);
    }

    @Transactional(readOnly = true)
    public Optional<QuoteFreshnessView> latestQuote(String ticker) {
        return observations.findFirstByRequestedTickerOrderByReceiptTimeDesc(ticker).map(o -> {
            Long ageSeconds = o.getSourceTime() == null ? null
                    : Duration.between(o.getSourceTime(), clock.instant()).getSeconds();
            return new QuoteFreshnessView(
                    o.getRequestedTicker(), o.getReturnedTicker(), o.isProviderRemapped(), o.getCurrency(),
                    o.getPrice(), o.getSourceTime(), o.getReceiptTime(), o.isProviderStale(), o.isEligible(),
                    ageSeconds, o.getRejectionReasons(), o.getProviderContract());
        });
    }

    @Transactional(readOnly = true)
    public List<dev.b3monitor.admin.AdminDtos.AuditEventView> audit(int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return auditRepo.findByOrderByOccurredAtDescIdDesc(
                        org.springframework.data.domain.PageRequest.of(0, capped)).stream()
                .map(a -> new dev.b3monitor.admin.AdminDtos.AuditEventView(
                        a.getOccurredAt(), a.getActor(), a.getAction().name(), a.getRuleId(),
                        a.getBeforeRevision(), a.getAfterRevision(), a.getOutcome().name(), a.getDetail()))
                .toList();
    }

    /**
     * UI-01 (cycle-12 E): one row per CANONICAL catalog asset (bounded — 23 single-row latest-observation
     * lookups, no N+1 over history). No observation ⇒ an explicit UNKNOWN row. Daily indicators and Python
     * context are NOT integrated, so they are reported as explicit readiness markers, never synthesized.
     */
    @Transactional(readOnly = true)
    public AssetFreshnessListView assetFreshness() {
        List<AssetFreshnessView> rows = catalog.tickers().stream().map(ticker -> {
            String auth = authorization.evaluate(
                    new dev.b3monitor.domain.rule.PriceRule("ui-probe", ticker,
                            dev.b3monitor.domain.rule.Comparator.ABOVE, java.math.BigDecimal.ONE, 2,
                            java.math.BigDecimal.ZERO, 1, dev.b3monitor.domain.rule.RuleMode.UNSELECTED)
            ).status().name();
            var obsOpt = observations.findFirstByRequestedTickerOrderByReceiptTimeDesc(ticker);
            if (obsOpt.isEmpty()) {
                return new AssetFreshnessView(ticker, auth, false, null, null, false, null, null, null,
                        null, null, false, false, "NO_OBSERVATION_UNKNOWN", null,
                        "NOT_INTEGRATED", "NOT_INTEGRATED");
            }
            var o = obsOpt.get();
            Long age = o.getSourceTime() == null ? null
                    : Duration.between(o.getSourceTime(), clock.instant()).getSeconds();
            return new AssetFreshnessView(ticker, auth, true, o.getRequestedTicker(), o.getReturnedTicker(),
                    o.isProviderRemapped(), o.getCurrency(), o.getPrice(), o.getSourceTime(), o.getReceiptTime(),
                    age, o.isProviderStale(), o.isEligible(),
                    o.getRejectionReasons() == null || o.getRejectionReasons().isBlank() ? "-" : o.getRejectionReasons(),
                    o.getProviderContract(), "NOT_INTEGRATED", "NOT_INTEGRATED");
        }).toList();
        return new AssetFreshnessListView(rows);
    }

    /**
     * UI-03 (completed cycle-13 P1-A): a BOUNDED, newest-first page of RECENT logical alerts across the
     * FULL transport lifecycle (PENDING…SUPPRESSED), each with its separate transport state and bounded
     * attempt lineage. Distinct from {@link #reconciliation(int)} (dead-letters only). ACCEPTED is shown
     * even when {@code deliveryConfirmed=false}; UNKNOWN_OUTCOME is flagged uncertain. No payload/secret.
     */
    @Transactional(readOnly = true)
    public AlertOutcomeListView alertOutcomes(int limit) {
        int capped = Math.max(1, Math.min(limit, 100));   // hard page-size cap
        List<AlertOutcomeView> alerts = outbox.findRecentAlerts(
                org.springframework.data.domain.PageRequest.of(0, capped)).stream().map(o -> {
            List<AttemptView> att = attempts.findByOutboxIdOrderByStartedAtAsc(o.getId()).stream()
                    .limit(20)   // bounded child attempts
                    .map(a -> new AttemptView(a.getClaimGeneration(), a.getFencingToken(), a.getStartedAt(),
                            a.getFinishedAt(), a.getOutcome() == null ? null : a.getOutcome().name(),
                            a.getSanitizedStatus(), a.getProviderMessageId(), a.getProviderAcceptedAt()))
                    .toList();
            boolean uncertain = o.getState() == dev.b3monitor.domain.outbox.OutboxState.UNKNOWN_OUTCOME;
            return new AlertOutcomeView(o.getLogicalKey(), o.getRuleId(), o.getTicker(), o.getRuleRevision(),
                    o.getEpisodeEpoch(), o.getSourceAsOf(), o.getIntentCreatedAt(), o.getState().name(),
                    o.isDeliveryConfirmed(), o.getAcceptedAt(), o.getProviderMessageId(),
                    o.getSuppressionReason(), o.getSendStartedAt(), o.getAttemptFinishedAt(), uncertain, att);
        }).toList();
        return new AlertOutcomeListView(alerts);
    }

    /**
     * UI-02 (cycle-14 E): a READ-ONLY operator readiness snapshot — catalog/authorization summary,
     * calendar dataset readiness, workers flag, rule counts, and the integration readiness of the
     * components that are NOT yet wired (Brapi contract/quota, Python/daily-indicator, import), each with
     * an explicit cause. Bounded (counts + 23 single authorization probes). It activates NOTHING: there is
     * no import, no live poll, no send, no mutation here. The literal UI-02 acceptance (imports creating
     * audit revisions) is NOT met by this view — it advances VISIBILITY only; UI-02 stays PARTIAL.
     */
    @Transactional(readOnly = true)
    public ReadinessView readiness() {
        long authorized = 0, partial = 0, quarantined = 0, notAuthorized = 0;
        for (String ticker : catalog.tickers()) {
            var status = authorization.evaluate(
                    new dev.b3monitor.domain.rule.PriceRule("ui-probe", ticker,
                            dev.b3monitor.domain.rule.Comparator.ABOVE, java.math.BigDecimal.ONE, 2,
                            java.math.BigDecimal.ZERO, 1, dev.b3monitor.domain.rule.RuleMode.UNSELECTED)
            ).status();
            switch (status) {
                case AUTHORIZED -> authorized++;
                case PARTIAL_IDENTITY -> partial++;
                case QUARANTINED -> quarantined++;
                default -> notAuthorized++;
            }
        }
        long total = ruleDefs.count();
        long operable = ruleDefs.findByEnabledTrueAndPausedFalse().stream()
                .filter(e -> e.getMode().isOperable()).count();
        long paused = ruleDefs.findAll().stream().filter(RuleDefinitionEntity::isPaused).count();

        String calVersion = calendar.datasetVersion();
        boolean calReady = calVersion != null && !"none".equalsIgnoreCase(calVersion);
        String calReadiness = calReady ? "READY" : "NOT_READY";

        var components = List.of(
                new ReadinessComponentView("asset_catalog", "READY",
                        catalog.tickers().size() + " canonical tickers (trusted catalog; class never inferred from suffix)"),
                new ReadinessComponentView("operational_authorization",
                        authorized == 0 ? "FAIL_CLOSED" : "PARTIAL",
                        "all assets start NOT_AUTHORIZED; " + authorized + " authorized / " + partial
                                + " partial-identity / " + quarantined + " quarantined / " + notAuthorized + " not-authorized"),
                new ReadinessComponentView("trading_calendar", calReadiness,
                        calReady ? "dataset " + calVersion + " loaded (" + calendar.zone() + ")"
                                 : "no validated dataset — fail-closed, every session UNKNOWN, holidays never invented"),
                new ReadinessComponentView("workers", workersEnabled ? "ENABLED" : "DISABLED",
                        workersEnabled ? "collection/dispatch workers enabled" : "workers disabled (no real polling or send)"),
                new ReadinessComponentView("brapi_contract", "NOT_INTEGRATED",
                        "live Brapi is a human gate (no credential, no real poll in this milestone); quota provenance modelled but not exercised live"),
                new ReadinessComponentView("python_daily_indicators", "NOT_INTEGRATED",
                        "daily indicators (SMA/RSI/EMA/volume) and Python context are not wired; values are never synthesized"),
                new ReadinessComponentView("catalog_import", "NOT_INTEGRATED",
                        "no import pipeline is wired; catalog is a static trusted list — no destructive import exists"),
                new ReadinessComponentView("waha_delivery", "NOT_INTEGRATED",
                        "real WAHA send is a human gate; only a simulated adapter exists, no recipients configured")
        );

        return new ReadinessView(catalog.tickers().size(), authorized, partial, quarantined, notAuthorized,
                workersEnabled, calVersion, calendar.zone().getId(), calReadiness,
                total, operable, paused, components);
    }

    private static RuleView toRuleView(RuleDefinitionEntity e) {
        return new RuleView(e.getRuleId(), e.getTicker(), e.getComparator(), e.getThreshold(),
                e.getPrecision(), e.getHysteresis(), e.getMode(), e.getRevision(), e.isEnabled(),
                e.isPaused(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static ReconciliationRowView toReconciliationRow(OutboxEntity o) {
        return new ReconciliationRowView(o.getLogicalKey(), o.getRuleId(), o.getTicker(),
                o.getState().name(), o.getRuleRevision(), o.getIntentCreatedAt(), o.getSendStartedAt(),
                o.getAttemptFinishedAt(), o.getAttempts(), o.getSuppressionReason());
    }
}
