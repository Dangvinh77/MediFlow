# HANDOFF-PHARMACY-PAYMENT-IDEMPOTENCY-RACE

- **Status:** `IMPLEMENTATION_REQUIRED`
- **Owner:** Huy (`LQHuy0210`) — `backend/pharmacy-service/**`
- **Trigger contract:** Billing publishes `payment.completed`; Pharmacy consumes it
- **Related contract:**
  [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- **Discovered by:** service integration workflow after the care-finance documentation alignment

## Problem

`PaymentReceiptRepositoryPort.claim(...)` distinguishes a new receipt from a duplicate, but
`PaymentApplicationService` intentionally resumes every non-terminal `DUPLICATE_SAME` receipt.
Two concurrent deliveries can therefore both pass the terminal check while the receipt is still
`RECEIVED` and both enter `dispenseWithPaymentProof(...)`.

The stock transaction currently protects the final stock/dispense effect with pessimistic locks and
the terminal dispense-slip state. That is a valid effect-idempotency boundary, but the unit test
`onPaymentCompleted_sameEventConcurrent_dispensesOnce` asserts that the orchestration method itself
is called once and returns the same mutable `PaymentReceipt` instance to both threads. Depending on
timing, it fails because the method is called twice or because both threads transition that shared
test object from `DISPENSED` to `DISPENSED`.

This is not a Billing payload mismatch. Do not change the `payment.completed` event or ask Billing
to suppress redelivery; at-least-once delivery is part of the contract.

## Required decision inside Pharmacy

Choose and document one durable concurrency policy:

1. **Effect-idempotent processing:** concurrent handlers may enter the use case, while the locked
   dispense transaction, receipt transition, compensation and outbox guarantee one observable
   outcome; or
2. **Single active owner:** extend the receipt lifecycle with an atomic processing owner/lease and
   a stale-owner recovery rule so only one handler enters dispense while a later retry can resume.

Do not solve this with an in-memory lock, a static event map, `synchronized`, or a check-then-save
sequence. Those approaches do not coordinate multiple service replicas.

## Required verification

Use PostgreSQL/Testcontainers and independent aggregate instances/transactions. A deterministic
concurrency test must prove all of the following:

- two simultaneous deliveries of the same event complete without an unhandled exception;
- stock is decremented once and one dispense slip reaches `DISPENSED`;
- exactly one `prescription.filled` outbox event is created;
- one payment receipt reaches the intended terminal state;
- a duplicate with the same payload after a transient failure can resume safely;
- a duplicate `eventId` with a different payload is rejected;
- the failure path emits compensation at most once;
- the existing Billing event fixture remains byte-compatible.

The unit test may verify orchestration decisions, but it must not model database concurrency by
sharing one mutable domain object between worker threads. Run the focused concurrency test repeatedly
and then run `mvn -q -pl backend/pharmacy-service -am test`.

## Completion handoff

When Pharmacy implements the selected policy:

1. update this file to `IMPLEMENTED` and name the enforcing test;
2. update the Pharmacy service document if the receipt lifecycle changes;
3. keep `CONTRACT-CARE-BILLING-01` unchanged unless the wire payload truly changes; and
4. notify Billing only if a versioned producer contract change is actually required.
