package dev.b3monitor.schedule;

import dev.b3monitor.adapter.brapi.BrapiException;
import dev.b3monitor.domain.auth.OperationalAuthorization;
import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.monitor.MonitorPipeline;
import dev.b3monitor.persistence.OutboxDispatcher;
import dev.b3monitor.quota.BrapiQuotaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Injectable evaluation worker, DISABLED by default ({@code b3monitor.workers.enabled=false}) and a
 * no-op until explicitly enabled — nothing invokes it automatically. A test or operator drives
 * {@link #tick(List)}.
 *
 * <p>Each tick, per rule, applies gates in a fail-closed order; a request reaches Brapi ONLY when
 * every gate passes:
 * <ol>
 *   <li><b>Operational authorization</b> ({@link OperationalAuthorization}) — current identity +
 *       explicit asset authorization + not paused. This is checked FIRST, before quota or calendar,
 *       so a {@code NOT_AUTHORIZED}/{@code PARTIAL}/{@code QUARANTINED}/{@code PAUSED} asset can never
 *       fetch even if the worker was accidentally enabled with a dedicated quota and an OPEN market
 *       (cycle-5 review P1).</li>
 *   <li><b>Trading calendar</b> ({@link TradingCalendar}) — UNKNOWN or CLOSED ⇒ skip (fail-closed).</li>
 *   <li><b>Quota admission</b> ({@link BrapiQuotaManager}) — durable single in-flight, conservative
 *       ceiling, shared-quota-UNKNOWN block, reconciliation flag.</li>
 * </ol>
 * After a granted fetch it feeds any 429/reset signal to the quota manager (OBSERVING reset deadlines,
 * never resetting the budget here), ALWAYS releases the fenced slot in a finally, confirms a cycle
 * rollover only when an observed deadline has elapsed, reconciles a stranded reservation, and drains
 * the post-commit {@link OutboxDispatcher}.
 */
@Service
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

    private final MonitorPipeline pipeline;
    private final OutboxDispatcher dispatcher;
    private final BrapiQuotaManager quota;
    private final TradingCalendar calendar;
    private final OperationalAuthorization authorization;
    private final dev.b3monitor.domain.rule.RuleSource ruleSource;
    private final boolean enabled;

    public MonitorScheduler(MonitorPipeline pipeline, OutboxDispatcher dispatcher,
                            BrapiQuotaManager quota, TradingCalendar calendar,
                            OperationalAuthorization authorization,
                            dev.b3monitor.domain.rule.RuleSource ruleSource,
                            @Value("${b3monitor.workers.enabled:false}") boolean enabled) {
        this.pipeline = pipeline;
        this.dispatcher = dispatcher;
        this.quota = quota;
        this.calendar = calendar;
        this.authorization = authorization;
        this.ruleSource = ruleSource;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    /** Run one tick over the rules from the single {@link dev.b3monitor.domain.rule.RuleSource}
     *  (cycle-7 item E) — collection and dispatch share one source of truth. */
    public TickReport tick() {
        return tick(ruleSource.activeRules());
    }

    /** Result of one scheduler tick, for test assertions and operator visibility. */
    public record TickReport(int rulesConsidered, int fetched, int skippedCalendar, int deniedAuth,
                             int deniedQuota, int fired, int dispatched, int quarantined) {}

    /**
     * Run one tick over the given rules. A no-op (all-zero report) when the worker is disabled —
     * the default, so this never polls unless explicitly enabled.
     */
    public TickReport tick(List<PriceRule> rules) {
        if (!enabled) {
            log.debug("scheduler disabled; tick is a no-op");
            return new TickReport(rules.size(), 0, 0, 0, 0, 0, 0, 0);
        }
        quota.confirmCycleRolloverIfElapsed();                   // only resets if an observed deadline elapsed
        quota.reconcileStranded();                               // flag (never reopen) a crash-stranded slot
        int quarantined = dispatcher.reconcileExpiredLeases();   // ambiguous IN_FLIGHT → UNKNOWN_OUTCOME
        int fetched = 0, skipped = 0, deniedAuth = 0, denied = 0, fired = 0;

        for (PriceRule rule : rules) {
            // GATE 1 — operational authorization (first, fail-closed). No network until this passes.
            OperationalAuthorization.Decision auth = authorization.evaluate(rule);
            if (!auth.mayFetch()) {
                deniedAuth++;
                log.debug("authorization denied for {}: {} ({})", rule.ticker(), auth.status(), auth.reason());
                continue;
            }
            // GATE 2 — trading calendar (UNKNOWN or CLOSED ⇒ fail-closed skip).
            if (calendar.isTradingNow() != TradingCalendar.Status.OPEN) {
                skipped++;
                continue;
            }
            // GATE 3 — quota admission (durable single in-flight, fenced).
            var admission = quota.tryAcquire();
            if (admission instanceof BrapiQuotaManager.Denied d) {
                denied++;
                log.debug("quota denied for {}: {}", rule.ticker(), d.reason());
                continue;
            }
            long token = ((BrapiQuotaManager.Granted) admission).token();
            boolean ok = false;
            try {
                var r = pipeline.runOnce(rule);
                if (r.fetched()) fetched++;
                if (r.fired()) fired++;
                ok = r.fetched();
                BrapiException rl = r.rateLimit();
                if (rl != null) {     // propagate 429 provenance (unified telemetry) to the quota manager
                    if (rl.retryAfterSeconds() != null) quota.onRateLimited(rl.retryAfterSeconds());
                    var s = rl.signal();
                    if (s.resetDeltaSeconds() != null || s.window() != null) {
                        quota.observeResetHeader(s.resetDeltaSeconds(), s.remaining(),
                                s.limit(), s.window(), s.serverDate());
                    }
                }
                var sig = r.quotaSignal();   // SUCCESS-path telemetry (2xx) — full billing-cycle provenance
                if (sig != null && sig.hasAnySignal()) {
                    quota.observeResetHeader(sig.resetDeltaSeconds(), sig.remaining(),
                            sig.limit(), sig.window(), sig.serverDate());
                }
            } finally {
                quota.onResult(ok);
                quota.release(token);  // ALWAYS release the fenced single in-flight slot
            }
        }
        int dispatched = dispatcher.drainBatch();   // bounded batch (review F: no Integer.MAX_VALUE)
        return new TickReport(rules.size(), fetched, skipped, deniedAuth, denied, fired, dispatched, quarantined);
    }
}
