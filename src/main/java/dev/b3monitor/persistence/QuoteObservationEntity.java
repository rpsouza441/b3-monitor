package dev.b3monitor.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable record of one quote observation. Market source time and receipt instant are
 * stored SEPARATELY (receipt never substitutes for source). Carries the provenance
 * (provider), the requested/returned identity, the validation verdict + reasons, and the
 * non-sensitive provider headers (e.g. stale flag). Rows are insert-only.
 */
@Entity
@Table(name = "quote_observation",
       indexes = {
           @Index(name = "ix_qobs_ticker_src", columnList = "requested_ticker, source_time"),
           @Index(name = "ix_qobs_receipt", columnList = "receipt_time")
       })
public class QuoteObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;              // e.g. "brapi"

    @Column(name = "requested_ticker", nullable = false, length = 20)
    private String requestedTicker;

    @Column(name = "returned_ticker", nullable = false, length = 40)
    private String returnedTicker;

    /** Whether the provider remapped the symbol ({@code changed=true}); provenance (cycle-6 review P1). */
    @Column(name = "provider_remapped", nullable = false)
    private boolean providerRemapped;

    /** The provider contract/schema revision this observation was mapped under (provenance). */
    @Column(name = "provider_contract", length = 40)
    private String providerContract;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "price", precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "previous_close", precision = 19, scale = 6)
    private BigDecimal previousClose;     // nullable; DATE/basis intentionally NOT stored (UNKNOWN)

    @Column(name = "source_time")
    private Instant sourceTime;           // market/source timestamp (may be null → ineligible)

    @Column(name = "receipt_time", nullable = false)
    private Instant receiptTime;

    @Column(name = "provider_stale", nullable = false)
    private boolean providerStale;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "rejection_reasons", length = 400)
    private String rejectionReasons;      // comma-joined enum names, or empty when eligible

    protected QuoteObservationEntity() {}

    /** Provider contract identifier recorded with Brapi-sourced observations (provenance). */
    public static final String BRAPI_V2_CONTRACT = "brapi/v2";

    public QuoteObservationEntity(String provider, String requestedTicker, String returnedTicker,
                                  boolean providerRemapped, String providerContract,
                                  String currency, BigDecimal price, BigDecimal previousClose,
                                  Instant sourceTime, Instant receiptTime, boolean providerStale,
                                  boolean eligible, String rejectionReasons) {
        this.provider = provider;
        this.requestedTicker = requestedTicker;
        this.returnedTicker = returnedTicker;
        this.providerRemapped = providerRemapped;
        this.providerContract = providerContract;
        this.currency = currency;
        this.price = price;
        this.previousClose = previousClose;
        this.sourceTime = sourceTime;
        this.receiptTime = receiptTime;
        this.providerStale = providerStale;
        this.eligible = eligible;
        this.rejectionReasons = rejectionReasons;
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getRequestedTicker() { return requestedTicker; }
    public String getReturnedTicker() { return returnedTicker; }
    public boolean isProviderRemapped() { return providerRemapped; }
    public String getProviderContract() { return providerContract; }
    public String getCurrency() { return currency; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public boolean isProviderStale() { return providerStale; }
    public boolean isEligible() { return eligible; }
    public String getRejectionReasons() { return rejectionReasons; }
    public Instant getSourceTime() { return sourceTime; }
    public Instant getReceiptTime() { return receiptTime; }
}
