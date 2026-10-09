package dev.b3monitor.domain.rule;

/**
 * Absolute price comparators. Proposed semantics (CONTRACTS): ABOVE/BELOW are
 * STRICT ( &gt; / &lt; ); equality evaluates FALSE. Inclusive variants would have to be
 * explicitly selected/versioned and are intentionally NOT provided here.
 */
public enum Comparator {
    ABOVE,
    BELOW
}
