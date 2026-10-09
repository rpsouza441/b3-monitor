-- V8: cycle-6 quote provenance (independent review ciclo-5 P1-2).
--   Persist the provider remap/changed flag and the provider contract/schema revision so two
--   observations with equal requested/returned symbols are distinguishable when one was remapped,
--   and so a rejection can be explained. Source time and receipt time remain distinct (V2).
--   previousClose DATE/basis is still intentionally NOT stored (UNKNOWN).

ALTER TABLE quote_observation ADD COLUMN provider_remapped BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quote_observation ADD COLUMN provider_contract VARCHAR(40);
