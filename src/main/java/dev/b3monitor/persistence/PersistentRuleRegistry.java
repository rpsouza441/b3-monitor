package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.PriceRule;
import dev.b3monitor.domain.rule.RuleRegistry;
import dev.b3monitor.domain.rule.RuleSource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The persistent typed rule registry (cycle-7 item E): the SINGLE source of truth backing both the
 * scheduler's {@link RuleSource#activeRules()} and the dispatch guard's {@link RuleRegistry#status}.
 * Marked {@link Primary} so it supersedes the fail-closed {@code EmptyRuleRegistry} placeholder when
 * the persistence layer is present. An unknown rule still yields {@link Optional#empty()} → the guard
 * fails closed exactly as before.
 */
@Service
@Primary
public class PersistentRuleRegistry implements RuleSource, RuleRegistry {

    private final RuleDefinitionRepository repo;

    public PersistentRuleRegistry(RuleDefinitionRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceRule> activeRules() {
        return repo.findByEnabledTrueAndPausedFalse().stream()
                .map(e -> new PriceRule(e.getRuleId(), e.getTicker(), e.getComparator(),
                        e.getThreshold(), e.getPrecision(), e.getHysteresis(), e.getRevision()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RuleStatus> status(String ruleId) {
        return repo.findByRuleId(ruleId)
                .map(e -> new RuleStatus(e.getRuleId(), e.getRevision(), e.isPaused(), !e.isEnabled()));
    }
}
