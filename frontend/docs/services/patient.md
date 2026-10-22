# Frontend Service: Patient

**Owner:** Hoàng Anh (`TranHoangAnh94`)  
**Backend:** `backend/core-service/src/main/java/com/mediflow/patient/**`
**Contract context:** [`docs/ai/services/patient.md`](../../../docs/ai/services/patient.md)

## Writable frontend scope

- `frontend/src/features/patient/**`
- `frontend/src/app/(dashboard)/patients/**`

## Current UI baseline

`/patients` contains paged search and detail. `ADMIN`/`NURSE` can create and update patients;
`ADMIN` can delete after explicit confirmation. ADMIN/NURSE can correct `soCmnd` on the existing
patient UUID; conflicts preserve the draft for correction. The service-only existence lookup
remains unavailable to browser code.

## Owner queue

- `FE-PATIENT-01` — `DONE`: live controller/DTO verified.
- `FE-PATIENT-02` — `DONE`: exact Vietnamese wire mapping and list/search route.
- `FE-PATIENT-03` — `DONE`: detail/create/update/delete with controller roles and validation.
- `FE-PATIENT-04` — `DONE` for local API/logic/component regression coverage; signed Gateway acceptance
  remains open. Tests exercise create/update payloads, editable identity, lookup retry, failed-save
  retention, role checks, admin-only delete visibility, confirmation/cancel, and rejected-delete feedback.

## Logic checkpoint — 2026-10-20

Hoàng Anh / IMPLEMENT. Source changes stay in `src/features/patient/**` with this guide and
the Patient workboard entry. No new screen, layout, stylesheet or shared transport/auth change.
Existing markup consumes the completed logic; no new UI design is claimed.

- `logic.ts`: headless create/update mapping, nullable optional fields, date/length/format validation
  matching the live DTO/domain, staff role matrix, field/error-code/correlation mapping and unknown
  mutation outcome classification. Dates use calendar values, not UTC conversion of the current day.
- `api.ts`: all five public operations use shared Gateway transport. Search normalizes zero-based
  page/default 20/max 100 size; request serializers whitelist exact fields including optional
  `soCmnd` corrections and strip server fields from updates at runtime. No browser `/exists` call.
- `usePatientForm.ts`: create/edit loading, explicit read retry, validation before HTTP, editable
  identity number, role check, preserved input on rejection and navigation using the returned patient UUID.
- `usePatientMutation.ts`: synchronous duplicate lock, terminal success, ignored completion after
  unmount/session replacement. Network/408/5xx/unreadable success leaves an unknown outcome and
  blocks replay; dismissing feedback does not unlock it. Error and correlation evidence remain in
  `uncertainError`. Start a new intent only after external reconciliation; no automatic resend.
- Form/detail workspaces remount per patient/session; list drops late request results/errors after
  unmount and session replacement. Delete requires ADMIN plus explicit confirmation, drops old
  confirmation on patient change, and uses fresh GET for error retry rather than repeating DELETE.
- Hook consumers must mount one workspace per patient/session (as the existing components do).
  State is memory-only; no patient data is written into localStorage or query strings.

Verification after identity correction: focused Patient **58/58 tests in 6 files**;
typecheck, lint and production build **PASS**.
These are local mocks/headless/component checks, not signed Gateway/browser/packaged acceptance.
See the dated workboard entry for the final build/full-regression result.

Identity correction contract: existing PUT accepts optional `soCmnd`; missing/null retains the
stored number for older clients. The current FE sends a trimmed, nonblank value up to 20 characters
for create and edit. UUID/creation time stay unchanged. Duplicate identity returns the existing
409/code and leaves the draft editable. No new self-service permission or audit-history endpoint
is introduced. Docker retest 2026-10-21: full Core **194/194**, zero failures/errors/skips,
including all three Patient PostgreSQL/RabbitMQ integration tests. Correction preserves UUID and
creation time; duplicate correction is rejected; concurrent assignment of one number to two
patients allows exactly one success and preserves the losing patient's old identity.

Shared dependency resolved on master `63b5f233` (synchronized 2026-10-21): `src/lib/api.ts`
clears a session on HTTP 401 only when the request bearer is still current. Patient continues to
ignore stale local callbacks. Post-update Patient + shared API regression: **71/71 / 7 files**.
Earlier handoff/checkpoint references describe the dependency before the shared owner fix.

## Handoffs and blockers

- Producer: Patient / Hoàng Anh. Consumers: Patient UI, Clinical, Lab, Billing, Notification.
  Acceptance: secured lookup/search responses and stable `patientId` semantics.
- `PatientDTO`, request fields and paging constraints were reconciled with the live controller.
  Verify populated, empty, unavailable and retry states through signed Gateway acceptance.
- Existing GET `/api/v1/patients/{id}` also supports PATIENT self-read from 2026-10-22.
  Core compares the locally verified `patientId` claim with the resource UUID and the use case
  checks ownership before repository access. PATIENT cannot search, edit, delete or read another
  patient's profile. No new endpoint or wire field; responses use `Cache-Control: no-store`.
  The app composes `PersonalPatientProfile` at `/organization/profile`, with only the existing
  detail read and a read-only view. Session replacement clears the profile and late callbacks
  are ignored. Do not infer further self-service routes or cross-import features.

## Contract and verification gate

The live controller and DTO override the design document once implemented. Recheck their wire
fixtures before adding workflows.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Patient tests when available. Full
functional smoke testing requires a healthy Patient backend through the gateway and is not claimed
by the local logic checkpoint.
