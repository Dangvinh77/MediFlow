-- v2.0 Wave 5: orderable services and versioned result templates. Lab owns both; Billing keeps prices.
CREATE TABLE lab_result_template (
    template_code VARCHAR(32)  NOT NULL,
    version       INTEGER      NOT NULL CHECK (version > 0),
    name          VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    active        BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (template_code, version)
);
-- Exactly one version of a template is offered to new tests; older versions stay readable.
CREATE UNIQUE INDEX uq_lab_result_template_active ON lab_result_template (template_code) WHERE active;

CREATE TABLE lab_template_field (
    template_code   VARCHAR(32)  NOT NULL,
    version         INTEGER      NOT NULL,
    field_code      VARCHAR(32)  NOT NULL,
    label           VARCHAR(100) NOT NULL CHECK (length(btrim(label)) > 0),
    position        INTEGER      NOT NULL,
    required        BOOLEAN      NOT NULL,
    value_type      VARCHAR(20)  NOT NULL
        CHECK (value_type IN ('QUANTITATIVE', 'QUALITATIVE', 'SEMI_QUANTITATIVE', 'TEXT')),
    unit            VARCHAR(20),
    allowed_options TEXT[],
    allows_operator BOOLEAN      NOT NULL DEFAULT false,
    PRIMARY KEY (template_code, version, field_code),
    FOREIGN KEY (template_code, version) REFERENCES lab_result_template (template_code, version),
    CONSTRAINT ck_lab_template_field_options CHECK (
        value_type NOT IN ('QUALITATIVE', 'SEMI_QUANTITATIVE')
        OR COALESCE(cardinality(allowed_options), 0) > 0),
    CONSTRAINT ck_lab_template_field_operator CHECK (NOT allows_operator OR value_type = 'QUANTITATIVE')
);

CREATE TABLE lab_reference_range (
    range_id      UUID PRIMARY KEY,
    template_code VARCHAR(32)    NOT NULL,
    version       INTEGER        NOT NULL,
    field_code    VARCHAR(32)    NOT NULL,
    position      INTEGER        NOT NULL,
    sex           VARCHAR(10) CHECK (sex IN ('MALE', 'FEMALE')),
    age_min_days  INTEGER,
    age_max_days  INTEGER,
    low           NUMERIC(14, 4),
    high          NUMERIC(14, 4),
    critical_low  NUMERIC(14, 4),
    critical_high NUMERIC(14, 4),
    -- false until a clinical laboratory approves the range; the demo catalogue is never reviewed.
    reviewed      BOOLEAN        NOT NULL DEFAULT false,
    FOREIGN KEY (template_code, version, field_code)
        REFERENCES lab_template_field (template_code, version, field_code)
);

CREATE TABLE lab_service_catalog (
    service_code     VARCHAR(32)  PRIMARY KEY,
    name             VARCHAR(50)  NOT NULL CHECK (length(btrim(name)) > 0),
    display_group    VARCHAR(32)  NOT NULL
        CHECK (display_group IN ('HEMATOLOGY', 'BIOCHEMISTRY', 'IMMUNOLOGY', 'URINALYSIS', 'MICROBIOLOGY')),
    specimen_type    VARCHAR(32)  NOT NULL,
    preparation      VARCHAR(500),
    turnaround_hours INTEGER CHECK (turnaround_hours > 0),
    price_code       VARCHAR(64)  NOT NULL CHECK (length(btrim(price_code)) > 0),
    template_code    VARCHAR(32)  NOT NULL,
    panel            BOOLEAN      NOT NULL,
    active           BOOLEAN      NOT NULL
);

-- The service and template version a test was created with; a later template version never rewrites it.
CREATE TABLE lab_test_template_binding (
    test_id          UUID        PRIMARY KEY REFERENCES lab_test (test_id),
    service_code     VARCHAR(32) NOT NULL REFERENCES lab_service_catalog (service_code),
    template_code    VARCHAR(32) NOT NULL,
    template_version INTEGER     NOT NULL,
    FOREIGN KEY (template_code, template_version) REFERENCES lab_result_template (template_code, version)
);
