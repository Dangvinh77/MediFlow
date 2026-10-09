# Surgery — full owned HTTP workflow follow-up

Date: 2026-10-07. Baseline Huy `e541d56`, preserving the uncommitted pre-op batch.
Status: **10/10 LOCAL checkpoints accepted (2026-10-08)**; all 14 actual HTTP/PG scenarios pass.
Latest full clean Surgery regression: **908/908 PASS**, 77 XML reports, zero failure/error/skip.
The 561-test selection and Docker failures below remain historical, not the current limitation.
Scope: Surgery test code and matching documentation only. Gateway/other services/shared files
remain read-only. No automatic commit/push, production activation or new clinical/legal defaults.

These ten execution checkpoints decompose existing **S-07.6, S-02.6, S-05.5 and S-07.5**;
they are not ten new global task IDs. The global/fixed ledger counts do not change from selecting them.
The preceding PREOP-09 PostgreSQL verification also passed in the same full clean module run.

## Selected before implementation

- [x] WF-01 — actual HTTP appointment-backed outpatient create-to-complete, keeping a distinct record ID.
- [x] WF-02 — the same actual HTTP workflow for exact ADMISSION identity.
- [x] WF-03 — walk-in outpatient workflow with record ID as selected episode.
- [x] WF-04 — actual HTTP cancellation at REQUESTED/PREOP/READY/SCHEDULED with derived stage and exact release.
- [x] WF-05 — replay all prior commands after completion/cancellation; original receipts never reopen terminal state.
- [x] WF-06 — changed completion payload under the same key cannot duplicate or correct the immutable result; performed quantity must fit existing NUMERIC(19,4) exactly, without DB rounding or overflow.
- [x] WF-07 — scheduled checklist correction invalidates/release, then restored evidence requires new READY/finalize.
- [x] WF-08 — current financial denial prevents START, stable denial replay, recovery only through new readiness commands.
- [x] WF-09 — failure at completed HELD capture rolls back result/items/release/audit/receipt; same-key HTTP retry recovers.
- [x] WF-10 — one missing typed consent blocks READY; recording the other type permits a new evaluation, not old-denial reuse.

## Verification contract

Use real Spring HTTP/security, owned application kernels, PostgreSQL migrations/repositories,
resource protocol, receipts and V7 HELD capture. Create/begin/checklist/consent/draft/lifecycle/cancel
must execute through HTTP: do not seed those case states through SQL or skip intermediate commands.
Test-only approved template and exact Billing grant prerequisites may be stored through owned ports;
they are not real referral/Billing publication or medical policy approval. Source authority/Clock
doubles are explicitly restricted to this test context; no service DB other than isolated Surgery.

Complement with framework-free cross-command application tests using only owned port seams.
No mocked transaction implies real atomic rollback; only actual PostgreSQL does.
No Root/Gateway/multi-service E2E, broker delivery or production authority acceptance is claimed.
Never count compiled or Docker-skipped tests as accepted. Record fresh results below after execution.

## Evidence

Latest 2026-10-08: 14/14 HTTP/PG workflow cases and 15/15 preceding pre-op cases PASS in a clean
908-test module run, 09:23:08–09:28:19 Asia/Bangkok, Maven exit 0. The source authority doubles
remain test-only; no production clinical/legal provider or held dispatch is activated.
[Fresh follow-up](2026-10-08-huy-ready-task-completion.md). Below are dated earlier attempts.

`SurgeryHttpWorkflowPostgresTest` compiles and defines **14 actual HTTP/PG scenarios**:
three episode workflows, four cancellation stages and seven result/quantity/correction/financial/
rollback/missing-consent cases. Creation, transitions and pre-op data go through HTTP. All
accepted command replays retain the entire original receipt plus Location after terminal state.
New draft revision follows released schedules; booking history is not erased to make a test pass.

`SurgeryWorkflowApplicationTest` has **8 passing framework-free cross-command cases**;
in-memory ports retain revoked consent history and original command bytes. This is application
orchestration evidence, not transaction/DB/source-authority proof.

