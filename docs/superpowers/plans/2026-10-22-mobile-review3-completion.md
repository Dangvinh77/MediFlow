# Mobile Review3 Completion Implementation Plan

> For agentic workers: use subagent-driven-development for bounded feature implementation and review; the coordinator handles app integration and evidence. Keep implementation assignments disjoint.

**Goal:** Complete active mobile outpatient workflows using current frontend contracts and replace Review3 screenshots with high resolution real runtime evidence.

**Architecture:** Flutter presentation to domain from data with Gateway-only adapters and Riverpod composition. Shared reference lookup exposes names and selectors without feature imports; domain contains no Flutter or network dependencies.

**Tech Stack:** Flutter, Dart, Riverpod, GoRouter, Dio, PostgreSQL/RabbitMQ runtime, python-docx.

## Task 1 Shared reference resolution

- [ ] Add failing tests in `mobile/test/reference_identity_test.dart` for exact ID/name validation, permission errors and UUID-safe display.
- [ ] Implement domain/data/presentation reference lookup in `mobile/lib/core/`, session-scoped via `workspaceAccessProvider`, bind in `mobile/lib/app/dependencies.dart`.
- [ ] Replace raw relationship labels and raw patient/doctor/department entry with names and selectors; preserve IDs on wire.
- [ ] Run focused tests; review session isolation and permission handling.

## Task 2 Clinical mobile

- [ ] Implement and test appointment commands/detail/forms under `mobile/lib/features/appointment/` and medical record workflow under `mobile/lib/features/medical_record/`, mirroring `frontend/src/features/appointment/{api,types}.ts` and `frontend/src/features/medical-record/{api,types}.ts` and current controller roles.
- [ ] Verify method/path/body, required fields, denied roles, current-record checks and stale session suppression with failing-then-passing tests.
- [ ] Connect pages without importing another feature; provide app integration notes.

## Task 3 Lab mobile

- [ ] Implement and test detail, source-based creation, start, cancel, results in `mobile/lib/features/lab/`, matching `frontend/src/features/lab/{api,types}.ts` and live controller roles.
- [ ] Keep legacy paid distinct from clearance. Do not fabricate clearance or emergency approval.
- [ ] Verify result entry, error retry and role gates; provide app integration notes.

## Task 4 Report mobile

- [ ] Replace placeholder under `mobile/lib/features/report/` with daily/monthly/top medicines/outpatient readers from current Report API and role scope.
- [ ] Test exact query dates, numeric parsing, report absence, failed requests and session clearing.
- [ ] Add filters, readable metric cards and name-based department selection.

## Task 5 Integration and existing workspaces

- [ ] Update `mobile/lib/app/dependencies.dart`, `modules.dart`, `router.dart` for new repositories and finished modules.
- [ ] Audit Organization/Patient/Pharmacy/Billing/Notification for visible UUID references and unfinished flows. Add meaningful regressions before changes.
- [ ] Run `mobile/tool/check.ps1`; correct failures without weakening assertions.

## Task 6 Runtime and evidence

- [ ] Start Docker Desktop and preserved Review3 containers, back up demo data and current Word under `.git/`.
- [ ] Build current packaged services and run existing private runtime; verify eight accounts and required gates.
- [ ] Check real workflows through web/mobile, record actual outcomes and capture PNGs by existing report index.
- [ ] Update `docs/documentation_docx/Review3/screenshots/captures.json` and inventory with all new images.
- [ ] Replace only the screenshot section of current `Review3.docx`, preserving other user edits. Render and inspect the entire report; regenerate tracked PDF if appropriate.
- [ ] Review the final diff and report checks, remaining limitations and deliverable paths accurately.
