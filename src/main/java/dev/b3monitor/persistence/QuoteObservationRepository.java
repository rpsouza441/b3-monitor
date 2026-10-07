package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteObservationRepository extends JpaRepository<QuoteObservationEntity, Long> {
    List<QuoteObservationEntity> findByRequestedTickerOrderByReceiptTimeDesc(String requestedTicker);

    /** Cycle-11 item F: bounded single-row latest observation (no full-list materialization). */
    Optional<QuoteObservationEntity> findFirstByRequestedTickerOrderByReceiptTimeDesc(String requestedTicker);
}
