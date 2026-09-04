-- Pharmacy: bổ sung vòng đời đơn thuốc và audit giải phóng giữ chỗ.

ALTER TABLE PRESCRIPTION
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by UUID,
    ADD COLUMN cancellation_reason VARCHAR(500);

UPDATE PRESCRIPTION p
SET status = 'FULFILLED'
FROM DISPENSE_SLIP d
WHERE d.prescription_id = p.prescription_id
  AND d.status = 'DISPENSED';

UPDATE PRESCRIPTION p
SET status = 'DISPENSE_FAILED'
FROM DISPENSE_SLIP d
WHERE d.prescription_id = p.prescription_id
  AND d.status = 'FAILED';

ALTER TABLE PRESCRIPTION
    ADD CONSTRAINT ck_prescription_status
        CHECK (status IN ('ACTIVE', 'FULFILLED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED')),
    ADD CONSTRAINT ck_prescription_cancellation_audit
        CHECK (
            status <> 'CANCELLED'
            OR (
                cancelled_at IS NOT NULL
                AND cancelled_by IS NOT NULL
                AND cancellation_reason IS NOT NULL
                AND length(trim(cancellation_reason)) > 0
            )
        );

CREATE INDEX idx_prescription_status ON PRESCRIPTION (status);

UPDATE STOCK_RESERVATION
SET expires_at = created_at + INTERVAL '24 hours'
WHERE expires_at IS NULL;

ALTER TABLE STOCK_RESERVATION
    ALTER COLUMN expires_at SET NOT NULL,
    ADD COLUMN release_reason VARCHAR(50),
    ADD COLUMN released_at TIMESTAMPTZ,
    ADD COLUMN released_by UUID;

UPDATE STOCK_RESERVATION
SET release_reason = 'TTL_EXPIRED',
    released_at = COALESCE(updated_at, expires_at, created_at)
WHERE status = 'EXPIRED';

UPDATE STOCK_RESERVATION
SET release_reason = 'LEGACY_MIGRATION',
    released_at = COALESCE(updated_at, created_at)
WHERE status = 'RELEASED';

ALTER TABLE STOCK_RESERVATION
    ADD CONSTRAINT ck_reservation_status
        CHECK (status IN ('RESERVED', 'FULFILLED', 'RELEASED', 'EXPIRED')),
    ADD CONSTRAINT ck_reservation_release_reason
        CHECK (
            release_reason IS NULL
            OR release_reason IN (
                'PRESCRIPTION_CANCELLED',
                'PAYMENT_FAILED',
                'DISPENSE_FAILED',
                'ADMIN_OVERRIDE',
                'TTL_EXPIRED',
                'LEGACY_MIGRATION'
            )
        ),
    ADD CONSTRAINT ck_reservation_release_audit
        CHECK (
            (
                status IN ('RELEASED', 'EXPIRED')
                AND release_reason IS NOT NULL
                AND released_at IS NOT NULL
            )
            OR (
                status IN ('RESERVED', 'FULFILLED')
                AND release_reason IS NULL
                AND released_at IS NULL
                AND released_by IS NULL
            )
        );

ALTER TABLE DISPENSE_SLIP
    ALTER COLUMN failure_reason TYPE VARCHAR(500),
    ADD CONSTRAINT ck_dispense_status
        CHECK (status IN ('PENDING', 'DISPENSED', 'FAILED', 'CANCELLED', 'EXPIRED'));
