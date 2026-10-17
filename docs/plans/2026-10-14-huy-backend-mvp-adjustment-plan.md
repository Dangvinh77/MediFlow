# Plan backend riêng Huy sau khi rút gọn MediFlow

Ngày: 2026-10-14. Owner: Huy (`LQHuy0210`). Baseline source: `5f40b32c6c24333ff46f9ae9826ffdd9403ba788`.
Chín batch đã triển khai Pharmacy–Report; batch 4–8 dựa trên master `e9480758` (2026-10-15).
Implementation Huy đã merge qua PR #385 (`7610cb97`, merge `f15430dc`). Lượt tiếp tục ngày
2026-10-16 dựa trên `9ef3b151`, chỉ sửa Pharmacy/Report và tài liệu Huy theo xác nhận của user;
phần foreign/shared được bàn giao, không bật gate hoặc sửa database đang vận hành.
Database/broker kiểm thử được tạo riêng bằng Testcontainers.
[Manifest/evidence](huy-backend-mvp-baseline.md).

## 1. Mục tiêu và nguồn quyết định

Hoàn thiện **Pharmacy ngoại trú V1** và **Report tối thiểu**, chuẩn bị Report trở thành module
trong **Support = Report + Notification**. Pharmacy vẫn độc lập với Billing để chứng minh
saga, lỗi từng phần và eventual consistency. Không tiếp tục hoàn thiện Surgery để đóng MVP.

Nguồn canonical: [thiết kế](01-system-design.md), [nghiệp vụ](02-business-flows.md),
[contracts](03-contracts.md), [reliability](04-data-reliability.md),
[plan tổng M6](05-implementation-plan.md), [nghiệm thu](06-acceptance-runbook.md),
[coding rules](../ai/README.md).

Plan này là **phân rã phần Huy của M6**, không thay 18 task/72 subtask của plan tổng,
không cộng 48 subtask ở đây vào 72 để tính tiến độ toàn team. Task chung chỉ đóng khi các
owner còn lại có evidence. Code có sẵn được reuse; checkbox TODO là phần điều chỉnh/nghiệm thu
theo scope mới, không có nghĩa các service hiện chưa có code.

### Phạm vi giữ / chỉnh / hoãn

| Phần | Quyết định |
|---|---|
| Drug catalogue, giá server, reservation, dispense, receipts, outbox | Giữ kernel và transaction semantics; bổ sung wiring/gaps V1 và regression |
| Pharmacy identity/context | Dùng authority thật từ Core/Clinical, không fake provider |
| Prescription V0/payment saga | Giữ compatibility/rollback; không dùng thay V1 khi thiếu quyền |
| Report visits/Lab/Rx/items/cash/refund | Reuse evidence/mappers/stores; chọn đúng ngoại trú và reader có coverage |
| Report full V2, all-five financial publication, settlement, archive lịch sử | Hoãn; không xóa tables/data/kernel đã có |
| Surgery | Giữ source, migration, data, tests; không runtime/route/job trong MVP |
| Admission medication/placement/bed capacity | Hoãn consumer Huy; producer mới trên master không mở lại backlog |
| FE | Không redesign, không phát triển thêm trong plan backend; chỉ ghi ảnh hưởng wire cho FE owner |

**Quyền sửa:** Huy tự thực hiện trong `backend/pharmacy-service/**`, `backend/report-service/**`
và tài liệu phần mình; chỉ chỉnh Surgery-owned gate/source guide nếu cần bảo toàn trạng thái hoãn.
Ngày 2026-10-17 người dùng giao hoàn thiện Billing/Notification liên quan và tích hợp
`backend/support-service/`, gồm root POM, Dockerfile, Compose, CI và Gateway cần thiết.
Host và hai exports đã được triển khai theo quyền task-scoped này; ownership lâu dài giữ nguyên.
Core/Organization/Patient, Clinical/Lab và common không tự mở rộng production write scope.
Không đọc SQL/repository của module khác, kể cả khi hai module cùng JVM.

## 2. Đối chiếu baseline trước implementation (giữ để truy vết)

Các nhận xét dưới đây là source inspection, **không phải kết quả nghiệm thu runtime V1 mới**.

| Bằng chứng source | Đã có | Phần điều chỉnh cần làm |
|---|---|---|
| `pharmacy/web/PrescriptionController.java`, `application/service/PrescriptionApplicationService.java` | POST/GET/cancel/dispense public, create V0 có stock reservation | Controller inject `CreatePrescriptionUseCase`; writer này từ chối `careContractVersion=1`. Cần driving adapter V1 thật, không chỉ bỏ fence |
| `ContextCheckedPrescriptionCreationService`, `CarePrescriptionCreationService` | Exact Clinical context + identity, command receipt/fingerprint, server snapshot, recheck sau lock | Nối API/profile với provider thật, không gọi overload thiếu proofs hoặc mock authority |
| `DispenseApplicationService`, `CareDispenseTransactionService`, `PrescriptionClearanceAuthorizationService` | V0 receipt orchestration và kernel V1 clearance/stock | Public dispense hiện dùng receipt V0. Cần dispatch theo persisted mode sang kernel V1 với signed staff actor |
| `PrescriptionCareEventWriterAdapter`, `contracts/care-finance-v1/README.md` | Immutable V1 lifecycle bytes, `delivery_enabled=false`, fixtures outpatient | Producer V1 còn HELD; same-byte consumer tests/activation là gate, không bật cả lịch sử bằng UPDATE hàng loạt |
| Pharmacy Feign clients và `application.yml` | Patient/Organization/Clinical clients, timeout, opt-in identity/context/clearance | Patient/Organization discovery còn tên cũ; retarget Core. Global care-finance gate đang dùng chung cho outpatient và admission |
| Report `ReportEventConsumer`, `ReportController`, `ReportApplicationService` | V0 flat payloads, daily/monthly/top-medicines | Legacy consumer reject versioned envelope; daily/monthly có zero-fill và naming revenue legacy, không tự đổi semantics |
| Report cash/refund services/configs, `FinancialSnapshotReadService` | Cash/refund/early recovery/replay kernels; gated snapshot reader | Reader hiện cần publication đủ `FinancialAmounts`; không bật toàn bộ all-five publication để chỉ đọc cash/refund MVP |
| `ReportOperationalConsumerConfiguration` | Operational intake có completion/Lab/Rx | Bindings còn Surgery/Admission; tách allowlist ngoại trú, không bật một consumer hỗn hợp làm side effect deferred |
| Report/Notification POM + Boot applications, root reactor | Hai executable Boot modules riêng; chưa có Core/Support host | Chuẩn bị plain Report library, per-module persistence/security trước khi co-host; không depend BOOT-INF JAR |

Prefix `pharmacy/` và `report/` ở bảng chỉ package dưới module tương ứng, không thư mục mới.
Inventory đầy đủ exact DTO/errors/role/queue/profile sẽ được khóa ở H6-01; không suy payload từ bảng này.

### Chỗ cần reuse trước khi thêm code

- Pharmacy: creation/terminal/dispense specialist services, clearance storage, reservation expiry
  lease/reconciliation, payment receipts, outbox dispatcher/confirm/fencing và ArchUnit tests.
- Report: `CashReceiptApplicationService`, `CashRefundApplicationService`, cash/refund replay,
  `OperationalReportFactReceiver`/contribution stores, source receipts và read ports.
- Fixtures V0: `backend/pharmacy-service/src/test/resources/contracts/` và
  `backend/report-service/src/test/resources/contracts/`.
- Fixtures V1 Pharmacy: `backend/pharmacy-service/src/test/resources/contracts/care-finance-v1/`;
  Billing source: `backend/billing-service/src/test/resources/contracts/ledger-v1/` và
  `clearance-authority-v1/`. Presence không đồng nghĩa đã owner-accepted/live.
