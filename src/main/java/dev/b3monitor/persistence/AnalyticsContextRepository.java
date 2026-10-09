package dev.b3monitor.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalyticsContextRepository extends JpaRepository<AnalyticsContextEntity, Long> {

    /** All context rows for one snapshot (bounded by MAX_RECORDS at import time). */
    List<AnalyticsContextEntity> findBySnapshot_Id(Long snapshotId);

    /** One asset's row within a given snapshot (UI-01 per-asset analytics context). */
    List<AnalyticsContextEntity> findBySnapshot_IdAndTicker(Long snapshotId, String ticker);

    /**
     * Cycle-18 item D: the LATEST valid context for ONE ticker across ALL committed snapshots, ordered by
     * snapshot.marketAsOf DESC, snapshot.importedAt DESC, snapshot.id DESC, context.id DESC — a deterministic
     * per-ticker selection that is correct for PARTIAL snapshots (an older snapshot's ticker stays current
     * when the newest snapshot omits it). Caller passes {@code PageRequest.of(0,1)} for the single latest.
     */
    @Query("""
           select c from AnalyticsContextEntity c
           join c.snapshot s
           where c.ticker = :ticker
           order by s.marketAsOf desc, s.importedAt desc, s.id desc, c.id desc
           """)
    List<AnalyticsContextEntity> findLatestForTicker(@Param("ticker") String ticker, Pageable page);
}
