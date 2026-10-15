# Vinh Clinical/Lab outpatient MVP source lock

**Status:** Partial implementation evidence; not full MVP acceptance. This file records the owned-service boundary and open work from M6-02/M6-09/M6-11/M6-14. The global plan remains open.

**Scope:** Production edits are limited to `backend/clinical-service` and `backend/lab-service`. The implementation profile is opt-in as `outpatient-mvp`; the default remains `compatibility-v0` so rollback and existing V0 regression behavior remain available.

## Chốt sổ Vinh — rà soát ngày 2026-10-15

Nguồn kiểm tra: HEAD `5411acfa56137b410459f98a49c8e680cde2c37b`, đã chứa PR #366/#370
và Core consolidation #368. Sau `git fetch origin`, `origin/master` là `cf9898ee`, chỉ thêm
dashboard commit activity/README; không có chênh lệch backend hoặc bộ thiết kế MVP.
Đây là source lock của lượt rà soát ban đầu; cập nhật upstream sau đó được ghi ngay bên dưới.
Không dùng số test lịch sử bên dưới để xác nhận bản hiện tại. Đây là evidence/gap register
của M6 plan, không backlog mới.

**Upstream refresh 2026-10-15:** `origin/master=3f07f216` đã chứa PR #373 của Lộc,
commits `867c2e38` và `8b64f90d`. Billing đã sửa generic EXAM fixture về `recordId=null`,
khai báo EXAM/LAB charge-consumer flags và bật các flags MVP Billing/Notification trong Compose.
Owner ghi nhận 940 Billing tests và 13 Surgery decoder tests đạt trong
`backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md`; đây là producer-side
evidence, chưa thay thế nghiệm thu Gateway/Clinical/Lab của V-CLOSE-03.

**Kết luận:** không còn lý do làm lại Lab order authority hay operation/fingerprint inbox.
Chưa được ghi toàn bộ backend Vinh DONE: V1 Rx attachment thiếu producer contract; real
Gateway/Billing/Lab slices và recovery liên service vẫn mở. Inpatient/Surgery không phải
điều kiện đóng Clinical/Lab ngoại trú. Có thể chuẩn bị FE theo contracts đã khóa; chỉ mở
flow V1 cho người dùng sau acceptance và cấu hình gates đồng bộ.

### Phần đã có source, không code lại

| M6 | Bằng chứng source hiện tại | Giới hạn kết luận |
|---|---|---|
| M6-05.1 | Clinical `application-outpatient-mvp.yml` retarget hai client tới Core, giữ JWT identity logic | Còn cần chạy lookup thật qua Core và signed delegation |
| M6-09.1–3 | Check-in, exact EXAM gate, assigned-doctor checks, completion ngoại trú; outbox và inbox transactional | Chưa chứng minh check-in → Billing payment → Gateway exam cùng runtime |
| M6-11.1–3 | Clinical persisted order + internal Lab-only lookup; Lab kiểm source trước transaction; request/outbox atomic; exact clearance; immutable resultVersion=1 | Chưa chứng minh toàn bộ request → Billing → result → Clinical/Report |
| M6-14 phần Clinical/Lab | `outpatient-mvp`, V0 mutation/deferred guards, Clinical HELD/quarantine và rollback fence | Không chứng minh profile toàn repo chỉ chạy sáu business apps |

### Vinh còn code/test gì để đóng phần mình

1. **V-CLOSE-01 — đã có standalone reliability evidence Clinical/Lab (M6-09.4/11.4/14.4).**
   Full PostgreSQL suites, actual Rabbit confirm/return, retry/DLQ và listener inbox/test-effect
   atomicity đã chạy. Clinical HELD/quarantine không release qua compatibility. Hai module
   còn có `OutboxProcessRecoveryIT`: kill packaged JVM sau publish, trước commit; start cùng
   JAR với PID mới, replay original delivery và không republish sau mark. Phạm vi/kết quả ở
   mục packaged JVM recovery bên dưới. Đây đóng gap outbox JVM restart từng được ghi mở;
   không chuyển business E2E hoặc consumer kill-after-effect-before-ACK của V-CLOSE-03 thành DONE.
2. **V-CLOSE-02 — implement Clinical V1 Rx attachment sau H-VINH-02 (M6-09/10).**
   Consumer hiện chủ động reject V1 envelope và quarantine V0 trong MVP. Sau producer lock:
   validate exact record/patient/department/OUTPATIENT_VISIT, operation theo dispense thật,
   claim/effect atomic; test identical replay/new event ID, conflicting bytes, wrong target
   và terminal state. Không bật V0 attachment thay V1 để coi đã xong.
3. **V-CLOSE-03 — chạy acceptance cùng owners sau H-VINH-01/03 (M6-09.4/11.4/15/16).**
   Packaged Core/Gateway/Clinical/Lab/Billing với PG/Rabbit isolated; check-in → EXAM payment
   → start exam và Clinical order → Lab request → LAB_TEST payment → result → Clinical.
   Chứng minh wrong-role/actor/target/expiry, duplicate/race/rollback, restart/DLQ và không
   gọi deferred services. Ghi source SHA, exact flags, test counts, source IDs/event hashes.
   Clinical flag `MEDIFLOW_CARE_FINANCE_V2_ENABLED`, Lab flag `MEDIFLOW_CARE_FINANCE_V2` và
   `MEDIFLOW_LAB_CLINICAL_CONTEXT_ENABLED` là ba keys khác nhau; không bật production ở lượt này.

