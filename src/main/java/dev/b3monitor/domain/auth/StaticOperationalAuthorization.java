package dev.b3monitor.domain.auth;

import dev.b3monitor.domain.rule.PriceRule;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Default, conservative operational-authorization state. EVERY asset starts
 * {@link Status#NOT_AUTHORIZED}; this class authorizes nothing on its own.
 *
 * <p>Fixed exceptions mirror the approved identity gate (DP-01, cycles 1–2):
 * <ul>
 *   <li>{@code SNAG11} → {@link Status#PARTIAL_IDENTITY} (identity only partially verified)</li>
 *   <li>{@code KNHY11} → {@link Status#QUARANTINED}</li>
 * </ul>
 * There is deliberately no setter that promotes an asset to {@link Status#AUTHORIZED}; flipping an
 * asset on is an operator action through a future authenticated admin surface, out of scope here.
 * An unknown ticker fails closed as {@link Status#NOT_AUTHORIZED}.
 */
@Component
public class StaticOperationalAuthorization implements OperationalAuthorization {

    private static final Map<String, Status> OVERRIDES = Map.of(
            "SNAG11", Status.PARTIAL_IDENTITY,
            "KNHY11", Status.QUARANTINED
    );

    /** Assets the operator has paused (none by default). */
    private static final Set<String> PAUSED = Set.of();

    @Override
    public Decision evaluate(PriceRule rule) {
        String ticker = rule.ticker() == null ? "" : rule.ticker().trim().toUpperCase();
        if (ticker.isEmpty()) {
            return new Decision(Status.NOT_AUTHORIZED, "empty ticker");
        }
        if (PAUSED.contains(ticker)) {
            return new Decision(Status.PAUSED, ticker + " paused by operator");
        }
        Status override = OVERRIDES.get(ticker);
        if (override != null) {
            return new Decision(override, ticker + " " + override.name().toLowerCase());
        }
        // Default: no asset is authorized without an explicit operator grant.
        return new Decision(Status.NOT_AUTHORIZED, ticker + " not authorized for operation");
    }
}