- Regression suites: `ContextCheckedPrescriptionCreationServiceTest`,
  `CarePrescriptionCreationServiceTest`, `PrescriptionControllerTest`,
  `PrescriptionCareEventWriterPostgresTest`, `PrescriptionClearancePostgresTest`,
  `CashReceiptPostgresTest`, `CashReceiptRabbitIntegrationTest`, `OperationalReportRabbitIntegrationTest`,
  `ReportControllerTest`, `RefundCashConfigurationTest` và hai module `ArchitectureTest`.

## 3. Cách theo dõi

**12 task chính / 48 subtask**, hiện **48/48 subtask trong môi trường phát triển hiện có**,
nghiệm thu ngày 2026-10-17. H6-02.3/H6-12.3 đã chạy inventory và cutover/rollback trên cold
copies của chính hai Compose volumes cũ theo yêu cầu tiếp tục của người dùng; kiểm cả hai
persisted Rabbit node identities. Dữ liệu nguồn quan sát được: Pharmacy năm bảng legacy đều
rỗng, Report/Notification chưa có bảng, hai broker nodes không có queue. Không giả lập lịch sử
hoặc tự baseline schema Pharmacy. Bốn writes mới giữ nguyên qua legacy → Support → legacy
→ Support, broker restart giữ probe bytes; source volume SHA-256 trước/sau không đổi.
H6-09 có code/test reader, không ký thay external completeness/coverage. Đây là tiến độ Huy,
không là tỷ lệ toàn backend, paid Lab acceptance hay xác nhận production đã cutover.
Commands/counts/source IDs/hashes ở [runbook mục 8–9](huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).

- **LOCAL:** Huy có thể implement/test trong module sở hữu, chưa cần service khác chạy.
- **CONTRACT:** Huy có thể code/test local trước; đóng end-to-end/bật gate cần producer/owner thật.
- **SHARED:** cần giao scope/owner phối hợp; không tự sửa shared/foreign paths.
- **RUNTIME:** dùng DB/broker/app thật; thiếu engine hoặc skipped phải ghi rõ, không tick pass.

Mỗi subtask đóng phải ghi ngay bên cạnh: commit, test/scenario, flags, result, evidence path.
Task chính đóng khi cả bốn subtask và điều kiện đóng đạt. Không lấy test của lượt merge trước
làm baseline Pharmacy/Report. Không tính những task Surgery/full V2 hoãn vào mẫu số Huy MVP.

## 4. Task chi tiết

### H6-01 — Khóa baseline và manifest phần Huy

Map M6-01/02. Ưu tiên P0. Dependency: không. **LOCAL**.
Files: hai module owned, contract fixtures, README/.http và bảng evidence trong plan này.

- [x] **H6-01.1** Ghi commit/worktree, inventory controllers/use cases/jobs/gates/migrations;
  phân loại V0 / outpatient V1 / deferred. Hash migration và fixture để so sánh sau refactor.
- [x] **H6-01.2** Lập manifest exact method/path/request/response/error/role/actor;
  event version/producer/key/bytes, queue/DLQ, JWT subject và idempotency/business keys.
- [x] **H6-01.3** Chạy baseline unit/web/architecture riêng Pharmacy–Report; inventory PG/Rabbit/IT,
  ghi executed/failed/skipped và nguyên nhân. Chưa có Docker không thay integration proof bằng mock.
- [x] **H6-01.4** Chọn golden slice ngoại trú và fixture nguồn cùng producer commit;
  ghi từng gap vào mục 5, phân biệt thiếu public wiring với thiếu upstream contract.

Đóng khi: có manifest và baseline tái tạo được, biết chính xác kernel nào reuse/gap nào cần chỉnh.
Không đổi source producer hoặc tự tick global M6-02 của cả team.

Evidence H6-01: baseline `5f40b32c` + manifest/hashes 62 file; baseline 1.399 passed,
32 skipped ở selector không-container; full run có hai Docker errors và không đóng runtime acceptance.
Chi tiết command/count/golden slice tại [baseline evidence](huy-backend-mvp-baseline.md).

### H6-02 — Tách gates ngoại trú khỏi nội trú/phẫu thuật

Map M6-14. P0. Dependency: H6-01. **LOCAL**, profile tổng là **SHARED**.
Files: Pharmacy/Report configs, `application.yml`, listener/job conditions và test wiring.

- [x] **H6-02.1** Tạo cấu hình module MVP có allowlist ngoại trú; chọn mode rõ ràng.
  Nếu phải thêm gate riêng thì khóa tên ở manifest; không dùng global `care-finance-v2=true`
  như bằng chứng mọi integration đã được phép bật, không fallback V1 sang V0.
- [x] **H6-02.2** Giữ admission-consumer/authority, settlement/final-recognition,
  Surgery/Admission operational intake và full publication/archive OFF ở MVP;
  test bean/client/listener/job absence cả khi environment bật global flag.
- [x] **H6-02.3** Kiểm kê historical pending/HELD/outbox và reservation jobs trước cutover.
  Phân loại deferred deliveries bằng policy durable/quarantine; không ACK-drop, purge,
  reset inbox, hoặc phát effect nội trú do job đang dùng chung.
- [x] **H6-02.4** Thêm startup/negative matrix chứng minh luồng ngoại trú không gọi Inpatient/Surgery;
  giữ V0 regression riêng và migration checksums. Surgery app OFF do profile tổng owner triển khai.

Đóng khi: config owned fail-closed, không implicit deferred effects; evidence cũ bảo toàn.
Không xóa module Surgery khỏi reactor hoặc xóa database để giảm service count.

Evidence H6-02.1/.2: hai `application-mvp-outpatient.yml`, negative profile guards,
PharmacyMvpDeferredConfigurationTest và ReportMvpDeferredConfigurationTest đạt cả global false/true.
.3/.4 còn mở phần inventory DB/broker thật và packaged startup/profile tổng; không claim đã drain backlog.

Batch 8 đóng .4 LOCAL sau packaged Pharmacy/Report MVP negative matrix khi global/legacy flags bật;
assert không có admission authority/consumer/Inpatient client/legacy payment consumer, Core destination
và Open-in-View tắt, deferred Report routes 404. V0 saga vẫn được chạy riêng. Profile tổng, lịch sử
broker/database và operator inventory chưa nghiệm thu; H6-02.3 còn mở.

Batch 5 chuẩn bị .3: hai operator SQL resources chỉ đọc có REPEATABLE READ, timeout và ROLLBACK;
phân loại Pharmacy HELD/quarantine/mode/receipt/reservation/lease và Report refund/source/coverage/
rebuild. Có resource guards và disposable PG preservation tests. Chưa chạy inventory trên DB/broker
được duyệt; .3/.4 vẫn mở. [Runbook phần Huy](huy-mvp-cutover-runbook.md) không tự cấp quyền cutover.

Refresh 2026-10-16: `MvpCutoverInventoryResourceTest` của cả Pharmacy và Report đạt (2/2),
xác nhận script inventory vẫn chỉ đọc, bounded và không lộ payload/định danh nhạy cảm. Đây chỉ là
kiểm tra resource; không thay thế quan sát DB clone/broker được duyệt nên H6-02.3 vẫn mở.

### H6-03 — Retarget identity sang Core, giữ Clinical authority

Map M6-05. P0. Dependency: H6-01; nghiệm thu cần Core M6-04. **CONTRACT**.
Files: Pharmacy Patient/Organization/Clinical Feign clients, adapters, fallback, configuration/tests.

- [x] **H6-03.1** Tách configurable discovery destination khỏi logical service identity;
  Patient/Organization clients hướng Core ở MVP, giữ `contextId`, exact paths, caller subject/scope.
  Legacy destination chỉ thuộc compatibility profile, không runtime fallback khi Core lỗi.