Không giao cho Vinh làm full Inpatient, Surgery, admission settlement, full Report V2,
email/SMS, gom DB hoặc redesign frontend để đóng ba việc trên.

### H-VINH-01 — Hoàng Anh / Gateway và Core

- **Producer/consumer:** public client → Gateway → Clinical Lab-order command; Clinical → Core lookups.
- **Gap xác nhận:** `RouteAuthorizationFilter.RULES` thiếu exact
  `POST /api/v1/records/{id}/lab-orders`; route prefix records đã có. Giữ internal
  `GET /api/v1/lab-orders/{id}/context` không public. Không cần mở broad POST records.
- **Deliverable của Hoàng Anh:** exact ADMIN/DOCTOR allowlist và test wrong role/method,
  nested/extra path, human/refresh/service-token misuse; nghiệm thu Core lookup/auth parity
  với Clinical retarget. Profile Gateway không expose admission/surgery khi chạy MVP.
- **Fixtures/tests nguồn:** `ClinicalLabOrderControllerTest`, `ClinicalLabOrderContextControllerTest`,
  `contracts/clinical-lab-order-context-v1/valid.json`; Gateway `CareRouteAuthorizationTest`
  và `GatewayWebTest`. Reuse signed staff/assigned-doctor authority, không suy từ role.
- **Đóng khi:** command đi qua Gateway tới Clinical thành công với signed assigned doctor;
  các denial cases không forward; service-only context vẫn bị chặn qua public Gateway;
  lookup Core phân biệt absence/unavailable. Hoàng Anh cung cấp source/test evidence;
  Vinh chạy V-CLOSE-03. Chưa có owner acceptance trong lượt này.

### H-VINH-02 — Huy / Pharmacy + Report; Vinh nhận consumer Clinical

- **Producer/consumer:** Pharmacy `prescription.filled` V1 → Clinical; Clinical/Lab facts → Report.
- **Gap xác nhận:** `contracts/care-finance-v1/prescription.filled.v1.json` có `dispenseId`,
  thiếu `recordId` và là ADMISSION fixture; không thể dùng làm outpatient attachment.
  Flat V0 `PrescriptionFilledEvent` có `recordId` không chứng minh V1 contract đã có.
- **Deliverable của Huy:** khóa producer-owned outpatient relationship contract từ persisted
  prescription/context, bằng additive field có compatibility proof hoặc exact service lookup
  được hai owner thống nhất. Đây là quyết định còn mở, không mặc định chọn schema mới.
  Cung cấp golden OUTPATIENT_VISIT producer bytes với exact record/episode và dispense operation.
  Report xác nhận consume `medicalrecord.completed`/`lab.result.created` ngoại trú bằng cùng
  fixtures nguồn, không suy từ ADMISSION proposal.
- **Đóng khi:** producer/Clinical fixtures và tests cùng PR (hoặc hai nhánh vẫn gates OFF
  cho tới khi nghiệm thu chung), exact link/mismatch/replay/conflict đều đạt; Clinical attach
  đúng một lần theo dispense. Report evidence đóng phần M6-08 liên quan; không full Report V2.
  Vinh vẫn phải code V-CLOSE-02; handoff này không chuyển trách nhiệm consumer cho Huy.

### H-VINH-03 — Lộc / Billing; phối hợp slice với Vinh

- **Producer/consumer:** Clinical check-in/Lab request → Billing charge; Billing exact grant → Clinical/Lab.
- **Fixture gap resolved — PR #373:** `clearance-exam.json` đã được Lộc sửa thành
  appointment-only, `recordId=null`, đúng live `ExamChargeRepositoryAdapter` và Clinical XOR.
  Không còn giao lại việc refresh fixture cho Lộc.
- **Producer evidence nhận từ Lộc:** owner ghi nhận `ExamChargeRabbitIntegrationTest` và
  `LabTestChargeRabbitIntegrationTest` đạt với PG/Rabbit thật; runtime flags có trong Compose.
  **Phần phối hợp còn lại:** xác nhận đầy đủ catalog/price codes, selected-payment driving API
  và emitted EXAM/LAB_TEST source bytes cho V-CLOSE-03; cung cấp flags,
  Kiểm tra request tạo đúng một Lab charge và result không tạo charge V1 lần đầu.
- **Đóng khi:** V-CLOSE-03 chạy qua Gateway/real Billing với exact non-expired grants,
  duplicate không charge/grant/effect hai lần, wrong purpose/target/episode bị chặn.
  Lộc owns Billing/Notification; Vinh không sửa producer Billing để tự mở gate.

### Điều kiện chuyển trọng tâm frontend

