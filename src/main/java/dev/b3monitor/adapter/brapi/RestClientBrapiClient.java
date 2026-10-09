package dev.b3monitor.adapter.brapi;

import tools.jackson.databind.JsonNode;
import dev.b3monitor.domain.quote.Quote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Brapi HTTP adapter implementing the AUDITED v2 contract (docs/references/brapi/SCHEMA.md,
 * docs/contracts/CONTRACTS.md):
 * <ul>
 *   <li>route {@code GET /api/v2/stocks/quote?symbols={oneTicker}} — one ticker per request;</li>
 *   <li>envelope {@code results[0]} with {@code requestedSymbol}/{@code symbol}/{@code changed}
 *       and a nested {@code data} object carrying the price fields;</li>
 *   <li>{@code data.regularMarketTime} is ISO-8601 UTC (NOT epoch seconds);</li>
 *   <li>staleness is the {@code x-brapi-stale} <em>response header</em> (NOT a body field);</li>
 *   <li>previousClose DATE/basis is NOT exposed by Brapi → {@code previousCloseDate} stays {@code null}
 *       (never synthesized from the quote time).</li>
 * </ul>
 * No field is defaulted: a missing symbol/currency/price/time fails the parse rather than being
 * invented. The token is read from config, attached as a bearer header, and never logged.
 */
@Component
public class RestClientBrapiClient implements BrapiClient {

    private final RestClient http;
    private final String token;
    private final Clock clock;

