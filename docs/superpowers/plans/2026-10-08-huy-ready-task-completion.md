# Huy — hoàn thiện các phần có thể thực hiện ngay (2026-10-08)

Baseline: Huy `dd255fb`, đã chứa master `8b639c4`; giữ các thay đổi pre-op/workflow chưa commit.
Scope: Surgery, Pharmacy, Report và tài liệu. Inpatient chỉ đọc/chạy test; không sửa Gateway,
service khác, root build, Common, Compose, CI hoặc dữ liệu môi trường người dùng.

Evidence cho **S-05.1.2/S-05.2.2/S-07.6, S-03.6, P-03.1.3/P-03.3.2,
R-01.1/R-04.1.3/R-04.4/X-01**, không tạo thêm global task IDs. Task cha có
clinical/source/publication acceptance vẫn PARTIAL dù các checkpoint LOCAL đã hoàn tất.

## Các checkpoint thực hiện trong lượt này

- [x] Chạy đủ 15 pre-op và 14 workflow PostgreSQL/HTTP cases còn treo; đóng PREOP-09 và WF-01..10 LOCAL.
- [x] Sửa terminal pre-op fixture: scheduled case đã có hai consent, kiểm tra exact before/after
  cho consent rows/history, case row/revision, reservations, outbox và receipt. Không bỏ assertion
  hoặc sửa production để phù hợp kỳ vọng sai.
- [x] Nghiệm thu sạch toàn bộ Surgery: 908 tests, 77 reports, 0 failure/error/skip.
- [x] Pharmacy: strict admission wire port/decoder và consumer theo ba routing key đã chốt.
- [x] Pharmacy: queue/DLQ riêng, hai gate mặc định false, ACK sau commit, bounded retry và safe rejection.
- [x] Pharmacy: producer-byte PG/Rabbit tests cho duplicate/new event ID, discharge/close-before-start
  và listener restart, conflicting patient rollback, malformed DLQ, storage failure/retained-byte
  replay. Intake không stock/Rx/slip/payment/outbox effect.
- [x] Pharmacy: gated Inpatient Feign read adapter, service JWT <=60s, exact IDs/correlation,
  strict JSON/row revision (including 0), observation <=30s / future skew <=5s, outage khác missing.
  Lookup không gọi trong mutation transaction; necessary context/freshness check có thể recheck
  sau lock wait. Timeout/circuit breaker/fallback không giả eligibility.
- [x] Nghiệm thu sạch toàn bộ Pharmacy sau bổ sung lookup: 484 tests, 81 reports, 0 failure/error/skip.
- [x] Report: strict wire port và in-port receive, reuse pure Clinical/Lab/Pharmacy/Surgery mappers
  và transactional journal/dedupe/two-scope kernels, không metric logic ở listener.
- [x] Report: dedicated seven-key queue/DLQ, hai gate false, ACK sau commit; không financial binding,
  legacy effect, accepted publication hoặc read cutover.
- [x] Report: admission start/close chỉ lưu minimal pending/pairing evidence, không tự sinh
  admissions/discharges/LOS/occupancy khi full semantics chưa được chấp thuận.
- [x] Report: actual producer-byte PG/Rabbit proof cho hai scopes/minimal journal, semantic
  conflict/correction rejection, second-scope rollback/DLQ replay, finite rebuild VERIFIED,
  admission pairing sau listener restart.
- [x] Nghiệm thu sạch toàn bộ Report: 458 tests, 57 reports, 0 failure/error/skip.
- [x] Inpatient read-only: 45 tests / 4 fresh reports, 0 failure/error/skip; trong đó 5 broker tests
  và 1 actual Surgery event-first sequence. Completion trước reference được lưu bền vững, apply
  sau exact registration; late READY/redelivery không tạo treatment entry mới. Negative semantics
  có unit proof, không gọi đó toàn bộ negative broker matrix/E2E.

## Kết quả kiểm thử

