# Vinh — outpatient frontend implementation

Approved scope: continue Vinh's remaining FE work from the ownership audit. Keep the existing
theme and feature boundaries. User explicitly limits this session's execution checks to build.

## Design and implementation plan

1. Extend `src/lib/api.ts` with a narrow mutation option for `Idempotency-Key`. Keep the existing
   signatures compatible, bearer controlled by session, and bodyless requests bodyless.
2. Server pages read `MEDIFLOW_OUTPATIENT_UI_ENABLED` (default false) at runtime and pass a
   boolean to Clinical/Lab components. The flag is UI configuration, not backend activation.
3. Appointment detail: ADMIN/NURSE check-in for PENDING/version 0; ADMIN/DOCTOR normal start
   for READY_FOR_EXAM/version 1 with server clearance. Show waiting state and manual read refresh.
   In MVP UI mode hide legacy arrival. Locate the server-created record through the existing
   patient-record read and exact appointmentId match, without inventing a record ID.
4. Record detail: ADMIN/DOCTOR complete an OPEN, appointment-backed record with diagnoses.
   Only outpatient dispositions are offered. Link to a Clinical Lab-order workspace.
5. `/records/[recordId]/lab-order`: app composition joins two authorized Vinh features, without
   feature-to-feature imports. Clinical creates the immutable source order; Lab submits the
   exact returned identity. Keep the order in page memory, never URL/localStorage. Preserve
   it after Lab errors. Block blind re-submission after an unknown Clinical create outcome.
6. In MVP UI mode hide legacy standalone record/Lab creation, preserve default-off compatibility.
7. Review the diff, run `pnpm build`, update workboard and guides, commit as the configured
   human author and open a focused FE PR. Do not run unit/component/browser/backend tests,
   standalone lint/typecheck, or activate any backend/publication flags in this session.

## Explicit completion limits

Build proves compilation/type validation and route generation only. Signed-role browser/Gateway
acceptance remains open. Rx attachment has no public Clinical read contract in this baseline;
do not add a guessed endpoint or import Pharmacy. Do not implement Inpatient/Surgery.
Shared idempotency producer can be implemented here; its consumer acceptance remains Huy's work.

## Implementation outcome

All seven implementation steps have source changes in this PR. The new shared command helper
locks requests synchronously, ignores late unmounted responses and keeps 4xx rejection separate
from unknown results (network, 5xx, malformed/empty successful response). The latter cannot
blindly repeat a non-idempotent create. Appointment check-in/cancel share one lock in enabled mode.

Static peer review found the empty-success unknown-outcome case; it was corrected before final
build. No remaining blocker was found. The first build failed on a missing required AsyncState
onRetry prop in the new record lookup; corrected, then build passed. A final build covers the
subsequent unknown-outcome correction and the unified appointment action lock.

Open acceptance remains explicit: no new behavioral regression tests were added/run; no
standalone lint/typecheck, browser or real Gateway smoke in this session. FE-VINH-MVP-01/04
remain open; FE-02/03 have UI implementation but are not signed off for release. Shared header
handoff stays active until both producer tests and Huy's consumer acceptance pass.

Final verification: `pnpm build` PASS, exit 0 on 2026-10-18, Next.js 16.2.11.
Compilation and Next's integrated TypeScript phase passed; 30 static pages generated and the new
`/records/[recordId]/lab-order` route is dynamic. No standalone typecheck was invoked.
Final static peer review: GO for PR, with runtime acceptance still open. `git diff --check` passed.
