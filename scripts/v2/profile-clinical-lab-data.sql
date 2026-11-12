-- MediFlow v2.0 data profile (MIG-01). Read-only: SELECT statements only.
-- Run with psql against the compose postgres, for example:
--   docker compose exec -T postgres psql -U postgres -f - < scripts/v2/profile-clinical-lab-data.sql
-- Rows counted here are review candidates. Do not infer missing values from them.

\connect mediflow_clinical
BEGIN TRANSACTION READ ONLY;

\echo == medical_record totals by status ==
SELECT status, count(*) AS records
FROM medical_record
GROUP BY status
ORDER BY status;

\echo == medical_record without appointment_id ==
SELECT count(*) AS records_without_appointment
FROM medical_record
WHERE appointment_id IS NULL;

\echo == medical_record whose patient, doctor or department differ from its appointment ==
SELECT count(*) FILTER (WHERE r.patient_id <> a.patient_id) AS patient_mismatch,
       count(*) FILTER (WHERE r.doctor_id <> a.doctor_id) AS doctor_mismatch,
       count(*) FILTER (WHERE r.department_id <> a.department_id) AS department_mismatch
FROM medical_record r
JOIN appointment a ON a.appointment_id = r.appointment_id;

\echo == medical_record by appointment care_contract_version ==
SELECT a.care_contract_version, count(*) AS records
FROM medical_record r
JOIN appointment a ON a.appointment_id = r.appointment_id
GROUP BY a.care_contract_version
ORDER BY a.care_contract_version;

COMMIT;

\connect mediflow_lab
BEGIN TRANSACTION READ ONLY;

\echo == lab_test by care_contract_version and status ==
SELECT care_contract_version, status, count(*) AS tests
FROM lab_test
GROUP BY care_contract_version, status
ORDER BY care_contract_version, status;

\echo == lab_test without source_order_id, by care_contract_version ==
SELECT care_contract_version, count(*) AS tests_without_source_order
FROM lab_test
WHERE source_order_id IS NULL
GROUP BY care_contract_version
ORDER BY care_contract_version;

\echo == distinct free-text test_type values to review before catalogue mapping ==
SELECT test_type, count(*) AS tests
FROM lab_test
GROUP BY test_type
ORDER BY tests DESC, test_type;

COMMIT;
