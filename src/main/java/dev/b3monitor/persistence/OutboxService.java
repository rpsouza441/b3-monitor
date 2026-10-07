package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.AlertIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists alert intents idempotently as {@code PENDING}. This class NEVER dispatches —
 * dispatch is a strictly post-commit concern owned by {@link OutboxDispatcher}. Enqueue is
 * idempotent on {@code logicalKey}: a repeated enqueue of the same episode is a no-op, so a
 * crash/retry cannot create duplicate alerts.
 *
 * <p>Per the independent review (P0-2): the triggering event is committed with the outbox
 * {@code PENDING} row in one atomic transaction, and sending happens later from a separate
 * transaction after that commit is durable. There is no HTTP/adapter call on this path.
 */
@Service
public class OutboxService {

    private final OutboxRepository repo;

    public OutboxService(OutboxRepository repo) {
        this.repo = repo;
    }

    /**
     * Idempotently persist an intent as PENDING, joining the CALLER's transaction (so it commits
     * atomically with the observation + rule_state). Propagation REQUIRED: in production it always
     * runs inside {@code MonitorProcessingService.process}'s transaction and joins it; the atomicity
     * guarantee is that single transaction, proven by {@code SchedulerTransactionTest}.
     */
    @Transactional
    public OutboxEntity enqueue(AlertIntent intent) {
        Optional<OutboxEntity> existing = repo.findByLogicalKey(intent.logicalKey());
        if (existing.isPresent()) {
            return existing.get(); // dedup: already enqueued for this episode
        }
        OutboxEntity e = new OutboxEntity(
                intent.logicalKey(), intent.ruleId(), intent.ticker(), intent.message(),
                intent.ruleRevision(), intent.episodeEpoch(), intent.sourceAsOf(),
                intent.createdAt(), intent.expiresAt());
        return repo.save(e);
    }
}
