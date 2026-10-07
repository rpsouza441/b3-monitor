package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.domain.rule.RuleRegistry;
import dev.b3monitor.domain.rule.RuleSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistent typed rule registry (cycle-7 item E + cycle-8 RuleMode): single source of truth for the
 * scheduler (RuleSource) and the dispatch guard (RuleRegistry). New rules default to UNSELECTED
 * (fail-closed, not collected); an explicit CROSSING selection bumps the revision and makes it active.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PersistentRuleRegistry.class, RuleAdminService.class, dev.b3monitor.admin.AdminAuditService.class, PersistentRuleRegistryTest.Beans.class})
class PersistentRuleRegistryTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");
    static class Beans {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean dev.b3monitor.domain.dispatch.DispatchEligibilityGuard guard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
        @Bean OutboxTxOps outboxTxOps(OutboxRepository r, OutboxAttemptRepository ar,
                dev.b3monitor.domain.dispatch.DispatchEligibilityGuard g, RuleDefinitionRepository rd, Clock c) { return new OutboxTxOps(r, ar, g, rd, c); }
    }

    @Autowired RuleAdminService admin;
    @Autowired PersistentRuleRegistry registry;   // RuleSource + RuleRegistry

    private void create(String id, String ticker) {
        admin.create(id, ticker, Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
    }

    @Test
    void newRuleIsUnselectedAndNotCollected() {
        create("r1", "WEGE3");
        assertTrue(registry.activeRules().isEmpty(), "UNSELECTED rule is not collected (fail-closed)");
        var st = registry.status("r1").orElseThrow();
        assertEquals(RuleMode.UNSELECTED, st.mode());
        assertEquals(1, st.currentRevision());
    }

    @Test
    void selectingCrossingBumpsRevisionAndActivates() {
        create("r1", "WEGE3");
        long rev = admin.selectMode("r1", RuleMode.CROSSING);
        assertEquals(2, rev, "mode selection bumps the revision");
        assertEquals(1, registry.activeRules().size());
        assertEquals(RuleMode.CROSSING, registry.status("r1").orElseThrow().mode());
        assertEquals(2, registry.activeRules().get(0).revision());
    }

    @Test
    void levelModeIsRefused() {
        create("r1", "WEGE3");
        assertThrows(IllegalArgumentException.class, () -> admin.selectMode("r1", RuleMode.LEVEL),
                "LEVEL is not activatable (Q-19 pending)");
    }

    @Test
    void unknownRuleIsEmptyForGuard() {
        assertTrue(registry.status("nope").isEmpty(), "unknown rule → guard fails closed");
    }

    @Test
    void editBumpsRevisionAndNeverDecreases() {
        create("r1", "WEGE3");
        admin.selectMode("r1", RuleMode.CROSSING);                 // rev 2
        long rev3 = admin.edit("r1", 2, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10"));
        assertEquals(3, rev3);
        assertEquals(3, registry.activeRules().get(0).revision());
    }

    @Test
    void pauseRemovesFromActiveAndReportsPausedToGuard() {
        create("r1", "WEGE3");
        admin.selectMode("r1", RuleMode.CROSSING);
        admin.pause("r1");
        assertTrue(registry.activeRules().isEmpty(), "paused rule is not collected");
        assertTrue(registry.status("r1").orElseThrow().paused(), "guard sees it paused");
        admin.resume("r1");
        assertEquals(1, registry.activeRules().size());
    }

    @Test
    void duplicateCreateRejected() {
        create("r1", "WEGE3");
        assertThrows(IllegalStateException.class, () -> create("r1", "PETR4"));
    }

    @Test
    void invalidThresholdRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> admin.create("bad", "WEGE3", Comparator.ABOVE, new BigDecimal("-1"), 2, BigDecimal.ZERO));
    }

    @Test
    void thresholdScaleBeyondSixRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> admin.create("bad2", "WEGE3", Comparator.ABOVE, new BigDecimal("50.1234567"), 2, BigDecimal.ZERO),
                "scale > 6 cannot round-trip NUMERIC(19,6)");
    }
}
