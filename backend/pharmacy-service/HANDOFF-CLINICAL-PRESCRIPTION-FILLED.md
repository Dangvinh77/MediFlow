# HANDOFF — `prescription.filled` record correlation for Clinical

> **Mandatory for coding agents:** read this file before changing `PrescriptionFilledEvent`, the
> dispense flow, or `prescription.filled` contract fixtures in `pharmacy-service`.
>
> **Status (2026-09-18): complete on both sides.** Pharmacy publishes the persisted prescription's
> `recordId`; Clinical consumes the event through its single queue dispatcher, claims `eventId`
> transactionally, and stores an idempotent `PRESCRIPTION` attachment keyed by
> `(recordId, prescriptionId)`.

- **Producer / owner:** Pharmacy — LQHuy0210
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked Clinical work:** attach a dispensed prescription to its medical record

## Historical gap

Pharmacy stores the prescription's authoritative `recordId`, but `PrescriptionFilledEvent` publishes
only `prescriptionId`, patient, department, amount, and item data. Clinical cannot safely infer the
record from patient or department and therefore cannot enable this consumer.

## Required contract

Add the producer-sourced `recordId: UUID` to `prescription.filled`. Keep the existing envelope fields
and `prescriptionId`. The value must come from the persisted prescription being dispensed.

## Acceptance criteria

- Publisher payload and fixtures include the prescription's exact `recordId`.
- Producer tests prove `recordId` is preserved through outbox serialization and dispatch.
- Existing consumers tolerate the additive field.
- Notify the Clinical owner when merged so it can add an idempotent consumer and attachment.
