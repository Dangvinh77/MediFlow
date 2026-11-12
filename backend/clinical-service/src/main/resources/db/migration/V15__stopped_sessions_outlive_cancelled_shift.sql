-- A cancelled shift may leave stopped sessions behind: their registrations still have to be served,
-- moved or cancelled, which keeps updating seat counters. Reopening stays impossible.
CREATE OR REPLACE FUNCTION validate_session_work_shift() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE source_id UUID; source_shift doctor_work_shift%ROWTYPE; target consultation_session%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'consultation_session_work_shift' THEN
        source_id := NEW.shift_id;
        SELECT * INTO target FROM consultation_session WHERE session_id = NEW.session_id;
    ELSE
        SELECT shift_id INTO source_id FROM consultation_session_work_shift WHERE session_id = NEW.session_id;
        IF source_id IS NULL THEN RETURN NEW; END IF;
        IF NEW.status = 'CANCELLED' THEN
            IF (NEW.doctor_id, NEW.department_id, NEW.room_id, NEW.session_date, NEW.start_time, NEW.end_time, NEW.breaks, NEW.capacity)
                    IS DISTINCT FROM
                    (OLD.doctor_id, OLD.department_id, OLD.room_id, OLD.session_date, OLD.start_time, OLD.end_time, OLD.breaks, OLD.capacity) THEN
                RAISE EXCEPTION 'SHIFT_SESSION_MISMATCH' USING ERRCODE = '23514';
            END IF;
            RETURN NEW;
        END IF;
        target := NEW;
    END IF;
    SELECT * INTO source_shift FROM doctor_work_shift WHERE shift_id = source_id FOR SHARE;
    -- Nested: the link table has no status column, and plpgsql does not short-circuit AND.
    IF TG_TABLE_NAME = 'consultation_session' AND source_shift.status = 'CANCELLED' THEN
        IF NEW.status = 'STOPPED'
                AND (NEW.doctor_id, NEW.department_id, NEW.room_id, NEW.session_date, NEW.start_time, NEW.end_time, NEW.breaks, NEW.capacity)
                    IS NOT DISTINCT FROM
                    (OLD.doctor_id, OLD.department_id, OLD.room_id, OLD.session_date, OLD.start_time, OLD.end_time, OLD.breaks, OLD.capacity) THEN
            RETURN NEW;
        END IF;
    END IF;
    IF source_shift.status <> 'ACTIVE' OR target.status = 'CANCELLED'
        OR source_shift.doctor_id <> target.doctor_id OR source_shift.department_id <> target.department_id
        OR source_shift.room_id <> target.room_id OR source_shift.shift_date <> target.session_date
        OR target.start_time < source_shift.start_time OR target.end_time > source_shift.end_time THEN
        RAISE EXCEPTION 'SHIFT_SESSION_MISMATCH' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION protect_work_shift() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Work shifts must be cancelled' USING ERRCODE = '23514'; END IF;
    IF OLD.status = 'CANCELLED' THEN RAISE EXCEPTION 'SHIFT_INACTIVE' USING ERRCODE = '23514'; END IF;
    IF NEW.department_id <> OLD.department_id OR NEW.revision <> OLD.revision + 1 THEN
        RAISE EXCEPTION 'Invalid shift revision or department' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM consultation_session_work_shift l JOIN consultation_session s USING(session_id)
            WHERE l.shift_id = OLD.shift_id
              AND (s.status = 'OPEN' OR (s.status = 'STOPPED' AND NEW.status <> 'CANCELLED'))) THEN
        RAISE EXCEPTION 'SHIFT_HAS_SESSIONS' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
