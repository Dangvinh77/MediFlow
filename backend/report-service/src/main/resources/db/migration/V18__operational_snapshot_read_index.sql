-- Additive read-only optimization; no source acceptance, publication or historical backfill.
CREATE INDEX ix_operational_snapshot_read
    ON operational_replay_scope(generation_id, department_id, metric_date, metric_type)
    INCLUDE (numeric_value);
