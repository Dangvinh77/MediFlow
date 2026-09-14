# Kế hoạch hoàn thiện Pharmacy Service theo Clean/Hexagonal Architecture

> Cập nhật: **13/09/2026**
> Module: `backend/pharmacy-service`
> Nhánh triển khai: `Huy`
> Phạm vi ghi code: `backend/pharmacy-service/**` và tài liệu/handoff được người dùng giao rõ ràng.

## 0. Mục tiêu và cách sử dụng plan

Mục tiêu cuối cùng là hoàn thiện toàn bộ phần code còn lại của Pharmacy Service theo thứ tự:

1. Hiểu và giữ đúng **Clean Architecture/Hexagonal Architecture**.
2. Xác định business rule trước khi viết implementation.
3. Chia mỗi feature thành các task có phạm vi nhỏ, đầu ra và gate rõ ràng.
4. Triển khai lần lượt từng task theo vòng lặp **rule → test → code → refactor → audit**.
5. Chỉ đánh dấu task `DONE` khi code, Javadocs, test và audit đều đạt.

Trạng thái dùng trong tài liệu:

- `DONE`: đã có code và bằng chứng kiểm thử.
- `IN_PROGRESS`: đang triển khai.
- `TODO`: chưa bắt đầu.
- `BLOCKED`: thiếu contract/quyết định từ service khác; không được tự suy đoán.

Baseline kiểm chứng gần nhất:

| Nội dung | Kết quả |
|---|---|
| Test Pharmacy Service | **181 total, 149 passed, 0 failures, 0 errors, 32 skipped** (Testcontainers cần Docker; integration gate chưa xác nhận) |
| PostgreSQL integration | Chưa chạy ở lượt này vì Docker daemon không khả dụng; baseline trước đó đã chạy Flyway V1–V5 thành công |
| Javadocs | Thành công |
| Maven verify | Thành công |
| Git diff check | Thành công |
| Audit chất lượng đợt gần nhất | GO, không còn blocker trong phạm vi đã sửa |

### 0.1 Nguồn chuẩn và thứ tự ưu tiên

| Ưu tiên | Nguồn | Dùng để quyết định |
|---:|---|---|
| 1 | [Thiết kế nghiệp vụ Pharmacy](../eproject_general_plan/pharmacy-service.html) | Pharmacy phải làm gì |
| 2 | [Backend spec Pharmacy](../eproject_general_plan/backend-spec/05-pharmacy.md) | DDL, DTO, port, thuật toán, event và rule-test map |
| 3 | [Shared backend contracts](../eproject_general_plan/backend-spec/00-overview.md) | Envelope, type, naming và contract chung |
| 4 | [AI golden rules](../ai/README.md) | Quy tắc triển khai bắt buộc |
| 5 | [System architecture](../ai/01-architecture.md) | Boundary service, REST/event và saga |
| 6 | [Microservice blueprint](../ai/04-microservice-blueprint.md) | Package/layer/dependency direction |
| 7 | [API conventions](../ai/05-api-conventions.md) | URL, status, DTO và response envelope |
| 8 | [RabbitMQ conventions](../ai/06-events-rabbitmq.md) | Event envelope, queue, DLQ và idempotency |
| 9 | [RBAC](../ai/07-security-rbac.md) | JWT, role và default deny |
| 10 | [Testing](../ai/09-testing.md) | Tầng test và acceptance bar |
| 11 | Code/migration/test đang chạy | Bằng chứng hiện trạng, không tự thay thế business spec |

Khi tài liệu mâu thuẫn:

- Design/spec quyết định **what**.
- `docs/ai` quyết định **how**.
- Migration đã deploy là lịch sử bất biến; giải quyết chênh lệch bằng migration mới.
- Contract cross-service chưa rõ phải được xác nhận bằng fixture/handoff, không dựa vào suy đoán.

### 0.2 Phạm vi và non-goals

Trong phạm vi:

- Drug catalog và stock quantity hiện tại.
- Prescription, prescription lines và price snapshot.
- Stock reservation và TTL.
- Dispense lifecycle.
- Payment proof/receipt cho saga Pharmacy.
- Cancel/expire/late-payment compensation.
- Transactional outbox và Pharmacy Rabbit consumer.
- Pharmacy API, security, persistence, scheduling, tests và module docs.

Ngoài phạm vi nếu chưa có yêu cầu riêng:

- Supplier, purchase order, batch/lot, FEFO, nhiều kho.
- Trả thuốc sau khi đã cấp, thu hồi thuốc hoặc kiểm kê toàn bệnh viện.
- Sửa Billing, Organization, Gateway, Notification hoặc Report production code.
- Frontend/mobile.
- Cross-service database query, shared entity hoặc distributed transaction.
- API vận hành/replay công khai chưa có security spec.

### 0.3 Definition of Ready cho một task

Một task chỉ được chuyển từ TODO sang IN_PROGRESS khi:

- [ ] Business rule và expected outcome đã được ghi rõ.
- [ ] Task owner và phạm vi file không giao nhau với thay đổi đang chạy khác.
- [ ] In-port/out-port/aggregate chịu trách nhiệm đã xác định.
- [ ] Contract ngoài service đã có fixture hoặc task không phụ thuộc contract đó.
- [ ] Migration number đã kiểm tra sau fetch nếu task đổi schema.
- [ ] Test thất bại cần viết đã được đặt tên và chọn đúng tầng.
- [ ] Rollback/recovery path đã được mô tả nếu task đổi transaction hoặc messaging.
- [ ] Worktree và file ngoài phạm vi đã được ghi nhận để bảo toàn.

## 1. Kiến trúc bắt buộc

### 1.1 Quy tắc phụ thuộc

```text
Driving adapters                         Core                         Driven adapters

web/controller ─┐                                             ┌─ persistence/JPA
Rabbit consumer ├─> application/port/in -> application/service ├─ messaging/outbox/RabbitMQ
scheduler ──────┘                       -> domain              └─ resilient REST client

infrastructure  ───────────────────────> application ────────> domain
```

Mọi dependency chỉ hướng vào trong:

- `domain`: Java thuần, chứa model, value object, state transition và invariant. Không Spring, JPA, AMQP, HTTP hoặc I/O.
- `application/port/in`: hợp đồng use case mà controller, consumer và scheduler được phép gọi.
- `application/port/out`: hợp đồng mà use case cần từ database, event publisher hoặc service ngoài.
- `application/service`: điều phối use case, transaction, gọi domain và port; không biết JPA/RabbitTemplate/HTTP.
- `web`, `messaging/consumer`, `infrastructure/scheduling`: driving adapter; chỉ chuyển input thành command rồi gọi in-port.
- `infrastructure/persistence`, `infrastructure/messaging`, `infrastructure/client`: driven adapter; hiện thực out-port.

### 1.2 Quy tắc thiết kế code

1. Một use case có một trách nhiệm chính; không tiếp tục mở rộng god service.
2. Controller/consumer/scheduler không chứa business rule và không gọi repository.
3. Application không import `jakarta.persistence`, Spring Data, AMQP hoặc Web.
4. Domain model và JPA entity là hai model riêng; business rule không nằm trong entity.
5. DTO/event là `record` khi phù hợp; không trả JPA entity qua boundary.
6. Thay đổi trạng thái phải ghi event intent qua out-port; adapter outbox đảm bảo phát bền.
7. Tiền dùng `BigDecimal`; ID dùng `UUID`; thời gian dùng `Clock`, `Instant`, `LocalDate`.
8. Public class, public constructor, public use-case method và rule không hiển nhiên phải có Javadocs.
9. Mỗi business rule phải có ít nhất một test ở tầng thấp nhất có thể chứng minh rule đó.
10. Không truy cập database service khác; liên kết ngoài Pharmacy chỉ là UUID.

### 1.3 Ranh giới transaction và thứ tự khóa

- Create prescription: khóa drug theo `drugId` tăng dần → tính available → lưu prescription, lines, reservations, pending slip và outbox trong một transaction.
- Dispense thành công: khóa prescription → dispense slip → drug theo `drugId` tăng dần → reservation theo cùng thứ tự → cập nhật tất cả lifecycle và outbox trong một transaction.
- Dispense thất bại nghiệp vụ: transaction cấp phải rollback hoàn toàn; sau đó bean khác ghi FAILED/release/compensation bằng transaction `REQUIRES_NEW`.
- Cancel/expire: khóa prescription → slip → reservations; kiểm tra lại trạng thái sau khi khóa.
- Payment consumer không giữ một transaction bao ngoài hai transaction success/failure.
- Không gọi REST hoặc chờ RabbitMQ trong khi giữ khóa database.
- Outbox row được ghi cùng transaction nghiệp vụ; dispatcher gửi sau commit.

## 2. Danh mục business rules

### 2.1 Quy tắc cốt lõi từ đặc tả

