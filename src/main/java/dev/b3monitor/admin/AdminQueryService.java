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
    private final AnalyticsSnapshotRepository snapshots;
    private final AnalyticsContextRepository analyticsRows;
    private final Clock clock;
    private final boolean workersEnabled;

    public AdminQueryService(RuleAdminService rules, RuleDefinitionRepository ruleDefs,
                             OutboxReconciliationService reconciliation, OutboxRepository outbox,
                             QuoteObservationRepository observations, AdminAuditRepository auditRepo,
                             TradingSessionCalendar calendar, AdminProperties adminProps,
                             dev.b3monitor.domain.auth.AssetCatalog catalog,
                             dev.b3monitor.domain.auth.OperationalAuthorization authorization,
                             OutboxAttemptRepository attempts,
                             AnalyticsSnapshotRepository snapshots, AnalyticsContextRepository analyticsRows,
                             Clock clock,
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
        this.snapshots = snapshots;
        this.analyticsRows = analyticsRows;
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
     * UI-02 (cycle-16): bounded, newest-first analytics import history (provenance only; checksum shown as
     * a prefix). Viewer-readable. No payload, no private data.
     */
    @Transactional(readOnly = true)
    public ImportHistoryListView importHistory(int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        List<ImportHistoryView> rows = snapshots.findByOrderByImportedAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, capped)).stream()
                .map(s -> new ImportHistoryView(s.getSnapshotId(), s.getProducer(), s.getProducerVersion(),
                        s.getSchemaVersion(), String.valueOf(s.getMarketAsOf()), s.getGeneratedAt(),
                        s.getImportedAt(), s.getImportedBy(), s.getRecordCount(),
                        s.getChecksum() == null ? null : s.getChecksum().substring(0, Math.min(12, s.getChecksum().length())),
                        s.getStatus()))
                .toList();
        return new ImportHistoryListView(rows);
    }

    /**
     * UI-01 (cycle-16): per-asset ANALYTICS CONTEXT from the latest imported snapshot — SEPARATE from quote
     * freshness. Cycle-18 item D: for EACH catalog asset the LATEST valid context is selected across ALL
     * committed snapshots (snapshot.marketAsOf DESC, importedAt DESC, id DESC, context.id DESC) — correct for
     * PARTIAL snapshots, where an older snapshot's ticker stays current when a newer snapshot omits it.
     * {@code present=false} ⇒ no context for this asset (explicit no-data). Analytics AGE is shown; there is
     * no invented staleness SLA (item E) — {@code stalePolicy=POLICY_NOT_CONFIGURED}. CONTEXT only (FIN-04).
     */
    @Transactional(readOnly = true)
    public AnalyticsContextListView analyticsContext() {
        boolean anyImported = snapshots.count() > 0;
        var one = org.springframework.data.domain.PageRequest.of(0, 1);
        List<AnalyticsContextView> rows = catalog.tickers().stream().map(ticker -> {
            var match = analyticsRows.findLatestForTicker(ticker, one);
            if (match.isEmpty()) {
                String integ = anyImported ? "ANALYTICS_MISSING" : "NOT_INTEGRATED";
                return new AnalyticsContextView(ticker, false, null, null, null, null, null, null, null,
                        "POLICY_NOT_CONFIGURED",
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, integ);
            }
            var r = match.get(0);
            var snap = r.getSnapshot();
            Long age = snap.getMarketAsOf() == null ? null
                    : Duration.between(snap.getMarketAsOf().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                              clock.instant()).getSeconds();
            return new AnalyticsContextView(ticker, true, snap.getSnapshotId(), snap.getProducer(),
                    snap.getProducerVersion(), snap.getSchemaVersion(), String.valueOf(snap.getMarketAsOf()),
                    snap.getImportedAt(), age, "POLICY_NOT_CONFIGURED",
                    r.getSma20(), r.getSma20Readiness(), r.getSma50(), r.getSma50Readiness(),
                    r.getRsi14(), r.getRsi14Readiness(), r.getEma9(), r.getEma9Readiness(),
                    r.getEma21(), r.getEma21Readiness(), r.getVolumeRatio(), r.getVolumeRatioReadiness(),
                    renderMetrics(r.getMetrics()), r.getQuality(), r.getStatus(), "CONSUMER_VERIFIED_SYNTHETIC");
        }).toList();
        return new AnalyticsContextListView(rows);
    }

    /** Render the structured FIN-02 context metrics for display (name=value units [readiness] (quality)). */
    private static String renderMetrics(java.util.List<dev.b3monitor.persistence.AnalyticsContextMetricEntity> ms) {
        if (ms == null || ms.isEmpty()) return "-";
        return ms.stream()
                .sorted(java.util.Comparator.comparing(m -> m.getName() == null ? "" : m.getName()))
                .map(m -> m.getName() + "=" + (m.getValue() == null ? "" : m.getValue().toPlainString())
                        + (m.getUnits() == null ? "" : " " + m.getUnits())
                        + " [" + m.getReadiness() + "]"
                        + (m.getQuality() == null ? "" : " (" + m.getQuality() + ")"))
                .collect(java.util.stream.Collectors.joining("; "));
    }

    /**
     * authorization summary, calendar dataset readiness, workers flag, rule counts, and the readiness of
     * each operator component across THREE INDEPENDENT dimensions (wiring / operational / runtime) so code
     * integration is never conflated with operational authorization or runtime verification. Bounded (counts
     * + 23 single authorization probes). It activates NOTHING: there is no import, no live poll, no send, no
     * mutation here. The literal UI-02 acceptance (imports creating audit revisions) is NOT met by this view
     * — it advances VISIBILITY only; UI-02 stays PARTIAL.
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

        long analyticsCount = snapshots.count();
        boolean analyticsImported = analyticsCount > 0;

        var components = List.of(
                // asset_catalog: wired, no operational dimension, verified by startup load.
                new ReadinessComponentView("asset_catalog", "READY", "NOT_APPLICABLE", "VERIFIED",
                        catalog.tickers().size() + " canonical tickers (trusted catalog; class never inferred from suffix)"),
                // operational_authorization: wired and executable, but every asset starts fail-closed.
                new ReadinessComponentView("operational_authorization", "READY",
                        authorized == 0 ? "BLOCKED_BY_GATE" : "AUTHORIZED", "VERIFIED",
                        "executable per-asset egress gate; all assets start NOT_AUTHORIZED — " + authorized
                                + " authorized / " + partial + " partial-identity / " + quarantined
                                + " quarantined / " + notAuthorized + " not-authorized (CROSSING-only operable; UNSELECTED fail-closed)"),
                // trading_calendar: the CODE may be ready while the DATASET is not loaded — distinct axes.
                new ReadinessComponentView("trading_calendar", "READY", "NOT_APPLICABLE",
                        calReady ? "VERIFIED" : "NOT_READY",
                        calReady ? "calendar code wired; dataset " + calVersion + " loaded (" + calendar.zone() + ")"
                                 : "calendar code wired, but no validated dataset — fail-closed, every session UNKNOWN, holidays never invented"),
                // workers: wired; runtime reflects whether they are enabled (deliberately disabled this milestone).
                new ReadinessComponentView("workers", "READY", "BLOCKED_BY_GATE",
                        workersEnabled ? "VERIFIED" : "NOT_RUN",
                        workersEnabled ? "collection/dispatch workers enabled" : "workers disabled (no real polling or send in this milestone)"),
                // brapi_contract: client code is wired (v2 contract + quota provenance), but live calls are a
                // human gate and have never been exercised at runtime.
                new ReadinessComponentView("brapi_contract", "READY", "BLOCKED_BY_GATE", "NOT_RUN",
                        "Brapi v2 client + quota-provenance code is wired, but live Brapi is a human gate "
                                + "(no credential, no real poll in this milestone); the live contract is NOT exercised"),
                // python_daily_indicators: not wired at all.
                new ReadinessComponentView("python_daily_indicators", "NOT_INTEGRATED", "NOT_APPLICABLE", "NOT_RUN",
                        "daily indicators (SMA/RSI/EMA/volume) and Python context are not wired; values are never synthesized"),
                // analytics_consumer: the import contract/validator/preview-commit exist (wiring READY), but a
                // REAL producer snapshot has never been verified at runtime (a synthetic import is NOT_VERIFIED,
                // never VERIFIED). Operational is NOT_APPLICABLE — importing authorizes nothing.
                new ReadinessComponentView("analytics_consumer",
                        "READY", "NOT_APPLICABLE",
                        analyticsImported ? "NOT_VERIFIED" : "NOT_RUN",
                        analyticsImported
                                ? "consumer importer wired; " + analyticsCount + " snapshot(s) imported — CONSUMER_VERIFIED_SYNTHETIC only, real projecao-carteira integration NOT verified"
                                : "consumer importer wired (schema " + dev.b3monitor.domain.analytics.AnalyticsSnapshotContract.SCHEMA_V1 + "); no snapshot imported yet; real producer integration NOT verified"),
                // catalog_import: not wired at all.
                new ReadinessComponentView("catalog_import", "NOT_INTEGRATED", "NOT_APPLICABLE", "NOT_APPLICABLE",
                        "no import pipeline is wired; catalog is a static trusted list — no destructive import exists"),
                // waha_delivery: only a simulated adapter exists (PARTIAL wiring); real send is a human gate.
                new ReadinessComponentView("waha_delivery", "PARTIAL", "BLOCKED_BY_GATE", "NOT_RUN",
                        "only a simulated outbound adapter exists; real WAHA send is a human gate, no recipients configured, no live transport verified")
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
