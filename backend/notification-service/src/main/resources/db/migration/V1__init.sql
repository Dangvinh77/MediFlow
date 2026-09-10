-- Notification: bản ghi thông báo gửi tới bệnh nhân + sổ chống xử lý trùng.
--
-- Tên bảng/cột tiếng Anh (07-notification.md §1, §3 — chuẩn hoá toàn service, không chỉ nhánh C).
-- UUID PK do Hibernate sinh; thời điểm = TIMESTAMPTZ. ddl-auto = validate: entity phải khớp lược đồ này.

CREATE TABLE NOTIFICATION (
    notification_id   UUID          PRIMARY KEY,
    patient_id        UUID          NOT NULL,       -- tham chiếu logic patient-service, UUID trần
    title             VARCHAR(255)  NOT NULL,
    content           TEXT          NOT NULL,
    channel           VARCHAR(10)   NOT NULL,       -- EMAIL | SMS | IN_APP
    recipient_address VARCHAR(150),                 -- email/sđt thực tế đã dùng; PII, không lộ ra DTO
    status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    failure_reason    VARCHAR(255),
    retry_count       INT           NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at           TIMESTAMPTZ
);
CREATE INDEX idx_notification_patient ON NOTIFICATION (patient_id, created_at DESC);
CREATE INDEX idx_notification_status  ON NOTIFICATION (status);

CREATE TABLE PROCESSED_EVENT (
    event_id     UUID          PRIMARY KEY,         -- eventId của message — KHÔNG auto-gen
    routing_key  VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
