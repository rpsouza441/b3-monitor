-- V5: cycle-4 quota correctness (independent review P0-3).
--   * No calendar reset: cycle_key widened to an opaque id advanced only by provider reset evidence.
--   * Durable single-in-flight guard (in_flight) so admission exclusion holds across instances.
--   * Provider reset provenance (reset_at, reset_observed_at) and remaining budget.

ALTER TABLE brapi_quota ALTER COLUMN cycle_key TYPE VARCHAR(64);
ALTER TABLE brapi_quota ADD COLUMN cycle_epoch       BIGINT  NOT NULL DEFAULT 0;
ALTER TABLE brapi_quota ADD COLUMN in_flight         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE brapi_quota ADD COLUMN reset_at          TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN reset_observed_at TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN provider_remaining INTEGER;
