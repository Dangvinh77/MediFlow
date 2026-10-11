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

**Still open:** EXAM issuance from `appointment.status.changed`/`medicalrecord.created`. Clinical's
`AppointmentStatusChangedV2Payload`/`MedicalRecordCreatedV2Payload` and the `ClinicalCareFinanceApplicationService`
producer code already emit the shared-envelope `sourceType=EXAM`/`sourceId`/`priceCode` fields, but
Clinical has not yet committed a producer JSON fixture for either event (only asserted via in-memory
unit tests). Per the no-copied/no-guessed-wire-format rule, Billing cannot build the EXAM consumer
until that fixture exists — this is not forgotten, it is blocked waiting on Clinical's own fixture
commit. Also still open: the two Docker acceptance flows above, and deleting this handoff (only after
both EXAM and LAB_TEST pass their Docker proofs).

## Acceptance criteria

- Billing serializes the canonical EXAM and LAB_TEST fixtures byte-for-byte with the consumers.
- Duplicate delivery has one effect; malformed or mismatched targets follow bounded retry/DLQ.
- Docker proves check-in → charge → payment → EXAM clearance → examination start.
- Docker proves Lab request → charge → payment → LAB_TEST clearance → execution start.
- Compatibility payment handling remains until migration and rollback evidence is recorded.
- Only after these checks may owners enable the affected Care-Finance flags and delete this handoff.
