# Role UX refinements implementation plan

Approved scope: user requested implementation of the 31 October role UI audit. Preserve source contracts, default-deny permissions, idempotency and deferred Surgery/Inpatient. Existing checkout is clean except local audit evidence; use branch codex/role-ux-refinements without recreating deleted worktrees.

Architecture: retain Next feature boundaries and public Gateway contracts. Prefer targeted interaction fixes to redesign. No fabricated report values or cross-context authority. Read owned source and applicable rules; add regression tests before behavior changes, verify red then green.

- [x] Backend/operations: Clinical missing query parameters return 400 with field evidence; cleanup tolerates disappearing containers and retains image candidates. Diagnose report readiness using existing source, fix a demonstrable defect or document the specific acceptance prerequisite.
- [x] Appointment/Clinical: required valid date ranges with Vietnam today defaults; initial read-only calendar/registration loads; split browse/create schedule UI; keep selected patient/lượt visible and primary record actions reachable; clear follow-up source labels. No direct prescriptions without live public contract.
- [x] Notification/Report: patient-specific list/detail without delivery internals or denied directory-name text; safe links to own results/registration, Vietnamese copy. REPORT_NOT_FOUND is a readiness state with correct scope, never zero. Preserve staff diagnostic view.
- [x] Shared shell/references and Organization: feature-gated role landing, exactly one nav active, full-width reference pickers, patient-friendly shell, linked-person account context and contextual scope entry if existing component permits it. No privilege changes.
- [x] Integration: focused tests, TypeScript/lint, review spec then code quality; live screenshot smoke on new paths, update audit with verified status, stop owned demo and clean Docker. Create a focused PR after verification under the user's standing PR authorization.

Ownership during this session: backend/operations worker owns backend/clinical-service + scripts/demo_25_10 and report diagnosis; appointment worker owns frontend appointment/medical-record and their routes; notification worker owns frontend notification/report and their routes. Root owns shared shell/lib/references, Organization paths and this plan/evidence. Do not commit workers' shared working tree; root integrates.

Completion evidence and remaining acceptance limits: [AFTER.md](../../acceptance/role-ux-2026-10-31/AFTER.md). Implementation checklist completion does not certify the unexecuted full medical workflow or mobile/performance acceptance.
