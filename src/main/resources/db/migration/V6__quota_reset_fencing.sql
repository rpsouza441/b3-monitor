-- V6: cycle-5 quota correctness (independent review ciclo-4 P0 + P1-high).
--   * Reset observation vs confirmation: pending_reset_at / pending_reset_observed_at record a
--     PREDICTED deadline only; last_confirmed_reset_at records a genuine, elapsed-deadline rollover.
--     The counter is no longer zeroed on an incoming ratelimit-reset delta.
--   * Fenced recoverable reservation: in_flight_owner / in_flight_token / in_flight_since let a
--     release match its reservation and let a crash-stranded slot be reconciled, not silently reopened.
--   * needs_reconcile: pessimistic operator gate when a reservation outlives its lease.

ALTER TABLE brapi_quota ADD COLUMN in_flight_owner           VARCHAR(80);
ALTER TABLE brapi_quota ADD COLUMN in_flight_token           BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE brapi_quota ADD COLUMN in_flight_since           TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN pending_reset_at          TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN pending_reset_observed_at TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN last_confirmed_reset_at   TIMESTAMPTZ;
ALTER TABLE brapi_quota ADD COLUMN needs_reconcile           BOOLEAN     NOT NULL DEFAULT FALSE;

-- Drop the cycle-4 columns the new model no longer uses (reset is now observed/confirmed, not applied).
-- reset_at / reset_observed_at are superseded by pending_reset_* and last_confirmed_reset_at.
ALTER TABLE brapi_quota DROP COLUMN reset_at;
ALTER TABLE brapi_quota DROP COLUMN reset_observed_at;