**No DB scenario executed:** the combined previous-preop/new-workflow attempt at **19:28:33–34**
failed in Testcontainers initialization, with two class-setup errors and Maven exit 1
(`Could not find a valid Docker environment`). These are NOT two business test failures, two
executed cases or a skipped-success result. The new 14 plus previous 15 PG cases remain unverified.
The last Docker backend log still records `dockerInference` startup failure; the bounded engine
diagnostic did not respond and was stopped. The user was asked to restore Engine running.
No Docker reset, data deletion or configuration edits, H2 replacement or assertions weakened.

Final clean selected regression: **561 tests / 45 fresh XML reports, 0 failures/errors/skips**,
Maven exit **0**, report window **2026-10-07 19:32:55–19:33:19 Asia/Bangkok**. Selection includes
all domain/application/web/configuration test files plus both pre-op flag-disabled classes and
both architecture classes; it excludes Docker persistence/transport/runtime classes. All test
sources, including the PG suite, compile. This is not full-module/root/multi-service acceptance.
Do not add previous 114/740 runs, failed attempts or focused reruns to this total.

Quantity TDD first reproduced six missing-domain-guard failures. After the fix, all **15** domain
precision cases pass (including scientific exponent boundaries), and all **35** lifecycle web cases
pass, including **9 new** precision acceptance/rejection checks. An intermediate 561-test run had
one test-fixture assertion failure: revoked consent history was asserted as a singleton after
re-consent correctly added another record. It now selects the exact original consent UUID while
keeping the two-record assertion; no production behavior changed for that fixture repair.

No whole global ID is closed: main backlog **62**, fixed selection **18/50 accepted / 32 open**.
WF-01..10 checkboxes above remain open for actual DB acceptance; source-complete is not DONE.
PREOP-09 and full-module regression remain pending. Existing production feature flags stay false;
there is no new provider, positive medical policy, dispatcher, migration or Gateway/other-owner edit.
The preexisting changelog line and pre-op additions were retained; **no commit/push** performed.

Commands actually used (sequential, no target races):

```powershell
# Compiled all test sources; initial RED precision run, then focused GREEN domain/web run.
mvn -q -pl backend/surgery-service -am '-DskipTests' test-compile
mvn -q -pl backend/surgery-service -am '-Dtest=SurgeryPerformedItemPrecisionTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/surgery-service -am '-Dtest=SurgeryPerformedItemPrecisionTest,SurgeryLifecycleApiTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlogging.level.root=ERROR' test
# Actual PG attempt: initialization blocked, NOT PASS.
mvn -q -pl backend/surgery-service -am '-Dtest=SurgeryPreopMutationHttpPostgresTest,SurgeryHttpWorkflowPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
# Final clean selected regression (same selection repeated after the fixture correction).
$testRoots = @('backend/surgery-service/src/test/java/com/mediflow/surgery/domain','backend/surgery-service/src/test/java/com/mediflow/surgery/application','backend/surgery-service/src/test/java/com/mediflow/surgery/web','backend/surgery-service/src/test/java/com/mediflow/surgery/infrastructure/config')
$selectedTests = @((Get-ChildItem -LiteralPath $testRoots -Recurse -Filter '*Test.java' | Select-Object -ExpandProperty BaseName)) + @('SurgeryPreopMutationApiBusinessFlagDisabledTest','SurgeryPreopMutationApiFeatureFlagDisabledTest','SurgeryArchitectureTest','ArchitectureTest')
$testArgument = '-Dtest=' + ($selectedTests -join ',')
mvn -q -pl backend/surgery-service -am clean $testArgument '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlogging.level.root=ERROR' test
# PENDING after a healthy engine: rerun actual PG command above, then full clean module regression.
mvn -q -pl backend/surgery-service -am clean '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
```

README/spec/service/charge contract document the same exact quantity boundary; `.http` now also
includes the four existing gated lifecycle routes, which lacked matching request examples.
Local documentation links and diff whitespace pass. No event name/version/fixture bytes change.

Discovered while wiring the workflow: performed quantities lacked the existing storage precision
guard, unlike planned quantities. PostgreSQL could round a submitted value to 4 places while held
event bytes retained its original value. Add domain representability and HTTP validation/tests;
no storage schema or cross-service price/quantity meaning is changed.
