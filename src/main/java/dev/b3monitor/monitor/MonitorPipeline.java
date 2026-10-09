package dev.b3monitor.monitor;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.adapter.brapi.BrapiException;
import dev.b3monitor.adapter.brapi.QuotaSignal;
import dev.b3monitor.domain.rule.PriceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Non-transactional orchestrator for one monitor cycle. It performs the Brapi fetch OUTSIDE any
 * transaction, then hands the quote to {@link MonitorProcessingService#process} — a SEPARATE
 * Spring bean, so its {@code @Transactional} boundary is actually applied by the proxy (cycle-3
 * review P0-1: the previous {@code runOnce → process} self-invocation on one object bypassed the
 * transaction advice). This class holds NO transaction and performs NO send.
 */
@Service
public class MonitorPipeline {

    private static final Logger log = LoggerFactory.getLogger(MonitorPipeline.class);

    private final BrapiClient brapi;
    private final MonitorProcessingService processing;

    public MonitorPipeline(BrapiClient brapi, MonitorProcessingService processing) {
        this.brapi = brapi;
        this.processing = processing;
    }

    /**
     * Run one cycle for a single rule: fetch (no transaction held), then process atomically in the
     * proxied transactional service. The caller owns scheduling and quota.
     */
    public CycleResult runOnce(PriceRule rule) {
        final BrapiClient.FetchResult fetched;
        try {
            fetched = brapi.fetch(rule.ticker());      // network I/O — NO DB transaction held
        } catch (BrapiException e) {
            log.warn("fetch failed for {}: {}", rule.ticker(), e.getMessage());
            // Surface a rate-limit signal (with header provenance) so the caller can feed the quota manager.
            BrapiException rl = e.isRateLimited() ? e : null;
            return new CycleResult(false, false, false, rl != null ? "RATE_LIMITED" : "FETCH_FAILED", rl, null);
        }
        CycleResult r = processing.process(rule, fetched.quote());  // proxied @Transactional boundary applies here
        // Carry the SUCCESS-path quota telemetry so the scheduler can observe reset/remaining (cycle-5 P1).
        return new CycleResult(r.fetched(), r.eligible(), r.fired(), r.detail(), r.rateLimit(),
                fetched.signal() != null && fetched.signal().hasAnySignal() ? fetched.signal() : null);
    }

    /**
     * @param fetched did we get a quote; @param eligible was it valid; @param fired new alert;
     * @param detail outcome tag; @param rateLimit the 429 signal (header provenance) or null;
     * @param quotaSignal 2xx-path rate-limit telemetry (reset/remaining) or null
     */
    public record CycleResult(boolean fetched, boolean eligible, boolean fired, String detail,
                              BrapiException rateLimit, QuotaSignal quotaSignal) {
        public CycleResult(boolean fetched, boolean eligible, boolean fired, String detail) {
            this(fetched, eligible, fired, detail, null, null);
        }
        public CycleResult(boolean fetched, boolean eligible, boolean fired, String detail,
                           BrapiException rateLimit) {
            this(fetched, eligible, fired, detail, rateLimit, null);
        }
    }
}
