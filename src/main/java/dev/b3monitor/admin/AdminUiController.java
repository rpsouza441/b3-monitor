package dev.b3monitor.admin;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.persistence.RuleAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Thin private browser UI (cycle-11 H; cycle-12 E/F/G). Registered ONLY when
 * {@code b3monitor.admin.enabled=true}. GET pages are read-only (viewer-readable per the security chain);
 * the audit page and every mutating POST are ADMIN-only (enforced by {@link AdminSecurityConfig}, not here).
 * Mutations delegate to the SAME {@link RuleAdminService}/{@link AdminQueryService} the JSON API uses — no
 * duplicated business logic — and use PRG with flash attributes (cycle-12 G) so a validation/conflict error
 * is shown sanitized after the redirect, with no mutation on invalid input and no false SUCCESS. No secret,
 * no asset-activation / WAHA / Brapi / live-send control.
 */
@Controller
@RequestMapping("/admin")
@ConditionalOnProperty(name = "b3monitor.admin.enabled", havingValue = "true")
public class AdminUiController {

    private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{4,12}");

    private final AdminQueryService query;
    private final RuleAdminService admin;

    public AdminUiController(AdminQueryService query, RuleAdminService admin) {
        this.query = query;
        this.admin = admin;
    }

    @GetMapping("/login")
    public String login() { return "admin/login"; }

    @GetMapping({"", "/", "/status"})
    public String status(Model m) {
        m.addAttribute("status", query.status());
        return "admin/status";
    }

    @GetMapping("/rules")
    public String rules(Model m) {
        m.addAttribute("rules", query.rules().rules());
        m.addAttribute("modes", RuleMode.values());
        m.addAttribute("comparators", Comparator.values());
        return "admin/rules";
    }

    @GetMapping("/assets")
    public String assets(Model m) {
        m.addAttribute("assets", query.assetFreshness().assets());
        return "admin/assets";
    }

    @GetMapping("/outbox")
    public String outbox(Model m) {
        m.addAttribute("metrics", query.outboxMetrics());
        m.addAttribute("reconciliation", query.reconciliation(50).rows());
        return "admin/outbox";
    }

    @GetMapping("/alerts")
    public String alerts(Model m) {
        m.addAttribute("alerts", query.alertOutcomes(50).alerts());
        return "admin/alerts";
    }

    @GetMapping("/audit")
    public String audit(Model m) {
        m.addAttribute("events", query.audit(50));
        return "admin/audit";
    }

    // ---- mutation forms (ADMIN + CSRF enforced by the security chain). PRG + flash (cycle-12 G). ----

    @PostMapping("/rules")
    public String create(@RequestParam String ruleId, @RequestParam String ticker,
                         @RequestParam Comparator comparator, @RequestParam BigDecimal threshold,
                         @RequestParam(defaultValue = "2") int precision,
                         @RequestParam(defaultValue = "0") BigDecimal hysteresis, RedirectAttributes ra) {
        try {
            admin.create(ruleId.trim(), validTicker(ticker), comparator, threshold, precision, hysteresis);
            ra.addFlashAttribute("ok", "Rule created: " + ruleId.trim());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", sanitize(e));   // no mutation happened; show the sanitized reason
        }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/mode")
    public String selectMode(@PathVariable String ruleId, @RequestParam RuleMode mode, RedirectAttributes ra) {
        try {
            admin.selectMode(ruleId, mode);
            ra.addFlashAttribute("ok", "Mode selected for " + ruleId);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", sanitize(e));
        }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/edit")
    public String edit(@PathVariable String ruleId, @RequestParam long expectedRevision,
                       @RequestParam Comparator comparator, @RequestParam BigDecimal threshold,
                       @RequestParam(defaultValue = "2") int precision,
                       @RequestParam(defaultValue = "0") BigDecimal hysteresis, RedirectAttributes ra) {
        try {
            long rev = admin.edit(ruleId, expectedRevision, comparator, threshold, precision, hysteresis);
            ra.addFlashAttribute("ok", "Rule " + ruleId + " edited → revision " + rev);
        } catch (RuleAdminService.StaleRevisionException e) {
            ra.addFlashAttribute("error", "Conflict: the rule changed since you loaded it (expected revision "
                    + e.expected + ", current " + e.actual + "). Reload and retry.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", sanitize(e));
        }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/pause")
    public String pause(@PathVariable String ruleId, RedirectAttributes ra) {
        try { admin.pause(ruleId); ra.addFlashAttribute("ok", "Paused " + ruleId); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", sanitize(e)); }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/resume")
    public String resume(@PathVariable String ruleId, RedirectAttributes ra) {
        try { admin.resume(ruleId); ra.addFlashAttribute("ok", "Resumed " + ruleId); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", sanitize(e)); }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/disable")
    public String disable(@PathVariable String ruleId, RedirectAttributes ra) {
        try { admin.disable(ruleId); ra.addFlashAttribute("ok", "Disabled " + ruleId); }
        catch (RuntimeException e) { ra.addFlashAttribute("error", sanitize(e)); }
        return "redirect:/admin/rules";
    }

    private String validTicker(String ticker) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        if (!TICKER.matcher(t).matches()) throw new IllegalArgumentException("invalid ticker: " + ticker);
        return t;
    }

    /** Sanitized, bounded error message — exception class message only, never a stack trace / secret / payload. */
    private static String sanitize(RuntimeException e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        msg = msg.replaceAll("\\s+", " ").trim();
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
