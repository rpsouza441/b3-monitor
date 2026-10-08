package dev.b3monitor.domain.analytics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract.*;
import dev.b3monitor.domain.auth.AssetCatalog;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Fail-closed validator for an {@link AnalyticsSnapshotContract} v1 document (FIN-01/FIN-03). It parses the
 * raw bytes DEFENSIVELY and refuses anything that is not an exact, bounded, in-contract snapshot:
 * <ul>
 *   <li>unknown / missing schema version;</li>
 *   <li>oversize document / too many records / overlong strings;</li>
 *   <li>a ticker outside the trusted {@link AssetCatalog};</li>
 *   <li>an unreasonable future {@code marketAsOf} / record {@code asOf};</li>
 *   <li>a checksum that does not match the canonical records encoding;</li>
 *   <li>any private-portfolio key (XIRR, holdings, quantity, cash flow, cost basis, broker id) — FIN-04;</li>
 *   <li>scripts/expressions/URLs/filesystem paths/archive-or-executable content embedded as strings;</li>
 *   <li>malformed required fields / wrong JSON types.</li>
 * </ul>
 * It computes the checksum over a CANONICAL encoding of the records (stable key order, normalized numbers)
 * so the same logical content always hashes identically — the basis for idempotency and the preview→commit
 * binding token. The validator is pure: it reads nothing, writes nothing, and never calls out.
 */
public class AnalyticsSnapshotValidator {

    private final AssetCatalog catalog;
    private final Clock clock;
    private final ObjectMapper mapper;

