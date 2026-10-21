# Huy FE — signed Gateway acceptance, 2026-10-19

Candidate: `51eedfcd` on `Huy` plus the uncommitted Pharmacy/Support working diff.
All records/accounts in this folder belong to disposable local acceptance data. No credentials,
tokens, signing keys or real patient records are included. This is not production rollout evidence.

## Pharmacy payment-read defect

The first actual browser run created a V1 prescription successfully. The actual CASH payment and
duplicate payment both returned HTTP 200, but Pharmacy detail stayed `paymentConfirmed=false`
for the full 30-second assertion window. The optional browser acceptance run therefore failed;
it is not counted as a successful workflow. V1 Billing clearance was not being read: detail only
checked the legacy receipt repository.

The repair reads verified, matching, non-expired V1 clearance projections in a read-only
transaction. It does not promote pending targets, acquire write locks, create legacy receipts,
dispense stock or bypass the command's current Billing authority lookup. Legacy receipt semantics
are retained. The wire DTO and frontend payment guard are unchanged.

## Backend regression evidence

- Initial focused run after a clean build: 40/40, no failures/errors/skips.
- Full Pharmacy run: 846 tests in 108 classes, zero assertion failures, eight errors in
  `PrescriptionCareEventWriterPostgresTest`. A class file disappeared while the test context
  read it. A subsequent attempt hit an invalid class-file body in the IDE's shared output.
  See [first-run totals](pharmacy-full-first-run.json); this run exited 1.
- A source-identical build snapshot under `.git/codex-fe-verification-20261019` avoids shared IDE
  outputs. Focused rerun: 48/48 across the two affected service/persistence suites and the eight
  previously errored cases, no failures/errors/skips; package exited 0.
  See [rerun results](pharmacy-isolated-rerun.json) and [source hashes](backend-source-hashes.json).
  Counts overlap: do not add the 40 or 48 to the 846 unique tests, or describe the initial full
  invocation as exit 0.
- The isolated Pharmacy executable was copied to the normal ignored `target` artifact.
  The disposable browser environment uses the isolated executable.

Latest frontend checks for the unchanged FE candidate remain the plan section 13 checkpoint:
551/552 tests, with the known unrelated Lab fixture failure; typecheck, lint and production build
passed. This turn changes the Pharmacy backend read behavior and the opt-in Support test harness,
not the frontend production implementation.

## Disposable runtime and reproduction

The browser environment is the opt-in branch of
`SixServiceOutpatientAcceptanceIT`, with `MEDIFLOW_FE_ACCEPTANCE_HOLD=true`.
It starts the actual packaged Core, Clinical, Lab, Pharmacy, Billing and Support applications,
Gateway and Eureka against disposable PostgreSQL/RabbitMQ containers. The source-identical
snapshot avoids IDE writes to shared Maven outputs. Its generated manifest records public
workflow IDs and role-check responses, never login tokens or passwords.

```powershell
$env:MEDIFLOW_FE_ACCEPTANCE_HOLD = 'true'
mvn -q -pl backend/support-service -am -Psix-service-acceptance `
  '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

Run the frontend on loopback with `GATEWAY_URL` taken from the freshly generated manifest and
`MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=false`. The harness's `fe-browser-command.json` supports
explicit `PAY`, `VERIFY_DISPENSE`, `VERIFY_REPLAY`, `PAUSE_SUPPORT`, `RESUME_SUPPORT` and `STOP`
operations. Browser mutations remain actual UI actions; `PAY` is a test-only API CASH ledger
operation. These are synthetic acceptance records, with no payment provider or real patient data.
`STOP` finishes the hold and cleans its application processes and containers. A successful hold
does not execute the normal test branch's Support rollback assertions.

The 09:45 hold expired after its 60-minute window and failed with
`FE browser acceptance timed out; disposable processes will be stopped`. It is not a PASS.
The subsequent restart initially encountered Docker's `dockerInference` startup error.
After the user restarted Docker, the engine reported version 29.6.2 and a fresh run was started.
Later acceptance results must identify that fresh manifest rather than reuse the expired gateway.

## Fresh signed run — PASS

Manifest unique `63684242eda5`, generated 2026-10-19 14:38 local time (UTC+7).
Gateway `http://127.0.0.1:60224` was ephemeral and has now stopped. The final opt-in hold
finished through explicit `STOP`: **1 test, 0 failures, 0 errors, 0 skipped, 770.5 seconds,
Maven exit 0**. See [the Failsafe report](signed-browser-hold-result.txt).
This closes HF6-01/06/07/10: **10/14 tasks, 45/56 criteria** in the current FE plan.

