# Follow-up before one combined PR — 25/10/2026

Base: `64a1f04340efe07d324a1335d44c2b90baac68fd` (fetched again at 08:05 on 25/10).
Branch: `codex/fix-lab-clearance-identity`.

## Billing legacy listener

`BillingEventConsumer` decoded `medicalrecord.created` / `lab.result.created` V1
envelopes as flat V0 DTOs. In the Spring mapper, unknown envelope fields were
ignored, leaving `departmentId` and other fee context null. This caused the log
`Khoản phí phải có khoa và loại phí xác định` during an otherwise successful V1
paid normal flow. Unwrapping and accruing the fee would introduce another fee
after the V1 EXAM check-in / LAB request issuer already created the real charge.

The consumer now validates those V1 lifecycle envelopes as informational when the
corresponding V1 issuer is configured. It creates no additional legacy fee. Invalid
envelopes, unknown versions/producers/context, disabled V1 and flat facts in V1 mode
still fail through normal retry/DLQ handling. Explicit V0 mode keeps its old handlers.
Production payment, clearance lookup, refunds and contracts are unchanged.

Two producer fixtures were copied byte-for-byte into Billing tests. The first
regression run (Spring-compatible mapper) had 17 tests, 5 failures. After the fix
all passed; a further duplicate-key/trailing-JSON/source-identity test brings the
targeted suite to 18. Logs: `evidence/billing-lifecycle-red.log` and
`evidence/billing-lifecycle-green.log`.

## Additional test corrections

- Billing inactive lookup fixture had source fields populated although the existing
  use case returns them null for ineligible authority. Fixture now matches the live
  producer; production lookup and eligibility checks are unchanged.
- Billing EXAM/LAB cancellation and Prescription terminal tests queried an asynchronous row before
  insertion, throwing `EmptyResultDataAccessException` outside Awaitility's assertion
  retry. It now first asserts row count, then retains its original status, money,
  charge, refund-due and payment assertions.
- Mobile appointment wire test used an expired fixed 22/10 date. It now creates
  tomorrow's date and edits to the following day. Four pre-existing formatter
  failures were corrected without changing production behavior.
- Node 26 native Web Storage conflicted with jsdom: 405 web test failures initially.
  Disabling that experimental storage left one loaded-machine 5-second timeout;
  the six-test file passed alone. The repeatable client runner uses bounded workers
  and a 15-second timeout. Node 24.19.0 then passed all 949 tests.

## Results

- Lab adapter: 19 tests passed; full `mvn -q -pl backend/lab-service -am test`:
  Lab module 257 tests, zero failures/errors/skips. See `evidence/proof/lab-unit-suites.json`.
- Billing targeted final check: `ArchitectureTest`, `BillingEventConsumerTest` and
  `ExamChargeRabbitIntegrationTest`: 28 tests passed, zero failures/errors/skips.
  The previous full run executed 1129 tests and exposed both the EXAM wait race and
  an ArchUnit package cycle in the initial helper placement. The reader now lives
  beside its consumer, package-private; no architecture rules were relaxed.
- Web: Node 24.19.0, typecheck/lint, 101 files / 949 tests and Next production build
  passed through `Run-ClientChecks.ps1 -Client Web`.
- Mobile: `mobile/tool/check.ps1` passed format, analyzer, 342 tests and web build.
  The second web build with outpatient demo defines also passed.
- SQL: all eight context databases restored into a new PostgreSQL 16 container:
  Organization 13, Patient 4, Clinical 13, Lab 9, Pharmacy 18, Billing 48,
  Notification 9 and Report 60 public tables. Populated-target reimport refused
  before data loading. No original database/volume was used or reset.
- Topology: six business JARs plus Gateway/Eureka health UP, then real ADMIN login
  and seven Gateway read routes returned HTTP 200 with successful API envelopes.
  Both production Next web and Flutter web logged in and displayed 8 restored Lab
  rows. This is read/browser smoke, not every write/role/refund/dispense scenario.

The final serial packaged acceptance passed at 08:08 on 25/10: **1 test, 0 failures,
0 errors, 0 skipped, 232.3 seconds**. The explicit Billing JAR was rebuilt after the
package relocation. This is a separate `-Ppackaged-slice ... verify` execution,
not the ordinary Lab unit-test command.

Runtime identifiers from that fresh flow:

