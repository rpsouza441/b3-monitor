package dev.b3monitor.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One FIN-02 contextual metric belonging to an {@link AnalyticsContextEntity} (cycle-18 item G). A
 * STRUCTURED child row so every validated field — name/value/units/readiness/quality — round-trips
 * losslessly (the cycle-16 compact string could truncate silently). {@code (context_fk, name)} is unique:
 * a duplicate metric name within one context row is rejected. CONTEXT only (FIN-04); no private data.
 */
@Entity
@Table(name = "analytics_context_metric",
       uniqueConstraints = @UniqueConstraint(name = "uq_acm_context_name",
               columnNames = {"analytics_context_fk", "name"}))
public class AnalyticsContextMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analytics_context_fk", nullable = false)
    private AnalyticsContextEntity context;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "metric_value", precision = 24, scale = 12)
    private BigDecimal value;

    @Column(name = "units", length = 200)
    private String units;

    @Column(name = "readiness", length = 16)
    private String readiness;

    @Column(name = "quality", length = 200)
    private String quality;

    protected AnalyticsContextMetricEntity() {}

    public AnalyticsContextMetricEntity(String name, BigDecimal value, String units,
                                        String readiness, String quality) {
        this.name = name;
        this.value = value;
        this.units = units;
        this.readiness = readiness;
        this.quality = quality;
    }

    void setContext(AnalyticsContextEntity c) { this.context = c; }

    public Long getId() { return id; }
    public AnalyticsContextEntity getContext() { return context; }
    public String getName() { return name; }
    public BigDecimal getValue() { return value; }
    public String getUnits() { return units; }
    public String getReadiness() { return readiness; }
    public String getQuality() { return quality; }
}
