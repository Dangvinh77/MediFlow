# HANDOFF — Clinical/Lab financial clearance

**Status:** ACTIVE — held fixture support exists; authoritative request issuance and live publication remain open.
**Owner:** Lộc (`locgit-89`), Billing.
**Unblocks:** Vinh's Clinical EXAM gate and Lab LAB_TEST gate.

## Producer

Billing produces `financial.clearance.granted` version 1 after an authoritative charge request is paid or an approved emergency path is recorded. The durable payload and target rules are defined in [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md).

## Consumer

- Clinical consumes only `purpose=EXAM` for the exact appointment or record and outpatient episode.
- Lab consumes only `purpose=LAB_TEST` with explicit `labTestIds` for the exact episode.

Both consumers already reject mismatched purpose, patient, episode and target IDs, and both feature paths remain disabled.

## Owner actions

1. Create Billing charge requests from accepted `appointment.status.changed`/`medicalrecord.created` and `lab.request.created` facts without double-charging the same source.
2. Reconcile payment to the exact request and publish the version-1 clearance through the outbox.
3. Preserve one clearance per business operation and idempotent replay by event ID.
4. Keep the held producer disabled until the same-byte producer/consumer fixtures pass together.

### LAB_TEST planned-request issuance (2026-10-10)

Opt-in `lab.request.created` issuer added: `LabTestPlannedRequestService` opens/reuses the exact
episode account, posts one `LAB_TEST` charge from the configured price catalog and creates one
`PAYMENT_REQUEST` (target `recordId`/`labTestIds`) per lab test, mirroring the Surgery planned-request
pattern. Gated by `mediflow.billing.ledger.enabled` + `mediflow.billing.lab-test-charge-consumer.enabled`
(both false by default); disabled leaves `lab.request.created` unbound on `billing.q`, unchanged from
before this slice. Exact replay returns the recorded request without re-pricing; a same-source event
with different `sourceOrderId`/bytes conflicts. Clearance granting needs no new code: the existing
generic `LedgerPaymentService` already grants `financial.clearance.granted` for any `PaymentRequestPurpose`,
including `LAB_TEST`, once the request is paid in full.
Verified against the real lab-service producer fixture, copied byte-for-byte into
`backend/billing-service/src/test/resources/contracts/lab-request-v1/lab.request.created.v1.json`
(mirrors `backend/lab-service/src/test/resources/contracts/lab.request.created.v1.json`).
Decoder/service/configuration tests pass; Docker-based E2E (check-in/request → charge → payment →
clearance → execution start) is not run on this machine (Testcontainers cannot reach Docker here —
pre-existing environment issue, unrelated to this code).

### EXAM charge issuance (2026-10-12)

Opt-in `appointment.status.changed` issuer added: `ExamChargePlannedRequestService` opens/reuses the
exact appointment's episode account, posts one `EXAM` charge from the configured price catalog and
creates one `PAYMENT_REQUEST` (target `appointmentId`) for the appointment's own `priceCode`, mirroring
the Lab-test planned-request pattern. Gated by `mediflow.billing.ledger.enabled` +
`mediflow.billing.exam-charge-consumer.enabled` (both false by default); disabled keeps the existing
legacy V0 `onAppointmentStatusChanged` compatibility handler on `appointment.status.changed` unchanged
— that routing key is already unconditionally bound, so no new Binding bean was needed, only a dispatch
switch in `BillingEventConsumer` (exactly one writer, same principle as the discharge-approval path).

Clinical reuses this one routing key for every appointment transition, always with `sourceType=EXAM`;
only the normal check-in transition (`oldStatus=PENDING`, `newStatus=AWAITING_PAYMENT`, `recordId=null`)
is the EXAM charge trigger. `ExamChargeDecoder#decodeApplicable` returns `Optional.empty()` (an
explicit no-op ACK, not a DLQ reject) for every other well-formed transition, the same "valid but not
applicable" shape Surgery already uses for non-SURGERY `financial.clearance.granted` purposes. A
malformed envelope, or a charge-trigger-shaped payload that violates its own invariants (`oldStatus`
not `PENDING`, or a non-null `recordId`), still rejects to DLQ.

Verified against the real clinical-service producer fixture (`appointment.status.changed.v1.json`,
PR #334), copied byte-for-byte into
`backend/billing-service/src/test/resources/contracts/appointment-status-changed-v1/`. Decoder/service/
configuration/consumer tests pass, including the not-applicable and malformed-shape branches; Docker-
based E2E (check-in → charge → payment → clearance → exam start) is not run on this machine
(Testcontainers cannot reach Docker here — pre-existing environment issue, unrelated to this code).
Clearance granting needs no new code: the existing generic `LedgerPaymentService` already grants
`financial.clearance.granted` for `EXAM` once the request is paid in full, same as LAB_TEST.

**Out of scope, deliberately not touched:** `medicalrecord.created` is NOT the EXAM charge trigger —
Clinical's own design doc (`docs/superpowers/specs/2026-10-10-vinh-loc-care-finance-contract-closure-design.md`)
states the record is only created after clearance already permits examination to start, so Billing's
legacy `onMedicalRecordCreated` V0 compatibility handler on that routing key is left exactly as-is.
Clinical's V2 `MedicalRecordCreatedV2Payload` can already be emitted (when `care-finance-v2` is on)
wrapped in the same V1 envelope shape that the legacy flat `MedicalRecordCreatedEvent` record does not
expect; whether that combination is safe today is a pre-existing question that predates this task
(the V2 payload class existed before this slice) and is not something this EXAM-issuance change
introduces, changes, or closes. Flagging it here rather than silently expanding scope to Clinical's
`medicalrecord.created` routing.

**Still open:** the Docker acceptance flows for both EXAM and LAB_TEST, and deleting this handoff
(only after both pass their Docker proofs).

## Acceptance criteria

- Billing serializes the canonical EXAM and LAB_TEST fixtures byte-for-byte with the consumers.
- Duplicate delivery has one effect; malformed or mismatched targets follow bounded retry/DLQ.
- Docker proves check-in → charge → payment → EXAM clearance → examination start.
- Docker proves Lab request → charge → payment → LAB_TEST clearance → execution start.
- Compatibility payment handling remains until migration and rollback evidence is recorded.
- Only after these checks may owners enable the affected Care-Finance flags and delete this handoff.