| ID | Business rule | Trạng thái |
|---|---|---|
| BR-D1 | Không xuất khi tồn vật lý nhỏ hơn số lượng yêu cầu. | DONE |
| BR-D2 | Không xuất thuốc đã hết hạn tại ngày nghiệp vụ. | DONE |
| BR-D3 | Tạo đơn phải tạo đúng một phiếu xuất `PENDING`. | DONE |
| BR-D4 | Mỗi đơn chỉ được trừ kho đúng một lần. | DONE |
| BR-D5 | Tổng tiền bằng tổng `quantity × unitPrice`, scale 2, HALF_UP. | DONE |
| BR-D6 | Xuất thất bại nghiệp vụ phải tạo compensation event bền. | DONE |
| BR-D7 | Giá được snapshot khi kê; đổi giá thuốc không đổi đơn cũ. | DONE |
| BR-D8 | Client không được quyết định giá hoặc tổng tiền. | DONE |
| BR-D9 | Redelivery `payment.completed` không tạo hiệu ứng nghiệp vụ lặp. | DONE theo eventId; còn business-key ở BR-P3 |
| BR-D10 | Tồn kho không âm khi hai luồng chạy đồng thời. | DONE với PostgreSQL concurrency test |
| BR-D11 | Tồn chạm ngưỡng phải tạo `stock.low`. | DONE |
| BR-D12 | Lỗi ở dòng thuốc sau phải rollback dòng trước nhưng vẫn lưu phiếu FAILED. | DONE với transaction integration test |

### 2.2 Quy tắc mở rộng cần hoàn tất

| ID | Business rule | Trạng thái |
|---|---|---|
| BR-R1 | Kê đơn giữ tồn khả dụng nhưng không trừ tồn vật lý. | DONE |
| BR-R2 | `available = onHand - tổng RESERVED`; điều chỉnh kho không được phá lượng đã giữ. | DONE |
| BR-R3 | Tập reservation phải khớp chính xác drug và quantity của đơn trước dispense/cancel/expire. | DONE |
| BR-R4 | Reservation chỉ chuyển trạng thái một lần; TTL dùng điều kiện `expiresAt <= now`. | DONE |
| BR-L1 | `FULFILLED/CANCELLED/EXPIRED/DISPENSE_FAILED` là terminal; không ghi đè kết quả thắng race. | IN_PROGRESS — unit đã có, integration concurrency cần Docker |
| BR-P1 | Cấp thuốc thủ công chỉ hợp lệ khi Pharmacy có payment proof bền, không tin cờ từ client. | TODO |
| BR-P2 | Payment event phải khớp prescription, patient và department trước mutation. | DONE |
| BR-P3 | Chống trùng theo eventId và business key thanh toán đã thống nhất với Billing. | PARTIAL |
| BR-P4 | Payment đến sau cancel/expire tạo compensation đúng một lần và giữ nguyên terminal state. | DONE theo eventId |
| BR-P5 | Crash giữa receipt, dispense và terminal claim phải có thể resume an toàn. | TODO |
| BR-E1 | Event intent phải commit/rollback cùng thay đổi nghiệp vụ. | DONE |
| BR-E2 | Chỉ đánh dấu outbox published sau publisher ACK và không bị returned. | DONE |
| BR-E3 | Nhiều instance dispatcher không gửi vượt thứ tự/giành cùng row ngoài semantics at-least-once. | TODO |
| BR-E4 | Retry outbox có backoff, quan sát được và có thao tác replay an toàn. | TODO |
| BR-A1 | Điều chỉnh kho phải lưu audit before/after/delta/reason/actor/correlation cùng transaction. | PARTIAL: event có, bảng audit chưa có |
| BR-S1 | Mọi endpoint có role; actor và correlation lấy từ context đã xác thực. | DONE ở endpoint hiện có |
| BR-S2 | Account identity, staff identity và system actor không được đồng nhất bằng UUID giả. | TODO, cần contract Organization/Gateway |
| BR-M1 | Migration mới chỉ được thêm; fresh DB và nâng từ V4/V5 có dữ liệu đều phải thành công. | PARTIAL |

## 3. Bản đồ feature và tiến độ

Phần trăm dưới đây tính theo trọng số gate nghiệp vụ, không tính theo số file hoặc số dòng.

| Feature | Trọng số | Đã đạt | Còn lại |
|---|---:|---:|---|
| Kiến trúc và phân tách use case | 10% | 10% | Gia cố architecture tests và xóa compatibility code |
| Danh mục thuốc và an toàn tồn kho | 10% | 9% | Race/reconciliation adjustment với reservation |
| Kê đơn, snapshot giá và reservation | 15% | 13% | Race/reconciliation dữ liệu legacy |
| Dispense và failure transaction | 15% | 14% | Payment outcome atomic, crash integration |
| Payment receipt/idempotency/recovery | 15% | 13% | Business key Billing, crash integration/recovery matrix |
| Event/outbox/RabbitMQ | 15% | 13% | Real broker/lease integration và failure classification sâu |
| Cancel/expire/scheduler | 10% | 7% | Concurrency matrix, scheduler đa instance, reconciliation |
| API, security và identity | 5% | 4% | Phân biệt account/staff/system actor |
| Migration, E2E và release gate | 5% | 2% | Upgrade test, Rabbit integration, Gateway/Billing E2E |
| **Tổng ước tính theo gate nghiêm ngặt** | **100%** | **85%** | **15%** |

## 4. Thứ tự task triển khai phần code còn lại

| Thứ tự | Task | Feature | Phụ thuộc | Trạng thái |
|---:|---|---|---|---|
| 01 | Tách application service theo từng in-port | Architecture | — | DONE |
| 02 | Gia cố architecture tests và xóa compatibility code | Architecture | T01 | DONE |
| 03 | Chốt identity account/staff/system actor | Security/Identity | Contract Gateway/Organization | BLOCKED — chờ producer claim |
| 04 | Tạo domain model và state machine payment receipt | Payment | Contract Billing hiện tại | DONE (business-key còn chờ Billing) |
| 05 | Thêm payment receipt port, adapter và migration V6 | Payment/Persistence | T04 | PARTIAL (business-key còn chờ Billing) |
| 06 | Viết payment workflow có thể resume sau crash | Payment/Dispense | T01, T05 | IN_PROGRESS — còn crash integration |
| 07 | Chặn endpoint dispense thủ công bằng payment proof | API/Payment | T03, T06 | DONE |
| 08 | Kiểm thử idempotency và race của payment/manual dispense | Payment/Test | T06, T07 | PARTIAL — đã bổ sung recovery/terminal/race tests; business-key còn chờ Billing |
| 09 | Claim/lease outbox an toàn cho nhiều instance, migration V7 | Outbox | — | DONE — code + V7 + PostgreSQL multi-instance/lease test |
| 10 | Backoff, metrics, replay và retention cho outbox | Outbox/Ops | T09 | DONE — unit + PostgreSQL repository integration; RabbitMQ thật thuộc T15 |
| 11 | Lưu stock-adjustment audit, migration V8 | Inventory/Audit | T01 | DONE — domain/persistence, rollback và race integration đã pass |
| 12 | Hoàn tất concurrency matrix cancel/expire/dispense/adjust | Lifecycle/Test | T01, T11 | IN_PROGRESS — guards + concurrent tests đã viết; integration gate cần Docker |
| 13 | Scheduler đa instance và reconciliation dữ liệu lệch | Lifecycle/Ops | T09, T12 | IN_PROGRESS — fencing token + dry-run matrix đã viết; PostgreSQL lease/reconciliation gate cần Docker |
| 14 | Test migration fresh DB và upgrade V4/V5 có dữ liệu | Migration | T05, T09, T11 | IN_PROGRESS — đã thêm Flyway compatibility test + runbook; cần chạy Docker để xác nhận |
| 15 | Test RabbitMQ thật: confirm, return, outage, redelivery, DLQ | Messaging/Test | T10 | IN_PROGRESS — real broker ACK/mandatory-return tests added; Docker gate pending |
| 16 | Contract/E2E với Gateway, Billing và Notification | Integration | T03–T15 | IN_PROGRESS — fixtures + HANDOFF added; E2E blocked by external services |
| 17 | Audit cuối, tài liệu, cập nhật plan và release/PR gate | Release | T01–T16 | TODO |

Nếu T03 hoặc T04 bị chặn bởi contract bên ngoài, tiếp tục các task độc lập T09 → T11 → T12 → T14 → T15; không tạo field, claim hoặc endpoint giả để lách dependency.

## 5. Task cards

### T01 — Tách application service theo feature

**Mục tiêu:** loại bỏ god service nhưng không thay đổi hành vi.

Tách `PharmacyApplicationService` thành:

- `DrugApplicationService implements ManageDrugUseCase`
- `PrescriptionApplicationService implements CreatePrescriptionUseCase, GetPrescriptionUseCase`
- `DispenseApplicationService implements DispensePrescriptionUseCase`
- `PaymentApplicationService implements ReactToPaymentUseCase`

Giữ các transaction service chuyên biệt đã có:

- `DispenseTransactionService`
- `RecordDispenseFailureService`
- `LatePaymentCompensationService`
- `CancelPrescriptionService`
- `ExpirePrescriptionTransaction`

