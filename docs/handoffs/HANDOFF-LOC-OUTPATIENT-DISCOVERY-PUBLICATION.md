# Lộc — implementation handoff: discovery và publication V1 ngoại trú

Ngày giao: **2026-10-16**. Refresh source: **`c8024921`**.
**Trạng thái: L1/L2 implementation đã merge; L3/L4 và G1 còn mở.**
Không làm lại discovery/cohort/writer. Checklist chưa tick là acceptance chưa được xác nhận,
không có nghĩa code của toàn mục chưa tồn tại.

## Current delivery — Hoàng Anh và Lộc nhận đúng phần còn lại

| Owner | Đã giao | Công việc tiếp theo |
|---|---|---|
| Lộc | L1 `9b89a355`; L2 `1624cbd3`, V22 cohort/V23 payload fence, writer và PG tests | L3 actual Rabbit/dispatcher + packaged recovery; L4 full regression và evidence/fixtures. Hoàn thiện các acceptance gaps L1/L2 nếu full verification phát hiện |
| Hoàng Anh | Public Lab-order và Core migration paths tại `0a8636cd` | G1 exact GET discovery ADMIN/CASHIER, query/correlation forwarding và deny tests; actual Billing smoke |
| Vinh | Rx V1 consumer + public Lab-order acceptance | V-CLOSE-03 actual paid EXAM/LAB_TEST và paid Rx sau producer/Gateway evidence |

