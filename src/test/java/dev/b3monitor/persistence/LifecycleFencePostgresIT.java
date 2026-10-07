package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.rule.*;
import dev.b3monitor.monitor.MonitorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL-fence PostgreSQL IT (cycle-12 item D). Unlike {@link OutboxPostgresIT} (transport-only, allow-all
 * guard), this exercises the ACTUAL lifecycle/eligibility fence end-to-end on Postgres using the REAL
 * {@code FailClosedDispatchEligibilityGuard} + {@code PersistentRuleRegistry} + {@code RuleAdminService},
 * with WEGE3 authorized so the fence can reach {@code prepareSend}. Proves PESSIMISTIC_WRITE row-lock
 * serialization between admin mutations and dispatch:
 * <ol>
 *   <li>pause wins before prepareSend ⇒ zero send authority;</li>
 *   <li>prepareSend wins before pause ⇒ SENDING authority preserved;</li>
 *   <li>stale edit/mode revision before prepareSend ⇒ no send authority.</li>
 * </ol>
 * No DB transaction spans adapter I/O. REQUIRES Docker; runs only under {@code mvn verify -Pdocker-it}.
 */
@Testcontainers
@SpringBootTest
class LifecycleFencePostgresIT {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("b3monitor").withUsername("b3monitor").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("b3monitor.workers.enabled", () -> "false");
    }

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        /** Authorize WEGE3 so the REAL guard can reach prepareSend; everything else stays fail-closed. */
        @Bean @Primary dev.b3monitor.domain.auth.OperationalAuthorization testAuthorization() {
            return rule -> "WEGE3".equals(rule.ticker())
                    ? new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.AUTHORIZED, "test")
                    : new dev.b3monitor.domain.auth.OperationalAuthorization.Decision(
                          dev.b3monitor.domain.auth.OperationalAuthorization.Status.NOT_AUTHORIZED, "test");
        }
        @Bean @Primary CountingAdapter countingAdapter() { return new CountingAdapter(); }
    }

    static class CountingAdapter implements dev.b3monitor.domain.outbox.WahaOutboundAdapter {
        final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        @Override public dev.b3monitor.domain.outbox.SubmissionResult send(dev.b3monitor.domain.outbox.AlertIntent i) {
            sends.incrementAndGet();
            return dev.b3monitor.domain.outbox.SubmissionResult.accepted();
        }
    }

    @Autowired dev.b3monitor.persistence.RuleAdminService admin;
    @Autowired MonitorProcessingService processing;
    @Autowired OutboxTxOps outboxTx;
    @Autowired OutboxRepository outbox;
    @Autowired dev.b3monitor.domain.outbox.WahaOutboundAdapter adapter;

    private long seedCrossing(String id) {
        if (admin.find(id).isEmpty()) {
            admin.create(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        }
        return admin.selectMode(id, RuleMode.CROSSING);
    }
    private PriceRule snap(String id, long rev) {
        return new PriceRule(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), rev)
                .withMode(RuleMode.CROSSING);
    }
    private Quote q(BigDecimal p, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", p, null, null, src, NOW, false);
    }
    private int sends() { return ((CountingAdapter) adapter).sends.get(); }

    private OutboxTxOps.Claim seedPendingAndClaim(String id, long rev) {
        processing.process(snap(id, rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180)));
        processing.process(snap(id, rev), q(new BigDecimal("50.50"), NOW.minusSeconds(120)));
        return outboxTx.claimNext("it-worker");
    }

    @Test
    void pauseWinsBeforePrepareSendZeroAdapterCalls() {
        long rev = seedCrossing("pg-e1");
        var claim = seedPendingAndClaim("pg-e1", rev);
        int before = sends();
        admin.pause("pg-e1");
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.DENIED_TERMINAL, prepared.outcome());
        assertEquals(before, sends());
    }

    @Test
    void prepareSendWinsBeforePauseAuthorityPreserved() {
        long rev = seedCrossing("pg-e2");
        var claim = seedPendingAndClaim("pg-e2", rev);
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome());
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
        admin.pause("pg-e2");
        assertEquals(OutboxState.SENDING, outbox.findById(claim.rowId()).orElseThrow().getState());
    }

    @Test
    void staleRevisionIntentCannotGetSendAuthority() {
        long rev = seedCrossing("pg-e3");
        var claim = seedPendingAndClaim("pg-e3", rev);
        admin.edit("pg-e3", rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10"));
        int before = sends();
        var prepared = outboxTx.prepareSend(claim.rowId(), claim.fencingToken(), NOW);
        assertNotEquals(OutboxTxOps.PrepareOutcome.AUTHORIZED, prepared.outcome());
        assertEquals(before, sends());
    }

    @Test
    void pauseVsProcessSerializedNoStaleOutbox() {
        long rev = seedCrossing("pg-proc");
        admin.pause("pg-proc");
        var r = processing.process(snap("pg-proc", rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("RULE_PAUSED_CURRENT", r.detail());
        assertEquals(0, outbox.findByRuleIdAndState("pg-proc", OutboxState.PENDING).size());
    }
}