- [x] **H6-03.2** Giữ lookup exact patient/staff/department và Clinical record/visit/prescriber;
  phân biệt confirmed absence với timeout/5xx/malformed/stale evidence; fail-closed và bounded retry.
- [x] **H6-03.3** Test fake HTTP upstream local: signed credential/correlation,
  wrong IDs/roles, inactive staff/khoa, unavailable và timeout không tạo prescription/reservation.
- [x] **H6-03.4** Chạy lookup thật tới packaged Core/Clinical theo fixture/source manifest;
  xác nhận destination parity, allowlist pharmacy-service và không call retired deployment names.

Đóng khi: identity có origin thật, không suy accountId thành staffId/patientId và không đoán visit.
Core/Clinical adapters upstream do Hoàng Anh/Vinh xử lý; Huy không sửa chúng trong task này.

Evidence .1/.2: configurable Feign names, Core profile + PharmacyCoreDestinationTest,
reuse PrescriptionIdentityAdapterTest/PharmacyClinicalContextAdapterTest và context-checked service tests.
Batch 3 .3 chạy PrescriptionIdentityHttpTest/PharmacyClinicalContextHttpTest qua HTTP server/Feign
thật local cùng checked-writer regression: credential/correlation, IDs/absence/unavailable và
fail-closed đạt. Evidence ở baseline batch 3.

Batch 8 packaged Core/Clinical acceptance tạo identity/context qua public API rồi gọi production
Pharmacy adapters bằng service JWT. Exact IDs, absence/unavailable, UUID correlation, caller type,
Core destination, role/security pass. Clinical's supported compatibility seed ran with its durable
publisher OFF because the actual durable legacy writer returned HTTP 500; see activation handoff.
Thus .4 closes for packaged lookup only, not Clinical publisher or public V1 creation end-to-end.

Refresh chốt độc lập 2026-10-16: `PharmacyAuthorityRuntimeAcceptanceIT` đã bỏ workaround tắt
Clinical durable publisher và chạy lại với durable-events=true: 2 passed, 0 failed/errors/skipped,
Maven exit 0. Lỗi timestamp cũ đã được sửa upstream; không còn là active blocker. Lookup vẫn
không chứng minh Gateway hoặc actual paid chain. Original completion source proof ở batch 9.

### H6-04 — Nối public creation vào kernel outpatient V1

Map M6-10.1. P0. Dependency: H6-01/02; đóng runtime cần H6-03. **LOCAL + CONTRACT**.
Files: `PrescriptionController`, DTO/ports, context-checked writer, receipt adapter, .http/web/PG tests.

- [x] **H6-04.1** Nối driving adapter V1 vào `CreateContextCheckedPrescriptionUseCase`;
  dispatch explicit theo care contract, reject admission context trong MVP. Giữ URL/envelope V0,
  không chỉ xóa exception V1 trong legacy writer. Wire bổ sung phải được ghi manifest trước code.
- [x] **H6-04.2** Ánh xạ command idempotency vào kernel receipt hiện có; nếu dùng header
  `Idempotency-Key` thì đó là đề xuất cần khóa Gateway/client pass-through, không claim đã hỗ trợ.
  Actor lấy signed principal; DOCTOR chỉ dùng staff chính mình, ADMIN cần prescriber identity thật.
- [x] **H6-04.3** Reuse catalogue snapshot, ordered locks, expiry/available-stock checks và
  context/identity recheck sau lock; prescription/slip/reservations/receipt/V1 outbox cùng transaction.
  Same key/same intent trả cùng outcome; khác intent conflict, không đơn/reservation mới.
- [x] **H6-04.4** Test API → real writer/DB: duplicate concurrent create, đổi giá client/catalogue,
  một dòng thiếu stock, upstream proof stale sau chờ lock, wrong episode/actor; V0 vẫn giữ behavior.

Đóng khi: public V1 tạo được aggregate với proofs thật và replay ổn định, chưa tự bật HELD delivery.

Evidence .1–.3: PublicOutpatientPrescriptionCreationService gọi duy nhất proof-checked writer,
config profile + năm gate (batch 2 yêu cầu cả clearance consumer), optional header V1 Idempotency-Key
và web tests negative/actor/correlation.
Creation kernel/transaction/receipt/outbox dùng lại. Batch 7 đóng .4 LOCAL bằng
OutpatientApiPostgresTest: signed HTTP → real writer/PG, same-key race/replay, untrusted client price,
server-price snapshot/catalogue drift, multi-drug rollback, stale proof sau real drug-lock wait,
wrong episode/prescriber/refresh token. Upstream ports là isolated test doubles, không là packaged
Core/Clinical. H6-04 đạt 4/4 code/test local; task chính vẫn CONTRACT OPEN theo H6-03/12.

### H6-05 — Nối dispense/cancel/expiry đúng mode và state machine

Map M6-10.3/10.4. P0. Dependency: H6-01/02/04; grants acceptance ở H6-06. **LOCAL + CONTRACT**.
Files: dispense orchestrator/V1 transaction service, clearance authorization/storage, terminal services/jobs.

- [x] **H6-05.1** Dispatch dispense theo mode của persisted prescription, không caller-controlled fallback;
  V1 dùng signed staff actor + V1 kernel, V0 giữ receipt/SYSTEM actor có kiểu rõ ràng.
  Receipt/grant arrival V1 không tự trừ stock hoặc tạo giả staff UUID.
- [x] **H6-05.2** Kiểm exact PRESCRIPTION target/patient/episode/purpose và valid time sau tất cả lock waits;
  giữ authorization/effects cùng local transaction. Ghi rõ hiệu lực/revoke semantics theo hợp đồng
  Billing thực tế; không tự coi grant local là authority vĩnh viễn hoặc reuse cho đơn khác.
- [x] **H6-05.3** Reuse cancellation/expiry/failure transaction boundaries và lease;
  release reservation đúng một lần, terminal không reopen, authorization denied không thành stock failure.
  Retry terminal operation giữ outcome/IDs, không tạo thêm lifecycle facts.
- [x] **H6-05.4** PG tests: hai dispense cạnh tranh, grant hết hạn lúc chờ lock, wrong grant,
  multi-drug rollback, cancel/expiry vs dispense, late payment; đối soát stock/slip/reservation/outbox.

Đóng khi: public V1 không lệ thuộc V0 receipt proof và terminal/concurrency invariants được chứng minh.

Evidence batch 2 .1/.3: OutpatientPrescriptionLifecycleApplicationService + gated controller wiring;
mode chọn từ persisted prescription, V1 admission/V0 không fallback, signed actor trước stock effects.
Dispense orchestration dùng propagation NEVER; chỉ ba stock-error codes kích hoạt REQUIRES_NEW
re-evaluation, FAILED retries đọc exact HELD proof; cancel/expiry gọi kernel riêng và leased expiry
wrapper giữ REQUIRES_NEW. Unit/web/architecture + regression V0 đạt, không release HELD.
Batch 7 đóng .2 LOCAL: exact live Billing lookup DTO đã khóa, signed Feign client và current
eligibility check sau mọi local lock; denied/stale/unavailable/expiry không có stock/failure effects.
PrescriptionCurrentAuthorityTest, PharmacyBillingClearanceAdapterTest/HttpTest và API/PG lock-wait
cases đạt. Đây là observation theo thời điểm, không lease chống một revocation xảy ra sau response;
real Billing PRESCRIPTION fixture/issuer acceptance vẫn thuộc H6-06/12. API cần sáu gates, default OFF.
Batch 7 đóng .4 LOCAL: OutpatientApiPostgresTest 15/15, CareDispensePostgresTest 25/25 và
PrescriptionClearancePostgresTest 8/8. Có concurrent dispense/cancel, expiry-vs-dispense, multi-drug
rollback, wrong patient/expired grant, expiry/revocation/unavailable sau real lock wait và late duplicate
grant sau cancel không reopen/stock effect. Đây không là Billing compensation acceptance của H6-07.3.
H6-05 đạt 4/4 local; task chính CONTRACT OPEN cho live issuer/current-authority rollout.

