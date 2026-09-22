# Frontend Service: Clinical

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

## Current UI baseline

- `/appointments`: paged appointment list with department/date filters through
  `GET /v1/appointments`; UI roles `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE`. ADMIN, DOCTOR, and NURSE
  can open `/appointments/{appointmentId}` backed by `GET /v1/appointments/{id}`.
- `/records`: explicit patient UUID lookup through `GET /v1/records/patient/{patientId}`; UI roles
  `ADMIN`, `DOCTOR`, `NURSE`.
- Both flows use feature-local types/API modules, shared async states, and the gateway API wrapper.

## Owner queue

- `FE-CLINICAL-01` — `DONE`: appointment detail with UUID validation, role gate, loading,
  not-found, error/retry, and list navigation.
- `FE-CLINICAL-02` — `VERIFY-CONTRACT`: add one appointment create/update/status vertical slice,
  including exact role, validation, conflict, and retry behavior.
- `FE-CLINICAL-03` — `VERIFY-CONTRACT`: add record detail, then one create/update/diagnosis slice
  supported by the live controller.
- `FE-CLINICAL-04` — `IMPLEMENT`: add focused API/component tests after the shared test harness is
  assigned.

## Handoffs and blockers

- Patient search, display names, and existence-aware forms are blocked until Patient exposes a live
  controller/DTO. Producer: Patient / Hoàng Anh. Acceptance: documented secured lookup response.
- Doctor and department selectors require Organization contracts. Producer: Organization / Hoàng
  Anh. Do not infer staff/department relationships from UUIDs.
- Lab results and Pharmacy dispense information must arrive through documented Clinical contracts;
  do not import those frontend features or join their responses in a Clinical component.

## Contract and verification gate

Check the current Clinical controller, request/response DTOs, roles, and error codes before changing
the API facade. Preserve bare UUID references and never make a direct service-port request.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Clinical tests when available. Smoke
test both role denial and the changed `/appointments` or `/records` flow.