| Step | Execution and result | Evidence |
|---|---|---|
| Authority sources | Public Clinical record/order and Core setup responses; episode explicitly returned by the order. `DRUG` allowed only in disposable Billing config. | [Source manifest](source-manifest.json) |
| Role checks | Assigned DOCTOR cancel 200/releasedReservations=1, foreign DOCTOR create/cancel 403, PHARMACIST cancel 403. Actual issuer staff accounts. | [Source manifest](source-manifest.json) |
| V1 create | DOCTOR browser validates/focuses fields, adds/removes lines, submits quantity 3. Server Rx `26161972-6b4e-433e-84a6-bc8d98d0f4c7`; unpaid/PENDING. | [Browser observations](browser-checks.json), [current created Rx](rx-created-current.jpg) |
| Payment | Public API CASHIER payment 30,000 VND and duplicate both 200 with same transaction. DOCTOR dispense 403, unpaid PHARMACIST dispense 422. Paid fresh read remains PENDING, stock 40. | [Payment result](pay-result.json), [paid Rx](rx-paid-pending.jpg) |
| Dispense | PHARMACIST browser refresh, confirmation/Escape/return focus, then manual dispense. One slip/event, stock 37, one Clinical attachment. Duplicate terminal command 200 preserves effect. | [Verification](dispense-result.json), [terminal view](rx-dispensed-dark.jpg) |
| Catalog/stock | Actual ADMIN browser search empty/reset/page sizes, creates test drug `662107e4-7994-4dfc-95e8-becc4a380c2c`, adjusts 5+7→12 using server response. | [Browser observations](browser-checks.json), [public stock GET](report-role-stock-checks.json), [stock screenshot](stock-adjusted.jpg) |
| Replay | ADMIN browser explicitly confirms known event `9ab5df4c-0cbe-41dd-97f6-7131abfb004c`. Payload hash unchanged; observed Clinical attachment/Report journal/Notification notice/dispense slip all 1, stock 37. This is not universal consumer-ACK fault proof. | [Origin/hash](replay-origin.json), [result](replay-result.json), [UI](outbox-replayed.jpg) |
| Legacy Report | DAILY/MONTHLY/TOP_MEDICINES signed ADMIN 200, DOCTOR 403. Browser zero/empty, invalid date/UUID/limit focus, Back/Forward submitted filters, denied role. | [HTTP evidence](report-role-stock-checks.json), [browser checks](browser-checks.json), [daily](report-daily-light.jpg), [denied](report-doctor-denied.jpg) |
| Failure/recovery | Pause only disposable Support JVM; real Gateway unavailable, named error group focused with correlation `80b14a90-ae78-458a-b234-264461ef6580`. Restart same jar/stores/flags, Enter retry loads report. | [Pause](support-paused.json), [resume](support-resumed.json), [error view](report-unavailable.jpg) |
| Keyboard/layout | V1 required fields/tab/add-remove/Enter/loading, cancel/dispense reason/focus/Escape/status; Report filter errors and retry. Light/System mobile 320px and Dark tablet 768px have no page overflow. Desktop System uses approved frame at 1366px. | [Observations](browser-checks.json), [mobile Rx](rx-mobile-light.jpg), [mobile Report](report-mobile-system.jpg), [tablet](report-tablet-dark.jpg), [desktop catalog](catalog-desktop-system.jpg) |
| Outpatient gate | `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=false` throughout. Direct route renders status/backlink, no query controls. No accepted outpatient coverage or MANAGER acceptance is claimed. | [OFF screenshot](report-outpatient-off.jpg) |

The old `rx-created-unpaid.jpg` belongs to the first failing payment-read run; it is retained only
as historical defect evidence. Use `rx-created-current.jpg` for the successful manifest above.
The outbox Escape observation's empty `focusText` comes from an input's empty text content;
component tests cover input return focus. Report legacy invalid inputs focus and reference error
text but currently have no `aria-invalid` attribute; do not describe that attribute as observed.

## Boundaries and cleanup

Source setup, allowed/denied API checks, CASH payment and effect verification use public APIs or
test-harness assertions; user-facing Pharmacy mutations and Report interactions use the actual
browser. This is a mixed browser/API acceptance, not an all-browser journey. Report outpatient
coverage/ON, MANAGER, Notification signed PATIENT/NURSE privacy/manual send and final release are
still open (HF6-09/11/12/14). The normal Support standalone rollback branch was not rerun here.

The isolated reactor `clean package` passed with tests deliberately skipped to build fresh jars;
the 48/48 rerun and final opt-in acceptance are separate executions. Backend source hashes match
the working tree and isolated snapshot. No full FE or full Pharmacy green result is asserted.

After PASS, the harness stopped its owned applications and disposable containers. The FE dev
server was stopped; port 3180 has no listener. Docker Desktop remains running. Browser session
was logged out, theme returned to System and viewport override reset. No commit or push was made.