Vinh có thể làm shared shell và appointment/record/Lab DTO/API/error/permission states theo
wire hiện tại trong lúc chờ owners; chưa gọi đó là V1 smoke đã đạt. Ưu tiên FE ngoại trú:
check-in → start exam → complete; create Clinical Lab order → create Lab từ exact sourceOrderId
→ start/results. Không đoán sourceOrderId ở browser, không gọi internal context bằng human token,
không fallback V0 trong MVP. Inpatient workboard cũ là lịch sử, không việc tiếp theo.

**Backend owner-closeout:** V-CLOSE-01/02 và Clinical/Lab assertions của V-CLOSE-03 đạt,
handoffs có owner evidence. **MVP toàn hệ thống:** vẫn theo M6-16/17/18; Support composition,
rollout/CI của cả nhóm không được đánh DONE bằng số test Clinical/Lab.

### Reliability tiếp theo — kịch bản và giới hạn bằng chứng

Các test mới dùng PostgreSQL `postgres:16-alpine` và RabbitMQ `rabbitmq:3.13-alpine`
riêng do Testcontainers tạo, không reset DB/queue ứng dụng. Không đổi production code,
wire fixtures, bindings, flags mặc định hoặc producer của owner khác.

| Test | Kịch bản và assertions |
|---|---|
| `ClinicalOutboxRabbitIntegrationTest` | Giữ ba cases confirm/return, HELD, quarantine; thêm publish đã confirm rồi rollback DB, retry exact original bytes từ pending row; Rabbit `stop_app`/`start_app` giữ message persistent và gửi tiếp row commit khi broker đang tắt |
| `LabOutboxRabbitIntegrationTest` | Mandatory return không đánh published; dispatcher mới retry cùng messageId/correlation/payload; rollback sau confirm cho phép replay; Rabbit application restart giữ message đã confirm và resume pending row |
| `LabListenerRabbitIntegrationTest` | Production RabbitConfig listener factory/interceptor/DLX; transient failure đúng ba attempts, claim/effect rollback rồi commit; retry cạn vào DLQ giữ bytes/x-death; duplicate sau listener restart chỉ một effect; changed bytes conflict, malformed JSON và MVP payment quarantine vào DLQ trước effect |

Listener test dùng production `LabQueueConsumer`, fingerprint factory, transactional proxy
`ConsumerInboxBoundary` và PostgreSQL inbox adapter. Application port cho medical-record
được thay bằng SQL test effect để quan sát transaction và inject transient failure sau write.
Đây không phải E2E projection hoặc bằng chứng Clinical đã nhận kết quả Lab. Các port payment/
clearance fail nếu bị gọi bất ngờ; không thay producer contract để tạo success giả.

`rabbitmqctl stop_app/start_app` restart broker application trong cùng container/Erlang VM;
không phải stop/start Docker container, VM hoặc kill JVM service. Rollback sau confirm mô phỏng
cửa sổ publish thành công nhưng DB chưa commit, không được mô tả như đã kill tiến trình thật.
Packaged JVM/service restart và V-CLOSE-03 vẫn mở.

Chạy lại đầy đủ trong Docker-ready environment:

```powershell
mvn -o -pl backend/clinical-service -am test
mvn -o -pl backend/lab-service -am test
```

Giữ source lock/flags và acceptance fixtures của H-VINH-01/02/03. Khi owners giao đủ,
chạy hai slice riêng: (1) Core identity → Gateway check-in → Billing exact EXAM grant →
assigned doctor start exam; (2) Clinical order → exact sourceOrderId Lab request → Billing
LAB_TEST grant → Lab result → Clinical attachment. Mỗi slice cần positive, wrong role/target/
episode/expiry, duplicate/rollback và unavailable denial; ghi actual IDs/hashes từ isolated
run, không bịa IDs/golden payload mới từ thiết kế. Chưa có runtime evidence cho hai slice.

### Packaged JVM recovery — 2026-10-15, sau lượt 15:30

Hai module có profile Maven opt-in `process-recovery`; `mvn test` thông thường vẫn giữ
310 Clinical + 216 Lab, không tự launch JAR. Profile dùng Failsafe sau Boot repackage và
chạy `OutboxProcessRecoveryIT` trong từng module, không sửa root/shared build.

```powershell
mvn -pl backend/clinical-service,backend/lab-service -am -Pprocess-recovery verify
```

Test khởi động actual `java -jar` với `outpatient-mvp`, random HTTP port, discovery disabled,
V1 financial gates OFF, isolated PostgreSQL/RabbitMQ và test-only JWT secret. Seed một outbox
row bằng fixture hiện có (`appointment.status.changed.v1.json` của Clinical,
`lab.request.created.v1.json` của Lab); không claim đã chạy API tạo aggregate hoặc Billing.

Test-only trigger trong disposable PG chờ advisory lock khi actual dispatcher update
`published_at`. Sau khi nhận message và quan sát transaction chờ lock, test kill cưỡng bức
child JVM, kiểm row vẫn pending, release lock rồi chạy lại cùng JAR với PID khác. Tiến trình
mới phải startup đầy đủ, replay đúng bytes của lần đầu, commit published marker và không
republish trong các lần poll sau. Clinical còn assert exact original fixture bytes; Lab
assert JSON semantics của fixture rồi exact equality giữa hai deliveries do JSONB normalize.
Các tiến trình con luôn được dọn, failed-run logs giữ trong temporary directory, không commit
logs/credentials. Trigger không có trong production migrations hoặc JAR.

