-- Pharmacy: danh mục thuốc + tồn kho + đơn thuốc + phiếu xuất.

-- UUID PK do Hibernate sinh; money = DECIMAL(15,2); ngày = DATE / TIMESTAMPTZ.

CREATE TABLE DRUG (
    drug_id              UUID          PRIMARY KEY,
    drug_name            VARCHAR(150)  NOT NULL,
    active_ingredient    VARCHAR(150),
    unit                 VARCHAR(20)   NOT NULL,
    price                DECIMAL(15,2) NOT NULL,
    stock_quantity       INT           NOT NULL DEFAULT 0,
    expiry_date          DATE          NOT NULL,
    manufacturer         VARCHAR(150),
    low_stock_threshold  INT           NOT NULL DEFAULT 10,   
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    CONSTRAINT ck_drug_price_non_negative   CHECK (price >= 0),
    CONSTRAINT ck_drug_stock_non_negative   CHECK (stock_quantity >= 0)  
);
CREATE INDEX idx_drug_name ON DRUG (drug_name);              

CREATE TABLE PRESCRIPTION (
    prescription_id  UUID          PRIMARY KEY,
    record_id        UUID          NOT NULL,                 
    patient_id       UUID          NOT NULL,                
    doctor_id        UUID          NOT NULL,                 
    department_id    UUID          NOT NULL,                
    prescribed_date  DATE          NOT NULL,
    total_amount     DECIMAL(15,2) NOT NULL DEFAULT 0,       
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ
);
CREATE INDEX idx_prescription_patient ON PRESCRIPTION (patient_id);
CREATE INDEX idx_prescription_dept   ON PRESCRIPTION (department_id);

CREATE TABLE PRESCRIPTION_LINE (
    line_id          UUID          PRIMARY KEY,
    prescription_id  UUID          NOT NULL REFERENCES PRESCRIPTION(prescription_id) ON DELETE CASCADE,
    drug_id          UUID          NOT NULL REFERENCES DRUG(drug_id),
    quantity         INT           NOT NULL,
    unit_price       DECIMAL(15,2) NOT NULL,                 
    dosage           VARCHAR(255),
    line_total       DECIMAL(15,2) NOT NULL,
    CONSTRAINT ck_line_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_line_prescription ON PRESCRIPTION_LINE (prescription_id);

CREATE TABLE DISPENSE_SLIP (
    dispense_id     UUID          PRIMARY KEY,
    prescription_id UUID          NOT NULL UNIQUE REFERENCES PRESCRIPTION(prescription_id), 
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   
    dispensed_at    TIMESTAMPTZ,
    dispensed_by    UUID,                                      
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_dispense_status ON DISPENSE_SLIP (status);


CREATE TABLE PROCESSED_EVENT (
    event_id      UUID          PRIMARY KEY,                  
    routing_key   VARCHAR(100)  NOT NULL,
    processed_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);