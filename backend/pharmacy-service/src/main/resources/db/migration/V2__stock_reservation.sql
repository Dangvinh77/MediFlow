-- Pharmacy: giữ chỗ tồn kho (stock reservation).

-- Kê đơn = "hứa" sẽ có thuốc: tăng dự trữ ngay lúc kê, để bác sĩ/bệnh nhân THẤY
-- lượng thuốc thật có thể bán TRƯỚC khi thanh toán. Chỉ trừ kho thật khi xuất (dispense).
--
-- Số tồn "có thể bán" của một thuốc = stock_quantity - SUM(quantity) của các dòng
-- STOCK_RESERVATION đang ở trạng thái RESERVED.
--
-- Vòng đời một giữ chỗ:
--   RESERVED   --đã thanh toán + xuất thành công--> FULFILLED   (hết)
--   RESERVED   --không thanh toán / hủy / hết hạn--> RELEASED / EXPIRED  (trả lại chỗ)
--
-- Không phá bảng DRUG: STOCK_RESERVATION là bảng riêng, 1 đơn = nhiều dòng giữ chỗ.

CREATE TABLE STOCK_RESERVATION (
    reservation_id   UUID         PRIMARY KEY,
    drug_id          UUID         NOT NULL REFERENCES DRUG(drug_id),
    prescription_id  UUID         NOT NULL REFERENCES PRESCRIPTION(prescription_id),
    quantity         INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',   -- RESERVED / FULFILLED / RELEASED / EXPIRED
    expires_at       TIMESTAMPTZ,                                 -- hết hạn giữ chỗ (policy TTL 24h)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT ck_reservation_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_reservation_drug          ON STOCK_RESERVATION (drug_id);
CREATE INDEX idx_reservation_prescription  ON STOCK_RESERVATION (prescription_id);
CREATE INDEX idx_reservation_status_expiry ON STOCK_RESERVATION (status, expires_at);  -- job release TTL
