-- Synthetic demo fixture only; run against the restored mediflow_organization DB.
-- Resolve the actual department from the demo doctor's account, never hard-code identities.
\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM account a JOIN staff s USING (staff_id)
                   WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE') THEN
        RAISE EXCEPTION 'Restore the synthetic demo snapshot before scheduling fixtures';
    END IF;
END $$;
INSERT INTO room (room_id, department_id, room_name, room_type, room_number, zone, location, is_active, created_at)
SELECT gen_random_uuid(), s.department_id, 'Phong kham ngoai tru demo V2', 'CONSULTATION',
       'V2-' || d.abbreviation, 'Khu A', COALESCE(NULLIF(d.location, ''), 'Khu A'), TRUE, now()
FROM account a JOIN staff s USING (staff_id) JOIN department d ON d.department_id = s.department_id
WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=s.department_id
                  AND r.room_type='CONSULTATION' AND r.is_active)
  AND NOT EXISTS (SELECT 1 FROM room r WHERE lower(r.zone)='khu a' AND r.room_number='V2-' || d.abbreviation);
COMMIT;

-- Bootstrap fixture rooms directly; admins manage real rooms via /api/v1/org/rooms.
-- Separate room and supplemental doctors avoid conflicts with demo.doctor's daily fixture.
BEGIN;
INSERT INTO room (room_id, department_id, room_name, room_type, room_number, zone, location, is_active, created_at)
SELECT gen_random_uuid(), d.department_id, 'Phong kham lich day demo', 'CONSULTATION',
       'LD-' || d.abbreviation, 'Khu A', COALESCE(NULLIF(d.location, ''), 'Khu A'), TRUE, now()
FROM department d
WHERE d.is_active AND d.department_type = 'CLINICAL'
  AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=d.department_id
                  AND r.room_name='Phong kham lich day demo' AND r.room_type='CONSULTATION' AND r.is_active)
  AND NOT EXISTS (SELECT 1 FROM room r WHERE lower(r.zone)='khu a' AND r.room_number='LD-' || d.abbreviation);
COMMIT;
