# Huy FE — actual Gateway/browser checkpoint, 2026-10-21

Candidate: branch `Huy`, base `42aab198` plus the working Gateway coverage allowlist change.
Frontend production source is unchanged in this checkpoint; the Pharmacy stock-form test now
waits for the existing focus effect before asserting focus. No branch, commit or push was made.
Verdict: **HOLD integrated release**, not four tasks closed. Progress remains **10/14 tasks,
49/56 criteria**. This evidence supplements the 2026-10-20 checkpoint without replacing its
manual IN_APP, role or responsive/theme evidence.

## Runtime and data provenance

Docker was available in this run. A source archive of the candidate was packaged separately
to avoid IDE build-output interference. The existing opt-in `SixServiceOutpatientAcceptanceIT`
browser hold started six actual business applications, Gateway/Eureka, disposable PostgreSQL 16
and RabbitMQ. All test users were created through public Core APIs and logged in through the
actual Gateway issuer. No credentials or bearer tokens are retained here. All identities and
clinical data in these artifacts are disposable synthetic acceptance data.

Backend snapshot: `.git/huy-fe-final-20261021`; package log: `.git/huy-fe-final-build.log`.
Gateway endpoint was `http://127.0.0.1:52865`; browser FE was `http://localhost:3183`.
FE settings: `GATEWAY_URL` pointing to that Gateway,
`MEDIFLOW_PHARMACY_PRESCRIPTION_MODE=outpatient`,
`MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=false`. Existing hold-harness activation settings were
used as implemented; Report approval and Notification refund gates were not enabled.

The Clinical public record and source-order responses supplied record, episode, patient,
doctor and department IDs. The drug was created through the public Pharmacy catalog API.
Prescription `priceCode=DRUG` came from the configured Billing prescription catalog policy;
the Clinical Lab price code was not reused for Pharmacy. No authority ID was generated or
inferred by the browser. IDs needed to correlate observations are in the JSON evidence.

## Actual browser observations

| Check | Result / evidence |
|---|---|
| Actual DOCTOR creates Rx V1 from public workflow IDs, 3 units × 10,000 VND | Rx `9d544191-44c5-4286-8a5f-dc3af8d9b2c5`; doctor detail has cancellation, no dispense action |
| Actual PHARMACIST submits unpaid dispense | Backend rejects missing valid clearance; UI displays error/correlation and refreshes, without marking paid. [Screenshot](pharmacy-unpaid-denied.png) |
| Cashier pays through public Billing API, same request/key repeated | One payment transaction returned; API step rather than Billing browser acceptance. [Responses](pharmacy-api-results.json) |
| Pharmacist refreshes and confirms dispense using Enter | Success and terminal state, no further dispense button. [Screenshot](pharmacy-dispensed.png) |
| Public stock read and repeated terminal command | Stock **40 → 37 → 37**; repeat returns existing terminal receipt, not another stock deduction. [Responses](pharmacy-api-results.json) |
| Cancelled prescription | Cancel audit displayed, no dispense action. [Screenshot](pharmacy-cancelled.png) |
| PATIENT owner reads generated notices | Real prescription, payment request, payment receipt, cancellation and dispense intake visible. Payment request explicitly is not a receipt. [History](notification-owner-history.png), [detail](notification-owner-detail.png) |
| Foreign PATIENT opens same notice | Access denied, private content absent, focus on error group. [Screenshot](notification-cross-patient-denied.png) |
| Support pause → Enter refresh | Error with correlation ID, previous content cleared, focus on error group. [Screenshot](notification-support-unavailable.png), [pause result](support-pause.json) |
| Support resume → Tab from error → Enter retry | Actual response reloads; focus returns to notice heading. [Screenshot](notification-support-recovered.png), [resume result](support-resume.json) |
| ADMIN Report UI with outpatient flag OFF | Existing legacy report navigation only; outpatient UI is absent. [Screenshot](report-outpatient-ui-off.png). Does not establish ON/rollback or successful outpatient metrics. |

