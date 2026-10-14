-- Real Organization reference data only: no seed, cross-service FK or reservation state.
CREATE TABLE operating_room (
    room_id UUID PRIMARY KEY,
    room_code VARCHAR(40) NOT NULL UNIQUE CHECK (room_code ~ '^[A-Z0-9_-]{1,40}$'),
    room_name VARCHAR(100) NOT NULL CHECK (length(trim(room_name)) > 0),
    department_id UUID NOT NULL REFERENCES department(department_id),
    is_active BOOLEAN NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_operating_room_department ON operating_room(department_id);

CREATE TABLE surgical_capability (
    staff_id UUID NOT NULL REFERENCES staff(staff_id),
    team_role VARCHAR(30) NOT NULL CHECK (team_role IN
        ('PRIMARY_SURGEON','ASSISTANT_SURGEON','ANESTHESIOLOGIST','OR_NURSE')),
    department_id UUID NOT NULL REFERENCES department(department_id),
    is_active BOOLEAN NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL CHECK (valid_until > valid_from),
    revision BIGINT NOT NULL CHECK (revision > 0),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (staff_id, team_role)
);

-- Durable immutable decision history, also used as the delivery outbox.
CREATE TABLE surgery_authority_outbox (
    event_id UUID PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_surgery_authority_outbox_pending ON surgery_authority_outbox(occurred_at)
    WHERE published_at IS NULL;
