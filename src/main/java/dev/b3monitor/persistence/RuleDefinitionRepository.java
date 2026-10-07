package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, Long> {
    Optional<RuleDefinitionEntity> findByRuleId(String ruleId);
    boolean existsByRuleId(String ruleId);

    /** Rules eligible for collection: enabled AND not paused. */
    List<RuleDefinitionEntity> findByEnabledTrueAndPausedFalse();
}
