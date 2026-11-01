CREATE TABLE doctor_work_shift (
    shift_id UUID PRIMARY KEY,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    doctor_id UUID NOT NULL,
    department_id UUID NOT NULL,
    room_id UUID NOT NULL,
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (start_time >= TIME '07:00' AND start_time < end_time AND end_time <= TIME '17:00'
        AND EXTRACT(SECOND FROM start_time) = 0 AND EXTRACT(SECOND FROM end_time) = 0)
);
CREATE INDEX idx_work_shift_department_date ON doctor_work_shift(department_id, shift_date, start_time, shift_id);
CREATE INDEX idx_work_shift_doctor_date ON doctor_work_shift(doctor_id, shift_date, start_time, shift_id);
CREATE TABLE doctor_work_shift_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES doctor_work_shift(shift_id),
    revision INTEGER NOT NULL,
    actor_account_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL,
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    UNIQUE (shift_id, revision)
);
CREATE TABLE consultation_session_work_shift (
    session_id UUID PRIMARY KEY REFERENCES consultation_session(session_id),
    shift_id UUID NOT NULL REFERENCES doctor_work_shift(shift_id)
);
CREATE INDEX idx_consultation_session_work_shift ON consultation_session_work_shift(shift_id);

-- Both source-link insertion and every later session edit lock the shift row. A cancellation
-- cannot commit between the source check and source-link insertion/session mutation.
CREATE FUNCTION validate_session_work_shift() RETURNS TRIGGER LANGUAGE plpgsql AS $$
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
    IF source_shift.status <> 'ACTIVE' OR target.status = 'CANCELLED'
        OR source_shift.doctor_id <> target.doctor_id OR source_shift.department_id <> target.department_id
        OR source_shift.room_id <> target.room_id OR source_shift.shift_date <> target.session_date
        OR target.start_time < source_shift.start_time OR target.end_time > source_shift.end_time THEN
        RAISE EXCEPTION 'SHIFT_SESSION_MISMATCH' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_session_work_shift_link BEFORE INSERT ON consultation_session_work_shift
    FOR EACH ROW EXECUTE FUNCTION validate_session_work_shift();
CREATE TRIGGER validate_session_work_shift_edit BEFORE UPDATE ON consultation_session
    FOR EACH ROW EXECUTE FUNCTION validate_session_work_shift();

CREATE FUNCTION protect_work_shift() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Work shifts must be cancelled' USING ERRCODE = '23514'; END IF;
    IF OLD.status = 'CANCELLED' THEN RAISE EXCEPTION 'SHIFT_INACTIVE' USING ERRCODE = '23514'; END IF;
    IF NEW.department_id <> OLD.department_id OR NEW.revision <> OLD.revision + 1 THEN
        RAISE EXCEPTION 'Invalid shift revision or department' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (SELECT 1 FROM consultation_session_work_shift l JOIN consultation_session s USING(session_id)
            WHERE l.shift_id = OLD.shift_id AND s.status <> 'CANCELLED') THEN
        RAISE EXCEPTION 'SHIFT_HAS_SESSIONS' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_work_shift BEFORE UPDATE OR DELETE ON doctor_work_shift
    FOR EACH ROW EXECUTE FUNCTION protect_work_shift();
CREATE FUNCTION reject_work_shift_history_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Work shift history and source links are immutable' USING ERRCODE = '23514'; END $$;
CREATE TRIGGER immutable_work_shift_audit BEFORE UPDATE OR DELETE ON doctor_work_shift_audit
    FOR EACH ROW EXECUTE FUNCTION reject_work_shift_history_mutation();
CREATE TRIGGER immutable_work_shift_source BEFORE UPDATE OR DELETE ON consultation_session_work_shift
    FOR EACH ROW EXECUTE FUNCTION reject_work_shift_history_mutation();