Phạm vi này bổ sung đúng khoảng trống JVM crash của outbox; broker/listener/PG race evidence
vẫn từ suites đã có. Không chứng minh integrated business E2E, host/container/VM crash,
consumer JVM kill-after-effect-before-ACK, backup/restore hoặc six-service rollout.

**Verification:** full `-Pprocess-recovery verify` kết thúc 2026-10-15 15:41:45 +07:00,
BUILD SUCCESS exit 0: Surefire Clinical 310 + Lab 216; Failsafe mỗi module 1 process test.
Tổng **528 distinct tests đạt, 0 failures/errors/skips**. Sau chỉnh cuối về startup-log assertion
và cleanup, đã recompile test và chạy lại riêng Failsafe cả hai module, kết thúc 15:43:57
+07:00, BUILD SUCCESS exit 0, 2/2 process tests đạt, 0 skips; không cộng lượt rerun vào 528.

Lệnh rerun riêng IT sau package (không thay JAR):

```powershell
mvn -o -pl backend/clinical-service,backend/lab-service -am -Pprocess-recovery test-compile org.apache.maven.plugins:maven-failsafe-plugin:3.2.5:integration-test org.apache.maven.plugins:maven-failsafe-plugin:3.2.5:verify
```

Source HEAD `5411acfa` + owned working-tree test/POM changes, chưa commit. JAR SHA-256:

| Artifact | SHA-256 |
|---|---|
| `clinical-service-0.0.1-SNAPSHOT.jar` | `C74FEC25548F8F24C9201BFA3AEBAC2DFC210BDDFEBDD8FAE09ECCE876C96B8D` |
| `lab-service-0.0.1-SNAPSHOT.jar` | `3322E31B6A0BA2EAB882D6107BFC403D99FA604690D4FE3376096ACDD6C5CAB5` |

Môi trường giữ Java 21.0.11 / Maven 3.9.16 / Docker 29.8.0 / PostgreSQL 16.15 /
RabbitMQ `3.13-alpine`. Logs local `%TEMP%/vinh-process-recovery.log` (full) và
`%TEMP%/vinh-process-recovery-final.log` (final IT); module reports ở
`target/surefire-reports` và `target/failsafe-reports`. Review độc lập không còn actionable
finding. Hai bài IT không mock dispatcher, repository, transaction hoặc Rabbit publisher.

**Công việc backend còn mở:** V-CLOSE-02 sau H-VINH-02 và V-CLOSE-03 sau H-VINH-01/03.
Không giao lại outbox JVM restart như một gap chưa có evidence. Handoffs chưa được owners
accept chỉ vì test của Vinh xanh; frontend preparation có thể tiếp tục theo workboard.

### Verification trước — 2026-10-15, 15:30 +07:00

Full suites chạy lại sau sửa assertions; không exclude/skip. Source là HEAD `5411acfa`
cộng working-tree test changes, chưa commit. Môi trường vẫn Java 21.0.11, Maven 3.9.16,
Docker Engine 29.8.0, PostgreSQL 16.15 và RabbitMQ image `3.13-alpine`.

| Full run cuối | Tests | Passed | Failures/errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Clinical (`mvn -o -pl backend/clinical-service -am test`), kết thúc 15:30:52 | 310 | 310 | 0 / 0 | 0 | 0 |
| Lab (`mvn -o -pl backend/lab-service -am test`), kết thúc 15:30:03 | 216 | 216 | 0 / 0 | 0 | 0 |
| Tổng | 526 | 526 | 0 / 0 | 0 | — |

Tăng chín cases so với lượt 14:19: Clinical outbox từ 3 lên 5; Lab thêm outbox 3 và
listener 4. Các suites PG/migration/race/atomicity cũ cũng chạy lại, không lấy kết quả lịch sử.
Review độc lập không còn actionable finding sau khi bỏ assertion Clinical AMQP `messageId`
không tồn tại trong implementation. Clinical kiểm original bytes và persisted row; Lab kiểm
messageId/correlation đúng publisher đang có. Không đổi production chỉ để test xanh.

Lượt thử đầu khi Docker còn khởi động bị lỗi container setup (2 errors, không skip).
Lượt full Clinical đầu chạy bản test đã compile trước sửa review: 310 tests, 2 failures do
assert `messageId=null`; đã bỏ hai assertions không đúng contract, full cuối đạt như bảng.
Raw logs cuối: `%TEMP%/vinh-clinical-reliability-final.log`,
`%TEMP%/vinh-lab-reliability-final.log`; XML từng module `target/surefire-reports`.
Logs là generated local artifacts, không commit thông tin môi trường/credentials test.

