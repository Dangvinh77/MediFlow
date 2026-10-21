# Huy — refund delivery and final regression, 2026-10-21

## Final FE verdict — isolated acceptance complete

**14/14 tasks, 56/56 criteria; FE READY FOR REVIEW. Production rollout HOLD.** This final
checkpoint supersedes the 11/14 FE progress later in this historical refund/regression record.
The final candidate additionally includes Support test harness/initializer and this evidence;
no FE production source changed after the 595-test regression. No branch, commit or push.

The fresh packaged scenario used Gateway `127.0.0.1:57283`, PostgreSQL/RabbitMQ and actual
Core-issued ADMIN/MANAGER/DOCTOR logins. All authority IDs came from public workflows.
After payment/dispense/refund projections settled, the harness stopped Clinical/Lab/Pharmacy/
Billing writers and retained five immutable command journals under `report-source-exports/`.
Their exact SHA-256 hashes match the approval in `report-calendar-api.json`. These journals
prove the finite scenario, **not production source exports or historical completeness**.
CLINICAL/LAB have zero completed evidence rows because this scenario has no completed visit
or Lab order; this does not certify a positive paid Lab workflow.

A separate test-classpath helper advances only Report's application Clock to 2027-01-02 in
Asia/Bangkok, allowing the scenario's calendar year to close. It is absent from the production
Support JAR. System, JWT and source clocks remain unchanged. No SQL coverage seed or relaxed
validation. `report-clock-boundary.json` proves wall time refuses that future coverage404,
then the isolated test Clock returns200. Source gates remain default OFF.

| Final check | Actual result / evidence |
|---|---|
| ADMIN approval via Gateway → Support |200, exact-request retry unchanged; MANAGER approval403; `report-calendar-api.json`. |
| ADMIN/MANAGER daily and monthly browser |Both roles successful, 12 months;1 Rx,3 units, cash160,000/refund30,000/net130,000 VND; `report-admin-daily-metrics.png`, `report-admin-monthly.png`, `report-manager-daily.png`, `report-manager-monthly.png`. |
| Forbidden role |DOCTOR API403 and browser access denied; `report-doctor-denied.png`. |
| Missing coverage |404, no success results/zero fallback, focused error, retry denied and corrected scope recovers; `report-missing-coverage.png`. |
| Keyboard and layout |Enter submits daily/monthly;390×844 mobile Light/Dark, no horizontal overflow; `report-manager-mobile-light.png`, `report-manager-mobile-dark.png`. |
| Packaged calendar profile |1/1 PASS, exit0,0 failures/errors/skips,737.548 seconds including browser hold. |

`report-browser-observations.json` separates actual browser observations from API setup.
The final workflow evidence links earlier Pharmacy/Notification browser runs with this Report
run on the same source candidate; it is not one all-browser E2E session. Accounts are test-only;
tokens/passwords are not retained here. Browser logged out, theme/viewport reset, FE and
acceptance JVMs/DB/broker stopped. Source-owner **production** exports, approval and cutover
remain operational follow-up in the single handoff; test acceptance is not production GO.

Candidate: Huy base `42aab198` plus the working changes for Gateway coverage routes,
new outpatient refund publication and the two test fixture/focus corrections. No new branch,
commit or push. This checkpoint supersedes the refund-publication and Lab-fixture blockers in
the earlier final/follow-up/matrix evidence; it does not supersede their other observations.

## Implemented

- Billing records new completed outpatient refunds in the transactional outbox using the
  existing immutable payment-request cohort. A separate refund-publication property defaults
  to false and requires an acceptance reference when enabled. V24 retains per-refund approval,
  checks persisted transaction/source/account facts, and prevents historical enrollment.
  Gate off or absent cohort keeps the event HELD. Old outbox rows are never released.
- The public refund payload, authorization, subject and idempotency contract are unchanged.
  Admission/deferred scope is not enabled. Notification production code is unchanged.
- The six-service harness can explicitly opt in with `MEDIFLOW_FE_REFUND_ACCEPTANCE=true`.
  It checks two race refunds published, two notifications and two Report APPLIED projections,
  in addition to the existing retry, over-refund, authorization, restart and rollback checks.
- The Lab READY fixture now supplies the clearance ID required by the locked contract.
  No production validation was relaxed to make the suite pass.

## Real public workflow and browser acceptance

