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
                                int recordCount, String canonicalChecksum, String token,
                                String disposition, List<String> errors) {}

    public record CommitResult(String disposition, String snapshotId, String checksum, int recordCount) {
        // disposition ∈ IMPORTED | IDEMPOTENT_NOOP | REJECTED_CONFLICT | REJECTED_VALIDATION | REJECTED_TOKEN_<reason>
    }

    private final AnalyticsSnapshotValidator validator;
    private final AnalyticsSnapshotRepository snapshots;
    private final AdminAuditService audit;
    private final AnalyticsPreviewToken tokens;
    private final Clock clock;

    public AnalyticsImportService(AnalyticsSnapshotValidator validator, AnalyticsSnapshotRepository snapshots,
                                  AdminAuditService audit, AnalyticsPreviewToken tokens, Clock clock) {
        this.validator = validator;
        this.snapshots = snapshots;
        this.audit = audit;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Validate-only; persists nothing. Issues an HMAC token bound to {schema, snapshotId, checksum, actor}. */
    @Transactional(readOnly = true)
    public PreviewResult preview(byte[] raw) {
        Result v = validator.validate(raw);
        if (!v.valid()) {
            return new PreviewResult(false, null, null, null, null, null, 0, null, null, "REJECTED", v.errors());
        }
        Snapshot s = v.snapshot();
        String token = tokens.issue(s.schemaVersion(), s.snapshotId(), v.canonicalChecksum(),
                AdminAuditService.currentActor());
        return new PreviewResult(true, s.schemaVersion(), s.snapshotId(), s.producer(), s.producerVersion(),
                String.valueOf(s.marketAsOf()), s.records().size(), v.canonicalChecksum(), token,
                "CONSUMER_VERIFIED_SYNTHETIC", List.of());
    }

    /**
     * Re-validate and persist. {@code token} is the HMAC preview token; it is verified against the LIVE
     * commit context — purpose, schemaVersion, snapshotId, canonical checksum AND actor must all match, and
     * it must not be expired — so a token cannot be replayed across actor/content/snapshot/schema. A changed
     * payload moves the checksum, so its MAC no longer binds. Content-mismatch, actor-mismatch, expiry,
     * wrong-purpose and tamper all reject fail-closed.
     */
    @Transactional
    public CommitResult commit(byte[] raw, String token) {
        Result v = validator.validate(raw);
        if (!v.valid()) {
            audit.recordRejection(Action.IMPORT_SNAPSHOT, null, null, Outcome.REJECTED_VALIDATION,
                    "snapshot import rejected: " + firstError(v.errors()));
            return new CommitResult("REJECTED_VALIDATION", null, null, 0);
        }
        Snapshot s = v.snapshot();
        String checksum = v.canonicalChecksum();
        String actor = AdminAuditService.currentActor();

        // Cryptographic preview→commit binding across content AND authorization context (actor/schema/
        // snapshot/purpose/expiry). The token NEVER carries a secret or raw payload — only the digest.
        var verdict = tokens.verify(token, s.schemaVersion(), s.snapshotId(), checksum, actor);
        if (!verdict.valid()) {
            audit.recordRejection(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, Outcome.REJECTED_VALIDATION,
                    "snapshot import rejected: preview token " + verdict.rejection() + " for " + s.snapshotId());
            return new CommitResult("REJECTED_TOKEN_" + verdict.rejection(), s.snapshotId(), checksum, 0);
        }

        Optional<AnalyticsSnapshotEntity> existing = snapshots.findBySnapshotId(s.snapshotId());
        if (existing.isPresent()) {
            if (existing.get().getChecksum().equals(checksum)) {
                audit.record(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, null, Outcome.NO_OP,
                        "snapshot already imported (idempotent): " + s.snapshotId());
                return new CommitResult("IDEMPOTENT_NOOP", s.snapshotId(), checksum, existing.get().getRecordCount());
            }
            audit.recordRejection(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, Outcome.REJECTED_CONFLICT,
                    "snapshot id reused with a different checksum: " + s.snapshotId());
            return new CommitResult("REJECTED_CONFLICT", s.snapshotId(), checksum, 0);
        }

        AnalyticsSnapshotEntity ent = new AnalyticsSnapshotEntity(
                s.snapshotId(), s.schemaVersion(), s.producer(), s.producerVersion(), s.generatedAt(),
                s.marketAsOf(), s.timezone(), s.sourceId(), checksum, s.records().size(),
                clock.instant(), actor, ConsumerStatus.CONSUMER_VERIFIED_SYNTHETIC.name());
        for (AnalyticsSnapshotContract.Record r : s.records()) {
            IndicatorSet ind = r.indicators();
            ent.addRow(new AnalyticsContextEntity(r.ticker(), r.asOf(),
                    v(ind, Ind.SMA20), rd(ind, Ind.SMA20), v(ind, Ind.SMA50), rd(ind, Ind.SMA50),
                    v(ind, Ind.RSI14), rd(ind, Ind.RSI14), v(ind, Ind.EMA9), rd(ind, Ind.EMA9),
                    v(ind, Ind.EMA21), rd(ind, Ind.EMA21), v(ind, Ind.VOL), rd(ind, Ind.VOL),
                    encodeContext(r.context()), r.quality(), r.status()));
        }
        snapshots.save(ent);
        audit.record(Action.IMPORT_SNAPSHOT, s.snapshotId(), null, (long) s.records().size(), Outcome.SUCCESS,
                "imported snapshot " + s.snapshotId() + " (" + s.records().size() + " records, checksum "
                        + checksum.substring(0, 12) + "…)");
        return new CommitResult("IMPORTED", s.snapshotId(), checksum, s.records().size());
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

    /** Bounded compact encoding of FIN-02 context metrics for storage/display. */
    private static String encodeContext(List<ContextMetric> cs) {
        if (cs == null || cs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ContextMetric c : cs) {
            if (sb.length() > 1800) break;   // bounded
            sb.append(c.name()).append('=').append(c.value() == null ? "" : c.value().toPlainString())
              .append(':').append(c.units() == null ? "" : c.units())
              .append(':').append(c.readiness()).append('#');
        }
        String out = sb.toString();
        return out.length() > 2000 ? out.substring(0, 2000) : out;
    }

    private static String firstError(List<String> errors) {
        return errors == null || errors.isEmpty() ? "unknown" : errors.get(0);
    }
}
