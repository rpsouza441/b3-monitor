package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistent typed rule definition (cycle-7 item E — RUL-01/RUL-05 groundwork). It is the SINGLE
 * source of truth for a rule: the scheduler reads active rules from here and the dispatch eligibility
 * guard reads revision/pause from here, so collection and dispatch cannot diverge. There is NO
 * arbitrary code / SQL / expression — only typed, bounded fields. A ticker is unique; each edit bumps
 * an immutable, monotonic {@code revision} (never decreases). {@code paused}/{@code enabled} gate both
 * collection and dispatch. No field here activates an asset operationally — that stays a separate
 * authorization concern.
 */
@Entity
@Table(name = "rule_definition",
       uniqueConstraints = @UniqueConstraint(name = "uq_rule_def_rule_id", columnNames = "rule_id"))
public class RuleDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparator", nullable = false, length = 8)
    private Comparator comparator;

    @Column(name = "threshold", nullable = false, precision = 19, scale = 6)
    private BigDecimal threshold;

    @Column(name = "price_precision", nullable = false)
    private int precision;

    @Column(name = "hysteresis", nullable = false, precision = 19, scale = 6)
    private BigDecimal hysteresis;

    /** Immutable, monotonic revision; bumped on every edit, never decreased. */
    @Column(name = "revision", nullable = false)
    private long revision = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "paused", nullable = false)
    private boolean paused = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RuleDefinitionEntity() {}

    public RuleDefinitionEntity(String ruleId, String ticker, Comparator comparator, BigDecimal threshold,
                                int precision, BigDecimal hysteresis, Instant now) {
        validate(ticker, comparator, threshold, precision, hysteresis);
        this.ruleId = ruleId;
        this.ticker = ticker;
        this.comparator = comparator;
        this.threshold = threshold;
        this.precision = precision;
        this.hysteresis = hysteresis;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Apply a typed edit: bump the revision (never decreases) and the updated-at stamp. */
    public long applyEdit(Comparator comparator, BigDecimal threshold, int precision,
                          BigDecimal hysteresis, Instant now) {
        validate(this.ticker, comparator, threshold, precision, hysteresis);
        this.comparator = comparator;
        this.threshold = threshold;
        this.precision = precision;
        this.hysteresis = hysteresis;
        this.revision++;
        this.updatedAt = now;
        return this.revision;
    }

    public void setPaused(boolean p, Instant now) { this.paused = p; this.updatedAt = now; }
    public void setEnabled(boolean e, Instant now) { this.enabled = e; this.updatedAt = now; }

    private static void validate(String ticker, Comparator comparator, BigDecimal threshold,
                                 int precision, BigDecimal hysteresis) {
        if (ticker == null || ticker.isBlank() || ticker.length() > 20)
            throw new IllegalArgumentException("ticker 1..20 chars required");
        if (comparator == null) throw new IllegalArgumentException("comparator required");
        if (threshold == null || threshold.signum() <= 0 || threshold.precision() > 19)
            throw new IllegalArgumentException("threshold must be > 0 and bounded");
        if (precision < 0 || precision > 10) throw new IllegalArgumentException("precision 0..10");
        if (hysteresis == null || hysteresis.signum() < 0)
            throw new IllegalArgumentException("hysteresis must be >= 0");
    }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getRuleId() { return ruleId; }
    public String getTicker() { return ticker; }
    public Comparator getComparator() { return comparator; }
    public BigDecimal getThreshold() { return threshold; }
    public int getPrecision() { return precision; }
    public BigDecimal getHysteresis() { return hysteresis; }
    public long getRevision() { return revision; }
    public boolean isEnabled() { return enabled; }
    public boolean isPaused() { return paused; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
