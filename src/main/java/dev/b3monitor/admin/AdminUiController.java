package dev.b3monitor.admin;

import dev.b3monitor.domain.rule.Comparator;
import dev.b3monitor.domain.rule.RuleMode;
import dev.b3monitor.persistence.RuleAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Thin private browser UI (cycle-11 item H), registered ONLY when {@code b3monitor.admin.enabled=true};
 * all routes live under {@code /admin/**} (authenticated by the security chain; login/logout permitted).
 * It renders server-side Thymeleaf and delegates EVERY mutation to the same {@link RuleAdminService} /
 * {@link AdminQueryService} the JSON API uses — no duplicated business logic. Forms carry a CSRF token
 * (Thymeleaf adds it automatically). No secret, no asset-activation / WAHA / Brapi / live-send control.
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

    @GetMapping("/outbox")
    public String outbox(Model m) {
        m.addAttribute("metrics", query.outboxMetrics());
        m.addAttribute("reconciliation", query.reconciliation(50).rows());
        return "admin/outbox";
    }

    @GetMapping("/audit")
    public String audit(Model m) {
        m.addAttribute("events", query.audit(50));
        return "admin/audit";
    }

    // ---- mutation forms (POST + CSRF enforced by the security chain) ----

    @PostMapping("/rules")
    public String create(@RequestParam String ruleId, @RequestParam String ticker,
                         @RequestParam Comparator comparator, @RequestParam BigDecimal threshold,
                         @RequestParam(defaultValue = "2") int precision,
                         @RequestParam(defaultValue = "0") BigDecimal hysteresis, Model m) {
        try {
            admin.create(ruleId.trim(), validTicker(ticker), comparator, threshold, precision, hysteresis);
        } catch (RuntimeException e) {
            m.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/mode")
    public String selectMode(@PathVariable String ruleId, @RequestParam RuleMode mode, Model m) {
        try { admin.selectMode(ruleId, mode); } catch (RuntimeException e) { m.addAttribute("error", e.getMessage()); }
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/edit")
    public String edit(@PathVariable String ruleId, @RequestParam long expectedRevision,
                       @RequestParam Comparator comparator, @RequestParam BigDecimal threshold,
                       @RequestParam(defaultValue = "2") int precision,
                       @RequestParam(defaultValue = "0") BigDecimal hysteresis) {
        admin.edit(ruleId, expectedRevision, comparator, threshold, precision, hysteresis);
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/{ruleId}/pause")
    public String pause(@PathVariable String ruleId) { admin.pause(ruleId); return "redirect:/admin/rules"; }

    @PostMapping("/rules/{ruleId}/resume")
    public String resume(@PathVariable String ruleId) { admin.resume(ruleId); return "redirect:/admin/rules"; }

    @PostMapping("/rules/{ruleId}/disable")
    public String disable(@PathVariable String ruleId) { admin.disable(ruleId); return "redirect:/admin/rules"; }

    private String validTicker(String ticker) {
        String t = ticker == null ? "" : ticker.trim().toUpperCase();
        if (!TICKER.matcher(t).matches()) throw new IllegalArgumentException("invalid ticker: " + ticker);
        return t;
    }
}
