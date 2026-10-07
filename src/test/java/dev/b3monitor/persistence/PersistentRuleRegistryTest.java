package dev.b3monitor.persistence;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleRegistry;
import dev.b3monitor.domain.rule.RuleSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistent typed rule registry (cycle-7 item E): the single source of truth for both the scheduler
 * (RuleSource) and the dispatch guard (RuleRegistry). Create/edit (immutable monotonic revision),
 * pause (removes from active AND reports paused), typed/bounded validation.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PersistentRuleRegistry.class, RuleAdminService.class, PersistentRuleRegistryTest.Beans.class})
class PersistentRuleRegistryTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");
    static class Beans { @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }

    @Autowired RuleAdminService admin;
    @Autowired PersistentRuleRegistry registry;   // implements both RuleSource and RuleRegistry

    private void create(String id, String ticker) {
        admin.create(id, ticker, Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
    }

    @Test
    void createdRuleIsActiveAndKnownToGuardAtRevisionOne() {
        create("r1", "WEGE3");
        RuleSource src = registry;
        assertEquals(1, src.activeRules().size());
        assertEquals("WEGE3", src.activeRules().get(0).ticker());
        assertEquals(1, src.activeRules().get(0).revision());
        RuleRegistry reg = registry;
        var st = reg.status("r1").orElseThrow();
        assertEquals(1, st.currentRevision());
        assertFalse(st.paused());
        assertFalse(st.disabled());
    }

    @Test
    void unknownRuleIsEmptyForGuard() {
        assertTrue(registry.status("nope").isEmpty(), "unknown rule → guard fails closed");
    }

    @Test
    void editBumpsRevisionAndNeverDecreases() {
        create("r1", "WEGE3");
        long rev2 = admin.edit("r1", Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10"));
        assertEquals(2, rev2);
        assertEquals(2, registry.status("r1").orElseThrow().currentRevision());
        assertEquals(2, registry.activeRules().get(0).revision());
        long rev3 = admin.edit("r1", Comparator.BELOW, new BigDecimal("40.00"), 2, BigDecimal.ZERO);
        assertEquals(3, rev3, "revision is monotonic and never decreases");
    }

    @Test
    void pauseRemovesFromActiveAndReportsPausedToGuard() {
        create("r1", "WEGE3");
        admin.pause("r1");
        assertTrue(registry.activeRules().isEmpty(), "paused rule is not collected");
        assertTrue(registry.status("r1").orElseThrow().paused(), "guard sees it paused (no dispatch)");
        admin.resume("r1");
        assertEquals(1, registry.activeRules().size());
    }

    @Test
    void disableRemovesFromActiveAndReportsDisabled() {
        create("r1", "WEGE3");
        admin.disable("r1");
        assertTrue(registry.activeRules().isEmpty());
        assertTrue(registry.status("r1").orElseThrow().disabled());
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
}
