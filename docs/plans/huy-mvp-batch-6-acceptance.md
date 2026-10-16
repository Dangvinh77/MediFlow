# Huy MVP — batch 6: ten local acceptance work items

2026-10-15. Source baseline `e948075893e797be056d1344f9c9ba0072ed6dae` plus uncommitted Huy changes.
Scope: Pharmacy, Report and owned documentation only. No Gateway/Notification/shared host edits.
Implementation was completed before running this batch's tests, as requested. Results below must
come from this run; Docker availability alone is not acceptance.

These are ten executable work items within existing H6 subtasks, not ten newly closed main tasks.
External producer authority, Support host and real source coverage remain separate gates.

| Item | Existing plan | Implementation | Verification |
|---|---|---|---|
| B6-01 | H6-04.4 | Public creation facade → checked context writer → real PostgreSQL reader, concurrent same-command replay | PASS — CareDispensePostgresTest.publicCreation_concurrentReplayUsesCheckedContextAndRealReader |
| B6-02 | H6-05.4 | Public lifecycle facade concurrent authenticated dispense, single stock/HELD effect, no V0 receipt | PASS — CareDispensePostgresTest.publicDispense_concurrentActorsCommitOneStockEffectAndHeldProof |
| B6-03 | H6-05.4 | Stock rollback before separately committed terminal failure; retry returns the same outcome | PASS — CareDispensePostgresTest.publicDispense_stockFailureRollsBackBeforeSeparateTerminalTransactionAndRetry |
| B6-04 | H6-08.4 | Same-byte outpatient filled fixture through real Rabbit/PG, duplicate and new envelope ID | PASS — OutpatientReportRabbitIntegrationTest.prescriptionProducerBytesAndNewEnvelopeIdHaveOneOperationEffect |
| B6-05 | H6-08.4 | Refund-before-payment, listener restart and duplicate delivery recover one linked cash/refund effect | PASS — OutpatientReportRabbitIntegrationTest.earlyRefundSurvivesConsumerRestartAndPaymentRecoversExactlyOnce |
| B6-06 | H6-02.4/H6-08.4 | Deferred admission deposit retained byte-identically in DLQ with no financial effect | PASS — OutpatientReportRabbitIntegrationTest.admissionDepositIsRetainedInDlqWithoutFinancialOrOperationalEffects |
| B6-07 | H6-08.4 | Conflicting operational fact retained in DLQ, accepted contribution unchanged | PASS — OutpatientReportRabbitIntegrationTest.conflictingSameOperationIsRetainedWithoutChangingAcceptedContribution |
| B6-08 | H6-08.4/H6-12.2 | PostgreSQL trigger fault, exactly three bounded attempts, atomic rollback and original-byte redrive | PASS — OutpatientReportRabbitIntegrationTest.databaseFailureRetriesThreeTimesRollsBackAllEffectsAndRetainsOriginalBytes |
| B6-09 | H6-09.4 | Real coverage queries: exact department/zone, midnight boundary, missing range and full-window witness drift | PASS — OutpatientReadPostgresTest.exactDepartmentZoneBoundaryAndWholeCoverageWitnessFailClosed |
| B6-10 | H6-10.4 | V26 standalone schema → namespaced V27 resources → standalone resource location; checksums and new writes preserved | PASS — ReportModuleUpgradePostgresTest.standaloneV26ToNamespacedV27PreservesHistoryAndRollbackReadability |

No fixture inserts are production history or coverage acceptance. The second-store module harness
is not the missing Support/Notification host. Listener restart is not a whole-process/broker restart.
Failure proof here does not claim Billing compensation or current clearance revocation acceptance.

## Results

Targeted container suites executed on Docker Desktop 29.6.2, PostgreSQL 16.14
(`postgres:16-alpine`) and RabbitMQ image `rabbitmq:3.13-alpine`:

| Suite | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| CareDispensePostgresTest | 25 | 0 | 0 |
| Pharmacy MvpCutoverInventoryPostgresTest | 1 | 0 | 0 |
| OutpatientReportRabbitIntegrationTest | 5 | 0 | 0 |
| OutpatientReadPostgresTest | 2 | 0 | 0 |
| ReportModuleUpgradePostgresTest | 1 | 0 | 0 |
| ReportModulePostgresTest | 1 | 0 | 0 |
| Report MvpCutoverInventoryPostgresTest | 1 | 0 | 0 |
| Total | 36 | 0 | 0 |

The first Report attempt exposed Docker client negotiation incompatibility; its test resources
now use the same API pin as Pharmacy. The module harness also loaded the standalone boot
configuration because a nested TestConfiguration made Spring search for a Boot application.
An explicit test-only configuration outside Report's scan fixes that composition error. The
two-store test then passes with no standalone launcher, no default transaction manager, 24 Report
migrations and no migration table in the other disposable store. No production credentials used.

Reproduce the targeted run:

```text
mvn -q -pl backend/pharmacy-service,backend/report-service -am test -Dtest=CareDispensePostgresTest,OutpatientReportRabbitIntegrationTest,OutpatientReadPostgresTest,ReportModuleUpgradePostgresTest,ReportModulePostgresTest,MvpCutoverInventoryPostgresTest -Dsurefire.failIfNoSpecifiedTests=false
```

Refreshed artifact profile `report-packaging-acceptance`, Maven exit 0: **5 passed / 0 failed /
0 skipped** (2 artifact/SQL/launcher checks and 3 fresh-JVM probes). These five are separate from
the 36 container tests. All 62 protected migration/fixture hashes are unchanged.

## Full module regression

```text
mvn -q -pl backend/pharmacy-service,backend/report-service -am test
```

The full selection executed 722 Pharmacy tests and 1,326 Report tests, with zero skips.
It exposed one stale test expectation: OperationalPublicationMigrationPostgresTest used
`target=latest` while asserting that upgrading V25 applied only V26. Since V27 now exists, that
run correctly failed (Maven exit 1, one failed assertion; no other failures/errors).

The V26-specific test now explicitly targets V26, preserving its exact one-migration and unchanged
row/checksum assertions. The separate V26 → V27 test covers the new migration. Its two tests were
rerun successfully (Maven exit 0). No released SQL was edited or assertion weakened.

Latest per-suite results across the full selection and that corrected rerun, counting each case
once rather than adding reruns:

| Module | Suites | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|---:|
| Pharmacy | 101 | 722 | 0 | 0 |
| Report | 137 | 1,326 | 0 | 0 |
| Total | 238 | 2,048 | 0 | 0 |

The 36 focused container cases are included in these 2,048, not additional. The five Failsafe
artifact/JVM tests are separate. This is a full selection plus a corrected-suite rerun, not a
claim that the original all-tests command exited zero.

Final post-fix selection (Maven exit 0): OperationalPublicationMigrationPostgresTest,
OutpatientReadPostgresTest, ReportContextBindingTest and ArchitectureTest — **11 passed /
0 failed / 0 skipped**. These reruns are included in the unique totals above.

```text
mvn -q -pl backend/report-service -am test -Dtest=OperationalPublicationMigrationPostgresTest,OutpatientReadPostgresTest,ReportContextBindingTest,ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Plan closure

H6-09.4 and H6-10.4 close with local PG/broker/architecture/artifact evidence. Huy's adjustment
plan is **27/48 closed, 21 open**. H6-10 completes Report library preparation; H6-09 has all four
code/test subtasks complete but still needs real source coverage and owner rollout acceptance.
The original H6 gates for real current clearance authority, producer acceptance/HELD activation,
Support/Gateway/shared runtime and cutover are unchanged. No deployment, commit or push performed.