**Khép thêm:** Lab actual broker listener/publisher, bounded retry/DLQ, inbox/test-effect
atomicity qua listener, broker application recovery và publish-confirm/DB-rollback windows
của Clinical/Lab. **Còn mở của V-CLOSE-01:** kill/restart packaged JVM/service trong isolated
runtime; không gọi listener/broker application restart là process-crash acceptance.
V-CLOSE-02 vẫn chờ Huy producer lock; V-CLOSE-03 vẫn chờ Hoàng Anh/Lộc và runtime liên service.
Frontend preparation có thể bắt đầu theo workboard, không cần đợi tất cả handoffs hoàn tất.

### Verification trước — 2026-10-15, 14:17–14:19 +07:00 (lịch sử, được bổ sung ở trên)

Áp dụng `verification-before-completion`: full suites chạy sau sửa test, không exclude,
không skip, không đổi gates production. Java 21.0.11, Maven 3.9.16, Docker Engine 29.8.0;
PostgreSQL `postgres:16-alpine` (server 16.15), RabbitMQ `rabbitmq:3.13-alpine`.
Container tests dùng stores/broker isolated do Testcontainers tạo, không dùng/xóa DB ứng dụng.

| Full run cuối | Tests | Passed | Failures/errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Clinical (`mvn -o -pl backend/clinical-service -am test`) | 308 | 308 | 0 / 0 | 0 | 0 |
| Lab (`mvn -o -pl backend/lab-service -am test`) | 209 | 209 | 0 / 0 | 0 | 0 |
| Tổng | 517 | 517 | 0 / 0 | 0 | — |

Clinical gồm `ClinicalIntegrationTransactionTest` (1), `ClinicalCareFinancePersistenceTest`
(5), `ClinicalPersistenceAdapterTest` (6), `PrescriptionContextPostgresTest` (4),
`ConsumerInboxPostgresTest` (5) và test PG/Rabbit mới `ClinicalOutboxRabbitIntegrationTest`
(3). Lab gồm `LabPersistenceAdapterTest` (11) và `ConsumerInboxPostgresTest` (5).
Inbox suites kiểm fresh/upgrade migrations, raw evidence/dedupe/conflicts, concurrent same-operation
và rollback; Clinical integration test kiểm claim/effect qua transactional proxy.

Lượt full đầu chạy Clinical 305/305 và Lab 208/209, không skips: Lab có một assertion ngẫu nhiên
do test so list UUID theo input order trong khi consumer canonical-sort. Đã thay input random
bằng hai IDs cố định đảo thứ tự và assert exact canonical output, không đổi production behavior.
Lượt PG/Rabbit mới đầu có một assertion dùng nhầm outgoing `deliveryMode` thay vì
`receivedDeliveryMode` của Spring AMQP; đã sửa test và full Clinical cuối đạt 308/308.

Raw logs lần chạy cuối: `%TEMP%/vinh-clinical-closeout-final.log` và
`%TEMP%/vinh-lab-closeout-final.log`; JUnit XML tại từng module `target/surefire-reports`.
Chúng là local generated evidence, có thể mất khi clean; các counts/environment/commands
ở đây là bản ghi bền trong repo. Build/test source của lượt này là HEAD nêu trên cộng hai
test-file changes trong working tree, chưa có commit SHA riêng.

**Đã khép trong lượt này:** môi trường Docker; full PG regression không exclusions/skips;
flaky Lab assertion; Clinical real outbox confirm/return/retention proof.
**Chưa khép:** Lab real broker listener/publisher recovery, full JVM/broker restart/crash/DLQ,
Gateway/Billing/Report flows, V1 Rx attachment và packaged six-service acceptance.
Không lấy 517 passing tests làm bằng chứng các phần này đã đạt. M6 tasks giữ mở đúng scope.

## Follow-up source lock (2026-10-15; PR #370 baseline)

The follow-up branch `fix/vinh-lab-authority-inbox` supersedes the earlier missing-source-order and event-ID-only implementation notes below. Final verification evidence for this branch is recorded separately; the 2026-10-14 test counts are historical, not evidence for these changes.

Clinical now persists an immutable, server-generated Lab order through `POST /api/v1/records/{id}/lab-orders` (ADMIN/DOCTOR). Creation requires both effective outpatient MVP mode and the Clinical care-finance gate, an OPEN record, its matching V1 appointment in IN_EXAM, and the signed assigned eligible doctor. Patient, department and outpatient episode come from Clinical's stored examination. `labType`, `priceCode` and `requestedDate` are explicit order fields; a future scheduled requested date is permitted by the existing Lab contract.

`GET /api/v1/lab-orders/{sourceOrderId}/context` is an internal Lab-only lookup. It accepts only the exact GET path, a short-lived signed `lab-service` SYSTEM service token, and signed `staffId`/`originalActorRole` delegation matching the stored requesting doctor. It does not broaden Pharmacy's separate prescription-context authority. Confirmed absence is a successful `exists=false` response; forbidden actors are denied, and upstream errors or malformed responses are unavailable rather than absence.

