package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Durable outbox row. The {@code logicalKey} is UNIQUE so a crash/retry cannot create a duplicate
 * logical alert. {@code deliveryConfirmed} is a separate dimension from {@code state}: ACCEPTED is
 * not delivery.
 *
 * <h2>Cycle-6 review P0-1 — claim generation fences out a stale late result</h2>
 * A fencing token alone was insufficient: a reconciliation transition
 * (expired-lease → UNKNOWN_OUTCOME, operator abandon → FAILED, proof requeue → PENDING) left the
 * token unchanged, so a slow worker that finished AFTER the transition could still match the token
 * and overwrite a terminal/operator decision. Now every reconciliation transition
 * {@link #invalidateClaim()}s (bumps the token and clears owner/lease), and {@link #isActiveClaim}
 * requires the row to still be {@code IN_FLIGHT} with the exact token — so a late result is fenced.
 *
 * <h2>Cycle-6 review P0-2 — dispatch lineage for the eligibility guard</h2>
 * The intent's {@code sourceAsOf} and {@code createdAt} (from the injected Clock, via
 * {@code AlertIntent}) are now persisted, plus the {@code ruleRevision}, an {@code expiresAt}, and a
 * terminal {@code suppressionReason}, so a pre-dispatch guard can fail closed on a superseded
 * revision / expiry / stale source without inventing values.
 */