### H6-06 — Nghiệm thu lifecycle V1 và release producer có chọn lọc

Map M6-02/10.2/12.3. P0. Dependency: H6-01/04/05. **CONTRACT**.
Files: Pharmacy codecs/writer/outbox adapters/consumer, fixtures và tests; foreign consumers read-only.

- [x] **H6-06.1** Khóa created/filled/cancelled/expired/dispense.failed ngoại trú cùng bytes nguồn;
  preserve eventId/occurredAt/correlation/logical producer và giá snapshot; filled có dispenseId thật.
- [x] **H6-06.2** Verify Billing issuer/adjustment, Clinical attachment, Report và Notification
  bằng fixture byte-identical. Billing quyết charge/refund; Pharmacy không invent invoiceId hoặc
  gửi lệnh “đã hoàn tiền”. Purpose/episode/source mismatch phải bị reject.
- [x] **H6-06.3** Thêm admission-free activation boundary cho V1 outpatient producer đã accepted;
  transaction/lease/fence bảo toàn ordering và replay original bytes. Historical HELD chỉ release
  sau audit/approval riêng, không đổi hàng loạt `delivery_enabled` vì đã bật profile MVP.
- [x] **H6-06.4** Rabbit/PG test confirms, unroutable, broker restart, retry/DLQ và duplicate grants;
  chỉ bật selected gate trong isolated acceptance khi issuer/consumer cùng đạt, production cần rollout.

Đóng khi: source-bound chain được consumer chấp nhận và new outpatient facts publish được an toàn.
Missing foreign acceptance giữ integration subtask mở, không chặn Huy code/test local trước.

