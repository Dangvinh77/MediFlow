ALTER TABLE patient
    ADD COLUMN email_unique_key VARCHAR(254);

WITH normalized_emails AS (
    SELECT patient_id, NULLIF(lower(btrim(email)), '') AS normalized_email
    FROM patient
),
unique_emails AS (
    SELECT normalized_email
    FROM normalized_emails
    WHERE normalized_email IS NOT NULL
    GROUP BY normalized_email
    HAVING count(*) = 1
)
UPDATE patient AS target
SET email_unique_key = source.normalized_email
FROM normalized_emails AS source
JOIN unique_emails AS unique_email USING (normalized_email)
WHERE target.patient_id = source.patient_id;

CREATE UNIQUE INDEX ux_patient_email_key
    ON patient (email_unique_key)
    WHERE email_unique_key IS NOT NULL;
