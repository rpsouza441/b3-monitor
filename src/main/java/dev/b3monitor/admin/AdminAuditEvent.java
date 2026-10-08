package dev.b3monitor.admin;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One append-only ADMIN MUTATION AUDIT row (cycle-11 item G). Written by {@link AdminAuditService} inside
 * the mutation transaction; never updated or deleted at runtime. It records WHO did WHAT, WHEN, and the
 * OUTCOME + revision transition — and deliberately stores NO secret (no password/hash, Authorization
 * header, CSRF token, provider credential, or arbitrary payload). The {@code detail}/{@code correlationId}
 * are bounded, sanitized strings.
 */
@Entity
@Table(name = "admin_audit_event")
public class AdminAuditEvent {

    public enum Action { CREATE_RULE, EDIT_RULE, SELECT_MODE, PAUSE, RESUME, DISABLE, IMPORT_SNAPSHOT }
    public enum Outcome { SUCCESS, NO_OP, REJECTED_CONFLICT, REJECTED_VALIDATION, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private Action action;

    @Column(name = "rule_id", length = 100)
    private String ruleId;

    @Column(name = "before_revision")
    private Long beforeRevision;

    @Column(name = "after_revision")
    private Long afterRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 24)
    private Outcome outcome;

    @Column(name = "detail", length = 400)
    private String detail;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    protected AdminAuditEvent() {}

    public AdminAuditEvent(Instant occurredAt, String actor, Action action, String ruleId,
                           Long beforeRevision, Long afterRevision, Outcome outcome,
                           String detail, String correlationId) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.ruleId = ruleId;
        this.beforeRevision = beforeRevision;
        this.afterRevision = afterRevision;
        this.outcome = outcome;
        this.detail = detail;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public Action getAction() { return action; }
    public String getRuleId() { return ruleId; }
    public Long getBeforeRevision() { return beforeRevision; }
    public Long getAfterRevision() { return afterRevision; }
    public Outcome getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public String getCorrelationId() { return correlationId; }
}
