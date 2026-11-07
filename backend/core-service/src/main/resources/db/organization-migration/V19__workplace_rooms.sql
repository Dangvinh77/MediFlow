-- Rooms become the Organization workplace registry: every room has a number, zone and
-- location, and a type that separates consultation rooms (assigned to one clinical
-- department) from symbolic department offices and the single workplace of a
-- functional unit (laboratory, pharmacy dispensing, cashier, nursing, administration).

ALTER TABLE room ADD COLUMN room_number VARCHAR(20);
ALTER TABLE room ADD COLUMN zone VARCHAR(50);
ALTER TABLE room ADD COLUMN location VARCHAR(255);

-- Types outside the workplace catalogue (e.g. deferred surgery fixtures) become offices.
UPDATE room SET room_type = 'DEPARTMENT_OFFICE'
WHERE room_type NOT IN ('CONSULTATION', 'DEPARTMENT_OFFICE', 'LABORATORY', 'PHARMACY',
                        'CASHIER', 'NURSING', 'ADMINISTRATION');

-- Backfill: a stable per-department number, the department's location as both zone and place.
UPDATE room r SET
    room_number = 'R-' || upper(substr(replace(r.room_id::text, '-', ''), 1, 8)),
    zone = COALESCE(NULLIF(left(btrim(d.location), 50), ''), 'Chưa phân khu'),
    location = COALESCE(NULLIF(btrim(d.location), ''), 'Chưa cập nhật')
FROM department d
WHERE d.department_id = r.department_id;

ALTER TABLE room ALTER COLUMN room_number SET NOT NULL;
ALTER TABLE room ALTER COLUMN zone SET NOT NULL;
ALTER TABLE room ALTER COLUMN location SET NOT NULL;

ALTER TABLE room ADD CONSTRAINT ck_room_type CHECK (room_type IN (
    'CONSULTATION', 'DEPARTMENT_OFFICE', 'LABORATORY', 'PHARMACY',
    'CASHIER', 'NURSING', 'ADMINISTRATION'));

-- One physical room number per zone, regardless of status, so history never collides.
CREATE UNIQUE INDEX uk_room_zone_number ON room (lower(zone), lower(room_number));
CREATE INDEX idx_room_type_active ON room (room_type, is_active);
