-- Synthetic demo fixture only; run against the restored mediflow_organization DB.
-- Resolve the actual department from the demo doctor's account, never hard-code identities.
--
-- Schema-aware: the restored snapshot predates Organization V19 (Core's Flyway runs after this
-- seed), so on that schema only legacy room columns exist and V19 backfills number, zone and
-- location when Core starts. On an already-migrated database the rooms get their workplace
-- placement here. PL/pgSQL plans each statement on first execution, so the branch that is not
-- taken never references missing columns. Admins manage real rooms via /api/v1/org/rooms.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE
    workplace_schema boolean := EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'room' AND column_name = 'room_number');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM account a JOIN staff s USING (staff_id)
                   WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE') THEN
        RAISE EXCEPTION 'Restore the synthetic demo snapshot before scheduling fixtures';
    END IF;

    -- demo.doctor's department needs one active consultation room.
    IF workplace_schema THEN
        INSERT INTO room (room_id, department_id, room_name, room_type, room_number, zone, location, is_active, created_at)
        SELECT gen_random_uuid(), s.department_id, 'Phong kham ngoai tru demo V2', 'CONSULTATION',
               'V2-' || d.abbreviation, 'Khu A', COALESCE(NULLIF(btrim(d.location), ''), 'Khu A'), TRUE, now()
        FROM account a JOIN staff s USING (staff_id) JOIN department d ON d.department_id = s.department_id
        WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE'
          AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=s.department_id
                          AND r.room_type='CONSULTATION' AND r.is_active)
          AND NOT EXISTS (SELECT 1 FROM room r WHERE lower(r.zone)='khu a'
                          AND lower(r.room_number)=lower('V2-' || d.abbreviation));
    ELSE
        INSERT INTO room (room_id, department_id, room_name, room_type, is_active, created_at)
        SELECT gen_random_uuid(), s.department_id, 'Phong kham ngoai tru demo V2', 'CONSULTATION', TRUE, now()
        FROM account a JOIN staff s USING (staff_id)
        WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE'
          AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=s.department_id
                          AND r.room_type='CONSULTATION' AND r.is_active);
    END IF;
END $$;
COMMIT;

-- Separate room and supplemental doctors avoid conflicts with demo.doctor's daily fixture.
BEGIN;
DO $$
DECLARE
    workplace_schema boolean := EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'room' AND column_name = 'room_number');
BEGIN
    IF workplace_schema THEN
        INSERT INTO room (room_id, department_id, room_name, room_type, room_number, zone, location, is_active, created_at)
        SELECT gen_random_uuid(), d.department_id, 'Phong kham lich day demo', 'CONSULTATION',
               'LD-' || d.abbreviation, 'Khu A', COALESCE(NULLIF(btrim(d.location), ''), 'Khu A'), TRUE, now()
        FROM department d
        WHERE d.is_active AND d.department_type = 'CLINICAL'
          AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=d.department_id
                          AND r.room_name='Phong kham lich day demo' AND r.room_type='CONSULTATION' AND r.is_active)
          AND NOT EXISTS (SELECT 1 FROM room r WHERE lower(r.zone)='khu a'
                          AND lower(r.room_number)=lower('LD-' || d.abbreviation));
    ELSE
        INSERT INTO room (room_id, department_id, room_name, room_type, is_active, created_at)
        SELECT gen_random_uuid(), d.department_id, 'Phong kham lich day demo', 'CONSULTATION', TRUE, now()
        FROM department d
        WHERE d.is_active AND d.department_type = 'CLINICAL'
          AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=d.department_id
                          AND r.room_name='Phong kham lich day demo' AND r.room_type='CONSULTATION' AND r.is_active);
    END IF;
END $$;

-- Lab v2 needs a second LAB_TECH so one person enters/submits and another releases. The snapshot
-- predates demo.lab2 (demo-seed.json staff-16), so add its account for the synthetic technician,
-- reusing demo.lab's password hash (same public demo password; no secret is written here).
-- Before V16 the username is the seed name and Core migrates it to the staff email.
DO $$
DECLARE
    email_schema boolean := EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'account' AND column_name = 'email_migrated');
BEGIN
    IF email_schema THEN
        EXECUTE $sql$
            INSERT INTO account (account_id, username, password_hash, staff_id, role, is_active, created_at, updated_at,
                                 email_migrated)
            SELECT gen_random_uuid(), lower(s.email), l.password_hash, s.staff_id, 'LAB_TECH', TRUE, now(), now(), TRUE
            FROM account l JOIN staff template ON template.staff_id = l.staff_id
            JOIN staff s ON lower(s.email) = 'demo.staff16@example.test' AND s.status = 'ACTIVE'
            WHERE l.role = 'LAB_TECH' AND lower(template.email) = 'demo.staff15@example.test'
              AND NOT EXISTS (SELECT 1 FROM account a WHERE a.staff_id = s.staff_id)
              AND NOT EXISTS (SELECT 1 FROM account a WHERE lower(a.username) = lower(s.email))
        $sql$;
    ELSE
        INSERT INTO account (account_id, username, password_hash, staff_id, role, is_active, created_at, updated_at)
        SELECT gen_random_uuid(), 'demo.lab2', l.password_hash, s.staff_id, 'LAB_TECH', TRUE, now(), now()
        FROM account l JOIN staff s ON lower(s.email) = 'demo.staff16@example.test' AND s.status = 'ACTIVE'
        WHERE l.username = 'demo.lab'
          AND NOT EXISTS (SELECT 1 FROM account a WHERE a.staff_id = s.staff_id OR a.username = 'demo.lab2');
    END IF;
END $$;
COMMIT;
