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
     * <p>Cycle-4 review P0-2: an expired-lease {@code IN_FLIGHT} row is deliberately NOT claimable
     * here. The adapter may already have been accepted by WAHA before the owner crashed, and the
     * fencing token protects only the local DB write, not the external send — so re-claiming an
     * IN_FLIGHT row would risk a duplicate real send. Expired IN_FLIGHT rows are instead reconciled
     * to {@code UNKNOWN_OUTCOME} by {@link #findExpiredInFlight}, never resent.
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

    /**
     * Unsent (PENDING) rows for a rule whose {@code ruleRevision} is strictly below {@code newRevision}
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
