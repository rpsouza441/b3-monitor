package dev.b3monitor.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One per-asset analytics context row belonging to an {@link AnalyticsSnapshotEntity} (cycle-16). Carries
 * the typed daily indicators (IND-01/02/04/05) each with its own readiness, plus a bounded compact string
 * of FIN-02 contextual metrics. CONTEXT only — never read into a rule decision (FIN-04). No private data.
 */
@Entity
@Table(name = "analytics_context")
public class AnalyticsContextEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_fk", nullable = false)
    private AnalyticsSnapshotEntity snapshot;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "as_of")
    private LocalDate asOf;

    @Column(name = "sma20", precision = 20, scale = 8) private BigDecimal sma20;
    @Column(name = "sma20_readiness", length = 16)     private String sma20Readiness;
    @Column(name = "sma50", precision = 20, scale = 8) private BigDecimal sma50;
    @Column(name = "sma50_readiness", length = 16)     private String sma50Readiness;
    @Column(name = "rsi14", precision = 20, scale = 8) private BigDecimal rsi14;
    @Column(name = "rsi14_readiness", length = 16)     private String rsi14Readiness;
    @Column(name = "ema9", precision = 20, scale = 8)  private BigDecimal ema9;
    @Column(name = "ema9_readiness", length = 16)      private String ema9Readiness;
    @Column(name = "ema21", precision = 20, scale = 8) private BigDecimal ema21;
    @Column(name = "ema21_readiness", length = 16)     private String ema21Readiness;
    @Column(name = "volume_ratio", precision = 20, scale = 8) private BigDecimal volumeRatio;
    @Column(name = "volume_ratio_readiness", length = 16)    private String volumeRatioReadiness;

    @Column(name = "context_metrics", length = 2000)
    private String contextMetrics;

    @Column(name = "quality", length = 200)
    private String quality;

    @Column(name = "status", length = 40)
    private String status;

    protected AnalyticsContextEntity() {}

    public AnalyticsContextEntity(String ticker, LocalDate asOf,
                                  BigDecimal sma20, String sma20Readiness,
                                  BigDecimal sma50, String sma50Readiness,
                                  BigDecimal rsi14, String rsi14Readiness,
                                  BigDecimal ema9, String ema9Readiness,
                                  BigDecimal ema21, String ema21Readiness,
                                  BigDecimal volumeRatio, String volumeRatioReadiness,
                                  String contextMetrics, String quality, String status) {
        this.ticker = ticker; this.asOf = asOf;
        this.sma20 = sma20; this.sma20Readiness = sma20Readiness;
        this.sma50 = sma50; this.sma50Readiness = sma50Readiness;
        this.rsi14 = rsi14; this.rsi14Readiness = rsi14Readiness;
        this.ema9 = ema9; this.ema9Readiness = ema9Readiness;
        this.ema21 = ema21; this.ema21Readiness = ema21Readiness;
        this.volumeRatio = volumeRatio; this.volumeRatioReadiness = volumeRatioReadiness;
        this.contextMetrics = contextMetrics; this.quality = quality; this.status = status;
    }

    void setSnapshot(AnalyticsSnapshotEntity s) { this.snapshot = s; }

    public Long getId() { return id; }
    public AnalyticsSnapshotEntity getSnapshot() { return snapshot; }
    public String getTicker() { return ticker; }
    public LocalDate getAsOf() { return asOf; }
    public BigDecimal getSma20() { return sma20; } public String getSma20Readiness() { return sma20Readiness; }
    public BigDecimal getSma50() { return sma50; } public String getSma50Readiness() { return sma50Readiness; }
    public BigDecimal getRsi14() { return rsi14; } public String getRsi14Readiness() { return rsi14Readiness; }
    public BigDecimal getEma9() { return ema9; } public String getEma9Readiness() { return ema9Readiness; }
    public BigDecimal getEma21() { return ema21; } public String getEma21Readiness() { return ema21Readiness; }
    public BigDecimal getVolumeRatio() { return volumeRatio; } public String getVolumeRatioReadiness() { return volumeRatioReadiness; }
    public String getContextMetrics() { return contextMetrics; }
    public String getQuality() { return quality; }
    public String getStatus() { return status; }
}
