# Vinh and Lộc frontend acceptance — 2026-10-20

Implementation began at `a9f06017`. The final branch incorporates the newer master before release
verification; see checks.txt for the final results. Work is limited to Vinh/Lộc features, authorized
shared frontend and the test-only held acceptance fixture. Production backend code is unchanged.

## Signed runtime and browser evidence

Docker Desktop ran disposable PostgreSQL/RabbitMQ, packaged Core/Clinical/Lab/Pharmacy/Billing/
Support plus Gateway/Eureka. Accounts were created by the actual Core issuer with signed roles and
patient links; no fabricated PATIENT claims or direct database identity assignment. All test
patients, diagnoses and transactions are synthetic. Credentials/tokens are not retained here.
Browser requests used same-origin `/api`; the isolated Gateway ran on loopback port 50104.
Only this matched test run enabled the outpatient UI and relevant backend test flags.
Packaged backend/browser runtime used the `a9f06017` source baseline plus the test-only fixture;
the later Report coverage writer is included in the final branch but is not runtime-certified here.

- Clinical source order `0e710190-005a-4384-9813-29650584e21c` was created in the browser from record
  `3c0c37b1-0af1-4bcc-9361-1a778b4818cd`. Its unchanged source context created Lab test
  `cc1c90c6-3661-408a-8abc-f9cc84a8a0d2`.
- CASHIER discovered the LAB_TEST payment request from source/patient/episode, paid 150000 VND and
  refreshed to PAID/remaining 0: [payment screenshot](billing-lab-paid.png).
- Lab received actual clearance `980bd66c-1729-475b-a45c-5a8d079ed28f`; ADMIN started it and recorded
  WBC results to COMPLETED/version 1: [Lab screenshot](lab-completed.png). This caught the legacy
  `paid: false` display bug; V1 now displays clearance independently of that flag.
- DOCTOR added a synthetic diagnosis and completed the record as OUTPATIENT_FOLLOW_UP:
  [record](clinical-record-completed.png). The appointment then showed COMPLETED and the exact
  record link: [appointment](clinical-appointment-completed.png). Initial appointment check-in and
  start were executed by the public API acceptance harness, not manually in this browser run.
- ADMIN sent one manual IN_APP notice: [send](notification-admin-sent.png). Actual owning PATIENT
  opened it: [own detail](notification-patient-own.png). A different signed PATIENT received 403,
  without private content or a retry control: [denied](notification-cross-patient-denied.png).
  [Ten signed role checks](notification-signed-role-checks.json) cover detail/history and privacy
  fields. NURSE is history-only; DOCTOR and cross-patient readers are denied.
- A synthetic prescription was paid then cancelled through public APIs. CASHIER looked up its
  applied refund obligation and recorded an explicitly reconciled 30000 VND refund in the browser:
  [refund screenshot](billing-refund-recorded.png), transaction
  `09efaca8-2f09-4586-b2e0-e941a7b980ff`. No real payment or bank transfer occurred. The UI records
  money already refunded; the backend validates the remaining refundable amount.
- Real CASHIER login now lands on Billing. Component regressions also verify PATIENT/LAB_TECH
  landing and session/hydration behavior.

Adjacent `.txt` files preserve visible DOM snapshots for each screenshot. Reviewer follow-up found
no remaining actionable P1/P2 issues after the invoice fee refresh correction.

## Verification and cleanup

The held `SixServiceOutpatientAcceptanceIT` run ended normally after STOP: **1 test, 0 failures,
0 errors, 0 skips, 1189 seconds, exit 0**. It verifies packaged startup and its API flow; screenshots
and signed checks above provide the additional browser evidence. An earlier hold attempt expired
while work was interrupted; that timeout is not counted as a passing run.
Frontend dev and all disposable backend processes/containers were stopped. Docker Desktop remains
available. No production flag was enabled. Full frontend verification is recorded in checks.txt.

## Explicit release limits

This closes implemented Vinh/Lộc outpatient frontend work within existing public contracts.
Notification resend/read-state controls remain unavailable because no live command exists.
Email/SMS delivery, real financial providers, automatic patient identity selection and external
human-readable selectors are not certified by this synthetic run. Report accepted source coverage
and the broader Huy integration checklist stay in the existing Huy handoff; this evidence does not
close those tasks. Surgery/Inpatient remain deferred. Production activation still requires a
matched backend release and its separate acceptance criteria.
