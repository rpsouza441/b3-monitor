package dev.b3monitor.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEvent, Long> {
    /** Bounded, newest-first audit page (cycle-11 F: callers pass a capped Pageable). */
    List<AdminAuditEvent> findByOrderByOccurredAtDescIdDesc(Pageable pageable);

    long countByRuleId(String ruleId);
}
