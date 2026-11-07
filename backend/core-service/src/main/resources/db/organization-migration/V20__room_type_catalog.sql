-- Room types become an administrator-managed catalogue. The three department categories
-- (clinical, paraclinical, administrative) stay fixed because scheduling, head appointment and
-- other contexts depend on them; each room type belongs to exactly one category.
-- CONSULTATION is system-defined: Clinical hosts sessions only in rooms of that code.

CREATE TABLE room_type_catalog (
    code                  VARCHAR(40)  PRIMARY KEY,
    type_name             VARCHAR(100) NOT NULL,
    description           VARCHAR(255),
    department_type       VARCHAR(20)  NOT NULL,
    single_per_department BOOLEAN      NOT NULL,
    system_defined        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order            INTEGER      NOT NULL DEFAULT 100,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    CONSTRAINT ck_room_type_catalog_department_type
        CHECK (department_type IN ('CLINICAL', 'PARACLINICAL', 'ADMINISTRATIVE')),
    CONSTRAINT ck_room_type_catalog_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$')
);

CREATE UNIQUE INDEX uk_room_type_catalog_name ON room_type_catalog (lower(type_name));

INSERT INTO room_type_catalog (code, type_name, description, department_type, single_per_department, system_defined, sort_order) VALUES
    ('CONSULTATION', 'Phòng khám', 'Buồng khám phân bổ cho khoa lâm sàng; nơi duy nhất được lập buổi khám.', 'CLINICAL', FALSE, TRUE, 10),
    ('DEPARTMENT_OFFICE', 'Văn phòng khoa (giao ban)', 'Nơi làm việc chung, giao ban của khoa; không lập buổi khám.', 'CLINICAL', TRUE, FALSE, 20),
    ('LABORATORY', 'Phòng lấy mẫu & xét nghiệm', 'Nơi làm việc của Khoa Xét nghiệm.', 'PARACLINICAL', TRUE, FALSE, 30),
    ('PHARMACY', 'Quầy cấp phát thuốc', 'Nơi cấp phát thuốc ngoại trú của Khoa Dược.', 'PARACLINICAL', TRUE, FALSE, 40),
    ('CASHIER', 'Quầy thu viện phí', 'Nơi thu viện phí của Phòng Tài chính kế toán.', 'ADMINISTRATIVE', TRUE, FALSE, 50),
    ('ADMINISTRATION', 'Phòng hành chính quản trị', 'Nơi làm việc của Phòng Hành chính quản trị.', 'ADMINISTRATIVE', TRUE, FALSE, 60);

-- Nurses work inside clinical departments, so the nursing workplace type is retired; an existing
-- nursing unit keeps its single workplace as an administrative office.
UPDATE room SET room_type = 'ADMINISTRATION', updated_at = now() WHERE room_type = 'NURSING';

ALTER TABLE room DROP CONSTRAINT ck_room_type;
ALTER TABLE room ADD CONSTRAINT fk_room_room_type
    FOREIGN KEY (room_type) REFERENCES room_type_catalog (code);
