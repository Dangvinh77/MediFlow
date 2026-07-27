-- Patient service schema. Vietnamese snake_case per docs/ai/08-persistence-naming.md.

CREATE TABLE BENH_NHAN (
    ma_benh_nhan   UUID          PRIMARY KEY,
    ho_ten         VARCHAR(100)  NOT NULL,
    ngay_sinh      DATE          NOT NULL,
    gioi_tinh      VARCHAR(1),
    so_cmnd        VARCHAR(20)   NOT NULL,
    dia_chi        VARCHAR(255),
    so_dien_thoai  VARCHAR(15),
    email          VARCHAR(100),
    bhyt_so        VARCHAR(20),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_benh_nhan_so_cmnd UNIQUE (so_cmnd)
);

CREATE INDEX idx_benh_nhan_ho_ten ON BENH_NHAN (ho_ten);
