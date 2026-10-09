package dev.b3monitor.domain.outbox;

/**
 * Port for the outbound WhatsApp (WAHA) adapter. The real adapter contract
 * (edition/version/auth/endpoint/receipt semantics) is an unresolved gate (Q-09/Q-25), so production
 * sending stays disabled.
 *
 * <p>Cycle-7 review P0-2: the adapter returns a narrow {@link SubmissionResult} — it does NOT and
 * cannot return an outbox persistence state. The dispatcher is the only component that maps the
 * transport outcome to a legal {@code OutboxState}, so an adapter can never cause a post-submission
 * {@code PENDING} (duplicate-send hazard) or any other illegal state.
 */
public interface WahaOutboundAdapter {
    /**
     * Attempt to submit one alert message.
     * @return the transport-level result (never null; a null kind is treated as UNKNOWN).
     */
    SubmissionResult send(AlertIntent intent);
}
