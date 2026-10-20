# Backend Docker audit and Vinh fixes — 2026-10-20

Source base: `26bf2ef7` (backend identical to `553ec59e`). Fix branch:
`codex/vinh-backend-audit-fixes`. Source fingerprints below identify the verified backend files
independently of the subsequent commit/PR publication.
Docker Engine 29.8.0, Java 21, Maven; PostgreSQL 16 and RabbitMQ disposable Testcontainers.
Existing application volumes were not reset and rollout gates were not enabled.

## Confirmed defects and ownership

| Defect | Owner | Disposition |
|---|---|---|
| Billing + standalone Notification accept a public fallback JWT secret when no external key is set | Vinh | Removed YAML fallback; actual YAML + security bean startup regression |
| Lab's oldest 100 failed outbox rows starve later healthy events | Vinh | V4 scheduling/quarantine; bounded failed attempts; retained identity/payload |
| Patient commit is followed by a non-durable direct broker send | Hoàng Anh | HA-20-01 OPEN |
| A department head can change from DOCTOR to NURSE and remain assigned | Hoàng Anh | HA-20-02 OPEN |
| Department deactivation races with staff creation | Hoàng Anh | HA-AUDIT-03 OPEN, now confirmed on real PostgreSQL |

The known development key is only a defect when silently selected by the standalone service.
This change does not claim that an explicitly configured development Compose key is a newly
discovered bypass. No additional concrete Pharmacy/Report/Gateway defect was confirmed in this
pass. Five confirmed groups do not constitute a proof that every possible backend bug is absent.

See [the Core handoff](../../handoffs/HANDOFF-HA-BACKEND-AUDIT-2026-10-20.md) for producer/consumer
impact, exact paths, acceptance and the [deterministic concurrency patch](HA-AUDIT-03-regression.patch).
The deliberately failing Core regression remains a patch, not part of the default green suite.

## Reproductions before fixes

- JWT: loading each actual module YAML + actual security configuration without a host secret
  wrongly started the authentication context. Each module's two-case RED run had one expected
  assertion failure and no errors. External valid secret was the positive control.
- Lab: 100 older unroutable `lab.request.created` rows followed by a bound result event; two
  dispatch cycles left the healthy event undelivered. One expected assertion failure, zero
  errors, PostgreSQL/RabbitMQ available. Log: `%TEMP%/mediflow-lab-starvation-red.log`.
- Core concurrency: barrier after deactivation's no-active-staff query, then commit staff creation
  in another transaction, then resume deactivation. Three tests, one expected invariant failure,
  zero errors; existing two tests passed. The patch retains the full deterministic harness.
- Core head: actual Spring use cases create a doctor, assign that staff as head, update title to
  NURSE and read persisted rows on PostgreSQL 16. One expected invariant assertion failure,
  zero errors/skips. [Regression patch](HA-20-02-regression.patch) retains this proof.

## Fix checks

Lab targeted first GREEN: 17 tests, zero failures/errors/skips. Covered failed full batch,
quarantined full batch, same-identity replay, broker application restart, rollback after confirm,
bounded exponential delay, and exhausted historical counters. A later-added V3→V4 preservation
test is included in the final full run rather than in that initial 17-case result.

JWT missing-secret diagnostic assertions were tightened during review; an over-specific expected
environment-variable name caused a test-only failure and was corrected to check the actual
`JwtProperties` / `mediflow.jwt.secret` constructor rejection. Final regression must include the
corrected assertions; the final Billing and Notification runs include them. Earlier targeted
green counts are not used as final verification.

Independent spec review and code quality/security review found no actionable blocker in the fix
diff. They do not close the three foreign production defects.

The full changed-revision run also exposed Billing test fixtures that had implicitly depended
on the removed production fallback: three full-context Rabbit tests and a separately built
application context inside `OutpatientPublicationRabbitIntegrationTest`. These now supply an
explicit test-only key at their own startup boundaries. No global test YAML shadows the actual
production configuration regression. Their final recompiled targeted rerun passed all 48 cases.

The first Lab process-recovery run passed the crash/replay assertions but reported one Windows
temporary-log cleanup error. An unchanged focused rerun passed and no child JVM was orphaned.
`OutboxProcessRecoveryIT` now retries deletion of its two known logs for up to ten seconds after
successful assertions and confirmed JVM termination; failed tests preserve their diagnostic logs.
This is a test-harness correction, not a sixth confirmed business/security defect.