Checklist:

- [x] Di chuyển logic theo use case, không copy.
- [x] Constructor injection, không self-injection, không dependency nullable.
- [x] Controller/consumer chỉ phụ thuộc đúng in-port.
- [x] Test hiện tại được chia theo service mới và giữ nguyên assertion.
- [x] Thêm Javadocs cho mọi public API.
- [x] 181 test hiện tại pass (0 failure, 0 error; 32 skipped do môi trường Docker).

**Bằng chứng hoàn thành:** `mvn -q -pl backend/pharmacy-service -am test` và Javadocs đã chạy thành công; `PharmacyApplicationService` đã được xóa, bốn application service mới nhận đúng in-port và các test đã chuyển sang service chuyên trách.

### T02 — Architecture gate

- [x] Mở rộng ArchUnit để cấm framework trong domain.
- [x] Cấm application import infrastructure, Spring Data, AMQP và Web.
- [x] Cấm controller/consumer gọi JPA repository hoặc driven adapter.
- [x] Cấm JPA entity xuất hiện trong chữ ký in-port/DTO.
- [x] Xóa constructor compatibility, dead helper và import thừa sau T01.
- [x] Chạy architecture test riêng rồi toàn module.

**Bằng chứng hoàn thành:** `ArchitectureTest` chạy riêng thành công với các rule bổ sung; toàn bộ module cũng pass trong lượt regression.

### T03 — Identity và actor contract

- [x] Đối chiếu JWT claims thật từ Gateway/Organization.
- [x] Phân biệt `accountId`, `staffId`, role và system actor trong application command.
- [ ] Nếu cần lookup, khai báo `StaffIdentityLookupPort`; adapter REST phải timeout/circuit-breaker/fallback. *(Chưa cần lookup trong phạm vi này.)*
- [x] Không so account UUID với doctor/staff UUID nếu contract không xác nhận chúng giống nhau.
- [x] Không dùng UUID toàn số 0 để giả nhân viên.
- [x] Test account khác staff, thiếu claim, sai role, Admin override và dependency unavailable.
- [x] Nếu producer contract thiếu, tạo HANDOFF; không sửa Organization/Gateway.

**Trạng thái:** BLOCKED ở phần end-to-end cho tới khi Gateway phát claim `staffId` theo [identity handoff](../eproject_general_plan/backend-spec/pharmacy-identity-contract-handoff.md).

**Bằng chứng phần đã hoàn thành:** `ActorIdentity` tách account/staff/role; JWT filter fail-closed với
subject hoặc `staffId` sai định dạng; prescription/cancel ownership chỉ dùng `staffId`; test account
khác staff, thiếu claim và malformed claim đều pass.

### T04 — Payment receipt domain

Tạo domain model/value type sau khi chốt contract Billing:

- `PaymentReceipt`
- `PaymentReceiptStatus` tối thiểu: `RECEIVED`, `DISPENSED`, `COMPENSATED`
- Transition methods phải idempotent và từ chối chuyển trạng thái ngược.

Receipt giữ dữ liệu có nguồn đáng tin: eventId, invoiceId, prescriptionId, patientId, departmentId, payment identity/time nếu contract có, status, failure code và timestamps.

Test domain:

- [x] Receipt mới ở `RECEIVED`.
- [x] Mark dispensed/compensated chỉ một lần (lặp lại cùng outcome là no-op).
- [x] Terminal state không bị ghi đè.
- [ ] Cùng invoice/prescription nhưng payload xung đột bị phát hiện — chờ Billing chốt business key/attempt policy.

### T05 — Payment receipt persistence và migration V6

- [x] Khai báo `PaymentReceiptRepositoryPort` với kết quả `CLAIMED`/`DUPLICATE_SAME`/`DUPLICATE_CONFLICT`.
- [x] Thêm JPA entity, mapper thủ công, repository và persistence adapter.
- [x] Migration V6 thêm bảng receipt; không sửa V1–V5.
- [ ] Unique `event_id` đã có; unique business key đúng contract Billing — chờ Billing chốt key.
- [x] Query claim dùng thao tác atomic `INSERT ... ON CONFLICT`; không dùng `exists → save`.
- [x] Có PostgreSQL integration tests (tự skip khi Docker không chạy).
- [x] Test fresh insert, duplicate eventId và payload conflict; duplicate business key để sau khi contract chốt.

### T06 — Payment workflow có thể resume

- [x] Consumer parse/validate rồi gọi duy nhất `ReactToPaymentUseCase`.
- [x] Ghi/claim receipt trước khi xử lý nhưng không coi `RECEIVED` là terminal.
- [x] Redelivery gặp receipt `RECEIVED` phải tiếp tục xử lý.
- [x] Nhánh success cập nhật dispense lifecycle, receipt terminal, processed claim và outbox nhất quán.
- [x] Nhánh business failure rollback stock rồi ghi failure lifecycle, receipt terminal, processed claim và compensation outbox trong transaction mới.
- [x] Lỗi hạ tầng giữ receipt ở trạng thái có thể retry và không ACK sai.
- [ ] Crash ở mọi điểm giữa các transaction đều resume mà không trừ kho/gửi compensation logic hai lần.

**Trạng thái:** IN_PROGRESS — workflow production và các nhánh idempotency đã hoàn thành; còn
integration test mô phỏng crash giữa các transaction (T08).

**Bằng chứng phần đã hoàn thành:** `PaymentApplicationService` claim receipt atomic trước dispense,
resume receipt `RECEIVED`, từ chối payload conflict, chốt `DISPENSED`/`COMPENSATED` và chỉ claim
`payment.completed` sau outcome. `PaymentApplicationServiceTest` bao phủ success, redelivery,
payload conflict, context mismatch, business failure và late payment.

### T07 — Payment gate cho dispense thủ công

- [x] Endpoint `PUT /prescriptions/{id}/dispense` không nhận `paid=true` từ client.
- [x] Application tra payment proof qua port trước mutation.
- [x] Chưa có proof trả lỗi nghiệp vụ ổn định, không trừ kho.
- [x] ADMIN cũng không được bỏ qua payment gate.
- [x] Actor/correlation lấy từ JWT/request context.
- [x] Manual và consumer dùng chung core transaction.
- [x] Cập nhật `pharmacy.http`, README và contract Swagger runtime.

### T08 — Payment/manual concurrency tests

- [x] Cùng eventId chạy đồng thời chỉ một outcome (per-event lock + bounded executor test).
- [ ] Hai eventId cho cùng business payment theo policy đã chốt.
- [x] Manual dispense và consumer chạy đồng thời chỉ trừ kho một lần (PostgreSQL test có barrier/latch).
- [x] Payment đến sau CANCELLED/EXPIRED compensation một lần.
- [x] Payment đến sau DISPENSED không tạo filled event mới.
- [x] Context patient/department sai không ghi receipt terminal hoặc đổi stock.
- [x] Các test concurrency dùng barrier/latch và timeout hữu hạn.

### T09 — Outbox đa instance và migration V7

- [x] Thêm trạng thái/lease cần thiết: `available_at`, `locked_at`, `locked_by`.
- [x] Claim batch bằng transaction ngắn và `FOR UPDATE SKIP LOCKED`.
- [x] Không giữ transaction database trong lúc chờ publisher confirm.
- [x] Lease hết hạn cho phép instance khác lấy lại (query reclaim).
- [x] EventId và payload không thay đổi qua retry.
- [x] Một instance không làm event của instance khác bị đánh dấu published (owner-guarded update).
- [x] Migration V7 chỉ thêm mới và có index cho pending/available rows.

### T10 — Outbox retry, metrics, replay và retention

- [x] Exponential backoff có giới hạn và cấu hình ngoài code.
- [x] Phân biệt NACK, unroutable return, timeout và serialization error qua lỗi confirm/return ổn định.
- [x] Micrometer metrics: pending count, oldest age, published, retry.
- [x] Log eventId/routing key nhưng không log token hoặc PII/payload đầy đủ.
- [x] Có thao tác replay an toàn cho row lỗi, không tạo eventId mới.
- [x] Có retention/cleanup cho row đã publish; không xóa pending.
- [x] Unit tests cho lease/backoff/replay/retention và metrics.
- [x] PostgreSQL integration tests đầy đủ cho lease/backoff/replay.

### T11 — Stock adjustment audit và migration V8

- [x] Tạo domain model `StockAdjustment`.
- [x] Lưu drug mutation và audit trong cùng transaction.
- [x] Audit gồm drugId, before, delta, after, reason, actor, correlationId, occurredAt.
- [x] Reason bắt buộc khi giảm; được trim và giới hạn chiều dài.
- [x] `stock.adjusted` outbox commit/rollback cùng audit.
- [x] Migration V8 thêm bảng/index/check constraint.
- [x] Test rollback audit/event khi mutation lỗi.
- [x] Test race adjust/create reservation.

### T12 — Lifecycle concurrency matrix

