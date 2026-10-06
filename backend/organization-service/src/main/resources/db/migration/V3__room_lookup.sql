CREATE TABLE room (
    room_id       UUID PRIMARY KEY,
    department_id UUID NOT NULL,
    room_name     VARCHAR(100) NOT NULL,
    room_type     VARCHAR(30) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT fk_room_department
        FOREIGN KEY (department_id)
        REFERENCES department (department_id)
);

CREATE INDEX idx_room_department_id
    ON room (department_id);
