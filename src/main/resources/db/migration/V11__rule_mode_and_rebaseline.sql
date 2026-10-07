-- V11: cycle-8 rule mode + pause/resume continuity (review P0-live).
--   * rule_definition.mode: typed evaluation mode; existing rows backfill UNSELECTED (fail-closed) so
--     no rule is auto-promoted to CROSSING. A human selectMode() bumps the revision.
--   * rule_state.rebaseline_required: set on ordinary resume so the first eligible post-resume
--     observation re-baselines and cannot fire (no replay / no inferred crossing across the gap).
--   * tighten the precision CHECK to 0..6 to match NUMERIC(19,6) (lossless persistence).

ALTER TABLE rule_definition ADD COLUMN mode VARCHAR(12) NOT NULL DEFAULT 'UNSELECTED';

ALTER TABLE rule_state ADD COLUMN rebaseline_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE rule_definition DROP CONSTRAINT ck_rule_def_precision;
ALTER TABLE rule_definition ADD CONSTRAINT ck_rule_def_precision CHECK (price_precision BETWEEN 0 AND 6);
