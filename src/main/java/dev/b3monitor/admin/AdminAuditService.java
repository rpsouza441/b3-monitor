package dev.b3monitor.admin;

import dev.b3monitor.admin.AdminAuditEvent.Action;
import dev.b3monitor.admin.AdminAuditEvent.Outcome;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Append-only admin-audit writer (cycle-11 item G). It lives in the admin command layer so a FUTURE
 * authenticated WhatsApp-command application service can reuse the same mechanism rather than bypass it.
 * The actor is resolved from the current {@link Authentication} (never a credential); detail is bounded
 * and sanitized by the caller. {@link #record} runs in the CALLER's transaction when there is one, so a
 * successful mutation + its SUCCESS audit row commit together; a rejected/standalone audit uses its own.
 */
@Service
public class AdminAuditService {

    private final AdminAuditRepository repo;
    private final Clock clock;

    public AdminAuditService(AdminAuditRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Resolve the current authenticated principal name, or "system" when none (non-HTTP callers). */
    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }

    /** Append one audit row in the current transaction. */
    @Transactional
    public void record(Action action, String ruleId, Long beforeRevision, Long afterRevision,
                       Outcome outcome, String detail) {
        repo.save(new AdminAuditEvent(clock.instant(), currentActor(), action, ruleId,
                beforeRevision, afterRevision, outcome, sanitize(detail), null));
    }

    /** Append an audit row in a NEW transaction (used to audit a rejected mutation whose business tx rolled back). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordRejection(Action action, String ruleId, Long beforeRevision,
                                Outcome outcome, String detail) {
        repo.save(new AdminAuditEvent(clock.instant(), currentActor(), action, ruleId,
                beforeRevision, null, outcome, sanitize(detail), null));
    }

    /** Bound the detail string so no oversized / payload-like content is stored. */
    private static String sanitize(String detail) {
        if (detail == null) return null;
        String d = detail.replaceAll("\\s+", " ").trim();
        return d.length() > 300 ? d.substring(0, 300) : d;
    }
}
