-- V9: cycle-7 submission authority + attempt provenance (independent review ciclo-6 P0-1/P0-2, item F).
--   * send_started_at: stamped when SENDING commits (submission authorized BEFORE network I/O).
--   * attempt_finished_at / provider_message_id / accepted_at: durable attempt provenance recorded
--     from the typed transport result (provider id/accepted-at only when a verified adapter supplies
--     them — never fabricated).
--   The SENDING state is an enum-string value in the existing state column; no DDL needed for it.

ALTER TABLE alert_outbox ADD COLUMN send_started_at     TIMESTAMPTZ;
ALTER TABLE alert_outbox ADD COLUMN attempt_finished_at TIMESTAMPTZ;
ALTER TABLE alert_outbox ADD COLUMN provider_message_id VARCHAR(120);
ALTER TABLE alert_outbox ADD COLUMN accepted_at         TIMESTAMPTZ;
