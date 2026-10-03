# Vinh — local / Cloud backlog

Audit date: 2026-10-03. Source baseline: `3fa55c9` (master matched origin/master after fetch).
Owner: Dangvinh77 / Harori. This is a task queue, not approval to enable V2 integration.

## Evidence and current state

- Full codebase-memory-mcp 0.11.0 index: 19,078 nodes, 85,796 edges; 128 deliberately
  excluded files, 40 partial parses, 12 unusable parses. Graph queries plus direct Java/config
  reads support this audit; this is not an exhaustive business-rule or SQL correctness review.
- Clinical: legacy APIs plus check-in/start-exam/complete/admission-referral commands exist.
  Appointment frontend list/detail/create/edit is implemented. Medical-record frontend exposes
  only patient lookup (`frontend/src/features/medical-record/api.ts`).
- Lab: list/detail/create/results/start/cancel/status APIs exist (`LabController`). Frontend
  exposes list search only (`frontend/src/features/lab/api.ts`). Payment and clinical status must
  stay separate. Gateway role mismatches are a concrete blocker, not a missing Lab detail API.
- Inpatient: admission/bed/treatment/discharge core, lifecycle fixtures and guarded consumers
  exist. Gateway route is absent. Surgery consumer still requires admissionId for Surgery facts;
  outpatient/late-event/reference-registration behavior awaits the shared Surgery contract.
- Billing: V2 domain model `FinancialClearance` exists. Current `BillingEventPublisherAdapter`
  still enqueues invoice-created/payment-completed/payment-failed only. Domain progress is not
  a clearance or settlement producer implementation.
- Existing five handoffs remain active in part. Do not delete whole handoffs merely because
  one local slice or same-byte decoder test is complete. Surgery shared runtime is already done.
- Docker daemon was unavailable during audit (Linux engine named pipe absent). No new Docker,
  browser E2E or regression-suite pass is claimed by this document.

## Work local first

| ID | Work | Scope / gate | Done when |
|---|---|---|---|
| LOCAL-01 | Refresh graph, source/contract audit, correct stale docs, route-role handoff | Documentation only | Completed in this audit; no application behavior changed |
| LOCAL-02 | Start Docker when available; verify login → appointments → records → lab with seeded test accounts | Existing Compose/config; no shared production edits | Record commit, role, request/path, expected/actual status and correlation ID; distinguish 401/403, disabled V2, unavailable service and real defect |
| LOCAL-03 | Run Vinh persistence/Rabbit regression; preserve data volumes | Clinical/Lab/Inpatient | `mvn -q -pl backend/clinical-service,backend/lab-service,backend/inpatient-service -am test`; failures and skips explicitly reported, not treated as success |
| LOCAL-04 | Prepare Surgery contract response from Vinh | Canonical contract docs + existing Surgery handoff | Referral path/business key, exact episode proof, external-order registration, outpatient/late delivery and clinical-policy decisions recorded; no invented medical/legal policy or new wire event |
| LOCAL-05 | Integrate Cloud PRs one at a time and smoke through Gateway | Owned frontend paths; shared fixes require assignment | Typecheck/lint/build and allowed/denied-role browser evidence on merged source |

Keep runtime checks local because they need Docker, actual account/role configuration and the
current multi-service environment. Do not launch the whole stack merely to render a read-only UI.
LOCAL-02/03 are pending runtime availability, not completed by static inspection.

## Cloud tasks to run later

These are prepared assignments, not launched Cloud tasks. Each task branches from the latest
merged master, reads AGENTS.md and the live controller/DTO, and stops at missing contracts.

### CLOUD-01 — Medical-record detail (ready, small)

Writable: `frontend/src/features/medical-record/**`, `frontend/src/app/(dashboard)/records/**`.
Add `getById` and `/records/[recordId]` using existing `GET /api/v1/records/{id}`;
link from the patient-record list. ADMIN/DOCTOR/NURSE only. Mirror the current DTO, diagnoses
and available attachments without fetching other features. Reuse appointment detail conventions
for UUID validation, loading, 404, denied role, retry and correlation ID. No record mutation yet.
Acceptance: typecheck/lint/build, invalid UUID/no-request, empty attachments, 404 and role checks.
Backend/Gateway/shared auth/components/package files are read-only. No new dependency or harness.