Chạy bằng Spring Boot + PostgreSQL thật:

- [x] cancel vs dispense
- [x] expire vs dispense
- [x] cancel vs expire
- [x] adjust stock vs create prescription (T11 PostgreSQL race coverage)
- [x] hai đơn tranh cùng drug
- [x] reservation thiếu/dư/sai quantity

Gate: chỉ một transition hợp lệ thắng; không stock âm, không release hai lần, không outbox logical duplicate và không deadlock vượt timeout.

Evidence: `PrescriptionLifecycleConcurrencyTest` chạy PostgreSQL thật với barrier và timeout hữu hạn;
`CancelPrescriptionServiceTest`/`ExpirePrescriptionTransactionTest` kiểm tra reservation quantity
mismatch và expiry logical event id; `StockAdjustmentIntegrationTest` kiểm tra adjust/create race.

### T13 — Scheduler đa instance và reconciliation

- [x] Claim candidate theo cursor/lease để nhiều scheduler không xử lý cùng aggregate.
- [x] Poison aggregate không gây starvation cho aggregate phía sau.
- [x] Expiry event logical chỉ phát một lần.
- [x] Viết reconciliation chế độ dry-run cho lifecycle/reservation legacy lệch.
- [x] Không tự sửa dữ liệu không xác định; báo prescriptionId và loại bất thường.
- [x] Test hơn một batch, wrap cursor, hai scheduler và một aggregate lỗi.

Evidence: migration `V10__pharmacy_scheduler_cursor_lease.sql`, PostgreSQL lease integration test,
`ReleaseExpiredReservationsLeaseTest`/`ReleaseExpiredReservationsServiceTest`, and
`LifecycleReconciliationPersistenceIntegrationTest`. Reconciliation has no mutation port and
reports unknown/legacy statuses by prescription id and anomaly type.

### T14 — Migration compatibility

- [x] Có test fresh database chạy V1 → migration mới nhất (`PharmacyMigrationCompatibilityTest`).
- [x] Có test database V4 có dữ liệu mẫu nâng lên migration mới nhất, không backfill payment proof.
- [ ] `ddl-auto=validate` pass.
- [ ] Unique/index/check/FK nội bộ đúng; không FK cross-service.
- [ ] Dữ liệu cũ thiếu payment proof giữ trạng thái unknown, không backfill thành paid.
- [x] Rollback/backup note và migration runbook đã cập nhật tại `backend/pharmacy-service/docs/migration-runbook.md`.

### T15 — RabbitMQ integration thật

Dùng Testcontainers PostgreSQL + RabbitMQ:

- [ ] Transaction rollback không để lại outbox.
- [x] ACK mới mark published (`PharmacyRabbitIntegrationTest`).
- [x] Mandatory return giữ pending và tăng attempt (`PharmacyRabbitIntegrationTest`).
- [ ] NACK/timeout, outage, crash-redelivery và DLQ cần chạy với broker/container trong CI.
- [ ] Broker outage không làm rollback nghiệp vụ đã commit.
- [ ] Crash sau send trước mark tạo redelivery nhưng consumer vẫn idempotent.
- [ ] Poison payment message retry hữu hạn rồi vào DLQ.
- [ ] Không test các hành vi này chỉ bằng mock `RabbitTemplate`.

### T16 — Contract và E2E

- [x] JSON fixtures `payment.completed`, `prescription.created`, `filled`, `dispense.failed` đã thêm và có test deserialize.
- [ ] E2E qua Gateway: create prescription → invoice → payment → dispense.
- [ ] E2E nhánh compensation.
- [ ] E2E redelivery và broker restart.
- [ ] Notification/report consumer compatibility được ghi nhận.
- [x] Dependency chưa có được ghi tại `docs/HANDOFF-pharmacy-t16-cross-service.md`; chưa tuyên bố E2E pass.

### T17 — Release gate

- [ ] Cập nhật checkbox và bằng chứng trong file plan sau từng task.
- [ ] Chạy toàn bộ test module, integration và migration.
- [ ] Chạy Javadocs, verify, architecture test và `git diff --check`.
- [ ] Audit theo `docs/ai/` và spec Pharmacy; báo Blocker/Should-fix/Nit.
- [ ] Không còn blocker hoặc skipped test quan trọng.
- [ ] Human review trước merge.
- [ ] Commit theo Conventional Commits, không stage file ngoài phạm vi.
- [ ] Chỉ sau audit đạt mới push nhánh `Huy` và cập nhật/tạo pull request khi người dùng yêu cầu.

## 6. Quy trình bắt buộc cho từng task

### Trước khi code

1. Đọc changelog, AGENTS, architecture rule và phần spec liên quan.
2. Kiểm tra worktree; giữ nguyên thay đổi không thuộc task.
3. Ghi rõ business rule và acceptance test của task.
4. Xác định in-port, out-port, domain behavior và adapter cần thiết.
5. Nếu contract ngoài service chưa rõ, chuyển task sang BLOCKED và làm task độc lập tiếp theo.

### Trong khi code

1. Viết/chỉnh test business rule trước.
2. Implement domain behavior.
3. Implement application port và use-case service.
4. Implement persistence/messaging/client adapter.
5. Implement controller/consumer/scheduler cuối cùng.
6. Thêm Javadocs ngay trong cùng task.
7. Không mở rộng sang service khác hoặc refactor ngoài feature.

### Sau khi code

```powershell
mvn -q -pl backend/pharmacy-service -am test
mvn -q -pl backend/pharmacy-service -am javadoc:javadoc
mvn -q -pl backend/pharmacy-service -am verify
git diff --check
git status --short
```

Sau đó:

1. Audit dependency direction, business rules, transaction/lock, RBAC, event/outbox và migration.
2. Sửa hết blocker; chạy lại gate bị ảnh hưởng.
3. Cập nhật task trong plan: trạng thái, test count, migration và SHA nếu có.
4. Commit tập trung theo task; không commit file ngoài phạm vi.
5. Push/PR chỉ thực hiện sau audit và theo yêu cầu Git hiện hành của người dùng.

## 7. Definition of Done toàn Pharmacy Service

- [ ] `PharmacyApplicationService` đã được tách; mỗi use case có trách nhiệm rõ.
- [ ] Clean/Hexagonal dependency được ArchUnit bảo vệ.
- [ ] BR-D1–BR-D12 và toàn bộ rule mở rộng có test.
- [ ] Manual dispense bắt buộc có payment proof.
- [ ] Payment receipt chống duplicate event/business payment và resume sau crash.
- [ ] Dispense/cancel/expire/adjust concurrency cho kết quả nhất quán.
- [ ] Outbox an toàn đa instance, có backoff, metrics, replay và retention.
- [ ] Stock adjustment có audit persistence cùng transaction.
- [ ] Identity account/staff/system actor đúng contract.
- [ ] Fresh/upgrade migrations pass trên PostgreSQL thật.
- [ ] Rabbit confirm/return/outage/redelivery/DLQ pass trên RabbitMQ thật.
- [ ] API/RBAC/error envelope/`.http`/OpenAPI đúng contract.
- [ ] E2E qua Gateway và Billing pass hoặc dependency chưa có được công bố rõ.
- [ ] Javadocs, test, verify, audit và human review đều đạt.
- [ ] Plan được cập nhật bằng bằng chứng thực tế; không suy diễn phần trăm từ số file.

## 8. Kiến trúc đích chi tiết theo code

### 8.1 Hiện trạng cần giữ

Các thành phần sau đã đúng hướng Hexagonal và phải được giữ khi refactor:

| Nhóm | Thành phần hiện có | Vai trò |
|---|---|---|
| Domain | `Drug`, `Prescription`, `PrescriptionLine`, `DispenseSlip`, `StockReservation` | Aggregate/model chứa invariant |
| In-port | 7 interface trong `application/port/in` | Hợp đồng cho web, consumer và scheduler |
| Out-port | 6 interface trong `application/port/out` | Persistence và event intent |
| Driving HTTP | `DrugController`, `PrescriptionController` | Validate/authenticate/map request |
| Driving event | `PaymentCompletedConsumer` | Map AMQP payload sang application command |
| Driving scheduler | `ReservationExpiryScheduler` | Kích hoạt use case hết TTL |
| Driven persistence | 5 persistence adapter và các JPA repository/entity | Lưu aggregate, lock và query |
| Driven messaging | `PharmacyEventPublisherAdapter` | Serialize và ghi transactional outbox |
| Outbox delivery | `PharmacyOutboxDispatcher` | Gửi RabbitMQ sau commit, chờ publisher confirm |
| Transaction specialist | `DispenseTransactionService`, `RecordDispenseFailureService`, `ExpirePrescriptionTransaction` | Ranh giới transaction quan trọng |

### 8.2 Vấn đề kiến trúc còn lại

