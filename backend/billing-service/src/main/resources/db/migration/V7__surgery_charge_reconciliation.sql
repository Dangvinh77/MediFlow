-- Surgery charge intake (CONTRACT-SURGERY-BILLING-01). Charge dedup already uses the existing
-- uq_charge_source(source_type, source_id, price_code); this column only remembers WHICH immutable
-- surgery.completed resultId last reconciled a charge, so a redelivered result with the same
-- figures is a no-op while a different result (or the same result with different figures) is a
-- contract conflict rather than a silent overwrite — see Charge#reconcilePerformed.
ALTER TABLE CHARGE ADD COLUMN reconciled_result_id UUID;
