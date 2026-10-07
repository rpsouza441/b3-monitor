package dev.b3monitor.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleStateRepository extends JpaRepository<RuleStateEntity, Long> {
    Optional<RuleStateEntity> findByRuleId(String ruleId);
}
