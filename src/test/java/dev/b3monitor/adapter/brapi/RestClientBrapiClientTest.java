package dev.b3monitor.adapter.brapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.b3monitor.domain.quote.Quote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the AUDITED Brapi v2 mapping against a loopback JDK HttpServer. No external
 * network, no token. Payloads/headers mirror docs/references/brapi/SCHEMA.md (v2 envelope,
 * results[0].data, ISO time, x-brapi-stale header). No legacy body is served.
 */
class RestClientBrapiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T17:30:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    private void handler(String bodyJson, int status, Map<String, String> headers) {
        server.createContext("/api/v2/stocks/quote", new HttpHandler() {
            @Override public void handle(HttpExchange ex) throws java.io.IOException {
                byte[] b = bodyJson.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                if (headers != null) headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
                ex.sendResponseHeaders(status, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
            }
        });
    }

    private RestClientBrapiClient client() {
        return new RestClientBrapiClient(baseUrl, "", 8000, clock);
    }

    private static final String OK_BODY = """
        {"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE3","changed":false,
          "data":{"shortName":"WEGE3","currency":"BRL","regularMarketPrice":49.75,
                  "regularMarketPreviousClose":50.10,
                  "regularMarketTime":"2026-10-06T17:17:30.000Z"}}],
         "requestedAt":"2026-10-06T17:30:00.000Z","took":0}
        """;

    @Test
    void mapsV2PayloadPreservingIdentityAndIsoTime() throws Exception {
        handler(OK_BODY, 200, Map.of()); // no x-brapi-stale header → not stale
        Quote q = client().fetchQuote("WEGE3");
        assertEquals("WEGE3", q.requestedTicker());
        assertEquals("WEGE3", q.returnedTicker());
        assertTrue(q.identityMatches());
        assertEquals("BRL", q.currency());
        assertEquals(0, q.price().compareTo(new java.math.BigDecimal("49.75")));
        assertEquals(0, q.previousClose().compareTo(new java.math.BigDecimal("50.10")));
        assertNull(q.previousCloseDate(), "previousClose DATE is not exposed by Brapi → UNKNOWN");
        assertEquals(Instant.parse("2026-10-06T17:17:30.000Z"), q.sourceTime());
        assertFalse(q.providerStale());
        assertEquals(clock.instant(), q.receiptTime());
    }

    @Test
    void staleHeaderPresentMarksQuoteStale() throws Exception {
        handler(OK_BODY, 200, Map.of("x-brapi-stale", "1"));
        Quote q = client().fetchQuote("WEGE3");
        assertTrue(q.providerStale(), "x-brapi-stale:1 header must set providerStale");
    }

    @Test
    void remapSurfacesAsChangedIdentity() throws Exception {
        String body = """
            {"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE9","changed":true,
              "data":{"currency":"BRL","regularMarketPrice":49.75,
                      "regularMarketTime":"2026-10-06T17:17:30.000Z"}}],"requestedAt":"x","took":0}
            """;
        handler(body, 200, Map.of());
        Quote q = client().fetchQuote("WEGE3");
        assertEquals("WEGE9", q.returnedTicker(), "literal returned symbol preserved, not decorated");
        assertTrue(q.remapped(), "changed=true surfaces as the separate remapped flag");
        assertFalse(q.identityMatches(), "a remapped symbol must not match the requested asset");
    }

    @Test
    void commaSeparatedTickerIsRejectedBeforeAnyRequest() {
        // one-ticker-per-request guard: never issues the HTTP call
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3,PETR4"));
    }

    @Test
    void missingDataObjectThrows() throws Exception {
        handler("""
            {"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE3","changed":false}],"took":0}
            """, 200, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void missingCurrencyThrowsNoDefault() throws Exception {
        handler("""
            {"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE3","changed":false,
              "data":{"regularMarketPrice":49.75,"regularMarketTime":"2026-10-06T17:17:30.000Z"}}],"took":0}
            """, 200, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"),
                "missing currency must fail, not default to BRL");
    }

    @Test
    void missingSymbolThrowsNoDefault() throws Exception {
        handler("""
            {"results":[{"requestedSymbol":"WEGE3","changed":false,
              "data":{"currency":"BRL","regularMarketPrice":49.75,"regularMarketTime":"2026-10-06T17:17:30.000Z"}}],"took":0}
            """, 200, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void malformedIsoTimeThrows() throws Exception {
        handler("""
            {"results":[{"requestedSymbol":"WEGE3","symbol":"WEGE3","changed":false,
              "data":{"currency":"BRL","regularMarketPrice":49.75,"regularMarketTime":"not-a-time"}}],"took":0}
            """, 200, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void errorBodyThrows() throws Exception {
        handler("""
            {"error":true,"code":"QUOTES_PER_REQUEST_EXCEEDED"}
            """, 400, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void rateLimited429Throws() throws Exception {
        handler("{\"error\":true,\"code\":\"RATE\"}", 429, Map.of("Retry-After", "30"));
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void emptyResultsThrows() throws Exception {
        handler("{\"results\":[],\"took\":0}", 200, Map.of());
        assertThrows(BrapiException.class, () -> client().fetchQuote("WEGE3"));
    }

    @Test
    void successResponseCapturesQuotaHeaders() throws Exception {
        handler(OK_BODY, 200, Map.of("ratelimit-reset", "7200", "ratelimit-remaining", "8000"));
        BrapiClient.FetchResult r = client().fetch("WEGE3");
        assertEquals("WEGE3", r.quote().returnedTicker());
        assertNotNull(r.signal());
        assertEquals(7200L, r.signal().resetDeltaSeconds(), "2xx ratelimit-reset captured");
        assertEquals(8000, r.signal().remaining(), "2xx ratelimit-remaining captured");
        assertFalse(r.signal().rateLimited());
    }

    @Test
    void successResponseWithoutQuotaHeadersYieldsNullSignals() throws Exception {
        handler(OK_BODY, 200, Map.of());
        BrapiClient.FetchResult r = client().fetch("WEGE3");
        assertNull(r.signal().resetDeltaSeconds(), "absent header is uncertain (null), never fabricated");
        assertNull(r.signal().remaining());
    }

    @Test
    void rateLimited429CarriesFullBillingCycleProvenance() {
        handler("{\"error\":true,\"code\":\"RATE\"}", 429, Map.of(
                "Retry-After", "30",
                "ratelimit-reset", "3600",
                "ratelimit-remaining", "9000",
                "ratelimit-limit", "15000",
                "x-ratelimit-window", "billing-cycle"));
        BrapiException ex = assertThrows(BrapiException.class, () -> client().fetch("WEGE3"));
        assertTrue(ex.isRateLimited());
        var s = ex.signal();
        assertEquals(30L, s.retryAfterSeconds());
        assertEquals(3600L, s.resetDeltaSeconds());
        assertEquals(9000, s.remaining());
        assertEquals(15000, s.limit(), "429 now carries limit (unified with 2xx)");
        assertEquals("billing-cycle", s.window(), "429 now carries window (unified with 2xx)");
    }

    @Test
    void rateLimited429WithoutProvenanceHasNullFields() {
        handler("{\"error\":true,\"code\":\"RATE\"}", 429, Map.of("Retry-After", "30"));
        BrapiException ex = assertThrows(BrapiException.class, () -> client().fetch("WEGE3"));
        assertEquals(30L, ex.signal().retryAfterSeconds());
        assertNull(ex.signal().resetDeltaSeconds(), "absent reset is null, not fabricated");
        assertNull(ex.signal().window());
        assertNull(ex.signal().limit());
    }
}
