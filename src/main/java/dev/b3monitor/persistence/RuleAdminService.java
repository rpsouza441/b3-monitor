package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

/**
 * Local administration of persistent rule definitions (cycle-7 item E). A DOMAIN service with NO HTTP
 * endpoint — a secure authenticated surface is future work; exposing an insecure endpoint now would be
 * worse than none. All mutations are typed and bounded (validation lives in the entity), use the
 * injected {@link Clock} for audit timestamps, bump an immutable monotonic revision on edit, and rely
 * on the entity's {@code @Version} for mutation-conflict handling. Pausing a rule removes it from
 * collection (RuleSource) AND makes the dispatch guard deny it. Nothing here activates an asset.
 */
@Service
public class RuleAdminService {

    private final RuleDefinitionRepository repo;
    private final Clock clock;

    public RuleAdminService(RuleDefinitionRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Create a new rule. Fails if the ruleId already exists. Returns the created entity. */
    @Transactional
    public RuleDefinitionEntity create(String ruleId, String ticker, Comparator comparator,
                                       BigDecimal threshold, int precision, BigDecimal hysteresis) {
        if (repo.existsByRuleId(ruleId)) {
            throw new IllegalStateException("rule already exists: " + ruleId);
        }
        return repo.save(new RuleDefinitionEntity(
                ruleId, ticker, comparator, threshold, precision, hysteresis, clock.instant()));
    }

    /** Edit a rule's typed parameters: bumps the revision (never decreases). Returns the new revision. */
    @Transactional
    public long edit(String ruleId, Comparator comparator, BigDecimal threshold,
                     int precision, BigDecimal hysteresis) {
        RuleDefinitionEntity e = require(ruleId);
        long rev = e.applyEdit(comparator, threshold, precision, hysteresis, clock.instant());
        repo.save(e);
        return rev;
    }

    @Transactional
    public void pause(String ruleId) { RuleDefinitionEntity e = require(ruleId); e.setPaused(true, clock.instant()); repo.save(e); }

    @Transactional
    public void resume(String ruleId) { RuleDefinitionEntity e = require(ruleId); e.setPaused(false, clock.instant()); repo.save(e); }

    @Transactional
    public void disable(String ruleId) { RuleDefinitionEntity e = require(ruleId); e.setEnabled(false, clock.instant()); repo.save(e); }

    @Transactional(readOnly = true)
    public Optional<RuleDefinitionEntity> find(String ruleId) { return repo.findByRuleId(ruleId); }

    private RuleDefinitionEntity require(String ruleId) {
        return repo.findByRuleId(ruleId)
                .orElseThrow(() -> new IllegalStateException("unknown rule: " + ruleId));
    }
}
