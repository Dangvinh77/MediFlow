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

## Acceptance criteria

- Billing serializes the canonical EXAM and LAB_TEST fixtures byte-for-byte with the consumers.
- Duplicate delivery has one effect; malformed or mismatched targets follow bounded retry/DLQ.
- Docker proves check-in → charge → payment → EXAM clearance → examination start.
- Docker proves Lab request → charge → payment → LAB_TEST clearance → execution start.
- Compatibility payment handling remains until migration and rollback evidence is recorded.
- Only after these checks may owners enable the affected Care-Finance flags and delete this handoff.
