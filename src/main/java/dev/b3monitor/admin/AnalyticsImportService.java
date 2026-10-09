package dev.b3monitor.admin;

import dev.b3monitor.admin.AdminAuditEvent.Action;
import dev.b3monitor.admin.AdminAuditEvent.Outcome;
import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract;
import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract.*;
import dev.b3monitor.domain.analytics.AnalyticsSnapshotValidator;
import dev.b3monitor.domain.analytics.AnalyticsSnapshotValidator.Result;
import dev.b3monitor.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * ADMIN-only analytics-snapshot IMPORT service (cycle-16; FIN-01/FIN-03). Two-step preview → commit:
 * <ul>
 *   <li><b>preview</b> validates the exact bytes WITHOUT persisting and reports schema/producer/snapshot/
 *       as-of/count/checksum/errors + {@code wouldImport}. The {@code canonicalChecksum} it returns is the
 *       cryptographic preview token.</li>
 *   <li><b>commit</b> re-validates the exact resubmitted bytes and requires {@code expectedChecksum} (the
 *       preview token) to equal the freshly computed canonical checksum — so changed content between
 *       preview and commit is REJECTED. An identical re-import of the same {@code snapshotId}+checksum is
 *       idempotent (NO_OP); the same id with a different checksum is a CONFLICT (never a silent overwrite).</li>
 * </ul>
 * Commit writes provenance + per-asset context rows and appends an {@code IMPORT_SNAPSHOT} audit event. It
 * NEVER authorizes an asset, enables a worker, triggers Brapi/WAHA, or modifies a rule. Importing is
 * CONTEXT only; status is always {@code CONSUMER_VERIFIED_SYNTHETIC}.
 */
@Service
public class AnalyticsImportService {

    public record PreviewResult(boolean wouldImport, String schemaVersion, String snapshotId,
                                String producer, String producerVersion, String marketAsOf,
                                int recordCount, String canonicalChecksum, String documentDigest, String token,
                                String disposition, List<String> errors) {}

    public record CommitResult(String disposition, String snapshotId, String checksum, int recordCount) {
        // disposition ∈ IMPORTED | IDEMPOTENT_NOOP | REJECTED_CONFLICT | REJECTED_VALIDATION | REJECTED_TOKEN_<reason>
    }

    private final AnalyticsSnapshotValidator validator;
    private final AnalyticsSnapshotRepository snapshots;
    private final AdminAuditService audit;
    private final AnalyticsPreviewToken tokens;
    private final Clock clock;
    /** Explicit transaction boundaries (cycle-19 P1-A): the insert attempt runs in its OWN committed/rolled-
     *  back transaction so a PostgreSQL unique-violation aborts ONLY that inner tx; the orchestration method
     *  itself is NOT @Transactional, so the post-race re-read runs on a fresh, usable connection. */
    private final org.springframework.transaction.support.TransactionTemplate writeTx;
    private final org.springframework.transaction.support.TransactionTemplate readTx;

