# HANDOFF — Gateway alignment for Clinical/Lab

- Status: OPEN; Gateway implementation updated 2026-10-05; downstream smoke still pending.
- Owner: Hoàng Anh (Gateway). Consumers: Vinh's Clinical/Lab frontend and backend.
- Evidence: `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`,
  Clinical `ClinicalCareFinanceController.java` and Lab `LabController.java`.

## Required behavior

| Method/path | Service authorization | Gateway status |
|---|---|---|
| GET `/api/v1/lab` | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH | Implemented |
| GET `/api/v1/lab/{id}` | ADMIN, DOCTOR, NURSE, LAB_TECH | Implemented |
| PUT `/api/v1/lab/{id}/start` | ADMIN, LAB_TECH | Implemented |
| PUT `/api/v1/lab/{id}/cancel` | ADMIN, DOCTOR, LAB_TECH | Implemented |
| POST `/api/v1/records/{id}/admission-referrals` | ADMIN, DOCTOR | Implemented |

Appointment state transitions now use explicit Gateway rules for `/check-in`
(ADMIN/NURSE) and `/start-exam` (ADMIN/DOCTOR); the broad appointment PUT rule was
replaced by resource-update and `/status` patterns.
Preserve downstream authorization, JWT verification, correlation ID and default deny.
Routing permission never enables a disabled care-finance feature.

## Why / acceptance

The Gateway WebTestClient matrix now covers every listed Lab/Clinical transition, allowed and
denied roles, MANAGER list-only behavior, and legacy Lab `/status`. Verify the same matrix through
the real Clinical/Lab services before closing the handoff; do not broaden all Lab writes or all
record POST paths.
Close only after Gateway tests and downstream smoke pass; move lasting rules into service docs
and remove this file and its registry row together.

The Inpatient route/RBAC work in `262d610` was reviewed separately and does not change these
Clinical/Lab mismatches. This handoff remains open in full.