| Identity | Value |
|---|---|
| Lab projection | `0e1925fd-35db-494e-bc57-a2c396e93fb4` |
| Lab business test / receipt operation / receipt source | `4b8410b6-d0b5-4f6d-9c8f-2c2b95844b4a` |
| Billing clearance | `936097ec-ccea-4c4a-a1bb-e4c3c8223680` |
| Clinical record | `2a82ab9f-0f04-4e0c-970a-7aadd30a3a4c` |
| Clinical source order | `ce433632-c19b-4d48-9683-888ef8f302bd` |
| Appointment | `a3790f04-d525-4061-abba-a2d073de1e9e` |

Lab's real HTTP client received Billing execution-start **200 / 173 ms** and current
lookup **200 / 18 ms**. Payments and `emergencyOverride=false` are in
`evidence/proof/paid-lab-evidence.json`; Clinical completed with its attached result.
Read-only observations of the isolated test stores confirm different projection/test
IDs and `operationId=sourceId=testId`. No SQL state/identity/delivery writes were used.

After both source outbox publications and broker settlement, `billing.q` and
`billing.dlq` had zero ready/unacknowledged/total messages; the test then verified
zero additional legacy EXAM/LAB fee rows for this record. The fresh Billing log has
no legacy fee-context error. `lab.dlq` retains the two V0 `payment.completed`
quarantine receipts deliberately asserted by the pre-existing acceptance contract;
they are distinct from financial clearance and Billing's lifecycle handling.
Three kill-after-commit recovery probes passed with exact original/redelivered bytes.

The final full Billing rerun completed at 08:16 on 25/10 with exit code 0:
`mvn -q -pl backend/billing-service -am -Dspring.test.context.cache.maxSize=1 test`.
All **1129 tests across 126 suites passed, zero failures/errors/skips**;
see `evidence/proof/billing-unit-suites.json`. The bounded Spring context cache
keeps this complete suite within local memory; no tests were excluded.
An intermediate or failed run is not a PASS. The concurrent packaged attempt
completed paid Lab results but failed final Clinical completion with Organization
`503 UPSTREAM_UNAVAILABLE`; it is retained as failed evidence. Full local logs remain
under `evidence/`; selected portable proof is retained in `evidence/proof/`.

## Changed files

Production changes are limited to Lab and Billing:

- `backend/lab-service/.../infrastructure/client/LabBillingExecutionAuthorityAdapter.java`
- `backend/lab-service/.../infrastructure/persistence/adapter/LabClearancePersistenceAdapter.java`
- `backend/billing-service/.../messaging/consumer/BillingEventConsumer.java`
- `backend/billing-service/.../messaging/consumer/OutpatientLifecycleFactReader.java`

Regression/harness changes: Lab adapter, persistence and packaged acceptance tests;
Billing consumer, EXAM/LAB cancellation and Prescription terminal integration tests;
Billing inactive lookup and two byte-exact producer fixtures. Mobile changes cover
one future-date wire fixture and four formatter-only files. No Gateway, Clinical,
Pharmacy, Support, frontend production code or schema changes are included.

The assigned `scripts/demo_25_10/` folder includes the preserved SQL snapshot and
detailed guide, updated README/verification notes, the implementation checklist,
three PowerShell runners, the isolated snapshot Python runner and portable proof.

## Limits

Flutter doctor reports unaccepted Android SDK licenses and no Android device, missing
Windows C++ build components, and iOS requires macOS/Xcode. No native acceptance is
claimed. These machine prerequisites are separate from the tested Flutter Web app.

Gateway auth initially timed out at the production default 1.5 seconds during the
concurrent JVM rehearsal. Restarting only this rehearsal Gateway with the existing
timeout property set to 15000 ms allowed real Web/Flutter logins. The runner now
sets that bounded local override; production Gateway code/defaults are unchanged.

Final startup review also added the required nonblank descriptions beside the
EXAM/LAB demo price amounts in `rehearse_snapshot.py`. An AST-based configuration
check passed for both amount/description pairs and Python syntax. The restored
stack smoke predates this configuration-only correction; fresh paid writes are
proven by the separate packaged acceptance with its own complete price catalog.

Report for the current uncovered period returned `404 REPORT_NOT_FOUND`, correctly
requiring accepted coverage. The snapshot's genuine 2025 coverage has department
`9a6dd24a-1c1f-4122-9e31-7cfea345ec45`; the read smoke used `2025-06-15` and returned
HTTP 200. No new coverage, state or delivery SQL was manufactured.

The snapshot preserves historical dates and audits. Restoring eight databases and
starting six deployments proves restore/topology smoke, not every business scenario.
The fresh paid normal Lab flow must pass its separate packaged acceptance.
