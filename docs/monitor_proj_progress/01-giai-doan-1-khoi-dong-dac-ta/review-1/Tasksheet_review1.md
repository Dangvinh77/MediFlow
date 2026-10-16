# MediFlow Review 1 — Frontend Task Sheet

- **Plan preparation / refresh date:** 2026-10-16.
- **Scope:** Outpatient MVP; six target deployments: Core, Clinical, Lab, Pharmacy, Billing, Support.
- **Evidence baseline:** Dated frontend workboard, owner service guides, and active handoffs linked below. This is a documentation reconciliation, not a new runtime verification run.
- **Relationship to Review 1:** The two Standard reports define customer requirements. This sheet tracks frontend delivery and integration dependencies; a listed requirement is not proof of a delivered screen.

## Scheduling and Evidence Rules

- Keep frontend features and routes organized by logical context. Core groups Organization/Patient and Support groups Report/Notification at deployment level; ownership remains separate.
- Use the existing task IDs where available. Sheet row numbers identify this summary only and do not create replacement implementation tasks.
- `Planned Start`, `Actual Start`, and `Actual Days` are `—` where no task-specific schedule or time record has been verified for this revised scope. A checkpoint date is not a start date; test counts and elapsed days are not person-days.
- The former 2026-10-12 forward schedule is superseded. Sequence work by the dependencies below; do not invent replacement dates or infer progress percentages from historical task totals.
- `BASELINE` and `LOCAL VERIFIED` do not mean accepted against the outpatient backend. Close a task only after the required contract, activation, signed-role Gateway, and runtime evidence is recorded.
- Missing contracts remain owner handoffs. Frontend code must not guess source IDs, identity mappings, prices, financial clearance, or unavailable report values.

## Status Legend

| Status | Meaning |
|---|---|
| `BASELINE` | Existing UI reported in the workboard/service guide; outpatient runtime acceptance is still separate. |
| `LOCAL VERIFIED` | Dated local tests or fixture-browser evidence exists for the stated work; does not close external gates. |
| `IN PROGRESS` | Part of the task is implemented or verified; named acceptance work remains. |
| `VERIFY CONTRACT` | Confirm the current controller, DTO, roles, activation, and Gateway contract before implementation. |
| `BLOCKED` | A named contract, identity, Gateway, or runtime dependency remains open. |
| `PLANNED` | Required work without completion evidence in the cited sources. |
| `DEFERRED` | Outside outpatient MVP acceptance; retain existing source and data. |

## Task Sheet

