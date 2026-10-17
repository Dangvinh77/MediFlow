# Gateway role audit — 2026-10-17

Source baseline: `27b8832d25daee8218250224af3f8334318a37df`, plus the local Gateway fixes described below.
Scope: compare the Gateway edge policy with live controller method/class guards and service security
configuration. Foreign modules were inspected read-only. No feature/deployment gate was enabled.

## Coverage

| Module | Controller method/path pairs |
|---|---:|
| Core (Organization + Patient) | 27 |
| Clinical | 18 |
| Lab | 8 |
| Pharmacy | 9 |
| Billing | 17 |
| Notification | 3 |
| Report | 23 |
| Inpatient (deferred) | 18 |
| Surgery (deferred) | 12 |
| Total | 135 |

The frozen `src/test/resources/route-role-contracts.csv` records 122 endpoints with human-role
guards, including Notification `/send`, which also permits a SYSTEM service token. Of these, 97
match existing edge rules; 25 remain unpublished/denied. Thirteen SYSTEM-only internal endpoints
are excluded from the human role fixture. Existing JWT/internal lookup web tests cover that separate
boundary. Method security is enabled downstream; application guards/ownership and profile/property
conditions still apply after the edge permits a request.

The fixture tests the edge filter only. Inpatient/Surgery entries describe existing policy when a
route is present; they do not assert that deferred routes are enabled. The fixture is a reviewed
snapshot of controller guards, not a runtime parser or automatic publication mechanism. Refresh it
against downstream source when contracts change.

## Confirmed mismatches and fixes

| Method/path | Previous Gateway roles | Downstream roles / fixed Gateway roles |
|---|---|---|
| `PUT /api/v1/pharmacy/prescriptions/{id}/cancel` | ADMIN, PHARMACIST | ADMIN, DOCTOR |
| `GET /api/v1/reports/financial/capabilities` | ADMIN, MANAGER | ADMIN |
| `GET /api/v1/reports/financial/rebuilds/{generationId}` | ADMIN, MANAGER | ADMIN |
| `GET /api/v1/reports/financial/source-archives/{generationId}` | ADMIN, MANAGER | ADMIN |
| `GET /api/v1/reports/operations/rebuilds/{id}` | ADMIN, MANAGER | ADMIN |

Pharmacy cancel/dispense now have separate exact PUT shapes. Dispense remains ADMIN/PHARMACIST.
Report's broad GET allowlist was replaced with exact existing controller paths so adding an ADMIN
rule cannot be bypassed by the old ADMIN/MANAGER wildcard (the filter permits any matching rule).
Ordinary report readers keep their downstream roles. Report maintenance endpoints remain subject
to their downstream feature/profile conditions, including exclusion from the outpatient profiles.
No other role mismatch was found on the inventoried endpoints already covered by edge rules.

## Unpublished endpoints retained as denied

These are differences in publication coverage, not proof that they should become public. Enabling
them requires the applicable owner contract, feature scope, health/correlation and acceptance work.
The CSV marks every endpoint below `false`; all nine canonical roles must remain denied.

- Billing (8): `POST /accounts/{id}/deposit-allocations`,
  `POST /accounts/{id}/settlement-reconciliations`, `GET /accounts/{id}/settlement-preview`,
  `POST /accounts/{id}/final-settlements`, `POST /transactions/{id}/refunds`,
  `GET /prescription-adjustments/{id}/refunds-due`, `POST /accounts/{id}/settlements`,
  `GET /surgery-cancellations/{id}/refunds-due` (prefix `/api/v1/billing`).
- Core (1): `PUT /api/v1/org/staff/{id}/surgery-roles/{teamRole}`.
- Report (9): `POST /financial/publications`, `POST /financial/publications/rollbacks`,
  `POST /financial/rebuilds`, `POST /financial/rebuilds/{generationId}/batches`,
  `POST /financial/source-archives`, `POST /operations/publications`,
  `POST /operations/publications/rollbacks`, `POST /operations/rebuilds`,
  `POST /operations/rebuilds/{id}/batches` (prefix `/api/v1/reports`).
- Surgery (7): `POST /cases`, `POST /cases/{id}/readiness/evaluate`,
  `POST /cases/{id}/schedule/finalize`, `POST /cases/{id}/start`,
  `POST /cases/{id}/complete`, `PUT /cases/{id}/checklist`,
  `POST /cases/{id}/consents` (prefix `/api/v1/surgery`).

## Verification

Environment: Windows PowerShell, JDK 21.0.11, existing Maven wrapper-free reactor command.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
mvn -q -pl backend/gateway -am test
git diff --check
```

`RouteRoleContractTest` exercises 122 endpoint policies × 9 roles = 1,098 cases. Every denied case
asserts HTTP 403 and that the filter chain was not called. `CareRouteAuthorizationTest` covers
Pharmacy cancel/dispense and wrong/extra/nested commands. `GatewayWebTest` additionally verifies
the four Report paths with signed access, refresh and service tokens, missing authentication,
wrong method/extra path, and preservation of ordinary report reader roles.

Full Gateway suite result: **1,384 tests, 0 failures, 0 errors, 0 skipped**, Maven exit code 0.
The contract matrix contributed 1,098 cases, also with no failures/errors/skips.
`git diff --check` passed. Local ignored evidence is under `target/surefire-reports/` and
`target/all-role-audit-tests.log`; the committed fixture and this document retain the reviewable
scope and results without requiring those local artifacts.

This is source/security/routing evidence, not an actual Report/Pharmacy business smoke or whole-MVP
acceptance. Other existing broad path rules were inspected for current role collisions; replacing
all wildcard paths or publishing missing endpoints is outside this audit's fixes.

## Integration with updated master

Updated to `ea3233108e4cf98d2929596a2cf9a975e8bbafe2` using a fast-forward after preserving local
Gateway changes in stash `gateway-role-audit-before-pull-2026-10-17`. The stash is retained as a
backup. Two overlapping files were resolved by keeping upstream's exact Pharmacy paths and expanded
Pharmacy role matrix, then retaining the local Report fix and additional regression coverage.
No commit or push was performed. Controller method/path/role declarations used by the frozen
fixture did not change between the original baseline and this update; Notification advice changes
do not alter its endpoint guards. Support now composes Report/Notification; their logical API
prefixes/ownership remain the same.

Re-ran `mvn -q -pl backend/gateway -am test` with JDK 21.0.11 on the updated source plus local
changes: **1,449 tests, 0 failures, 0 errors, 0 skipped**, exit code 0. `git diff --check` passed
and `git ls-files -u` was empty. Ignored local log: `target/role-audit-after-pull-tests.log`.
