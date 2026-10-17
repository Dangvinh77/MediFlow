# Frontend Vinh and Flutter foundation implementation plan

> Execute task by task with subagent-driven-development; preserve existing working changes.

**Goal:** Continue Vinh frontend and give four developers one runnable real-backend Flutter app.
**Approved design:** User approved Riverpod/GoRouter/Dio/SecureStorage architecture with real backend only, no demo. Existing Flutter blueprint remains authoritative. Shared mobile framework and empty owner modules are explicitly assigned by this task; foreign backend/frontend business code stays read-only.
**Architecture:** Flutter presentation → pure Dart domain ← data, shared Gateway client, secure session store, role-aware router. Context features remain separate. No production business mutations or inferred contracts in owner placeholders. Frontend retains current theme and V1 activation gates.
**Stack:** Existing Next/TypeScript; installed Flutter/Dart, resolved package versions saved in pubspec.lock.

## A. Frontend (independent worker)

- [x] Audit appointment/record/Lab lists and create/edit forms against current source.
- [x] Add failing regressions for stale/unmount, duplicate submit, submitted-filter pagination and role/error states as applicable.
- [x] Fix confirmed defects within Vinh feature paths; preserve earlier detail fixes.
- [x] Run focused/full tests, typecheck/lint/build, update workboard with fresh results.

## B. Flutter (primary)

- [x] Generate Android/iOS/web/Windows hosts without replacing existing loader package.
- [x] Test Gateway URL validation, envelopes/failures, auth serialization and session lifecycle before implementation.
- [x] Implement shared network/config/errors, secure token storage, auth repository/use cases/controller and login.
- [x] Implement role router, adaptive home/navigation, explicit unimplemented owner workspaces and actual read-only Vinh appointment/Lab slices using locked live contracts.
- [x] Divide feature folders and document four-owner contribution workflow, run commands, local Gateway address/CORS/HTTPS and verification.
- [x] Run format/analyze/tests and web build; inspect runnable UI. Report native toolchain/backend runtime limits honestly.

## C. Closeout

- [x] Independent spec and code-quality review; fix findings and rerun affected checks.
- [x] Update plan and evidence; implementation evidence recorded; subsequent user request authorizes commits, push and PR, with no deployment.


## Final evidence

Frontend: full 363/363 tests; focused 57/57. Review caught missing Lab test type import; restored, reproduced pre-fix TS2304, fresh typecheck/build and Lab 4/4 passed. Workboard contains correction.

Mobile: format clean, analyzer clean, 28/28 tests, web build pass. Browser login and required fields checked at localhost:4300. See mobile/docs/verification-2026-10-17.md. Gateway was unavailable (503), so authenticated smoke remains open; native builds require missing platform tooling.