Lab's V1 create verifies every order/request identity, signed actor, episode, test type, price code and requested date before starting its local write transaction. The local test and request outbox remain atomic. The lookup requires `mediflow.lab.clinical-context.enabled=true` (`MEDIFLOW_LAB_CLINICAL_CONTEXT_ENABLED`), as well as Lab's existing care-finance gate; both remain opt-in. Missing authority has no compatibility fallback. The Clinical/Lab `contracts/clinical-lab-order-context-v1/valid.json` fixtures have identical SHA-256 `B1ECB5A8DC167BA934716728119BB324649DD7EFD1B0EB80A630E9AE2C659B50`.

Append-only Clinical V6 stores orders; Clinical V7 and Lab V3 add delivery/operation receipts. Receipts retain original body bytes and SHA-256, plus canonical business bytes and SHA-256. Event identity is unique across handlers within each module. An identical event is a no-op; the same operation under a new event ID is a no-op only when business evidence agrees. Reused IDs or operation keys with changed evidence are conflicts. Claim, effect and any resulting outbox writes share one transaction; failures roll them back together. Legacy effects without verifiable evidence fail closed rather than acquiring invented fingerprints. These database guarantees still require real PostgreSQL acceptance.

Clinical V1 Lab results require `resultVersion=1` and the exact record/patient/department/outpatient episode before attachment. Amendments are outside this MVP. Effective outpatient MVP mode quarantines flat V0 Lab/Rx facts before receipt or effect, while their compatibility paths remain available outside MVP. Permanent conflicts/deferred evidence go to the existing durable DLQs; transient database/broker failures have bounded retry.

### Dependencies still open

- **Hoang Anh / Gateway:** add exactly `POST /api/v1/records/{id}/lab-orders` for ADMIN/DOCTOR to the Gateway allowlist, with wrong-role, wrong-method and extra/nested-path denial tests. Keep the internal `/api/v1/lab-orders/{id}/context` lookup unavailable to human/public Gateway callers. Clinical's downstream signed assigned-doctor checks remain mandatory. The current allowlist does not yet expose the new driving API.
- **Huy / Pharmacy and Vinh / Clinical:** the locked V1 `prescription.filled` contract uses `dispenseId` and does not provide Clinical's required `recordId`. Agree a producer-owned exact relationship contract and same-PR fixtures/tests before enabling V1 attachment. Clinical's flat V0 attachment path must not guess a record or treat a prescription ID as a V1 dispense operation.
- **Vinh with Billing/Gateway owners:** run real PostgreSQL/RabbitMQ migration, race, rollback, restart, DLQ and Gateway flow acceptance. Core consolidation is now present on remote master; its presence does not prove the cross-service slice has passed. No Core/Gateway production files are changed by this branch.

Rollback to a pre-V7 Clinical or pre-V3 Lab binary must keep its old consumer listeners disabled after the new receipts have accepted writes. Those binaries do not understand operation/raw-body evidence. Preserve orders, receipts, outbox and DLQ contents for forward recovery; do not reset markers or delete evidence to make replay succeed.

### Follow-up verification evidence (historical; superseded by full runs above)

Fresh verification on 2026-10-15 used Java 21.0.11, Maven 3.9.16 and the existing offline dependency cache. The branch is based on `334df99d`; remote master is nine commits ahead with Core/Gateway and frontend changes, but no Clinical/Lab source differences. Verification here does not claim a merged six-service runtime test.

| Latest run | Tests | Passed | Failed/errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Clinical selected suite | 301 | 284 | 0 / 0 | 17 | Clinical reactor step SUCCESS; excludes only `PrescriptionContextPostgresTest`. |
| Lab full suite | 209 | 193 | 0 / 0 | 16 | Exit 0, BUILD SUCCESS; no Lab test exclusions. |
| Total selected tests | 510 | 477 | 0 / 0 | 33 | Skipped container tests are not passing evidence. |

The initial unrestricted owned-module run stopped in Clinical with a Docker initialization error, one stale command assertion and three controller-test context errors; Lab was not reached. The test-harness errors were corrected and the latest selected Clinical and full Lab runs above passed. `PrescriptionContextPostgresTest` still requires a running Docker engine and is excluded only from the selected Clinical run, not counted as passed or skipped there. The new five-test PostgreSQL inbox class in each module is included in the 33 skips.

Commands (add `-o -Dmaven.repo.local=<existing-cache>` only when using the offline cache):

```text
mvn -pl backend/clinical-service,backend/lab-service -am -Dtest=*,!PrescriptionContextPostgresTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl backend/lab-service -am test
mvn -pl backend/clinical-service,backend/lab-service -am -DskipTests package
```

The first command reached Clinical SUCCESS before a now-fixed Lab test-compile issue. Clinical production code did not change afterward. The second command is the fresh successful Lab evidence. Packaging compiles both modules and their final test sources; it does not execute container acceptance. Sol 5.6 high completed static review; its last `labTestIds` canonical-order finding is fixed and covered by the passing Clinical factory test. No full MVP or PostgreSQL/RabbitMQ/Gateway acceptance is claimed.

## Initial PR #366 baseline (historical)

The remaining sections describe the first implementation at `334df99d`. The follow-up source lock above replaces its missing-authority and event-ID-only notes; its original test evidence is retained for traceability.

## Locked source fixtures

