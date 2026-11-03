# Clinical, Lab and cashier UI polish implementation plan

> **For agentic workers:** Use subagent-driven-development for the independent shared, Clinical and Billing tasks; root implements Lab and verifies integration. No commits or deployment in this task.

**Goal:** Deliver the approved restrained workflow design, remove exposed technical identifiers from routine screens and make the existing application easier to operate.

**Architecture:** Keep the existing Next.js/Tailwind theme and feature boundaries. Shared presentation fixes cover all consumers; business edits stay in Vinh-owned Lab, Clinical and Billing. UUIDs remain exact wire values, available only inside deliberate support disclosures. No invented labels, financial state or backend contracts.

**Tech Stack:** Next.js 16, React 19, TypeScript, Tailwind v4, Vitest/testing-library.

User follow-up “hãy tiến hành cải tiến” authorizes implementation of the design in
`docs/reviews/2026-11-03-doctor-lab-cashier-ux-audit.md`, with additional UUID cleanup.
Visual thesis: calm clinical workspace, clear typography, light separators and a single accent.
Content: patient context, current work, then secondary details/support.
Interaction: restrained hover/focus, native disclosures for secondary actions, no ornamental motion.

## Task 1 — Shared references and visual hierarchy

Files: `frontend/src/components/references/{ReferenceText,ReferenceInput}.tsx`, matching tests;
`frontend/src/components/layout/{PageShell,DashboardSidebar}.tsx`, `frontend/src/components/ui/{Button,StatusBadge}.tsx`.

- [x] Add behavior regression tests for UUID-shaped labels (including fallback/resolver result) and selected reference inputs not displaying IDs as names.
- [x] Run shared reference tests; GREEN verified. The shared worker source edit preceded these tests, so no shared-label RED is claimed.
- [x] Guard display labels against UUID strings; keep `technical` opt-in support access, retain exact selected IDs and searches. Do not blindly sanitize input or API payloads.
- [x] Refine spacing/type/surface and navigation affordances within existing palette and approved UI. Do not add decorative cards/controls.
- [x] Run shared component tests. Record read-only inventory of UUID leaks in other owners' features without editing those features.

## Task 2 — Cashier presentation and Lab refunds

Files: `frontend/src/features/billing/{api,types}.ts`, Billing components/tests, `frontend/src/app/(dashboard)/billing/**`.

- [x] Add failing tests for scheduling-gated navigation, no no-op selected-item button, collapsed support lookup, and Lab refund lookup through the real existing endpoint.
- [x] Verify red with focused Billing tests.
- [x] Use one Billing navigation strip; remove duplicate payment links/card launchers; preserve compatibility invoice history behind deliberate navigation.
- [x] Match refund DTO/controller/Gateway before adding Lab source selector to the existing refund workspace. Preserve payment/refund locks and uncertainty behavior.
- [x] Put transaction/clearance IDs in support disclosures, format currency as text, only show provider reference when appropriate. Retain partial payment contract.
- [x] Run focused Billing tests, then submit for root review. No backend changes.

## Task 3 — Doctor working surface

Files: `frontend/src/features/medical-record/components/**`, related tests,
`frontend/src/features/appointment/components/OutpatientExamActions.tsx` only if needed.

- [x] Add regression tests for absence of redundant record lookup action, grouped record actions and refresh preserving active drafts.
- [x] Run focused medical-record regression tests; these were added after the worker source edit, so no doctor RED is claimed.
- [x] Put patient context and exam actions together. Open add-diagnosis/completion forms deliberately; keep clinician primary work visible, support references collapsed.
- [x] Remove successful “Tìm lại hồ sơ”; preserve retry on actual lookup failure. Keep Pharmacy links but do not edit Pharmacy source.
- [x] Prevent refresh from unmounting active drafts or in-flight commands; retain explicit reconciliation after uncertain commands.
- [x] Run focused tests and submit for root review.

## Task 4 — Lab working surface and navigation context

Files: Lab components/tests/API presentation, `frontend/src/app/(dashboard)/lab/**`, Clinical Lab-order route composition where needed.

- [x] Add failing tests for reason-only cancellation under secondary disclosure, safe refresh during result editing, role-default queue, source-context navigation and absence of global queue shortcut.
- [x] Run relevant Lab tests to establish red.
- [x] Place lifecycle/financial context above a focused results/actions workspace. Keep primary action according to state; move cancellation/support to deliberate secondary disclosures.
- [x] One refresh control; preserve draft and command lock, handle authoritative changed lifecycle without silently applying a draft to a new state.
- [x] Keep Lab filters/page/context in URL and validated local return URL; preserve episode scope on reset. Default LAB_TECH to READY only in unscoped view; no client filtering of paginated results.
- [x] Hide unsupported/deferred filter choices from primary outpatient flow, keep technical support access where required. Do not fake patient search or requote.
- [x] After create, return to exact record/detail; remove global queue shortcut. Preserve both producer steps and unknown-result guards until source recovery contract exists.
- [x] Run Lab and Lab-order workspace tests.

## Task 5 — Integration and visual verification

- [x] Review every edited path against ownership; leave pre-existing `scripts/demo_video/` unchanged.
- [x] Run affected frontend tests, full typecheck, lint and production build. Run broader tests once; separate pre-existing failures with evidence.
- [x] Inspect rendered doctor/Lab/cashier pages at desktop and narrow widths with clearly identified API fixtures, keyboard focus, disclosures, empty state and no page overflow; component tests verify error/recovery states.
- [x] Independent spec review, then code-quality review; resolve actionable findings.
- [x] Update audit/evidence with actual checks, remaining backend gaps and any foreign-feature UUID leaks requiring handoff. `git diff --check`.

## Execution record

- Implemented shared, doctor, Lab and cashier changes. Added a single cashier
  sidebar entry and account menu; no Pharmacy/Patient/Organization/Report feature
  source or tests changed. Pre-existing `scripts/demo_video/` was left untouched.
- TDD deviation: shared UUID guards and doctor source edits preceded root-added
  regression tests. No pre-edit RED is claimed for those tasks. RED/GREEN was
  observed for Lab queue behavior, cashier landing, account menu assertions,
  cashier sidebar consolidation, support visibility and Billing recovery identity.
- Review found doctor read/write race, episodeType restoration, recovered payment
  quote identity and final shared retry keyboard order; all were resolved and
  independently reviewed Go.
- Final frontend: **145 files / 1,299 tests pass, 0 skipped** with 4 workers and
  20-second test timeout; typecheck, lint, production build and diff check pass.
  Intermediate failures were corrected; the final full run is authoritative.
- Browser observations use local synthetic API fixtures at desktop/mobile widths;
  they are separate from the earlier real six-JAR Gateway acceptance. Fixture/dev
  processes stopped and viewport override reset. No commit/deployment performed.
- Evidence and remaining contract gaps:
  `docs/acceptance/lab-flow-2026-11-03/ui/README.md` and
  `docs/reviews/2026-11-03-doctor-lab-cashier-ux-audit.md`.
