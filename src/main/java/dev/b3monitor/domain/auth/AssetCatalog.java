package dev.b3monitor.domain.auth;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The trusted canonical B3 asset catalog — the 23 approved tickers (DP-01, cycles 1–2), mirrored from
 * {@code docs/planning/ASSET-CATALOG.md}. This is DATA, not inference: asset class is NEVER derived from
 * a ticker suffix (cycle-12 item E). The list is immutable and used by the UI-01 freshness view to show
 * a row per approved asset (and an explicit UNKNOWN when no observation exists). Nothing here authorizes
 * an asset — that remains {@link OperationalAuthorization}, which starts every asset NOT_AUTHORIZED.
 */
@Component
public class AssetCatalog {

    private static final List<String> TICKERS = List.of(
            "ALZR11", "BPAC11", "BTHF11", "BTLG11", "CMIG4", "EGIE3", "GARE11", "GGRC11", "HGBS11",
            "HGLG11", "ITUB4", "KNCR11", "KNHY11", "MXRF11", "RBVA11", "SAPR11", "SBSP3", "SNAG11",
            "TAEE11", "TIMS3", "VRTA11", "WEGE3", "XPLG11"
    );

    /** The canonical approved tickers, in a stable order. */
    public List<String> tickers() { return TICKERS; }
}
