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
INSERT INTO room (room_id, department_id, room_name, room_type, is_active, created_at)
SELECT gen_random_uuid(), s.department_id, 'Phong kham ngoai tru demo V2', 'CONSULTATION', TRUE, now()
FROM account a JOIN staff s USING (staff_id)
WHERE a.username='demo.doctor' AND a.is_active AND s.status='ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM room r WHERE r.department_id=s.department_id
                  AND r.room_type='CONSULTATION' AND r.is_active);
COMMIT;