    public RestClientBrapiClient(
            @Value("${b3monitor.brapi.base-url:https://brapi.dev/api}") String baseUrl,
            @Value("${b3monitor.brapi.token:}") String token,
            @Value("${b3monitor.brapi.timeout-ms:8000}") int timeoutMs,
            Clock clock) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeoutMs);
        rf.setReadTimeout(timeoutMs);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
        this.token = token;
        this.clock = clock;
    }

    @Override
    public FetchResult fetch(String ticker) throws BrapiException {
        // One ticker per request — reject any comma/list/whitespace batch outright.
        if (ticker == null || ticker.isBlank()
                || ticker.indexOf(',') >= 0 || ticker.chars().anyMatch(Character::isWhitespace)) {
            throw new BrapiException("brapi one-ticker-per-request violated: '" + ticker + "'");
        }
        final JsonNode[] holder = new JsonNode[1];
        final boolean[] staleHeader = new boolean[1];
        final QuotaSignal[] signal = { QuotaSignal.none() };

        try {
            http.get()
                .uri(uri -> uri.path("/v2/stocks/quote").queryParam("symbols", ticker).build())
                .headers(h -> { if (token != null && !token.isBlank()) h.setBearerAuth(token); })
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    if (status == 429) {
                        Long retry = parseLong(res.getHeaders().getFirst("Retry-After"));
                        Long resetDelta = parseLong(firstHeader(res.getHeaders(), "ratelimit-reset", "x-ratelimit-reset"));
                        Integer remaining = toInt(parseLong(firstHeader(res.getHeaders(), "ratelimit-remaining", "x-ratelimit-remaining")));
                        Integer limit = toInt(parseLong(firstHeader(res.getHeaders(), "ratelimit-limit", "x-ratelimit-limit")));
                        String window = firstHeader(res.getHeaders(), "x-ratelimit-window", "ratelimit-window");
                        Instant serverDate = parseHttpDate(res.getHeaders().getFirst("Date"));
                        String reqId = firstHeader(res.getHeaders(), "x-request-id", "x-amzn-requestid");
                        QuotaSignal sig = new QuotaSignal(retry, resetDelta, remaining, limit, window, serverDate, reqId, true);
                        throw new UncheckedBrapi(BrapiException.rateLimited(
                                "brapi rate limited (429) for " + ticker, sig));
                    }
                    if (status == 401 || status == 403) {
                        throw new UncheckedBrapi(new BrapiException("brapi auth/plan error (" + status + ") for " + ticker));
                    }
                    if (!res.getStatusCode().is2xxSuccessful()) {
                        throw new UncheckedBrapi(new BrapiException("brapi HTTP " + status + " for " + ticker));
                    }
                    // x-brapi-stale header (value "1" means stale); absent => not stale
                    staleHeader[0] = "1".equals(res.getHeaders().getFirst("x-brapi-stale"));
                    // Capture full quota telemetry from the SUCCESS response (cycle-6 review P1):
                    // reset DELTA, remaining, LIMIT, WINDOW, server Date and request id — so the quota
                    // layer can decide a billing-cycle by WINDOW+LIMIT, not by the delta's duration.
                    Long resetDelta = parseLong(firstHeader(res.getHeaders(), "ratelimit-reset", "x-ratelimit-reset"));
                    Integer remaining = toInt(parseLong(firstHeader(res.getHeaders(), "ratelimit-remaining", "x-ratelimit-remaining")));
                    Integer limit = toInt(parseLong(firstHeader(res.getHeaders(), "ratelimit-limit", "x-ratelimit-limit")));
                    String window = firstHeader(res.getHeaders(), "x-ratelimit-window", "ratelimit-window");
                    Instant serverDate = parseHttpDate(res.getHeaders().getFirst("Date"));
                    String reqId = firstHeader(res.getHeaders(), "x-request-id", "x-amzn-requestid");
                    signal[0] = new QuotaSignal(null, resetDelta, remaining, limit, window, serverDate, reqId, false);
                    holder[0] = res.bodyTo(JsonNode.class);
                    return null;
                });
        } catch (UncheckedBrapi e) {
            throw e.checked;
        } catch (Exception e) {
            throw new BrapiException("brapi request failed for " + ticker + ": "
                    + e.getClass().getSimpleName(), e);
        }

        JsonNode root = holder[0];
        if (root == null || root.path("error").asBoolean(false)) {
            String code = root == null ? "null-body" : root.path("code").asText("unknown");
            throw new BrapiException("brapi error body for " + ticker + ": " + code);
        }
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            throw new BrapiException("brapi empty results for " + ticker);
        }
        JsonNode item = results.get(0);

        // Full identity: requestedSymbol must be present and match; symbol is the data identity.
        String requested = requireText(item, "requestedSymbol", ticker);
        String returned = requireText(item, "symbol", ticker);
        boolean changed = item.path("changed").asBoolean(false);

        JsonNode data = item.get("data");
        if (data == null || data.isNull()) {
            throw new BrapiException("brapi missing results[0].data for " + ticker);
        }

        // No invented defaults: currency/price/time must be present.
        String currency = requireText(data, "currency", ticker);
        BigDecimal price = requireDecimal(data, "regularMarketPrice", ticker);
        BigDecimal prevClose = optionalDecimal(data, "regularMarketPreviousClose"); // number only; DATE unknown
        Instant sourceTime = requireIsoInstant(data, "regularMarketTime", ticker);

        // 'changed' means the provider remapped the symbol — model it as a SEPARATE flag.
        // Keep the LITERAL returned symbol intact; the validator's identity check uses the
        // flag to fail-closed, so we never corrupt the provider's literal value.
        Quote quote = new Quote(
                requested,
                returned,          // literal symbol, never decorated
                changed,           // remapped flag (separate dimension)
                currency,
                price,
                prevClose,
                null,              // previousCloseDate: Brapi does NOT expose it → UNKNOWN
                sourceTime,
                clock.instant(),   // receipt instant (never a substitute for sourceTime)
                staleHeader[0]
        );
        return new FetchResult(quote, signal[0]);
    }

    private static String requireText(JsonNode n, String field, String ticker) throws BrapiException {
        if (n == null || !n.hasNonNull(field) || n.get(field).asText().isBlank()) {
            throw new BrapiException("brapi missing '" + field + "' for " + ticker);
        }
        return n.get(field).asText();
    }

    /** First present value among candidate header names (case-insensitive via HttpHeaders), or null. */
    private static String firstHeader(org.springframework.http.HttpHeaders h, String... names) {
        for (String name : names) {
            String v = h.getFirst(name);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** Parse a long header; null (uncertain) on absent/malformed — never a fabricated default. */
    private static Long parseLong(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Long v) { return v == null ? null : v.intValue(); }

    /** Parse an RFC-1123 HTTP {@code Date} header to an Instant; null on absent/malformed (uncertain). */
    private static Instant parseHttpDate(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.from(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(v.trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal requireDecimal(JsonNode n, String field, String ticker) throws BrapiException {
        if (n == null || !n.hasNonNull(field) || !n.get(field).isNumber()) {
            throw new BrapiException("brapi missing/non-numeric '" + field + "' for " + ticker);
        }
        return new BigDecimal(n.get(field).asText());
    }

    private static BigDecimal optionalDecimal(JsonNode n, String field) {
        return (n != null && n.hasNonNull(field) && n.get(field).isNumber())
                ? new BigDecimal(n.get(field).asText()) : null;
    }

    private static Instant requireIsoInstant(JsonNode n, String field, String ticker) throws BrapiException {
        if (n == null || !n.hasNonNull(field)) {
            throw new BrapiException("brapi missing '" + field + "' for " + ticker);
        }
        try {
            return Instant.parse(n.get(field).asText()); // ISO-8601 with Z
        } catch (DateTimeParseException e) {
            throw new BrapiException("brapi malformed ISO time in '" + field + "' for " + ticker);
        }
    }

    /** Unchecked carrier so a checked BrapiException can escape the exchange() lambda. */
    private static final class UncheckedBrapi extends RuntimeException {
        final BrapiException checked;
        UncheckedBrapi(BrapiException c) { super(c); this.checked = c; }
    }
}
