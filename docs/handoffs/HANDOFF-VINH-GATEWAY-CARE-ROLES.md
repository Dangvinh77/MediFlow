# HANDOFF — Gateway alignment for Clinical/Lab

- Status: OPEN; source audit 2026-10-03, baseline `3fa55c9`.
- Owner: Hoàng Anh (Gateway). Consumers: Vinh's Clinical/Lab frontend and backend.
- Evidence: `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`,
  Clinical `ClinicalCareFinanceController.java` and Lab `LabController.java`.

## Required behavior

| Method/path | Service authorization | Gateway gap |
|---|---|---|
| GET `/api/v1/lab` | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH | DOCTOR/NURSE missing |
| GET `/api/v1/lab/{id}` | ADMIN, DOCTOR, NURSE, LAB_TECH | LAB_TECH missing |
| PUT `/api/v1/lab/{id}/start` | ADMIN, LAB_TECH | Explicit rule missing |
| PUT `/api/v1/lab/{id}/cancel` | ADMIN, DOCTOR, LAB_TECH | Explicit rule missing |
| POST `/api/v1/records/{id}/admission-referrals` | ADMIN, DOCTOR | Explicit rule missing |

Also verify the broad appointment PUT rule against `/check-in` (ADMIN/NURSE) and
`/start-exam` (ADMIN/DOCTOR); specific rules must precede broad compatibility patterns.
Preserve downstream authorization, JWT verification, correlation ID and default deny.
Routing permission never enables a disabled care-finance feature.

## Why / acceptance

The Lab queue permits LAB_TECH but the Gateway currently denies that role's detail request.
A frontend button cannot repair this mismatch. Add route authorization tests for every allowed
and denied role above, including MANAGER list-only behavior, and verify through the real service.
Test legacy `/status` separately; do not broaden all Lab writes or all record POST paths.
Vinh can build read detail for currently allowed roles while LAB_TECH detail acceptance waits.
Close only after Gateway tests and downstream smoke pass; move lasting rules into service docs
and remove this file and its registry row together.
