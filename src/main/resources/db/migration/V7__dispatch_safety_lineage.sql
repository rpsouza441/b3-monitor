-- V7: cycle-6 dispatch safety + lineage (independent review ciclo-5 P0-1/P0-2/P0-3).
--   * claim_generation: bumped on every claim AND every reconciliation transition, so a stale late
--     result (older generation / non-IN_FLIGHT) is fenced out and cannot overwrite a reconciliation.
--   * dispatch lineage for the pre-dispatch eligibility guard: the rule revision the intent was minted
--     for, the market source-as-of, the intent-created instant (from the injected Clock), an optional
--     hard expiry, the episode epoch, and a terminal suppression reason.
--   New terminal states (CANCELLED/EXPIRED/SUPPRESSED) are enum-string values in the existing
--   state column; no DDL change is needed for them.

ALTER TABLE alert_outbox ADD COLUMN claim_generation   BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE alert_outbox ADD COLUMN rule_revision      BIGINT      NOT NULL DEFAULT 1;
ALTER TABLE alert_outbox ADD COLUMN source_as_of       TIMESTAMPTZ;
ALTER TABLE alert_outbox ADD COLUMN intent_created_at  TIMESTAMPTZ;
ALTER TABLE alert_outbox ADD COLUMN expires_at         TIMESTAMPTZ;
ALTER TABLE alert_outbox ADD COLUMN episode_epoch      BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE alert_outbox ADD COLUMN suppression_reason VARCHAR(120);

-- Backfill intent_created_at for any pre-existing rows so the NOT NULL mapping is satisfiable, then
-- enforce it. (No rows exist in practice — the app has never run live — but this keeps the migration
-- safe and idempotent under ddl-auto=validate.)
UPDATE alert_outbox SET intent_created_at = created_at WHERE intent_created_at IS NULL;
ALTER TABLE alert_outbox ALTER COLUMN intent_created_at SET NOT NULL;

CREATE INDEX ix_outbox_rule_rev_state ON alert_outbox (rule_id, rule_revision, state);
