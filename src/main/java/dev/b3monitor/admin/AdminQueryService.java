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
    private final TradingSessionCalendar calendar;
    private final Clock clock;
    private final boolean workersEnabled;

    public AdminQueryService(RuleAdminService rules, RuleDefinitionRepository ruleDefs,
                             OutboxReconciliationService reconciliation, OutboxRepository outbox,
                             QuoteObservationRepository observations, TradingSessionCalendar calendar,
                             Clock clock,
                             @Value("${b3monitor.workers.enabled:false}") boolean workersEnabled) {
        this.rules = rules;
        this.ruleDefs = ruleDefs;
        this.reconciliation = reconciliation;
        this.outbox = outbox;
        this.observations = observations;
        this.calendar = calendar;
        this.clock = clock;
        this.workersEnabled = workersEnabled;
    }

    @Transactional(readOnly = true)
    public StatusView status() {
        long total = ruleDefs.count();
        long active = ruleDefs.findByEnabledTrueAndPausedFalse().stream()
                .filter(e -> e.getMode().isOperable()).count();
        String session = calendar.statusAt(clock.instant()).name();
        return new StatusView(workersEnabled, calendar.datasetVersion(), session, total, active);
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
    public ReconciliationView reconciliation() {
        List<ReconciliationRowView> rows = reconciliation.deadLetters().stream()
                .map(AdminQueryService::toReconciliationRow).toList();
        return new ReconciliationView(rows);
    }

    @Transactional(readOnly = true)
    public Optional<QuoteFreshnessView> latestQuote(String ticker) {
        List<QuoteObservationEntity> rows = observations.findByRequestedTickerOrderByReceiptTimeDesc(ticker);
        if (rows.isEmpty()) return Optional.empty();
        QuoteObservationEntity o = rows.get(0);
        Long ageSeconds = o.getSourceTime() == null ? null
                : Duration.between(o.getSourceTime(), clock.instant()).getSeconds();
        return Optional.of(new QuoteFreshnessView(
                o.getRequestedTicker(), o.getReturnedTicker(), o.isProviderRemapped(), o.getCurrency(),
                o.getPrice(), o.getSourceTime(), o.getReceiptTime(), o.isProviderStale(), o.isEligible(),
                ageSeconds, o.getRejectionReasons(), o.getProviderContract()));
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