| ID | Hiện trạng | Tác động | Task xử lý |
|---|---|---|---|
| AR-01 | `PharmacyApplicationService` hiện thực 5 in-port và giữ quá nhiều dependency | Khó cô lập rule, dễ tạo transaction boundary sai | T01 |
| AR-02 | Có constructor tương thích tự tạo service con và dependency nullable | Bỏ qua Spring proxy, che giấu wiring lỗi | T01–T02 |
| AR-03 | Payment idempotency mới lưu terminal eventId, chưa có receipt state machine | Không resume rõ ràng sau crash | T04–T08 |
| AR-04 | Manual dispense gọi core nhưng chưa có payment-proof port | Có thể cấp thuốc trước thanh toán | T07 |
| AR-05 | Outbox polling chưa có claim/lease đa instance | Hai replica có thể cùng gửi một row | T09 |
| AR-06 | Stock adjustment chỉ có event audit, chưa có audit record bền | Khó điều tra nếu consumer/event gặp sự cố | T11 |
| AR-07 | Account, staff và system actor chưa có model contract rõ | Audit/ownership có nguy cơ sai danh tính | T03 |

### 8.3 Cây package đích

Tên file mới dưới đây là tên dự kiến chính thức. Nếu implementation phát hiện tên chưa diễn đạt đúng
nghiệp vụ thì được đổi trong cùng task, nhưng trách nhiệm lớp không được nhập lại thành god service.

```text
com.mediflow.pharmacy/
├── domain/
│   ├── model/
│   │   ├── Drug.java
│   │   ├── Prescription.java
│   │   ├── PrescriptionLine.java
│   │   ├── DispenseSlip.java
│   │   ├── StockReservation.java
│   │   ├── PaymentReceipt.java                    # T04
│   │   └── StockAdjustment.java                   # T11
│   ├── model/enums/
│   │   ├── DispenseStatus.java
│   │   ├── PrescriptionStatus.java
│   │   ├── ReservationStatus.java
│   │   ├── ReservationReleaseReason.java
│   │   └── PaymentReceiptStatus.java              # T04
│   └── exception/
│       ├── PaymentReceiptRuleException.java       # T04
│       ├── PaymentProofRequiredException.java     # T07
│       └── ... existing typed exceptions
├── application/
│   ├── dto/command/
│   │   ├── AuthenticatedActor.java                # T03
│   │   ├── CreatePrescriptionCommand.java
│   │   ├── CancelPrescriptionCommand.java
│   │   ├── DispensePrescriptionCommand.java       # T03/T07
│   │   └── PaymentCompletedCommand.java
│   ├── port/in/                                   # existing contracts, refine only when needed
│   ├── port/out/
│   │   ├── PaymentReceiptRepositoryPort.java      # T05
│   │   ├── StockAdjustmentRepositoryPort.java     # T11
│   │   ├── StaffIdentityLookupPort.java           # T03, only if contract requires lookup
│   │   └── ... existing repository/event ports
│   └── service/
│       ├── DrugApplicationService.java            # T01
│       ├── PrescriptionApplicationService.java    # T01
│       ├── DispenseApplicationService.java        # T01
│       ├── PaymentApplicationService.java         # T01/T06
│       ├── DispenseTransactionService.java
│       ├── RecordDispenseFailureService.java
│       ├── LatePaymentCompensationService.java
│       ├── CancelPrescriptionService.java
│       ├── ReleaseExpiredReservationsService.java
│       └── ExpirePrescriptionTransaction.java
├── web/                                           # driving HTTP adapters
├── messaging/consumer/                            # driving AMQP adapters
└── infrastructure/
    ├── persistence/
    │   ├── adapter/
    │   │   ├── PaymentReceiptPersistenceAdapter.java     # T05
    │   │   └── StockAdjustmentPersistenceAdapter.java    # T11
    │   ├── jpaEntity/
    │   │   ├── PaymentReceiptJpaEntity.java               # T05
    │   │   └── StockAdjustmentJpaEntity.java              # T11
    │   └── repository/
    │       ├── PaymentReceiptJpaRepository.java           # T05
    │       └── StockAdjustmentJpaRepository.java          # T11
    ├── messaging/
    │   ├── PharmacyEventPublisherAdapter.java
    │   ├── PharmacyOutboxDispatcher.java
    │   └── PharmacyOutboxClaimRepository.java             # T09, infrastructure-only
    ├── client/                                             # T03 only if REST mapping is approved
    ├── scheduling/
    ├── security/
    └── config/
```

### 8.4 Quyết định kiến trúc cố định

| ADR | Quyết định | Lý do |
|---|---|---|
| ADR-PH-01 | Outbox là chi tiết của messaging adapter, không tạo `OutboxPort` cho application | Application chỉ nói “publish event”; không biết cơ chế giao |
| ADR-PH-02 | Payment receipt là domain/application concept, khác `PROCESSED_EVENT` | Receipt biểu diễn bằng chứng và outcome; processed-event chỉ là dedupe terminal |
| ADR-PH-03 | Security principal được chuyển thành application command/value, không truyền `Authentication` vào application | Giữ application độc lập Spring Security |
| ADR-PH-04 | `Clock` được inject ở application/config, domain nhận thời điểm làm tham số | Test boundary thời gian deterministically |
| ADR-PH-05 | Core dispense dùng chung cho consumer và manual endpoint | Ngăn hai implementation trừ kho khác nhau |
| ADR-PH-06 | Business failure và infrastructure failure có đường xử lý khác nhau | Không tự compensation do timeout/deadlock tạm thời |
| ADR-PH-07 | Lock order là contract kỹ thuật của toàn service | Ngăn deadlock giữa dispense/cancel/expire/adjust |
| ADR-PH-08 | Không thêm API admin replay công khai nếu chưa có security/operations spec | Tránh tạo bề mặt tấn công; ưu tiên internal operation/runbook |

## 9. Aggregate, invariant và transaction map

### 9.1 Aggregate ownership

| Aggregate/record | Sở hữu dữ liệu | Invariant chính | Không được làm |
|---|---|---|---|
| `Drug` | Giá, tồn vật lý, hạn dùng, ngưỡng | Giá/tồn/ngưỡng hợp lệ; không overflow; không xuất thuốc hết hạn | Không tự đọc reservation hoặc publish event |
| `Prescription` + lines | Ý định kê, giá snapshot, tổng tiền, lifecycle | Có dòng; drug không trùng; tổng chính xác; terminal không đảo ngược | Không tham chiếu JPA entity hoặc Billing invoice entity |
| `DispenseSlip` | Bằng chứng cấp thuốc | Một phiếu/đơn; transition hợp lệ; failure reason bounded | Không tự trừ stock |
| `StockReservation` | Phần tồn đã giữ | Quantity dương; RESERVED chỉ chuyển một lần | Không thay stock vật lý |
| `PaymentReceipt` | Payment proof và outcome cục bộ | Dedupe; context khớp; terminal không ghi đè; resume RECEIVED | Không coi receipt RECEIVED là đã xử lý xong |
| `StockAdjustment` | Audit bất biến | before + delta = after; actor/reason/correlation đầy đủ | Không dùng làm current stock |
| Outbox row | Delivery state hạ tầng | Payload/eventId ổn định; published chỉ sau ACK | Không chứa business transition |

### 9.2 Transaction matrix

| Use case | Transaction | Khóa/claim | Ghi dữ liệu | Event intent |
|---|---|---|---|---|
| Create drug | 1 transaction | Không cần cross-row lock | DRUG | Không bắt buộc nếu contract không yêu cầu |
| Adjust stock | 1 transaction | Drug → đọc tổng RESERVED | DRUG + STOCK_ADJUSTMENT | stock.adjusted, có thể stock.low |
| Create prescription | 1 transaction | Drugs sorted | PRESCRIPTION + LINE + RESERVATION + SLIP | prescription.created |
| Read prescription | read-only | Không khóa ghi | Không ghi | Không |
| Cancel | 1 transaction | Prescription → slip → reservations | Lifecycle + release audit | prescription.cancelled |
| Expire | 1 transaction/aggregate | Prescription → slip → reservations | Lifecycle + expiry audit | prescription.expired |
| Dispense success | 1 transaction | Prescription → slip → drugs sorted → reservations sorted | Drug + reservation + prescription + slip + receipt/processed nếu payment-driven | filled + stock.low |
| Dispense business failure | transaction cấp rollback, rồi 1 `REQUIRES_NEW` | Khóa lại prescription → slip → reservations | FAILED/released + receipt terminal/processed | dispense.failed |
| Payment arrival | Orchestrator không có transaction dài | Atomic receipt claim; sau đó gọi transaction specialist | Receipt RECEIVED rồi terminal | Qua transaction success/failure |
| Outbox dispatch | Claim ngắn; network ngoài DB transaction; finalize ngắn | Lease/owner token | attempts, publishedAt, lastError | Gửi RabbitMQ |

### 9.3 Lock-order checklist bắt buộc

Trước khi thêm query `FOR UPDATE`, reviewer phải trả lời đủ:

