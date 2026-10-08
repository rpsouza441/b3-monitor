package dev.b3monitor.admin;

import dev.b3monitor.admin.AdminDtos.*;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.persistence.RuleAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Private admin/status surface (cycle-10 items C/D). Registered ONLY when
 * {@code b3monitor.admin.enabled=true}; otherwise the bean is absent and the fail-closed security chain
 * denies {@code /api/admin/**}. Reads require authentication; mutations require role ADMIN (enforced by
 * {@link AdminSecurityConfig}). It delegates to domain services — no direct repository mutation — and
 * returns only {@link AdminDtos} records. It CANNOT mutate asset authorization, recipients, WAHA, Brapi
 * credentials or trigger a live send; there is deliberately no such endpoint.
 */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
public class AdminController {

    private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{4,12}");

    private final AdminQueryService query;
    private final RuleAdminService admin;
    private final AnalyticsImportService imports;

    public AdminController(AdminQueryService query, RuleAdminService admin, AnalyticsImportService imports) {
        this.query = query;
        this.admin = admin;
        this.imports = imports;
    }

    // ---- reads ----

    @GetMapping("/status")
    public StatusView status() { return query.status(); }

    @GetMapping("/rules")
    public RuleListView rules() { return query.rules(); }

    @GetMapping("/rules/{ruleId}")
    public RuleView rule(@PathVariable String ruleId) {
        return query.rule(ruleId).orElseThrow(() -> notFound("rule not found: " + ruleId));
    }

    @GetMapping("/outbox/metrics")
    public OutboxMetricsView outboxMetrics() { return query.outboxMetrics(); }

    @GetMapping("/outbox/reconciliation")
    public ReconciliationView reconciliation(@RequestParam(name = "size", defaultValue = "50") int size) {
        return query.reconciliation(size);   // service hard-caps the page size (no unbounded response)
    }

    @GetMapping("/audit")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public java.util.List<AdminDtos.AuditEventView> audit(
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return query.audit(size);            // ADMIN only; service hard-caps the page size
    }

    @GetMapping("/quotes/{ticker}/latest")
    public QuoteFreshnessView latestQuote(@PathVariable String ticker) {
        String t = validTicker(ticker);
        return query.latestQuote(t).orElseThrow(() -> notFound("no observation for " + t));
    }

    @GetMapping("/assets")
    public AdminDtos.AssetFreshnessListView assets() { return query.assetFreshness(); }

    @GetMapping("/alerts")
    public AdminDtos.AlertOutcomeListView alerts(@RequestParam(name = "size", defaultValue = "50") int size) {
        return query.alertOutcomes(size);   // service hard-caps size + child attempts
    }

    /** UI-02 (cycle-14 E): read-only operator readiness snapshot. Activates nothing. */
    @GetMapping("/readiness")
    public AdminDtos.ReadinessView readiness() { return query.readiness(); }

    /** UI-02 (cycle-16): bounded analytics import history (viewer-readable, provenance only). */
    @GetMapping("/imports")
    public AdminDtos.ImportHistoryListView imports(@RequestParam(name = "size", defaultValue = "50") int size) {
        return query.importHistory(size);
    }

    /** UI-01 (cycle-16): per-asset analytics context from the latest snapshot (viewer-readable, context only). */
    @GetMapping("/analytics")
    public AdminDtos.AnalyticsContextListView analytics() { return query.analyticsContext(); }

    // ---- analytics snapshot import (ADMIN + CSRF): preview → commit ----

    /** Validate-only; persists nothing. ADMIN (POST under /api/admin/** is ADMIN-gated). */
    @PostMapping(value = "/imports/preview", consumes = "application/json")
    public AnalyticsImportService.PreviewResult previewImport(@RequestBody(required = false) byte[] body) {
        return imports.preview(body == null ? new byte[0] : body);
    }

    /** Re-validate + persist. Requires the preview token (HMAC-bound to content+actor+schema+snapshot). */
    @PostMapping(value = "/imports/commit", consumes = "application/json")
    public ResponseEntity<AnalyticsImportService.CommitResult> commitImport(
            @RequestBody(required = false) byte[] body,
            @RequestParam(name = "token", required = false) String token) {
        var result = imports.commit(body == null ? new byte[0] : body, token);
        HttpStatus status = switch (result.disposition()) {
            case "IMPORTED" -> HttpStatus.CREATED;
            case "IDEMPOTENT_NOOP" -> HttpStatus.OK;
            case "REJECTED_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;   // REJECTED_VALIDATION / REJECTED_TOKEN_*
        };
        return ResponseEntity.status(status).body(result);
    }

    // ---- mutations (ADMIN + CSRF) ----

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView create(@RequestBody CreateRuleRequest req) {
        try {
            admin.create(req.ruleId(), validTicker(req.ticker()), req.comparator(),
                    req.threshold(), req.precision() == null ? 2 : req.precision(),
                    req.hysteresis() == null ? BigDecimal.ZERO : req.hysteresis());
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            throw conflict(e.getMessage());               // already exists
        }
        return query.rule(req.ruleId()).orElseThrow(() -> notFound(req.ruleId()));
    }

    @PutMapping("/rules/{ruleId}")
    public RevisionResponse edit(@PathVariable String ruleId, @RequestBody EditRuleRequest req) {
        try {
            long rev = admin.edit(ruleId, req.expectedRevision(), req.comparator(), req.threshold(),
                    req.precision() == null ? 2 : req.precision(),
                    req.hysteresis() == null ? BigDecimal.ZERO : req.hysteresis());
            return new RevisionResponse(ruleId, rev);
        } catch (RuleAdminService.StaleRevisionException e) {
            throw conflict(e.getMessage());               // 409 stale expectedRevision
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            throw notFound(e.getMessage());               // unknown rule
        }
    }

    @PostMapping("/rules/{ruleId}/mode")
    public RevisionResponse selectMode(@PathVariable String ruleId, @RequestBody SelectModeRequest req) {
        if (req.mode() == RuleMode.LEVEL) throw badRequest("LEVEL mode is not activatable (Q-19 pending)");
        if (req.mode() == null || req.mode() == RuleMode.UNSELECTED)
            throw badRequest("a concrete operable mode is required");
        try {
            return new RevisionResponse(ruleId, admin.selectMode(ruleId, req.mode()));
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            throw notFound(e.getMessage());
        }
    }

    @PostMapping("/rules/{ruleId}/pause")
    public MessageResponse pause(@PathVariable String ruleId) {
        guardExists(ruleId); admin.pause(ruleId); return new MessageResponse("paused");
    }

    @PostMapping("/rules/{ruleId}/resume")
    public MessageResponse resume(@PathVariable String ruleId) {
        guardExists(ruleId); admin.resume(ruleId); return new MessageResponse("resumed");
    }

    @PostMapping("/rules/{ruleId}/disable")
    public MessageResponse disable(@PathVariable String ruleId) {
        guardExists(ruleId); admin.disable(ruleId); return new MessageResponse("disabled");
    }

    // ---- helpers ----

    private void guardExists(String ruleId) {
        if (admin.find(ruleId).isEmpty()) throw notFound("rule not found: " + ruleId);
    }

    private String validTicker(String ticker) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        if (!TICKER.matcher(t).matches()) throw badRequest("invalid ticker: " + ticker);
        return t;
    }

    private static ResponseStatusException badRequest(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m)   { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
    private static ResponseStatusException conflict(String m)   { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
