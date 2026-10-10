# Frontend Service: Inpatient

**Owner:** Vinh (`Dangvinh77` / `Harori`)
**Backend:** `backend/inpatient-service/**`
**Contract context:** [`docs/ai/services/inpatient.md`](../../../docs/ai/services/inpatient.md)

## Writable frontend scope

- `frontend/src/features/inpatient/**`
- `frontend/src/app/(dashboard)/inpatient/**`

Changes to the shared dashboard navigation, auth/session, common components or `src/lib/**` still
require an explicit shared frontend assignment.

## Current UI baseline

`/inpatient` provides admission filters and paging, `/inpatient/new` creates an admission request,
`/inpatient/{admissionId}` shows the exact admission projection and external-order references, and
`/inpatient/beds` provides bed filters and paging. Gateway roles mirror the live controller. Only
the admission-create transition is writable; the UI does not activate Billing, Surgery, Pharmacy
or Report integrations.

## Owner queue

- `FE-INPATIENT-01` — `DONE`: typed admission search/detail preserve exact status and bare UUIDs.
- `FE-INPATIENT-02` — `DONE`: bed list keeps current bed state separate from admission history.
- `FE-INPATIENT-03` — `IN PROGRESS`: admission create is delivered for `ADMIN` and `DOCTOR`, with
  exact Vietnamese wire names, client/server validation, conflict rendering and correlation IDs.
  Assign/transfer/release/treatment/discharge commands remain queued as separate bounded slices.
- `FE-INPATIENT-04` — `BLOCKED`: deposit, top-up, settlement and Surgery-facing UI wait for their
  producer/consumer handoffs and same-byte fixtures. Do not derive financial clearance locally.

## Handoffs and blockers

- Billing / Lộc must publish canonical ADMISSION_DEPOSIT clearance, top-up and settlement facts.
- Surgery / Huy and Vinh must approve reference registration plus event-first, outpatient and late
  outcome behavior before a Surgery timeline is shown as integrated.
- Bed transfer/release/capacity events are not approved. The read UI may show current Inpatient
  state from its own endpoint but must not advertise that state as a Report/Pharmacy projection.

## Contract and verification gate

Confirm the live Inpatient controller DTOs, roles, error codes and pagination contract before each
slice. Use the Gateway through `src/lib/api.ts`; never call port `8090` directly.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Inpatient tests when available. Smoke
test an allowed and denied role through the real Gateway route.
