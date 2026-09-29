# Frontend Service: Patient

**Owner:** Hoàng Anh (`TranHoangAnh94`)  
**Backend:** `backend/patient-service/**`  
**Contract context:** [`docs/ai/services/patient.md`](../../../docs/ai/services/patient.md)

## Writable frontend scope

- `frontend/src/features/patient/**`
- `frontend/src/app/(dashboard)/patients/**`

## Current UI baseline

`/patients` contains a paged keyword list UI and is gated to `ADMIN`, `DOCTOR`, and `NURSE`. Its
TypeScript contract now has a live Patient read/list controller and response DTO to verify against.
The service-only existence lookup is also implemented and is consumed by Clinical; it is not a
frontend endpoint.

## Owner queue

- `FE-PATIENT-01` — `BLOCKED`: implement and verify the backend Patient controller and DTO first.
- `FE-PATIENT-02` — after that contract lands, replace spec-backed frontend fields with an exact
  live DTO mapping and verify the list/search route.
- `FE-PATIENT-03` — `VERIFY-CONTRACT`: add detail, then one create/update/delete slice with exact
  roles and validation from live source.
- `FE-PATIENT-04` — `IMPLEMENT`: add contract/component tests after the shared harness exists.

## Handoffs and blockers

- Producer: Patient / Hoàng Anh. Consumers: Patient UI, Clinical, Lab, Billing, Notification.
  Acceptance: secured lookup/search responses and stable `patientId` semantics.
- Reconcile the current frontend `PatientDTO` and paging assumptions with the live Patient
  controller fixture, then verify populated, empty, unavailable and retry states through Gateway.
- Patient self-service and “my notifications” require a documented patient identity mapping from
  Gateway and Patient. Do not decode or infer an undocumented token claim.
- Do not add mutations, fake data or direct database access before the live write contract exists.

## Contract and verification gate

The live controller and DTO override the design document once implemented. Reconcile naming in one
PR before adding workflows.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Patient tests when available. Full
functional smoke testing remains blocked until the Patient backend is healthy through the gateway.
