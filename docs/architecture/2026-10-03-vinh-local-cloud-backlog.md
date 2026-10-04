# Vinh — three-service completion audit

Audit refresh: 2026-10-04. Source baseline: `2e8f024` (master matched origin/master before this
documentation branch). Owner: Dangvinh77 / Harori. Scope: Clinical, Lab and Inpatient plus their
owned frontend features. This queue does not authorize editing another owner's production code or
enabling an integration before shared fixtures pass.

## Evidence and current state

- Fresh codebase-memory-mcp index: 19,226 nodes and 86,526 edges; 128 files excluded by design,
  40 partial parses and 12 unusable diagram/document parses. Graph evidence was checked against
  controllers, event consumers, Gateway rules and active handoffs.
- **Clinical:** legacy APIs and V2 check-in/start-exam/complete/admission-referral commands exist.
  Appointment list/detail/create/edit and medical-record detail exist in the frontend. The backend
  still cannot truthfully activate EXAM clearance because Billing does not publish it.
- **Lab:** list/detail/create/results/start/cancel/status APIs and list/detail frontend pages exist.
  Billing's explicit `labTestIds` compatibility path remains valid. Exact V2 clearance is absent,
  and current Gateway rules still deny valid downstream roles/methods.
- **Inpatient:** Core V1 admission/bed/treatment/discharge APIs, persistence, outbox, guarded event
  consumers and tests exist. Gateway route/RBAC/tests landed in `262d610`. Billing still does not
  publish ADMISSION_DEPOSIT clearance, top-up or settlement facts. Surgery reference/outcome
  behavior remains a joint contract gate.
- **Organization update:** service-only staff and department lookup endpoints landed in `cffdbf5`.
  They unblock Huy's Surgery adapter for those two lookups. The room lookup and authoritative
  job-title-to-team-role mapping remain open.
- Owned Maven regression passed on this branch: Clinical 216 tests (13 skipped), Lab 168 (11
  skipped), Inpatient 82 (8 skipped), with zero failures/errors. Testcontainers could not connect
  to Docker, so the skipped integration tests and real browser/Gateway smoke remain required before
  calling the workflow complete.

## What Vinh can implement now

### P0 — finish before taking speculative integration work

| ID | Work | Scope | Done when |
|---|---|---|---|
| VINH-P0-01 | Complete integration regression and Gateway smoke for current routes | Clinical/Lab/Inpatient; no foreign edits | Unit/web suites already pass; start Docker, run the 32 skipped integration cases, then record login and allowed/denied Clinical, Lab and Inpatient requests with correlation IDs |
| VINH-P0-02 | Clinical record mutations, one command per commit | `frontend/src/features/medical-record/**`, `frontend/src/app/(dashboard)/records/**` | Create, update and diagnosis actions mirror live DTO validation/state rules; ADMIN/DOCTOR only; 400/403/404/409 and retry are handled |
| VINH-P0-03 | Lab request creation | `frontend/src/features/lab/**`, `frontend/src/app/(dashboard)/lab/**` | ADMIN/DOCTOR can create from exact backend DTO; success links to detail; no local payment inference |
| VINH-P0-04 | Resolve Vinh-owned Surgery contract decisions | canonical contract/spec docs + existing Surgery handoff | Stable referral identity/path, external-order registration, outpatient not-applicable behavior and late-event policy are explicit and fixture-ready |

P0-02 and P0-03 are independent. Lab result/start/cancel UI must wait for the Gateway role/method
matrix to align so the page does not promise a workflow that the public route rejects.

### P1 — start only after the named gate

| ID | Work | Gate | Vinh implementation |
|---|---|---|---|
| VINH-P1-01 | Clinical EXAM clearance | Lộc publishes canonical EXAM fixture through outbox | Consumer/command/state transition plus duplicate/mismatch/DLQ tests; then activate by explicit rollout |
| VINH-P1-02 | Lab exact-test clearance | Lộc publishes canonical LAB_TEST fixture; Hoàng Anh aligns Gateway roles | Purpose/episode/test-ID consumer tests, then result/start/cancel frontend actions |
| VINH-P1-03 | Inpatient deposit/top-up/settlement integration | Lộc publishes all canonical fixtures | Same-byte consumer tests, real Rabbit smoke and explicit close-command validation; consuming settlement never auto-closes |
| VINH-P1-04 | Inpatient ↔ Surgery outcomes | Vinh/Huy approve registration and delivery semantics; Huy publishes fixtures | Reference registration and durable event-first/late/duplicate handling without reopening a closed admission |
| VINH-P1-05 | Inpatient frontend base | `docs/ai/15-frontend-ownership.md` assigns canonical route/feature paths | Read/list/detail/bed status first through Gateway; mutation slices follow exact roles and live APIs |

### P2 — intentionally deferred

- Bed transfer/release/capacity events have no approved wire contract. Keep placement truth inside
  Inpatient until consumers and fields are agreed.
- Emergency deposit override, Surgery post-start abort and debt/waiver policy need explicit
  clinical/financial authority. Do not invent approvers or legal policy.
- Do not add Patient/Organization lookups merely because endpoints exist. Admission referrals
  already carry producer-validated IDs; use synchronous lookup only when a command needs current
  eligibility, and distinguish absence from outage.

## Other developer actions

| Owner | Must deliver | Unblocks Vinh | Active handoff |
|---|---|---|---|
| Lộc — Billing | EXAM/LAB_TEST clearance producer fixtures; ADMISSION_DEPOSIT, top-up and settlement producer/outbox/replay behavior | Clinical exam gate, Lab exact authorization, Inpatient admit/close workflow | [Clinical/Lab clearance](../../backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md), [Inpatient deposit/settlement](../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md) |
| Hoàng Anh — Gateway | Clinical/Lab method/role parity | LAB_TECH detail/write workflow, Doctor/Nurse Lab queue, admission-referral public access | [Gateway care roles](../handoffs/HANDOFF-VINH-GATEWAY-CARE-ROLES.md) |
| Huy — Surgery | Business API/consumer/publisher, staff/department lookup adapter, exact outpatient/admission and delivery-order fixtures | Inpatient reference/outcome integration | [Surgery decisions](../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md), [foundation bootstrap](../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md) |
| Hoàng Anh — Organization | Room lookup and authoritative job-title values/eligibility semantics | Huy's scheduling/team validation and later Inpatient Surgery E2E | [Surgery decisions](../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) |
| Huy + Lộc | Pharmacy/Report admission and finance projections with same-byte fixtures | Full admission medication/reporting acceptance | [Huy care-finance consumers](../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) |

The former Inpatient Gateway route handoff is retired because `262d610` satisfies it. It must not
be reopened to represent unrelated Billing or Surgery blockers.

## Completion definition for Vinh's three services

1. Owned module tests pass, with duplicate/mismatch/out-of-order cases for each active event.
2. Gateway role/path behavior matches downstream `@PreAuthorize` rules and real smoke evidence.
3. Every enabled producer/consumer pair shares exact versioned fixture bytes and a rollout flag.
4. Clinical and Lab never infer payment; Inpatient never infers settlement or external order IDs.
5. Frontend calls same-origin `/api/*` through `src/lib/api.ts` and exposes only accepted roles.
6. Active handoffs are deleted only when their acceptance evidence lands and lasting rules move to
   canonical contracts/service docs.

Use focused commits and PRs per service. Keep the configured Vinh identity and never add AI/coauthor
trailers.
