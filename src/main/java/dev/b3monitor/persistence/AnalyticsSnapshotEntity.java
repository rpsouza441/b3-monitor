package dev.b3monitor.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Provenance of ONE imported analytics snapshot (cycle-16, FIN-01/FIN-03). Records what was imported, by
 * whom, when, and its validation disposition. {@code snapshotId} is unique — a re-import of the SAME id
 * with the SAME checksum is idempotent; a DIFFERENT checksum is a conflict (never a silent overwrite).
 * Stores no raw document blob and no private-portfolio datum (FIN-04).
 */
@Entity
@Table(name = "analytics_snapshot",
       uniqueConstraints = @UniqueConstraint(name = "uq_analytics_snapshot_id", columnNames = "snapshot_id"))
public class AnalyticsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, length = 200)
    private String snapshotId;

    @Column(name = "schema_version", nullable = false, length = 100)
    private String schemaVersion;

    @Column(name = "producer", nullable = false, length = 200)
    private String producer;

    @Column(name = "producer_version", nullable = false, length = 200)
    private String producerVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "market_as_of", nullable = false)
    private LocalDate marketAsOf;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "source_id", length = 200)
    private String sourceId;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AnalyticsContextEntity> rows = new ArrayList<>();

    protected AnalyticsSnapshotEntity() {}

    public AnalyticsSnapshotEntity(String snapshotId, String schemaVersion, String producer,
                                   String producerVersion, Instant generatedAt, LocalDate marketAsOf,
                                   String timezone, String sourceId, String checksum, int recordCount,
                                   Instant importedAt, String importedBy, String status) {
        this.snapshotId = snapshotId;
        this.schemaVersion = schemaVersion;
        this.producer = producer;
        this.producerVersion = producerVersion;
        this.generatedAt = generatedAt;
        this.marketAsOf = marketAsOf;
        this.timezone = timezone;
        this.sourceId = sourceId;
        this.checksum = checksum;
        this.recordCount = recordCount;
        this.importedAt = importedAt;
        this.importedBy = importedBy;
        this.status = status;
    }

    public void addRow(AnalyticsContextEntity row) { row.setSnapshot(this); rows.add(row); }

    public Long getId() { return id; }
    public String getSnapshotId() { return snapshotId; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getProducer() { return producer; }
    public String getProducerVersion() { return producerVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
    public LocalDate getMarketAsOf() { return marketAsOf; }
    public String getTimezone() { return timezone; }
    public String getSourceId() { return sourceId; }
    public String getChecksum() { return checksum; }
    public int getRecordCount() { return recordCount; }
    public Instant getImportedAt() { return importedAt; }
    public String getImportedBy() { return importedBy; }
    public String getStatus() { return status; }
    public List<AnalyticsContextEntity> getRows() { return rows; }
}
