# Inpatient Create Command — Design

**Date:** 2026-10-09  
**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Task:** First bounded implementation slice of `FE-INPATIENT-03`

## Context

Clinical, Lab and Inpatient backend modules are locally complete. Their remaining runtime activation
work depends on Billing and Surgery contracts outside Vinh's writable production scope. The next
unblocked owner task is therefore the Inpatient command frontend. The current Inpatient workspace
supports admission and bed reads only.

The frontend has no automated test harness. The user explicitly authorized the shared frontend
changes needed to add one before implementing the command.

## Approaches considered

### A. Test harness plus one complete vertical slice

Add the shared Vitest/React Testing Library harness, then implement only admission creation from
route to API call and success navigation. This follows the workboard's instruction to add Inpatient
commands one bounded transition at a time.

**Trade-off:** Delivers fewer commands in this slice, but gives the remaining commands a tested
pattern and keeps contract risk small.

### B. Implement every Inpatient command in one batch

Add creation, bed assignment/transfer/release, treatment, discharge, close and cancellation
together.

**Trade-off:** More visible surface immediately, but mixes unrelated roles, state transitions and
finance gates. Failures would be harder to isolate, and close/admit actions have external activation
constraints.

### C. Add the test harness without a user-facing command

Land only test infrastructure and defer production behavior.

**Trade-off:** Lowest feature risk, but it does not advance `FE-INPATIENT-03` for users.

## Decision

Use approach A. It is the smallest useful tracer bullet and becomes the reference pattern for later
Inpatient transitions.

## Scope

### Shared frontend test infrastructure

- Add Vitest with the React plugin, `jsdom`, TypeScript path resolution and React Testing Library.
- Add deterministic `test` and `test:watch` scripts.
- Keep the setup minimal: DOM matchers and automatic cleanup only. Do not introduce a custom render
  abstraction until a test needs shared providers.

### Inpatient feature contract

Mirror `CreateAdmissionRequest` exactly in `features/inpatient/types.ts`:

- `maYeuCauNoiTru`: UUID
- `maHoSoNguon`: UUID
- `maBenhNhan`: UUID
- `maKhoa`: UUID
- `nguoiYeuCau`: UUID
- `tomTatChanDoan`: trimmed, required, maximum 4,000 characters
- `doUuTien`: `ROUTINE | URGENT | EMERGENCY`
- `capCuu`: boolean
- `thoiGianYeuCau`: ISO instant

Add `inpatientApi.createAdmission` using `POST /v1/inpatient/admissions`. The API returns the live
`AdmissionDTO`; no cross-feature lookup or local financial state is added.

### UI and routing

- Add `/inpatient/new`, guarded for `ADMIN` and `DOCTOR`.
- Add a feature-local `CreateAdmissionForm` and pure form validation/mapping helpers.
- Add a create link to the admission workspace only for the same roles.
- Ask the user for `nguoiYeuCau` explicitly. The current session contract exposes role and tokens but
  not a canonical staff identifier; decoding or inferring one in the browser would create an
  undocumented identity contract. The backend remains authoritative and rejects impersonation.
- On success, replace navigation with `/inpatient/{maDotNoiTru}?notice=created`.
- On `401`, redirect to login. Keep `403`, validation, conflict and business-rule errors in context,
  including the correlation ID. Retry only transport/5xx failures.

## Data flow

1. The route role gate renders the form for `ADMIN` or `DOCTOR`.
2. Client validation rejects malformed UUIDs, empty/oversized diagnosis summaries and invalid
   request timestamps without an HTTP call.
3. Mapping trims text and converts the local datetime input to an ISO instant.
4. `inpatientApi.createAdmission` sends the exact request through the shared gateway wrapper.
5. The backend validates the actor against the JWT, creates the admission and returns
   `AdmissionDTO`.
6. The UI navigates to the created admission detail and shows a creation notice.

## Testing

Follow red-green-refactor:

1. Harness smoke test proves aliases and `jsdom` work.
2. Form helper tests cover valid mapping, every UUID field, required/length validation and datetime
   conversion.
3. API test verifies the exact method, path and request payload through a mocked shared API boundary.
4. Component tests cover role-independent form behavior, validation preventing submission, success
   navigation, server field/business errors, correlation display, retry eligibility and `401`
   redirect.
5. Run focused tests, then full frontend tests, typecheck, lint and production build.

## Out of scope

- Admission activation, deposit clearance, settlement or Surgery composition.
- Bed assignment, transfer, release, admit, treatment, discharge, close and cancel commands; these
  become separate follow-up slices using this pattern.
- Patient, staff or department selector endpoints.
- JWT claim decoding or changes to backend identity contracts.
- Backend or Gateway production changes.

## Completion criteria

- The test harness runs in CI-friendly non-watch mode.
- `ADMIN` and `DOCTOR` can submit the exact admission request contract through the Gateway.
- Invalid forms make no request and focus the first invalid field.
- Success opens the canonical admission detail route.
- Authentication, authorization, validation, conflict, business and retryable failures are distinct.
- Focused tests, the complete frontend test suite, typecheck, lint and build pass.

