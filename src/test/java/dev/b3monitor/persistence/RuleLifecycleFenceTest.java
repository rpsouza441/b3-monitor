package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.quote.QuoteValidator;
import dev.b3monitor.domain.rule.*;
import dev.b3monitor.monitor.MonitorProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-10 items A+B — the linearizable lifecycle fence (PESSIMISTIC_WRITE on rule_definition) between
 * admin mutations and in-flight processing, plus retry-safe/idempotent mutation semantics. Full
 * {@code @SpringBootTest} on H2 with NO ambient transaction (asserted), driving the real services and
 * repositories so the row-lock transaction boundaries are genuinely exercised.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleLifecycleFenceTest {

    static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired RuleAdminService admin;
    @Autowired MonitorProcessingService processing;
    @Autowired RuleDefinitionRepository ruleDefs;
    @Autowired RuleStateRepository ruleStates;
    @Autowired OutboxRepository outbox;

    private long seedCrossing(String id) {
        admin.find(id).ifPresent(e -> {});
        if (admin.find(id).isEmpty()) {
            admin.create(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"));
        }
        return admin.selectMode(id, RuleMode.CROSSING);   // rev 2
    }

    private PriceRule snapshot(String id, long rev) {
        return new PriceRule(id, "WEGE3", Comparator.ABOVE, new BigDecimal("50.00"), 2, new BigDecimal("0.10"), rev)
                .withMode(RuleMode.CROSSING);
    }
    private Quote q(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }
    private void cleanup(String id) {
        outbox.findByRuleIdAndState(id, OutboxState.PENDING).forEach(outbox::delete);
        ruleStates.findByRuleId(id).ifPresent(ruleStates::delete);
        admin.find(id).flatMap(e -> ruleDefs.findByRuleId(id)).ifPresent(ruleDefs::delete);
    }

    @Test
    void noAmbientTransaction() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** A1 — the fetch snapshot is for the current revision, but a pause commits before process() runs;
     *  the fence then observes paused and refuses: no state mutation, no PENDING. */
    @Test
    void pauseBeforeProcessDeniesStaleEvaluation() {
        String id = "fence-a1";
        long rev = seedCrossing(id);
        PriceRule snap = snapshot(id, rev);                // fetched while active
        admin.pause(id);                                   // admin wins the race
        var r = processing.process(snap, q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("RULE_PAUSED_CURRENT", r.detail());
        assertTrue(ruleStates.findByRuleId(id).isEmpty(), "no rule_state mutation on a fenced-out evaluation");
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
        cleanup(id);
    }

    /** A2 — process wins first and creates PENDING; a later pause cancels that unsent PENDING. */
    @Test
    void processWinsThenPauseCancelsResultingPending() {
        String id = "fence-a2";
        long rev = seedCrossing(id);
        PriceRule snap = snapshot(id, rev);
        processing.process(snap, q(new BigDecimal("49.00"), NOW.minusSeconds(120))); // ARMED
        var fire = processing.process(snap, q(new BigDecimal("50.50"), NOW.minusSeconds(90)));
        assertTrue(fire.fired());
        assertEquals(1, outbox.countByState(OutboxState.PENDING));
        admin.pause(id);                                   // pause after → cancels the unsent PENDING
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
        assertEquals(1, outbox.countByState(OutboxState.CANCELLED));
        cleanup(id);
    }

    /** A3 — an edit commits (new revision) while a fetch is in flight; the old-revision snapshot cannot
     *  evaluate/mutate after the fetch returns. */
    @Test
    void editWinsSoStaleRevisionSnapshotIsDenied() {
        String id = "fence-a3";
        long rev = seedCrossing(id);
        PriceRule staleSnap = snapshot(id, rev);           // fetched at rev
        admin.edit(id, rev, Comparator.ABOVE, new BigDecimal("55.00"), 2, new BigDecimal("0.10")); // rev+1
        var r = processing.process(staleSnap, q(new BigDecimal("50.50"), NOW.minusSeconds(60)));
        assertFalse(r.fired());
        assertEquals("STALE_RULE_DEFINITION", r.detail());
        assertTrue(ruleStates.findByRuleId(id).isEmpty());
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
        cleanup(id);
    }

    /** A4 — a true concurrent race: a latch holds the admin pause transaction mid-flight while process
     *  runs; the fence serializes them and the outcome is consistent (no PENDING survives active). */
    @Test
    void concurrentPauseAndProcessAreLinearizedNoDeadlock() throws Exception {
        String id = "fence-a4";
        long rev = seedCrossing(id);
        processing.process(snapshot(id, rev), q(new BigDecimal("49.00"), NOW.minusSeconds(180))); // ARMED

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> f1 = pool.submit(() -> { go.await(); admin.pause(id); return null; });
        Future<?> f2 = pool.submit(() -> { go.await();
            try { processing.process(snapshot(id, rev), q(new BigDecimal("50.50"), NOW.minusSeconds(60))); }
            catch (Exception ignored) {} return null; });
        go.countDown();
        f1.get(15, TimeUnit.SECONDS); f2.get(15, TimeUnit.SECONDS);   // completes → no deadlock
        pool.shutdownNow();
        // Whatever the interleaving, an active rule must never be left with an uncancelled PENDING:
        // either process lost (no PENDING) or process won then pause cancelled it.
        assertEquals(0, outbox.countByState(OutboxState.PENDING),
                "no unsent PENDING survives a committed pause");
        cleanup(id);
    }

    // ----- item B: retry-safe / idempotent mutations -----

    @Test
    void duplicateResumeDoesNotReMarkRebaseline() {
        String id = "fence-b1";
        seedCrossing(id);
        ruleStates.save(new RuleStateEntity(id));
        admin.pause(id);
        admin.resume(id);                                  // real transition → marks rebaseline
        assertTrue(ruleStates.findByRuleId(id).orElseThrow().isRebaselineRequired());
        // consume the marker, then a duplicate resume must NOT set it again
        var rs = ruleStates.findByRuleId(id).orElseThrow(); rs.clearRebaselineRequired(); ruleStates.save(rs);
        admin.resume(id);                                  // already active → no-op
        assertFalse(ruleStates.findByRuleId(id).orElseThrow().isRebaselineRequired(),
                "duplicate resume on an active rule must not re-mark rebaseline");
        cleanup(id);
    }

    @Test
    void duplicatePauseIsIdempotent() {
        String id = "fence-b2";
        seedCrossing(id);
        long rev = admin.find(id).orElseThrow().getRevision();
        admin.pause(id);
        admin.pause(id);                                   // idempotent — no revision mint
        assertEquals(rev, admin.find(id).orElseThrow().getRevision());
        assertTrue(admin.find(id).orElseThrow().isPaused());
        cleanup(id);
    }

    @Test
    void duplicateCrossingSelectionDoesNotMintExtraRevision() {
        String id = "fence-b3";
        long rev = seedCrossing(id);                       // first selection bumps to rev
        long again = admin.selectMode(id, RuleMode.CROSSING);
        assertEquals(rev, again, "selecting the already-current mode is an idempotent no-op");
        cleanup(id);
    }

    @Test
    void staleExpectedRevisionEditIsRejected() {
        String id = "fence-b4";
        long rev = seedCrossing(id);
        assertThrows(RuleAdminService.StaleRevisionException.class,
                () -> admin.edit(id, rev - 1, Comparator.ABOVE, new BigDecimal("60.00"), 2, new BigDecimal("0.10")));
        assertEquals(rev, admin.find(id).orElseThrow().getRevision(), "no mutation on a stale expectedRevision");
        cleanup(id);
    }
}
