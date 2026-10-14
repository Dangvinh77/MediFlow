CREATE TABLE department (
    department_id       UUID PRIMARY KEY,
    department_name     VARCHAR(100) NOT NULL,
    abbreviation        VARCHAR(20) NOT NULL,
    department_type     VARCHAR(20) NOT NULL,
    department_head_id  UUID,
    location            VARCHAR(255),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,

    CONSTRAINT uk_department_abbreviation
        UNIQUE (abbreviation)
);

CREATE TABLE staff (
    staff_id       UUID PRIMARY KEY,
    full_name      VARCHAR(100) NOT NULL,
    department_id  UUID NOT NULL,

    job_title      VARCHAR(20) NOT NULL,
    specialization VARCHAR(100),
    license_number VARCHAR(50),
    phone_number   VARCHAR(15),
    email          VARCHAR(100),

    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,

    CONSTRAINT fk_staff_department
        FOREIGN KEY (department_id)
        REFERENCES department (department_id)
);

CREATE INDEX idx_staff_department_id
    ON staff (department_id);

CREATE INDEX idx_staff_job_title
    ON staff (job_title);

ALTER TABLE department
    ADD CONSTRAINT fk_department_head
    FOREIGN KEY (department_head_id)
    REFERENCES staff (staff_id);

CREATE TABLE account (
    account_id     UUID PRIMARY KEY,
    username       VARCHAR(50) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    staff_id       UUID,
    role           VARCHAR(20) NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,

    CONSTRAINT uk_account_username
        UNIQUE (username),

    CONSTRAINT fk_account_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff (staff_id)
);