- [ ] Aggregate nào được khóa trước?
- [ ] Có đường code khác khóa các row tương tự theo thứ tự ngược không?
- [ ] Danh sách UUID đã sort ổn định chưa?
- [ ] Có I/O mạng hoặc publisher confirm bên trong transaction không?
- [ ] Nếu process chết khi đang giữ lease/lock, hệ thống tự phục hồi thế nào?
- [ ] Timeout/deadlock được coi là infrastructure failure và còn khả năng retry chưa?

## 10. Port contract map

### 10.1 In-port hiện tại và owner đích

| In-port | Driving adapter | Service đích sau T01 | Transaction owner |
|---|---|---|---|
| `ManageDrugUseCase` | `DrugController` | `DrugApplicationService` | Service method |
| `CreatePrescriptionUseCase` | `PrescriptionController` | `PrescriptionApplicationService` | Create method |
| `GetPrescriptionUseCase` | `PrescriptionController` | `PrescriptionApplicationService` | Read-only method |
| `DispensePrescriptionUseCase` | `PrescriptionController`, payment application | `DispenseApplicationService` | Transaction specialist |
| `ReactToPaymentUseCase` | `PaymentCompletedConsumer` | `PaymentApplicationService` | Resume-safe orchestrator |
| `CancelPrescriptionUseCase` | `PrescriptionController` | `CancelPrescriptionService` | Service method |
| `ReleaseExpiredReservationsUseCase` | `ReservationExpiryScheduler` | `ReleaseExpiredReservationsService` | Một transaction/aggregate |

### 10.2 Out-port hiện tại

| Out-port | Adapter | Aggregate/use case sử dụng |
|---|---|---|
| `DrugRepositoryPort` | `DrugPersistenceAdapter` | Drug, prescription create, dispense, adjustment |
| `PrescriptionRepositoryPort` | `PrescriptionPersistenceAdapter` | Create/read/cancel/expire/dispense/payment validation |
| `DispenseSlipRepositoryPort` | `DispenseSlipPersistenceAdapter` | Create/cancel/expire/dispense |
| `StockReservationRepositoryPort` | `StockReservationPersistenceAdapter` | Available stock, create, lifecycle, scheduler |
| `ProcessedEventPort` | `ProcessedEventPersistenceAdapter` | Terminal payment-event dedupe |
| `PharmacyEventPublisherPort` | `PharmacyEventPublisherAdapter` | Ghi event intent vào outbox |

### 10.3 Out-port mới dự kiến

| Port | Trách nhiệm | Kết quả cần biểu diễn |
|---|---|---|
| `PaymentReceiptRepositoryPort` | Claim receipt, đọc proof, lưu terminal outcome | Claimed, duplicate-same, duplicate-conflict; không chỉ boolean |
| `StockAdjustmentRepositoryPort` | Lưu audit stock bất biến | Audit đã persist cùng transaction |
| `StaffIdentityLookupPort` | Map account → staff nếu JWT không có claim được duyệt | Found, not-found, dependency-unavailable |

Nguyên tắc chữ ký port:

- Không dùng `Pageable`, JPA entity, `Authentication`, AMQP `Message` hoặc HTTP response.
- Kết quả nhiều trạng thái dùng enum/record có nghĩa; không dùng `null` hoặc boolean mơ hồ.
- Phương thức khóa ghi đặt tên `...ForUpdate`; application yêu cầu semantics, adapter chọn kỹ thuật.
- Port mới phải có Javadocs mô tả consistency/idempotency contract.

## 11. Dữ liệu và migration plan

### 11.1 Schema hiện có

| Migration | Nội dung | Trạng thái |
|---|---|---|
| V1 | DRUG, PRESCRIPTION, LINE, DISPENSE_SLIP, PROCESSED_EVENT | Đã áp dụng |
| V2 | STOCK_RESERVATION | Đã áp dụng |
| V3 | Unique prescription/drug reservation | Đã áp dụng |
| V4 | Prescription lifecycle và reservation audit | Đã áp dụng |
| V5 | PHARMACY_EVENT_OUTBOX | Đã áp dụng và test fresh DB |

Không được sửa V1–V5. Số migration tiếp theo phải được kiểm tra lại sau `git fetch`; V6–V8 dưới
đây là thứ tự logic hiện tại, không được giữ số nếu master đã chiếm số đó.

### 11.2 V6 dự kiến — PAYMENT_RECEIPT

| Cột | Kiểu/constraint | Ý nghĩa |
|---|---|---|
| receipt_id | UUID PK | ID nội bộ |
| event_id | UUID UNIQUE NOT NULL | Dedupe broker delivery |
| invoice_id | UUID NOT NULL | Billing reference |
| prescription_id | UUID NOT NULL | Pharmacy aggregate |
| patient_id | UUID NOT NULL | Context validation |
| department_id | UUID NOT NULL | Context/report |
| total_amount | DECIMAL(15,2) NOT NULL | Giá trị từ Billing; không mặc định bằng tiền thuốc |
| payment_method | VARCHAR | Theo contract Billing |
| payment_occurred_at | TIMESTAMPTZ NOT NULL | Thời điểm event |
| correlation_id | VARCHAR NOT NULL | Trace xuyên saga |
| payload_fingerprint | VARCHAR | Phát hiện cùng key nhưng payload khác |
| status | VARCHAR NOT NULL | RECEIVED/DISPENSED/COMPENSATED |
| failure_code | VARCHAR NULL | Mã outcome thất bại |
| created_at/updated_at | TIMESTAMPTZ | Audit |

Index tối thiểu: `event_id`, `prescription_id + status`, business key được Billing xác nhận.
Không đặt FK đến BILLING/PATIENT/ORGANIZATION.

### 11.3 V7 dự kiến — outbox lease/retry

| Cột | Kiểu | Mục đích |
|---|---|---|
| available_at | TIMESTAMPTZ | Backoff; chỉ claim khi đến hạn |
| locked_at | TIMESTAMPTZ | Bắt đầu lease |
| locked_by | VARCHAR | Instance owner |
| lock_token | UUID | Chống owner cũ finalize row |
| published_at | TIMESTAMPTZ hiện có | Terminal delivery success |
| attempts/last_error | hiện có | Quan sát retry |

Index cần phục vụ `published_at IS NULL AND available_at <= now`. Query/plan phải được kiểm tra
bằng PostgreSQL thật; không giả định H2 có semantics tương đương.

### 11.4 V8 dự kiến — STOCK_ADJUSTMENT

| Cột | Kiểu/constraint |
|---|---|
| adjustment_id | UUID PK |
| drug_id | UUID NOT NULL, FK nội bộ DRUG |
| quantity_before | INT NOT NULL CHECK >= 0 |
| delta | INT NOT NULL CHECK <> 0 |
| quantity_after | INT NOT NULL CHECK >= 0 |
| reason | VARCHAR(500) NOT NULL |
| actor_id | UUID NOT NULL sau khi T03 chốt contract |
| correlation_id | VARCHAR |
| occurred_at | TIMESTAMPTZ NOT NULL |

Application/domain vẫn kiểm tra `before + delta = after`; DB constraint là tuyến phòng thủ cuối.

### 11.5 Migration acceptance

- [ ] Checksum V1–V5 không đổi.
- [ ] Fresh database migrate đến latest.
- [ ] V4 sample data → latest.
- [ ] V5 outbox pending/published sample → latest.
- [ ] Duplicate data tạo lỗi dễ hiểu trước khi thêm unique, hoặc có migration reconciliation rõ.
- [ ] `ddl-auto=validate` pass.
- [ ] Index được dùng cho query chính bằng `EXPLAIN` khi dữ liệu đủ lớn.
- [ ] Không seed payment proof giả cho dữ liệu cũ.
- [ ] Có hướng dẫn backup/rollback; migration destructive cần review riêng.

## 12. API, security và error contract

### 12.1 Endpoint matrix

| Method/path | Role | In-port | Success | Test bắt buộc |
|---|---|---|---|---|
| GET `/drugs` | ADMIN/DOCTOR/PHARMACIST | ManageDrug | 200 page envelope | auth, page bounds, keyword |
| GET `/drugs/{id}` | ADMIN/DOCTOR/PHARMACIST | ManageDrug | 200 | 404, malformed UUID |
| POST `/drugs` | ADMIN/PHARMACIST | ManageDrug | 201 + Location | validation, role |
| PUT `/drugs/{id}/stock` | ADMIN/PHARMACIST | ManageDrug | 200 | actor, reason, reserved guard |
| POST `/prescriptions` | ADMIN/DOCTOR | CreatePrescription | 201 + Location | ownership, duplicate line, atomicity |
| GET `/prescriptions/{id}` | ADMIN/DOCTOR/PHARMACIST | GetPrescription | 200 | snapshot, lifecycle, 404 |
| PUT `/prescriptions/{id}/cancel` | ADMIN/DOCTOR | CancelPrescription | 200 | ownership, idempotency, terminal |
| PUT `/prescriptions/{id}/dispense` | ADMIN/PHARMACIST | DispensePrescription | 200 | payment proof, concurrency, actor |

### 12.2 Error matrix đích

