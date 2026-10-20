# Huy FE closeout — 2026-10-20

Candidate: `44ff1ed7` plus the uncommitted Notification test timing correction. Work stays on the
current branch; no new branch, commit, push or PR is created by this closeout run.

## Report source audit

Compared local source with fetched `origin/master` at `71328cbc`. Master has merged Notification
history PR #466 and backend audit PR #464; it adds no outpatient coverage acceptance writer.
No master changes were merged into this candidate during the audit.

- `backend/report-service/src/main/resources/db/migration/V27__outpatient_read_coverage.sql`
  creates an empty coverage table and requires owner-reviewed source-export/reconciliation.
- `OutpatientCoverage.REQUIRED_SOURCES` is exactly CLINICAL, LAB, PHARMACY, BILLING_CASH,
  BILLING_REFUND. Acceptance requires source references, actor/reference, ordered timestamps,
  exact scope/zone/currency and local evidence fingerprint.
- `sourceObservedAt` must cover the end of the complete final business day. Today's newly
  generated transactions cannot establish complete coverage for today before that day ends.
- `OutpatientReportReadPersistenceAdapter` reads/checks coverage; a local fingerprint does not
  prove upstream completeness. No public/API/operation writer was found in production source.
- Direct inserts found in `OutpatientReadPostgresTest` are isolated fixtures, not accepted source
  exports or authority for FE acceptance. Financial/V2 archive APIs do not grant MVP coverage.
- The same explicit limitation remains in master `backend/report-service/README.md`, lines 52–57.

HF6-09.2/.3/.4, the successful Report leg of HF6-11.2 and final HF6-12.4 cannot be certified from
these fixtures. Existing default gates remain unchanged. The consolidated Huy handoff tracks
the external acceptance dependency; no additional handoff is created.

## Regression

