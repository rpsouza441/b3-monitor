package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrapiQuotaRepository extends JpaRepository<BrapiQuotaEntity, Long> {
    Optional<BrapiQuotaEntity> findByCycleKey(String cycleKey);
}