    /** Keys that must NEVER appear anywhere in the document — private-portfolio data (FIN-04). */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "xirr", "holdings", "holding", "quantity", "quantities", "cashflow", "cashflows",
            "cash_flow", "cash_flows", "costbasis", "cost_basis", "position", "positions",
            "broker", "brokerid", "broker_id", "account", "accountid", "account_id", "pnl",
            "portfolio", "lots", "lot", "trades", "trade");

    /** Reject strings that look like scripts, expressions, URLs, paths or archive/executable content. */
    private static final List<String> DANGEROUS_SUBSTRINGS = List.of(
            "<script", "javascript:", "://", "file:", "\\\\", "${", "#{", "<%", "%>",
            "..\\", "../", "\u0000");

    public record Result(boolean valid, ConsumerStatus status, String canonicalChecksum,
                         Snapshot snapshot, List<String> errors) {
        public static Result ok(Snapshot s, String checksum) {
            return new Result(true, ConsumerStatus.CONSUMER_VERIFIED_SYNTHETIC, checksum, s, List.of());
        }
        public static Result fail(List<String> errors) {
            return new Result(false, ConsumerStatus.REJECTED, null, null, List.copyOf(errors));
        }
        public static Result fail(String error) { return fail(List.of(error)); }
    }

    public AnalyticsSnapshotValidator(AssetCatalog catalog, Clock clock, ObjectMapper mapper) {
        this.catalog = catalog;
        this.clock = clock;
        this.mapper = mapper;
    }

    /** Validate raw document bytes. Returns a fail-closed {@link Result}; never throws on bad input. */
    public Result validate(byte[] raw) {
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.length == 0) return Result.fail("empty document");
        if (raw.length > AnalyticsSnapshotContract.MAX_FILE_BYTES)
            return Result.fail("document exceeds " + AnalyticsSnapshotContract.MAX_FILE_BYTES + " bytes");

        JsonNode root;
        try {
            root = mapper.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Result.fail("malformed JSON: " + safe(e.getMessage()));
        }
        if (root == null || !root.isObject()) return Result.fail("root must be a JSON object");

        // Forbidden-key + dangerous-content scan over the WHOLE tree (defense in depth vs smuggling).
        scanTree(root, errors);
        if (!errors.isEmpty()) return Result.fail(errors);

        // Schema version first — unknown version fails closed before anything else is trusted.
        String schema = text(root, "schemaVersion");
        if (!AnalyticsSnapshotContract.SCHEMA_V1.equals(schema))
            return Result.fail("unsupported schemaVersion: " + safe(schema));

        String snapshotId = requireBoundedString(root, "snapshotId", errors);
        String producer = requireBoundedString(root, "producer", errors);
        String producerVersion = requireBoundedString(root, "producerVersion", errors);
        String timezone = optionalBoundedString(root, "timezone", errors);
        String sourceId = optionalBoundedString(root, "sourceId", errors);
        String declaredChecksum = requireBoundedString(root, "checksum", errors);
        if (declaredChecksum != null && !declaredChecksum.matches("[0-9a-f]{" + AnalyticsSnapshotContract.CHECKSUM_HEX_LEN + "}"))
            errors.add("checksum must be " + AnalyticsSnapshotContract.CHECKSUM_HEX_LEN + "-char lowercase hex");

        Instant generatedAt = parseInstant(root, "generatedAt", errors);
        LocalDate marketAsOf = parseDate(root, "marketAsOf", errors);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (marketAsOf != null && marketAsOf.isAfter(today))
            errors.add("marketAsOf " + marketAsOf + " is in the future (> " + today + ")");

        JsonNode recs = root.get("records");
        if (recs == null || !recs.isArray()) {
            errors.add("records must be a JSON array");
            return Result.fail(errors);
        }
        if (recs.size() == 0) errors.add("records must not be empty");
        if (recs.size() > AnalyticsSnapshotContract.MAX_RECORDS)
            errors.add("records exceed " + AnalyticsSnapshotContract.MAX_RECORDS);

        Set<String> tickers = new HashSet<>(catalog.tickers());
        List<AnalyticsSnapshotContract.Record> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < recs.size() && i < AnalyticsSnapshotContract.MAX_RECORDS; i++) {
            JsonNode r = recs.get(i);
            String where = "records[" + i + "]";
            if (!r.isObject()) { errors.add(where + " must be an object"); continue; }
            String ticker = text(r, "ticker");
            if (ticker == null || !tickers.contains(ticker))
                errors.add(where + ".ticker not in trusted catalog: " + safe(ticker));
            if (ticker != null && !seen.add(ticker))
                errors.add(where + ".ticker duplicated in snapshot: " + safe(ticker));
            LocalDate asOf = parseDate(r, "asOf", errors);
            if (asOf != null && asOf.isAfter(today))
                errors.add(where + ".asOf " + asOf + " is in the future");
            records.add(new AnalyticsSnapshotContract.Record(ticker, asOf, parseIndicators(r.get("indicators"), where, errors),
                    parseContext(r.get("context"), where, errors),
                    optionalBoundedText(r, "quality"), optionalBoundedText(r, "status")));
        }
        if (!errors.isEmpty()) return Result.fail(errors);

        // Canonical checksum over the records (not the whole envelope — the envelope carries the checksum).
        String canonical = canonicalChecksum(records);
        if (declaredChecksum != null && !declaredChecksum.equals(canonical))
            return Result.fail("checksum mismatch: declared " + declaredChecksum + " but canonical " + canonical);

        Snapshot snap = new Snapshot(schema, snapshotId, producer, producerVersion, generatedAt,
                marketAsOf, timezone, sourceId, canonical, records);
        return Result.ok(snap, canonical);
    }

    /** SHA-256 (lowercase hex) over a stable canonical encoding of the records. Public so the importer can
     *  recompute it to bind a preview token and enforce idempotency / conflict detection. */
    public String canonicalChecksum(List<AnalyticsSnapshotContract.Record> records) {
        StringBuilder sb = new StringBuilder();
        List<AnalyticsSnapshotContract.Record> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(AnalyticsSnapshotContract.Record::ticker, Comparator.nullsLast(Comparator.naturalOrder())));
        for (AnalyticsSnapshotContract.Record r : sorted) {
            sb.append(r.ticker()).append('|').append(r.asOf()).append('|');
            IndicatorSet s = r.indicators();
            if (s != null) {
                sb.append(num(s.sma20())).append(',').append(s.sma20Readiness()).append(';')
                  .append(num(s.sma50())).append(',').append(s.sma50Readiness()).append(';')
                  .append(num(s.rsi14())).append(',').append(s.rsi14Readiness()).append(';')
                  .append(num(s.ema9())).append(',').append(s.ema9Readiness()).append(';')
                  .append(num(s.ema21())).append(',').append(s.ema21Readiness()).append(';')
                  .append(num(s.volumeRatio())).append(',').append(s.volumeRatioReadiness());
            }
            sb.append('|');
            if (r.context() != null) {
                List<ContextMetric> cs = new ArrayList<>(r.context());
                cs.sort(Comparator.comparing(ContextMetric::name, Comparator.nullsLast(Comparator.naturalOrder())));
                for (ContextMetric c : cs) {
                    sb.append(c.name()).append('=').append(num(c.value())).append(':')
                      .append(c.units()).append(':').append(c.readiness()).append('#');
                }
            }
            sb.append(r.status()).append('\n');
        }
        return sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder h = new StringBuilder(d.length * 2);
            for (byte b : d) h.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return h.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ---- parsing helpers (defensive; accumulate errors, never throw) ----

    private IndicatorSet parseIndicators(JsonNode n, String where, List<String> errors) {
        if (n == null || n.isNull()) return null;
        if (!n.isObject()) { errors.add(where + ".indicators must be an object"); return null; }
        return new IndicatorSet(
                dec(n, "sma20", where, errors), readiness(n, "sma20Readiness", where, errors),
                dec(n, "sma50", where, errors), readiness(n, "sma50Readiness", where, errors),
                dec(n, "rsi14", where, errors), readiness(n, "rsi14Readiness", where, errors),
                dec(n, "ema9", where, errors),  readiness(n, "ema9Readiness", where, errors),
                dec(n, "ema21", where, errors), readiness(n, "ema21Readiness", where, errors),
                dec(n, "volumeRatio", where, errors), readiness(n, "volumeRatioReadiness", where, errors));
    }

    private List<ContextMetric> parseContext(JsonNode n, String where, List<String> errors) {
        if (n == null || n.isNull()) return List.of();
        if (!n.isArray()) { errors.add(where + ".context must be an array"); return List.of(); }
        List<ContextMetric> out = new ArrayList<>();
        for (int i = 0; i < n.size(); i++) {
            JsonNode c = n.get(i);
            if (!c.isObject()) { errors.add(where + ".context[" + i + "] must be an object"); continue; }
            out.add(new ContextMetric(bounded(text(c, "name"), where + ".context.name", errors),
                    dec(c, "value", where, errors), bounded(text(c, "units"), where + ".context.units", errors),
                    readiness(c, "readiness", where, errors), bounded(text(c, "quality"), where + ".context.quality", errors)));
        }
        return out;
    }

    private BigDecimal dec(JsonNode n, String f, String where, List<String> errors) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        if (!v.isNumber() && !(v.isTextual() && v.asText().matches("-?\\d+(\\.\\d+)?"))) {
            errors.add(where + "." + f + " must be a number"); return null;
        }
        try { return new BigDecimal(v.asText()); }
        catch (NumberFormatException e) { errors.add(where + "." + f + " not a decimal"); return null; }
    }

    private MetricReadiness readiness(JsonNode n, String f, String where, List<String> errors) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return MetricReadiness.NOT_READY;
        try { return MetricReadiness.valueOf(v.asText()); }
        catch (IllegalArgumentException e) { errors.add(where + "." + f + " invalid readiness: " + safe(v.asText())); return MetricReadiness.NOT_READY; }
    }

    private void scanTree(JsonNode node, List<String> errors) {
        if (node.isObject()) {
            for (var e : node.properties()) {
                if (FORBIDDEN_KEYS.contains(e.getKey().toLowerCase(Locale.ROOT)))
                    errors.add("forbidden private-portfolio key present: " + e.getKey());
                scanTree(e.getValue(), errors);
            }
        } else if (node.isArray()) {
            node.forEach(c -> scanTree(c, errors));
        } else if (node.isTextual()) {
            String s = node.asText();
            if (s.length() > AnalyticsSnapshotContract.MAX_STRING)
                errors.add("string field exceeds " + AnalyticsSnapshotContract.MAX_STRING + " chars");
            String low = s.toLowerCase(Locale.ROOT);
            for (String bad : DANGEROUS_SUBSTRINGS)
                if (low.contains(bad)) { errors.add("string contains disallowed content: " + bad); break; }
        }
    }

    private String requireBoundedString(JsonNode n, String f, List<String> errors) {
        String v = text(n, f);
        if (v == null || v.isBlank()) { errors.add("missing required field: " + f); return null; }
        return bounded(v, f, errors);
    }
    private String optionalBoundedString(JsonNode n, String f, List<String> errors) {
        String v = text(n, f);
        return v == null ? null : bounded(v, f, errors);
    }
    private String optionalBoundedText(JsonNode n, String f) {
        String v = text(n, f);
        return v == null ? null : (v.length() > AnalyticsSnapshotContract.MAX_STRING ? v.substring(0, AnalyticsSnapshotContract.MAX_STRING) : v);
    }
    private String bounded(String v, String f, List<String> errors) {
        if (v != null && v.length() > AnalyticsSnapshotContract.MAX_STRING) { errors.add(f + " exceeds " + AnalyticsSnapshotContract.MAX_STRING + " chars"); }
        return v;
    }
    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() || !v.isValueNode() ? null : v.asText();
    }
    private Instant parseInstant(JsonNode n, String f, List<String> errors) {
        String v = text(n, f);
        if (v == null) { errors.add("missing required field: " + f); return null; }
        try { return Instant.parse(v); } catch (Exception e) { errors.add(f + " must be ISO-8601 instant"); return null; }
    }
    private LocalDate parseDate(JsonNode n, String f, List<String> errors) {
        String v = text(n, f);
        if (v == null) { errors.add("missing required field: " + f); return null; }
        try { return LocalDate.parse(v); } catch (Exception e) { errors.add(f + " must be ISO-8601 date"); return null; }
    }
    private static String num(BigDecimal b) { return b == null ? "" : b.stripTrailingZeros().toPlainString(); }
    private static String safe(String s) {
        if (s == null) return "null";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}