### CLOUD-02 — Lab detail (ready with role limitation, small)

Writable: `frontend/src/features/lab/**`, `frontend/src/app/(dashboard)/lab/**`.
Add detail API and `/lab/[testId]`, matching live `LabTestDTO`; display results, conclusion,
episode context and payment separately. Preserve missing values; never infer normal/abnormal
from COMPLETED. MANAGER has list permission only; currently ADMIN/DOCTOR/NURSE can use Gateway
detail, while LAB_TECH is blocked by the registered Gateway handoff. Do not advertise LAB_TECH
detail as working until that fix lands. Acceptance: typecheck/lint/build, invalid UUID, 404,
empty results, retry and denied role. No start/cancel/result writes in this slice.

### CLOUD-03 — Record create / diagnosis (after CLOUD-01, medium)

Same ownership as CLOUD-01. First inspect create/update/diagnosis DTO validation and application
rules under the active feature flag. Implement one mutation per commit, ADMIN/DOCTOR only;
handle validation/conflict and refresh detail. Keep exact UUID inputs until selector contracts
are accepted. Do not expose V2 complete/referral/exam actions as a workaround for missing finance.
Acceptance: current role/validation/state contract, safe retries and deterministic frontend checks.

### CLOUD-04 — Lab request / result entry (after CLOUD-02 and route gates, medium)

Same ownership as CLOUD-02. Split request creation from result entry. Confirm active legacy/V2
behavior and exact create/results/start/cancel request DTOs first. Respect the backend payment
gate; never set paid locally. Gate LAB_TECH workflow on the Gateway fix. Result submission must
handle stale state/409, missing clearance and backend validation without claiming success.
No cross-feature imports, new billing assumptions or automatic activation of feature flags.

## Work still blocked / owner action

| Owner | Needed | Vinh work unlocked |
|---|---|---|
| Lộc | Actual EXAM/LAB_TEST/ADMISSION_DEPOSIT clearance fixtures + transactional producer, settlement facts and replay semantics | Real payment-to-exam/test/admission and administrative-close integration |
| Hoàng Anh | Inpatient Gateway route; Clinical/Lab method/role parity; Organization room/staff authority for Surgery | Inpatient frontend and complete Lab/Clinical end-to-end access |
| Huy with Vinh | Surgery referral-to-case/reference contract, actual context-specific outcome fixtures and late/out-of-order semantics | Inpatient Surgery consumer integration |
| Vinh with Huy | Approved transfer/release/capacity and source-operation contracts; clinical checklist/consent policy input | Pharmacy admission eligibility and Report metrics beyond existing immutable lifecycle facts |
| Huy with Lộc | Pharmacy held V1 lifecycle acceptance, classified finance and replay/cutover | Safe activation, not another speculative consumer implementation |

Use the [active handoff registry](../handoffs/README.md). Existing exact producer fixtures are
evidence, not a substitute for receiver tests or an activation approval. Bed occupancy and medical
LOS must not be derived from administrative-close or initial-bed snapshots.

## Execution / quota / merge policy

Run CLOUD-01 and CLOUD-02 independently only on disjoint paths; sequence 03 after 01 and 04 after
02. Local owns shared workboard/contract updates so Cloud branches do not fight over them.
Use one Sol high planning pass, Luna for bounded implementation (max only for hard reasoning),
then deterministic checks and one Astra low review per finished slice; avoid three full audits
of unchanged files. Revalidate model availability when dispatching; no quota percentage estimate.
Each task returns changed paths, commands/results, unresolved gates and a focused commit/PR.
Use the configured allowed human identity; no co-author trailers. Never edit another service,
toggle integration flags, force-push, merge or create additional tasks implicitly from a task prompt.
