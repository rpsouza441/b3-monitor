package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteObservationRepository extends JpaRepository<QuoteObservationEntity, Long> {
    List<QuoteObservationEntity> findByRequestedTickerOrderByReceiptTimeDesc(String requestedTicker);
}