The disposable hold runtime used packaged Gateway at `http://127.0.0.1:50409`, PostgreSQL and
RabbitMQ, with refund producer/consumer gates explicitly enabled for this acceptance only.
Core issued the positive owner/foreign patient credentials. Source authority IDs came from
public Core/Clinical/Pharmacy workflow responses. Test accounts and fixture data are disposable.

| Observation | Result and evidence |
|---|---|
| Create V1 Rx, cashier payment, cancel, cashier refund | Public commands succeeded; `refund-api.json` records statuses and responses. |
| Retry exact refund request | Same refund transaction `921ef690-b34c-47c8-ba5c-9f15201f4cc3`; exactly one owner notice for that refund. |
| Actual owner opens refund notice | Notice `19064d74-9ecb-4e97-b922-9b2161cb8ad9`, amount 30,000 VND; `refund-owner.png`. Keyboard Enter opens the link; reloading restores heading focus. |
| Actual foreign patient opens the same direct URL | Access denied, notice content hidden, error receives focus; `refund-foreign-denied.png`. |
| Actual issuer role/privacy checks | Owner detail 200; foreign patient, NURSE and MANAGER detail 403; foreign patient history 403. NURSE list 200 does not grant send permission. See `public-api.json`. |
| Malformed signed TEST credentials on the real notice | Missing patientId 401 at Gateway; wrong patientId 403; expired/missing-exp 401; subject equal to patientId does not bypass claim validation. See `refund-api.json`. These are test-signed negative credentials, **not malformed tokens issued by Core**. |
| Refund reaches Report | Real Report projection state APPLIED, 30,000 VND, business date 2026-10-21, Asia/Bangkok. `report-refund-projection.json` is a read-only query of Report's own test store, not cross-context application access. |

The hold acceptance finished with Failsafe **1 test, 0 failures, 0 errors, 0 skipped**; its
Support unit suite was **18/18**. The FE process and hold services were stopped, and browser
sessions logged out after capture. Saved JSON contains no access/refresh tokens or passwords.
Browser observations and API/DB observations are distinguished above; this is not a claim that
every cashier or setup step was performed in the browser.

## Frontend verification

Current source: full FE **595/595 tests in 57 files**, typecheck, lint and production build
all passed. The focused Lab suite passed **15/15**. These verify regressions/buildability and
do not substitute for the browser observations above or the prior Pharmacy browser evidence.
Final Docker reruns finished:

- Billing `mvn -q -pl backend/billing-service -am test`: **1,083 tests in 124 suites,
  0 failures, 0 errors, 0 skipped**, Maven exit 0. Surefire reported its fork-JVM shutdown
  timeout after `System.exit(0)`; this cleanup warning is retained in `verification.json`
  rather than described as a clean shutdown. Test assertions all passed.
- Normal packaged six-service acceptance, opt-in refund publication/consumer: **1/1,
  0 failures/errors/skips, 215.332 seconds, exit 0**. Unit selection was skipped in this
  invocation; the earlier hold run separately passed 18 Support unit tests. The added
  assertions verified two published refunds, two notices and two APPLIED refund projections.
  `normal-refund-api.json` records the actual race 201/422, final partial refund and zero
  remaining obligations. This is API/DB/broker acceptance, not browser acceptance.

Counts, commands, runtime flags, migration SHA-256 and limitations are retained in
`verification.json`. The test runtime processes have exited; no source gate was enabled.

## What remains before release

Progress **11/14 tasks, 52/56 criteria**. HF6-14.3 is now closed. Remaining criteria:
**HF6-09.2/.3, HF6-11.2, HF6-12.4**. Release remains **HOLD**.

Gateway routing is connected and packaged ADMIN preview returned 200. Today's real refund
intake is APPLIED, but it is not accepted five-source report coverage. Approval needs retained,
reviewed completeness evidence from CLINICAL, LAB, PHARMACY, BILLING_CASH and BILLING_REFUND
for the same closed business period and scope. The monthly reader requires the complete
calendar year. Today's 2026 transactions cannot establish completed 2026 annual coverage.
No source-owner approved exports for a suitable closed period have been supplied here.

We did not submit an empty preview as source proof, fabricate timestamps or authority IDs,
backdate server clocks, seed coverage, release historical HELD rows, or enable source gates.
Final successful ADMIN/MANAGER report metrics and the integrated release remain unverified
until that real reconciliation is available. Dependency details stay in the single
`docs/handoffs/HANDOFF-HUY-MVP.md` file.
