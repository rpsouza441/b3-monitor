package dev.b3monitor.admin;

import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycle-19 P1-B (mechanism) — unit proof of the two guarantees the import size filter makes, independent of
 * HTTP transport and Spring Security ordering:
 * <ul>
 *   <li>a DECLARED Content-Length over the cap is rejected with 413 before the chain is invoked (the body is
 *       never read);</li>
 *   <li>a chunked / unknown-length body is wrapped in a bounded stream that throws
 *       {@link AnalyticsImportSizeLimitFilter.BodyTooLargeException} the instant more than
 *       {@code MAX_FILE_BYTES} bytes are consumed — so an oversize chunked upload can never be fully
 *       buffered.</li>
 * </ul>
 * The real embedded-Tomcat endpoint behavior is proven separately in
 * {@link AnalyticsImportSizeLimitEndpointTest}.
 */
class AnalyticsImportSizeLimitFilterTest {

    static final int MAX = AnalyticsSnapshotContract.MAX_FILE_BYTES;
    private final AnalyticsImportSizeLimitFilter filter = new AnalyticsImportSizeLimitFilter();

    private MockHttpServletRequest importPost() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/imports/preview");
        req.setContentType("application/json");
        return req;
    }

    // declared Content-Length over the cap -> 413, chain NEVER invoked (body not materialized).
    @Test
    void declaredOversizeRejectedWithoutInvokingChain() throws Exception {
        MockHttpServletRequest req = importPost();
        byte[] big = new byte[MAX + 1];
        req.setContent(big);                      // MockHttpServletRequest sets Content-Length = big.length
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (rq, rs) -> chainCalled[0] = true;

        filter.doFilter(req, resp, chain);

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, resp.getStatus(), "413 for declared oversize");
        assertFalse(chainCalled[0], "the downstream chain must not be invoked for a declared-oversize body");
    }

    // a non-import path is passed through untouched regardless of size.
    @Test
    void nonImportPathPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/rules");
        req.setContent(new byte[MAX + 1]);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        filter.doFilter(req, resp, (rq, rs) -> chainCalled[0] = true);
        assertTrue(chainCalled[0], "a non-import path is not size-filtered");
        assertNotEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, resp.getStatus());
    }

    // chunked / unknown length (Content-Length = -1): the bounded wrapper stream aborts past MAX.
    @Test
    void boundedStreamAbortsPastMaxOnChunkedBody() throws Exception {
        // A request reporting an UNKNOWN content length but delivering MAX+256 bytes. The filter wraps it;
        // reading the wrapped stream to the end must throw BodyTooLargeException exactly once past MAX.
        final byte[] payload = new byte[MAX + 256];
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/imports/preview") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1L; }
            @Override public ServletInputStream getInputStream() {
                InputStream src = new java.io.ByteArrayInputStream(payload);
                return new ServletInputStream() {
                    @Override public int read() throws IOException { return src.read(); }
                    @Override public int read(byte[] b, int off, int len) throws IOException { return src.read(b, off, len); }
                    @Override public boolean isFinished() { return false; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener l) { }
                };
            }
        };
        req.setContentType("application/json");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // The filter passes a wrapped request down the chain; the chain tries to read the whole body.
        FilterChain readingChain = (rq, rs) -> {
            HttpServletRequest http = (HttpServletRequest) rq;
            try (InputStream in = http.getInputStream()) {
                byte[] buf = new byte[8192];
                while (in.read(buf) != -1) { /* drain */ }
            }
        };

        assertThrows(AnalyticsImportSizeLimitFilter.BodyTooLargeException.class,
                () -> filter.doFilter(req, resp, readingChain),
                "reading an oversize chunked body through the bounded stream must throw past MAX");
    }

    // a body at or under the cap on the chunked path drains cleanly (no false positive at the boundary).
    @Test
    void boundedStreamAllowsBodyAtLimit() throws Exception {
        final byte[] payload = new byte[MAX];
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/imports/preview") {
            @Override public long getContentLengthLong() { return -1L; }
            @Override public ServletInputStream getInputStream() {
                InputStream src = new java.io.ByteArrayInputStream(payload);
                return new ServletInputStream() {
                    @Override public int read() throws IOException { return src.read(); }
                    @Override public int read(byte[] b, int off, int len) throws IOException { return src.read(b, off, len); }
                    @Override public boolean isFinished() { return false; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener l) { }
                };
            }
        };
        req.setContentType("application/json");
        int[] read = {0};
        FilterChain readingChain = (rq, rs) -> {
            try (InputStream in = ((HttpServletRequest) rq).getInputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) read[0] += n;
            }
        };
        filter.doFilter(req, new MockHttpServletResponse(), readingChain);
        assertEquals(MAX, read[0], "a body exactly at the limit drains fully without aborting");
    }
}
