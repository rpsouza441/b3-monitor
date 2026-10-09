package dev.b3monitor.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshotEntity, Long> {

    Optional<AnalyticsSnapshotEntity> findBySnapshotId(String snapshotId);

    /** Bounded, newest-first import history (caller passes a capped Pageable). */
    List<AnalyticsSnapshotEntity> findByOrderByImportedAtDescIdDesc(Pageable pageable);

    /** The most recent snapshot overall (UI-01 shows the latest analytics context per asset). */
    Optional<AnalyticsSnapshotEntity> findFirstByOrderByMarketAsOfDescImportedAtDesc();
}
