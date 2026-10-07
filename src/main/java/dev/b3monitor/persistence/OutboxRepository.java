package dev.b3monitor.persistence;

import dev.b3monitor.domain.outbox.OutboxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    Optional<OutboxEntity> findByLogicalKey(String logicalKey);
    List<OutboxEntity> findByState(OutboxState state);
    List<OutboxEntity> findByRuleIdAndState(String ruleId, OutboxState state);
    boolean existsByLogicalKey(String logicalKey);

    /**
     * Rows a dispatcher may CLAIM for a first send: strictly {@code PENDING}.
     *
     * <p>Mid-attempt rows are never claimable here; they are handled by lease reconciliation
     * ({@link #findExpiredInFlightOrSending}) per the cycle-8 state machine:
     * <ul>
     *   <li>{@code IN_FLIGHT} = PRE-SEND (claimed, but SENDING never committed, so no adapter call was
     *       possible). An expired IN_FLIGHT lease is SAFELY RECOVERED to {@code PENDING} for a fresh
     *       claim + eligibility recheck — there is provably no external side effect to duplicate.</li>
     *   <li>{@code SENDING} = AMBIGUOUS (committed before I/O; the provider may already have accepted).
     *       An expired SENDING lease is quarantined to {@code UNKNOWN_OUTCOME}, never resent blindly,
     *       and its open attempt row is closed UNKNOWN.</li>
     * </ul>
     */
    @Query("""
           select o from OutboxEntity o
           where o.state = dev.b3monitor.domain.outbox.OutboxState.PENDING
           order by o.id asc
           """)
    List<OutboxEntity> findClaimable();

    /** Mid-attempt rows (IN_FLIGHT or SENDING) whose lease has expired — quarantine candidates. */
    @Query("""
           select o from OutboxEntity o
           where o.state in (dev.b3monitor.domain.outbox.OutboxState.IN_FLIGHT,
                             dev.b3monitor.domain.outbox.OutboxState.SENDING)
             and o.leaseUntil is not null and o.leaseUntil < :now
           order by o.id asc
           """)
    List<OutboxEntity> findExpiredInFlightOrSending(@Param("now") Instant now);

    /** Count rows in a given state — cheap metric for the reconciliation surface. */
    long countByState(OutboxState state);

    /** Creation instant of the oldest still-PENDING row, or null when none — for the oldest-age metric. */
    @Query("""
           select min(o.intentCreatedAt) from OutboxEntity o
           where o.state = dev.b3monitor.domain.outbox.OutboxState.PENDING
           """)
    Instant oldestPendingCreatedAt();

    /**
     * Rows needing operator attention: ambiguous ({@code UNKNOWN_OUTCOME}) or definitely failed
     * ({@code FAILED}). Oldest first. This is the local reconciliation/dead-letter queue; it is NOT
     * a resend list — an UNKNOWN_OUTCOME row is resent only with proof of non-delivery.
     */
    @Query("""
           select o from OutboxEntity o
           where o.state in (dev.b3monitor.domain.outbox.OutboxState.UNKNOWN_OUTCOME,
                             dev.b3monitor.domain.outbox.OutboxState.FAILED)
           order by o.id asc
           """)
    List<OutboxEntity> findNeedingReconciliation();

    /** Cycle-11 item F: BOUNDED dead-letter page (caller passes a capped Pageable), stable newest-first. */
    @Query("""
           select o from OutboxEntity o
           where o.state in (dev.b3monitor.domain.outbox.OutboxState.UNKNOWN_OUTCOME,
                             dev.b3monitor.domain.outbox.OutboxState.FAILED)
           order by o.id desc
           """)
    List<OutboxEntity> findNeedingReconciliation(org.springframework.data.domain.Pageable pageable);

    /**
     * Cycle-13 P1-A: a BOUNDED, deterministic newest-first page of RECENT logical alerts across ALL
     * transport states (PENDING/IN_FLIGHT/SENDING/ACCEPTED/UNKNOWN_OUTCOME/FAILED/CANCELLED/EXPIRED/
     * SUPPRESSED) for the UI-03 lifecycle view. Ordered by id DESC (stable monotonic tie-break). The
     * caller passes a hard-capped {@link org.springframework.data.domain.Pageable}; this is NOT a resend
     * list and is distinct from the reconciliation (dead-letter) query.
     */
    @Query("select o from OutboxEntity o order by o.id desc")
    List<OutboxEntity> findRecentAlerts(org.springframework.data.domain.Pageable pageable);

    /** Unsent (PENDING) rows for a rule whose {@code ruleRevision} is strictly below {@code newRevision}
     * — the rows a revision bump must cancel (cycle-6 review P0-3). ACCEPTED/UNKNOWN/terminal rows are
     * excluded so audit evidence is preserved.
     */
    @Query("""
           select o from OutboxEntity o
           where o.ruleId = :ruleId
             and o.state = dev.b3monitor.domain.outbox.OutboxState.PENDING
             and o.ruleRevision < :newRevision
           order by o.id asc
           """)
    List<OutboxEntity> findPendingByRuleBelowRevision(@Param("ruleId") String ruleId,
                                                      @Param("newRevision") long newRevision);
}
