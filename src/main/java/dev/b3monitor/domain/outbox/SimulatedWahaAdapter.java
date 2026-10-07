package dev.b3monitor.domain.outbox;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * SIMULATED WAHA adapter. It performs NO network I/O and sends NO real WhatsApp
 * message. It records the message as ACCEPTED so the outbox lifecycle can be
 * exercised end-to-end in tests and local development, while live sending stays
 * disabled. The real adapter is gated on the outbound contract (Q-09/Q-25) and a
 * separately authorized environment.
 */
@Component
@Profile("!live-waha")
public class SimulatedWahaAdapter implements WahaOutboundAdapter {

    @Override
    public SubmissionResult send(AlertIntent intent) {
        // Intentionally no I/O. Treat as accepted-for-simulation; delivery stays UNKNOWN.
        return SubmissionResult.accepted();
    }
}
