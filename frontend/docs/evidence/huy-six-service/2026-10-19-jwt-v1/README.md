# Huy JWT / Pharmacy V1 — candidate evidence, 2026-10-19

FE code `9a00d7f1749f141ed57544c998587dc04e269375`, base master
`5ccecf83a628c855beb8c4de363704dd0cc03efc`; backend code
`5334aa8cdda83f749915997b11ff824b12624fbc` from security PR
[#445](https://github.com/Dangvinh77/MediFlow/pull/445). FE PR
[#446](https://github.com/Dangvinh77/MediFlow/pull/446) adds this evidence after the tested code;
no production changes follow that code checkpoint.

## Environment and source authority

Disposable PostgreSQL 16/RabbitMQ; packaged Core, Clinical, Lab, Pharmacy, Billing and Support,
plus Gateway/Eureka. Browser Next dev server 3182 proxies same-origin `/api/*` to the real Gateway.
No HTTP mocks, token injection into browser storage, backend payment boolean patch, or changes to
existing Compose volumes. Staff sessions use actual public Core issuer logins. Fixture people,
contacts and IDs are synthetic; no access tokens, passwords, cookies or real patient data are saved.

The existing opt-in six-service test profile supplies isolated test configuration. Its existing
test flags do not authorize rollout or full V2; production/default gate configuration is unchanged.
`MEDIFLOW_PHARMACY_PRESCRIPTION_MODE=outpatient`; Report FE UI gate stays OFF.

[Candidate/source lock](candidate-and-sources.json) records public MedicalRecord and genuine test
Lab-order responses, the fixture Billing allowlist and packaged JAR hashes. The explicit episode
comes from the Lab order, not an inferred equality with appointment/record IDs. A production workflow
must not create an unnecessary Lab order merely to obtain the episode. The public Clinical episode
entrypoint and production price-code selection remain dependencies in the consolidated Huy handoff.

## Checks actually executed

| Surface | Observed result | Evidence |
|---|---|---|
| Browser, issuer DOCTOR | Create V1 succeeds; detail has neutral payment text and no dispense button | `01-doctor-created.png/.txt` |
| Browser, issuer PHARMACIST | Unpaid dispense denied with real clearance error and correlation; no client paid/dispensed state | `02-unpaid-denied.png/.txt` |
| Public API, issuer CASHIER | Payment and duplicate both 200; same payment transaction. Paid GET remains `paymentConfirmed=false`, ACTIVE/PENDING; stock remains 40 | `payment-api-checks.json` |
| Browser, issuer PHARMACIST | Manual refresh keeps V1 dispense available despite false; explicit confirmation succeeds | `03-*`, `04-*`, `05-*` |
| API reconciliation after browser dispense | Terminal retry returns stored dispense ID; stock 37; harness asserts one slip, one filled outbox, one Clinical attachment | `dispense-api-checks.json`, `dispense-event-identity.json`, overlay assertions |
| Browser terminal reload | FULFILLED/DISPENSED; no dispense control | `06-terminal-system.png/.txt` |
| Signed API authority matrix | Foreign doctor create/cancel 403; PHARMACIST cancel 403; assigned doctor cancel 200, releasedReservations=1; DOCTOR dispense 403 | `candidate-and-sources.json`, `payment-api-checks.json` |
| Browser ADMIN | Canceled prescription has no cancel/dispense control | `10-cancelled-admin.png/.txt` |
| Keyboard/theme/layout | Enter opens, initial Close focus, Escape returns trigger, Tab reaches confirm, success status receives focus; Light/Dark/System, desktop and 320px; document width 305 at viewport 320 | screenshots/AX `02`–`06` |
| Browser NURSE, real Support intake | Correct patient history includes prescription recorded, payment requested/received and medicine supplied notices | `07-notification-nurse.png/.txt` |
| Browser NURSE Report | Role gate denies access | `08-report-nurse-denied.png/.txt` |
| Browser ADMIN outpatient Report | Gate OFF shows unavailable, no zero report | `09-report-gate-off.png/.txt` |

The warning in screenshots `03`/`04` is feedback from the earlier unpaid command, retained with
its correlation. A fresh GET cannot establish V1 payment. The subsequent confirmed command succeeds;
terminal reload clears prior command feedback. No screenshot treats the legacy boolean as proof of payment.

Payment is a real **API harness** operation, not a Billing browser test. Notification EMAIL in the
history uses the fixture delivery setup, not proof of a real external email provider.

## Code verification

- Red V1 component batch: 11 failures / 22 tests before implementation.
- Final focused Pharmacy + matching routes: **233/233 tests, 21 files**.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: **PASS**, exit 0.
- Full FE: **529/532 tests, 57 files**; existing Lab start-control fixture fails, two Inpatient
  cases time out. Separate rerun of those two files: **19/20**, Inpatient passes, Lab failure
  persists. No foreign source, fixture or timeout setting was changed to hide the failure.
- Backend #445: Pharmacy **843/843**, Report **1,407/1,407**, Support composition/security
  **12/12**, no failures/errors/skips; real PG/Rabbit integrations in the module suites.

Commands for FE checks:

```powershell
pnpm test -- src/features/pharmacy 'src/app/(dashboard)/pharmacy/prescriptions'
pnpm test
pnpm test -- src/features/lab/components/LabDetail.test.tsx src/features/inpatient/components/CreateAdmissionForm.test.tsx
pnpm typecheck
pnpm lint
pnpm build
```

## Reproducing the isolated browser environment

The [test-only overlay](browser-harness.patch) adds an explicit hold/command channel to the existing
six-service acceptance fixture; its normal acceptance branch remains unchanged. It is evidence,
not a production Support change. Apply it only to a disposable verification checkout with both PRs:

```powershell
git apply --unidiff-zero frontend/docs/evidence/huy-six-service/2026-10-19-jwt-v1/browser-harness.patch
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service,backend/gateway,backend/eureka-server -am -DskipTests package
$env:MEDIFLOW_FE_ACCEPTANCE_HOLD='true'
mvn -q -pl backend/support-service -am -Psix-service-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

The manifest and command/result files are local under `backend/support-service/target/six-service-acceptance/`.
`PAY` uses real public Billing APIs, waits for the local clearance projection, asserts GET false
and no stock deduction. `VERIFY_DISPENSE` checks the effects after browser dispense. `STOP` ends the
hold and cleans up child processes/containers. The hold run is **not** the normal packaged refund,
rollback and cutover acceptance run: its early return skips that branch. Vinh owns that final regression.

## Verdict and remaining acceptance

The blocking V1 boolean defect is fixed and demonstrated with a paid real prescription whose GET
still returns false. This closes HF6-06.2/.3 only: **6/14 task, 41/56 subtask**. Handoffs remain OPEN.

Still pending: browser expired/revoked/Billing-unavailable matrix; signed V0 runtime; complete
keyboard/status matrix; Report ADMIN/MANAGER with genuinely accepted coverage through Support;
issuer PATIENT owner/cross-patient Notification denial; final shared packaged refund/paid regression
and Vinh's joint acceptance. Unit/component tests for error fixtures do not replace these proofs.
No gates, Surgery, Inpatient or full V2 workspace were enabled.
