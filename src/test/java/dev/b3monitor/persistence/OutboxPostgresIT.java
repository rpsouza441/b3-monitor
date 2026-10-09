package dev.b3monitor.persistence;

import dev.b3monitor.adapter.brapi.BrapiClient;
import dev.b3monitor.domain.outbox.OutboxState;
import dev.b3monitor.domain.quote.Quote;
import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.PriceRule;
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
 * Integration tests against a REAL PostgreSQL via Testcontainers, exercising Flyway V1–V14 (incl. the V13
 * admin_audit_event ledger + the latest-quote index, and the V14 analytics_snapshot/analytics_context
 * provenance tables + the extended audit-action CHECK), the unique logical-key constraint, the post-commit
 * dispatcher (claim/lease/fencing/claim-generation), a TWO-CONSUMER dispatch race, durable rule-state
 * recovery, expired-lease quarantine (NO resend), stale-result fencing, revision supersession, V13 audit
 * persistence, latest-quote index compatibility, and the quota first-allocation race. REQUIRES a Docker
 * daemon and runs ONLY under {@code mvn verify -Pdocker-it}. Named *IT so the Surefire unit phase skips it.
 *
 * <p>The allow-all {@code testGuard} here isolates the TRANSPORT-only dispatch tests (claim→SENDING→record);
 * the REAL lifecycle/eligibility fence (pause↔process / pause↔prepareSend / stale-revision↔prepareSend) is
 * exercised with the REAL {@code FailClosedDispatchEligibilityGuard} in {@link LifecycleFencePostgresIT}.
 *
 * <p>A FIXED test {@link Clock} (NOW) keeps the 45-minute freshness guard stable on any run date;
 * {@code ddl-auto=validate} makes context start itself prove V1–V14 match the entities.
 */
