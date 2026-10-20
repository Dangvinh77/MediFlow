# Frontend Service: Clinical

## Accepted closeout — 2026-10-20

This checkpoint supersedes the runtime/test gaps below. Appointment, record and Lab-order workspaces
reset on resource, role and bearer changes; late replies cannot expose old content or redirect a new
session. Shared command guards prevent duplicate sends and blind retries after ambiguous replies.
Regression tests cover check-in/start eligibility, exact record lookup, source identity and immutable
completion. Signed browser acceptance created a Clinical Lab order, submitted its unchanged source
context to Lab, added a diagnosis and completed the outpatient record. Appointment detail then showed
COMPLETED and linked the exact record. Initial appointment check-in/start were exercised by the
packaged API harness, not manually through the browser in this run.

`FE-CLINICAL-04` is DONE for the implemented MVP regression/runtime scope. `FE-CLINICAL-05` is DONE
for the conditional command UI and matched opt-in runtime; production rollout remains default-off.
See [evidence and limits](../evidence/vinh-loc/2026-10-20/README.md). The dated sections below remain
historical; external selector/attachment contract dependencies are not silently closed.


**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Backend:** `backend/clinical-service/**`  
**Contract context:** [`docs/ai/services/clinical.md`](../../../docs/ai/services/clinical.md)

## Writable frontend scope

- `frontend/src/features/appointment/**`
- `frontend/src/features/medical-record/**`
- `frontend/src/app/(dashboard)/appointments/**`
- `frontend/src/app/(dashboard)/records/**`

The matching service owns appointments, medical records, and diagnoses. Patient, staff,
department, Lab, Pharmacy, and Billing data remain external references.

## Current implementation — 2026-10-18

The default-off server UI gate `MEDIFLOW_OUTPATIENT_UI_ENABLED` now controls the outpatient
command UI. The following older baseline descriptions are historical where they describe
commands as not implemented or compatibility-only. See the latest [workboard](../frontend-workboard.md)
and [implementation record](../vinh-outpatient-implementation-2026-10-18.md).

Appointment detail implements confirmed check-in/start and an exact appointment-backed record
lookup. Record detail implements confirmed outpatient completion; the Lab-order route composes
Clinical authority and Lab creation. No emergency override, admission or transfer controls.
Default-off keeps existing compatibility screens; enabled mode replaces legacy arrival and
standalone record creation. Runtime smoke and new behavioral regression tests remain open;
this session validates build only by explicit user instruction.

## Historical UI baseline

- `/appointments`: paged appointment list with department/date filters through
  `GET /v1/appointments`; UI roles `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE`. ADMIN, DOCTOR, and NURSE
  can open `/appointments/{appointmentId}` backed by `GET /v1/appointments/{id}`.
- `/appointments/new`: ADMIN and NURSE can create through `POST /v1/appointments` using UUID
  references. Patient/doctor/department selectors remain blocked on their owner contracts.
- `/appointments/{appointmentId}/edit`: ADMIN, DOCTOR, and NURSE can edit date, time, and reason
  only while the fetched appointment is `PENDING`.
- Appointment detail presents every live status and the available care-finance/examination
  timestamps. Its generic status controls expose only `PENDING -> ARRIVED` and
  `PENDING -> CANCELLED`; care-finance states are display-only here.
- `/records`: explicit patient UUID lookup through `GET /v1/records/patient/{patientId}`; UI roles
  `ADMIN`, `DOCTOR`, `NURSE`.
- `/records/{recordId}`: exact record detail through `GET /v1/records/{id}` with diagnoses,
  status, disposition, completion fields and no cross-feature enrichment.
- `/records/new`: ADMIN/DOCTOR create from the exact Clinical request DTO with one required initial
  diagnosis, UUID/date/length/ICD validation, backend field errors, and 403/409/business-rule states.
- `/records/{recordId}/edit`: ADMIN/DOCTOR update symptoms on an open record; completed records are
  presented as immutable. The detail page also supports adding a diagnosis with an inline success state.
- Both flows use feature-local types/API modules, shared async states, and the gateway API wrapper.
- Check-in, examination start, record completion and admission referral are intentionally not
  exposed while `mediflow.features.care-finance-v2` remains disabled by default in Clinical and
  Docker Compose.

## MVP contract preparation — 2026-10-15

The appointment API facade now supports bodyless PUT check-in and PUT start-exam (normal default:
`{ emergencyOverride: null }`), both returning `AppointmentDTO`. The record facade supports PUT
completion and POST Clinical Lab order creation; its response preserves the server-issued
`sourceOrderId` and all episode/price/reference fields. Request types mirror the live controller
records, including emergency audit fields; no override approval UI is enabled.

Feature-local HTTP tests exercise the real shared API wrapper with mocked transport/session,
covering same-origin Gateway paths, bearer propagation, bodyless check-in, payloads, response
identity, validation details, 403/422 errors and correlation IDs without mutation retries or V0
fallback. These are contract-preparation tests, not authenticated Gateway or integrated acceptance.
V1 command controls remain gated by `FE-VINH-MVP-02/03` and the backend closeout handoffs.

2026-10-17 continuation extends the appointment/record detail regressions with list and form
lifecycle coverage. Appointment pagination/retry preserves submitted filters and rejects invalid
department UUIDs; record lookup rejects malformed patient UUIDs and ignores stale/unmounted
responses. Create/edit and diagnosis mutations use a synchronous duplicate-submit lock, ignore
late responses after unmount, and keep edit state tied to the current appointment/record identity.
Component checks cover backend field errors and correlation IDs, roles, retry, and immutable
completed records. Tests use mocked API/session; full frontend suite 363/363 (45 files),
typecheck/lint/build pass. Authenticated browser smoke remains open. See the dated
[workboard](../frontend-workboard.md).

## Owner queue

- `FE-CLINICAL-01` — `DONE`: appointment detail with UUID validation, role gate, loading,
  not-found, error/retry, and list navigation.
- `FE-CLINICAL-02` — `DONE`: appointment create/update/status vertical slice aligned to the live
  request, response, role, validation, conflict, correlation-ID, and transition contracts.
- `FE-CLINICAL-03` — `DONE`: record detail, create, update and add-diagnosis commands mirror the
  live controller DTOs, roles, validation and error states.
- `FE-CLINICAL-04` — `DONE`: MVP regression and signed runtime acceptance; see latest checkpoint.
- `FE-CLINICAL-05` — `DONE`: gated outpatient controls accepted in matched opt-in runtime; production rollout stays OFF.

## Handoffs and blockers

- Patient existence lookup is implemented and fixture-tested for Clinical's service-only caller.
  Patient search/display selectors still need a separate audit of the human-authorized list/read
  contract; never call the service-only existence endpoint from the browser.
- Doctor and department selectors require Organization contracts. Producer: Organization / Hoàng
  Anh. Do not infer staff/department relationships from UUIDs.
- Lab results and Pharmacy dispense information must arrive through documented Clinical contracts;
  do not import those frontend features or join their responses in a Clinical component.

## Contract and verification gate

Check the current Clinical controller, request/response DTOs, roles, and error codes before changing
the API facade. Preserve bare UUID references and never make a direct service-port request.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Clinical tests when available. Smoke
test both role denial and the changed `/appointments` or `/records` flow.