Fresh targeted rerun 2026-10-16: **61 Billing tests, 0 failures/errors/skips**, Java 21,
real PostgreSQL; discovery 27, publication/migration 34. Gateway care matrix/Lab-order
**183 tests, 0 failures/errors/skips**. Hai commands exit 0, không thay full Billing/Gateway
regression hoặc actual publication. Logs và reproduce ở [Vinh closeout](HANDOFF-VINH-OUTPATIENT-MVP.md#verification-refresh--2026-10-16).

Chưa có `OutpatientPublicationRabbitIntegrationTest`, `OutpatientPublicationProcessRecoveryIT`
hay profile `outpatient-publication-acceptance` trên source refresh. Test tên
`actualIssuanceAndPaymentPublishExactOriginalEvents` kiểm eligible DB rows, chưa đọc broker.
Gateway allowlist chưa có exact GET by-source, nên chưa drive public paid slice được.
Các gates deployment giữ nguyên; handoff này không là rollout approval.

**Goal còn lại:** hoàn thiện actual publication/recovery và evidence của discovery/cohort đã merge,
để Vinh chạy V-CLOSE-03 bằng actual Billing payment/grant qua HTTP/Rabbit.

**Architecture:** Giữ Billing là money authority, đọc duy nhất Billing store; dùng application
ports và infrastructure adapters hiện có. Tái sử dụng `BILLING_EVENT_OUTBOX` và dispatcher có
confirm/retry/lease; cohort được ghi lúc issue request mới, không release historical HELD.

**Tech stack:** Java 21, Spring Boot/Maven, PostgreSQL/Flyway, RabbitMQ/Testcontainers hiện có.
**Spec:** [contracts](../plans/03-contracts.md), [reliability](../plans/04-data-reliability.md),
[M6 plan](../plans/05-implementation-plan.md), [Vinh closeout](HANDOFF-VINH-OUTPATIENT-MVP.md),
[Huy handoff](HANDOFF-HUY-MVP.md). Đây là kế hoạch triển khai cho gap đã xác nhận, không backlog mới.

Lộc tiếp tục checklist còn lại L3/L4, **không cần viết thêm proposal/design/implementation plan**.
Các lựa chọn API/cohort/flags đã chốt trong handoff này cho phần việc được giao. Đọc nearest
AGENTS và canonical docs trước code; nếu source upstream thay đổi, ghi delta thật, không tự
coi API/event ở tài liệu này là live. Không tự push/merge hoặc bật deployment vì checklist local đạt.

## 1. Ownership và thứ tự giao

| Owner | Deliverable | Phạm vi |
|---|---|---|
| Lộc | Task L1–L4; Billing API/DB/publication/recovery evidence | `backend/billing-service/**`, cập nhật evidence trong handoff này |
| Hoàng Anh | Task G1 exact Gateway rule/test sau L1 | `backend/gateway/**`; Lộc không sửa thay |
| Vinh | V-CLOSE-02 trước; public Lab-order acceptance tiếp; V-CLOSE-03 sau L4/G1 | Clinical/Lab; không sửa Billing để bypass producer gate |
| Huy + Lộc | Rx/Report/Notification nhận original bytes trước activation tương ứng | Theo ownership hiện có; consumer chưa đạt thì cohort của purpose đó giữ OFF |

Không giao Lộc Support composition, full refunds/settlement, Surgery/Inpatient, shared POM/CI/Compose
hoặc frontend trong handoff này. Rx discovery/issuer publication được đưa vào cùng boundary để
đóng đúng dependency Billing đã nêu trong Huy handoff; terminal/refund acceptance còn theo file Huy.

## 2. Source gap tại baseline `71e2684d` — historical rationale

Các bullet dưới mô tả trước L1/L2, không dùng làm current owner queue. Refresh ở đầu file.

- `web/LedgerPaymentController.java` chỉ có `POST /api/v1/billing/payment-requests/{id}/payments`.
  EXAM/LAB_TEST/PRESCRIPTION issuers sinh request ID, chưa có HTTP discovery từ exact source.
- `application/service/LedgerPaymentService.java` gọi `LedgerEventPort.appendHeld` cho cả
  `payment.completed` và `financial.clearance.granted`. `PrescriptionChargeService` cũng giữ
  `invoice.created` V1. `infrastructure/messaging/LedgerEventOutboxAdapter.java` luôn insert
  `publication_enabled=FALSE, contract_version=1`.
- Migration `V6__ledger_event_delivery_fence.sql` cấm publish mọi contract version khác 0.
  Bật ledger/charge-consumer flags trong Compose không thay đổi constraint hoặc writer này.
- `ExamChargeRabbitIntegrationTest` và `LabTestChargeRabbitIntegrationTest` lấy request ID bằng
  SQL, gọi payment use case, rồi assert grant JSON trong outbox; dispatcher bị tắt ở hai test.
  Chúng chứng minh charge/payment/persisted grant phía Billing, **chưa chứng minh grant được
  dispatcher phát qua Rabbit**. Giữ tests, bổ sung proof; không đổi tên evidence thành paid E2E.

## 3. Contract L1 — discovery được chọn để triển khai

**Endpoint:** `GET /api/v1/billing/payment-requests/by-source`.
**Roles:** ADMIN, CASHIER; chỉ signed human access token, không SYSTEM/service/refresh/PATIENT.
**Gate:** `mediflow.billing.ledger.enabled=true` và
`mediflow.billing.payment-request-discovery.enabled=true` (flag mới, default false).

Query bắt buộc, mỗi key đúng một giá trị:

| Key | Type / nghĩa |
|---|---|
| `purpose` | Enum chính xác `EXAM`, `LAB_TEST`, `PRESCRIPTION`; không nhận deferred purposes |
| `sourceId` | UUID: appointmentId cho EXAM; **labId** cho LAB_TEST; prescriptionId cho PRESCRIPTION |
| `patientId` | UUID expectation phải khớp persisted Billing account; không là credential |
| `careEpisodeId` | UUID exact outpatient visit expectation; không suy từ recordId/patientId |

Không nhận accountId tùy ý để chọn khoản của account khác. Các query expectations chỉ ràng buộc
exact result; quyền đến từ signed ADMIN/CASHIER. Endpoint này không phải patient self-service.
LAB_TEST không nhận Clinical `sourceOrderId` thay cho labId.

HTTP 200: `ApiResponse<PaymentRequestDiscoveryDTO>`, giữ correlation theo convention hiện tại.
DTO record mới có **đúng** các field sau (wire English camelCase):

| Fields | Java type / nullability |
|---|---|
| `paymentRequestId`, `invoiceId`, `accountId`, `patientId`, `departmentId` | UUID, non-null, từ persisted relationship |
| `purpose`, `sourceType` | String, cùng enum ba purposes ở trên |
| `sourceId`, `careEpisodeId` | UUID non-null |
| `careEpisodeType` | String, luôn `OUTPATIENT_VISIT` cho endpoint này |
| `appointmentId`, `recordId`, `prescriptionId` | UUID nullable; mirror đúng target/context đã lưu |
| `labTestIds` | immutable `List<UUID>`, empty khi không phải LAB_TEST; exact singleton labId trong slice này |
| `requestedAmount`, `paidAmount`, `remainingAmount` | BigDecimal, non-null; remaining = max(requested - paid, 0) |
| `currency`, `status`, `accountStatus` | String non-null; trim CHAR currency; mirror stored enums |
| `expiresAt` | Instant nullable, giữ null nếu source không có expiry |
| `observedAt` | Instant non-null, thời điểm đọc snapshot |

`paidAmount` là tổng completed PAYMENT cho exact request, không trừ REFUND/REVERSAL để mở lại
request đã PAID. Refund không biến request thành khoản có thể thanh toán lại. Trả cả PAID/
EXPIRED/CANCELLED và account đóng để người dùng biết trạng thái; POST payment vẫn là nơi kiểm
payability cuối cùng. Snapshot đọc không phải lease hoặc clearance.

Lấy mapping từ `exam_charge_source`, `lab_test_charge_source`, `prescription_charge_source`,
join PAYMENT_REQUEST/ACCOUNT/TARGET/CHARGE và receipts trong **Billing store**. Rx recordId từ
`prescription_charge_source.record_id`; EXAM giữ recordId=null, không đi lookup latest record.
Mọi join phải kiểm account/patient/source/purpose/episode khớp, không chỉ trả UUID tìm được.
Đọc coherent trong một SQL snapshot; không đọc status và amounts qua các transaction rời.

| Tình huống | HTTP / error.code mới hoặc hiện có |
|---|---|
| Thiếu/sai UUID/enum, repeated query key, unknown query key | 400 `INVALID_REQUEST` |
| Source chưa issue/chưa tới consumer, không có source hoặc patient/episode không khớp | 404 `BILLING_PAYMENT_REQUEST_NOT_FOUND`; cùng shape, không tiết lộ request khác |
| Mapping tồn tại nhưng broken/multiple/inconsistent account/target/purpose | 503 `BILLING_LEDGER_UNAVAILABLE`, không chọn tùy tiện một row |
| Store lỗi/timeout | 503 `BILLING_LEDGER_UNAVAILABLE`, không trả 404/zero |
| Chưa đăng nhập / role-token type sai | 401/403 theo security hiện tại; không cấp quyền bằng query |

404 do asynchronous issuance có thể retry GET có giới hạn; GET không issue charge/request,
không có side effect. Duplicate source/event/new eventId hợp lệ vẫn tìm cùng request.

## 4. Contract L2 — chỉ publish cohort ngoại trú mới

Flags mới, default OFF, khai báo application.yml + env mapping cùng tên uppercase:

- `mediflow.billing.outpatient-publication.enabled` / `MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_ENABLED`.
- `mediflow.billing.outpatient-publication.purposes` / `MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_PURPOSES`:
  enum list default empty; chỉ ba purposes EXAM, LAB_TEST, PRESCRIPTION; value khác fail startup.
- `mediflow.billing.outpatient-publication.acceptance-reference` /
  `MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_ACCEPTANCE_REFERENCE`: nonblank khi enabled.
- Discovery env: `MEDIFLOW_BILLING_PAYMENT_REQUEST_DISCOVERY_ENABLED`, default false.

Enrollment cần effective profile `outpatient-mvp`, ledger + relevant issuer consumer gate,
global publication enabled, purpose trong allowlist và nonblank acceptance reference. Không
chấp nhận global flag để bật deferred purposes. Không đổi default deployment/Compose ở task này.

**Enrollment:** chỉ tại lần issue thành công một request mới, cùng transaction charge/request/
source receipt. Tạo `billing_outpatient_publication_cohort` với requestId PK/FK, accountId,
purpose, sourceId, enrolledAt, acceptanceReference. Validate persisted exact source và
OUTPATIENT_VISIT; không enroll trên replay/duplicate source hoặc lúc payment của request cũ.
Request đã tồn tại trước activation không bao giờ join bằng flag toggle, retry hay admin replay.

**Outbox:** thêm `outpatient_payment_request_id` nullable FK tới cohort. V0 giữ semantics hiện tại.
V1 không thuộc cohort tiếp tục HELD. V1 thuộc cohort chỉ có ba event shapes được phép:

| Event | Điều kiện |
|---|---|
| `invoice.created` | Chỉ existing `PrescriptionPaymentRequestPayload`, purpose PRESCRIPTION, exact request/account/source của cohort |
| `payment.completed` | Completed PAYMENT SERVICE_PAYMENT của exact enrolled request; không deposit/settlement |
| `financial.clearance.granted` | Paid-in-full exact request; purpose EXAM/LAB_TEST/PRESCRIPTION, exact persisted target/episode; không emergencyOverride |

Không tạo invoice event mới cho EXAM/LAB_TEST, không rename/reroute/rebuild payload; original
DTO/serializer bytes giữ nguyên. `payment.completed` không là care permission. Refund/deferred/
unknown V1 events vẫn HELD. Historical HELD không được reclassify dựa vào payload.

Thêm `LedgerEventPort.appendForOutpatientRequest(UUID accountId, UUID paymentRequestId,
LedgerIntegrationEvent event)`: adapter kiểm persisted cohort + event relationship rồi ghi
eligible row, hoặc HELD nếu request không enrolled. Mismatch trên enrolled request là conflict
và rollback transaction; không fallback publish/HELD để che corruption. Giữ `appendHeld` cho
deferred callers. Đổi đúng ba call sites ở LedgerPaymentService và PrescriptionChargeService.
Enrollment port `enrollIfEligible(UUID paymentRequestId)` chạy MANDATORY sau save request mới
trong ba issuer adapters; mọi duplicate-return branch không gọi enrollment.

**Migration mới:** tại baseline latest là V21, thêm
`V22__outpatient_publication_cohort.sql` (nếu upstream đã chiếm V22, dùng version trống tiếp theo).
Không sửa V1–V21. Migration tạo cohort/FK và thay V6 constraint bằng fence tương đương:
V0 như cũ; V1 publish chỉ khi có immutable cohort reference và allowed event. Mọi existing row
giữ original bytes/ID/publication status; không backfill cohort, không mass UPDATE release.
DB trigger kiểm exact cohort/account/allowed event/payload links khi insert và chặn thay đổi
cohort/reference/eventId/payload/routing/aggregate/contractVersion/publication eligibility về sau;
retry/claim/confirm/quarantine metadata được thay theo dispatcher. Cohort không update/delete
hoặc tái enroll bằng application/admin path. Test direct SQL vi phạm fence cũng phải fail.

Request đã enrolled tiếp tục lưu eligible facts theo immutable cohort dù enrollment flag OFF;
đây là ngừng nhận cohort mới, không bỏ mất obligation đã nhận. Dừng gửi dùng existing outbox
dispatcher flag; pending bytes/cohort giữ nguyên để resume. Không chạy pre-migration binary
để nhận writes cho cohort mới; rollback chạy current schema/current cohort-aware binary với
enrollment OFF, rồi resume cùng bytes. Không restore DB snapshot cũ hoặc drop constraint.

## 5. Checklist thực thi — Lộc vào code theo thứ tự

Tất cả đường dẫn Java dưới đây tương đối với
`backend/billing-service/src/main/java/com/mediflow/billing/`; tests giữ package tương ứng.
Theo vòng test fail → implementation → test pass cho từng task, không weaken existing guards.

### L1 — discovery API

- [ ] Thêm failing `PaymentRequestDiscoveryServiceTest`, `PaymentRequestDiscoveryControllerTest`,
  `PaymentRequestDiscoveryPostgresTest`: ba purpose sources, duplicate same ID, asynchronous
  missing, wrong patient/episode, token/role, malformed/repeated query, partial/paid/expired,
  coherent concurrent payment snapshot, broken mapping và storage failure đúng section 3.
- [ ] Tạo `application/dto/command/PaymentRequestSourceQuery.java`,
  `application/dto/response/PaymentRequestDiscoveryDTO.java`,
  `application/port/in/DiscoverPaymentRequestUseCase.java` với
  `PaymentRequestDiscoveryDTO discover(PaymentRequestSourceQuery query)`;
  query record gồm purpose/sourceId/patientId/careEpisodeId đúng section 3.
- [ ] Tạo `application/port/out/PaymentRequestDiscoveryPort.java` với cùng query,
  `Optional<PaymentRequestDiscoveryDTO> findExact(PaymentRequestSourceQuery query)`;
  service `PaymentRequestDiscoveryService`, JDBC adapter `PaymentRequestDiscoveryAdapter`,
  config `PaymentRequestDiscoveryConfiguration`, web `PaymentRequestDiscoveryController`.
  Không Spring Data/infrastructure imports trong application; map not-found/unavailable explicit.
- [ ] Update `web/GlobalExceptionHandler.java` nếu cần cho query validation/correlation;
  thêm flag/config, `.http` ADMIN/CASHIER examples và JSON fixtures ở
  `src/test/resources/contracts/payment-request-discovery-v1/` (exam/lab/prescription + error).
- [ ] Chạy targeted tests và giao exact endpoint/query/DTO/error fixtures cho Hoàng Anh G1.

### L2 — migration/cohort/transactional writer

- [ ] Thêm failing `OutpatientPublicationMigrationPostgresTest` và
  `OutpatientPublicationPostgresTest`: fresh/upgrade V21 giữ held rows/checksums, three purposes,
  default-off, wrong profile/purpose, historical request paid sau flag vẫn HELD, duplicate issuer
  không enroll, mixed/forged event bị reject, direct SQL fence, atomic rollback sau enrollment.
- [ ] Tạo migration section 4; tạo `application/port/out/OutpatientPublicationCohortPort.java`
  và `infrastructure/persistence/adapter/OutpatientPublicationCohortAdapter.java` cho enrollment.
  Wire vào `ExamChargeRepositoryAdapter`, `LabTestChargeRepositoryAdapter`,
  `PrescriptionChargeRepositoryAdapter` sau save mới, cùng transaction hiện có.
- [ ] Extend `LedgerEventPort`, `LedgerEventOutboxAdapter`, sửa ba append call sites section 4;
  thêm `OutpatientPublicationConfiguration` cho typed flags/validation, giữ original serializer.
  Không thêm một dispatcher/outbox khác hoặc publish trực tiếp trong payment transaction.
- [ ] Giữ state change/payment/clearance/outbox atomic; test fault sau payment save trước outbox
  rollback tất cả, idempotent retry tạo đúng receipts/clearance và immutable event set.

### L3 — actual Rabbit publication và recovery

- [ ] Thêm `OutpatientPublicationRabbitIntegrationTest`: dùng real PG/Rabbit và actual
  `BillingOutboxDispatcher`, confirm mode correlated + mandatory returns. Không mock LedgerEventPort,
  không tự rabbit.send grant thay dispatcher. Assert broker body SHA-256 bằng stored outbox bytes.
- [ ] Ba flows: original Clinical check-in fixture → EXAM request; original Lab request fixture →
  LAB_TEST request; Pharmacy outpatient creation fixture → PRESCRIPTION request/invoice.
  Tìm request bằng L1 HTTP, thanh toán bằng existing signed ADMIN/CASHIER POST; không SQL lấy ID.
  SQL chỉ assert Billing-owned state. Partial payment chưa có grant; paid-full có exact grant.
- [ ] Assert duplicate input/new eventId cùng operation/payment retry không tạo charge/payment/
  grant logic lần hai; same key/different bytes conflict. V0 compatibility/deferred và historical
  HELD không bị phát. Nhận actual original bytes từ broker, không chỉ assert JSON trong table.
- [ ] Test unroutable/NACK/timeout, retry exhaustion/quarantine, expired lease/stale owner,
  restart pending và kill JVM sau broker publish trước mark: giữ eventId/bytes, có thể redeliver
  nhưng không tạo financial operation lần hai. Admin replay chỉ eligible quarantined row;
  không enroll historical hoặc release deferred. Thêm `OutpatientPublicationProcessRecoveryIT`
  và opt-in Maven profile `outpatient-publication-acceptance` để chạy packaged JVM proof.
- [ ] Equal-timestamp payment/grant và nhiều requests cùng account: receipt/clearance không mất;
  không dựa vào random UUID order để suy causality. Consumer có thể nhận grant trước receipt;
  care gate chỉ dựa exact grant. Quarantine predecessor không bị skip để che lỗi.

### L4 — verification và giao evidence

- [ ] Full module: `mvn -q -pl backend/billing-service -am test`.
- [ ] Package: `mvn -q -pl backend/billing-service -am -DskipTests package`.
- [ ] Recovery: `mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify`;
  profile phải đăng ký Failsafe `OutpatientPublicationProcessRecoveryIT` và không duplicate IT
  trong Surefire. Docker/Java 21 bắt buộc; skipped không tính passed.
- [ ] Ghi source SHA, migration/checksums, flags/profile/purposes, run/fail/skip counts, actual
  source/request/account/transaction/clearance/event IDs và body hashes; không ghi tokens/PII.
  Cập nhật handoff Billing cũ: persisted grant evidence khác actual publication evidence.
- [ ] Giao golden discovery responses + original broker bytes và cách tái lập cho Vinh/Huy/
  Hoàng Anh/Lộc Notification. Producer đạt không tự đóng Clinical/Lab/Report/Notification hoặc
  toàn M6. Consumer purpose nào chưa nghiệm thu giữ enrollment purpose đó OFF ở deployment.

### G1 — Hoàng Anh / Gateway (dependency riêng, không phải việc code của Lộc)

- [ ] Thêm exact GET `/api/v1/billing/payment-requests/by-source` ADMIN/CASHIER; giữ existing
  POST `/{id}/payments`. Không GET wildcard, không cho PATIENT/SYSTEM/service/refresh.
- [ ] Gateway test forward original query/correlation, unauthorized request không đến Billing,
  wrong method/nested path deny. DTO/query validation thực hiện ở Billing, Gateway không suy ID.
- [ ] Chạy `mvn -q -pl backend/gateway -am test`, ghi SHA/counts; chạy public discovery/payment
  với Billing thật trước đóng G1. Downstream double chỉ chứng minh routing/security.

## 6. Review focus và điều kiện Vinh nhận V-CLOSE-03

| Rủi ro cần khóa | Test/deliverable chịu trách nhiệm |
|---|---|
| Async source chưa đến, storage lỗi bị hiểu thành không có khoản phí | L1 404 khác 503, bounded GET retry |
| Same patient nhưng wrong episode/account/source bị trả nhầm request | L1 exact joins + mismatch tests; paid status không tự cho phép care |
| Bật flag/payment/replay làm historical HELD hoặc deferred tự phát | L2 immutable new-request cohort + migration/direct SQL tests |
| DB grant tồn tại nhưng broker chưa nhận, hoặc crash phát trùng | L3 actual dispatcher/confirm/returns + process recovery original bytes |
| Partial/duplicate/refund khiến request trả đủ bị tính lại tiền hoặc grant sai | L1 amount semantics + L2 atomicity + L3 payment idempotency/exact targets |

Vinh nhận V-CLOSE-03 khi L1–L4 + G1 có evidence: lấy actual request từ actual source qua
Gateway, POST trả tiền thật, actual dispatcher publish exact grant tới Clinical/Lab queues.
Vinh tự chứng minh Clinical start-exam/Lab start-result effects, wrong target/expiry/role,
duplicate/restart và reconciliation. Không inject fixture grant, không query Billing DB từ
harness Vinh để gọi đó là paid E2E. Catalog price codes dùng cấu hình Billing đã test, không
tự giả price/expiry/identities hoặc tắt clearance gate.

V-CLOSE-02 và public Lab-order acceptance có thể thực hiện ngay, độc lập với handoff này.
Không làm lại Lab authority/inbox hoặc Clinical/Lab recovery proof đã merge.

## 7. Evidence refresh — 2026-10-16

| Gate | Source SHA / command / counts / IDs-hashes / kết quả |
|---|---|
| L1 discovery | Implement `9b89a355`; fresh service/controller/PG: 27 tests, 0 failure/error/skip; actual HTTP qua Gateway còn mở |
| L2 migration/cohort | Implement `1624cbd3` + V22/V23; fresh cohort/migration PG: 34 tests, 0 failure/error/skip; không tick toàn bộ acceptance checklist chỉ từ focused run |
| L3 Rabbit/recovery | OPEN: thiếu dedicated Rabbit + packaged recovery proof theo section L3 |
| L4 full regression/delivery | OPEN: targeted 61 tests đạt; chưa có full regression/package/recovery và artifact delivery xác nhận đủ handoff |
| G1 Gateway + actual Billing | OPEN: thiếu exact GET allowlist; Lab-order route đã có là task khác |
| V-CLOSE-03 consumer acceptance | Chưa chạy — chờ producer/Gateway |
