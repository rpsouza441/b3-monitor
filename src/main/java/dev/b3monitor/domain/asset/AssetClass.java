package dev.b3monitor.domain.asset;

/**
 * Explicit instrument classes (ADR-010). Classification MUST come from evidence
 * (official, internal-API, or provider), NEVER inferred from the ticker suffix.
 * {@link #UNKNOWN} is a first-class value for unresolved/quarantined assets.
 */
public enum AssetClass {
    ACAO,      // common/preferred share (ON/PN)
    UNIT,      // unit (bundle of shares)
    FII,       // fundo de investimento imobiliário
    FIAGRO,    // fundo do agronegócio
    UNKNOWN    // unresolved / quarantined (e.g. KNHY11)
}
