package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.SubmissionResult;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Append-only ledger of EXTERNAL submission attempts (cycle-8 review P1-high / item C). One row is
 * created when {@code prepareSend} commits {@code SENDING} — i.e. when an external side effect becomes
 * possible — NOT on claim. A pre-send claim crash therefore creates no attempt. {@code record} closes
 * exactly the open attempt with a typed outcome + provider provenance. A proof-gated requeue leads to a
 * NEW attempt row on the next send; prior rows are immutable, so the full attempt history is
 * reconstructable. No payload/secret is stored.
 */
@Entity
@Table(name = "outbox_attempt",
       uniqueConstraints = @UniqueConstraint(name = "uq_outbox_attempt",
               columnNames = {"outbox_id", "claim_generation"}))
public class OutboxAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Column(name = "logical_key", nullable = false, length = 200)
    private String logicalKey;

    /** Claim generation this attempt belongs to (lineage; unique per outbox row). */
    @Column(name = "claim_generation", nullable = false)
    private long claimGeneration;

    @Column(name = "fencing_token", nullable = false)
    private long fencingToken;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Typed transport outcome (null while the attempt is open). */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private SubmissionResult.Kind outcome;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "provider_accepted_at")
    private Instant providerAcceptedAt;

    @Column(name = "sanitized_status", length = 200)
    private String sanitizedStatus;

    protected OutboxAttemptEntity() {}

    public OutboxAttemptEntity(Long outboxId, String logicalKey, long claimGeneration,
                               long fencingToken, Instant startedAt) {
        this.outboxId = outboxId;
        this.logicalKey = logicalKey;
        this.claimGeneration = claimGeneration;
        this.fencingToken = fencingToken;
        this.startedAt = startedAt;
    }

    /** Close the attempt with its result (immutable thereafter). */
    public void close(Instant finishedAt, SubmissionResult result) {
        this.finishedAt = finishedAt;
        this.outcome = result.kind();
        this.providerMessageId = result.providerMessageId();
        this.providerAcceptedAt = result.providerAcceptedAt();
        this.sanitizedStatus = result.sanitizedStatus();
    }

    public Long getId() { return id; }
    public Long getOutboxId() { return outboxId; }
    public String getLogicalKey() { return logicalKey; }
    public long getClaimGeneration() { return claimGeneration; }
    public long getFencingToken() { return fencingToken; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public SubmissionResult.Kind getOutcome() { return outcome; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getProviderAcceptedAt() { return providerAcceptedAt; }
    public String getSanitizedStatus() { return sanitizedStatus; }
}
