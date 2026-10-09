package dev.b3monitor.admin;

import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract;
import dev.b3monitor.persistence.AnalyticsSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-19/20 P1-B — REAL-ENDPOINT proof of the analytics import body-size cap at the actual embedded-Tomcat
 * endpoint (a {@code @SpringBootTest(webEnvironment=RANDOM_PORT)} server + a JDK {@link HttpClient}), covering
 * transport semantics {@code MockMvc} cannot model.
 *
 * <h2>Contract (cycle-20 item 3 — determined and documented)</h2>
 * <ol>
 *   <li><b>Declared {@code Content-Length} &gt; MAX ⇒ DETERMINISTIC 413</b>, before Spring Security and before
 *       {@code @RequestBody byte[]} materialization. This is the normal case (a well-behaved client sends
 *       Content-Length) and the strong guarantee.</li>
 *   <li><b>Chunked / unknown-length body that REACHES import handling (authenticated + CSRF) ⇒ DETERMINISTIC
 *       413</b>, raised by the bounded stream the instant more than MAX bytes are read, so the oversize body is
 *       never fully materialized and nothing is imported.</li>
 *   <li><b>Chunked body that is NOT authorized to reach import handling ⇒ security-first rejection (401/403)
 *       before the controller reads the body.</b> Spring Security's CSRF filter runs before the body is read,
 *       so a token-less/anonymous oversize chunked POST is rejected without materialization. This is correct
 *       and must NOT be weakened to force a 413 — the body is still never imported.</li>
 * </ol>
 * The size cap is transport-level and independent of the database, so this runs on H2 (no Docker).
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
    @Autowired AnalyticsSnapshotRepository snapshots;

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

    // 2 — chunked oversize that is NOT authorized (no CSRF/session): security-first rejection (401/403),
    //     the body is never materialized or imported. This is the correct, un-weakened behavior.
    @Test
    void unauthenticatedChunkedOversizeIsSecurityFirstRejection() throws Exception {
        byte[] body = bodyOfBytes(MAX + 4096);
        HttpResponse<String> resp = post(body, false);       // chunked (no Content-Length)
        int sc = resp.statusCode();
        assertTrue(sc == 401 || sc == 403,
                "an un-CSRF'd chunked POST is rejected by security before the body is read, got " + sc);
        assertFalse(resp.body().contains("aaaa"), "no payload echo");
    }

    // 2b — chunked oversize that REACHES import handling (authenticated + CSRF via a real browser-style
    //      session) -> DETERMINISTIC 413 from the bounded stream, and NOTHING is imported. This is the
    //      cycle-20 strengthening: it proves oversized chunked content cannot be materialized or imported
    //      even when the request is fully authorized, without weakening auth/CSRF.
    @Test
    void authenticatedChunkedOversizeIsDeterministic413AndNotImported() throws Exception {
        // Browser-style session: GET the login page to establish a JSESSIONID + a CSRF token, like a browser.
        CookieManager cookies = new CookieManager();
        HttpClient sessionHttp = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> login = sessionHttp.send(
                HttpRequest.newBuilder(URI.create(base() + "/admin/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), "login page renders");
        String csrf = extractCsrf(login.body());
        assertNotNull(csrf, "login form carries a CSRF token");

        long before = snapshots.count();
        byte[] body = bodyOfBytes(MAX + 4096);
        // Chunked (no Content-Length) + Basic auth + the CSRF token as a HEADER (so the CSRF filter never
        // touches the body) + the session cookie carried by the CookieManager.
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/admin/imports/preview"))
                .header("Authorization", basic())
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body)))
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> resp = sessionHttp.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, resp.statusCode(),
                "an authorized oversize chunked body aborts at the bounded stream with a deterministic 413, got "
                        + resp.statusCode());
        assertFalse(resp.body().contains("aaaa"), "no payload echo");
        assertEquals(before, snapshots.count(), "nothing was imported from the oversize chunked body");
    }

    private static String extractCsrf(String html) {
        // Thymeleaf+Spring Security render: <input type="hidden" name="_csrf" value="...">
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("value=\"([^\"]+)\"\\s+name=\"_csrf\"").matcher(html);
        return m.find() ? m.group(1) : null;
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
