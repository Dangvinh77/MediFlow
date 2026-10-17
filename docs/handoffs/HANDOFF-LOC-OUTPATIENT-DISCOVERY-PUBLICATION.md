# Lộc — implementation handoff: discovery và publication V1 ngoại trú

## 0. Phiếu thực thi hiện hành — 2026-10-17, Lộc đọc mục này trước

**Owner:** Lộc (`locgit-89`). **Người nhận nghiệm thu:** Vinh; Huy nhận phần PRESCRIPTION.
Source đối chiếu: `d80d23b2` + working changes Vinh được ghi trong
[Vinh closeout](HANDOFF-VINH-OUTPATIENT-MVP.md#fresh-vinh-source-verification--2026-10-17).
Đây là task tiếp tục Billing, không giao lại L1/L2 và không cấp quyền sửa service khác.
Các mục 1–7 bên dưới giữ contract và lịch sử; khi trạng thái cũ mâu thuẫn, dùng mục 0 này.

**Refresh tích hợp Huy ngày 2026-10-17:** theo giao quyền người dùng, hai Rabbit cases
duplicate-new-event-ID và historical-outpatient-held đã chạy đạt trong suite 8/8; không còn
chờ Docker/compile-only cho hai case này. Actual sáu-app paid Rx/current authority/dispense/
Clinical attachment/Report/private notice và Pharmacy restart cũng đã đạt. Huy sửa các lỗi
exact Pharmacy service caller, current Rx ledger/allocations và original grant Instant ở Billing.
Evidence mới nằm trong đúng [handoff chung Huy mục 10](HANDOFF-HUY-MVP.md#10-integrated-backend-closeout--2026-10-17)
và [runbook mục 8](../plans/huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).
Các dòng “chưa chạy/paidRx còn mở” bên dưới là trạng thái trước lượt tích hợp này. Không suy
full LOC-03/04/05, full Billing regression/recovery bundle hoặc global release đã được nghiệm
thu từ selected Billing 133 tests và sáu-app test; không giao lại code Rx/Support đã đạt.

### 0.1. Trạng thái đã kiểm và phần không làm lại

| Hạng mục | Trạng thái hiện hành | Hành động của Lộc |
|---|---|---|
| L1 discovery, L2 cohort/V22/V23 | Đã có implementation và tests | Giữ regression; không tạo endpoint/cohort mới |
| Gateway exact GET by-source | Đã có; #417 sửa Billing error correlation | Không code Gateway; không mở lại lỗi 404 correlation đã sửa |
| Actual paid EXAM/LAB_TEST | Vinh chạy PASS qua Gateway/Billing/Rabbit → Clinical/Lab ngày 17/10 | Dùng làm đối chiếu; không gọi đây là full Billing/G1 acceptance |
| Rabbit happy path + Rx hai installments | Đã có collector FIFO và kiểm đủ hai receipts | Giữ nguyên các assertions và original bytes |
| Unroutable/retry/quarantine, admin replay happy path, expired lease reclaim, equal-timestamp ordering | Source hiện có 4 tests trong `OutpatientPublicationDispatcherResilienceRabbitIntegrationTest` | Chạy lại và trích evidence; không viết lại từ đầu |
| Duplicate source với eventId mới; historical outpatient request trước activation tại broker | Còn thiếu Rabbit-level proof theo review #401 | Làm LOC-01 và LOC-02 |
| NACK và confirm timeout | Chưa có case riêng; unroutable không chứng minh hai nhánh này | Làm LOC-03 |
| Full L4 evidence bundle | Có historical owner counts, thiếu bộ bàn giao mới đầy đủ | Làm LOC-05; không chép số cũ thành fresh result |
| paidRx actual producer/consumer | Còn mở, phụ thuộc Huy + Vinh | Lộc làm Billing trong LOC-06; không tự đóng consumer |

Không suy Billing có bug chỉ vì còn thiếu test. Nếu test mới chứng minh implementation đúng,
deliverable có thể chỉ là tests/evidence; chỉ sửa production khi đã tái hiện lỗi.

### 0.2. Phạm vi và điểm bắt đầu

Được sửa `backend/billing-service/**` và evidence trong handoff này. Service khác, root POM,
common, Compose, scripts/CI và frontend chỉ đọc. Không sửa producer/consumer để test Billing đạt.
Trước code: chạy changelog; đọc root/module AGENTS, `docs/ai/README.md`, canonical plans 03/04/06
và các care-finance contracts được module AGENTS chỉ định. Đối chiếu live DTO/serializer/fixtures.
Không thêm migration nếu chỉ cần bổ sung test; không sửa checksum migration đã giao.

Đường dẫn bắt đầu, tương đối với `backend/billing-service/`:

| Nhu cầu | File hiện có |
|---|---|
| Rabbit happy path, source fixture → HTTP discovery/payment → broker bytes | `src/test/java/com/mediflow/billing/OutpatientPublicationRabbitIntegrationTest.java` |
| Failure/replay/lease/ordering với real PG/Rabbit | `src/test/java/com/mediflow/billing/OutpatientPublicationDispatcherResilienceRabbitIntegrationTest.java` |
| Kill JVM/restart cùng JAR | `src/test/java/com/mediflow/billing/OutpatientPublicationProcessRecoveryIT.java` |
| Nhánh NACK/timeout và retry | `src/main/java/com/mediflow/billing/infrastructure/messaging/BillingOutboxDispatcher.java` |
| Unit seam dispatcher | `src/test/java/com/mediflow/billing/infrastructure/messaging/BillingOutboxDispatcherTest.java` |
| Cohort/history/immutability | `src/test/java/com/mediflow/billing/OutpatientPublicationPostgresTest.java`, `OutpatientPublicationMigrationPostgresTest.java` cùng thư mục |

### 0.3. Thứ tự thực hiện và tiêu chí từng task

**LOC-01 — Duplicate source, eventId mới, cùng operation (L3; bắt buộc).**

- [ ] Mở rộng Rabbit integration hiện có. Tạo source từ original producer fixture như happy path;
  discovery qua HTTP để lấy request. Phát lại cùng source operation với eventId mới theo đúng
  producer contract; giữ business payload/target, không ngẫu nhiên thay UUID quan hệ.
- [ ] Chứng minh vẫn một charge/request/cohort; discovery trả cùng request. Thanh toán qua HTTP
  với cùng operation key khi retry. Assert một logical transaction/clearance, không grant mới
  do duplicate source; partial payment không cấp grant. Đối soát đủ event set theo purpose.
- [ ] Observer nhận original bytes từ actual dispatcher. So eventId, số lượng và SHA-256 với
  stored outbox; dùng bounded polling/quiet window có timeout, không chỉ `sleep` rồi thấy rỗng.
  Duplicate do delivery/recovery được phân biệt với logical outbox event mới.

**LOC-02 — Historical outpatient request không được enroll muộn (L3; bắt buộc).**

- [ ] Issue request ngoại trú bằng issuer thật khi enrollment OFF; ghi lại request/cohort/outbox
  baseline. Sau đó bật enrollment trong isolated test context/process trên cùng DB và thanh toán
  request cũ qua HTTP. Không SQL thêm cohort hoặc sửa publication eligibility.
- [ ] Assert request cũ vẫn không có cohort, V1 rows vẫn HELD, original eventId/body không đổi;
  actual dispatcher không phát các historical event đó. Retry payment/admin replay không biến
  HELD thành eligible; dùng đúng live error/status của replay contract.
- [ ] Cùng bài test có request mới sau activation được enroll và phát được: positive control này
  chứng minh broker/observer/dispatcher hoạt động, tránh kết luận sai từ một queue luôn rỗng.

**LOC-03 — NACK và confirm timeout (L3; bắt buộc, ghi đúng tầng bằng chứng).**

- [ ] Thêm hai test riêng gọi actual `dispatchPending()`: correlated confirm NACK và future không
  hoàn tất trong `confirm-timeout-ms`. Tái sử dụng constructor/test seam hiện có; không gọi private
  method bằng reflection, không đưa test hook vào API production.
- [ ] Với từng case: không gọi thành công `markPublished`, đi `markFailure`, retry có giới hạn/
  backoff và không vượt max; event tiếp theo không vượt predecessor lỗi. Dùng PG integration
  kiểm durable attempts/availableAt/quarantine nếu assertions đó vượt phạm vi unit test.
- [ ] Nếu không ép được broker NACK/timeout ổn định: dùng controlled RabbitTemplate/confirm future
  ở tầng unit/adapter; ghi rõ đó là injected-confirm proof. Real PG/Rabbit unroutable/recovery
  tests vẫn phải chạy. Không ghi NACK/timeout là actual-broker proof khi chưa làm được.
  Timeout có thể xảy ra sau khi broker nhận body: không assert tuyệt đối zero deliveries hoặc
  exactly-once transport; eventId/bytes phải giữ nguyên và consumer xử lý idempotent.
- [ ] Audit admin replay eligibility và stale-owner fencing hiện có: ngoài happy reclaim, owner
  cũ không được commit `published`/failure sau khi lease đã bị owner mới claim. Nếu chưa có case
  đúng tầng PG, thêm test; HELD/historical/ineligible rows không được mở bằng admin replay.

**LOC-04 — completedAt ở payment replay (xác minh và chốt hành vi).**

Vinh ghi nhận first `2026-10-17T10:04:52.023375300Z`, replay
`2026-10-17T10:04:52.023375Z`: PostgreSQL microsecond precision; cùng transaction/business fields.
Đây chưa phải bằng chứng thu tiền hai lần.

- [ ] Tái hiện first POST và retry cùng key qua HTTP + PG, so mọi business field và số transaction.
  Đọc canonical idempotency/response contract trước khi chọn cách xử lý.
- [ ] Nếu cần stable response, chuẩn hóa timestamp một lần ở boundary lưu/response phù hợp rồi
  trả cùng giá trị first/replay; thêm regression cho nanosecond input và persisted readback.
  Không sửa event bytes cũ, không sửa DB history, không bỏ `completedAt` khỏi response.
- [ ] Nếu contract cho phép precision hiện tại, ghi rõ căn cứ/giới hạn vào evidence để Vinh review;
  không tự tuyên bố byte-equivalent. Không thêm tolerance chung cho amount/IDs/status.

**LOC-05 — Full regression và bộ bàn giao (L4; bắt buộc).**

Từ repository root, Java 21 và Docker sẵn sàng; mỗi lệnh phải exit 0:

```powershell
mvn -version
docker info
mvn -q -pl backend/billing-service -am '-Dtest=OutpatientPublicationRabbitIntegrationTest,OutpatientPublicationDispatcherResilienceRabbitIntegrationTest,BillingOutboxDispatcherTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl backend/billing-service -am test
mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify
git diff --check
```

`verify` phải package JAR mới và chạy Failsafe recovery IT; `test` không chạy `*IT`.
Nếu thêm class mới, thêm vào targeted command. Không skip Docker tests để lấy màu xanh.
Ghi counts Surefire/Failsafe riêng, không cộng full `test` hai lần với suite chạy lại trong `verify`.
Lệnh kiểm tra dưới đây không bật deployment flags; test profiles chỉ dùng tài nguyên isolated.

- [ ] Lưu raw JSON broker bytes + discovery/payment responses đã bỏ credentials trong
  `backend/billing-service/target/outpatient-handoff/` (đề xuất thư mục evidence, chưa phải tool có sẵn).
  Export phải giữ raw bytes trước parse/format để hash đúng. Không ghi Bearer token/password/PII.
- [ ] Tạo manifest ở đó: source SHA + trạng thái dirty/diff, JDK/Maven/Docker/image version,
  commands/exit codes/counts, migration versions/checksums, effective profile/flags/purposes,
  catalog/priceCode/currency, exact source/patient-test/episode/request/account/transaction/
  clearance/event IDs, filenames/SHA-256, expected-vs-actual và kết quả từng LOC task.
- [ ] Ghi summary bền vững + commands tái lập trong mục 7 handoff này. `target/` bị ignore:
  bàn giao bundle qua artifact/PR attachment hoặc vị trí chung truy cập được và đặt link tại đây;
  không chỉ gửi đường dẫn máy cá nhân. Fixture đưa vào Git chỉ dùng dữ liệu test an toàn.
- [ ] Rerun correlation regression hiện có; không sửa lại #417 nếu không có lỗi mới. Counts cũ
  1007/1011 chỉ là lịch sử, không hardcode làm acceptance của source mới.

**LOC-06 — Gói phối hợp paidRx cho Huy/Vinh (Billing deliverable).**

- [ ] Thống nhất với Huy actual prescription source + priceCode/quantity/episode theo live
  Pharmacy contract. Lộc cung cấp flags/catalog isolated, hướng dẫn login CASHIER và bốn query
  values `purpose=PRESCRIPTION/sourceId=prescriptionId/patientId/careEpisodeId` từ API thật.
  Không dùng golden UUID cũ như dữ liệu môi trường đang chạy; không SQL lấy request ID.
- [ ] Public discovery → partial/full payment → original invoice/receipt/clearance broker bytes;
  ghi actual IDs/hash và thử payment retry. Invoice không thay clearance; receipt không cấp quyền
  dispense. Fixture publisher chỉ chứng minh producer-local integration, không phải actual paidRx.
- [ ] Huy nghiệm thu grant → dispense/filled; Vinh nghiệm thu original filled bytes → Clinical.
  Ghi phần nào PASS/BLOCKED, owner và input còn thiếu. Không tự sửa Pharmacy/Clinical, không đóng
  paidRx khi chỉ Billing tests đạt. Integrated JVM recovery là một gate phối hợp riêng.

### 0.4. Mẫu báo cáo bắt buộc khi giao lại

Điền sau khi chạy, không thay ô chưa chạy bằng dự đoán:

| Task | PASS / FAIL / BLOCKED | Test/method hoặc command | Fresh counts, exit code | Artifact + hash / owner dependency |
|---|---|---|---|---|
| LOC-01 duplicate new eventId | Chưa nghiệm thu | Chưa ghi | Chưa chạy | Chưa giao |
| LOC-02 historical HELD tại broker | Chưa nghiệm thu | Chưa ghi | Chưa chạy | Chưa giao |
| LOC-03 NACK/timeout + fencing/replay audit | Chưa nghiệm thu | Chưa ghi; tách unit/PG/broker | Chưa chạy | Chưa giao |
| LOC-04 timestamp replay | Chưa nghiệm thu | Chưa ghi | Chưa chạy | Chưa giao |
| LOC-05 regression/recovery/artifacts | Chưa nghiệm thu | Chưa ghi | Chưa chạy | Chưa giao |
| LOC-06 Billing paidRx package | Chưa nghiệm thu | Chưa ghi | Chưa chạy | Huy/Vinh nhận consumer |

Vinh nhận khi từng assertion có test/evidence đúng tầng, full suite và recovery đạt không skip,
bundle mở được và tái lập được. Nếu thiếu môi trường/input: ghi blocker chính xác, log lỗi và
owner cần cung cấp; tiếp tục task độc lập. Không bỏ assertion, tắt gate, purge DLQ hoặc release
historical HELD để làm test đạt. Publication purposes ở deployment vẫn OFF nếu consumer chưa
nghiệm thu. Không đóng toàn MVP/G1/paidRx dựa vào Billing owner-local tests.

---

## Snapshot lịch sử ngày 2026-10-16

Ngày giao: **2026-10-16**. Refresh: PR **#401**, source `bea617db` tích hợp master.
Refresh **2026-10-17**, baseline `16a5e06e`: người dùng đã giao Huy làm trực tiếp phần Lộc
liên quan Pharmacy/Report. Đã bổ sung hai Rabbit cases new-event-ID và historical-outpatient-held
trong working tree; test sources compile nhưng chưa chạy do Docker chưa sẵn sàng. Đây không phải
full L3 acceptance. Evidence hiện hành ghi trong [file chung Huy, mục 8](HANDOFF-HUY-MVP.md#8-trực-tiếp-xử-lý-billingnotification-liên-quan-huy-2026-10-17);
không tạo handoff mới hoặc yêu cầu Lộc nhận lại phần code đã làm.
**Trạng thái: L1/L2 đã merge; L3 có Rabbit/recovery proof nhưng CHƯA ĐÓNG; L4 có evidence owner.**
Hai nhóm L3 còn thiếu: unroutable/NACK/timeout/retry exhaustion/quarantine/stale owner và
equal-timestamp/multi-request ordering. G1 và V-CLOSE-03 vẫn mở.

## Current delivery — Hoàng Anh và Lộc nhận đúng phần còn lại

| Owner | Đã giao | Công việc tiếp theo |
|---|---|---|
| Lộc | L1 `9b89a355`; L2 `1624cbd3`, V22 cohort/V23 payload fence, writer và PG tests | PR #401 thêm actual Rabbit/dispatcher, packaged recovery và L4 artifacts; còn hai nhóm L3 chưa tick ở mục 5 |
| Hoàng Anh | Public Lab-order và Core migration paths tại `0a8636cd` | G1 exact GET discovery ADMIN/CASHIER, query/correlation forwarding và deny tests; actual Billing smoke |
| Vinh | Rx V1 consumer + public Lab-order acceptance | V-CLOSE-03 actual paid EXAM/LAB_TEST và paid Rx sau producer/Gateway evidence |

Fresh targeted rerun 2026-10-16: **61 Billing tests, 0 failures/errors/skips**, Java 21,
real PostgreSQL; discovery 27, publication/migration 34. Gateway care matrix/Lab-order
**183 tests, 0 failures/errors/skips**. Hai commands exit 0, không thay full Billing/Gateway
regression hoặc actual publication. Logs và reproduce ở [Vinh closeout](HANDOFF-VINH-OUTPATIENT-MVP.md#verification-refresh--2026-10-16).

PR #401 đã thêm `OutpatientPublicationRabbitIntegrationTest` (6 cases),
`OutpatientPublicationProcessRecoveryIT` và profile `outpatient-publication-acceptance`.
Bằng chứng này bổ sung cho eligible DB rows, không thay nghiệm thu consumer/Gateway.
Gateway allowlist ở baseline master tích hợp chưa có exact GET by-source; G1 working-tree
implementation/test refresh nằm bên dưới. Public paid slice vẫn mở tới actual Billing smoke.
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

- [x] Thêm failing `PaymentRequestDiscoveryServiceTest`, `PaymentRequestDiscoveryControllerTest`,
  `PaymentRequestDiscoveryPostgresTest`: ba purpose sources, duplicate same ID, asynchronous
  missing, wrong patient/episode, token/role, malformed/repeated query, partial/paid/expired,
  coherent concurrent payment snapshot, broken mapping và storage failure đúng section 3.
- [x] Tạo `application/dto/command/PaymentRequestSourceQuery.java`,
  `application/dto/response/PaymentRequestDiscoveryDTO.java`,
  `application/port/in/DiscoverPaymentRequestUseCase.java` với
  `PaymentRequestDiscoveryDTO discover(PaymentRequestSourceQuery query)`;
  query record gồm purpose/sourceId/patientId/careEpisodeId đúng section 3.
- [x] Tạo `application/port/out/PaymentRequestDiscoveryPort.java` với cùng query,
  `Optional<PaymentRequestDiscoveryDTO> findExact(PaymentRequestSourceQuery query)`;
  service `PaymentRequestDiscoveryService`, JDBC adapter `PaymentRequestDiscoveryAdapter`,
  config `PaymentRequestDiscoveryConfiguration`, web `PaymentRequestDiscoveryController`.
  Không Spring Data/infrastructure imports trong application; map not-found/unavailable explicit.
- [x] Update `web/GlobalExceptionHandler.java` nếu cần cho query validation/correlation;
  thêm flag/config, `.http` ADMIN/CASHIER examples và JSON fixtures ở
  `src/test/resources/contracts/payment-request-discovery-v1/` (exam/lab/prescription + error).
  (GlobalExceptionHandler không cần sửa — path `/api/v1/billing/payment-requests/` đã khớp nhánh
  503 có sẵn; validation 400 xử lý trực tiếp ở controller.)
- [x] Chạy targeted tests và giao exact endpoint/query/DTO/error fixtures cho Hoàng Anh G1.

### L2 — migration/cohort/transactional writer

- [x] Thêm failing `OutpatientPublicationMigrationPostgresTest` và
  `OutpatientPublicationPostgresTest`: fresh/upgrade V21 giữ held rows/checksums, three purposes,
  default-off, wrong profile/purpose, historical request paid sau flag vẫn HELD, duplicate issuer
  không enroll, mixed/forged event bị reject, direct SQL fence, atomic rollback sau enrollment.
  (V22 của Lộc + V23 payload-content fence của một thành viên khác tiếp tục trên cùng nhánh.)
- [x] Tạo migration section 4; tạo `application/port/out/OutpatientPublicationCohortPort.java`
  và `infrastructure/persistence/adapter/OutpatientPublicationCohortAdapter.java` cho enrollment.
  Wire vào `ExamChargeRepositoryAdapter`, `LabTestChargeRepositoryAdapter`,
  `PrescriptionChargeRepositoryAdapter` sau save mới, cùng transaction hiện có.
- [x] Extend `LedgerEventPort`, `LedgerEventOutboxAdapter`, sửa ba append call sites section 4;
  thêm `OutpatientPublicationConfiguration` cho typed flags/validation, giữ original serializer.
  Không thêm một dispatcher/outbox khác hoặc publish trực tiếp trong payment transaction.
  (Config tên thật là `OutpatientPublicationProperties` + `BillingApplicationModeConfiguration`.)
- [x] Giữ state change/payment/clearance/outbox atomic; test fault sau payment save trước outbox
  rollback tất cả, idempotent retry tạo đúng receipts/clearance và immutable event set.

### L3 — actual Rabbit publication và recovery

- [x] Thêm `OutpatientPublicationRabbitIntegrationTest`: dùng real PG/Rabbit và actual
  `BillingOutboxDispatcher`, confirm mode correlated + mandatory returns. Không mock LedgerEventPort,
  không tự rabbit.send grant thay dispatcher. Assert broker body SHA-256 bằng stored outbox bytes.
- [x] Ba flows: original Clinical check-in fixture → EXAM request; original Lab request fixture →
  LAB_TEST request; Pharmacy outpatient creation fixture → PRESCRIPTION request/invoice.
  Tìm request bằng L1 HTTP, thanh toán bằng existing signed ADMIN/CASHIER POST; không SQL lấy ID.
  SQL chỉ assert Billing-owned state. Partial payment chưa có grant; paid-full có exact grant.
- [x] Assert duplicate input/new eventId cùng operation/payment retry không tạo charge/payment/
  grant logic lần hai; V0 compatibility/deferred không bị phát (test
  `deferredAdmissionDepositPurpose_isNeverPublishedToTheBroker`). Nhận actual original bytes từ
  broker qua SHA-256, không chỉ assert JSON trong table. (Riêng "same key/different bytes conflict"
  đã có test ở tầng service — `LedgerPaymentServiceTest`/idempotency unit tests — không lặp lại ở L3.)
  **Review PR #401 (Vinh) phát hiện 3 gap, 1 đã sửa:** (1) `received()` helper trước đó chỉ buffer
  **một** message mỗi eventType (`Map<String, Message>`) — nếu Rx hai installments sinh hai
  `payment.completed` cho cùng request, cái thứ hai bị cái sau ghi đè mất, test chỉ kiểm được một
  cách tình cờ. **Đã sửa**: đổi thành hàng đợi FIFO mỗi eventType
  (`Map<String, Deque<Message>>`) + sửa `prescriptionOutpatient...TwoInstallments` để kiểm **cả
  hai** receipt (750.00 rồi 1250.00) đúng thứ tự, đúng bytes, không mất — 6/6 pass lại sau sửa.
  (2) duplicate input với **eventId mới** cùng một operation và (3) **historical outpatient
  request issued trước khi bật flag** vẫn HELD — hai Rabbit-level cases đã được Huy bổ sung
  ngày 2026-10-17 theo override người dùng. Case historical dùng prior application context thật
  với publication OFF, không SQL seed/mock enrollment. Đã compile; chưa chạy PG/Rabbit trong
  refresh này, vì vậy runtime acceptance vẫn mở.
- [x] Thêm `OutpatientPublicationDispatcherResilienceRabbitIntegrationTest` (4 test, real PG/Rabbit,
  dispatcher thật): unroutable (không binding) → retry → quarantine đúng `maxAttempts`, không bao
  giờ `published_at`; admin replay (`POST /api/v1/billing/admin/outbox/{id}/replay`) chỉ publish
  lại **đúng một lần** cho row đang quarantined, replay lần 2 trên row đã publish bị từ chối 404;
  expired lease/stale owner (`locked_at` giả cũ hơn `lease-seconds`) được dispatcher thật reclaim
  và publish đúng một lần. Kill JVM sau broker publish trước mark vẫn ở
  `OutpatientPublicationProcessRecoveryIT` (mục trên) + profile `outpatient-publication-acceptance`
  (at-least-once redelivery sau crash; không republish sau khi `published_at` đã commit).
  NACK/confirm-timeout không có test riêng (khó ép broker NACK một publish hợp lệ mà không giả
  broker; rủi ro này đã được "unroutable" + "expired lease" che phủ phần lớn: cùng đường code
  `markFailure`/retry/quarantine).
- [x] Cùng file trên: equal-timestamp (`created_at` ép bằng nhau qua SQL) + hai request cùng account
  — event có `event_id` nhỏ hơn bị thử trước; khi nó bị quarantine (chưa resolve), event thứ hai
  **không bị mất, không bị bỏ qua (skip)** — `attempts=0`, chưa publish, đứng im đúng như spec
  "quarantine predecessor không bị skip để che lỗi" — tới khi replay cái đầu, cái thứ hai mới được
  publish, đúng thứ tự. (Phát hiện phụ lúc viết test: dispatcher không có lỗi; lỗi là do test tự
  để queue cũ từ lần test trước rò sang lần sau trên cùng container Rabbit chia sẻ — đã sửa test,
  không sửa `BillingOutboxDispatcher.java`.)

Lưu ý lịch sử PR #401 về helper `received()` đã được sửa bằng FIFO deque và kiểm đủ hai
installment receipts như mô tả ở trên; không giao lại bug đã sửa. Recovery IT seed Billing-only state để kiểm outbox replay;
không suy thành HTTP financial-operation/consumer dedup proof từ outbox count/bytes.

### L4 — verification và giao evidence

- [x] Full module: `mvn -q -pl backend/billing-service -am test`.
- [x] Package (package phase được chạy trong verify bên dưới; command riêng nếu cần): `mvn -q -pl backend/billing-service -am -DskipTests package`.
- [x] Recovery: `mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify`;
  profile phải đăng ký Failsafe `OutpatientPublicationProcessRecoveryIT` và không duplicate IT
  trong Surefire. Docker/Java 21 bắt buộc; skipped không tính passed.
- [ ] Ghi source SHA, migration/checksums, flags/profile/purposes, run/fail/skip counts, actual
  source/request/account/transaction/clearance/event IDs và body hashes; không ghi tokens/PII.
  Cập nhật handoff Billing cũ: persisted grant evidence khác actual publication evidence.
- [ ] Giao golden discovery responses + original broker bytes và cách tái lập cho Vinh/Huy/
  Hoàng Anh/Lộc Notification. Producer đạt không tự đóng Clinical/Lab/Report/Notification hoặc
  toàn M6. Consumer purpose nào chưa nghiệm thu giữ enrollment purpose đó OFF ở deployment.

### G1 — Hoàng Anh / Gateway (dependency riêng, không phải việc code của Lộc)

- [x] Thêm exact GET `/api/v1/billing/payment-requests/by-source` ADMIN/CASHIER; giữ existing
  POST `/{id}/payments`. Không GET wildcard, không cho PATIENT/SYSTEM/service/refresh.
- [x] Gateway test forward original query/correlation, unauthorized request không đến Billing,
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

Source SHA cho evidence owner L1–L4 (L3 còn các gaps ghi ở mục 5): `8bf1be00171b9849d38603fc9542cbf93956a3c5` (nhánh
`feat/billing-outpatient-discovery-publication`, dựa trên `master` đã có PR #396 — bao gồm cả
phần V23 payload fence do một thành viên khác tiếp tục hoàn thiện trên cùng nhánh). Migration
`V22__outpatient_publication_cohort.sql` (cohort + fence) và `V23__outpatient_publication_payload_fence.sql`
(kiểm nội dung payload đúng thực tế persisted, không chỉ account/event-type); `V1`–`V21` không đổi.
Flags dùng khi test: `mediflow.billing.ledger.enabled=true`,
`mediflow.billing.payment-request-discovery.enabled=true`,
`mediflow.billing.outpatient-publication.enabled=true`,
`mediflow.billing.outpatient-publication.purposes=EXAM,LAB_TEST,PRESCRIPTION`,
`mediflow.billing.outpatient-publication.acceptance-reference=<non-blank>`,
`mediflow.features.outpatient-mvp.enabled=true`. Mặc định cả hai flags publication/discovery
vẫn `false` — hành vi hiện tại không đổi khi chưa bật.

| Gate | Source SHA / command / counts / IDs-hashes / kết quả |
|---|---|
| L1 discovery | `mvn -pl backend/billing-service -am test` — nằm trong 1007/1007 pass (0 fail/error/skip, xem L4). Endpoint `GET /api/v1/billing/payment-requests/by-source`. Golden response thật (ADMIN token) lưu ở `src/test/resources/contracts/payment-request-discovery-v1/{exam,lab,prescription,not-found}.json`; ví dụ `.http` ở `backend/billing-service/payment-request-discovery.http`. Request mẫu đã nghiệm thu: EXAM `paymentRequestId=b7943beb-9a7e-4abc-9f68-04d43e210741`, LAB_TEST `67d05904-4eee-46f2-9008-1076249e6ddf`, PRESCRIPTION `94346737-ce0b-43fb-be01-3a4f18eca121`. |
| L2 migration/cohort | `OutpatientPublicationMigrationPostgresTest` (10 test) + `OutpatientPublicationPostgresTest` (nay do V23 mở rộng thêm, bao gồm `mismatchedPayloadIsRejectedByWriterAndDirectSql` và `actualIssuanceAndPaymentPublishExactOriginalEvents`) — toàn bộ nằm trong 1007/1007. Fresh-migrate tới V23 và upgrade-từ-V21 đều giữ nguyên historical bytes/publication status; direct-SQL vi phạm fence (sửa `publication_enabled`, `payload`, `routing_key`, cohort row, hoặc insert với account/purpose/payload sai) đều bị `DataIntegrityViolationException`. |
| L3 Rabbit/recovery | `OutpatientPublicationRabbitIntegrationTest` (6/6 pass, real PG+Rabbit+`BillingOutboxDispatcher`, không mock). Broker bytes SHA-256 khớp đúng bytes lưu `BILLING_EVENT_OUTBOX.payload` — ví dụ thật: EXAM `payment.completed` sha256 `525c365d364e1403c5180631e5a8dc1ff571feb2c91c5223df9403a5e5c442e5`, EXAM `financial.clearance.granted` sha256 `6e87afff8c45362ac04ac7b34c176ea48ad99e146b18b22ecb956f37061269a0`, PRESCRIPTION `invoice.created` sha256 `bbc8e5cc4e51c386aaab421a4d30c131f50fa34cf6bbbb70b487f759e0a77ff5` (shape khớp fixture có sẵn `contracts/ledger-v1/{payment-service,clearance-exam,invoice-prescription}.json`). `OutpatientPublicationDispatcherResilienceRabbitIntegrationTest` (4/4 pass, real PG+Rabbit): unroutable→retry→quarantine, admin replay chỉ-eligible-quarantined-row (publish lại đúng một lần, lần 2 bị 404), expired-lease/stale-owner reclaim đúng một lần, same-timestamp same-account ordering với quarantined predecessor không bị skip. `OutpatientPublicationProcessRecoveryIT` qua `mvn -pl backend/billing-service -am -Poutpatient-publication-acceptance verify`: 1/1 pass (kill JVM thật sau broker confirm, trước commit `published_at`; restart cùng jar resume đúng eventId/bytes theo at-least-once; có redelivery sau crash, không republish sau khi `published_at` đã commit). Không IT nào bị Surefire chạy trùng (mặc định Surefire không khớp `*IT.java`). **Review PR #401 (Vinh) phát hiện 3 gap, 1 đã sửa** (xem mục 5): (1) `received()` helper chỉ buffer một message mỗi eventType — **đã sửa** thành FIFO queue mỗi eventType, `prescriptionOutpatient...TwoInstallments` nay kiểm cả hai `payment.completed` (750.00 rồi 1250.00) đúng thứ tự/bytes; (2) duplicate input với eventId mới cùng operation chưa có Rabbit-level proof — còn treo; (3) historical outpatient request issued trước khi bật flag vẫn HELD chưa có actual-broker proof (chỉ có DB-level ở L2) — còn treo. |
| L4 full regression/delivery | `mvn -pl backend/billing-service -am test`: **1011/1011 pass, 0 fail/error/skip** (chạy lặp lại 2 lần, ổn định). `mvn -pl backend/billing-service -am -DskipTests package`: build thành công, jar `billing-service-0.0.1-SNAPSHOT.jar`. `mvn -pl backend/billing-service -am -Poutpatient-publication-acceptance verify`: **1/1 pass** (yêu cầu Docker + Java 21; môi trường chạy thật, không skip). Không ghi token/PII; mọi ID ở trên là UUID nghiệp vụ (account/request/transaction/clearance/event), không phải thông tin bệnh nhân thật. |
| G1 Gateway + actual Billing | Full Gateway suite **256 tests, 0 failures/errors/skips**, rerun với Docker. Actual EXAM source → public discovery/payment → PAID + Rabbit receipt/grant đã chạy; smoke exit 1 do Billing 404 thiếu body correlation (xác nhận direct Billing). G1 chưa đóng; chi tiết refresh Docker bên dưới. |
| V-CLOSE-03 consumer acceptance | Chưa chạy — chờ G1 xong; Vinh tự chứng minh theo mục 6. |

### Review/rerun PR #401 — 2026-10-16

Reviewed Billing code `bea617db` integrated with master `07cc2ad0`; integration edits only
resolve changelog and refresh handoff/plan claims. Java 21.0.11, Docker Desktop 29.8.0,
PostgreSQL `postgres:16-alpine`, RabbitMQ `rabbitmq:3.13-alpine`.

Command from repository root: `mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify`,
exit **0**. Fresh Surefire Billing: **1007 tests, 0 failures/errors/skips**, including
`OutpatientPublicationRabbitIntegrationTest` **6/6**. Package phase produced executable Billing
JAR; Failsafe `OutpatientPublicationProcessRecoveryIT`: **1 test, 0 failures/errors/skips**.
IT was not duplicated in Surefire. Log: `%TEMP%/pr401-billing-verify.log`; XML reports:
`backend/billing-service/target/{surefire-reports,failsafe-reports}/`.

Review: **GO for partial-evidence merge; NO-GO for closing L3/L4 in full**. The remaining L3
cases above and L4 artifact checklist stay open. Owner hashes/IDs in section 7 are historical
owner captures; fresh test rerun verifies byte equality but does not export a new full raw
event/hash/ID bundle. G1, V-CLOSE-03 and deployment flags remain unchanged.

### G1 Gateway working-tree evidence — Hoàng Anh, 2026-10-16

Snapshot trước Docker; trạng thái actual smoke được thay bằng refresh Docker ở cuối file.

**PARTIAL: implementation và routing/security regression đạt; actual Billing smoke chưa chạy.
G1 vẫn OPEN.** Source baseline `da99a906ffd410f1ab8a9698b1103e8649ca7a38`, khớp
`origin/master` sau `git fetch origin` trong lượt này. Evidence dùng baseline + uncommitted
Gateway changes, không phải một commit/PR đã merge. Không push/merge trong lượt này.

Đã đọc root/Gateway AGENTS, canonical `docs/ai/README.md`, `docs/plans/01`–`06`, security/API
compatibility pointers và mục 3/G1. Đối chiếu live Billing files tại baseline:
`web/PaymentRequestDiscoveryController.java` (GET, bốn single-value query, ADMIN/CASHIER),
`infrastructure/config/SecurityConfig.java`, `infrastructure/security/JwtAuthFilter.java`
(signed access + account UUID, không service/refresh/SYSTEM cho payment-requests),
`web/LedgerPaymentController.java` và `application/dto/request/CompleteLedgerPaymentRequest.java`.
Không thay DTO/contracts, Billing/source relationships hoặc code ngoài production Gateway.

Production diff đúng một rule trong `RouteAuthorizationFilter`: exact GET
`/api/v1/billing/payment-requests/by-source`, ADMIN/CASHIER. Existing POST
`/api/v1/billing/payment-requests/{id}/payments` giữ nguyên. Query/correlation đi qua existing
`lb://billing-service` route và JWT/correlation filters; không thêm parser, ID lookup hoặc
query rewrite. Correlation UUID hợp lệ được giữ nguyên; thiếu/invalid correlation tiếp tục
theo convention existing filter: cấp UUID mới.

`BillingDiscoveryGatewayWebTest` dùng actual Gateway HTTP/JWT/filter chain + simple service
discovery tới JDK HttpServer **downstream double**, không phải Billing thật. 22 cases:
ADMIN/CASHIER, ba purposes; giữ raw query, Authorization, signed identity thay forged headers,
correlation; missing/malformed/wrong-signature/expired JWT; tất cả forbidden human roles và
SYSTEM; service/refresh kể cả service giả ADMIN/CASHIER; wrong POST/PUT/PATCH/DELETE/HEAD/OPTIONS;
extra/trailing/nested/unknown GET paths. Mỗi denial assert forwarded count = 0. Missing/repeated/
malformed/unknown query vẫn forward để Billing validate. Double trả 400 `INVALID_REQUEST`,
404 `BILLING_PAYMENT_REQUEST_NOT_FOUND`, 503 `BILLING_LEDGER_UNAVAILABLE`: assert exact body,
HTTP status và correlation giữ nguyên. Existing ADMIN/CASHIER payment POST giữ body/path/method.
Đây chỉ là routing/security/pass-through evidence, không chứng minh Billing storage/business.

Reproduce từ repository root trên Windows PowerShell, Maven 3.9.16 dùng Java **21.0.11**
(`JAVA_HOME=C:\Program Files\Java\jdk-21.0.11`; `java` trên PATH riêng là 24 nên dùng Maven đã
verify bằng `mvn -version`). Tests dùng test-only key trong annotation, random Gateway/downstream
ports, `eureka.client.enabled=false`, `spring.cloud.gateway.discovery.locator.enabled=false`,
`spring.cloud.discovery.client.simple.instances.billing-service[0].uri=http://127.0.0.1:<random>`.
Không cần PG/Rabbit/Docker cho suite Gateway; không bật Billing/deployment flags.

| Command | Fresh result |
|---|---|
| `node scripts/changelog.js --summary` | Sandbox EPERM khi spawn cmd; chạy lại ngoài sandbox: thiếu sqlite3. `node scripts/changelog.js --limit 5` chạy thành công; đối chiếu thêm `git log -5 --oneline`. |
| `mvn -q -pl backend/gateway -am '-Dtest=BillingDiscoveryGatewayWebTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` trước production rule | RED: 22 tests, 10 failures, 0 errors/skips. 8 cases thiếu rule GET, 2 cases test helper đọc body HEAD; đã sửa helper theo HTTP HEAD semantics. Lần gọi đầu thiếu quote Maven property trong PowerShell bị CLI parse error, không phải test run. |
| `mvn -q -pl backend/gateway -am test` | Exit 0; Gateway **256 tests, 0 failures, 0 errors, 0 skips**, gồm discovery **22/22**. Parent/common reactor không có test reports; không cộng test lịch sử/Billing vào số này. |
| Targeted command ở trên sau khi chỉnh payment test body mirror live DTO | Exit 0; **22 tests, 0 failures/errors/skips**. |
| `git diff --check` | Exit 0. |

Logs local (ignored): `backend/gateway/target/g1-gateway-full.log`,
`backend/gateway/target/g1-discovery-final.log`, `backend/gateway/target/billing-discovery-red.log`
(Bearer values đã redact); XML fresh ở `backend/gateway/target/surefire-reports/TEST-*.xml`.

**Actual public discovery/payment: NOT RUN, không có source/request/account/transaction/
clearance/event IDs hoặc hashes mới để báo cáo.** Không có listener trên cổng Gateway/Billing/
business thông thường khi kiểm tra local; `docker` không có trong PATH và executable Docker
Desktop mặc định không tồn tại. Chưa có endpoint/môi trường test hoặc approved fresh source tuple,
catalog và account từ Lộc. Đã hỏi người dùng cung cấp thông tin phối hợp; chưa nhắn chat khác
khi chưa có explicit authorization. Không dùng golden fixture UUIDs/historical IDs hay SQL để
giả discovery, không bật deployment gates để thay nghiệm thu.

Lộc cần giao môi trường isolated, flags/profile đang chạy (ledger/discovery, issuer theo purpose,
publication/cohort/dispatcher khi nghiệm thu grant), catalog/priceCode thực và **request mới đủ
điều kiện** từ actual producer: `purpose/sourceId/patientId/careEpisodeId`, cách login ADMIN/CASHIER.
Không suy ID hoặc tự cấu hình giá/expiry. Bộ request chuẩn bị tại
`backend/gateway/billing-discovery.http`: public ADMIN/CASHIER GET → dùng duy nhất request ID/
currency/remainingAmount từ response để POST payment → GET lại cùng tuple; giữ operation key
khi retry cùng payment. Điền biến privately, không commit token/PII. Nếu async chưa issue, chỉ
bounded retry GET 404; 503 là unavailable, không coi là không có phí.

Khi chạy được, ghi cấu hình thực, source/request/account/transaction/clearance IDs, correlation,
HTTP/error/status trước/sau thanh toán và reference raw broker bytes do Lộc cung cấp vào đây.
Chỉ sau actual smoke mới tick dòng cuối G1. Consumer V-CLOSE-03, public Lab-order/Core fix,
Lộc L3/L4 và toàn MVP không được đóng bởi bộ Gateway tests này.

### G1 actual Billing / Docker refresh — 2026-10-16, 23:38 Asia/Bangkok

**Actual EXAM happy path PASS; tổng smoke FAIL với 1 contract failure, 0 skipped. G1 OPEN.**
Người dùng đã chuẩn bị Docker và yêu cầu chạy test. Source vẫn
`da99a906ffd410f1ab8a9698b1103e8649ca7a38` + Gateway working-tree diff; production rule blob
`f16c6958dfe7ec3c3400cc81471f2b4758a20653`. Không sửa Billing/Clinical/shared config,
không SQL tạo hoặc tìm charge/request/payment, không fixture source event/grant. Root Compose
không dùng để khởi động business apps vì còn topology legacy; test chạy JAR mới build.

**Cấu hình isolated:** Docker Desktop 4.48.0, Engine 28.5.1; Java 21.0.11 / Maven 3.9.16.
Hai container riêng, bind loopback: `mediflow-g1-postgres` (`postgres:16-alpine`, image SHA-256
`721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`, port 15432),
`mediflow-g1-rabbit` (`rabbitmq:3.13-management-alpine`, image SHA-256
`606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281`, AMQP 15672,
management 15673). Fresh databases organization/patient/clinical/billing tạo bằng `createdb`,
schema do actual Flyway chạy. SQL không được dùng cho business data/discovery.

Core 18089, Clinical 18082, Billing 18086, Gateway 18080. `eureka.client.enabled=false`;
simple discovery ánh xạ các service names tới các JAR thật trên loopback, Gateway vẫn dùng
existing `lb://billing-service`. Đây không phải Eureka registry acceptance. Bootstrap ADMIN
signed token chỉ dùng tạo staff/accounts qua API theo packaged acceptance hiện có; ADMIN/CASHIER/
DOCTOR dùng cho smoke đều login thật qua Gateway → Core. Test credentials chỉ trong harness,
không xuất tokens vào evidence/handoff.

Clinical dùng profile `outpatient-mvp`, care-finance-v2=true cho EXAM API/clearance, admission
referral=false theo profile. Billing bật ledger/discovery/EXAM issuer, outpatient-mvp=true,
publication=true **chỉ EXAM**, acceptance-reference=`G1-local-da99a906`, outbox dispatcher mặc định
true; deferred và các issuer khác không bật. Catalog test lấy đúng owner fixture trong
`OutpatientPublicationRabbitIntegrationTest`: `OUTPATIENT_EXAM`, description `Kham ngoai tru`,
unitAmount `100000.00`, không dùng V0 default-exam-fee để suy giá V1. Chỉ cấu hình process test
isolated, không thay deployment gates/config.

| Command | Kết quả fresh |
|---|---|
| `mvn -q -pl backend/core-service,backend/clinical-service,backend/billing-service,backend/gateway,backend/eureka-server -am -DskipTests package` | Exit 0; đóng gói JAR từ source hiện tại. Đây là package với tests skipped, không tính là test pass. Eureka JAR không dùng trong slice này. |
| `mvn -q -pl backend/gateway -am test` | Exit 0, **256 tests, 0 failures/errors/skips**, discovery 22/22. Log `backend/gateway/target/g1-gateway-docker-refresh.log`. |
| `pwsh -File backend/gateway/src/test/scripts/g1-live-smoke.ps1 -Phase Start` | Bốn actual apps healthy. Docker named pipe cần chạy tool ngoài sandbox; thêm Docker resources/bin vào PATH cho credential helper. |
| `pwsh -File backend/gateway/src/test/scripts/g1-live-smoke.ps1 -Phase Smoke` | Final run exit **1** vì **1 body-correlation contract failure**. **23 captured non-login HTTP responses** có đúng status/header correlation; **22/23** có đúng body correlation; **17 independent business/error/broker assertions PASS**, 0 skipped. Ba actual logins + direct Billing diagnostic + bounded discovery polling không cộng vào 23. |

Harness nằm hoàn toàn trong Gateway; mỗi Smoke tạo source mới, không purge/reset data. Hai lần
đầu sửa lỗi harness (POST create trả 201; collector phải match clearance theo appointment/episode,
không yêu cầu clearance chứa paymentRequestId). Không tính các lượt dừng sớm thành nghiệm thu.
Lỗi Billing 404 được giữ là failure, harness tiếp tục kiểm tra payment/503 độc lập rồi exit 1.

**Final actual source và result:** Clinical API tạo appointment rồi check-in
`AWAITING_PAYMENT`, actual durable event tới Billing issuer. Source/episode/appointment
`195939d9-3efd-4653-82ab-21359437020a`; patient `10e073bd-3696-4a79-8514-443f5a5f1bcd`;
department `e981f3a8-2374-4f59-9b3e-d2f2e1e90e99`. ADMIN và CASHIER public GET cùng trả request
`bffc5472-26f5-497d-bdc8-06a812c6a724`, account `6c866957-1181-4357-8138-7aea6f2454a9`,
invoice `3b2bd788-92a4-4baf-a30a-4400bf122c14`, PENDING/remaining 100000.00 VND.
POST CASHIER trả transaction `0015f8d0-31da-46d6-90c9-a5bd852b494d`, COMPLETED; retry cùng
idempotency key trả cùng transaction. GET readback: PAID, paidAmount 100000.00, remainingAmount 0.
Payment correlation `ca754f06-79cd-4976-8181-d9ff8ef17a84`.

Observer queue riêng `g1-evidence` bound exchange `mediflow.events`, không inject grant và không
đụng consumer queue. Nhận actual dispatcher events cho exact request/target:

| Event | ID / SHA-256 raw bytes nhận từ Rabbit |
|---|---|
| `payment.completed` | `3e81f718-8be4-42c7-932f-fa7ad3b12701` / `d21b1312d68c0670dceecb1f0ade4a26612be0f63f99a6b0868c90b6f7127632` |
| `financial.clearance.granted` | `7bf23b50-ad7a-458e-80ab-f2741302ef4b` / `e4b24e262b505ff7c4cf609f9b2821fa6bc98295edcf7a1d86ac209a5ca32455`; clearance `7be20610-839f-4d3e-bd28-7cfe4d2f466e` |

Đây là actual broker receipt proof trong slice G1; không claim stored-outbox-byte equality,
consumer care effects, full L3 hoặc V-CLOSE-03. LAB_TEST/PRESCRIPTION actual smoke chưa chạy.

Validation malformed UUID: 400 `INVALID_REQUEST`, correlation giữ. Missing token 401,
DOCTOR 403, refresh 401, SYSTEM/service 403. Sau payment dừng riêng PostgreSQL test:
GET trả **503 `BILLING_LEDGER_UNAVAILABLE`** với header/body correlation
`639c0691-3945-47ba-91a8-819436eb625f`; restart PG → cùng request vẫn PAID.

**HANDOFF cho Lộc — lỗi 404 correlation:** gửi wrong patient expectation qua Gateway với
correlation `28ca4af3-feb4-4bae-bd91-18ede2c6a3b9`: HTTP 404,
`error.code=BILLING_PAYMENT_REQUEST_NOT_FOUND`, header Gateway giữ đúng correlation nhưng
`body.correlationId=null`. Gọi trực tiếp Billing cùng query/token/header: vẫn 404, cùng error,
body correlation null, không có response correlation header. Live
`GlobalExceptionHandler.notFound()` gọi `build()` → `ApiResponse.fail(error)` không truyền
request correlation. Gateway không sửa response body để che lỗi downstream.
Lộc cần sửa trong Billing và thêm regression cho not-found discovery; acceptance: direct và
Gateway 404 giữ supplied UUID ở response header/body, không thay error/status hoặc reveal wrong
patient result. Sau fix chạy lại actual smoke, không tạo lại route hoặc relax Gateway guards.

Evidence local ignored ở `backend/gateway/target/g1-live/`: `result.json`, `http-evidence.json`,
`direct-billing-404.json`, `broker-evidence.json`, `<eventId>.json` raw bytes và process logs;
summary log `backend/gateway/target/g1-live-smoke.log`. Không lưu login tokens trong evidence.
Để tái lập từ containers fresh, thêm `C:\Program Files\Docker\Docker\resources\bin` vào PATH,
chạy `docker run -d --name mediflow-g1-postgres -e POSTGRES_PASSWORD=g1-local-test -p 127.0.0.1:15432:5432 postgres:16-alpine`
và `docker run -d --name mediflow-g1-rabbit -p 127.0.0.1:15672:5672 -p 127.0.0.1:15673:15672 rabbitmq:3.13-management-alpine`,
đợi infra ready rồi package/Start/Smoke như trên. `Start` tạo fresh DB và observer queue;
`Smoke` rerun được trên apps đang chạy với source mới; `Stop` dừng đúng các JAR test, không xóa DB.
Không xóa containers/volumes hiện có để chạy lại. G1 chưa tick đóng, không push/merge hoặc kết luận MVP.
Sau lượt chạy đã dừng bốn JAR và hai container test riêng; dữ liệu và artifacts giữ nguyên.
