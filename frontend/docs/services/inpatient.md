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

No Inpatient feature or route exists yet. The backend Core V1 endpoints are implemented, and
Gateway routes `/api/v1/inpatient/**` to `lb://inpatient-service` with endpoint-specific role rules.
Cross-service RabbitMQ flags remain disabled; frontend availability does not activate Billing,
Surgery, Pharmacy or Report integrations.

## Owner queue

- `FE-INPATIENT-01` — `IMPLEMENT`: add typed admission search and detail using the live
  `GET /v1/inpatient/admissions` and `GET /v1/inpatient/admissions/{id}` contracts. Preserve exact
  status, patient/department/referral IDs and empty/error/retry states without cross-feature joins.
- `FE-INPATIENT-02` — `IMPLEMENT`: add the bed list from `GET /v1/inpatient/beds`, keeping bed state
  separate from an admission's historical initial-bed snapshot.
- `FE-INPATIENT-03` — `VERIFY-CONTRACT`: add create/assign/transfer/release/treatment/discharge
  commands one bounded transition at a time, with exact roles, validation and conflict handling.
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