Full default run: **585/591 PASS**, six failures (one Lab fixture, four deferred Inpatient
timeouts, one Notification focus assertion racing React's effect). Corrected the Notification
test to wait for focus rather than only for the group's DOM presence; production focus and
security behavior are unchanged.

Full rerun `pnpm test -- --maxWorkers=2 --testTimeout=15000`: **590/591 PASS, 57 files**,
one remaining failure at `LabDetail.test.tsx:194` (fixture does not expose the expected
`Bắt đầu xét nghiệm` action). All Notification and deferred Inpatient cases pass in this rerun.
This is a failing full-suite invocation, not a full PASS; Lab source/tests remain untouched.

Focused Pharmacy/Report/Notification: **446/446 PASS, 31 files**. Typecheck, lint and production
build all **PASS, exit 0**. Full-suite failure above remains a release blocker; focused tests
and build do not replace browser acceptance.

## Runtime isolation

Backend executable build succeeded from a tracked-source archive under ignored
`.git/huy-fe-closeout-20261020`, avoiding IDE writes to shared class outputs. It uses the
existing `SixServiceOutpatientAcceptanceIT` opt-in hold against new disposable PG16/RabbitMQ
containers. Backend production, harness, Compose, migrations, shared auth and rollout flags
were not edited. The opt-in hold does not prove the normal branch's refund/rollback assertions.
The hold finished **1 test, 0 failure/error/skip, Maven exit 0** after explicit STOP. This result
certifies that hold branch only; the normal refund/rollback branch was not executed in this run.
FE server was stopped, browser logged out, System theme restored and viewport override reset.

## Actual Gateway / Support browser acceptance

Run `161b9e8196d7`, Gateway `http://127.0.0.1:62256`, FE `http://127.0.0.1:3182`.
All identities and records are disposable synthetic test data. ADMIN/DOCTOR/PHARMACIST/NURSE
come from the existing harness; ADMIN created owner/foreign PATIENT and MANAGER accounts through
public APIs. All runtime role checks below use actual issuer login, not injected browser roles.
No credentials, bearer tokens or signing keys are stored here.

| Observation | Result / proof |
|---|---|
| ADMIN manual IN_APP | Enter submits once; pending controls disabled; response, detail and history agree. ID `8e992b96-37d6-4901-ac36-e2ca5ea165e6`, status SENT. Two history reads show the same one manual entry among seven notices; GET refresh/retry never invokes resend. `notification-admin-detail.png` |
| PATIENT owner | Own history and manual/lifecycle/payment detail visible. `notification-owner-history.png`, `notification-owner-detail.png`, `notification-owner-lifecycle.png`, `notification-owner-payment.png` |
| Foreign PATIENT | Own signed identity receives 403 for the first patient's history and detail, private content absent and no retry. `notification-foreign-detail-denied.png`, `notification-foreign-history-denied.png` |
| NURSE | History allowed, detail/send links absent; direct routes denied. Signed public API returns history 200, detail/send 403. `notification-nurse-list.png`, `notification-nurse-detail-denied.png`, `notification-nurse-send-denied.png` |
| Lifecycle/payment privacy | Real backend-created cancellation `b91ee385-87dd-4766-a1b2-e2edd346be23` and EXAM payment `7ae7de5b-e1ac-442f-a82a-df44c774aa31` visible to owner; same signed detail matrix owner 200, foreign/NURSE/MANAGER 403. Payment request notice is explicitly a request, not a receipt. |
| Support unavailable / recovery | Pause only this disposable Support process; refresh focuses named error. Resume same artifact/stores; Tab reaches Thử lại, Enter GET recovers and focuses notification heading. Final failure correlation `a3c34eb6-15be-4407-8972-b443c62940ed`. `notification-error-mobile-dark.png`, `notification-recovered-mobile-dark.png` |
| Themes / keyboard | Light/Dark 320px detail: document width 320px, readable content and wrapped UUIDs. System 320px history: document 305px, wide table scrolls locally. Desktop System captured at 1366×900. Enter submit/refresh and Tab/Enter error retry verified. `notification-detail-mobile-light.png`, `notification-detail-mobile-dark.png`, `notification-history-mobile-system.png`, `notification-detail-desktop-system.png` |
| Report missing coverage | Signed ADMIN/MANAGER daily and monthly return 404 REPORT_NOT_FOUND; DOCTOR returns 403. No success, zero-valued report or accepted coverage inferred. Outpatient UI remains OFF. |

Safe public API response evidence is in [signed-api-checks.json](signed-api-checks.json).
`notification-history-mobile-light.png` was captured at 674px; its filename alone must not be
used as 320px proof. The explicit 320px Light detail capture above is the narrow-screen evidence.

## Remaining dependencies / release verdict

**HOLD integrated release.** Close HF6-11.4 and HF6-14.1/.2/.4: **49/56 criteria (87.5%),
10/14 tasks (71.4%)**. HF6-14.4 records UI checks and this HOLD verdict, not global release approval.

- HF6-09.2/.3/.4: no accepted five-source coverage/export/reconciliation or production acceptance
  writer; source audit above explains why isolated fixtures cannot close them.
- HF6-11.2/.3: Report success leg and full session/unavailable/retry matrix remain incomplete.
  This run verifies Notification recovery/privacy; it does not repeat the prior Pharmacy dispense
  browser workflow or certify the global failure matrix.
- HF6-14.3: lifecycle/payment are observed, but refund consumer is OFF by default and not enabled
  by this existing hold. Refund notice and signed missing-patient-claim denial remain unverified.
  No accountId fallback is added. EMAIL/SMS SENT from a local adapter is not external provider
  delivery acceptance; no real recipient was contacted.
- HF6-12.4: pending those acceptance conditions, known Lab full-suite failure and the existing
  shared late-401 session dependency. No shared auth, backend consumer or rollout gate changed.

All unresolved items remain in the single consolidated `docs/handoffs/HANDOFF-HUY-MVP.md`.