The following Clinical-owned fixtures are the checked-in golden inputs for this slice. Their SHA-256 values make source changes reviewable; refresh them only from a producer acceptance test and update the owning consumer test in the same change.

| Fixture | SHA-256 | Locked facts |
|---|---|---|
| `backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json` | `34BE41CD12BD6FE38E18C89F0828097350405D077F05FA853E828F50578715E7` | Billing EXAM trigger: appointment ID is the episode and source; `recordId` is null; transition is `PENDING` to `AWAITING_PAYMENT`. |
| `backend/clinical-service/src/test/resources/contracts/financial.clearance.granted.v1.json` | `3876A219BEB33B00A0BAC57AE41688E80B969BA08E5E2B880C26F723AE754713` | Accepted live EXAM clearance targets exactly one appointment; `recordId` is null and episode ID equals appointment ID. |
| `backend/clinical-service/src/test/resources/contracts/prescription-context-v1/appointment.json` | `CB9385BC7F582A87C7576944A64CF99CFE379E5F384358C42EF215070B7D28C7` | Existing exact appointment-backed stored record relationship response; no symptoms or diagnosis details. |
| `backend/clinical-service/src/test/resources/contracts/prescription-context-v1/walk-in.json` | `AB97DB118BC8ECF7BC6360B541A6469FB77F1099479B9C8E32DA76E693438D5C` | Existing exact walk-in record relationship response; episode ID is the record ID. |

The generic Billing fixture `backend/billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json` previously contained both `appointmentId` and `recordId`. Lộc resolved that discrepancy in PR #373 (`8b64f90d`): `recordId=null` now matches the appointment-only live producer. Keep Clinical's exactly-one-target check unchanged. Integrated Clinical/Lab acceptance remains open separately from this resolved fixture item.

## Runtime switches and current guard behavior

| Mode | Clinical | Lab |
|---|---|---|
| Default | `compatibility-v0`; `mediflow.features.outpatient-mvp.enabled=false`; V2 retains the pre-existing opt-in flag behavior. | `compatibility-v0`; `mediflow.features.outpatient-mvp.enabled=false`; legacy behavior remains available. |
| `outpatient-mvp` profile | V0 medical-record create is rejected with `CLINICAL_V0_WRITE_DISABLED`; V0 appointment status changes are rejected with `CLINICAL_V0_STATUS_WRITE_DISABLED` except cancellation. Active V1 diagnosis edits require the authenticated assigned doctor and matching authoritative department. Start/complete require the same authority. The admission-referral route and service gate are disabled by the effective outpatient-mode condition; admission/transfer completion returns `CLINICAL_ADMISSION_DEFERRED`. | V0 create/lifecycle mutations are rejected with `LAB_V0_MUTATION_DISABLED`; historical V1 Admission rows are also barred from outpatient mutation. `payment.completed` is rejected without requeue so the configured durable `lab.dlq` retains evidence. Admission clearances are rejected with `LAB_ADMISSION_DEFERRED`. Cancellation requires signed staff identity matching `cancelledBy`; spoof/missing identity returns `LAB_ACTOR_MISMATCH`. |

The effective outpatient mode is `mediflow.features.outpatient-mvp.enabled=true` **or** the active `outpatient-mvp` Spring profile; a false property cannot turn off a selected profile. `MEDIFLOW_OUTPATIENT_MVP_ENABLED` selects the property mode. Both V1 care-finance gates default off in the MVP profile until the complete consumer/producer acceptance runs. A caller-supplied Lab `sourceOrderId` is not treated as proof of a Clinical order. Outpatient MVP Lab creation fails closed with `LAB_SOURCE_ORDER_UNVERIFIED` whenever V1 create is configured, until Clinical owns and persists an exact order identity and the request can be checked against it. No `recordId`, appointment ID, or caller field is substituted for that missing authority. No Lab-specific Clinical context endpoint was added because it could only confirm record relationships and could not verify the required source order.

| HTTP operation | Role gate | Outpatient-MVP contract |
|---|---|---|
| Clinical `PUT /api/v1/appointments/{id}/check-in` | `ADMIN`, `NURSE` | Available only with the Clinical V1 gate. |
| Clinical `PUT /api/v1/appointments/{id}/start-exam` | `ADMIN`, `DOCTOR` | Requires signed actor staff ID equal to the assigned doctor; authority errors are `CLINICAL_STAFF_IDENTITY_REQUIRED`, `CLINICAL_DOCTOR_NOT_ASSIGNED`, `CLINICAL_DOCTOR_NOT_ELIGIBLE`, or `CLINICAL_DOCTOR_WRONG_DEPARTMENT`. |
| Clinical `PUT /api/v1/records/{id}/complete` | `ADMIN`, `DOCTOR` | Same assigned-doctor check; admission/transfer returns `CLINICAL_ADMISSION_DEFERRED`. |
| Clinical `POST /api/v1/records/{id}/admission-referrals` | `ADMIN`, `DOCTOR` | Route is absent in outpatient mode even if both feature properties are overridden true. |
| Lab `POST /api/v1/lab` | `ADMIN`, `DOCTOR` | V0 is blocked; V1 remains gated and then returns `LAB_SOURCE_ORDER_UNVERIFIED` until an exact order authority exists. |
| Lab `PUT /api/v1/lab/{id}/start` and `/results` | `ADMIN`, `LAB_TECH` | V0 mutations are blocked; V1 also needs its care-finance gate and outpatient episode. |
| Lab `PUT /api/v1/lab/{id}/cancel` | `ADMIN`, `DOCTOR`, `LAB_TECH` | V1 audit identity must match signed staff; mismatch is `LAB_ACTOR_MISMATCH`. |
| Lab `PUT /api/v1/lab/{id}/status` | `ADMIN`, `LAB_TECH` | V0 mutation is blocked; V1 requires its care-finance gate. |

