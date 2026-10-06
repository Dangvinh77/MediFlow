# HANDOFF — Gateway alignment for Clinical/Lab

- Status: PARTIAL; master role corrections integrated 2026-10-06 (`ea60ea4`) with local exact-command rules; downstream smoke still pending.
- Owner: Hoàng Anh (Gateway). Consumers: Vinh's Clinical/Lab frontend and backend.
- Implementation: Huy, under the user's task-scoped dependency override. Long-term ownership is unchanged.
- Evidence: `backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java`,
  Clinical `ClinicalCareFinanceController.java` and Lab `LabController.java`.

## Required behavior

Gateway now mirrors all five routes below, plus exact appointment check-in/start-exam roles.
The broad appointment PUT fallback was replaced with the four actual controller paths; otherwise
its union of roles would defeat a narrower rule even if that rule came first. Lab results/status
keep their existing roles but now match one exact `{id}` segment. Unknown nested writes stay denied.

Verification: Gateway full suite **135 tests, 0 failures/errors/skips**, including the 99-case
canonical-role matrix, negative command paths and signed-token web checks. The root run immediately
before this last Gateway edit passed 1,759 tests; do not label the later targeted run a new root
reactor. Clinical/Lab controller suites also passed in that root run. Allowed-route 503 in the
Gateway web test means discovery routing was reached, **not** successful real-service E2E.
Actual Gateway-to-Clinical/Lab deployment smoke remains open, so this handoff is not deleted and
the feature flags are not enabled.

| Method/path | Service authorization | Gateway gap |
|---|---|---|
| GET `/api/v1/lab` | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH | Implemented and tested |
| GET `/api/v1/lab/{id}` | ADMIN, DOCTOR, NURSE, LAB_TECH | Implemented and tested; MANAGER denied |
| PUT `/api/v1/lab/{id}/start` | ADMIN, LAB_TECH | Implemented and tested |
| PUT `/api/v1/lab/{id}/cancel` | ADMIN, DOCTOR, LAB_TECH | Implemented and tested |
| POST `/api/v1/records/{id}/admission-referrals` | ADMIN, DOCTOR | Implemented and tested |

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

The Inpatient route/RBAC work in `262d610` was reviewed separately. The Clinical/Lab policy gaps
are now implemented; only the deployment acceptance described above remains open here.
