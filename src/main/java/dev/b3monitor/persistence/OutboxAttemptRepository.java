package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxAttemptRepository extends JpaRepository<OutboxAttemptEntity, Long> {
    List<OutboxAttemptEntity> findByOutboxIdOrderByStartedAtAsc(Long outboxId);
    Optional<OutboxAttemptEntity> findByOutboxIdAndClaimGeneration(Long outboxId, long claimGeneration);
    long countByOutboxId(Long outboxId);
}