The primary profile keys are `mediflow.features.care-finance-v2.enabled=false` and `mediflow.features.admission-referral.enabled=false` for Clinical, and `mediflow.features.care-finance-v2=false` for Lab. The compatibility configuration preserves the legacy admission-referral default derived from the old Care-Finance gate.

The Clinical Core clients use profile-configured target names `core-service` while retaining the logical JWT identities and compatibility defaults `organization-service`/`patient-service` outside the profile.

## Explicit open owner tasks at PR #366 (historical; superseded above)

- **Vinh — Lab order authority (M6-11):** define and persist the exact Clinical Lab-order identity and producer contract; then add a bounded Lab-to-Clinical relationship lookup and reject every mismatched record/patient/doctor/department/episode/order combination. Keep Lab V1 create disabled until its producer and consumer fixtures agree. Do not derive order identity from a record or appointment.
- **Vinh - durable Clinical acceptance (M6-09/M6-14):** the four existing flat Clinical facts now enqueue original converter bytes in the outbox when durable publication or effective outpatient MVP mode is enabled. Real PostgreSQL/RabbitMQ migration, atomicity and restart acceptance remains open; local serializer/dispatcher tests do not close this task.
- **Vinh — operation plus fingerprint idempotency (M6-09/M6-11):** current inbox claims are event-ID based. Add scoped operation/fingerprint conflict handling for Clinical and Lab consumers only with append-only migrations and tests for duplicate-identical versus duplicate-conflicting bytes.
- **Lộc — fixture ownership RESOLVED in PR #373:** generic `clearance-exam.json` now matches appointment-only EXAM producer behavior; Clinical's XOR acceptance invariant stays unchanged. V-CLOSE-03 integrated acceptance remains open.
- **Cross-owner acceptance:** run real PostgreSQL/RabbitMQ proof for check-in to payment to exam start/completion and Lab request to result, including DLQ/restart. Unit fixtures do not satisfy M6-09, M6-11, M6-14, or M6-16.

## Durable publication and rollback fence

`appointment.created`, `appointment.status.changed`, `medicalrecord.created` and `diagnosis.added` retain their existing flat body shape, event IDs, correlation IDs and converter date/time representation. Effective MVP mode forces durable publication and activates its single scheduler even if the independent care-finance or durable-event flags are false. Outside MVP, durable publication is opt-in and the compatibility default retains its original after-commit publisher.

Append-only migration V5 adds original payload bytes and HELD/quarantine evidence without rewriting V4 payloads. The dispatcher preserves original bytes across retries, bounds failed publication attempts and holds deferred admission/transfer facts before selecting an active page. HELD and quarantined rows remain excluded when switching profiles; a profile switch cannot release them.

Rollback to the current artifact's compatibility mode preserves these guards. A pre-V5 binary does not understand the new HELD/quarantine columns: stop and drain the current host, disable the older binary's care-finance/outbox scheduler, and preserve all V5 rows for forward recovery. Never run an old enabled dispatcher against HELD/quarantined rows, reset their markers, or drop the new columns to force rollback.

## Test evidence

Fresh post-edit verification on 2026-10-14 (Java 21, Maven offline using the existing dependency cache):

| Run | Clinical | Lab | Result |
|---|---|---|---|
| Full owned-module `test` | 266 tests, 0 failures, 1 environment error, 13 skipped | Not reached because the reactor stopped | Docker is unavailable; `PrescriptionContextPostgresTest` cannot initialize `postgres:16-alpine`. |
| `test` excluding only `PrescriptionContextPostgresTest` | 265 tests, 0 failures/errors, 13 skipped | 180 tests, 0 failures/errors, 11 skipped | Exit 0; 421 passed and 24 skipped across both modules. |
| Owned-module `-DskipTests package` | Packaged | Packaged | Exit 0; compilation and packaging only, not runtime acceptance. |

Commands target `-pl backend/clinical-service,backend/lab-service -am`. The filtered run uses `-Dtest=*,!PrescriptionContextPostgresTest -Dsurefire.failIfNoSpecifiedTests=false`; it does not change test annotations or disable application guards. Other Docker-dependent tests skip themselves without an available engine. Skipped or excluded tests are not passing evidence. V5 PostgreSQL execution, RabbitMQ confirmation/restart/DLQ proof, Core/Gateway integration and the Lab order-authority happy path remain unverified; the global M6 tasks remain open.
