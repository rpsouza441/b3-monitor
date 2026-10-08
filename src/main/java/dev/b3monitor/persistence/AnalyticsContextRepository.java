package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsContextRepository extends JpaRepository<AnalyticsContextEntity, Long> {

    /** All context rows for one snapshot (bounded by MAX_RECORDS at import time). */
    List<AnalyticsContextEntity> findBySnapshot_Id(Long snapshotId);

    /** One asset's row within a given snapshot (UI-01 per-asset analytics context). */
    List<AnalyticsContextEntity> findBySnapshot_IdAndTicker(Long snapshotId, String ticker);
}
