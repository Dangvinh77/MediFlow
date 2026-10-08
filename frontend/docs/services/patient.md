# Frontend Service: Patient

**Owner:** Hoàng Anh (`TranHoangAnh94`)  
**Backend:** `backend/patient-service/**`  
**Contract context:** [`docs/ai/services/patient.md`](../../../docs/ai/services/patient.md)

## Writable frontend scope

- `frontend/src/features/patient/**`
- `frontend/src/app/(dashboard)/patients/**`

## Current UI baseline

`/patients` contains paged search and detail. `ADMIN`/`NURSE` can create and update patients;
`ADMIN` can delete after explicit confirmation. The immutable `soCmnd` rule is reflected in the
edit form. The service-only existence lookup remains unavailable to browser code.

## Owner queue

- `FE-PATIENT-01` — `DONE`: live controller/DTO verified.
- `FE-PATIENT-02` — `DONE`: exact Vietnamese wire mapping and list/search route.
- `FE-PATIENT-03` — `DONE`: detail/create/update/delete with controller roles and validation.
- `FE-PATIENT-04` — `IMPLEMENT`: add contract/component tests after the shared harness exists.

## Handoffs and blockers

- Producer: Patient / Hoàng Anh. Consumers: Patient UI, Clinical, Lab, Billing, Notification.
  Acceptance: secured lookup/search responses and stable `patientId` semantics.
- Reconcile the current frontend `PatientDTO` and paging assumptions with the live Patient
  controller fixture, then verify populated, empty, unavailable and retry states through Gateway.
- Patient self-service and “my notifications” require a documented patient identity mapping from
  Gateway and Patient. Do not decode or infer an undocumented token claim.
- Patient self-service mutation still requires an explicit owner contract; current writes are staff-only.

## Contract and verification gate

The live controller and DTO override the design document once implemented. Reconcile naming in one
PR before adding workflows.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Patient tests when available. Full
functional smoke testing remains blocked until the Patient backend is healthy through the gateway.