Both full reactors independently exposed an existing stale Support assertion: it expected four
successful Notification migrations although unchanged base source already includes V5 raw receipt
identity. Baseline: 6,980 tests, one assertion failure, zero errors/skips; the only failing case
was `SupportStoreIsolationTest.eachMigrationHistoryAndPoolBelongsToExactlyOneContext` (expected 4,
actual 5). The test now checks ordered successful versions `1,2,3,4,5`; pool and cross-store
isolation assertions are retained. This test-only repair is permitted under Vinh's repository-wide
verification scope; it is separate from the five production defect groups. The final Support
rerun passed all 18 cases. Independent review found no issue in this delta.

## Final verification

All commands below completed on 20 October 2026 with Docker available. Failures from the full
runs are retained; they are not relabeled as successful whole-reactor commands.

| Run | Tests | Failures / errors / skipped | Maven exit |
|---|---:|---|---:|
| Unchanged base, all 13 reactor modules | 6,980 | 1 / 0 / 0, stale Support migration assertion | 1 |
| Modified full reactor, before final fixture recompilation | 6,990 | 1 / 39 / 0, same Support assertion plus Billing secret fixtures | 1 |
| Final Billing affected classes, including actual-YAML JWT regression | 48 | 0 / 0 / 0 | 0 |
| Final Support complete default test suite | 18 | 0 / 0 / 0 | 0 |
| Billing packaged publication process recovery | 1 | 0 / 0 / 0 | 0 |
| Lab consumer + outbox process recovery | 2 | 0 / 0 / 0 | 0 |
| Lab packaged paid EXAM/LAB_TEST workflow | 1 | 0 / 0 / 0 | 0 |
| Notification receipt identity upgrade/backfill CLI | 5 | 0 / 0 / 0 | 0 |
| Packaged six-business-application Gateway workflow | 1 | 0 / 0 / 0 | 0 |

The modified full run passed Gateway 1,451; Core 187; Clinical 339; Lab 227; Pharmacy 851;
Notification 293; Report 1,407; deferred Inpatient 150 and Surgery 991. Common/Eureka built
successfully. Billing's other default tests passed in that full run; the final 48-case rerun
covers every affected startup boundary and both JWT controls. Support's final 18-case rerun
covers its entire default suite, including the repaired isolation assertion.

Final evidence therefore combines the full run and the precise fixture reruns, plus ten
successful opt-in acceptance cases. **No fresh single all-green full-reactor command was run
after the final fixture repairs.** Root `verify` does not run every opt-in profile. Do not add
the 48/18 repeated cases, historical runs or deliberately RED Core proofs to a unique green total.

[Machine-readable results](verification-results.json) retain command exits, per-module test
summaries, raw `%TEMP%` log names and SHA-256 hashes. The full-run logs are
`mediflow-backend-docker-baseline.log` and `mediflow-backend-fixed-verify.log`; final rerun/profile
logs are listed in that JSON. Final code review and `git diff --check` found no actionable issue.

## Reproduce the final checks

Run from the repository root with Docker available. No running clinic database is required.

```powershell
mvn -B -ntp -fae verify
mvn -B -ntp -pl backend/billing-service -am -Poutpatient-publication-acceptance '-Dtest=AdmissionChargeCloseRabbitIntegrationTest,PrescriptionChargeRabbitIntegrationTest,SurgeryCompletionRabbitIntegrationTest,OutpatientPublicationRabbitIntegrationTest,JwtSecretConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
mvn -B -ntp -pl backend/lab-service -am -Pprocess-recovery '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
mvn -B -ntp -pl backend/notification-service -am -Preceipt-identity-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
mvn -B -ntp -pl backend/support-service -am -Psix-service-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
mvn -B -ntp -pl backend/support-service -am '-Dtest=Support*Test' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

For Lab's `packaged-slice` profile, package the reactor first, then use `-pl backend/lab-service
-am -Ppackaged-slice` with the same no-unit-test selector and explicit absolute
`-Dlab.acceptance.{core,gateway,eureka,clinical,lab,billing}.jar` properties. Each property points
to that checkout's corresponding `backend/<module>/target/<module>-0.0.1-SNAPSHOT.jar`;
the Eureka module is `eureka-server`. Quote each complete Maven property in PowerShell.

## Limits and integration

The two Vinh defects are fixed in the local worktree; three Core defects remain OPEN for
Hoàng Anh. No additional concrete Pharmacy/Report/Gateway defect was confirmed in this pass.
Previously open deployment/cutover and browser acceptance records are not automatically closed
by these tests. Unselected opt-in profiles, live target-environment receipt inventories and a
production rollout remain outside this verification. Inpatient/Surgery source is included in
the default reactor regression only; their deferred business scope is not activated.

The [source lock](source-lock.json) records SHA-256 hashes of the changed backend files captured
before commit/PR publication. No deployment, rollout flag change or real volume reset was
performed. Apply each deliberately RED Core patch separately on its owner's branch; both pass
`git apply --check` against this unchanged Core source.
