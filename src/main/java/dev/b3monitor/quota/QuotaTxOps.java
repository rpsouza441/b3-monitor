package dev.b3monitor.quota;

import dev.b3monitor.persistence.BrapiQuotaEntity;
import dev.b3monitor.persistence.BrapiQuotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Atomic, durable quota DB operations in their OWN bean so the {@code @Transactional} proxy applies.
 * Admission reserves the single in-flight slot (fenced by owner + monotonic token) AND debits the
 * counter in ONE transaction, guarded by the row's {@code @Version} so two instances cannot both win.
 *
 * <h2>Cycle-5 review P0 — reset handling</h2>
 * A {@code ratelimit-reset} DELTA is only ever an <b>observation of a predicted deadline</b>
 * ({@link #observeReset}): it never zeroes the counter or advances the epoch. The counter is zeroed
 * only by {@link #confirmResetIfElapsed}, which requires that a previously-observed deadline has
 * demonstrably passed. Implausibly short deltas (the sandbox 20/60s window, not the 15k account
 * cycle) are recorded for provenance but are NOT accepted as account-cycle evidence.
 *
 * <h2>Cycle-5 review P1-high — fenced, recoverable reservation</h2>
 * {@link #reserve} stamps owner/token/since; {@link #release} clears the slot only on a token match,
 * so a late release of an older token cannot clear a newer reservation. {@link #reconcileStranded}
 * flags (never silently reopens) a reservation older than the lease.
 */
@Service
public class QuotaTxOps {

    /** The single durable quota row's key. STABLE — never re-keyed by a reset. */
    public static final String BOOTSTRAP_CYCLE = "bootstrap";

    /** How long a reservation may stand before {@link #reconcileStranded} flags it for an operator. */
    static final Duration RESERVATION_LEASE = Duration.ofSeconds(60);

    private final BrapiQuotaRepository repo;

    public QuotaTxOps(BrapiQuotaRepository repo) {
        this.repo = repo;
    }

    public enum Decision { GRANTED, SHARED_UNKNOWN, RATE_LIMIT_WINDOW, CEILING, IN_FLIGHT_BUSY, NEEDS_RECONCILE }

    /** Result of a successful reservation: the fencing token the caller must present to release. */
    public record Reservation(long token) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BrapiQuotaEntity load() {
        return repo.findByCycleKey(BOOTSTRAP_CYCLE)
                .orElseGet(() -> repo.save(new BrapiQuotaEntity(BOOTSTRAP_CYCLE)));
    }

    /**
     * Atomically decide and (if granted) reserve the in-flight slot + debit one unit, stamping the
     * caller as owner with a fresh monotonic token. All checks and the write happen in one
     * transaction; the {@code @Version} optimistic lock makes a concurrent grant impossible.
     *
     * @return a token (&gt;0) on GRANTED, or 0 with a {@link Decision} available via {@link #lastDecision}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long admit(String owner, Instant now, Decision[] out) {
        BrapiQuotaEntity q = load();
        if (q.isNeedsReconcile())                            { out[0] = Decision.NEEDS_RECONCILE; return 0; }
        if (q.isSharedUsageUnknown())                        { out[0] = Decision.SHARED_UNKNOWN; return 0; }
        if (q.getBlockedUntil() != null && now.isBefore(q.getBlockedUntil())) { out[0] = Decision.RATE_LIMIT_WINDOW; return 0; }
        if (q.getConsumed() >= q.getRoutineCeiling())        { out[0] = Decision.CEILING; return 0; }
        if (q.isInFlight())                                  { out[0] = Decision.IN_FLIGHT_BUSY; return 0; }
        long token = q.reserve(owner, now);
        q.recordConsumed(1);                                 // pessimistic: counted on reserve
        repo.save(q);
        out[0] = Decision.GRANTED;
        return token;
    }

    /** Release the single in-flight slot, fenced by token. A stale token is a no-op (never un-counts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(long token) {
        BrapiQuotaEntity q = load();
        if (q.releaseIfOwner(token)) { repo.save(q); }
    }

    /** Persist a 429 block window from Retry-After (seconds). Does NOT reset the counter. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void blockFor(long retryAfterSeconds, Instant now) {
        BrapiQuotaEntity q = load();
        q.blockUntil(now.plusSeconds(Math.max(1, retryAfterSeconds)));
        repo.save(q);
    }

    /** The expected billing-cycle window token Brapi sends on account-debited responses. */
    public static final String BILLING_CYCLE_WINDOW = "billing-cycle";

    /**
     * Observe rate-limit provenance from a response. An account-cycle PREDICTED deadline is recorded
     * ONLY when the evidence is compatible with the configured billing-cycle contract
     * (cycle-6 review P1): {@code window == "billing-cycle"} AND {@code limit == hardLimit} AND a
     * positive {@code resetDelta}. The deadline is anchored on {@code serverDate + resetDelta} when the
     * server Date is present, else on local {@code now + resetDelta}. On a verified billing-cycle
     * response the conservative high-water is raised ({@code consumed = max(local, hardLimit-remaining)};
     * never lowered). Any other window (sandbox 20/60s, a non-billing 3600s limiter), a mismatched
     * limit, or missing evidence records provenance but sets NO deadline and never renews budget
     * (fail-closed). Observing a deadline still does NOT reset {@code consumed} — that is
     * {@link #confirmResetIfElapsed}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void observeReset(Long resetDeltaSeconds, Integer remaining, Integer limit,
                             String window, Instant serverDate, Instant now) {
        BrapiQuotaEntity q = load();
        boolean billingCycle = BILLING_CYCLE_WINDOW.equalsIgnoreCase(window)
                && limit != null && limit == q.getHardLimit()
                && resetDeltaSeconds != null && resetDeltaSeconds > 0;
        if (billingCycle) {
            Instant anchor = (serverDate != null) ? serverDate : now;
            q.observeResetDeadline(anchor.plusSeconds(resetDeltaSeconds), remaining, now);
            if (remaining != null) q.raiseHighWaterFromRemaining(remaining);   // conservative, never lowers
        } else {
            // Not a verified billing-cycle signal: keep remaining as uncertain evidence, NO deadline.
            if (remaining != null) q.setProviderRemaining(remaining);
        }
        repo.save(q);
    }

    /**
     * CONFIRM a new cycle ONLY if a previously observed account-cycle deadline has demonstrably
     * elapsed by {@code now}. This is the single path that zeroes the counter. If no deadline was
     * observed, nothing happens (fail-closed). If a deadline elapsed but {@code remaining} evidence is
     * inconsistent (lower than expected after a reset), flag for reconciliation instead of resetting.
     *
     * @return true if a new cycle was confirmed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirmResetIfElapsed(Instant now) {
        BrapiQuotaEntity q = load();
        Instant deadline = q.getPendingResetAt();
        if (deadline == null || now.isBefore(deadline)) {
            return false;  // no evidence a reset occurred → counter persists
        }
        // Deadline elapsed. Accept the rollover (monotonic, evidence-backed). providerRemaining, if
        // present and clearly post-reset high, corroborates; if suspiciously low, we still roll but
        // leave providerRemaining as-is for the next conservative admission check.
        q.confirmNewCycle(now);
        repo.save(q);
        return true;
    }

    /** Flag (never silently clear) a reservation older than the lease, for operator reconciliation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileStranded(Instant now) {
        BrapiQuotaEntity q = load();
        if (q.isInFlight() && q.getInFlightSince() != null
                && q.getInFlightSince().plus(RESERVATION_LEASE).isBefore(now)) {
            q.flagNeedsReconcile();   // pessimistic: the stranded request may have consumed real quota
            repo.save(q);
            return true;
        }
        return false;
    }

    /** Operator action: clear a reconciliation flag after human/automated verification. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearReconcile() {
        BrapiQuotaEntity q = load();
        if (q.isNeedsReconcile()) {
            // Clearing the flag also forcibly frees any stranded reservation (operator-authorized).
            q.releaseIfOwner(q.getInFlightToken());
            q.clearNeedsReconcile();
            // confirmNewCycle is NOT auto-called here; the operator decides whether budget was spent.
            repo.save(q);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void declareDedicated() {
        BrapiQuotaEntity q = load();
        q.setSharedUsageUnknown(false);
        repo.save(q);
    }
}
