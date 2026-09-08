-- Billing: viện phí + hóa đơn + sổ chống xử lý trùng + bảng chiếu loại xét nghiệm.
--
-- Bảng/cột dùng tiếng Anh theo thống nhất riêng cho nhánh C (pharmacy/billing/report) —
-- xem backend-spec/06-billing.md §1. Phần còn lại của hệ thống vẫn dùng tiếng Việt snake_case.
--
-- UUID PK do Hibernate sinh; tiền = DECIMAL(15,2); ngày = DATE / TIMESTAMPTZ.
-- ddl-auto = validate: mọi @Column của entity phải khớp đúng lược đồ này.

CREATE TABLE FEE (
    fee_id         UUID          PRIMARY KEY,
    patient_id     UUID          NOT NULL,        -- ref patient-service, UUID trần
    record_id      UUID,                          -- ref clinical-service HO_SO_BA, nullable
    department_id  UUID          NOT NULL,        -- ref organization-service KHOA (BR-B8)
    source_ref_id  UUID,                          -- lab test / prescription sinh ra khoản phí này
    fee_type       VARCHAR(10)   NOT NULL,        -- EXAM | LAB | DRUG | SERVICE
    incurred_date  DATE          NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    is_paid        BOOLEAN       NOT NULL DEFAULT false,
    invoice_id     UUID,                          -- gán khi khoản phí được gộp vào một hóa đơn
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT ck_fee_amount_non_negative CHECK (amount >= 0)
);
CREATE INDEX idx_fee_patient  ON FEE (patient_id, is_paid);
CREATE INDEX idx_fee_dept     ON FEE (department_id, incurred_date);
CREATE INDEX idx_fee_invoice  ON FEE (invoice_id);
-- mỗi event nguồn chỉ sinh đúng một khoản phí: tuyến phòng thủ cuối cho BR-B7,
-- an toàn ngay cả khi hai message đến tương tranh. Giữ nguyên dạng partial (WHERE ... IS NOT NULL):
-- ràng buộc UNIQUE thường sẽ chặn nhiều dòng source_ref_id = NULL trên một số engine.
CREATE UNIQUE INDEX uq_fee_source ON FEE (fee_type, source_ref_id)
    WHERE source_ref_id IS NOT NULL;

CREATE TABLE INVOICE (
    invoice_id      UUID          PRIMARY KEY,
    patient_id      UUID          NOT NULL,
    created_date    DATE          NOT NULL,
    total_amount    DECIMAL(15,2) NOT NULL,       -- luôn tính từ danh sách phí (BR-B2), không nhận từ request
    is_paid         BOOLEAN       NOT NULL DEFAULT false,
    payment_method  VARCHAR(20),                  -- CASH | TRANSFER | INSURANCE
    dispense_id     UUID,                          -- ref pharmacy-service DISPENSE_SLIP, gán khi saga COMPLETED
    prescription_id UUID,                          -- đơn thuốc mở saga (nếu có)
    saga_status     VARCHAR(20)   NOT NULL DEFAULT 'NONE',  -- NONE|AWAITING_PAYMENT|PAID|AWAITING_DISPENSE|COMPLETED|REFUNDED
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    CONSTRAINT ck_invoice_total_non_negative CHECK (total_amount >= 0)
);
CREATE INDEX idx_invoice_patient ON INVOICE (patient_id);
-- mỗi đơn thuốc tối đa một hóa đơn (BR-B6) — cũng dạng partial vì prescription_id nullable.
CREATE UNIQUE INDEX uq_invoice_prescription ON INVOICE (prescription_id)
    WHERE prescription_id IS NOT NULL;

CREATE TABLE PROCESSED_EVENT (
    event_id     UUID          PRIMARY KEY,       -- eventId của message — KHÔNG auto-gen
    routing_key  VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Bảng chiếu local (labId -> labType). Vì sao cần: billing sinh phí LAB với số tiền
-- priceList.labFee(labType) (06-billing.md §7) nhưng event lab.result.created KHÔNG mang labType
-- (04-lab.md §8) — trường đó chỉ có ở lab.request.created. Consumer lab.request.created (Phần 5/5)
-- ghi vào bảng này; FeeAccrualService tra ở đây. KHÔNG gọi REST đồng bộ sang lab-service.
-- Xem application/port/out/LabTestTypePort.java và THELOC-INTEGRATION-FOLLOWUP.md mục 2.
CREATE TABLE LAB_TEST_TYPE (
    lab_id      UUID          PRIMARY KEY,
    lab_type    VARCHAR(100)  NOT NULL,
    recorded_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
