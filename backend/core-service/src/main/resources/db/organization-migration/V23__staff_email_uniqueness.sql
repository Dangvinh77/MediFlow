ALTER TABLE staff
    ADD COLUMN email_unique_key VARCHAR(254);

WITH normalized_emails AS (
    SELECT staff_id, NULLIF(lower(btrim(email)), '') AS normalized_email
    FROM staff
),
unique_emails AS (
    SELECT normalized_email
    FROM normalized_emails
    WHERE normalized_email IS NOT NULL
    GROUP BY normalized_email
    HAVING count(*) = 1
)
UPDATE staff AS target
SET email_unique_key = source.normalized_email
FROM normalized_emails AS source
JOIN unique_emails AS unique_email USING (normalized_email)
WHERE target.staff_id = source.staff_id;

CREATE UNIQUE INDEX ux_staff_email_key
    ON staff (email_unique_key)
    WHERE email_unique_key IS NOT NULL;