| Nhóm | HTTP | Mã ví dụ |
|---|---:|---|
| Request/JSON/validation sai | 400 | VALIDATION_ERROR, INVALID_UUID |
| Thiếu hoặc token không hợp lệ | 401 | UNAUTHORIZED |
| Role/ownership/identity không đủ | 403 | FORBIDDEN, PRESCRIPTION_CREATE_FORBIDDEN |
| Resource không tồn tại | 404 | DRUG_NOT_FOUND, PRESCRIPTION_NOT_FOUND, DISPENSE_NOT_FOUND |
| Duplicate resource/business key | 409 | PAYMENT_RECEIPT_CONFLICT |
| Business rule | 422 | DRUG_OUT_OF_STOCK, DRUG_EXPIRED, DISPENSE_NOT_PAID |
| Dependency đồng bộ lỗi | 503 | IDENTITY_SERVICE_UNAVAILABLE |
| Lỗi bất ngờ | 500 | INTERNAL_ERROR, không lộ stack trace |

Mọi response lỗi phải giữ correlationId. Controller không catch `Exception` để tự trả status; dùng
typed exception và `GlobalExceptionHandler`.

### 12.3 Identity acceptance

- JWT thiếu/invalid → 401.
- JWT hợp lệ nhưng role sai → 403.
- Subject không parse được theo contract → 403, không 500.
- Doctor không được kê/hủy bằng staffId khác.
- Admin override vẫn lưu actor thật và target doctor riêng.
- System-triggered dispense phải có biểu diễn actor được contract hóa, không dùng nhân viên giả.
- Không log token hoặc dữ liệu bệnh nhân đầy đủ.

## 13. Event, idempotency và delivery contract

### 13.1 Outbound event matrix

| Routing key | Business trigger | Idempotency key | Trường bắt buộc |
|---|---|---|---|
| prescription.created | Aggregate kê đơn commit | eventId | envelope, prescription/patient/record/department, total, items |
| prescription.filled | Dispense success commit | eventId | envelope, prescription/patient/department, total, dispensedItems |
| prescription.dispense.failed | Failure outcome commit | eventId | prescription, invoice nếu payment-driven, patient, reason, failedItems |
| prescription.cancelled | Cancel commit | eventId | prescription, actor/reason/released items theo contract |
| prescription.expired | Expiry commit | eventId/deterministic logical key | prescription, expiry/released context |
| stock.low | Stock mutation commit | eventId | drug, name, currentStock, threshold |
| stock.adjusted | Manual adjustment commit | eventId | drug, before/after/delta/reason/actor |

### 13.2 Inbound payment contract hiện tại

`PaymentCompletedEvent`/`PaymentCompletedCommand` hiện có:

- `eventId: UUID`
- `occurredAt: Instant`
- `correlationId: String`
- `invoiceId: UUID`
- `patientId: UUID`
- `departmentId: UUID`
- `prescriptionId: UUID`
- `totalAmount: BigDecimal`
- `paymentMethod: String`

Các câu hỏi phải chốt với Billing trước T04/T05:

1. Một invoice có thể chứa nhiều prescription không?
2. Một prescription có thể có nhiều payment attempt/thanh toán bổ sung không?
3. Business key chính xác là invoiceId, paymentId hay tổ hợp nào?
4. `totalAmount` là tổng invoice hay phần thuốc?
5. Payment reversal/refund có event riêng không?
6. Field nào nullable và quy tắc tương thích payload cũ?

### 13.3 Delivery semantics

- Outbox cung cấp **at-least-once**, không tuyên bố exactly-once.
- Publisher confirm chứng minh broker nhận message, không chứng minh consumer đã xử lý.
- Crash sau send/trước mark published có thể gửi lại cùng eventId.
- Consumer dedupe terminal theo eventId và kiểm tra business key/payload conflict.
- Không sinh eventId mới khi retry/replay cùng logical event.
- Lỗi serialization phải rollback transaction tạo event intent.
- Lỗi broker sau business commit giữ row pending, không rollback nghiệp vụ đã commit.

## 14. Traceability: rule → code → test → task

### 14.1 Core BR-D1–BR-D12

| Rule | Code chịu trách nhiệm | Bằng chứng hiện tại | Test cần bổ sung | Task |
|---|---|---|---|---|
| BR-D1 | Drug + DispenseTransactionService | Unit dispense/Drug | two prescriptions same drug | T12 |
| BR-D2 | Drug.isExpiredOn/dispenseStock | DrugTest + dispense tests | timezone/business-date integration | T12 |
| BR-D3 | Prescription create transaction | Application + rollback integration | — | DONE |
| BR-D4 | Slip/prescription locks + idempotent return | Unit + concurrent same prescription | manual vs consumer | T08 |
| BR-D5 | Prescription/line money calculation | PrescriptionTest | max precision/overflow DB | T14 |
| BR-D6 | RecordDispenseFailureService + outbox | Unit failure/outbox | Rabbit delivery integration | T15 |
| BR-D7 | PrescriptionLine snapshot | Persistence test | upgrade-data preservation | T14 |
| BR-D8 | Request DTO/controller | Web test | OpenAPI schema assertion | T17 |
| BR-D9 | ProcessedEvent + payment workflow | Unit event redelivery | concurrent/business-key/crash | T08 |
| BR-D10 | Pessimistic locks + sorted UUID | PostgreSQL concurrency | cross-use-case race/deadlock | T12 |
| BR-D11 | StockLowEvent + outbox | Unit | broker outage delivery | T15 |
| BR-D12 | Separate transaction beans | PostgreSQL rollback test | receipt terminal atomicity | T06/T08 |

### 14.2 Extended rules

| Rule | Test class/method đích | Tầng | Task |
|---|---|---|---|
| BR-P1 | `PrescriptionControllerTest.dispense_withoutPaymentProof_returns422` | Web/application | T07 |
| BR-P3 | `PaymentReceiptPersistenceAdapterTest.claim_sameBusinessKeyDifferentPayload_reportsConflict` | PostgreSQL | T05 |
| BR-P5 | `PaymentRecoveryIntegrationTest.receivedReceipt_redeliveryResumesDispense` | Integration | T08 |
| BR-E3 | `PharmacyOutboxLeaseIntegrationTest.twoDispatchers_eachRowOwnedOncePerLease` | PostgreSQL | T09 |
| BR-E4 | `PharmacyOutboxDispatcherTest.nack_schedulesExponentialBackoff` | Unit | T10 |
| BR-A1 | `StockAdjustmentIntegrationTest.adjustStock_eventFailure_rollsBackDrugAndAudit` | Integration | T11 |
| BR-L1 | `PrescriptionLifecycleConcurrencyTest.cancelVsDispense_singleWinner` | PostgreSQL | T12 |
| BR-S2 | `ActorIdentityTest.accountAndStaffRemainDistinct` | Unit/web | T03 |
| BR-M1 | `PharmacyMigrationTest.upgradeFromV5_preservesRows` | PostgreSQL/Flyway | T14 |

### 14.3 Test suite đích

| Tầng | Mục tiêu | Không được thay bằng |
|---|---|---|
| Domain unit | State transition, invariant, money/time | Spring context |
| Application unit | Use case với mock out-port | Mock JPA repository |
| Web slice | Validation, envelope, role, actor/correlation mapping | Unit controller gọi trực tiếp |
| Persistence slice | Mapping, constraint, lock/query | H2 |
| Integration PostgreSQL | Transaction rollback và concurrency thật | Mockito |
| Integration RabbitMQ | Confirm/return/outage/redelivery/DLQ | Mock RabbitTemplate |
| Contract test | JSON fixture tương thích service owner | Payload tự bịa |
| Architecture test | Dependency direction/cycle/boundary | Review thủ công duy nhất |

Mỗi integration test đồng thời phải có timeout hữu hạn, cleanup deterministic và không phụ thuộc
thứ tự chạy test.

## 15. File-impact manifest theo task

| Task | Production files chính | Test files chính | Migration/config |
|---|---|---|---|
| T01 | 4 application services mới; xóa dần PharmacyApplicationService | Chia 4 test class cũ theo service | — |
| T02 | Xóa compatibility constructors/dead code | ArchitectureTest | — |
| T03 | AuthenticatedActor, dispense command, optional identity port/client | ActorIdentityTest, controller/security tests | timeout/circuit-breaker nếu có client |
| T04 | PaymentReceipt, PaymentReceiptStatus, typed exception | PaymentReceiptTest | — |
| T05 | Receipt port/entity/repository/mapper/adapter | Receipt persistence tests | V6 |
| T06 | PaymentApplicationService + success/failure transaction integration | PaymentWorkflowIntegrationTest | — |
| T07 | Dispense in-port/service/controller contract | Controller + application tests | pharmacy.http/OpenAPI |
| T08 | Không thêm logic nếu test lộ lỗi; sửa đúng owner lớp | PaymentRecovery/Lifecycle tests | — |
| T09 | Outbox claim repository/entity/dispatcher | Outbox lease integration | V7 |
| T10 | Dispatcher policy + metrics/maintenance components | Retry/replay/retention tests | application.yml/README |
| T11 | StockAdjustment domain/port/persistence + drug service | Domain/application/integration tests | V8 |
| T12 | Lock/query changes tối thiểu nếu race test fail | `PrescriptionLifecycleConcurrencyTest`, `ExpirePrescriptionTransactionTest` | DB timeout test config |
| T13 | Scheduler claim/reconciliation components | `ReleaseExpiredReservationsLeaseTest`, `PharmacySchedulerLeasePersistenceIntegrationTest`, `LifecycleReconciliationPersistenceIntegrationTest` | V10/V11; scheduler properties |
| T14 | Không sửa migration cũ; test fixtures | PharmacyMigrationTest | migration test resources |
| T15 | Rabbit topology/dispatcher/consumer nếu test lộ lỗi | PharmacyRabbitIntegrationTest | Rabbit Testcontainer config |
| T16 | Chỉ Pharmacy contract adapters/fixtures; service khác read-only | E2E/contract tests | HANDOFF docs |
| T17 | Javadocs/docs/HTTP examples | Full regression | Release notes/plan |

