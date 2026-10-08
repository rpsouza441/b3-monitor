package dev.b3monitor.admin;

import dev.b3monitor.domain.analytics.AnalyticsSnapshotContract;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cycle-18 item J: enforce the analytics import body-size cap BEFORE Spring materializes the full
 * {@code @RequestBody byte[]}. For a declared Content-Length over the cap we reject immediately with 413
 * (no body is read). For an unknown-length / chunked body we wrap the input stream in a bounded reader that
 * aborts once more than {@code MAX_FILE_BYTES} bytes have been consumed, so an oversize chunked upload can
 * never be fully buffered. The exactly-at-limit case is accepted; one byte over is rejected. The payload is
 * never echoed back — only a short status message. Applies to the import preview/commit routes only.
 */
public class AnalyticsImportSizeLimitFilter extends OncePerRequestFilter {

    private static final long MAX = AnalyticsSnapshotContract.MAX_FILE_BYTES;

    private static boolean isImportPath(String uri) {
        return uri != null && (uri.startsWith("/api/admin/imports/") || uri.startsWith("/admin/imports/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isImportPath(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared > MAX) {                                   // declared oversize: reject before reading a byte
            reject(response);
            return;
        }
        // Unknown/chunked length (declared < 0) or declared-but-trust-nothing: wrap with a bounded stream.
        chain.doFilter(new BoundedRequest(request), response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);   // 413
        response.setContentType("text/plain");
        response.getWriter().write("analytics snapshot exceeds " + MAX + " bytes");   // no payload echo
    }

    /** Wraps the request so the servlet input stream aborts past MAX bytes (chunked-safe). */
    static final class BoundedRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        BoundedRequest(HttpServletRequest r) { super(r); }
        @Override public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            jakarta.servlet.ServletInputStream delegate = super.getInputStream();
            return new jakarta.servlet.ServletInputStream() {
                long count = 0;
                @Override public int read() throws IOException {
                    int b = delegate.read();
                    if (b != -1 && ++count > MAX) throw new BodyTooLargeException();
                    return b;
                }
                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    int n = delegate.read(buf, off, len);
                    if (n > 0) { count += n; if (count > MAX) throw new BodyTooLargeException(); }
                    return n;
                }
                @Override public boolean isFinished() { return delegate.isFinished(); }
                @Override public boolean isReady() { return delegate.isReady(); }
                @Override public void setReadListener(jakarta.servlet.ReadListener l) { delegate.setReadListener(l); }
            };
        }
    }

    /** Signals an oversize chunked body; mapped to 413 by the advice. */
    public static final class BodyTooLargeException extends IOException {
        public BodyTooLargeException() { super("analytics snapshot body exceeds " + MAX + " bytes"); }
    }
}
