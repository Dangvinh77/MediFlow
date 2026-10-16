-- Operator-only snapshot on the Pharmacy store. Not a migration, repair or activation script.
-- Run against an approved clone first. Zero rows/counts do not certify broker drain or MVP readiness.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '1s';

SELECT 'pharmacy' AS module, current_database() AS database_name, transaction_timestamp() AS observed_at,
       current_setting('transaction_read_only') AS read_only,
       current_setting('transaction_isolation') AS isolation_level;

SELECT o.care_contract_version AS outbox_contract_version, p.care_contract_version AS prescription_contract_version,
       COALESCE(p.care_context, 'UNKNOWN') AS care_context, o.delivery_enabled,
       CASE WHEN o.published_at IS NOT NULL THEN 'PUBLISHED'
            WHEN o.quarantined_at IS NOT NULL THEN 'QUARANTINED'
            WHEN NOT o.delivery_enabled THEN 'HELD'
            WHEN o.locked_at IS NOT NULL THEN 'LEASE_MARKED'
            WHEN o.available_at > transaction_timestamp() THEN 'SCHEDULED'
            ELSE 'READY' END AS delivery_state,
       count(*) AS event_count, min(o.created_at) AS oldest_created_at
FROM pharmacy_event_outbox o LEFT JOIN prescription p ON p.prescription_id = o.aggregate_id
GROUP BY o.care_contract_version, p.care_contract_version, p.care_context, o.delivery_enabled, delivery_state
ORDER BY outbox_contract_version, prescription_contract_version, care_context, delivery_state;

SELECT p.care_contract_version, COALESCE(p.care_context, 'UNKNOWN') AS care_context,
       r.status AS receipt_status, count(*) AS receipt_count, min(r.created_at) AS oldest_created_at
FROM payment_receipt r LEFT JOIN prescription p ON p.prescription_id = r.prescription_id
GROUP BY p.care_contract_version, p.care_context, r.status
ORDER BY p.care_contract_version, care_context, receipt_status;

SELECT p.care_contract_version, p.care_context, r.status AS reservation_status,
       count(*) AS reservation_count, sum(r.quantity) AS reservation_units, min(r.expires_at) AS earliest_expiry
FROM stock_reservation r JOIN prescription p ON p.prescription_id = r.prescription_id
GROUP BY p.care_contract_version, p.care_context, r.status
ORDER BY p.care_contract_version, p.care_context, reservation_status;

SELECT job_name, cursor_id IS NOT NULL AS cursor_present, lease_owner IS NOT NULL AS owner_present,
       lease_until, lease_token IS NOT NULL AS fence_present, updated_at
FROM pharmacy_scheduler_lease ORDER BY job_name;

ROLLBACK;