## 16. Dependency graph và milestone

### 16.1 Dependency graph

```text
T01 ──> T02
 │
 ├──────────────> T11 ──> T12 ──> T13
 │
 └─> T04 ──> T05 ──> T06 ──> T07 ──> T08
                       ▲       ▲
T03 ───────────────────┘───────┘

T09 ──> T10 ──> T15
 │              ▲
 └──────> T13   │

T05 + T09 + T11 ──> T14
T03..T15 ─────────> T16 ──> T17
```

### 16.2 Lanes có thể tiếp tục khi dependency ngoài bị chặn

| Lane | Chuỗi task | Có thể chạy độc lập với |
|---|---|---|
| A — Architecture | T01 → T02 | Mọi contract ngoài |
| B — Payment | T04 → T05 → T06 → T07 → T08 | Bị chặn ở contract Billing/Identity |
| C — Outbox | T09 → T10 → T15 | Identity |
| D — Inventory/lifecycle | T11 → T12 → T13 | Billing, trừ test manual/payment |
| E — Release | T14 → T16 → T17 | Chờ các lane trước hoàn thành |

Không triển khai song song hai task cùng sửa một transaction owner hoặc cùng migration number.

### 16.3 Milestone và mức tăng dự kiến

| Milestone | Task | Gate | Điểm hoàn thành tích lũy mục tiêu |
|---|---|---|---:|
| M1 — Application boundaries | T01–T02 | Architecture + regression pass | 71% |
| M2 — Identity/payment proof | T03–T08 | Payment recovery/manual gate pass | 80% |
| M3 — Delivery/inventory reliability | T09–T13 | Multi-instance/outbox/audit/race pass | 91% |
| M4 — Production verification | T14–T16 | Migration + Rabbit + E2E pass | 98% |
| M5 — Release ready | T17 | Audit/human review/PR gate | 100% |

Các phần trăm là trọng số acceptance criteria. Nếu task code xong nhưng integration test bị skip,
điểm tương ứng chưa được cộng.

## 17. Risk register và phương án kiểm soát

| Risk | Dấu hiệu kích hoạt | Mức | Kiểm soát | Chặn task |
|---|---|---:|---|---|
| R-01 Billing business key chưa rõ | Không xác định duplicate payment khác eventId | Cao | Contract fixture + HANDOFF, không tự chọn key | T04–T08 |
| R-02 Account/staff contract chưa rõ | JWT chỉ có subject account | Cao | T03, lookup port hoặc claim đã duyệt | T03/T07/T11 |
| R-03 Migration number bị master chiếm | Fetch xuất hiện V6/V7/V8 mới | Cao | Đánh số lại trước commit, không sửa migration đã áp dụng | T05/T09/T11 |
| R-04 Outbox gửi trùng | Crash sau send trước mark | Bình thường | Giữ eventId, consumer idempotent, metric redelivery | Không chặn |
| R-05 Deadlock | Lock timeout/SQLState deadlock | Cao | Lock order + concurrency tests + retry infrastructure | T12 |
| R-06 CI không có Docker/Rabbit | Integration tests skipped | Cao | CI provision container; skipped = chưa đạt gate | T14/T15 |
| R-07 Manual dispense bypass payment | Endpoint gọi thẳng core | Critical | T07 payment proof bắt buộc | Release |
| R-08 Legacy lifecycle lệch | ACTIVE nhưng slip/reservation terminal | Cao | Dry-run reconciliation, không tự sửa mù | T13/T14 |
| R-09 Dirty worktree ngoài Pharmacy | File user/shared đang thay đổi | Trung bình | Không stage/revert; commit path cụ thể | Mọi task |
| R-10 Log lộ dữ liệu | Payload/token xuất hiện trong WARN/ERROR | Cao | Log eventId/code; test/log review | T10/T17 |

## 18. Cách tính và cập nhật tiến độ

### 18.1 Công thức

```text
Service completion (%) =
  tổng trọng số acceptance criterion đã có đủ code + test + audit
  ------------------------------------------------------------- × 100
                  tổng trọng số toàn service
```

Không tính là hoàn thành khi:

- Code tồn tại nhưng test quan trọng bị skip.
- Unit test pass nhưng rule yêu cầu PostgreSQL/RabbitMQ thật.
- Contract ngoài service đang được giả định.
- Migration chỉ chạy fresh DB nhưng chưa test upgrade.
- Audit còn blocker.
- Tài liệu/HTTP example không khớp code vừa đổi.

### 18.2 Mẫu cập nhật sau mỗi task

```markdown
#### Task Txx — <tên>
- Status: DONE | IN_PROGRESS | BLOCKED
- Business rules: BR-...
- Files changed: ...
- Migration: none | V...
- Tests: <passed>/<failed>/<skipped>
- Commands: ...
- Audit: Blocker 0 | Should-fix n | Nit n
- External dependency: none | HANDOFF link
- Commit/SHA: local | <sha>
- Remote/PR: not pushed | <link>
- Progress: before x% → after y%
- Remaining risk: ...
```

### 18.3 Điều kiện chuyển task

Chỉ chuyển sang task tiếp theo khi:

- [ ] Acceptance criteria task hiện tại được tick bằng bằng chứng.
- [ ] Test bị ảnh hưởng chạy pass, không skip ngoài lý do đã công bố.
- [ ] Javadocs và compilation pass.
- [ ] `git diff --check` pass.
- [ ] Audit không còn blocker.
- [ ] Plan đã cập nhật status/test count.
- [ ] Worktree không mất hoặc ghi đè file ngoài phạm vi.

## 19. Git, audit và rollback checklist

### 19.1 Trước commit

- [ ] Fetch và đối chiếu `origin/master` nhưng không reset/checkout đè local.
- [ ] Xác nhận branch hiện tại và upstream.
- [ ] Kiểm tra migration number không xung đột.
- [ ] Stage theo path của task; không dùng stage-all khi worktree có file ngoài phạm vi.
- [ ] Commit message dạng `type(pharmacy): mô tả`.

### 19.2 Audit bắt buộc

| Nhóm | Câu hỏi |
|---|---|
| Architecture | Dependency có hướng vào trong? Controller/consumer mỏng? |
| Domain | Rule nằm đúng aggregate? Typed exception? |
| Transaction | Atomicity, rollback, proxy boundary và lock order đúng? |
| Persistence | Mapping/constraint/index/migration upgrade đúng? |
| Messaging | Event envelope, correlation, idempotency, outbox semantics đúng? |
| Security | Role, actor, ownership, secret/config đúng? |
| API | Status/envelope/validation/`.http` tương thích? |
| Tests | Rule/failure/race/recovery được chứng minh ở đúng tầng? |
| Javadocs | Public/non-obvious behavior được giải thích? |
| Scope | Không sửa service ngoài quyền sở hữu? |

### 19.3 Rollback theo loại thay đổi

- Refactor T01/T02: revert commit tập trung; không có schema/data impact.
- Additive migration: không xóa column/table đã chạy; phát hành forward-fix migration.
- Feature mới chưa bật: dùng property/feature switch chỉ khi đã được thiết kế trong task, không để
  switch trở thành đường bypass business rule.
- Outbox dispatcher lỗi: tắt dispatcher có kiểm soát, giữ pending rows, không xóa dữ liệu.
- Payment workflow lỗi: dừng consumer, giữ receipt/outbox để replay; không sửa stock thủ công không audit.
- Tuyệt đối không `git reset --hard`, force-push nhánh dùng chung hoặc sửa checksum migration đã deploy.

## 20. Task bắt đầu tiếp theo

Hai task vừa được triển khai tiếp là **T08 — payment/manual recovery tests** và **T10 — outbox retry/ops tests**.

Phần còn lại của T08 là test business-key theo contract Billing; phần còn lại của T10 là PostgreSQL/RabbitMQ integration test. Các phần này không được giả lập contract hoặc hạ tầng chưa có.
