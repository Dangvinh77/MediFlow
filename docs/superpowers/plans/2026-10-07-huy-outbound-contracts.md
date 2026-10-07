# Huy outbound dependencies — 2026-10-07

Baseline: `974924e`. Scope: remove missing Huy-owned producer/consumer contracts that block
other contexts; do not implement unrelated Billing/Clinical policies or activate unverified care.

## Acceptance inventory

- [x] LOCAL: Surgery READY exact episode, schedule revision/planned times; provisional, no booking claim.
- [x] LOCAL: Surgery COMPLETED immutable result identity, actual times, performed codes/quantities,
  controlled complication category and no clinical narrative.
- [x] LOCAL: Surgery CANCELLED immutable cancellation identity, prior-state stage, exact context/actor.
- [x] LOCAL: ten actual producer serializer fixtures for admission/outpatient, including creation and invalidation.
- [x] LOCAL: capture wire bytes atomically with READY/COMPLETE/CANCEL and all seven invalidation callers; delivery remains held
  until downstream acceptance. Existing private V6 intents are never republished as events.
- [x] LOCAL: internal case-created capture with explicit immutable planned lines in caller transaction; no live referral creation yet.
- [x] LOCAL UNIT+PG: Report maps Clinical, Surgery and Pharmacy producer bytes to source-keyed operational contributions;
  actual PostgreSQL dedupe/rollback/replay verifies the consumer, not just JSON parsing.
- [x] Supply producer fixtures and reference-first/event-first/terminal-before-ready test specifications;
  document the acceptance still owned by each downstream service.
- [x] Reassess active Huy handoffs, registries/service contracts and record remaining gaps; whole-flow acceptance stays OPEN.

## Decisions owned by Huy

V1 result and cancellation are immutable singleton operations with explicit `sourceRevision=1`,
independent of envelope `version`. Result identity is persisted `resultId`; cancellation identity
is producer-owned deterministic UUID for `(surgery.cancelled, surgeryCaseId)`. READY identity is
`readinessSnapshotId`; `caseRevision` orders re-ready cycles but does not invent upstream revisions.
READY remains provisional and carries `reservationConfirmed=false`.

No narrative accompanies COMPLETED; optional `complicationsSummary` is null for compatibility
with Inpatient's existing optional reader. The controlled `complicationsCategory` comes only from
the persisted result; absent category stays null, never inferred as “no complications”.
Cancellation's bounded reason is retained for care/Billing; Report must retain only its controlled
stage/category, never the reason or patient data. No post-start abort or correction is added.

Case-created charge bridge, clinical referral proof, admission reference registration, financial
writers, clinical/legal data and activation cannot be declared complete from these outcome fixtures.
They will be evaluated separately in the final evidence, not silently checked off.

## Remaining work after the local producer boundary

The case-created payload/capture are now implemented; no runtime case-creation caller exists yet.
Clinical/Inpatient must supply a stable exact referral proof before this can be connected safely.
No migration is rewritten: Surgery V7 and Report V11 are additive new files.

Report V11 keeps a normalized payload hash per source operation as well as existing event/fact
fingerprints. A changed non-metric field under a new delivery ID conflicts instead of silently
passing KPI dedupe. Only hashes are retained, no raw clinical payload. Old redacted journals cannot
reconstruct that hash: existing sources are LEGACY_UNVERIFIED, and new delivery requires controlled
source revalidation, never an inferred hash. Finite replay retains the prior accepted inputs.

Runtime engineering still open: Inpatient reference/pending/outpatient/late outcomes; Billing charge
and reconciliation writers; Notification provisional/invalidation reminder intake; Huy referral
creation/public lifecycle/source fencing, Pharmacy admission wiring and Report finance/live cutover.
These are not all external blockers or finished by this producer contract work. The previous user
override authorizes named dependency implementation, but not invented clinical/legal/price data.

## Đối chiếu những phần team đang chờ Huy

| Phía nhận | Phần Huy đã bổ sung trong working tree | Chưa được gọi là hoàn thành liên service |
|---|---|---|
| Vinh / Inpatient | Case-created identity/episode và ba outcome admission/outpatient; đọc actual READY/COMPLETED/CANCELLED bytes qua consumer hiện tại | Chưa có live create/referral caller; reference/pending/outpatient/late application handling chưa đạt |
| Lộc / Billing | Planned item/price/quantity bridge tách upstream referral; actual performed lines, result/cancellation identity và stage | Chưa có Billing reader/issuance/reconciliation/refund acceptance cho các wire mới |
| Lộc / Notification | READY có lịch/revision tạm thời; exact snapshot/schedule invalidation, structured reasons và mẫu hai contexts | Reminder/suppress/re-ready/late-terminal consumer chưa được nối; không tự phát HELD rows |
| Report của Huy | Clinical/Pharmacy/Surgery operational mapping, business timestamps/revisions, two-scope dedupe, source hash và finite replay actual bytes | Finance allocations/recognition/refund/settlement, admission metrics và live publication còn mở |
| Gateway / Hoàng Anh | Không đổi các route hiện có; schema/fixtures đã rõ cho các API/consumer phía sau | Không bổ sung public READY/START/COMPLETE API khi authority/policy chưa đủ |

