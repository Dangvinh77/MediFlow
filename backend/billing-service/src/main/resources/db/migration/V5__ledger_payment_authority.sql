-- No inferred IDs or prices. Request targets are written only by an authoritative request issuer.
CREATE TABLE PAYMENT_REQUEST_TARGET (
    payment_request_id UUID PRIMARY KEY REFERENCES PAYMENT_REQUEST(payment_request_id),
    appointment_id UUID,
    record_id UUID,
    lab_test_ids UUID[] NOT NULL DEFAULT '{}',
    prescription_id UUID,
    admission_id UUID,
    surgery_case_id UUID
);

-- Keep historical transactions nullable; every new command stores the authenticated actor.
ALTER TABLE PAYMENT_TRANSACTION ADD COLUMN actor_account_id UUID;

-- Reuse the reliable outbox, but never let a V1 payload reach flat V0 consumers by accident.
-- Existing producers retain their current publication behavior; V1 writers explicitly hold rows.
ALTER TABLE BILLING_EVENT_OUTBOX ADD COLUMN publication_enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_billing_outbox_release ON BILLING_EVENT_OUTBOX(publication_enabled, available_at)
    WHERE published_at IS NULL AND quarantined_at IS NULL;
