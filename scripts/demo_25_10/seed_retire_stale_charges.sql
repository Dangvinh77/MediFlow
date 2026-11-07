-- Retires the snapshot's open charges, which can never be paid: their quotes were not
-- exported, so the cashier only ever sees "Billing chua cap bao gia". The queue is filled by
-- prepare_cashier_demo.py through the public APIs instead. Exact ids: this matches the
-- 2026-10-25 snapshot only and leaves every paid or refunded movement in place.
\set ON_ERROR_STOP on

\connect mediflow_billing
UPDATE payment_request SET status = 'EXPIRED'
 WHERE status IN ('PENDING', 'PARTIALLY_PAID') AND created_at < TIMESTAMPTZ '2026-10-25';

\connect mediflow_lab
UPDATE lab_test
   SET status = 'CANCELLED', cancelled_at = now(), cancelled_by = '00000000-0000-4000-8000-0000000000de',
       cancellation_reason = 'Du lieu snapshot khong co bao gia', updated_at = now()
 WHERE test_id IN ('46b39529-0f90-4a84-a914-5b3b7fe39c45', 'b48a2469-9d26-453d-9bcf-e66be5a80acb')
   AND status = 'AWAITING_PAYMENT';

\connect mediflow_pharmacy
UPDATE prescription
   SET status = 'EXPIRED', updated_at = now()
 WHERE prescription_id IN ('b801b039-64ed-4e02-9178-2a9d4f9e21c8', '7568382c-58c4-4592-88d2-80e88174b86e')
   AND status = 'ACTIVE';