Pharmacy đã có năm V1 producer fixtures và held writer trước lượt này; không viết lại hoặc công bố
đó là code mới. Lượt này thêm consumer-byte tests cho fill tại Inpatient/Report. Public admission
create/dispense fencing vẫn là phần code Huy chưa hoàn thiện, không phải đã hoàn thành vì có fixture.

## Kiểm chứng cụ thể

Producer: `SurgeryCareEventFactoryTest` (10 cases). Actual Inpatient adapter: `HuyOutcomeProducerContractTest`
(4 cases, adapter/port only). Report source mapper: `HuyOperationalProducerContractTest` (15 cases).

Real PostgreSQL: `SurgeryCasePersistenceIntegrationTest` (30), `SurgeryLifecycleIntegrationTest`
(13), `SurgeryMigrationUpgradeIntegrationTest` (6), `OperationalContributionPostgresTest` (14),
`OperationalReplayPostgresTest` (12), `ReportMigrationPostgresTest` (9). All critical targeted tests
pass with zero failures/errors/skips. Migration checks retain V1–V6 Surgery rows/private retry bytes,
and Report V10 sources/counters; they never fabricate authority/jobs/publication/raw source hashes.

Tests use Docker Desktop 29.6.2, PostgreSQL `postgres:16-alpine` (16.14) and the existing module
RabbitMQ integration images. `-Dapi.version=1.44` keeps Surgery's Testcontainers 1.19.8 compatible
without changing dependencies. Packaged multi-service runtime acceptance is not run in this slice.

Final full-module regression PASS on 2026-10-07 (09:08–09:15, Asia/Bangkok):

```text
mvn -q -pl backend/surgery-service,backend/report-service,backend/inpatient-service -am -Dapi.version=1.44 test
```

Fresh Surefire reports: Surgery 552, Report 266, Inpatient 102 — **920 tests, zero failures,
errors or skips**. Maven exit code 0; `git diff --check` passes. This is module regression,
including real PostgreSQL/RabbitMQ integration tests, not packaged multi-service acceptance
of the newly held outbound events. No activation, commit/push or stash mutation.

## Follow-up: close Huy's charge event naming for Lộc

The user's follow-up assigns finishing the producer contract and leaving it ready for their own
push, not replying to Lộc, committing or pushing on their behalf. H-01.2.B no longer says name,
version or routing is undecided: `surgery.case.created` for planned charges, `surgery.completed`
for actual reconciliation; both nested V1, producer `surgery-service`, exchange `mediflow.events`,
routing key identical to eventType. Constants in the producer enforce the same immutable identity.

Four additional actual-serializer completion fixtures cover planned/performed quantity differences
and deliberately unrecognized Billing price codes, for both care contexts. Each is an isolated
alternative scenario, not a correction to the base result. Billing supplies its own catalogue
and monetary expectations; Surgery invents no amount or production code approval.

The Rabbit integration test now sends all eight creation/completion fixture files on the exact
exchange/routing keys and checks envelope/AMQP version, IDs, correlation and byte-identical retry.
This only verifies producer transport; it does not activate V7 held rows or test Billing effects.
Targeted verification PASS on 2026-10-07 (09:33–09:35, Asia/Bangkok), Maven exit 0:

```powershell
mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' '-Dtest=SurgeryCareEventFactoryTest,RabbitSurgeryEventPublisherAdapterTest,RabbitSurgeryEventPublisherAdapterUnitTest,SurgeryCasePersistenceIntegrationTest,SurgeryLifecycleIntegrationTest,SurgeryMigrationUpgradeIntegrationTest,ArchitectureTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Fresh Surgery reports total **78 tests, zero failures/errors/skips**: factory 15, Rabbit integration
10, publisher unit 2, PostgreSQL capture 30, lifecycle 13, migration upgrades 6, architecture 2.
The initial run found eight test-harness errors: an auto-delete queue disappeared between timed
receives of duplicate messages. The Billing test queue now stays until explicitly deleted; the
entire 78-case selection was rerun successfully after the fix. No producer behavior or consumer
dedupe was changed to hide that error. `git diff --check` passes.

H-01.2.3 closes only Huy's producer naming/fixture/test deliverable. Remaining whole-flow tasks
listed above are unchanged. No Billing production changes, activation, message to Lộc, commit or push.
