-- A department's location follows its office room instead of being typed by hand:
-- the active DEPARTMENT_OFFICE of a clinical department, the single active workplace of any
-- other unit. A department without such a room has no location yet.
-- DEPARTMENT_OFFICE becomes system-defined so the catalogue can never lose the office type.

UPDATE room_type_catalog SET system_defined = TRUE, updated_at = now()
WHERE code = 'DEPARTMENT_OFFICE';

UPDATE department d
SET location = (
        SELECT r.location FROM room r
        WHERE r.department_id = d.department_id AND r.is_active
          AND (d.department_type <> 'CLINICAL' OR r.room_type = 'DEPARTMENT_OFFICE')
        ORDER BY r.created_at
        LIMIT 1);