@Entity
@Table(name = "alert_outbox",
       uniqueConstraints = @UniqueConstraint(name = "uq_outbox_logical_key", columnNames = "logical_key"))
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "logical_key", nullable = false, length = 200)
    private String logicalKey;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private OutboxState state = OutboxState.PENDING;

    @Column(name = "delivery_confirmed", nullable = false)
    private boolean deliveryConfirmed = false;

    /** Monotonic fencing token: each claim increments it so a stale lease cannot act. */
    @Column(name = "fencing_token", nullable = false)
    private long fencingToken = 0;

    /**
     * Monotonic claim generation. Bumped on every claim AND on every reconciliation transition, so a
     * late result carrying an older generation is rejected even if the fencing token coincidentally
     * matched. (Review P0-1.)
     */
    @Column(name = "claim_generation", nullable = false)
    private long claimGeneration = 0;

    /** Owner of the current claim (worker id). Null when unclaimed. */
    @Column(name = "claimed_by", length = 80)
    private String claimedBy;

    /** Lease expiry; a claim past this instant is reconciled (never silently re-claimed). */
    @Column(name = "lease_until")
    private Instant leaseUntil;

    /** Dispatch attempts, for bounded retry / dead-letter policy. */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    // ----- Cycle-6 dispatch lineage (review P0-2) --------------------------------------------

    /** Rule revision this intent was minted for; a superseded revision must not dispatch. */
    @Column(name = "rule_revision", nullable = false)
    private long ruleRevision = 1;

    /** Market source-as-of of the triggering observation (for the dispatch-time source-age policy). */
    @Column(name = "source_as_of")
    private Instant sourceAsOf;

    /** Intent creation instant from the injected Clock (NOT a hidden Instant.now()). */
    @Column(name = "intent_created_at", nullable = false)
    private Instant intentCreatedAt;

    /** Hard expiry; past this instant the intent must not dispatch (fail-closed). Nullable = no expiry. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Episode lineage: the rule's episode epoch at mint time, to explain supersession decisions. */
    @Column(name = "episode_epoch", nullable = false)
    private long episodeEpoch = 0;

    /** Reason a non-sent intent was cancelled/suppressed/expired (audit); null while live. */
    @Column(name = "suppression_reason", length = 120)
    private String suppressionReason;

    /** When submission was AUTHORIZED and committed (SENDING). Null until prepareSend commits. */
    @Column(name = "send_started_at")
    private Instant sendStartedAt;

    /** When the attempt finished (result recorded). Null until a terminal/ambiguous result lands. */
    @Column(name = "attempt_finished_at")
    private Instant attemptFinishedAt;

    /** Provider message/request id, only when a verified adapter supplies it. Never fabricated. */
    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    /** Provider-accepted instant, only when supplied by a verified adapter. */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Optimistic lock so two workers cannot both win the same row. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEntity() {}

    /** Full constructor: lineage comes from the AlertIntent + rule, created_at from the injected Clock. */
    public OutboxEntity(String logicalKey, String ruleId, String ticker, String message,
                        long ruleRevision, long episodeEpoch, Instant sourceAsOf,
                        Instant intentCreatedAt, Instant expiresAt) {
        this.logicalKey = logicalKey;
        this.ruleId = ruleId;
        this.ticker = ticker;
        this.message = message;
        this.ruleRevision = ruleRevision;
        this.episodeEpoch = episodeEpoch;
        this.sourceAsOf = sourceAsOf;
        this.intentCreatedAt = intentCreatedAt;
        this.expiresAt = expiresAt;
        this.createdAt = intentCreatedAt;
    }

    /** Claim this row for dispatch: bump the fencing token AND the claim generation; set owner + lease.
     *  Does NOT count as an external attempt — that happens only when SENDING commits (markSending). */
    public long claim(String worker, Instant leaseUntil) {
        this.fencingToken++;
        this.claimGeneration++;
        this.claimedBy = worker;
        this.leaseUntil = leaseUntil;
        return this.fencingToken;
    }

    /** True iff this row is still the exact active claim for the given token (review P0-1 fence). */
    public boolean isActiveClaim(long expectedToken) {
        return this.state == OutboxState.IN_FLIGHT && this.fencingToken == expectedToken;
    }

    /** Invalidate the current claim on a reconciliation transition: bump generation, clear claim metadata.
     *  A late result from the old claim can no longer match (fence). */
    public void invalidateClaim() {
        this.fencingToken++;
        this.claimGeneration++;
        this.claimedBy = null;
        this.leaseUntil = null;
    }

    public boolean leaseExpired(Instant now) {
        return leaseUntil != null && now.isAfter(leaseUntil);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** Authorize submission: move IN_FLIGHT → SENDING, stamp the attempt start, and count ONE external
     *  attempt. Committed BEFORE I/O. */
    public void markSending(Instant now) {
        this.state = OutboxState.SENDING;
        this.sendStartedAt = now;
        this.attempts++;
    }

    /** Record a transport result's provenance (set by the dispatcher after the adapter call). */
    public void recordAttemptResult(Instant finishedAt, String providerMessageId, Instant acceptedAt) {
        this.attemptFinishedAt = finishedAt;
        if (providerMessageId != null) this.providerMessageId = providerMessageId;
        if (acceptedAt != null) this.acceptedAt = acceptedAt;
    }

    public Long getId() { return id; }
    public String getLogicalKey() { return logicalKey; }
    public String getRuleId() { return ruleId; }
    public String getTicker() { return ticker; }
    public String getMessage() { return message; }
    public OutboxState getState() { return state; }
    public void setState(OutboxState state) { this.state = state; }
    public boolean isDeliveryConfirmed() { return deliveryConfirmed; }
    public void setDeliveryConfirmed(boolean v) { this.deliveryConfirmed = v; }
    public long getFencingToken() { return fencingToken; }
    public long getClaimGeneration() { return claimGeneration; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public int getAttempts() { return attempts; }
    public long getRuleRevision() { return ruleRevision; }
    public Instant getSourceAsOf() { return sourceAsOf; }
    public Instant getIntentCreatedAt() { return intentCreatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getEpisodeEpoch() { return episodeEpoch; }
    public String getSuppressionReason() { return suppressionReason; }
    public void setSuppressionReason(String r) { this.suppressionReason = r; }
    public Instant getSendStartedAt() { return sendStartedAt; }
    public Instant getAttemptFinishedAt() { return attemptFinishedAt; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
