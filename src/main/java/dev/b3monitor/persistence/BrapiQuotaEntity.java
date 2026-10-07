package dev.b3monitor.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persistent Brapi quota + admission state.
 *
 * <h2>Cycle-5 review P0 — reset observation is NOT reset confirmation</h2>
 * Brapi's {@code ratelimit-reset} is the number of <b>seconds remaining until</b> the window reset,
 * not evidence that a reset already happened. Cycle 4 wrongly zeroed {@code consumed}, bumped
 * {@code cycleEpoch} and cleared {@code blockedUntil} on <em>any</em> positive delta, so a 429 with
 * {@code ratelimit-reset=3600} wiped the 10,500 counter an hour early.
 *
 * <p>This entity now separates the two concepts:
 * <ul>
 *   <li>{@link #observeResetDeadline} — records the <b>predicted</b> deadline + provenance only. It
 *       does not renew budget, does not touch {@code cycleEpoch}, does not clear a 429 block.</li>
 *   <li>{@link #confirmNewCycle} — the ONLY path that zeroes {@code consumed} and advances
 *       {@code cycleEpoch}. The manager calls it only with defensible monotonic evidence (an
 *       observed deadline that has demonstrably elapsed). Absent that, the counter persists
 *       (fail-closed).</li>
 * </ul>
 *
 * <h2>Cycle-5 review P1-high — fenced, recoverable reservation</h2>
 * The single in-flight guard now carries an {@code inFlightOwner} + monotonic {@code inFlightToken}
 * + {@code inFlightSince}. {@link #reserve} stamps all three; {@link #releaseIfOwner} clears the slot
 * only when the caller's token matches, so a late release of an older token cannot clear a newer
 * reservation. A crash leaves the slot set with its owner/age visible for reconciliation — it is
 * never silently reopened.
 */
@Entity
@Table(name = "brapi_quota",
       uniqueConstraints = @UniqueConstraint(name = "uq_brapi_quota_cycle", columnNames = "cycle_key"))
public class BrapiQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Opaque row identity. STABLE — never changed by resets, so the single row is always found. */
    @Column(name = "cycle_key", nullable = false, length = 64)
    private String cycleKey;

    /** Monotonic reset epoch. Advances ONLY on {@link #confirmNewCycle}; the "cycle" the counter counts. */
    @Column(name = "cycle_epoch", nullable = false)
    private long cycleEpoch = 0;

    @Column(name = "hard_limit", nullable = false)
    private int hardLimit = 15_000;

    @Column(name = "routine_ceiling", nullable = false)
    private int routineCeiling = 10_500;

    @Column(name = "consumed", nullable = false)
    private int consumed = 0;

    /** True while a request slot is reserved. Durable single-in-flight guard across instances. */
    @Column(name = "in_flight", nullable = false)
    private boolean inFlight = false;

    /** Owner that holds the current reservation (worker id). Null when free. Fencing provenance. */
    @Column(name = "in_flight_owner", length = 80)
    private String inFlightOwner;

    /** Monotonic reservation token. Each reserve bumps it; release must match (fencing). */
    @Column(name = "in_flight_token", nullable = false)
    private long inFlightToken = 0;

    /** When the current reservation was stamped — age for crash reconciliation. Null when free. */
    @Column(name = "in_flight_since")
    private Instant inFlightSince;

    /** True when we cannot prove the account's quota is used only by this app → block admission. */
    @Column(name = "shared_usage_unknown", nullable = false)
    private boolean sharedUsageUnknown = true;

    /** Next instant admission may resume after a 429/Retry-After. */
    @Column(name = "blocked_until")
    private Instant blockedUntil;

    /** PREDICTED reset instant from a {@code ratelimit-reset} DELTA — an observation, NOT a reset. */
    @Column(name = "pending_reset_at")
    private Instant pendingResetAt;

    /** Local instant we observed the pending reset header — provenance for {@link #pendingResetAt}. */
    @Column(name = "pending_reset_observed_at")
    private Instant pendingResetObservedAt;

    /** Instant of the last CONFIRMED cycle rollover (provenance for {@link #cycleEpoch}). Nullable. */
    @Column(name = "last_confirmed_reset_at")
    private Instant lastConfirmedResetAt;

    /** Set when a reset deadline elapsed but we could not safely confirm — needs operator reconciliation. */
    @Column(name = "needs_reconcile", nullable = false)
    private boolean needsReconcile = false;

    /** Provider-reported remaining budget from the last response, if any (uncertain until proven). */
    @Column(name = "provider_remaining")
    private Integer providerRemaining;

    protected BrapiQuotaEntity() {}

    public BrapiQuotaEntity(String cycleKey) { this.cycleKey = cycleKey; }

    public void recordConsumed(int n) { this.consumed += n; }
    public void blockUntil(Instant until) { this.blockedUntil = until; }

    // ----- P1-high: fenced reservation -------------------------------------------------------

    /** Stamp a new reservation for {@code owner}; returns the fresh monotonic token the owner must
     *  present to {@link #releaseIfOwner}. */
    public long reserve(String owner, Instant now) {
        this.inFlight = true;
        this.inFlightToken++;
        this.inFlightOwner = owner;
        this.inFlightSince = now;
        return this.inFlightToken;
    }

    /** Release the slot only if the presented token matches the current reservation (fencing).
     *  A stale token (older worker) is a no-op, so it cannot clear a newer reservation. */
    public boolean releaseIfOwner(long token) {
        if (inFlight && this.inFlightToken == token) {
            this.inFlight = false;
            this.inFlightOwner = null;
            this.inFlightSince = null;
            return true;
        }
        return false;
    }

    // ----- P0: observation vs confirmation ---------------------------------------------------

    /** Record a PREDICTED reset deadline + provenance. Does NOT renew budget, bump the epoch, or
     *  clear a 429 block. {@code remaining} is stored as uncertain external evidence only. */
    public void observeResetDeadline(Instant predictedResetAt, Integer remaining, Instant observedAt) {
        this.pendingResetAt = predictedResetAt;
        this.pendingResetObservedAt = observedAt;
        if (remaining != null) this.providerRemaining = remaining;
    }

    /**
     * Reconcile a conservative provider high-water (cycle-6 review P1): given a verified billing-cycle
     * {@code remaining} and the hard limit, {@code providerConsumed = hardLimit - remaining} and
     * {@code consumed = max(localConsumed, providerConsumed)}. NEVER lowers local consumed from a header.
     */
    public void raiseHighWaterFromRemaining(int remaining) {
        int providerConsumed = this.hardLimit - remaining;
        if (providerConsumed > this.consumed) {
            this.consumed = providerConsumed;
        }
    }

    /** CONFIRM a genuine new cycle: zero the counter, advance the epoch, clear the pending deadline
     *  and any block. Called only with defensible elapsed-deadline evidence by the manager. */
    public void confirmNewCycle(Instant confirmedAt) {
        this.cycleEpoch++;
        this.consumed = 0;
        this.blockedUntil = null;
        this.lastConfirmedResetAt = confirmedAt;
        this.pendingResetAt = null;
        this.pendingResetObservedAt = null;
        this.needsReconcile = false;
    }

    public void flagNeedsReconcile() { this.needsReconcile = true; }
    public void clearNeedsReconcile() { this.needsReconcile = false; }

    // ----- accessors -------------------------------------------------------------------------

    public long getCycleEpoch() { return cycleEpoch; }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getCycleKey() { return cycleKey; }
    public int getHardLimit() { return hardLimit; }
    public void setHardLimit(int v) { this.hardLimit = v; }
    public int getRoutineCeiling() { return routineCeiling; }
    public void setRoutineCeiling(int v) { this.routineCeiling = v; }
    public int getConsumed() { return consumed; }
    public boolean isInFlight() { return inFlight; }
    public String getInFlightOwner() { return inFlightOwner; }
    public long getInFlightToken() { return inFlightToken; }
    public Instant getInFlightSince() { return inFlightSince; }
    public boolean isSharedUsageUnknown() { return sharedUsageUnknown; }
    public void setSharedUsageUnknown(boolean v) { this.sharedUsageUnknown = v; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public Instant getPendingResetAt() { return pendingResetAt; }
    public Instant getPendingResetObservedAt() { return pendingResetObservedAt; }
    public Instant getLastConfirmedResetAt() { return lastConfirmedResetAt; }
    public boolean isNeedsReconcile() { return needsReconcile; }
    public Integer getProviderRemaining() { return providerRemaining; }
    public void setProviderRemaining(Integer v) { this.providerRemaining = v; }
}
