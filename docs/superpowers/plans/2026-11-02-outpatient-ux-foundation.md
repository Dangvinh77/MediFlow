# Outpatient UX foundation implementation plan

> **For agentic workers:** Use subagent-driven-development for the independent Report authorization task; execute the frontend slice inline, then review specification compliance and code quality separately.

**Goal:** Deliver the first accepted review slice: enforce current department scope for manager report reads; prevent contradictory reception success/stale lists; keep patient identity at clinical decision points; remove technical clutter from Lab.

**Architecture:** Preserve service-owned data. Report obtains current management authority through Core's existing public capability/authority contract, validates the verified actor and fails closed when authority is unavailable; never queries Organization tables. Frontend keeps exact DTOs, authoritative counters, safe retries and scoped reads. No new medical rules or simulated workflow states.

**Tech stack:** Existing Spring Boot/Java, Next.js/TypeScript/Tailwind, JUnit/MockMvc and Vitest.

**Approval:** User approved the preceding product review with “Tiến hành code cải thiện”. This slice implements existing invariants and the review's first priority; remaining 30-item backlog is not considered complete. Branch `codex/outpatient-ux-foundation` starts from merged master `99b80114`.

## Task 1 — Report scope (MF-01)

Owned paths: `backend/report-service/**`; Support/Gateway configuration only if necessary for the real public contract, with exact-path tests. No foreign frontend changes.

- [ ] Add failing tests: manager with no department/hospital scope denied; assigned department allowed; other department denied; same token after revoke denied; invalid/mismatched/unavailable Core authority fails closed; ADMIN behavior retained.
- [ ] Implement a current-authority port/adapter and authorization at every human report-data read boundary, preserving existing role restrictions and disabled/deferred gates. Actor comes from authenticated principal. No cached grant or body/query actor override. Bound HTTP timeout, validate response identity/role/envelope and retain correlation. Reuse live Core wire rather than invent a new report scope table.
- [ ] Run `mvn -q -pl backend/report-service -am test` and host-relevant verification. Record container tests separately if not run.

## Task 2 — Reception consistency (MF-02)

Paths: `frontend/src/features/appointment/components/ReceptionWorkspace.tsx` and its test.

- [ ] Reproduce success followed by stale empty list/capacity. Add regression for stale response after filter/session change and failed refresh after a successful write.
- [ ] After `createRegistration` settles, retain confirmed success and refetch authoritative session/list data without converting read failure into uncertain create. Use request generations/session identity; never decrement capacity optimistically. Block another registration against a stale revision until current data is obtained; a failed refresh retries reads only.
- [ ] Run focused Vitest; ensure existing same-key recovery and follow-up/arrival tests still pass.

## Task 3 — Patient context and Lab presentation (MF-05 / MF-08 / MF-27)

Paths: shared feature-agnostic patient context component if useful; `frontend/src/features/lab/components/**`; `frontend/src/app/(dashboard)/records/[recordId]/lab-order/**`; shared `reference-directory.ts` only for already-authorized label reads.

- [ ] Add regression for patient/date/khoa being present at Lab result entry and lab-order form; changing resource must not retain former patient's draft/data.
- [ ] Add context without inventing identity: resolve patient through existing minimum directory; display explicit unavailable state, never fallback to a different patient. Preserve exact test/record IDs for actions.
- [ ] Main Lab detail shows patient/type/date/care lifecycle and financial status; move contract/version/source/clearance metadata into support disclosure; translate outpatient label and financial waiting copy. Keep cashier handoff accessible.
- [ ] Do not open staff/department searches to LAB_TECH without a verified minimal search contract. Record any remaining owner-specific Pharmacy/UI work in a handoff instead of changing Huy's files.

## Task 4 — Integration and review

- [ ] Run frontend affected tests, `pnpm typecheck`, `pnpm lint`, `pnpm build`; run build after stopping dev.
- [ ] Independent specification review, then code-quality review; resolve important findings and rerun affected checks.
- [ ] Start isolated demo, verify nurse success/list/counter and Lab/context on real UI with screenshots; keep real clock and state clinical-hours limits. Verify Report no-scope failure through Gateway and source authorization tests. Do not claim full patient journey.
- [ ] Update acceptance, review progress and DEMO_UI_TUNG_BUOC.md for changed behavior. Stop owned frontend, run `clean-docker.bat`, verify ports/containers stopped.
- [ ] Commit/push a focused feature branch and create PR. Do not merge automatically.
