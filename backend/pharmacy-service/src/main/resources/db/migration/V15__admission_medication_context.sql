-- Offline V1 projection. No FK to another service or prescription: CLOSED may arrive first.
CREATE TABLE admission_medication_context (
    admission_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    department_id UUID,
    started_at TIMESTAMPTZ,
    source_started_at VARCHAR(40),
    started_fingerprint VARCHAR(64),
    closed_at TIMESTAMPTZ,
    source_closed_at VARCHAR(40),
    closed_fingerprint VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_admission_medication_start CHECK (
        (started_at IS NULL AND source_started_at IS NULL AND started_fingerprint IS NULL AND department_id IS NULL)
        OR (started_at IS NOT NULL AND source_started_at IS NOT NULL AND started_fingerprint IS NOT NULL AND department_id IS NOT NULL)
    ),
    CONSTRAINT ck_admission_medication_close CHECK (
        (closed_at IS NULL AND source_closed_at IS NULL AND closed_fingerprint IS NULL)
        OR (closed_at IS NOT NULL AND source_closed_at IS NOT NULL AND closed_fingerprint IS NOT NULL)
    ),
    CONSTRAINT ck_admission_medication_time CHECK (
        started_at IS NULL OR closed_at IS NULL OR closed_at >= started_at
    ),
    -- PostgreSQL rounds to microseconds. Preserve exact source instants for domain ordering.
    CONSTRAINT ck_admission_medication_source_time CHECK (
        CAST(source_started_at AS TIMESTAMPTZ) IS NOT DISTINCT FROM started_at
        AND CAST(source_closed_at AS TIMESTAMPTZ) IS NOT DISTINCT FROM closed_at
    ),
    CONSTRAINT ck_admission_medication_hash CHECK (
        (started_fingerprint IS NULL OR started_fingerprint ~ '^[a-f0-9]{64}$')
        AND (closed_fingerprint IS NULL OR closed_fingerprint ~ '^[a-f0-9]{64}$')
    )
);
CREATE TABLE admission_medication_event (
    event_id UUID PRIMARY KEY,
    event_fingerprint VARCHAR(64) NOT NULL CHECK (event_fingerprint ~ '^[a-f0-9]{64}$'),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