| No. | Member | Contents / task reference | Page Name / scope | Planned Start | Actual Start | Actual Days | Status |
|---:|---|---|---|---|---|---:|---|
| 1 | Vinh | Appointment list/detail/create/edit and legacy arrival/cancel. Existing screens do not prove the V1 check-in/examination flow. | `/appointments` and child routes | — | — | — | `BASELINE` |
| 2 | Vinh | Medical-record lookup/detail/create/edit/diagnosis. Keep completed-state and permission guards; distinguish legacy operations from outpatient V1. | `/records` and child routes | — | — | — | `BASELINE` |
| 3 | Vinh | `FE-VINH-MVP-01/04`, `FE-LAB-04`: reconcile Clinical/Lab API/types and role/error/loading/retry states. Lab stale-response regression and local tests recorded on 16 October; signed-role smoke remains. | `/appointments`, `/records`, `/lab` | — | — | — | `IN PROGRESS` |
| 4 | Vinh | `FE-VINH-MVP-02`: check-in → start examination → outpatient completion. Wait for exact EXAM Gateway/Billing acceptance; unpaid visits remain blocked. | Existing appointment/record workspaces; V1 controls gated | — | — | — | `BLOCKED` |
| 5 | Vinh | `FE-VINH-MVP-03`: Clinical Lab order → Lab request using the returned source order ID → processing/results. Producer/API preparation exists; actual paid LAB_TEST and Gateway acceptance remain. | `/records`, `/lab` | — | — | — | `BLOCKED` |
| 6 | Vinh | `FE-VINH-MVP-04`: integrate prescription status from accepted Clinical/Pharmacy contracts and run authenticated role/error smoke; no V0 fallback presented as V1. | `/records`, related outpatient read states | — | — | — | `BLOCKED` |
| 7 | Huy | `P-02/P-03/P-05/P-07`: drug detail/create/stock, prescription form, and ADMIN outbox UX. Local tests/fixture checks recorded on 16 October; preserve server prices and exact DTOs. | `/pharmacy` and existing child routes | — | — | — | `LOCAL VERIFIED` |
| 8 | Huy | `P-06`: cancel/dispense terminal actions; 4/4 local subtasks complete. Main task still needs GW-01 real-role smoke, including doctor-owned cancellation and denied actors. | Existing prescription detail and terminal controls | — | — | — | `IN PROGRESS` |
| 9 | Huy | `P-08`: connect public outpatient prescription source/payment/dispense only after accepted activation, exact clearance and signed actor contracts. Do not substitute the legacy V0 flow. | Existing Pharmacy workspaces; outpatient V1 controls gated | — | — | — | `BLOCKED` |
| 10 | Huy | `R-01/R-02/R-04/R-05`: daily/monthly/top-medicine report scope, filtering and request lifecycle. Local work recorded; numeric values keep existing contract semantics. | `/reports` and existing report views | — | — | — | `LOCAL VERIFIED` |
| 11 | Huy; Lộc / Billing; Hoàng Anh / Organization | Resolve report currency/department display and source coverage before new MVP report snapshots. Distinguish cash received from earned revenue and unavailable from zero. Each owner supplies only their own contract. | `/reports`; display contracts for `/billing` | — | — | — | `BLOCKED` |
| 12 | Hoàng Anh | `FE-ORG-01/02/03`: department/staff/account workflows, including transfer and account status. Service-only identity endpoints remain outside browser code. | `/organization` and child routes | — | — | — | `BASELINE` |
| 13 | Hoàng Anh | `FE-PATIENT-01/02/03`: search/detail/create/update/delete with current roles and immutable identity number. Existing writes are staff-only; patient self-service requires an explicit contract. | `/patients` and child routes | — | — | — | `BASELINE` |
| 14 | Hoàng Anh | `FE-ORG-04`, `FE-PATIENT-04`: verify remaining component/contract tests, independent section errors, paging, validation, role denial, retry, and actual Core routing. Shared test harness already exists; do not recreate it. | Organization/Patient owned feature and route paths | — | — | — | `PLANNED` |
| 15 | Hoàng Anh | Gateway/Core identity liaison: document signed staff/account/patient mappings; G1 exact Billing discovery routing and GW-01 Pharmacy method/role acceptance. These are backend/Gateway prerequisites for the UI. | Gateway contracts; no new browser access to internal lookups | — | — | — | `IN PROGRESS` |
| 16 | Lộc | `FE-BILLING-01/02/03/05/06/08`: invoice lookup/detail/create/payment, selected-charge V1 payment, department revenue view, ADMIN outbox replay. Current ledger UI accepts an exact request ID; actual source discovery integration needs G1 and Billing evidence. | `/billing`, `/billing/new`, `/billing/[invoiceId]`, `/billing/payment-requests`, `/billing/payment-requests/[id]`, `/billing/revenue`, `/billing/admin/outbox` | — | — | — | `BASELINE` |
| 17 | Lộc | `FE-BILLING-04`: verify/add tests for pagination, monetary display, permission, terminal state, API errors, retries, and payment flow using the existing harness. Do not assume currency from locale. | Billing owned feature and route paths | — | — | — | `PLANNED` |
| 18 | Lộc; Hoàng Anh / Gateway | `FE-BILLING-07` outpatient prescription-adjustment refund-due view: confirm exact Gateway allowlist and DTO/roles before exposing it. Read-only refund-due is not a refund command. | Planned control/view under Billing; route subject to accepted contract | — | — | — | `BLOCKED` |
| 19 | Lộc | `FE-NOTIFICATION-01/02`: patient history, secured detail and ADMIN manual send baseline. Protect recipient data; retain exact role/privacy boundaries. MVP only requires accepted outpatient in-app intents. | `/notifications`, `/notifications/[notificationId]`, `/notifications/send` | — | — | — | `BASELINE` |
| 20 | Lộc | `FE-NOTIFICATION-04`: verify/add pagination, status/channel, error/retry rendering, role and cross-patient denial tests using the existing harness. | Notification owned feature and route paths | — | — | — | `PLANNED` |
| 21 | Lộc; Hoàng Anh / identity producer | Patient-owned notification entry needs authoritative patient/account mapping and ownership checks. `FE-NOTIFICATION-03` retry/read-state controls remain unavailable until a live command contract exists. | Planned patient-owned notification entry; no assumed route or claim | — | — | — | `BLOCKED` |
| 22 | Vinh / shared coordinator; Huy / recorded frame delivery | Six-group sidebar/header/home/theme work has local evidence on 16 October. Remaining shared release work: signed-session desktop/mobile navigation, drawer, denied-role and expired-session smoke. Prior Huy frame assignment does not grant new shared scope. | Existing shared layout, home and theme paths only as authorized | — | — | — | `IN PROGRESS` |
| 23 | Context owners; Vinh / coordination | Verify the integrated identity → EXAM → LAB_TEST → prescription/payment/dispense journey, asynchronous reports/notifications, duplicate delivery and failure recovery. Requires actual Billing publication, Gateway rules and accepted runtime gates. Six navigation groups do not prove six deployed processes. | All active outpatient routes through Gateway | — | — | — | `BLOCKED` |
| 24 | Lộc / Notification; Huy / Report; assigned shared runtime/build owner; Hoàng Anh / Gateway | Support deployment and routing acceptance for existing Report/Notification screens. This is an external dependency, not permission to edit shared build or another owner's production files. | Existing `/reports`, `/notifications`; target Support host | — | — | — | `BLOCKED` |
| 25 | Vinh / Inpatient; Huy / Surgery; Lộc / related Billing | Inpatient beds/admission/treatment, Surgery, admission deposit/top-up/settlement, Surgery refund views, full Report V2 and advanced reminders remain outside MVP. Preserve their existing code/data; do not activate them to close outpatient tasks. | `/inpatient` and future Surgery/advanced-care surfaces | — | — | — | `DEFERRED` |

