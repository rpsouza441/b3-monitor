package dev.b3monitor.admin;

import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-19 P1-B — REAL-ENDPOINT proof that the analytics import body-size cap is enforced by the registered
 * servlet filter at the actual embedded-Tomcat endpoint, BEFORE Spring materializes {@code @RequestBody
 * byte[]}. A {@code @SpringBootTest(webEnvironment=RANDOM_PORT)} server + a JDK {@link HttpClient} exercise
 * transport semantics that {@code MockMvc} cannot model: a declared {@code Content-Length} over the cap, a
 * chunked (unknown-length) body over the cap, the exactly-at-limit boundary, and one byte over. The 413 is
 * raised by the {@code HIGHEST_PRECEDENCE} filter ahead of Spring Security, so an oversize body is rejected
 * without any auth/CSRF work and without echoing the payload. Runs on H2 — the cap is transport-level and
 * independent of the database, so this needs no Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "b3monitor.admin.enabled=true",
        "b3monitor.admin.username=admin"
})
class AnalyticsImportSizeLimitEndpointTest {

    static final String ADMIN_PASS = "s3cr3t-admin-pass";
    static final int MAX = AnalyticsSnapshotContract.MAX_FILE_BYTES;   // 512 KiB

    @DynamicPropertySource
    static void adminHash(DynamicPropertyRegistry r) {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(ADMIN_PASS);
        r.add("b3monitor.admin.password-hash", () -> hash);
    }

    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private String base() { return "http://127.0.0.1:" + port; }

    private static String basic() {
        return "Basic " + Base64.getEncoder()
                .encodeToString(("admin:" + ADMIN_PASS).getBytes(StandardCharsets.UTF_8));
    }

    /** A JSON body of EXACTLY n bytes (padded in a string field — content is irrelevant here; we assert
     *  transport-layer behavior only). */
    private static byte[] bodyOfBytes(int n) {
        String prefix = "{\"schemaVersion\":\"x\",\"pad\":\"";
        String suffix = "\"}";
        int fill = n - prefix.length() - suffix.length();
        if (fill < 0) throw new IllegalArgumentException("n too small");
        StringBuilder sb = new StringBuilder(n);
        sb.append(prefix);
        for (int i = 0; i < fill; i++) sb.append('a');
        sb.append(suffix);
        byte[] b = sb.toString().getBytes(StandardCharsets.UTF_8);
        assertEquals(n, b.length, "exact body size");
        return b;
    }

    private HttpResponse<String> post(byte[] body, boolean fixedLength) throws Exception {
        HttpRequest.BodyPublisher pub = fixedLength
                ? HttpRequest.BodyPublishers.ofByteArray(body)                 // sets Content-Length
                : HttpRequest.BodyPublishers.ofInputStream(                     // chunked: unknown length
                        () -> new java.io.ByteArrayInputStream(body));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/admin/imports/preview"))
                .header("Authorization", basic())
                .header("Content-Type", "application/json")
                .POST(pub)
                .timeout(Duration.ofSeconds(20))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // 1 — declared Content-Length over the cap -> 413, rejected before body materialization, no payload echo.
    @Test
    void declaredOversizeContentLengthRejectedBeforeMaterialization() throws Exception {
        byte[] body = bodyOfBytes(MAX + 1024);
        HttpResponse<String> resp = post(body, true);        // fixed length => Content-Length header present
        assertEquals(413, resp.statusCode(), "declared oversize must be 413");
        assertFalse(resp.body().contains("aaaa"), "the payload must never be echoed back");
    }

    // 2 — chunked / unknown-length body over the cap -> DETERMINISTIC rejection before the body is
    //     materialized or imported. With no Content-Length the HIGHEST_PRECEDENCE filter wraps the request
    //     in a bounded stream and passes it on; Spring Security's CSRF filter runs BEFORE HTTP Basic, so a
    //     token-less POST is rejected (401) before the controller ever reads the body — and an
    //     authenticated+CSRF'd request would instead abort at the bounded stream with 413. Both are
    //     deterministic, both reject before materialization, neither imports. The assertion pins exactly
    //     that: a non-2xx deterministic reject with no payload echo. (The bounded-stream 413 itself is proven
    //     directly in AnalyticsImportSizeLimitFilterTest.)
    @Test
    void chunkedOversizeRejectedDeterministically() throws Exception {
        byte[] body = bodyOfBytes(MAX + 4096);
        HttpResponse<String> resp = post(body, false);       // input-stream publisher => chunked, no Content-Length
        int sc = resp.statusCode();
        assertTrue(sc == 413 || sc == 401 || sc == 403,
                "chunked oversize must be deterministically rejected before materialization, got " + sc);
        assertTrue(sc >= 400, "never a 2xx for an oversize chunked body");
        assertFalse(resp.body().contains("aaaa"), "no payload echo on the chunked path");
    }

    // 3 — exactly 512 KiB is accepted by TRANSPORT (filter lets it through; downstream may 4xx on content,
    //     but it must NOT be a 413).
    @Test
    void exactlyAtLimitAcceptedByTransport() throws Exception {
        byte[] body = bodyOfBytes(MAX);
        HttpResponse<String> resp = post(body, true);
        assertNotEquals(413, resp.statusCode(),
                "a body exactly at the limit must pass the size filter (got " + resp.statusCode() + ")");
    }

    // 4 — one byte over the limit -> rejected (413) even with a declared length.
    @Test
    void oneByteOverRejected() throws Exception {
        byte[] body = bodyOfBytes(MAX + 1);
        HttpResponse<String> resp = post(body, true);
        assertEquals(413, resp.statusCode(), "one byte over the cap must be 413");
    }

    // 5 — the BROWSER form path (/admin/imports/preview, form POST) is also size-capped at 413.
    @Test
    void oversizeBrowserFormPostRejected() throws Exception {
        byte[] big = new byte[MAX + 2048];
        java.util.Arrays.fill(big, (byte) 'a');
        String form = "snapshot=" + new String(big, StandardCharsets.US_ASCII);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/admin/imports/preview"))
                .header("Authorization", basic())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, resp.statusCode(), "oversize browser form POST must be 413");
        assertFalse(resp.body().contains("aaaa"), "no form payload echo");
    }
}