The paid prescription's actual legacy `paymentConfirmed` value was **true** after this payment.
This run does not reproduce the paid-but-false case; existing V1 contract/component regressions
cover that case. The real run proves initial false/unpaid rejection and paid refresh/dispense.
The unpaid screenshot preserves the earlier rejection state before UI refresh; payment was
subsequently made by the API helper. It is not a claim that the prescription remained unpaid.

## Actual signed API checks

[Signed responses](signed-api-results.json) record method/path/status and bodies without login
responses or secrets:

- Notification detail: owner 200; foreign PATIENT, NURSE and MANAGER 403. History: owner and
  NURSE 200; foreign PATIENT 403. PATIENT/NURSE send commands denied 403.
- Outpatient daily and monthly: ADMIN/MANAGER 404 `REPORT_NOT_FOUND` without accepted coverage;
  DOCTOR 403. Missing coverage was not replaced by zero metrics.
- Packaged Gateway coverage preview: ADMIN reaches the default-off Support surface and gets
  404; MANAGER/DOCTOR get 403. No approval POST was used to fabricate evidence. Separate Gateway
  HTTP regressions verify both newly allowed coverage endpoints with a synthetic Support peer;
  they are not successful packaged coverage acceptance evidence.

## Regression and remaining criteria

- Full FE suite: **594/595 tests, 57 files; 56 files pass, one fails**. Remaining failure:
  `src/features/lab/components/LabDetail.test.tsx`, old-pending-mutation test. Its V1 READY
  fixture lacks `clearanceId`, so the start button is correctly disabled. Production validation
  and assertions were not weakened. Exact-file delegation was requested; no reply received
  at this checkpoint, so Lab remains unchanged.
- Pharmacy stock-form focused suite **17/17 PASS** after waiting for focus effect.
- Typecheck, lint and production build **PASS, exit 0**. Local logs:
  `.git/huy-fe-final-tests-rerun.log`, `.git/huy-fe-final-adjuststock.log`,
  `.git/huy-fe-final-typecheck.log`, `.git/huy-fe-final-lint.log`,
  `.git/huy-fe-final-production-build.log`.
- Hold harness **1/1 PASS, zero failure/error/skip, exit 0**. This was browser hold mode;
  normal harness refund/rollback branches did not run and are not counted as accepted.

| Open task | Still required |
|---|---|
| HF6-09 (1/4) | Reviewed five-source exports/completeness/cursors and real accepted coverage; successful ADMIN/MANAGER reports with reconciled metrics; authorized UI ON/OFF/rollback acceptance |
| HF6-11 (2/4) | Workflow through the accepted Report; remaining complete real session/source-invalid/unavailable/retry matrix on the final candidate. Current partial matrix is recorded above. |
| HF6-12 (3/4) | Full regression PASS and final integrated browser/release verdict after dependencies |
| HF6-14 (3/4) | Refund intake while its consumer remains OFF, plus issuer-supported signed PATIENT missing-claim negative. Core's public account workflow requires patientId. |

No complete final-day/year source evidence was available for
CLINICAL/LAB/PHARMACY/BILLING_CASH/BILLING_REFUND. Creating transactions during this run cannot
attest a completed day or a full calendar year. The production approval writer and Gateway
routes now exist; the missing dependency is source completeness evidence, not another Gateway API.
The single coordination file remains `docs/handoffs/HANDOFF-HUY-MVP.md`.

## Reproduction and cleanup

Build an isolated source snapshot, run the existing Support `six-service-acceptance` profile
with `MEDIFLOW_FE_ACCEPTANCE_HOLD=true`, and use its generated manifest's Gateway, public
workflow IDs and disposable role accounts. Point a FE process at that Gateway with the settings
above. Repeat the browser and public API sequence without enabling additional gates or seeding
coverage. Use the hold command file's PAUSE_SUPPORT/RESUME_SUPPORT for the retry check and STOP
when finished. Do not use these responses as production data or export-completeness assertions.

After this run: browser logged out, System theme retained, temporary viewport reset, FE process
stopped, hold stopped normally with exit 0 and its disposable stack cleaned up. Ports 3183 and
52865 had no listener after shutdown. Local runtime log: `.git/huy-fe-final-runtime.log`.
No change to default deployment gates, original stores, Surgery or Inpatient.
