# Vinh and Lộc frontend closeout

> **For agentic workers:** Execute the existing approved outpatient design in bounded tasks using subagent-driven-development and the verification-before-completion workflow.

**Goal:** Complete the implemented Vinh/Lộc outpatient frontend flows, repair demonstrated defects, and record honest acceptance evidence.

**Architecture:** Keep the existing Next.js route composition and feature APIs. Gateway/controller/DTO contracts remain authoritative; no inferred identities, financial authority, foreign feature imports, or production flag rollout.

**Tech stack:** Next.js 16, React 19, TypeScript, Vitest, pnpm, real packaged backend acceptance harness.

## Authorized paths and classification

- IMPLEMENT, Vinh-owned: `frontend/src/features/appointment/**`, `medical-record/**`, `lab/**` and matching `app/(dashboard)/appointments/**`, `records/**`, `lab/**`.
- DELEGATED by the user's 2026-10-20 request to finish Vinh and Lộc frontend: `frontend/src/features/billing/**`, `notification/**` and matching routes. Preserve existing Notification implementation.
- IMPLEMENT, Vinh shared scope as needed: `frontend/src/lib/{api,use-command}.ts`, adjacent regressions, shared login role landing and its regression, `frontend/src/app/(dashboard)/layout{,.test}.tsx`, frontend workboard/service guides and closeout evidence. The dashboard session boundary must remount private workspaces when the bearer/role changes.
- IMPLEMENT test-only, Vinh verification authority: `backend/support-service/src/test/java/com/mediflow/support/SixServiceOutpatientAcceptanceIT.java`; align held browser mode with Lab V1 and Billing LAB_TEST, without production/default-mode changes.
- VERIFY-CONTRACT only: live backend controllers, DTOs, Gateway policy, Pharmacy/Report/Core features. Missing foreign contracts remain HANDOFF; deferred Inpatient/Surgery stay deferred.

## Tasks

- [x] Establish baseline under Node 22 (the machine's Node 26 exposes an incompatible native localStorage in jsdom).
- [x] Billing: reproduce stale invoice and unsafe payment retry/duplicate/session behavior in feature tests; fix bounded components using existing shared patterns; run scoped tests.
- [x] Clinical: add regression coverage for existing check-in/start, record completion, exact server record lookup and source lab order composition; repair any demonstrated faults without widening the approved design.
- [x] Lab: verify V1 READY plus clearance contract; correct stale-response test fixture and cover READY without clearance.
- [x] Shared command: demonstrate HTTP 408 uncertainty, prevent blind resends, and verify regression without changing feature contracts.
- [x] Inspect Notification against existing privacy/role/uncertain-send tests; perform available real signed Gateway browser checks using synthetic disposable fixtures.
- [x] Run complete frontend tests, typecheck, lint and production build. Record actual browser evidence and any unavailable issuer/contract acceptance separately.
- [x] Update workboard and service guides with completed work and concrete remaining handoffs. Compare changed files with authorized scope.

## Verification gates

No test-only fixture grants real clearance. No mocked PATIENT claim counts as actual issuer acceptance. No real email/SMS is sent. Keep the UI flag default-off; enable it only for the isolated matched backend acceptance run. Stop the held backend harness and frontend process after verification.

## Closeout result — 2026-10-20

Billing source discovery, prescription refund recording and Lab financial status were completed
against live Gateway/controller/DTO contracts. CASHIER/PATIENT and other role login landing pages
now open their permitted workspace. Independent review found a stale invoice fee row after payment;
two regressions reproduced it, and authoritative refresh now preserves the payment lock on failure.
Review follow-up reports no remaining actionable P1/P2 findings.

See [durable acceptance evidence](../../../frontend/docs/evidence/vinh-loc/2026-10-20/README.md)
for final checks, signed runtime/browser results and release limits. External producer/provider
contracts remain explicit dependencies; no foreign production service or runtime flag was changed.
