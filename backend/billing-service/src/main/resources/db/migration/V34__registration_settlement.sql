-- One financial outcome per registration revision: an unpaid void, a refund obligation, or a move.
CREATE TABLE outpatient_registration_settlement (
    registration_id UUID NOT NULL REFERENCES outpatient_registration_source(registration_id),
    registration_revision BIGINT NOT NULL CHECK (registration_revision > 0),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('VOID_UNPAID','REFUND_DUE','RESCHEDULED')),
    payment_request_id UUID REFERENCES PAYMENT_REQUEST(payment_request_id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    source_type VARCHAR(16) NOT NULL CHECK (source_type = 'EXAM'),
    source_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    actor_staff_id UUID,
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    previous_session_id UUID,
    new_session_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (registration_id, registration_revision),
    CHECK ((kind = 'RESCHEDULED') = (previous_session_id IS NOT NULL AND new_session_id IS NOT NULL))
);
CREATE INDEX idx_outpatient_registration_settlement_kind
    ON outpatient_registration_settlement(kind, occurred_at DESC, registration_id);
