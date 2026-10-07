package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.RuleState;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Durable persistence of a rule's CROSSING lifecycle so arm/latch/baseline survive restart
 * (independent review P1 #5). Holds the {@link RuleState.Phase}, the rule revision, an episode
 * epoch, the last fired time and a cooldown marker. Policy fields that need human approval
 * (LEVEL initial-token default, the two-FALSE-confirmation requirement — Q-19) are modelled but
 * default to the conservative CROSSING-only behaviour and are NOT silently enabled.
 */
@Entity
@Table(name = "rule_state",
       uniqueConstraints = @UniqueConstraint(name = "uq_rule_state_rule", columnNames = "rule_id"))
public class RuleStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic-lock guard: two workers racing the same rule — the loser retries, never double-fires. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private RuleState.Phase phase = RuleState.Phase.UNBASELINED;

    @Column(name = "rule_revision", nullable = false)
    private long ruleRevision = 1;

    @Column(name = "episode_epoch", nullable = false)
    private long episodeEpoch = 0;

    /**
     * Market source time of the last observation that actually MUTATED this state.
     * An observation whose sourceTime is not strictly greater is a replay / duplicate /
     * out-of-order arrival and is rejected (no state change, no fire).
     */
    @Column(name = "last_processed_source_time")
    private Instant lastProcessedSourceTime;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    protected RuleStateEntity() {}

    public RuleStateEntity(String ruleId) { this.ruleId = ruleId; }

    /** Hydrate an in-memory {@link RuleState} from the stored phase. */
    public RuleState toDomain() {
        return RuleState.at(phase);
    }

    /** Persist the phase from an in-memory {@link RuleState} after an evaluation that advanced the clock. */
    public void updateFrom(RuleState s, Instant processedSourceTime, boolean fired, Instant firedAt) {
        this.phase = s.getPhase();
        if (processedSourceTime != null) { this.lastProcessedSourceTime = processedSourceTime; }
        if (fired) { this.lastFiredAt = firedAt; this.episodeEpoch++; }
    }

    /** True when an observation at {@code sourceTime} is strictly newer than the last processed one. */
    public boolean isNewerThanProcessed(Instant sourceTime) {
        if (sourceTime == null) { return false; }                 // no source time → cannot order → reject
        return lastProcessedSourceTime == null || sourceTime.isAfter(lastProcessedSourceTime);
    }

    /** Outcome of reconciling an incoming rule revision against the persisted one (cycle-6 review P0-3). */
    public enum RevisionVerdict {
        STALE,   // incoming < persisted → reject: no evaluation, no mutation, no fire
        SAME,    // incoming == persisted → evaluate normally
        BUMPED   // incoming > persisted → establish new revision, re-baseline, cancel old PENDING
    }

    /**
     * Reconcile an incoming rule revision against the persisted one, fail-closed on a downgrade.
     * <ul>
     *   <li>incoming &lt; persisted → {@link RevisionVerdict#STALE}; state is NOT mutated (the caller
     *       must not evaluate stale rule parameters against newer state);</li>
     *   <li>incoming == persisted → {@link RevisionVerdict#SAME};</li>
     *   <li>incoming &gt; persisted → {@link RevisionVerdict#BUMPED}: adopt the new revision,
     *       re-baseline (UNBASELINED, never auto-fire), advance the episode epoch, drop the processed
     *       clock. The caller cancels superseded PENDING intents.</li>
     * </ul>
     */
    public RevisionVerdict reconcileRevision(long incomingRevision) {
        if (incomingRevision < this.ruleRevision) {
            return RevisionVerdict.STALE;
        }
        if (incomingRevision == this.ruleRevision) {
            return RevisionVerdict.SAME;
        }
        this.ruleRevision = incomingRevision;
        this.phase = RuleState.Phase.UNBASELINED;             // revision change re-baselines, never auto-fires
        this.episodeEpoch++;
        this.lastProcessedSourceTime = null;
        return RevisionVerdict.BUMPED;
    }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getRuleId() { return ruleId; }
    public RuleState.Phase getPhase() { return phase; }
    public void setPhase(RuleState.Phase p) { this.phase = p; }
    public long getRuleRevision() { return ruleRevision; }
    public long getEpisodeEpoch() { return episodeEpoch; }
    public Instant getLastProcessedSourceTime() { return lastProcessedSourceTime; }
    public Instant getLastFiredAt() { return lastFiredAt; }
}