## Evidence and Dependency Register

1. [Frontend workboard](../../../../frontend/docs/frontend-workboard.md): 16 October Clinical/Lab regression, Huy local work, shared frame checkpoints, and historical route baseline. Its dated current priorities override older shipped-route labels for MVP activation.
2. [Frontend ownership](../../../ai/15-frontend-ownership.md): canonical feature/route boundaries and shared-scope rules.
3. [Organization guide](../../../../frontend/docs/services/organization.md) and [Patient guide](../../../../frontend/docs/services/patient.md): rows 12–14 and identity dependencies. Owner guides describe their recorded baseline; they do not prove a fresh Core runtime smoke.
4. [Billing guide](../../../../frontend/docs/services/billing.md) and [Notification guide](../../../../frontend/docs/services/notification.md): rows 16–21. Reconfirm older missing-endpoint statements against the newer discovery handoff before starting work; API implementation, Gateway exposure, publication and actual UI acceptance are separate gates.
5. [Billing discovery/publication handoff](../../../handoffs/HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md): L1/L2 merged; L3 still has open acceptance groups, L4 has owner evidence, G1 and actual paid slice remain open at its recorded checkpoint. Do not mark publication or end-to-end UI complete from API availability alone.
6. [Huy MVP handoff](../../../handoffs/HANDOFF-HUY-MVP.md): GW-01, signed actor, Rx source/paid flow, report coverage and Support dependencies. P-06 local completion does not close these gates.
7. [System design](../../../plans/01-system-design.md), [implementation plan](../../../plans/05-implementation-plan.md), and [acceptance runbook](../../../plans/06-acceptance-runbook.md): six-service target and required backend evidence. Core exists in source; Support composition is not yet present. Inpatient/Surgery remain deferred.

This sheet records no new application test results and grants no additional production write scope. Refresh the relevant row when its owner supplies new evidence; keep detailed backend gaps in the existing handoffs rather than creating a second registry here.