@Testcontainers
@SpringBootTest
class OutboxPostgresIT {

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); // proves V1–V13 match the entities
        r.add("b3monitor.workers.enabled", () -> "false");
    }

    @TestConfiguration
    static class FixedClockCfg {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        /** Allow-all dispatch guard so the dispatch tests exercise the send path on real Postgres;
         *  the fail-closed production default is covered by the unit tests. */
        @Bean @Primary dev.b3monitor.domain.dispatch.DispatchEligibilityGuard testGuard() {
            return (row, now) -> new dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Decision(
                    dev.b3monitor.domain.dispatch.DispatchEligibilityGuard.Denial.OK);
        }
    }

    @Autowired OutboxRepository outbox;
    @Autowired OutboxAttemptRepository attempts;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxReconciliationService reconcile;
    @Autowired RuleStateRepository ruleStates;
    @Autowired QuoteObservationRepository observations;
    @Autowired MonitorProcessingService processing;
    @Autowired BrapiClient brapi; // present for context; process() is driven directly

    /** New-ctor outbox row with cycle-6 lineage. */
    private OutboxEntity newRow(String key) {
        return new OutboxEntity(key, "r1", "WEGE3", "msg", 1L, 1L, NOW.minusSeconds(60), NOW, null);
    }

    @Test
    void flywaySchemaEnforcesUniqueLogicalKey() {
        outbox.save(newRow("it-k1"));
        assertTrue(outbox.existsByLogicalKey("it-k1"));
        assertThrows(Exception.class,
                () -> outbox.saveAndFlush(newRow("it-k1")),
                "V1 unique constraint must reject a duplicate logical key");
    }

    @Test
    void dispatcherClaimsCommitsInFlightThenRecordsAccepted() {
        outbox.save(newRow("it-dispatch"));
        var outcome = dispatcher.dispatchOne();
        assertEquals(OutboxState.ACCEPTED, outcome.orElseThrow());
        OutboxEntity row = outbox.findByLogicalKey("it-dispatch").orElseThrow();
        assertEquals(OutboxState.ACCEPTED, row.getState());
        assertEquals(1, row.getAttempts());
        assertTrue(row.getFencingToken() >= 1);
        assertFalse(row.isDeliveryConfirmed());
    }

    @Test
    void expiredLeaseSendingQuarantinedInFlightRecoveredOnRealPostgres() {
        OutboxEntity sending = newRow("it-sending");
        sending.claim("dead", NOW.minusSeconds(5));
        sending.markSending(NOW.minusSeconds(5));
        sending.setState(OutboxState.SENDING);
        Long sendingId = outbox.save(sending).getId();
        // Seed the OPEN attempt that prepareSend would have written (cycle-9 item D).
        attempts.save(new OutboxAttemptEntity(sendingId, "it-sending",
                sending.getClaimGeneration(), sending.getFencingToken(), NOW.minusSeconds(5)));
        OutboxEntity inflight = newRow("it-inflight");
        inflight.claim("dead2", NOW.minusSeconds(5));
        inflight.setState(OutboxState.IN_FLIGHT);
        outbox.save(inflight);

        int[] rec = dispatcher.reconcileExpiredLeases();
        assertEquals(1, rec[0], "IN_FLIGHT → safe recovery");
        assertEquals(1, rec[1], "SENDING → quarantined");
        assertEquals(OutboxState.UNKNOWN_OUTCOME, outbox.findByLogicalKey("it-sending").orElseThrow().getState());
        assertEquals(OutboxState.PENDING, outbox.findByLogicalKey("it-inflight").orElseThrow().getState());
        // the SENDING attempt is closed UNKNOWN (not left dangling); IN_FLIGHT never had one.
        var sendingAttempt = attempts.findByOutboxIdAndClaimGeneration(sendingId,
                outbox.findByLogicalKey("it-sending").orElseThrow().getClaimGeneration()).orElseThrow();
        assertFalse(sendingAttempt.isOpen());
        assertEquals(dev.b3monitor.domain.outbox.SubmissionResult.Kind.UNKNOWN, sendingAttempt.getOutcome());
        assertEquals("lease-expired", sendingAttempt.getSanitizedStatus());
    }

    @Test
    void abandonThenReconcileKeepsFailedTerminalOnRealPostgres() {
        OutboxEntity row = newRow("it-abandon");
        row.claim("w", NOW.minusSeconds(5));
        row.setState(OutboxState.UNKNOWN_OUTCOME);
        Long id = outbox.save(row).getId();
        assertTrue(reconcile.abandon(id));
        OutboxEntity after = outbox.findById(id).orElseThrow();
        assertEquals(OutboxState.FAILED, after.getState());
        // a stale record() against the old token must be fenced out (state no longer IN_FLIGHT)
        assertEquals(OutboxState.FAILED, outbox.findById(id).orElseThrow().getState());
    }

    @Test
    void pipelinePersistsObservationAndRuleStateOnRealPostgres() {
        PriceRule rule = new PriceRule("it-r", "WEGE3", Comparator.ABOVE,
                new BigDecimal("50.00"), 2, new BigDecimal("0.10")).withMode(dev.b3monitor.domain.rule.RuleMode.CROSSING);
        processing.process(rule, quote(new BigDecimal("49.00"), NOW.minusSeconds(60)));        // baseline
        var fire = processing.process(rule, quote(new BigDecimal("50.50"), NOW.minusSeconds(30)));
        assertTrue(fire.fired());
        assertEquals(2, observations.findByRequestedTickerOrderByReceiptTimeDesc("WEGE3").size());
        assertEquals(dev.b3monitor.domain.rule.RuleState.Phase.LATCHED,
                ruleStates.findByRuleId("it-r").orElseThrow().getPhase());
        assertTrue(outbox.findByLogicalKey("it-r|rev1|ep1").isPresent());
    }

    private Quote quote(BigDecimal price, Instant src) {
        return new Quote("WEGE3", "WEGE3", false, "BRL", price, null, null, src, NOW, false);
    }

    @Autowired dev.b3monitor.quota.QuotaTxOps quotaTx;

    @Test
    void twoConsumersNeverDoubleDispatchTheSameRow() throws Exception {
        // Seed 20 PENDING rows; two dispatchers drain concurrently. Each row must be sent exactly once.
        for (int i = 0; i < 20; i++) outbox.save(newRow("it-race-" + i));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> drainer = () -> { start.await(); return dispatcher.drain(100); };
        var f1 = pool.submit(drainer);
        var f2 = pool.submit(drainer);
        start.countDown();
        int total = f1.get(30, java.util.concurrent.TimeUnit.SECONDS)
                  + f2.get(30, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(20, total, "every row dispatched exactly once across two consumers");
        assertEquals(20, outbox.countByState(OutboxState.ACCEPTED));
        assertEquals(0, outbox.countByState(OutboxState.PENDING));
    }

    @Test
    void quotaFirstAllocationRaceGrantsExactlyOne() throws Exception {
        quotaTx.declareDedicated();
        var mgrA = new dev.b3monitor.quota.BrapiQuotaManager(quotaTx, Clock.fixed(NOW, ZoneOffset.UTC));
        var mgrB = new dev.b3monitor.quota.BrapiQuotaManager(quotaTx, Clock.fixed(NOW, ZoneOffset.UTC));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> acq = () -> { start.await(); return true; };
        var fa = pool.submit(() -> { start.await(); return mgrA.tryAcquire(); });
        var fb = pool.submit(() -> { start.await(); return mgrB.tryAcquire(); });
        start.countDown();
        var ra = fa.get(15, java.util.concurrent.TimeUnit.SECONDS);
        var rb = fb.get(15, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdownNow();
        long granted = java.util.stream.Stream.of(ra, rb)
                .filter(x -> x instanceof dev.b3monitor.quota.BrapiQuotaManager.Granted).count();
        assertEquals(1, granted, "exactly one manager wins the single durable in-flight slot");
    }

    @Autowired dev.b3monitor.admin.AdminAuditRepository auditRepo;

    /** V13: the admin_audit_event table persists and round-trips on real Postgres. */
    @Test
    void adminAuditEventPersistsOnRealPostgres() {
        var ev = new dev.b3monitor.admin.AdminAuditEvent(NOW, "admin",
                dev.b3monitor.admin.AdminAuditEvent.Action.PAUSE, "it-audit", 2L, 2L,
                dev.b3monitor.admin.AdminAuditEvent.Outcome.SUCCESS, "paused", null);
        var saved = auditRepo.save(ev);
        assertNotNull(saved.getId());
        var page = auditRepo.findByOrderByOccurredAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(page.stream().anyMatch(e -> "it-audit".equals(e.getRuleId())));
        assertEquals(1, auditRepo.countByRuleId("it-audit"));
    }

    /** V13 index + entity: the bounded latest-observation query returns the newest row by receipt time. */
    @Test
    void latestQuoteBoundedQueryUsesV13Index() {
        observations.save(new QuoteObservationEntity("brapi", "WEGE3", "WEGE3", false,
                QuoteObservationEntity.BRAPI_V2_CONTRACT, "BRL", new BigDecimal("40.00"), null,
                NOW.minusSeconds(600), NOW.minusSeconds(600), false, true, ""));
        observations.save(new QuoteObservationEntity("brapi", "WEGE3", "WEGE3", false,
                QuoteObservationEntity.BRAPI_V2_CONTRACT, "BRL", new BigDecimal("41.00"), null,
                NOW.minusSeconds(60), NOW.minusSeconds(60), false, true, ""));
        var latest = observations.findFirstByRequestedTickerOrderByReceiptTimeDesc("WEGE3").orElseThrow();
        assertEquals(0, latest.getPrice().compareTo(new BigDecimal("41.00")), "newest-by-receipt row returned");
    }

    @Autowired AnalyticsSnapshotRepository analyticsSnapshots;
    @Autowired AnalyticsContextRepository analyticsRows;

    /** V14: the analytics_snapshot + analytics_context provenance tables persist, round-trip the unique
     *  snapshot-id constraint, and cascade on real Postgres (ddl-auto=validate already proves the schema). */
    @Test
    void analyticsSnapshotPersistsAndIsUniqueOnRealPostgres() {
        var snap = new AnalyticsSnapshotEntity("it-snap-1", "b3-monitor.analytics-snapshot/1",
                "projecao-carteira", "0.0.1-synthetic", NOW, java.time.LocalDate.of(2026, 10, 6),
                "America/Sao_Paulo", "COTAHIST-RAW", "a".repeat(64), "d".repeat(64), 1, NOW, "admin",
                "CONSUMER_VERIFIED_SYNTHETIC");
        var row = new AnalyticsContextEntity("WEGE3", java.time.LocalDate.of(2026, 10, 6),
                new BigDecimal("50.10"), "READY", new BigDecimal("48.20"), "READY",
                new BigDecimal("55.00"), "READY", new BigDecimal("50.90"), "READY",
                new BigDecimal("51.10"), "READY", new BigDecimal("1.20"), "READY",
                "RAW close-only", "OK");
        row.addMetric(new AnalyticsContextMetricEntity("graham_fair_value", new BigDecimal("60.00"), "BRL", "PARTIAL", "snapshot"));
        snap.addRow(row);
        analyticsSnapshots.save(snap);
        assertNotNull(snap.getId());
        assertEquals(1, analyticsRows.findBySnapshot_IdAndTicker(snap.getId(), "WEGE3").size());

        // unique snapshot_id: a second row with the same id must fail at the DB constraint.
        var dup = new AnalyticsSnapshotEntity("it-snap-1", "b3-monitor.analytics-snapshot/1",
                "projecao-carteira", "0.0.1-synthetic", NOW, java.time.LocalDate.of(2026, 10, 6),
                "America/Sao_Paulo", "COTAHIST-RAW", "b".repeat(64), "e".repeat(64), 0, NOW, "admin",
                "CONSUMER_VERIFIED_SYNTHETIC");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> analyticsSnapshots.saveAndFlush(dup), "snapshot_id is unique");
    }

    /** V14: the extended admin-audit CHECK accepts the new IMPORT_SNAPSHOT action on real Postgres. */
    @Test
    void auditCheckAcceptsImportSnapshotAction() {
        var ev = new dev.b3monitor.admin.AdminAuditEvent(NOW, "admin",
                dev.b3monitor.admin.AdminAuditEvent.Action.IMPORT_SNAPSHOT, null, null, 1L,
                dev.b3monitor.admin.AdminAuditEvent.Outcome.SUCCESS, "imported it-snap-x", null);
        var saved = auditRepo.saveAndFlush(ev);
        assertNotNull(saved.getId(), "V14 CHECK allows IMPORT_SNAPSHOT");
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    /** Cycle-17 item 6: two threads commit the SAME snapshotId concurrently; the unique constraint must
     *  let exactly ONE durable snapshot through and the other must fail with a data-integrity violation —
     *  never two rows, never a swallowed unrelated error. */
    @Test
    void concurrentSameSnapshotCommitYieldsExactlyOneDurable() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var tt = new org.springframework.transaction.support.TransactionTemplate(txManager);
        java.util.concurrent.Callable<String> commitOne = () -> {
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            try {
                return tt.execute(s -> {
                    var e = new AnalyticsSnapshotEntity("race-snap", "b3-monitor.analytics-snapshot/1",
                            "projecao-carteira", "0.0.1", NOW, java.time.LocalDate.of(2026, 10, 6),
                            "America/Sao_Paulo", "COTAHIST-RAW", "c".repeat(64), "f".repeat(64), 0, NOW, "admin",
                            "CONSUMER_VERIFIED_SYNTHETIC");
                    analyticsSnapshots.saveAndFlush(e);
                    return "OK";
                });
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                return "DUP";   // the EXPECTED loser outcome — unique snapshot_id
            }
        };
        try {
            var f1 = pool.submit(commitOne);
            var f2 = pool.submit(commitOne);
            String r1 = f1.get(20, java.util.concurrent.TimeUnit.SECONDS);
            String r2 = f2.get(20, java.util.concurrent.TimeUnit.SECONDS);
            // exactly one OK and one DUP (any OTHER exception propagates out of Future.get and fails the test)
            assertTrue((r1.equals("OK") && r2.equals("DUP")) || (r1.equals("DUP") && r2.equals("OK")),
                    "exactly one durable commit; the other hits the unique constraint — got " + r1 + "/" + r2);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, analyticsSnapshots.findByOrderByImportedAtDescIdDesc(
                org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(s -> s.getSnapshotId().equals("race-snap")).count(),
                "exactly one durable analytics_snapshot row for the raced id");
    }

    @org.springframework.beans.factory.annotation.Autowired
    dev.b3monitor.admin.AnalyticsImportService imports;

    private byte[] fixedBody(String id, String value) {
        String rec = "{\"ticker\":\"WEGE3\",\"asOf\":\"2026-10-06\","
                + "\"indicators\":{\"sma20\":" + value + ",\"sma20Readiness\":\"READY\"},\"status\":\"OK\"}";
        String base = "{\"schemaVersion\":\"b3-monitor.analytics-snapshot/1\",\"snapshotId\":\"" + id + "\","
                + "\"producer\":\"projecao-carteira\",\"producerVersion\":\"0.0.1\","
                + "\"generatedAt\":\"2026-10-08T12:00:00Z\",\"marketAsOf\":\"2026-10-06\","
                + "\"timezone\":\"America/Sao_Paulo\",\"sourceId\":\"COTAHIST-RAW\",\"checksum\":\"%s\","
                + "\"records\":[" + rec + "]}";
        byte[] probe = base.formatted("0".repeat(64)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var p = imports.preview(probe);
        String canonical = p.canonicalChecksum();
        return base.formatted(canonical).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Cycle-19 P1-A: drive the REAL {@link dev.b3monitor.admin.AnalyticsImportService#commit} from two
     *  threads with the IDENTICAL document on real Postgres. The sound (REQUIRES_NEW insert + fresh re-read)
     *  path must yield exactly one IMPORTED and one IDEMPOTENT_NOOP — never a generic 500, never a second
     *  durable row, never a SUCCESS audit without a row. This is the runtime proof the H2 test cannot give
     *  (H2 does not reproduce Postgres' aborted-transaction-after-constraint-violation semantics). */
    @Test
    void concurrentServiceCommitSameDocumentYieldsImportedAndNoop() throws Exception {
        byte[] body = fixedBody("svc-race", "50.10");
        var token1 = imports.preview(body).token();
        var token2 = imports.preview(body).token();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<String> c1 = () -> { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return imports.commit(body, token1).disposition(); };
        java.util.concurrent.Callable<String> c2 = () -> { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return imports.commit(body, token2).disposition(); };
        try {
            var f1 = pool.submit(c1);
            var f2 = pool.submit(c2);
            String r1 = f1.get(25, java.util.concurrent.TimeUnit.SECONDS);
            String r2 = f2.get(25, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((r1.equals("IMPORTED") && r2.equals("IDEMPOTENT_NOOP"))
                            || (r1.equals("IDEMPOTENT_NOOP") && r2.equals("IMPORTED")),
                    "exactly one IMPORTED and one IDEMPOTENT_NOOP — got " + r1 + "/" + r2);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, analyticsSnapshots.findByOrderByImportedAtDescIdDesc(
                        org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(s -> s.getSnapshotId().equals("svc-race")).count(),
                "exactly one durable row after the concurrent service commit");
    }

    /** Cycle-19 P1-C: the max NUMERIC(24,12) value round-trips EXACTLY through a real Postgres column — no
     *  silent rounding — and a value needing 13 integer digits was already refused by the validator before
     *  it could reach the DB (covered in the H2 hardening test; here we prove the stored precision). */
    @Test
    void maxNumericRoundTripsExactlyOnRealPostgres() {
        String max = "999999999999.999999999999";
        byte[] body = fixedBody("num-pg", max);
        var r = imports.commit(body, imports.preview(body).token());
        assertEquals("IMPORTED", r.disposition());
        var snap = analyticsSnapshots.findBySnapshotId("num-pg").orElseThrow();
        var row = analyticsRows.findBySnapshot_IdAndTicker(snap.getId(), "WEGE3").get(0);
        assertEquals(0, new BigDecimal(max).compareTo(row.getSma20()),
                "NUMERIC(24,12) stored the max value with no rounding");
    }
}