| Verification | Tests | Reports | Fail / error / skip | Fresh report window (Asia/Bangkok) |
|---|---:|---:|---|---|
| Surgery complete clean suite | 908 | 77 | 0 / 0 / 0 | 09:23:08–09:28:19 |
| Pharmacy complete clean suite | 484 | 81 | 0 / 0 / 0 | 09:40:08–09:43:36 |
| Report complete clean suite | 458 | 57 | 0 / 0 / 0 | 09:35:49–09:39:07 |
| Inpatient selected existing suites, read-only | 45 | 4 | 0 / 0 / 0 | 09:34:08–09:34:49 |

XML-discovered results + Maven exit 0, not annotation counts, skipped Docker passes or old totals.
Report has 14 new real broker/PG cases; Pharmacy intake has 7. Focused reruns are not added again.
Pharmacy's exact lookup has 23 fixture-over-HTTP cases, 5 necessary observation checks, 4 disabled
configuration checks and a safe 503 error mapping case. Total complete owned suites: **1850 tests**,
all pass; selected Inpatient 45 are separate, not a fourth complete module acceptance.
Surgery workflow authority providers are explicit test doubles; owned kernels/SQL/audit/resource/
HELD capture are real, no clinical approval is invented. Listener restart is not a separate JVM crash.

Intermediate failures: Surgery fixture expected zero seeded consents; Pharmacy fixture path fixed
to actual Inpatient `admission.discharged.json`; Report log-safety test now sets/restores its WARN
level instead of relying on root logging. An overlapping `clean -am` removed Common class output
during another module's test compilation; clean reactors were sequenced afterward, no source hack.

```powershell
# Sequential: -am reactors share backend/common/target.
mvn -q -pl backend/surgery-service -am clean '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/report-service -am clean '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/pharmacy-service -am clean '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/inpatient-service -am '-Dtest=InpatientRabbitIntegrationTest,InpatientApplicationServiceTest,InpatientEventConsumerTest,SurgeryEventReceiptPersistenceMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
```

Docker Desktop 29.6.2; isolated PostgreSQL 16/RabbitMQ 3.13. No H2, Docker reset/user volume removal,
migration rewrite, automatic commit/push or production flag change.

### Build and documentation checks

- [x] Package all three owned modules and required dependencies: Maven exit 0;
  fresh Pharmacy, Report and Surgery executable JARs at 09:46:42, 09:46:53 and 09:47:09.
  Packaging skips tests only after the complete clean test runs above passed.
- [x] Verify Pharmacy dependency tree: Eureka client already supplies Spring Cloud LoadBalancer
  transitively; no duplicate dependency or root/shared build change is required.
- [x] Validate local links in 20 changed/new Markdown documents: zero broken local links.
- [x] `git diff --check` passes; no unmerged paths. Existing local work is retained, not discarded.

```powershell
mvn -q -pl 'backend/pharmacy-service,backend/report-service,backend/surgery-service' -am '-DskipTests' package
```

## Những phần chưa thể đóng từ evidence này

1. **Surgery:** actual referral/case relationship, approved checklist/legal consent/team policy
   providers, source/financial mutation fences/revocation, accepted live held delivery and complete
   Billing/Notification effects. Inpatient consumer is no longer “not coded”; full negative broker
   matrix/live publication remain. Local HTTP/PG does not prove these source authorities.
2. **Pharmacy:** patient/prescriber/episode/order/current placement authority, admission command
   wiring and medical-discharge/close-versus-stock race, approved adjustment/public V1 rollout/hold
   release. Current lookup and lifecycle evidence are necessary, not sufficient permission.
3. **Report:** financial allocation/recognition/refund/settlement sources, medical versus administrative
   duration/rounding and bed transfer/release/capacity facts, historical export/coverage, live catch-up/
   controlled publication/read cutover. Exact accepted inputs do not prove complete history.
4. **Shared:** Gateway policy/CI/deployment remain Hoàng Anh's scope; no change made there.

New local intakes do not release held producers. Unknown/unsupported facts fail closed; missing
metrics remain unavailable, not successful zero. S-03.6/source/publication parents retain full
acceptance gates. Main count remains 62 and fixed selection 18/50; local execution checkboxes are
not closure of those non-overlapping business/rollout IDs.
