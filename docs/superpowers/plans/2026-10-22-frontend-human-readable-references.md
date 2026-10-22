# Human-readable frontend references

**Goal:** Replace UUID-first presentation and person/department UUID inputs in Vinh/Lộc frontend with human-readable references and search/selection.

**Approved direction:** User selected “Thay cả ô nhập UUID bằng tìm kiếm/chọn tên” on 2026-10-22. Preserve server identities, money/state authority and technical reconciliation. No backend production or foreign feature edits.

**Architecture:** Generic shared reference controls accept an injected directory; app composition binds public Patient/Organization/Clinical read facades. Feature components never import siblings. Directory labels are display-only and cannot authorize clinical/financial commands. Cache and in-flight results live within the keyed dashboard session; no localStorage or global private cache. Public read roles and Gateway remain mandatory. Unsupported lookups keep an explicit technical fallback and a producer handoff; never broaden roles in frontend.

**Tech stack:** React/Next.js, TypeScript, Tailwind, Vitest, Node 22.

- [x] Write and run failing control tests: selection emits server ID, typing clears stale selection, errors/empty/pagination, late responses, directory/session change, technical details.
- [x] Implement generic directory provider, reference search and human-readable display controls under frontend/src/components/references.
- [x] Compose role-aware directory adapters in frontend/src/app/(dashboard)/_components and mount within existing session boundary. Read existing feature APIs only; retain original DTOs.
- [x] Replace Patient/doctor/department inputs and identifier display in appointment/records/Lab/Billing/Notification. Use date/title/context links for document identities; technical UUIDs stay collapsed.
- [x] Verify directory adapter roles and unchanged command identities; run relevant feature suites, typecheck, lint, production build. Add current workboard entry and any required backend contract handoff.

## Contract limits established from live source

Patient search/detail permits ADMIN/DOCTOR/NURSE; CASHIER, LAB_TECH, PATIENT and MANAGER cannot use it. Organization staff permits ADMIN/MANAGER/DOCTOR/NURSE. Selection must not be advertised as available to a forbidden role. No public search exists for every financial source/event/transaction; retain reconciliation inputs there. Existing generic UUID validation remains at the wire boundary.

## Verification — 2026-10-22 local working tree

- Shared-control tests were first observed failing against stubs, then passing after implementation.
- Full frontend Vitest: **852/852 PASS, 90 files, 0 skipped**, Node 22, maxWorkers=2.
- TypeScript `tsc --noEmit`: PASS, exit 0.
- ESLint: PASS, exit 0 for shared references, dashboard composition/layout, affected Clinical/Lab,
  Billing/Notification features and LabOrderWorkspace regression test.
- Next.js production build: PASS, exit 0, including TypeScript and route generation.
- `git diff --check`: PASS. Existing unrelated working-tree files retained; no commit/push.

Regression coverage includes server ID submission after name selection, clearing dependent records
when patient changes, old search responses, failed reads, empty/current-page pagination, session
replacement and technical-detail collapse on identity change. Existing payment/refund/uncertain
command protections remain exercised. Existing source-order session tests now assert the hidden
technical identity is removed after a session change instead of expecting a visible UUID.

No new signed Gateway/browser acceptance was performed in this change. Local tests and build do
not close the role/producer contract dependencies documented in
[Core handoff](../../handoffs/HANDOFF-HA-FRONTEND-REFERENCE-DIRECTORY-2026-10-22.md).
Person/department selectors use existing authorized reads; roles without those reads retain an
explicit collapsed compatibility input. Financial source/event/transaction, care-episode and
prescription lookup remain technical where no suitable public producer lookup exists. Staff and
appointment query filtering is per server page, explicitly explained by the result UI.

## PR preparation — 2026-10-22

Candidate rebased by copying only scoped changes onto `origin/master` at `3100d7b5` in a
managed worktree; unrelated changelog and Review3 working files were excluded. Existing Huy
identity implementations and handoff progress were preserved. Independent review found one
transient name-read retry gap; fixed with a read-only retry and regression observed red then
green (8/8 shared-control tests). Follow-up review: GO subject to PR checks. Next.js production
build and scoped lint both pass on this integrated candidate. Full-suite result for the PR
candidate is recorded in the PR description; the 852-test figure above belongs to the earlier
working-tree checkpoint and is not a substituted result for the integrated candidate.
