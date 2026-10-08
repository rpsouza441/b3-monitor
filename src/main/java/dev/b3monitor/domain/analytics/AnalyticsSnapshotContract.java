package dev.b3monitor.domain.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * ANALYTICS SNAPSHOT CONTRACT v1 — the b3-monitor CONSUMER-side wire contract for a daily analytics /
 * daily-indicator snapshot produced OUT OF BAND by a future authorized {@code projecao-carteira} exporter
 * (FIN-01 "versioned tested Java public-asset snapshot consumer"; FIN-06 is the producer, NOT this).
 *
 * <p>This defines ONLY what the consumer accepts; it invents no indicator semantics beyond the ones the
 * requirements already name:
 * <ul>
 *   <li>IND-01 SMA20 / SMA50, IND-02 RSI14 (Wilder), IND-04 EMA9 / EMA21, IND-05 abnormal-volume ratio
 *       {@code V[t]/mean(V[t-20..t-1])};</li>
 *   <li>FIN-02 contextual metrics (Graham fair value, FII P/VP) carried as read-only context, each with its
 *       OWN readiness/quality — never promoted into a rule (FIN-04).</li>
 * </ul>
 *
 * <p>Hard privacy boundary (FIN-04): this contract has NO field for XIRR, holdings, quantities, cash flows,
 * cost basis, broker identifiers or any private-portfolio datum. Those are rejected by their very absence —
 * the typed record cannot carry them — and the validator additionally refuses a payload that smuggles them
 * as unknown keys. Every value is a public, per-asset analytic.
 *
 * <p>A value is a SNAPSHOT of a producer's output, not a live computation. Importing it is CONTEXT only:
 * it never authorizes an asset, never enables a worker, never changes a rule decision. Status after a
 * synthetic import is {@code CONSUMER_VERIFIED_SYNTHETIC} — never "real Python integration".
 */
public final class AnalyticsSnapshotContract {

    private AnalyticsSnapshotContract() {}

    /** The only schema version this consumer understands. Anything else fails closed. */
    public static final String SCHEMA_V1 = "b3-monitor.analytics-snapshot/1";

    // ---- bounds (fail-closed oversize guards) ----
    public static final int MAX_FILE_BYTES   = 512 * 1024;   // 512 KiB whole-document cap
    public static final int MAX_RECORDS      = 500;          // >> 23 catalog assets, but still bounded
    public static final int MAX_STRING       = 200;          // any single string field
    public static final int CHECKSUM_HEX_LEN = 64;           // SHA-256 lowercase hex

    /** Per-metric readiness, mirroring IND-03 / FIN-02 (readiness is never silently "ready"). */
    public enum MetricReadiness { READY, NOT_READY, PARTIAL, NOT_SUPPORTED }

    /** The envelope. {@code generatedAt} is producer wall-clock; {@code marketAsOf} is the trading date the
     *  analytics describe (completed daily bars only — HIS-02). {@code checksum} is the SHA-256 (lowercase
     *  hex) of the canonical records encoding, bound into provenance. */
    public record Snapshot(
            String schemaVersion,
            String snapshotId,
            String producer,
            String producerVersion,
            Instant generatedAt,
            LocalDate marketAsOf,
            String timezone,
            String sourceId,          // provenance / source identifier (e.g. COTAHIST RAW annual)
            String checksum,          // SHA-256 hex over the canonical records encoding
            List<Record> records) {}

    /** One per-asset analytics record. Every indicator is OPTIONAL + carries its own readiness, so a
     *  warmup/insufficient-history asset is explicitly NOT_READY rather than fabricated (IND-03). */
    public record Record(
            String ticker,
            LocalDate asOf,                 // the completed-bar date this record describes
            IndicatorSet indicators,
            List<ContextMetric> context,    // FIN-02 contextual metrics (Graham, FII P/VP, …) — read-only
            String quality,                 // free-form bounded quality note (e.g. "RAW close-only")
            String status) {}               // e.g. OK / PARTIAL / UNKNOWN

    /** Typed daily indicators (IND-01/02/04/05). All nullable; each paired with a readiness flag. */
    public record IndicatorSet(
            BigDecimal sma20,  MetricReadiness sma20Readiness,
            BigDecimal sma50,  MetricReadiness sma50Readiness,
            BigDecimal rsi14,  MetricReadiness rsi14Readiness,
            BigDecimal ema9,   MetricReadiness ema9Readiness,
            BigDecimal ema21,  MetricReadiness ema21Readiness,
            BigDecimal volumeRatio, MetricReadiness volumeRatioReadiness) {}

    /** A single FIN-02 contextual metric: a named public analytic with its own units/readiness/quality. */
    public record ContextMetric(
            String name,         // e.g. "graham_fair_value", "fii_pvp"
            BigDecimal value,
            String units,        // e.g. "BRL", "ratio"
            MetricReadiness readiness,
            String quality) {}

    /** The disposition of a validated snapshot — never "real integration". */
    public enum ConsumerStatus { CONSUMER_VERIFIED_SYNTHETIC, REJECTED }
}
