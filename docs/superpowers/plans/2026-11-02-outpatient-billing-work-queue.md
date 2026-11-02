# Outpatient Billing work queue implementation plan

> **For agentic workers:** Use subagent-driven-development for the independent Billing/Gateway API task. Parent implements Billing frontend. Review specification compliance before code quality. No concurrent Maven builds against shared outputs.

**Goal:** Implement MF-03's Lab/Rx cashier queue without copying source/patient/episode UUIDs.
**Architecture:** Billing read model supplies immutable source references and current financial snapshot; selection revalidates through existing by-source discovery and reuses existing command/recovery UI. No external-store joins, new financial events or bulk payment.
**Tech stack:** Existing Java/Spring/JDBC, common PageQuery/PageResult, Next/TypeScript/Tailwind, JUnit/Testcontainers/Vitest.
**Spec:** [Approved review-derived scope](../specs/2026-11-02-outpatient-billing-work-queue.md).

## 1. Backend and Gateway — worker

Owned `backend/billing-service/**` and exact Gateway GET rule/tests under `backend/gateway/**`.

- [ ] Write failing PostgreSQL cases extending canonical discovery fixtures: pending Lab/Rx listed with exact IDs; patient/episode/purpose filter; paid/cancelled/expired/closed/inpatient excluded; partial balance and Lab0; stable paging; corrupt target denied.
- [ ] Add `PaymentRequestQueueItem` DTO, `FindPaymentRequestQueueUseCase`, `PaymentRequestQueuePort`, read service/config and `GET /queue`. Reuse canonical discovery mapping in the JDBC adapter, count/page repeatable-read, bounded page, server clock and money; no per-row queries.
- [ ] Add signed JWT/controller tests for allowed/denied roles/token types, gates, bad input, no-store/correlation; Gateway exact GET rule plus downstream/correlation authorization tests.
- [ ] Verify focused RED→GREEN, then `mvn -q -pl backend/billing-service -am test` and `mvn -q -pl backend/gateway -am test`. Report exact skips/profiles and signal Maven idle before parent runtime packaging.

## 2. Frontend — parent

Owned `frontend/src/features/billing/**`, Billing route and matching tests only.

- [ ] API/DTO test locks GET `/v1/billing/payment-requests/queue` with actual optional filters and PageResult shape.
- [ ] Add `PaymentRequestWorkspace.tsx` with session boundary, paged queue, patient/type/care-episode filters and selected detail. Use `ReferenceText` labels and authoritative amounts; distinguish initial/loading/empty/unavailable states.
- [ ] Extend `PaymentRequestLookupForm` with optional initial selection, auto read with exact identity verification, collapsed support lookup and stable lock/completion callbacks. Keep default behavior for existing consumers/tests. Do not derive episode or payment authority from the queue row.
- [ ] Regression: click Lab/Rx row transfers exact query, mismatched fresh read blocks payment, pending/uncertain locks switching, late reads do not change filter, payment confirmed preserves receipt and refreshes queue, role/session resets.
- [ ] Compose new workspace in `/billing/payment-requests/page.tsx`, retain existing quote/zero gates and ADMIN/CASHIER RoleGate. Update public contract/service guide and demo click steps.
- [ ] Run affected Vitest, typecheck, lint, stop dev then build.

## 3. Acceptance and integration — parent

- [ ] Fresh independent spec review then quality/security review; fix important findings and rerun affected checks.
- [ ] Real UI: create Lab from a new outpatient visit, cashier selects queue item without IDs and pays exact quote; same for Rx if fixture available, otherwise record untested scope. Verify zero/uncertainty through tests without faking browser evidence. Save screenshots and exact source/limits.
- [ ] Stop owned frontend and run clean-docker.bat; verify ports and project containers stopped.
- [ ] Update review progress and demo, commit/push feature branch and create PR; no auto merge. Preserve Surgery/Inpatient and frontend ownership handoffs.
