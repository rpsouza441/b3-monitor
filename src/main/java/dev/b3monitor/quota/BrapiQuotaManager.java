package dev.b3monitor.quota;

import dev.b3monitor.persistence.BrapiQuotaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Conservative, DURABLE Brapi admission control.
 *
 * <h2>Cycle-5 review P0 — a reset header never anticipates a reset</h2>
 * {@link #observeResetHeader} records a predicted deadline + remaining as provenance only; it does
 * NOT zero the counter. The counter is zeroed only by {@link #confirmCycleRolloverIfElapsed}, which
 * requires that an observed account-cycle deadline has demonstrably passed. A missing/short/repeated
 * header never resets the budget. This fixes the cycle-4 defect where {@code ratelimit-reset=3600}
 * wiped the 10,500 counter an hour early.
 *
 * <h2>Cycle-5 review P1-high — fenced, recoverable reservation</h2>
 * {@link #tryAcquire} stamps the caller as owner with a monotonic token and returns it; the caller
 * MUST {@link #release(long)} with that token in a finally. A late release of an older token cannot
 * clear a newer reservation. {@link #reconcileStranded} flags (never silently reopens) a reservation
 * older than the lease so an operator can decide whether real quota was consumed.
 *
 * <p>This gate performs NO network I/O and enables no real polling by itself.
 */
@Service
public class BrapiQuotaManager {

    private static final Logger log = LoggerFactory.getLogger(BrapiQuotaManager.class);

    private final QuotaTxOps tx;
    private final Clock clock;
    private final String owner;

    public BrapiQuotaManager(QuotaTxOps tx, Clock clock) {
        this.tx = tx;
        this.clock = clock;
        this.owner = "quota-" + Long.toHexString(ProcessHandle.current().pid());
    }

    public sealed interface Admission permits Granted, Denied {}
    /** Carries the fencing token the caller must present to {@link #release(long)}. */
    public record Granted(long token) implements Admission {}
    public record Denied(String reason) implements Admission {}

    /**
     * Attempt to admit one Brapi request. On {@link Granted} the caller MUST later
     * {@link #release(long)} with the returned token (finally). On {@link Denied} nothing is reserved.
     */
    public Admission tryAcquire() {
        var out = new QuotaTxOps.Decision[1];
        final long token;
        try {
            token = tx.admit(owner, clock.instant(), out);
        } catch (ObjectOptimisticLockingFailureException race) {
            return new Denied("CONCURRENCY_BUSY");   // another instance won the single slot
        }
        return switch (out[0]) {
            case GRANTED           -> new Granted(token);
            case SHARED_UNKNOWN    -> new Denied("SHARED_QUOTA_UNKNOWN");
            case RATE_LIMIT_WINDOW -> new Denied("RATE_LIMIT_WINDOW");
            case CEILING           -> new Denied("ROUTINE_CEILING_REACHED");
            case IN_FLIGHT_BUSY    -> new Denied("CONCURRENCY_BUSY");
            case NEEDS_RECONCILE   -> new Denied("NEEDS_RECONCILE");
        };
    }

    /** Release the single in-flight slot, fenced by the token from {@link #tryAcquire}. Idempotent. */
    public void release(long token) {
        try { tx.release(token); }
        catch (RuntimeException e) { log.warn("quota release failed: {}", e.getClass().getSimpleName()); }
    }

    /** Record request completion. The pessimistic debit already happened on acquire; no un-count. */
    public void onResult(boolean success) {
        log.debug("brapi request completed success={}", success);
    }

    /** HTTP 429: persist a block window from Retry-After (seconds). Does NOT reset the counter. */
    public void onRateLimited(long retryAfterSeconds) {
        tx.blockFor(retryAfterSeconds, clock.instant());
    }

    /**
     * Feed rate-limit provenance from a response (2xx OR 429): reset DELTA, remaining, limit, window
     * and server Date. Records a predicted deadline ONLY when the evidence matches the billing-cycle
     * contract (window + limit); never resets the budget. See {@link QuotaTxOps#observeReset}.
     */
    public void observeResetHeader(Long resetDeltaSeconds, Integer remaining, Integer limit,
                                   String window, java.time.Instant serverDate) {
        tx.observeReset(resetDeltaSeconds, remaining, limit, window, serverDate, clock.instant());
    }

    /**
     * Confirm a cycle rollover if a previously observed account-cycle deadline has elapsed. The only
     * path that zeroes the counter. Returns true when a rollover was confirmed.
     */
    public boolean confirmCycleRolloverIfElapsed() {
        return tx.confirmResetIfElapsed(clock.instant());
    }

    /** Flag a reservation older than the lease for operator reconciliation (pessimistic; no reopen). */
    public boolean reconcileStranded() {
        return tx.reconcileStranded(clock.instant());
    }

    /** Operator action: clear a reconciliation flag after verification. */
    public void clearReconcile() { tx.clearReconcile(); }

    /** Operator action: prove the account's quota is used only by this app. */
    public void declareDedicatedQuota() {
        tx.declareDedicated();
    }

    public int consumedThisCycle() { return tx.load().getConsumed(); }
    public BrapiQuotaEntity snapshot() { return tx.load(); }
}