Evidence batch 3 .1: giữ created.outpatient fixture cũ, thêm bốn outpatient alternative fixtures,
khóa hashes riêng trong baseline và codec round-trip cả năm. Filled bytes được Report dùng trực tiếp.
Đây là examples từ producer codec, không proof events đã commit/publish ở runtime hoặc cả team accepted.
Admission fixtures/migrations cũ không đổi. Batch 7 đóng .3 LOCAL implementation: V20 tạo cohort
chỉ cho new persisted outpatient; sáu gates/profile + event gate/acceptance reference, default OFF.
Historical HELD không join bằng replay/flag; immutable trigger, terminal kế thừa cohort, lifecycle
ordering và published V1 proof retention có real PG tests. Upgrade V19→20 giữ bytes/state/leases/
nineteen checksums. Không release historical rows hoặc coi acceptance-reference là owner sign-off.
.2/.4 và producer rollout vẫn mở; broker confirms/unroutable/restart/three-attempt quarantine/
lease-owner fencing đã kiểm thử local, chưa là issuer/consumer chain thật. Evidence tại
[batch 7](huy-backend-mvp-baseline.md#12-current-clearance-and-selective-outpatient-delivery).

### H6-07 — Chốt saga Pharmacy–Billing và recovery

Map M6-13. P1. Dependency: H6-05/06 và Billing M6-12. **RUNTIME + CONTRACT**.
Files: receipt/dispense/failure/outbox tests, existing `PharmacyDistributedRuntimeAcceptanceIT`.

- [x] **H6-07.1** Pharmacy offline → pending delivery → restart: V0 payment resumes saga;
  V1 grant/cash delivery chỉ persist proof, dispense vẫn cần authenticated command. Đối soát hai mode riêng.
- [x] **H6-07.2** Inject crash sau stock commit trước receipt-finalize/ACK và sau publish trước mark;
  replay đọc committed result, cùng event/operation không stock decrement/slip/financial effect lần hai.
- [x] **H6-07.3** Chạy expired/cancelled/stock failure và late/duplicate payment với Billing thật;
  một adjustment/compensation obligation đúng source, pending refund không báo completed.
- [x] **H6-07.4** Poison/conflicting bytes/DB rollback/DLQ tests bounded;
  lưu source IDs, counts, stock/ledger/outbox snapshots đã redacted, không reset inbox cho đẹp demo.

Đóng khi: effects một lần được chứng minh qua durable state; không tuyên bố broker exactly-once.

Batch 7: PharmacyDistributedRuntimeAcceptanceIT chạy bốn packaged applications, bốn private PG
stores và Rabbit thật, chỉ business HTTP/broker, không đọc/seed foreign DB. **4 passed, 0 failed,
0 skipped, Maven exit 0**: duplicate/new envelope IDs giữ một stock/report effect; Report JVM down
paid saga vẫn xong rồi catch up; broker down/Rx commit/producer JVM restart hồi phục outbox;
Pharmacy JVM down/payment chờ durable rồi restart hoàn tất V0 saga một lần. Đây là V0 compatibility,
không thay V1 source/compensation/fault-at-exact-commit acceptance.

Batch 8 đóng H6-07.1–.4 compatibility/runtime cases: 12 packaged V0 cases pass, gồm Pharmacy
offline/restart, three kill-at-commit boundaries, real Billing cancel/expiry/stock shortage with
late and duplicate payment, conflicting message bytes, bounded retry/DLQ, DB rollback and redrive.
The test-only fault bootstrap is excluded from the shipped Boot JAR and only observes/injects into
Pharmacy's private acceptance DB. Snapshots contain synthetic IDs/states and hashes. Billing saga
is REFUNDED while Pharmacy receipt is COMPENSATED; no false `payment.refunded` fact is emitted.
Original Billing V1 grant/cash fixtures also survive two Pharmacy restarts without automatic dispense.
That fixture check does not substitute for live issuer acceptance: Billing source is HELD and the
opt-in V1 chain remains skipped by default. H6-06.2/.4 and end-to-end rollout remain CONTRACT OPEN.
Detailed commands/limits: [batch 8 evidence](huy-mvp-batch-8-acceptance.md).

### H6-08 — Thu gọn Report intake còn facts ngoại trú có evidence

Map M6-08.1/08.2/14. P1. Dependency: H6-01/02; fixtures của H6-06 có thể dùng trước activation.
**LOCAL + CONTRACT**. Files: operational/cash/refund consumers/config, mappers, receipts/stores/tests.

- [x] **H6-08.1** Chọn intake medicalrecord.completed / lab.result.created / prescription.filled
  và payment.completed / payment.refunded đúng V1. Tách khỏi V0 flat parser và deferred bindings;
  completed visit không cộng thêm lượt từ medicalrecord.created cho cùng episode.
- [x] **H6-08.2** Reuse claim/eventId + business-operation receipts, canonical fingerprint;
  new eventId/same operation không contribution mới, same key/different intent conflict;
  units/drug/department/timezone từ source, không suy toàn patient hoặc đọc producer SQL.
- [x] **H6-08.3** Reuse cash/refund kernels, original payment linkage và refund-before-payment recovery;
  filter outpatient classification đúng contract, deposit/settlement không thành cash ngoại trú/earned.
  Partial refund và nhiều dòng Rx không double-count gross payment/items.
- [x] **H6-08.4** Same-byte + PG/Rabbit tests duplicate/out-of-order/conflict/crash;
  legacy queue không nhận V1 như payload flat. Deferred evidence còn trên durable path có owner,
  không bị silently ACK-drop hoặc xóa binding/queue lịch sử trong broker thật.

Đóng khi: mỗi metric có đúng một contribution nguồn và không phát sinh Surgery/Admission effects.

Evidence batch 3 .1–.3: default-OFF MVP five-key consumer/queue/DLQ, source validation trước
effects, operational/cash/refund receipt kernels reuse, bounded outpatient-only early recovery.
Actual Lab/outpatient filled/Billing service payment+refund bytes và admission/deposit negatives đạt;
original dedupe/refund/partial/operation tests vẫn đạt regression. Clinical fixture hiện là ADMISSION;
outpatient completion positive chỉ là LOCAL VARIANT, cần fixture thật Vinh trước activation.
  .4 DB/broker/fault acceptance còn mở ở batch 3; không xóa hoặc drain các queue cũ.

Batch 9 (2026-10-16) supersedes trạng thái .4 và source-positive gap ở trên: real PG/Rabbit
tests giữ nguyên Lab/Rx/Billing bytes, duplicate/new delivery ID, conflict DLQ, early refund,
storage rollback và retry. Fault sau commit trước ACK gọi receiver/transaction thật lần nữa mà
không thêm contribution; đây là mô phỏng cửa sổ crash trong JVM, không kill producer/Support JVM.
Legacy queue giữ backlog, không có MVP consumer và không cộng flat payment. Packaged Core/Clinical
hiện tại tạo OUTPATIENT_FOLLOW_UP bằng public API với audited emergency override; original producer
bytes tạo đúng một completed visit sau listener restart/replay, conflict giữ trên DLQ, coverage
không tự được chấp nhận. Actual ADMISSION fixture vẫn là negative. H6-08 đạt 4/4 LOCAL;
paid chain, Notification/Support, historical coverage và rollout vẫn mở ở H6-06/11/12.
Commands, source hash và giới hạn proof: [batch 9 acceptance](huy-mvp-batch-9-acceptance.md).

### H6-09 — Reader MVP: số lượt/thuốc và cash/refund có coverage

Map M6-08.3/08.4. P1. Dependency: H6-01/08. **LOCAL**, Gateway/client acceptance là **CONTRACT**.
Files: Report read ports/services/adapters/DTO/controllers, .http, web/PG/replay tests.

- [x] **H6-09.1** Reuse source stores để query visits/Lab/Rx/items và cash received/refunds/net cash;
  không dùng `FinancialSnapshotReadService` all-five publication như prerequisite,
  không viết lại toàn bộ financial/operational publication platform.
- [x] **H6-09.2** Định nghĩa coverage từ evidence/cursor/cutover thực: thiếu/không đối soát → unavailable;
  zero chỉ khi chứng minh kỳ phủ đầy đủ và không operation. Event không đến không đủ chứng minh zero;
  không fabricate lịch sử, không biến legacy zero-fill thành coverage thật.
- [x] **H6-09.3** Giữ legacy daily/monthly/top-medicines semantics cho compatibility;
  chọn additive/versioned MVP read contract trong manifest và thông báo Gateway/FE owner trước activation.
  ADMIN/MANAGER, pagination/date/zone/currency và error envelope explicit; cash không gắn nhãn earned revenue.
- [x] **H6-09.4** Test coverage gap/legitimate zero, date boundary/timezone, department scope,
  partial refund/early recovery, duplicates và replay từ Report evidence riêng;
  rebuild không chạy producer command, không gửi lại notification hay ghi financial effects.

Đóng khi: đọc được bộ KPI MVP có đối soát/coverage, không cần settlement/liability/full history V2.
Route mới nếu cần là đề xuất ở manifest; không coi Gateway đã route chỉ vì controller local có.

Evidence batch 3 .1–.3 là LOCAL implementation: một REPEATABLE READ query từ Report-owned
operation/cash/refund sources, DTO counts/cash/netCash và hai additive ADMIN/MANAGER routes.
V27 coverage table trống mặc định, không API accept/write hoặc suy coverage từ queue yên lặng.
Proof phải chỉ rõ cả năm nguồn, period/zone/exact department, thời điểm đối soát/owner acceptance
và local witness; thiếu/chưa accept/drift/pending => REPORT_NOT_FOUND. Source exports/reconciliation
thật và Gateway/client rollout vẫn là contract gate, không claim đang có historical coverage.
.4 đã có unit/web/gate/hash tests và isolated PostgreSQL scenario nhưng PG skipped vì engine
unavailable; acceptance/replay/timezone DB/broker còn mở, không tick hoàn tất task chính.

Batch 6 supersedes trạng thái skipped của .4: OutpatientReadPostgresTest 2/2 trên PG16.14
chứng minh accepted zero, late-source drift, pending refund unavailable, partial refund/net cash,
exact department/zone, ranh giới midnight và witness của toàn cửa sổ dù query subrange.
OutpatientReportRabbitIntegrationTest 5/5 chứng minh intake duplicate/conflict/refund recovery,
rollback/retry/DLQ. Existing isolated cash/refund/operational replay PG tests chạy lại không có
producer command, financial write hoặc notification publisher. .4 đóng LOCAL; coverage test-only
không được nhập làm lịch sử thật, H6-09 rollout vẫn cần accepted source exports/Gateway owner.

### H6-10 — Chuẩn bị Report thành module dùng trong Support

Map M6-03/06. P1. Dependency: H6-01/02. **LOCAL** ở Report, phối hợp shared-build khi package.
Files: Report POM/launcher/config/persistence/security/advice và architecture/context tests.

- [x] **H6-10.1** Tách Report business module/plain JAR khỏi executable standalone launcher;
  chọn classifier/launcher packaging tương thích build owner, giữ package names và rollback launcher.
  Host không depend executable BOOT-INF artifact hoặc scan legacy application class.
- [x] **H6-10.2** Chuẩn bị explicit Report datasource/EMF/repository/transaction-manager/Flyway wiring;
  kiểm toàn bộ unqualified `@Transactional`/TransactionTemplate/native JDBC dùng đúng Report store,
  không dùng @Primary hoặc bean override che nhầm transaction manager.
- [x] **H6-10.3** Scope Report beans, JWT filter/security chain, advice, ObjectMapper/Clock/ZoneId,
  AMQP factory/scheduler names để co-host không collision/catch-all permission;
  giữ API role/human-access token checks và logical consumer identity.
- [x] **H6-10.4** Module context/architecture tests và packaged plain-JAR smoke;
  schema fresh/upgrade giữ nguyên released migration bytes, giữ standalone regression,
  không import Notification entities/repositories/Feign contacts vào Report.

Đóng khi: Report library composition-ready; chưa coi Support host đã chạy chỉ vì Report compile.

Evidence batch 3 .1: own Report POM attaches classifier `lib` plain JAR (root classes/migrations),
main executable JAR và tên launcher rollback giữ nguyên. ReportPackagingAcceptanceIT kiểm structure,
launcher và exact V1/V27 migration bytes sau package. Host phải depend classifier lib, exclude
ReportServiceApplication khỏi scan. Chưa có host/multi-store/security startup proof; .2–.4 mở.

Batch 4 (2026-10-15) supersedes trạng thái .2/.3 ở batch 3: ReportModuleConfiguration export
cho plain lib, profile report-module + outpatient-mvp (mvp-outpatient alias), scoped scan, qualified
DS/Flyway/EMF/repository/tx/JDBC, migrations copy nguyên bytes sang module-specific location.
Standalone giữ aliases cũ; tất cả transaction source chọn reportTransactionManager, JDBC injections
chọn reportJdbcTemplate, không @Primary fallback. Guards + recording-manager tests đạt local.
Security chain chỉ match Report trong module mode, bean/advice names riêng; 404 resolver chỉ
claim Report paths. Clock reader qualified, zone explicit, ONE host common JSON mapper là hợp đồng
composition chứ không import customizer full V2. Report jobs dùng scheduler riêng bounded 1..2.
.4 vẫn OPEN: ReportModulePostgresTest có hai stores test-owned nhưng skipped do Docker engine
unavailable; packaged Support/Notification/real migration/upgrade vẫn cần owner acceptance.

Batch 5 tăng proof .4: artifact tests so từng byte cả 24 SQL files hiện có (latest V27),
ba JVM mới load Report từ lib JAR khi loại target/classes/test-classes khỏi classpath. Scoped reader
đọc thiếu coverage trả unavailable; wrong-mode và foreign default datasource bị từ chối đúng guard.
Đây không phải full module/Support startup có datastore thật. Test đếm migration sửa 27 thành 24
theo source; không thêm V3–V5 hoặc thay released SQL. .4 vẫn mở PG/upgrade/host acceptance.

Source Core đã chuyển Patient/Organization nên batch 4 sửa fixture paths ở Pharmacy và ba Surgery
test classes (compatibility only), không đổi payload/logical producer hoặc bật Surgery. 40 identity
HTTP tests local chạy lại; real Core .03.4 còn mở. Huy hỗ trợ profile canonical outpatient-mvp,
không sửa mode của Vinh, Gateway, Notification, Compose/root build/CI.
Owner actions chi tiết: [Support handoff](../handoffs/HANDOFF-HUY-MVP.md).

Batch 6: Docker đã chạy; [10 đầu việc kiểm chứng](huy-mvp-batch-6-acceptance.md) bổ sung
public creation/lifecycle → real PG, Report five-key intake → real Rabbit/PG, duplicate/conflict,
early refund/listener restart, bounded retry/DLQ/rollback, coverage date/department/window witness,
và V26 → V27/resource-location rollback. Nhóm chạy tập trung đạt 36/36, không skipped;
artifact/JVM đạt 5/5. ReportModulePostgresTest xác nhận hai stores test-owned, không standalone
launcher hoặc default transaction manager. Trạng thái Docker-unavailable của batch 4–5 là lịch sử.
H6-10.4 đóng LOCAL sau fresh PG migration/JPA wiring và upgrade V26 → namespaced V27 →
standalone migration location, giữ checksums/legacy rows/new writes, không Flyway repair.
H6-10 hoàn tất chuẩn bị Report library; packaged Support/Notification vẫn là H6-11, chưa đóng.
Đây là mười đầu việc nằm trong các subtask hiện có, không tự cộng thành mười task chính đã đóng.
Real producer current-authority, historical inventory/coverage và Support/Gateway vẫn có gate riêng.

### H6-11 — Phối hợp Support host và resource isolation

Map M6-03/06/07. P2. Dependency: H6-10 và Lộc hoàn tất Notification module export.
**SHARED + RUNTIME**. Ngày 2026-10-17 người dùng giao trực tiếp hoàn thiện phần Lộc còn thiếu
và tích hợp Support, gồm host và shared build/runtime/Gateway cần thiết. Source hiện có
`backend/support-service/`; quyền này là task-scoped, không chuyển ownership lâu dài.

- [x] **H6-11.1** Chốt host path, artifact/launcher/discovery, property namespaces và composition API
  với Lộc/shared owner; Huy cung cấp Report export, Lộc cung cấp Notification export.
  Scaffold host khi được giao phải dùng skill hiện hành; không copy Notification business code vào Report.
  Evidence 2026-10-17: baseline `27b8832d` + working tree chưa commit; skill new-microservice,
  hai classifier lib, host executable/discovery support-service, explicit module imports/profiles,
  namespaces/credentials và scoped security. Build/package đạt, actual JAR guard probe đạt,
  28 SQL copies nguyên bytes; [runbook](../../backend/support-service/README.md) và
  [verification/giới hạn](../handoffs/HANDOFF-HUY-MVP.md#9-support-host--2026-10-17).
  Không dùng mục này để coi real PG/Rabbit hoặc cả task H6-11 đã đạt.
- [x] **H6-11.2** Wire hai datasource/EMF/Flyway/tx riêng với migration resource locations không lẫn;
  Report và Notification giữ credentials/stores riêng, không cross-store transaction/SQL.
  Shared owner đăng ký reactor/Compose/CI; Hoàng Anh đổi Gateway target Support, URLs giữ nguyên.
- [x] **H6-11.3** Giữ report.q/notification.q và specialized queues/DLQ/factories riêng;
  budget listener/rebuild/delivery/pool tách cấu hình, host JWT/security/advice có exact path scopes.
  Host crash vẫn chung failure domain; không hứa isolation tuyệt đối trong một JVM.
- [x] **H6-11.4** Packaged Support chạy hai PG stores/Rabbit thật: Report replay/load không resend
  notification hoặc chiếm toàn pool; Notification timeout không rollback Report effects;
  một process/discovery registration, không song song standalone writers/jobs trên cùng stores.

Đóng khi: Report + Notification chạy đúng một host và vẫn tách authority/data; có shared/owner acceptance.
Đây là coordination task, không quyền mặc định để Huy sửa Notification/root POM/Gateway.

### H6-12 — Nghiệm thu phần Huy, rollout và tài liệu

Map M6-15/16/17/18. P2. Dependency: H6-03 đến H6-11 đạt phần liên quan. **RUNTIME + CONTRACT + SHARED**.
Files owned: module README/AGENTS/.http, fixtures/tests/evidence; profile/Gateway/CI do owner phụ trách.

- [x] **H6-12.1** Chạy Gateway → creation → Billing selected Rx payment/grant → authenticated dispense
  → Clinical/Report/Notification slice thật, thêm wrong role/target/refresh token/nested path negatives.
  Đối soát IDs, stock, ledger, counts/cash/refund/notice; Huy không sửa Gateway policy để cho test qua.
- [x] **H6-12.2** Map evidence bắt buộc T07–T10/T12–T18 và phần T02/T11/T19 liên quan ở runbook;
  chạy module/packaged-host PG/Rabbit tests và fault scenarios, ghi run/failed/skipped thay vì chỉ số %.
- [x] **H6-12.3** Rehearse stop/drain standalone Report → Support và rollback trên clone giữ current DB;
  preserve original bytes/cursors/pending receipts/HELD và accepted writes, no dual writers/no restore
  snapshot cũ đè payments mới. Shared/operator chịu trách nhiệm topology đủ sáu business apps.
- [x] **H6-12.4** Update source guides owned trỏ scope mới, manifest/.http/test commands và bảng completion;
  ghi gap owner chưa nghiệm thu, không đánh dấu global M6 đã xong hoặc Surgery/full V2 “100%”.

Đóng khi: phần Huy hoạt động trong MVP sáu-service target, integration evidence + rollback tái tạo được.
Không yêu cầu làm thêm KPI/nội trú/phẫu thuật để đóng task này.

Evidence batch 5 .4 LOCAL documentation: Pharmacy/Report/Surgery guides nói rõ scope/modes;
Surgery historical notes không là backlog/activation. .http dùng canonical profile, source/gate
manifest + commands/evidence cập nhật, operator inventory và cutover/rollback checklist có owner
boundaries rõ. Không chỉnh host/Gateway/shared runtime, không claim global M6 hoặc Surgery/full V2
hoàn thành. .1–.3 giữ mở cho real chain và rehearsal.

## 5. Bảng phối hợp — chỉ các blocker MVP

Đây là gap register chung cho các task trên, không spec payload thứ hai. Nếu phát hiện thiếu
hợp đồng mới, ghi source commit/fixture/test tại đây và handoff theo root ownership rules;
không phục hồi các handoff lịch sử làm dependency bắt buộc.

| Gap/gate | Huy làm | Owner/phần cần phối hợp | Acceptance trước khi đóng |
|---|---|---|---|
| Core destination + identity (H6-03) | Client/config/fail-closed tests | Hoàng Anh: packaged Core, patient/staff/department APIs, caller allowlist | Exact lookup/credentials thật, absence khác unavailable; không cần staff head mutation mới |
| Clinical outpatient context (H6-03/04) | Adapter + context checks + create wiring | Vinh: record context/current visit/prescriber/department authoritative | `/api/v1/records/{id}/prescription-context` cùng DTO/fixture, stale/wrong IDs denied |
| Rx source charge/clearance (H6-05/06) | Lifecycle producer + grant consumer + dispense | Lộc: selected charge/payment/allocation và exact PRESCRIPTION grant/effectivity | Outpatient creation fixture → one charge → payment → exact valid grant; revocation semantics nếu source hỗ trợ, không invented contract |
| Failure/cancel/expiry adjustment (H6-06/07) | Terminal fact/source proof + replay tests | Lộc: original-linked obligation/refund operation | One compensation đúng mode; grant invalidation/late payment handling theo accepted contract; pending không completed |
| Operational facts (H6-08) | Report receiver/dedupe/replay | Vinh: Clinical completed và Lab result producer | Byte-identical completion/result fixture, exact source/result revision, không created/completed double-count |
| Cash/refund/coverage (H6-08/09) | Projection + reader, không fake history | Lộc: payment/refund facts và nguồn đối soát/cutover coverage | Link payment gốc, sum đối soát theo episode/currency; cursor coverage có evidence, missing khác zero |
| Support composition (H6-10/11) | Hai exports, host/reactor/Compose/CI/Gateway đã code theo override 2026-10-17 | Runtime/operator: chạy hai stores/broker và cutover thật; không còn chờ Lộc viết export | Two stores/tx/queue riêng; packaged host, signed human rules và no notification replay |
| Route/header/cutover (H6-04/09/12) | API manifest/tests, client impact notes | Hoàng Anh: Gateway targets/policy/header; Vinh/shared: six-app runtime | Exact method/path/role denial, passthrough nếu cần idempotency header, six apps không deferred calls |

Producer code hiện đã có một phần. Mỗi owner gate cần kiểm cùng source mới, không mặc định họ
“chưa làm” hoặc “đã đủ” chỉ từ tên file. Admission lookup/bed-capacity/Surgery policy **không là
blocker ngoại trú**; chỉ giữ trạng thái hoãn an toàn.

## 6. Thứ tự triển khai để không chờ cả team

1. **Batch đầu: H6-01 → H6-02 → phần local H6-04.** Khóa baseline/gates trước; public create
   có thể phát triển bằng adapter tests, nhưng không công bố V1 live khi chưa có proofs upstream.
2. **Pharmacy:** H6-03 và H6-04 → H6-05 → H6-06 → H6-07.
3. **Report song song ở module hiện tại:** H6-08 → H6-09; H6-10 không cần chờ Support host.
   Source fixtures có thể dùng cho local tests trước khi live producer được bật.
4. **Topology cuối:** H6-10 + Notification export/shared assignment → H6-11 → H6-12.

Đây là thứ tự công việc, không chỉ định spawn subagents. Local acceptance và integrated
acceptance tách rõ: thiếu shared host không chặn Huy hoàn thiện code owned, nhưng vẫn chặn
claim runtime sáu-service hoặc đóng end-to-end task.

## 7. Verification và điều kiện hoàn tất

Lệnh existing modules (không tự bỏ PG/Rabbit tests để claim full pass):

```text
mvn -q -pl backend/pharmacy-service,backend/report-service -am test
mvn -pl backend/pharmacy-service -am -Pdistributed-acceptance verify
```

Pharmacy profile hiện bao gồm `PharmacyDistributedRuntimeAcceptanceIT`; inspect coverage và
package dependencies ở H6-01/H6-07, bổ sung V1 slice khi cần. Command thành công mà test skipped
không chứng minh fault/recovery acceptance. Support/Gateway/full reactor commands chỉ chạy
theo host/profile do owner triển khai; không ghi lệnh giả cho host chưa tồn tại.

Mỗi evidence entry gồm commit, selected mode, effective flags, fixture fingerprints, container
versions, tests run/failed/skipped, assertions và source-effect reconciliation. Demo seed chỉ
ở isolated environment; không dùng fixture để điền lịch sử production hoặc reset database.

**Hoàn tất plan Huy khi:** Pharmacy public V1 + source-bound reservation/dispense/terminal flow
đạt; Report tối thiểu có counts/cash/refund và coverage thật; Report composition-ready và chạy
trong Support đã nghiệm thu cùng Lộc/shared owner; runtime MVP không phụ thuộc Surgery/Inpatient;
security/fault/race/replay/rollback có evidence. Full V2/Surgery readiness báo riêng, không nâng scope.

### Bảng cập nhật sau triển khai

Trạng thái hiện hành được chốt theo nghiệm thu ngày 2026-10-17 ở bảng này và mục cuối file.
Các progress note/batch bên dưới giữ ngày chạy và giới hạn lịch sử; nhận định cũ “chưa có
Docker/Support/paid chain” đã được thay bởi kết quả mới, không là việc phải giao lại.

| Task | Trạng thái | Evidence / gap còn mở |
|---|---|---|
| H6-01 | DONE — 4/4 | Baseline/manifest/golden slice/hashes; không là full runtime acceptance |
| H6-02 | DONE — 4/4 local environment | Profile tổng sáu app + inventory cold copies của persisted Compose volumes; schema/data gốc không đổi |
| H6-03 | DONE — 4/4 isolated acceptance | Core/Clinical authority thật trong public Gateway Rx flow; production activation chưa thực hiện |
| H6-04 | DONE — 4/4 isolated acceptance | Fresh API PG cases + actual source/paid chain; server price/reservation/rollback giữ đúng |
| H6-05 | DONE — 4/4 isolated acceptance | Billing issuer/current eligibility thật, JWT/source/allocation/refund negatives; no auto-dispense |
| H6-06 | DONE — 4/4 isolated acceptance | Five lifecycle consumer fixtures + real paid Rx/filled chain; historical HELD không release |
| H6-07 | DONE — 4/4 isolated acceptance | Batch 8 V0 crash/fault evidence giữ riêng; actual V1 payment lúc Pharmacy down và restart đạt |
| H6-08 | DONE — 4/4 isolated acceptance | Full Report + actual source/cash/Rx + replay/recovery; không tự approve historical source coverage |
| H6-09 | LOCAL 4/4 — COVERAGE OPERATIONS OPEN | Reader/PG scope/refund/zone/coverage/replay đạt; external completeness/coverage chỉ nhận khi có operator proof |
| H6-10 | DONE — 4/4 LOCAL | Plain lib, context/security/architecture/JVM smoke và fresh/upgrade PG đạt; Support host thuộc H6-11 |
| H6-11 | DONE — 4/4 isolated acceptance | Hai PG stores/Rabbit, pool/fault/DLQ isolation, packaged migration/restart và một discovery identity đạt |
| H6-12 | DONE — 4/4 local environment | Actual paid chain + persisted-volume cold-copy inventory/rollback/broker restart; giữ bốn writes mới, không dual writer |

H6-12.2 progress note (2026-10-16): added the owned evidence-to-runbook map in
[`huy-mvp-cutover-runbook.md`](huy-mvp-cutover-runbook.md#6-h6-122-evidence-map--refreshed-2026-10-16).
The map records this refresh's 15 non-container Pharmacy/Report suites (215 passing tests) without
claiming container-backed or foreign-owner acceptance. The subtask stays open until source IDs,
fixture hashes and integrated owner/runtime evidence are recorded.

### Rà soát phần độc lập — 2026-10-16

Đã đối chiếu đủ 10 checkbox còn mở với owned code/tests, source foreign hiện tại và điều kiện
nghiệm thu gốc. Không còn subtask có thể đóng toàn bộ chỉ bằng production paths của Huy và
disposable local tests. Phần local của H6-06.4/H6-12.2 đã có evidence; live issuer/consumer,
Support/Gateway và historical/operator proof vẫn cần owner nên không tick thay.

Toàn bộ việc cần nhận, owner, exact source gap và acceptance nằm trong **một file**
[handoff chung Huy](../handoffs/HANDOFF-HUY-MVP.md). Sáu handoff/pointer backend Huy cũ đã được gộp, không giữ
thêm file pointer/backlog trùng. Core retarget đã có packaged acceptance; Surgery chỉ ghi deferred,
không tạo việc mới để tăng scope. Giữ nguyên mẫu số và tiến độ 38/48, không đổi tiêu chí để tăng %.

### Backend continuation — chặn disposition deferred tại Pharmacy (2026-10-16)

Rà soát lại source Clinical V1 cho thấy Pharmacy còn chấp nhận relationship ngoại trú có
disposition `ADMISSION`/`TRANSFER`, trong khi Clinical không nhận thuốc cho các disposition này.
Đã bổ sung kiểm tra trong `OutpatientPrescriptionContextPort.Observation.requireExact`: trả
`PHARMACY_CARE_CONTEXT_INVALID` (HTTP 422) trước lookup identity và trước mutation/creation replay.
Completed outpatient `PRESCRIPTION`/`OUTPATIENT_FOLLOW_UP`/`OTHER` vẫn hợp lệ. Không đổi DTO, fixture,
migration, V0 hoặc các activation/delivery gate; đây là sửa tính đúng đắn trong scope Pharmacy.

Verification mới của lượt này: **85 class không cần container, 694 tests, 0 failure/error/skip**,
gồm observation, creation orchestration/writer, real Feign HTTP và signed controller tests.
Đã thêm 5 PostgreSQL cases cho create/replay bị từ chối và completed outpatient replay hợp lệ;
các case đã biên dịch đạt (`-DskipTests test-compile`) nhưng chưa chạy vì Docker Desktop lỗi startup.
Theo lựa chọn của người dùng, tiếp tục
phần không cần Docker; không dùng kết quả biên dịch thay cho database/runtime acceptance.

Tiến độ vẫn **38/48**. Mục còn mở và yêu cầu chạy lại PostgreSQL tiếp tục nằm trong
[handoff chung Huy](../handoffs/HANDOFF-HUY-MVP.md#7-backend-continuation--pharmacy-disposition-guard-2026-10-16).

### Support composition — 2026-10-17

Theo giao quyền mới, hoàn thiện Notification module export và tạo Support host, đăng ký
reactor/Dockerfile/Compose/CI, Gateway giữ public paths nhưng chuyển hai targets tới Support.
Default Compose có sáu business deployments. Original stores/SQL/queues/HELD được giữ;
standalone launchers chỉ dùng rollback sau stop/drain host. Deferred receipt/refund và
CareProjection/Surgery bị chặn trong host cả khi global outpatient gates được bật.

Verification không cần Docker: **1.436 tests / 136 class, zero failure/error/skip**:
Report 940, Notification 226, Gateway 257, Support 13. Bao gồm default-OFF và opt-in component
graphs, JWT/roles/patient identity/advice, named transaction manager selection và guards.
Packaged JAR inspection/guard smoke đạt, không là real store startup. New container cases
đã biên dịch nhưng chưa chạy: hai stores/Rabbit isolation và actual packaged restart/upgrade.
Các test Billing/Notification container từ mục 8 handoff vẫn chưa chạy trong lượt này.

Đóng **H6-11.1**, tiến độ **39/48**. H6-11.2/.3 đã có implementation và non-container checks,
giữ mở cho runtime resource/security acceptance; H6-11.4 và paid-chain/cutover vẫn cần Docker
và operator evidence. Tất cả phần còn lại cập nhật trong đúng
[một handoff chung](../handoffs/HANDOFF-HUY-MVP.md#9-support-host--2026-10-17).

### Chốt tích hợp backend — 2026-10-17

Đã thực chạy Core, Clinical, Lab, Pharmacy, Billing, Support cùng Gateway/Eureka trên tám
context stores tạo mới và RabbitMQ. Tài khoản/đăng nhập/patient/check-in, EXAM payment và
Rx payment/dispense dùng public API. Khi Pharmacy dừng, Billing vẫn ghi payment/grant; sau
restart, grant đến nhưng chưa trừ tồn. Dispense hợp lệ trừ đúng 3 đơn vị, retry không nhân
effect; Clinical một attachment, Report một filled contribution và hai cash receipts tổng
130.000 VND, Notification một private filled notice. Negative role/token/ownership/path đạt.

Support đã đạt real two-store/broker/pool/fault isolation và packaged migration/restart.
Diễn tập current-store: dừng Support → chạy hai legacy launchers → nhận write mới → dừng
legacy → chạy lại Support; giữ write mới, original bytes/journal/HELD, không dual writers.
Đây là disposable acceptance, chưa là historical-clone/broker cutover được operator duyệt.

Sửa các gap phát hiện khi chạy thật: Billing cho đúng Pharmacy service credential lookup,
kiểm current PRESCRIPTION ledger/allocations và giữ original source Instant thay timestamp
đã bị DB làm tròn; Report refund recovery poll tự mở named read transaction; Gateway giới
hạn exact Pharmacy paths/methods/roles. Không đổi migration/fixture hoặc Core/Clinical/Lab
production code. Các activation gates mặc định giữ OFF.

**2.128 unique tests / 195 class, zero failure/error/skip**, cộng năm JSON contract checks.
Billing/Pharmacy là selected suites; Report full regression cộng impacted rerun, không cộng
trùng lượt chạy. Source IDs/hashes/commands và map T02/T07–T19 ở
[runbook mục 8](huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).

Đóng H6-06.2/.4, H6-11.2/.3/.4, H6-12.1/.2: **46/48**. Chỉ H6-02.3/H6-12.3 còn mở vì
thiếu approved historical clone/broker inventory và rehearsal. Checklist duy nhất ở
[handoff chung](../handoffs/HANDOFF-HUY-MVP.md#10-integrated-backend-closeout--2026-10-17).
Không suy ra global M6, paid Lab hoặc frontend acceptance từ slice này.

### Hoàn tất hai task vận hành còn lại — 2026-10-17

Người dùng giao tiếp thực hiện H6-02.3/H6-12.3. Đã quan sát và cold-copy các volume Compose
có sẵn, không dùng DB tạo mới để thay bản inventory nguồn. Nguồn thực tế không có historical
business rows trong Pharmacy/Report/Notification; Rabbit có hai node identities cũ, mỗi node
vhost `/` và không có queues. Ghi missing legacy tables là ABSENT_IN_LEGACY_SCHEMA; không
coi query thiếu bảng là zero, không baseline/repair hoặc migrate Pharmacy để làm bài test đạt.

Actual standalone pair → Support → broker restart → legacy rollback → Support return trên
bản sao: bốn notification writes qua API còn nguyên bytes/IDs; Report rows và hai Flyway
histories giữ nguyên; một durable probe message giữ bytes qua restart. Chỉ một writer set
chạy mỗi thời điểm. Toàn bộ original volumes SHA-256 trước/sau trùng nhau, clone containers
đã dừng và clone volumes giữ lại. Guard từ chối nguồn đang có writer đã được kiểm tra.

`HistoricalSupportCutoverIT` **1/1 passed, zero failure/error/skip**, cộng selection trước:
**2.129 unique tests / 196 class**. Chốt **48/48** cho môi trường local đã quan sát. Đây không
là production rollout hoặc chứng nhận một lịch sử dữ liệu khác. Commands/hash/nguồn/phân loại
và giới hạn ở [runbook mục 9](huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).
Trạng thái 46/48 ở các batch trước giữ làm lịch sử; không còn Huy task phải bàn giao tiếp.
