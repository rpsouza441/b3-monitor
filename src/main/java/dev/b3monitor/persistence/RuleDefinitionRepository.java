package dev.b3monitor.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, Long> {
    Optional<RuleDefinitionEntity> findByRuleId(String ruleId);
    boolean existsByRuleId(String ruleId);

    /** Rules eligible for collection: enabled AND not paused. */
    List<RuleDefinitionEntity> findByEnabledTrueAndPausedFalse();

    /**
     * The shared LIFECYCLE FENCE (cycle-10 item A): a {@code PESSIMISTIC_WRITE} row lock on the single
     * {@code rule_definition} row. Every path that must be linearizable against admin lifecycle
     * mutations acquires THIS lock, in its own transaction, before deciding:
     * <ul>
     *   <li>{@code RuleAdminService} edit/selectMode/pause/resume/disable;</li>
     *   <li>{@code MonitorProcessingService.process()} before any rule_state/outbox mutation;</li>
     *   <li>{@code OutboxTxOps.prepareSend()} before the final eligibility / SENDING decision.</li>
     * </ul>
     * Whoever locks first wins; the others wait on the row and then observe the committed state. No
     * transaction holding this lock may span a Brapi fetch or a WAHA send. Returns empty when the rule
     * does not exist (callers fail closed).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RuleDefinitionEntity r where r.ruleId = :ruleId")
    Optional<RuleDefinitionEntity> findByRuleIdForUpdate(@Param("ruleId") String ruleId);
}
