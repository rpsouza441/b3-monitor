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

    /** Strict v1: exactly these top-level fields are permitted; any other is rejected. */
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "schemaVersion", "snapshotId", "producer", "producerVersion", "generatedAt",
            "marketAsOf", "timezone", "sourceId", "checksum", "records");

    /** Strict v1: exact allowed keys at each nested level (cycle-18 item H). */
    private static final Set<String> ALLOWED_RECORD = Set.of(
            "ticker", "asOf", "indicators", "context", "quality", "status");
    private static final Set<String> ALLOWED_INDICATORS = Set.of(
            "sma20", "sma20Readiness", "sma50", "sma50Readiness", "rsi14", "rsi14Readiness",
            "ema9", "ema9Readiness", "ema21", "ema21Readiness", "volumeRatio", "volumeRatioReadiness");
    private static final Set<String> ALLOWED_CONTEXT_METRIC = Set.of(
            "name", "value", "units", "readiness", "quality");
    /** Documented record status vocabulary. */
    private static final Set<String> ALLOWED_STATUS = Set.of("OK", "PARTIAL", "UNKNOWN");

    /** Max JSON nesting depth. Numeric values must fit the DB NUMERIC(24,12) column EXACTLY (cycle-19 P1-C):
     *  scale ∈ [0, 12] AND integer digits ≤ 12. A value needing 13+ integer digits (e.g. 1E12) is rejected
     *  BEFORE persistence — the old {@code abs ≤ 1E12} / precision≤24 pair accepted 1E12 (13 int digits),
     *  which overflows NUMERIC(24,12) on PostgreSQL. */
    private static final int MAX_DEPTH = 12;
    private static final int MAX_NUM_SCALE     = AnalyticsSnapshotContract.NUMERIC_SCALE;      // 12
    private static final int MAX_NUM_INT_DIGITS = AnalyticsSnapshotContract.NUMERIC_INT_DIGITS; // 12

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
            // STRICT parse: reject duplicate JSON keys (a duplicate key is ambiguous and a classic smuggling
            // vector) and non-finite numbers (NaN/Infinity). Floats are read as BigDecimal (not double) so a
            // full-precision decimal like 999999999999.999999999999 keeps every digit — reading it as a
            // double would silently round it (e.g. up to 1E12) and corrupt both the bound check and the
            // round-trip. The depth of the structure is bounded below.
            var reader = mapper.reader()
                    .with(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            root = reader.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Result.fail("malformed JSON: " + safe(e.getMessage()));
        }
        if (root == null || !root.isObject()) return Result.fail("root must be a JSON object");

        // Depth + forbidden-key + dangerous-content + non-finite scan over the WHOLE tree (defense in depth).
        scanTree(root, 0, errors);
        if (!errors.isEmpty()) return Result.fail(errors);

        // Strict v1: reject unknown top-level fields so a future/foreign field is never silently ignored.
        for (var e : root.properties()) {
            if (!ALLOWED_TOP_LEVEL.contains(e.getKey()))
                errors.add("unknown top-level field (strict v1): " + safe(e.getKey()));
        }
        if (!errors.isEmpty()) return Result.fail(errors);

        // Schema version first — unknown version fails closed before anything else is trusted.
        String schema = text(root, "schemaVersion");
        if (!AnalyticsSnapshotContract.SCHEMA_V1.equals(schema))
            return Result.fail("unsupported schemaVersion: " + safe(schema));

        String snapshotId = requireBoundedString(root, "snapshotId", errors);
        String producer = requireBoundedString(root, "producer", errors);
        String producerVersion = requireBoundedString(root, "producerVersion", errors);
        String timezone = optionalBoundedString(root, "timezone", errors);
        if (timezone != null && !timezone.isBlank()) {
            try { java.time.ZoneId.of(timezone); }
            catch (Exception e) { errors.add("timezone must be a valid IANA ZoneId or null: " + safe(timezone)); }
        }
        String sourceId = optionalBoundedString(root, "sourceId", errors);
        String declaredChecksum = requireBoundedString(root, "checksum", errors);
        if (declaredChecksum != null && !declaredChecksum.matches("[0-9a-f]{" + AnalyticsSnapshotContract.CHECKSUM_HEX_LEN + "}"))
            errors.add("checksum must be " + AnalyticsSnapshotContract.CHECKSUM_HEX_LEN + "-char lowercase hex");

        Instant generatedAt = parseInstant(root, "generatedAt", errors);
        LocalDate marketAsOf = parseDate(root, "marketAsOf", errors);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Instant nowSkew = clock.instant().plusSeconds(300);   // allow 5-min clock skew, no more
        if (generatedAt != null && generatedAt.isAfter(nowSkew))
            errors.add("generatedAt " + generatedAt + " is in the future");
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
            for (var e : r.properties())
                if (!ALLOWED_RECORD.contains(e.getKey()))
                    errors.add(where + " unknown field (strict v1): " + safe(e.getKey()));
            String ticker = text(r, "ticker");
            if (ticker == null || !tickers.contains(ticker))
                errors.add(where + ".ticker not in trusted catalog: " + safe(ticker));
            if (ticker != null && !seen.add(ticker))
                errors.add(where + ".ticker duplicated in snapshot: " + safe(ticker));
            LocalDate asOf = parseDate(r, "asOf", errors);
            if (asOf != null && asOf.isAfter(today))
                errors.add(where + ".asOf " + asOf + " is in the future");
            if (asOf != null && marketAsOf != null && asOf.isAfter(marketAsOf))
                errors.add(where + ".asOf " + asOf + " is after the snapshot marketAsOf " + marketAsOf
                        + " (marketAsOf is the upper bound)");
            String recStatus = optionalBoundedText(r, "status");
            if (recStatus != null && !recStatus.isBlank() && !ALLOWED_STATUS.contains(recStatus))
                errors.add(where + ".status must be one of " + ALLOWED_STATUS + " (got " + safe(recStatus) + ")");
            records.add(new AnalyticsSnapshotContract.Record(ticker, asOf, parseIndicators(r.get("indicators"), where, errors),
                    parseContext(r.get("context"), where, errors),
                    optionalBoundedText(r, "quality"), recStatus));
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

    /** SHA-256 (lowercase hex) over a stable, UNAMBIGUOUS canonical encoding of the records. Every
     *  semantic field is included (incl. record.quality and context-metric quality), and every value is
     *  LENGTH-PREFIXED (len:bytes) so no delimiter-like string can collide with the structure. Records are
     *  ticker-sorted; context metrics name-sorted. Public so the importer can recompute it for the
     *  producer-facing records checksum. (cycle-18 item I: fidelity + delimiter-collision fix.) */
    public String canonicalChecksum(List<AnalyticsSnapshotContract.Record> records) {
        StringBuilder sb = new StringBuilder();
        List<AnalyticsSnapshotContract.Record> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(AnalyticsSnapshotContract.Record::ticker, Comparator.nullsLast(Comparator.naturalOrder())));
        lp(sb, "records");
        lp(sb, Integer.toString(sorted.size()));
        for (AnalyticsSnapshotContract.Record r : sorted) {
            lp(sb, r.ticker());
            lp(sb, r.asOf() == null ? "" : r.asOf().toString());
            IndicatorSet s = r.indicators();
            lp(sb, s == null ? "" : "ind");
            if (s != null) {
                lpNum(sb, s.sma20()); lp(sb, rn(s.sma20Readiness()));
                lpNum(sb, s.sma50()); lp(sb, rn(s.sma50Readiness()));
                lpNum(sb, s.rsi14()); lp(sb, rn(s.rsi14Readiness()));
                lpNum(sb, s.ema9());  lp(sb, rn(s.ema9Readiness()));
                lpNum(sb, s.ema21()); lp(sb, rn(s.ema21Readiness()));
                lpNum(sb, s.volumeRatio()); lp(sb, rn(s.volumeRatioReadiness()));
            }
            List<ContextMetric> cs = r.context() == null ? List.of() : new ArrayList<>(r.context());
            cs.sort(Comparator.comparing(ContextMetric::name, Comparator.nullsLast(Comparator.naturalOrder())));
            lp(sb, Integer.toString(cs.size()));
            for (ContextMetric c : cs) {
                lp(sb, nz(c.name()));
                lpNum(sb, c.value());
                lp(sb, nz(c.units()));
                lp(sb, c.readiness() == null ? "" : c.readiness().name());
                lp(sb, nz(c.quality()));           // cycle-18 fidelity fix: context quality IS in the digest
            }
            lp(sb, nz(r.quality()));                // cycle-18 fidelity fix: record quality IS in the digest
            lp(sb, nz(r.status()));
        }
        return sha256Hex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** length-prefixed append: "<utf8-byte-len>:<value>" — unambiguous, collision-free framing. */
    private static void lp(StringBuilder sb, String v) {
        String s = v == null ? "" : v;
        sb.append(s.getBytes(StandardCharsets.UTF_8).length).append(':').append(s).append('\n');
    }
    private static void lpNum(StringBuilder sb, BigDecimal b) { lp(sb, b == null ? "" : b.stripTrailingZeros().toPlainString()); }
    private static String rn(MetricReadiness r) { return r == null ? "" : r.name(); }
    private static String nz(String s) { return s == null ? "" : s; }

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
        for (var e : n.properties())
            if (!ALLOWED_INDICATORS.contains(e.getKey()))
                errors.add(where + ".indicators unknown field (strict v1): " + safe(e.getKey()));
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
        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < n.size(); i++) {
            JsonNode c = n.get(i);
            String cw = where + ".context[" + i + "]";
            if (!c.isObject()) { errors.add(cw + " must be an object"); continue; }
            for (var e : c.properties())
                if (!ALLOWED_CONTEXT_METRIC.contains(e.getKey()))
                    errors.add(cw + " unknown field (strict v1): " + safe(e.getKey()));
            String name = text(c, "name");
            if (name == null || name.isBlank()) errors.add(cw + ".name must be nonblank");
            else if (!seenNames.add(name)) errors.add(cw + ".name duplicated in context: " + safe(name));
            out.add(new ContextMetric(bounded(name, cw + ".name", errors),
                    dec(c, "value", where, errors), bounded(text(c, "units"), cw + ".units", errors),
                    readiness(c, "readiness", where, errors), bounded(text(c, "quality"), cw + ".quality", errors)));
        }
        return out;
    }

    private BigDecimal dec(JsonNode n, String f, String where, List<String> errors) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return null;
        if (v.isNumber() && isNonFinite(v)) { errors.add(where + "." + f + " must be finite"); return null; }
        if (!v.isNumber() && !(v.isTextual() && v.asText().matches("-?\\d+(\\.\\d+)?"))) {
            errors.add(where + "." + f + " must be a number"); return null;
        }
        BigDecimal d;
        try { d = new BigDecimal(v.asText()); }
        catch (NumberFormatException e) { errors.add(where + "." + f + " not a decimal"); return null; }
        // Exact NUMERIC(24,12) fit (cycle-19 P1-C) — no silent rounding, no overflow. A fractional scale
        // beyond 12 would be rounded on persistence; 13+ integer digits (e.g. 1E12, stored scale -12) would
        // overflow. Compute integer digits defensively from the UNSCALED precision and scale: intDigits =
        // precision - scale (negative scale ⇒ precision + |scale|, which is what 1E12 needs = 13).
        int scale = d.scale();
        int intDigits = d.precision() - scale;   // for 1E12: precision 1 - scale(-12) = 13  → rejected
        if (scale > MAX_NUM_SCALE)
            errors.add(where + "." + f + " scale " + scale + " exceeds " + MAX_NUM_SCALE
                    + " (NUMERIC(" + AnalyticsSnapshotContract.NUMERIC_PRECISION + "," + MAX_NUM_SCALE + "))");
        else if (scale < 0 || intDigits > MAX_NUM_INT_DIGITS)
            // scale<0 means the value is only representable by shifting the point right (e.g. 1E12); such a
            // value cannot be stored in a fixed-scale NUMERIC without inventing precision — reject it.
            errors.add(where + "." + f + " magnitude out of range: " + intDigits + " integer digits exceed "
                    + MAX_NUM_INT_DIGITS + " (NUMERIC(" + AnalyticsSnapshotContract.NUMERIC_PRECISION + ","
                    + MAX_NUM_SCALE + ") max magnitude < 1e" + MAX_NUM_INT_DIGITS + ")");
        return d;
    }

    private MetricReadiness readiness(JsonNode n, String f, String where, List<String> errors) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return MetricReadiness.NOT_READY;
        try { return MetricReadiness.valueOf(v.asText()); }
        catch (IllegalArgumentException e) { errors.add(where + "." + f + " invalid readiness: " + safe(v.asText())); return MetricReadiness.NOT_READY; }
    }

    private void scanTree(JsonNode node, int depth, List<String> errors) {
        if (depth > MAX_DEPTH) { errors.add("JSON nesting exceeds max depth " + MAX_DEPTH); return; }
        if (node.isObject()) {
            for (var e : node.properties()) {
                if (FORBIDDEN_KEYS.contains(e.getKey().toLowerCase(Locale.ROOT)))
                    errors.add("forbidden private-portfolio key present: " + e.getKey());
                scanTree(e.getValue(), depth + 1, errors);
            }
        } else if (node.isArray()) {
            node.forEach(c -> scanTree(c, depth + 1, errors));
        } else if (node.isNumber()) {
            if (isNonFinite(node))   // a non-finite number is never a valid metric (parse also rejects NaN/Inf tokens)
                errors.add("non-finite numeric value (NaN/Infinity) is not allowed");
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

    /** True for a non-finite floating node (NaN/±Infinity). Jackson's strict parse already rejects the
     *  NaN/Infinity LITERAL tokens; this is defense-in-depth for a double node that somehow decoded to one. */
    private static boolean isNonFinite(JsonNode v) {
        if (v != null && (v.isDouble() || v.isFloat())) {
            double d = v.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d);
        }
        return false;
    }
    private static String safe(String s) {
        if (s == null) return "null";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}