    public AnalyticsImportService(AnalyticsSnapshotValidator validator, AnalyticsSnapshotRepository snapshots,
                                  AdminAuditService audit, AnalyticsPreviewToken tokens, Clock clock,
                                  org.springframework.transaction.PlatformTransactionManager txManager) {
        this.validator = validator;
        this.snapshots = snapshots;
        this.audit = audit;
        this.tokens = tokens;
        this.clock = clock;
        this.writeTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.writeTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.readTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** SHA-256 of the EXACT raw request bytes — the full-document digest (envelope + records + whitespace). */
    private static String documentDigest(byte[] raw) {
        return AnalyticsSnapshotValidator.sha256Hex(raw == null ? new byte[0] : raw);
    }

    /** Validate-only; persists nothing. Issues an HMAC token bound to BOTH the records checksum AND the
     *  full-document digest (so an envelope-only or whitespace change invalidates the token). */
    @Transactional(readOnly = true)
    public PreviewResult preview(byte[] raw) {
        Result v = validator.validate(raw);
        if (!v.valid()) {
            return new PreviewResult(false, null, null, null, null, null, 0, null, null, null, "REJECTED", v.errors());
        }
        Snapshot s = v.snapshot();
        String docDigest = documentDigest(raw);
        String token = tokens.issue(s.schemaVersion(), s.snapshotId(), v.canonicalChecksum(), docDigest,
                AdminAuditService.currentActor());
        return new PreviewResult(true, s.schemaVersion(), s.snapshotId(), s.producer(), s.producerVersion(),
                String.valueOf(s.marketAsOf()), s.records().size(), v.canonicalChecksum(), docDigest, token,
                "CONSUMER_VERIFIED_SYNTHETIC", List.of());
    }

    /**
     * Re-validate and persist. {@code token} is the HMAC preview token; it is verified against the LIVE
     * commit context — purpose, schemaVersion, snapshotId, records checksum, FULL-DOCUMENT digest AND actor
     * must all match, and it must not be expired. So an envelope-only change (producer/version/generatedAt/
     * marketAsOf/timezone/sourceId/quality), a whitespace change, a records change, or a different actor all
     * move a bound field and reject fail-closed.
     *
     * <p>Idempotency/conflict is on the FULL DOCUMENT identity: same snapshotId + same document digest ⇒
     * NO_OP; same snapshotId + different document ⇒ CONFLICT (never a silent overwrite).
     *
     * <p><b>Transaction soundness (cycle-19 P1-A).</b> This method is deliberately NOT {@code @Transactional}.
     * On PostgreSQL a unique-constraint violation aborts the ENTIRE enclosing transaction ("current
     * transaction is aborted, commands ignored until end of transaction block"), so the old
     * catch-and-requery inside one {@code @Transactional} could not read the winner's row. Instead the
     * durable insert runs in its OWN {@code REQUIRES_NEW} transaction ({@link #writeTx}); if a concurrent
     * commit of the same snapshotId won the unique constraint, ONLY that inner transaction aborts and rolls
     * back (insert + its SUCCESS audit together — never a SUCCESS audit without a row). The loser then
     * re-reads the durable winner on a FRESH {@code REQUIRES_NEW} read transaction ({@link #readTx}) and
     * disposes by full document identity. A {@code DataIntegrityViolationException} that is NOT the
     * snapshot-id race (the row still isn't there) is re-thrown — never translated into idempotency.
     */
    public CommitResult commit(byte[] raw, String token) {
        Result v = validator.validate(raw);
        if (!v.valid()) {
            audit.recordRejection(Action.IMPORT_SNAPSHOT, null, null, Outcome.REJECTED_VALIDATION,
                    "snapshot import rejected: " + firstError(v.errors()));
            return new CommitResult("REJECTED_VALIDATION", null, null, 0);
        }
        Snapshot s = v.snapshot();
        String checksum = v.canonicalChecksum();
        String docDigest = documentDigest(raw);
        String actor = AdminAuditService.currentActor();

        var verdict = tokens.verify(token, s.schemaVersion(), s.snapshotId(), checksum, docDigest, actor);
        if (!verdict.valid()) {
            audit.recordRejection(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, Outcome.REJECTED_VALIDATION,
                    "snapshot import rejected: preview token " + verdict.rejection() + " for " + s.snapshotId());
            return new CommitResult("REJECTED_TOKEN_" + verdict.rejection(), s.snapshotId(), checksum, 0);
        }

        // Phase 1 — pre-check in its own read tx: the common path when the snapshot already exists.
        var pre = readTx.execute(st -> snapshots.findBySnapshotId(s.snapshotId()));
        if (pre != null && pre.isPresent()) return disposeExisting(pre.get(), s, checksum, docDigest);

        // Phase 2 — insert + SUCCESS audit in ONE committed/rolled-back write tx (REQUIRES_NEW).
        try {
            return writeTx.execute(st -> {
                AnalyticsSnapshotEntity ent = build(s, checksum, docDigest, actor);
                snapshots.saveAndFlush(ent);   // flush so a unique-violation surfaces HERE, inside THIS tx
                audit.record(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, (long) s.records().size(),
                        Outcome.SUCCESS, "imported snapshot " + s.snapshotId() + " (" + s.records().size()
                                + " records, checksum " + checksum.substring(0, 12) + "…)");
                return new CommitResult("IMPORTED", s.snapshotId(), checksum, s.records().size());
            });
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            // The inner write tx has already rolled back (no row, no SUCCESS audit). This connection/thread
            // is clean. Re-read the durable winner on a FRESH read tx and dispose by full document identity.
            var now = readTx.execute(st -> snapshots.findBySnapshotId(s.snapshotId()));
            if (now != null && now.isPresent()) return disposeExisting(now.get(), s, checksum, docDigest);
            throw race;   // NOT the snapshot-id race we understand — never swallow an unrelated integrity error
        }
    }

    /**
     * Resolve a re-import against an already-durable snapshot by FULL document identity.
     *
     * <p><b>Legacy policy (cycle-19 P2).</b> V14 rows were persisted before the full-document digest existed,
     * so their {@code document_digest} is NULL and the original raw bytes were never stored — we CANNOT prove
     * that a new submission is byte-identical to the historical document. Records-checksum equality is NOT
     * such proof (two documents with identical records but a different envelope/provenance share a records
     * checksum — exactly the gap cycle-18 closed). So a re-import whose snapshotId collides with a legacy
     * NULL-digest row is treated FAIL-CLOSED as a {@code REJECTED_CONFLICT_LEGACY_NO_DIGEST}: we neither
     * fabricate a digest for the old row (no destructive back-fill) nor claim an unprovable NO_OP. The
     * operator imports under a fresh snapshotId. V15+ rows carry a real digest and get exact NO_OP/CONFLICT.
     */
    private CommitResult disposeExisting(AnalyticsSnapshotEntity existing, Snapshot s, String checksum, String docDigest) {
        if (existing.getDocumentDigest() == null) {
            audit.recordRejection(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, Outcome.REJECTED_CONFLICT,
                    "snapshot id reuses a legacy row with no document digest (identity unprovable): " + s.snapshotId());
            return new CommitResult("REJECTED_CONFLICT_LEGACY_NO_DIGEST", s.snapshotId(), checksum, 0);
        }
        if (docDigest.equals(existing.getDocumentDigest())) {
            audit.record(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, null, Outcome.NO_OP,
                    "snapshot already imported (idempotent, same document): " + s.snapshotId());
            return new CommitResult("IDEMPOTENT_NOOP", s.snapshotId(), checksum, existing.getRecordCount());
        }
        audit.recordRejection(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, Outcome.REJECTED_CONFLICT,
                "snapshot id reused with a different document: " + s.snapshotId());
        return new CommitResult("REJECTED_CONFLICT", s.snapshotId(), checksum, 0);
    }

    private AnalyticsSnapshotEntity build(Snapshot s, String checksum, String docDigest, String actor) {
        AnalyticsSnapshotEntity ent = new AnalyticsSnapshotEntity(
                s.snapshotId(), s.schemaVersion(), s.producer(), s.producerVersion(), s.generatedAt(),
                s.marketAsOf(), s.timezone(), s.sourceId(), checksum, docDigest, s.records().size(),
                clock.instant(), actor, ConsumerStatus.CONSUMER_VERIFIED_SYNTHETIC.name());
        for (AnalyticsSnapshotContract.Record r : s.records()) {
            IndicatorSet ind = r.indicators();
            AnalyticsContextEntity row = new AnalyticsContextEntity(r.ticker(), r.asOf(),
                    v(ind, Ind.SMA20), rd(ind, Ind.SMA20), v(ind, Ind.SMA50), rd(ind, Ind.SMA50),
                    v(ind, Ind.RSI14), rd(ind, Ind.RSI14), v(ind, Ind.EMA9), rd(ind, Ind.EMA9),
                    v(ind, Ind.EMA21), rd(ind, Ind.EMA21), v(ind, Ind.VOL), rd(ind, Ind.VOL),
                    r.quality(), r.status());
            if (r.context() != null) {
                for (ContextMetric c : r.context()) {   // lossless: every validated field persisted structurally
                    row.addMetric(new AnalyticsContextMetricEntity(c.name(), c.value(), c.units(),
                            c.readiness() == null ? null : c.readiness().name(), c.quality()));
                }
            }
            ent.addRow(row);
        }
        return ent;
    }

    private enum Ind { SMA20, SMA50, RSI14, EMA9, EMA21, VOL }

    private static java.math.BigDecimal v(IndicatorSet s, Ind i) {
        if (s == null) return null;
        return switch (i) {
            case SMA20 -> s.sma20(); case SMA50 -> s.sma50(); case RSI14 -> s.rsi14();
            case EMA9 -> s.ema9();   case EMA21 -> s.ema21(); case VOL -> s.volumeRatio();
        };
    }
    private static String rd(IndicatorSet s, Ind i) {
        if (s == null) return MetricReadiness.NOT_READY.name();
        MetricReadiness r = switch (i) {
            case SMA20 -> s.sma20Readiness(); case SMA50 -> s.sma50Readiness(); case RSI14 -> s.rsi14Readiness();
            case EMA9 -> s.ema9Readiness();   case EMA21 -> s.ema21Readiness(); case VOL -> s.volumeRatioReadiness();
        };
        return r == null ? MetricReadiness.NOT_READY.name() : r.name();
    }

    private static String firstError(List<String> errors) {
        return errors == null || errors.isEmpty() ? "unknown" : errors.get(0);
    }
}
