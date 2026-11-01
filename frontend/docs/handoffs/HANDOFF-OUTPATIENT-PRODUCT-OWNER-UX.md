# HANDOFF: Outpatient product review to feature owners — patient safety and scoped reports

- Status: ACTIVE
- Producer owner: Vinh (`Dangvinh77` / `Harori`), shared UI and all backend contexts.
- Consumer owners: Huy — Pharmacy/Report frontend; Hoàng Anh — Patient/Organization frontend.
- Affected contexts: Pharmacy/Report and Patient/Organization frontend only.
- Affected paths: `frontend/src/features/pharmacy/**`, `frontend/src/features/report/**`, `frontend/src/features/patient/**`, `frontend/src/features/organization/**` and their owned routes.
- Verified baseline: review source `f3850620`, implementation base `99b80114`; [review and screenshots](../../../docs/reviews/2026-11-01-outpatient-product/REVIEW_UI_UX_NGOAI_TRU.md).

## Huy — concrete remaining work

1. Report: remove hospital-wide selection for MANAGER/department heads. Read current management departments from Core capabilities, require an explicitly selected permitted department, clear results on scope/session changes and on 403. Do not show denied/unavailable values as zero. Backend now rechecks live authority on every read; this handoff does not grant extra endpoint roles.
2. Pharmacy: show resolved patient name/date of birth and selected prescription at the final dispense decision. Keep exact IDs for commands. Use existing minimal care-label resolution for doctor/department where permitted; do not open staff/department searches to PHARMACIST.
3. Prescription entry: reduce opaque identifier handoff while preserving Clinical source record and episode authority. Do not infer an episode from patient identity or a historical record.

## Hoàng Anh — concrete remaining work

1. Patient form: clarify that current `01-12345678-9` BHYT validation is a demo/domain limitation, not a verified production Vietnamese insurance format. Coordinate a separately reviewed backend migration before changing wire validation; do not silently rewrite insurance identifiers.
2. Patient account issuance: make optional portal activation and first-login password change clear, preserve the nurse's direct continuation to reception, and prevent duplicate account issuance.
3. Organization: make empty manager scope visible as no authority; clearly distinguish staff specialty, department-head appointment and system-account privileges. Preserve doctor practice when appointments end.

## Acceptance and compatibility

- Screenshots at clinical decision points show the correct person and visit; stale names/results/drafts do not survive a changed resource/session.
- A manager with no scope cannot request hospital-wide reports; revoked scope is denied with the same token. ADMIN behavior remains unchanged.
- No raw UUID is required as the default user workflow where an authorized selector exists; technical identifiers remain available for support.
- Test through Gateway with each actual demo role. A disabled/unavailable capability is shown honestly; no invented aggregates or medical rules.
- Resume owner: each frontend owner implements their own context, Vinh validates shared integration and backend gaps. This document is a dependency record, not authorization to edit another owner's frontend.
