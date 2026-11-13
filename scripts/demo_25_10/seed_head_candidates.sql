-- Department-head candidates for the disposable demo (mediflow_organization, applied after restore).
-- Each active clinical department gets three doctors: TK01 and TK02 with an active DOCTOR account,
-- KHOA with a locked one. Accounts reuse demo.doctor's password hash, i.e. the same public demo
-- password as the other seeded accounts; no secret is written here and no account is issued
-- through the temporary-password email path. Rerunning is a no-op (identity/email/username guards).
-- Before V16 the username is the short seed name and Core migrates it to the staff email.
BEGIN;
WITH candidate(suffix, ordinal, keep_active, qualification) AS (
    VALUES ('tk01', 1, TRUE, 'Bác sĩ chuyên khoa II'),
           ('tk02', 2, TRUE, 'Bác sĩ chuyên khoa I'),
           ('khoa', 3, FALSE, 'Bác sĩ chuyên khoa I')
), clinical AS (
    SELECT department_id, department_name,
           lower(regexp_replace(coalesce(abbreviation, ''), '^DEMO-', '')) AS code,
           row_number() OVER (ORDER BY abbreviation, department_id) AS rank
    FROM department
    WHERE is_active AND department_type = 'CLINICAL'
)
INSERT INTO staff (staff_id, full_name, department_id, job_title, specialization, license_number, phone_number,
                   email, status, created_at, updated_at, identity_number, date_of_birth, gender, address,
                   hire_date, qualification)
SELECT gen_random_uuid(), left('BS Ứng viên ' || upper(c.suffix) || ' ' || d.department_name, 100), d.department_id,
       'DOCTOR', left(regexp_replace(d.department_name, ' Demo$', ''), 100),
       'LOCAL-DEMO-HEAD-' || upper(d.code) || '-' || upper(c.suffix),
       '0908899' || lpad(d.rank::text, 2, '0') || c.ordinal,
       'demo.head.' || d.code || '.' || c.suffix || '@example.test', 'ACTIVE', now(), now(),
       '99988299' || lpad(d.rank::text, 2, '0') || lpad(c.ordinal::text, 2, '0'),
       make_date(1975 + c.ordinal, c.ordinal, 10 + d.rank::int % 9), CASE c.ordinal WHEN 2 THEN 'FEMALE' ELSE 'MALE' END,
       (20 + d.rank) || ' Nguyễn Văn Cừ, TP. Hồ Chí Minh', make_date(2010 + c.ordinal, c.ordinal + 2, 1), c.qualification
FROM clinical d CROSS JOIN candidate c
WHERE d.code <> ''
  AND NOT EXISTS (SELECT 1 FROM staff s
                  WHERE lower(s.email) = 'demo.head.' || d.code || '.' || c.suffix || '@example.test'
                     OR s.identity_number = '99988299' || lpad(d.rank::text, 2, '0') || lpad(c.ordinal::text, 2, '0'));

DO $$
DECLARE
    email_schema boolean := EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'account' AND column_name = 'email_migrated');
    template_hash text := (SELECT a.password_hash FROM account a JOIN staff s ON s.staff_id = a.staff_id
                           WHERE a.role = 'DOCTOR' AND lower(s.email) = 'demo.staff03@example.test' LIMIT 1);
BEGIN
    IF template_hash IS NULL THEN
        RAISE EXCEPTION 'Head candidates need the demo DOCTOR account of demo.staff03@example.test';
    END IF;
    IF email_schema THEN
        INSERT INTO account (account_id, username, password_hash, staff_id, role, is_active, created_at, updated_at,
                             email_migrated)
        SELECT gen_random_uuid(), lower(s.email), template_hash, s.staff_id, 'DOCTOR',
               s.email NOT LIKE '%.khoa@example.test', now(), now(), TRUE
        FROM staff s
        WHERE lower(s.email) LIKE 'demo.head.%@example.test'
          AND NOT EXISTS (SELECT 1 FROM account a WHERE a.staff_id = s.staff_id)
          AND NOT EXISTS (SELECT 1 FROM account a WHERE lower(a.username) = lower(s.email));
    ELSE
        INSERT INTO account (account_id, username, password_hash, staff_id, role, is_active, created_at, updated_at)
        SELECT gen_random_uuid(), split_part(lower(s.email), '@', 1), template_hash, s.staff_id, 'DOCTOR',
               s.email NOT LIKE '%.khoa@example.test', now(), now()
        FROM staff s
        WHERE lower(s.email) LIKE 'demo.head.%@example.test'
          AND NOT EXISTS (SELECT 1 FROM account a WHERE a.staff_id = s.staff_id)
          AND NOT EXISTS (SELECT 1 FROM account a WHERE a.username = split_part(lower(s.email), '@', 1));
    END IF;
END $$;
COMMIT;
