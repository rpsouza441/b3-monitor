package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsContextMetricRepository extends JpaRepository<AnalyticsContextMetricEntity, Long> {

    /** Metrics for one context row, read directly (no lazy nav). */
    List<AnalyticsContextMetricEntity> findByContext_Id(Long contextId);
}
