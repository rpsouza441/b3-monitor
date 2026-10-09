package dev.b3monitor.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-12 item C / SEC-01 — REAL browser-session inactivity expiry over a running server (RANDOM_PORT)
 * with a 1-second test-only timeout, driven with the JDK {@link HttpClient} and explicit cookies (no
 * redirect follow). Proves: form login with a real cookie succeeds; the cookie authenticates a later GET
 * without Basic; after waiting BEYOND the inactivity timeout the SAME cookie no longer authenticates and
 * cannot mutate. Genuine EXPIRY — not a logout/invalidate substitute.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionExpiryTest {

    static final String ADMIN_PASS = "s3cr3t-session-pass";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern JSESSION = Pattern.compile("JSESSIONID=([^;]+)");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("b3monitor.admin.enabled", () -> "true");
        r.add("b3monitor.admin.username", () -> "admin");
        r.add("b3monitor.admin.password-hash",
                () -> new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(ADMIN_PASS));
        r.add("b3monitor.admin.bind-address", () -> "127.0.0.1");
        r.add("b3monitor.admin.session-timeout-seconds", () -> "1");
        r.add("server.servlet.session.timeout", () -> "1s");
    }

    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private String base() { return "http://127.0.0.1:" + port; }

    private static String sessionCookie(HttpResponse<?> resp) {
        return resp.headers().allValues("set-cookie").stream()
                .map(JSESSION::matcher).filter(Matcher::find)
                .map(m -> "JSESSIONID=" + m.group(1)).findFirst().orElse(null);
    }

    /** Real form login → returns the authenticated JSESSIONID cookie. */
    private String login() throws Exception {
        HttpResponse<String> page = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/admin/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String preCookie = sessionCookie(page);
        Matcher mm = CSRF.matcher(page.body());
        assertTrue(mm.find(), "login page carries a CSRF token");
        String csrf = mm.group(1);

        String form = "username=admin&password=" + ADMIN_PASS + "&_csrf=" + csrf;
        HttpResponse<String> loginResp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/admin/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", preCookie)
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, loginResp.statusCode(), "successful form login redirects");
        String authCookie = sessionCookie(loginResp);
        return authCookie != null ? authCookie : preCookie;
    }

    private int get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void sessionExpiresAfterInactivityTimeout() throws Exception {
        String cookie = login();
        assertNotNull(cookie, "a session cookie was issued");

        // reuse immediately → authorized on the JSON API without Basic
        assertEquals(200, get("/api/admin/status", cookie), "fresh session authenticates");

        // wait BEYOND the 1s inactivity timeout
        Thread.sleep(2500);

        // the SAME stale cookie no longer authenticates (401)
        assertEquals(401, get("/api/admin/status", cookie), "an expired session must not authenticate");

        // and it cannot mutate (401/403, never a 2xx/302 success)
        int mut = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/admin/rules/anything/pause"))
                        .header("Cookie", cookie)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        assertTrue(mut == 401 || mut == 403, "an expired session cannot mutate, got " + mut);
    }
}
