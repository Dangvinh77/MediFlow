-- Diagnostic reads only. No source backfill, projection mutation, retention purge or publication.
CREATE INDEX ix_report_pending_close_observed
    ON report_admission_fact(created_at,admission_id) WHERE fact_type='CLOSED';
CREATE INDEX ix_operational_replay_telemetry
    ON operational_replay_generation(status,created_at) INCLUDE (source_events,applied_events);
CREATE INDEX ix_cash_replay_telemetry
    ON cash_replay_generation(status,created_at) INCLUDE (source_receipts,applied_receipts);
CREATE INDEX ix_operational_legacy_unverified
    ON operational_source_snapshot(evidence_state) WHERE evidence_state='LEGACY_UNVERIFIED';
