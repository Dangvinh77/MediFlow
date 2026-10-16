-- Operator-only snapshot on the Report store. Never source/coverage acceptance or a repair.
-- Run against an approved clone first. Do not use inventory counts as published report metrics.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '1s';

SELECT 'report' AS module, current_database() AS database_name, transaction_timestamp() AS observed_at,
       current_setting('transaction_read_only') AS read_only,
       current_setting('transaction_isolation') AS isolation_level;

SELECT status AS legacy_payment_status, count(*) AS contribution_count
FROM payment_contribution GROUP BY status ORDER BY status;

SELECT care_episode_type, classification, state AS refund_state, count(*) AS refund_count,
       min(created_at) AS oldest_created_at, min(next_attempt_at) AS earliest_attempt
FROM report_cash_refund GROUP BY care_episode_type, classification, state
ORDER BY care_episode_type, classification, refund_state;

SELECT source_type, metric_type, care_episode_type, fact_fingerprint IS NOT NULL AS fingerprint_present,
       count(*) AS contribution_count, min(metric_date) AS first_metric_date, max(metric_date) AS last_metric_date
FROM operational_contribution GROUP BY source_type, metric_type, care_episode_type, fingerprint_present
ORDER BY source_type, metric_type, care_episode_type, fingerprint_present;

SELECT count(*) AS journal_count, min(recorded_at) AS first_recorded_at, max(recorded_at) AS last_recorded_at
FROM operational_event_journal;

SELECT zone_id, currency, department_id IS NOT NULL AS department_scoped, count(*) AS coverage_record_count,
       min(covered_from) AS first_covered_from, max(covered_to) AS last_covered_to, max(accepted_at) AS latest_acceptance
FROM report_outpatient_coverage GROUP BY zone_id, currency, department_scoped
ORDER BY zone_id, currency, department_scoped;

SELECT 'OPERATIONS' AS rebuild_kind, status, count(*) AS generation_count FROM operational_replay_generation GROUP BY status
UNION ALL
SELECT 'CASH', status, count(*) FROM cash_replay_generation GROUP BY status
UNION ALL
SELECT 'REFUND', status, count(*) FROM refund_replay_generation GROUP BY status
UNION ALL
SELECT 'FINANCIAL_SOURCES_DEFERRED', status, count(*) FROM financial_source_rebuild GROUP BY status
ORDER BY rebuild_kind, status;

ROLLBACK;
