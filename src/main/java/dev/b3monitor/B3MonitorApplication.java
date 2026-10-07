package dev.b3monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * B3 Monitor — validated quote monitoring with explainable price alerts.
 *
 * <p>MVP vertical slice (roadmap Phase 2–4 groundwork): Brapi quote ingestion →
 * quote validation → persistence → price rule evaluation → durable outbox →
 * outbound WAHA adapter (simulated; no real sends). Live activation is deliberately
 * NOT wired: workers start disabled and the WAHA adapter is a no-op simulator.
 */
@SpringBootApplication
@EnableScheduling
public class B3MonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(B3MonitorApplication.class, args);
    }
}
