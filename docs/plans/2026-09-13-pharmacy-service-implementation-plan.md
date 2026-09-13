# Kế hoạch code tiếp theo — Pharmacy Service

> Ngày lập: **13/09/2026**. Module thực tế: `backend/pharmacy-service` (phần người dùng gọi là pharma-service).
> Đây là kế hoạch triển khai dựa trên code đã kiểm tra, không phải xác nhận các chức năng đã hoàn thành hoặc yêu cầu triển khai tất cả ngay lập tức.

## 1. Mục tiêu, phạm vi và mốc đối chiếu

**Mục tiêu:** hoàn thiện chu trình kê đơn → giữ tồn → thanh toán → cấp thuốc; hủy/hết hạn giải phóng đúng lượng giữ tồn; cấp thất bại có kết quả bù trừ tin cậy; API có phân quyền và kiểm thử chứng minh không xuất trùng, không mất event, không làm sai tồn kho.

| Nội dung | Mốc đã kiểm tra khi lập kế hoạch |
| --- | --- |
| Nhánh local | `Huy`, commit `693b58f6bf4174a9fe6709b32a7a527bb207ab1f` |
| Master đã fetch | `e1c56232d77605037eb1183fb6deef64b518ec6c`; chưa tích hợp vào nhánh local tại mốc này |
| Công việc đang review | [Draft PR #74](https://github.com/Dangvinh77/MediFlow/pull/74): thay đổi tạo đơn và danh tính người kê |
| Dữ liệu local cần giữ | `.changelog/entries.jsonl` đang thay đổi; không xóa hoặc ghi đè khi đồng bộ Git |
| Migration hiện có | V1 khởi tạo, V2 reservation, V3 unique reservation theo đơn/thuốc, V4 lifecycle và audit |
| API hiện có | 4 API thuốc; POST tạo đơn; PUT hủy đơn |
| Kiểm thử baseline | Báo cáo lần chạy trước trong phiên làm việc ngày 13/09: **61 ca, 6 errors, 7 skipped**, thuộc 17 lớp test Java |
| Nguyên nhân cần xử lý trước | Caller trong test chưa theo `CreatePrescriptionCommand`; nhóm Testcontainers chưa chạy do Docker không khả dụng |

Số liệu test trên lấy từ báo cáo Surefire còn lưu tại thời điểm lập tài liệu, **không phải kết quả chạy lại sau khi sửa**. Có dấu hiệu output biên dịch cũ/IDE (`Unresolved compilation problems`), vì vậy phải chạy build sạch ở P0. Không dùng số test pass này để suy ra phần trăm hoàn thành nghiệp vụ.

Phạm vi ghi code: pharmacy-service và tài liệu liên quan. Gateway, Organization, Billing, Notification, Medical Record, frontend chỉ đối chiếu contract và phối hợp chủ sở hữu; không tự sửa các service đó. File ownership trên master đã fetch quy định Pharmacy thuộc Huy; đọc lại bản `AGENTS.md` gần nhất sau khi đồng bộ.

Nguồn bắt buộc đối chiếu khi bắt đầu code:

- [Quy tắc chung](../ai/README.md), [blueprint](../ai/04-microservice-blueprint.md), [bounded context pharmacy](../ai/services/pharmacy.md).
- [Đặc tả pharmacy](../eproject_general_plan/backend-spec/05-pharmacy.md), [hợp đồng dùng chung](../eproject_general_plan/backend-spec/00-overview.md), [thiết kế nghiệp vụ](../eproject_general_plan/pharmacy-service.html).
- [API](../ai/05-api-conventions.md), [event](../ai/06-events-rabbitmq.md), [RBAC](../ai/07-security-rbac.md), [kiểm thử](../ai/09-testing.md), [Git](../ai/10-git-workflow.md).

Đặc tả có đoạn cũ không đồng nhất với reservation/lifecycle mới, ví dụ ghi chỉ dispense cần khóa trong khi create cũng phải khóa để giữ tồn. Khi gặp mâu thuẫn: ghi quyết định và xác nhận contract trước; không sao chép nguyên pseudocode gây mất tính nhất quán. Giữ ngoại lệ tên field/DB tiếng Anh của pharmacy theo đặc tả; không đổi hàng loạt sang tiếng Việt.

## 2. Hiện trạng và khoảng trống có bằng chứng

Trong các bảng bên dưới, đường dẫn Java rút gọn tính từ `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/`. Đường dẫn test tính từ package tương ứng trong `src/test/java/`.

| Luồng | Phần đã có | Phần cần làm tiếp / rủi ro cụ thể |
| --- | --- | --- |
| Danh mục và tồn kho | `web/DrugController.java`: search, get, create, adjust; khóa drug khi điều chỉnh | `adjustStock` cho giảm tồn nhưng mới chặn tồn âm, chưa bảo vệ lượng đang giữ; `reason` chưa được lưu audit; chưa phát event thay đổi tồn ở luồng này |
| Kê đơn và giữ tồn | `application/service/PharmacyApplicationService.java`: loại drug trùng, khóa theo UUID, tính available, snapshot giá, tạo reservation và phiếu PENDING trong transaction | Đang so JWT subject với `doctorId`; correlation từ command không đi vào `prescription.created`; test chưa theo chữ ký mới |
| Tra cứu đơn | Có `PrescriptionDTO`, mapper, repository | Chưa có GET chi tiết đơn và query use case; cần kiểm tra tương thích khi DTO thêm lifecycle |
| Cấp thuốc | Có use case, khóa thuốc/reservation, trừ kho và đánh dấu phiếu DISPENSED | Chưa khóa đơn/phiếu trước kiểm tra trạng thái; chưa kiểm tra TTL ở thời điểm cấp; chưa gọi `Prescription.markFulfilled(now)` và lưu lại |
| Cấp thất bại | Có `markDispenseFailed` dùng `REQUIRES_NEW` qua self-proxy | Được gọi khi transaction ngoài chưa rollback xong; bắt mọi RuntimeException; chưa chuyển đơn sang DISPENSE_FAILED và giải phóng reservation; payload thiếu invoice/patient/correlation |
| Nhận thanh toán | `messaging/consumer/PaymentCompletedConsumer.java` nhận event, có bảng processed event | Application chỉ dùng eventId/prescriptionId; chưa lưu bằng chứng thanh toán; dedupe kiểu check rồi save chưa chứng minh an toàn đồng thời; lỗi nghiệp vụ bị ném lại dẫn đến redelivery |
| Hủy đơn | `CancelPrescriptionService`: khóa đơn → phiếu → reservation, kiểm tra quyền và audit, hủy lặp không giải phóng thêm | Cùng vấn đề accountId/staffId; kiểm tra reservation chưa chứng minh đủ đúng tất cả dòng và quantity; chưa định nghĩa payment đến sau hủy |
| Hết hạn giữ tồn | Scheduler + `ReleaseExpiredReservationsService` + `ExpirePrescriptionTransaction`, một transaction mỗi đơn | TTL tạo đơn hardcode 24 giờ; batch cố định 100; một lỗi có thể dừng phần còn lại; cần xử lý starvation và cạnh tranh với payment/dispense |
| Phát event | `PharmacyEventPublisherAdapter` gửi trong callback afterCommit; Rabbit có main queue/DLQ | AfterCommit không bảo đảm gửi lại khi broker lỗi; chưa có outbox bền; việc khai báo DLQ chưa chứng minh đã có retry hữu hạn |
| Kiểm thử | Domain, application, web, security, persistence, architecture đã có khung | Thiếu bằng chứng DB thật cho cấp thuốc đồng thời, rollback nhiều dòng, failure transaction, cancel/expire race và broker outage |

Hai phân biệt quan trọng:

- **Có implementation không đồng nghĩa đã nghiệm thu.** Luồng hủy/TTL đã có code nhưng chưa đủ test để đánh dấu hoàn thành.
- **Payload thiếu `invoiceId` vẫn là vấn đề contract**, dù Billing local hiện tìm invoice bằng `prescriptionId`; không khẳng định chỉ riêng field null này đã làm mọi phiên bản Billing lỗi ngay.

## 3. Quyết định phải chốt trước khi code phần phụ thuộc

| ID | Quyết định / phương án đề xuất | Chủ thể xác nhận | Chặn phần nào |
| --- | --- | --- | --- |
| D1 | JWT `sub` là account ID; `doctorId` tham chiếu staff. Tách `actorAccountId` dùng audit và `actorStaffId` dùng ownership. Lấy staff từ claim đã xác thực theo contract được duyệt; nếu không có, mapping qua resilient REST trước transaction. Không tự đặt tên claim/URL hoặc coi hai UUID là một | Gateway + Organization + Pharmacy | Hoàn tất quyền tạo/hủy; audit người cấp thủ công |
| D2 | Giữ luồng tự động cấp sau `payment.completed` như đặc tả. Endpoint thủ công chỉ dùng cùng core và phải có bằng chứng thanh toán do backend xác nhận; không nhận `paid=true` từ client | Billing + Pharmacy | Payment receipt và endpoint dispense |
| D3 | Xác định invoice có bao nhiêu đơn, `amount` là tổng hóa đơn hay phần thuốc, field nào nullable, khóa định danh thanh toán ổn định. Không mặc định `amount == prescription.totalAmount` | Billing + Pharmacy | Validation event, unique business key |
| D4 | Đơn CANCELLED/EXPIRED nhận payment muộn: không hồi sinh và không xuất thuốc; ghi nhận yêu cầu bù trừ theo contract. Không ghi đè trạng thái cũ thành DISPENSE_FAILED | Billing + Pharmacy | Terminal-state payment handling |
| D5 | Chốt mốc TTL: đề xuất dùng thời điểm xử lý cấp thuốc, `expiresAt <= now` là không còn hiệu lực. Nếu muốn ưu tiên thời điểm thanh toán thì phải thiết kế lại phối hợp TTL/receipt; không tự gia hạn | Billing + Pharmacy | Ca sát thời điểm hết hạn |
| D6 | Hệ thống tự cấp không phải một nhân viên thật. Chốt biểu diễn system actor; UUID toàn số 0 hiện tại không được diễn giải là staff đã tồn tại | Organization + Pharmacy | Audit `dispensedBy` và contract DTO |
| D7 | Đề xuất không cho giảm tồn vật lý dưới tổng RESERVED. Nếu kiểm kê thực tế bắt buộc giảm, cần workflow xử lý thiếu hàng riêng, không âm thầm hủy giữ tồn | Pharmacy / người phụ trách nghiệp vụ | Điều chỉnh kho |
| D8 | Bổ sung transactional outbox, receipt/inbox và audit là phần tăng độ tin cậy được đề xuất, chưa phải tính năng hiện hữu. Chốt schema, retention, retry, vận hành và nguồn lực trước implementation | Pharmacy + người review kiến trúc | P3, P5, P7 |

Khi D1 chưa có nguồn danh tính đáng tin: từ chối quyền cần ownership, không fallback về so account với staff. Token không hợp lệ → 401; token hợp lệ nhưng thiếu quyền/danh tính bắt buộc → 403; dependency mapping không khả dụng → lỗi dependency theo API convention, không giả thành “không có quyền”.

Nếu chưa chốt một quyết định, chỉ làm task không phụ thuộc nó và đánh dấu BLOCKED rõ lý do. Không dùng dữ liệu giả để vượt gate.

## 4. Mô hình trạng thái và transaction đích

### 4.1. Invariant cần giữ

1. Giá đơn do server lấy từ thuốc tại lúc kê; tổng tiền dùng `BigDecimal`, scale/rounding theo spec. Client không thay được snapshot.
2. Tạo đơn không trừ tồn vật lý. `available = onHand - tổng quantity của RESERVED`; một reservation cho mỗi cặp đơn/thuốc, đủ đúng số lượng của dòng.
3. Reservation đã quá TTL nhưng chưa được job chuyển trạng thái vẫn được tính RESERVED một cách bảo thủ khi kiểm tra available; không “bán lại” phần đó trong khi dispense vẫn có thể dùng nó.
4. Cấp thành công cập nhật thuốc, tất cả reservation, phiếu và đơn trong **cùng một transaction**; lỗi bất kỳ dòng nào không được trừ một phần.
5. Không giữ khóa DB trong lúc gọi REST hoặc gửi RabbitMQ. Ghi outbox trong DB được phép; gửi message thật sau đó ở worker.
6. Các timestamp nghiệp vụ lấy từ `Clock` được inject, cùng một `now` cho một quyết định. Domain nhận `Instant`/`LocalDate` cần thiết, không phụ thuộc Spring.
7. Đọc trạng thái trước khóa không đủ chống trùng; phải khóa rồi đọc/kiểm tra lại trước mutation.

### 4.2. Bảng chuyển trạng thái cần test

| Kết quả | Prescription | DispenseSlip | Reservation | Tồn vật lý / event |
| --- | --- | --- | --- | --- |
| Kê thành công | ACTIVE | PENDING | RESERVED | Không trừ; `prescription.created` |
| Cấp thành công | FULFILLED | DISPENSED | FULFILLED | Trừ đúng quantity một lần; `prescription.filled`, có thể `stock.low` |
| Hủy hợp lệ | CANCELLED | CANCELLED | RELEASED + reason/actor/time | Không thay tồn; `prescription.cancelled` |
| Hết TTL | EXPIRED | EXPIRED | EXPIRED + audit | Không thay tồn; `prescription.expired` |
| Cấp thất bại nghiệp vụ trên đơn ACTIVE | DISPENSE_FAILED | FAILED | RELEASED + DISPENSE_FAILED | Transaction cấp rollback; ghi kết quả thất bại và event bù trừ bền |
| Payment muộn sau CANCELLED/EXPIRED | Giữ nguyên | Giữ nguyên | Giữ nguyên | Không trừ; receipt và yêu cầu bù trừ theo D4 |
| Lỗi hạ tầng tạm thời | Không chuyển sang terminal do lỗi này | Không đổi | Không đổi | Rollback; retry hữu hạn, không hoàn tiền chỉ vì DB/broker timeout |
| Gửi lại cùng yêu cầu đã thành công | FULFILLED | DISPENSED | FULFILLED | Trả kết quả đã lưu / ACK; không trừ hoặc tạo event nghiệp vụ mới |

### 4.3. Thứ tự khóa và ranh giới transaction

- Với đơn đã tồn tại: **prescription → dispense slip → các drug theo drugId tăng dần → các reservation theo drugId tăng dần**. Khóa hết nhóm drug cần dùng trước khi khóa nhóm reservation, không xen kẽ mỗi luồng theo thứ tự khác nhau.
- Cancel/expire không cần sửa drug có thể bỏ nhóm drug nhưng không đảo thứ tự. Điều chỉnh kho chỉ khóa drug và đọc tổng giữ tồn; không khóa reservation rồi quay lại khóa prescription.
- Create chưa có aggregate tồn tại: khóa các drug theo thứ tự, kiểm tra available và lưu aggregate mới; không thêm đường gọi ngược từ create sang đơn khác.
- Inbox/receipt cần một thứ tự khóa được ghi rõ: đề xuất cùng gate prescription trước các thao tác claim cho đơn đó. Consumer/manual/cancel phải dùng thống nhất; không có đường claim trước prescription ở một nơi và làm ngược ở nơi khác.
- Orchestrator cấp thuốc **không có transaction bao ngoài**. Nó gọi bean transaction cấp; chỉ khi lời gọi đó đã rollback hoàn toàn mới gọi bean transaction ghi thất bại. Bỏ self-injection `@Lazy`.
- Transaction ghi thất bại khóa và kiểm tra trạng thái lại. Nếu tiến trình khác đã cấp/hủy/hết hạn trong khoảng giữa hai transaction, không ghi đè kết quả; xử lý theo trạng thái mới và payment context.
- Nếu tiến trình chết giữa rollback và ghi thất bại, message chưa ACK phải được gửi lại; thiết kế receipt/inbox cho phép tiếp tục, không đánh dấu hoàn tất sớm.

## 5. Backlog triển khai theo thứ tự

Mỗi giai đoạn chỉ được đánh dấu DONE khi có code, test và bằng chứng gate. Tên lớp **đề xuất mới** bên dưới có thể tinh chỉnh theo blueprint, nhưng trách nhiệm và tiêu chí nghiệm thu phải giữ.

### P0 — Chốt baseline, đồng bộ an toàn và tái hiện lỗi

**Ưu tiên:** bắt buộc đầu tiên. **Phụ thuộc:** không.

- [x] P0.1 Đọc changelog, root/nested AGENTS; kiểm tra branch, tracked/untracked files, upstream và trạng thái PR #74 hiện tại.
- [ ] P0.2 Fetch master mới, đối chiếu diff pharmacy và shared contracts; bảo toàn thay đổi local bằng checkpoint có phạm vi rõ. Không reset hard, không force push, không tự xử lý conflict bằng cách bỏ một phía.
- [ ] P0.3 Tích hợp master trong đợt code được giao; nếu lịch sử đã phân kỳ thì merge có kiểm soát, không giả định có thể fast-forward. Ghi lại SHA baseline mới trong PR.
- [ ] P0.4 Kiểm tra Java 21, Maven, Docker; chạy `mvn -q -pl backend/pharmacy-service -am clean test` để loại output cũ và lưu lỗi thực tế.
- [x] P0.5 Cập nhật test caller sang command mới ở `PharmacyApplicationServicePrescriptionTest` và `PrescriptionControllerTest`; giữ assertion nghiệp vụ, không bỏ test hoặc vô hiệu hóa security cho xanh.

**Gate:** test source biên dịch được; biết rõ nhóm nào pass/fail/skip và lý do. Nếu Docker chưa có, ghi INCOMPLETE cho DB tests; chưa được công bố module đã verified. PR #74 giữ draft tới khi các blocker trong phạm vi PR được xử lý.

### P1 — Sửa tạo đơn, quyền sở hữu và correlation

**Ưu tiên:** P0 về an toàn. **Phụ thuộc:** P0; D1, D6 cho phần danh tính.

**File chính:** `web/PrescriptionController.java`, `infrastructure/security/JwtAuthFilter.java`, các command tạo/hủy, `PharmacyApplicationService`, `CancelPrescriptionService`, mapper/DTO và test tương ứng.

- [x] P1.1 Đưa identity đã xác thực vào command dưới dạng giá trị rõ account/staff/roles; controller không tự tin `doctorId` client, application vẫn kiểm tra ownership. Đã có test actor bác sĩ lệch doctorId bị từ chối và Admin override dùng doctor đích.
- [x] P1.2 Doctor chỉ kê/hủy đơn của staff tương ứng; Admin override có audit account thực hiện và doctor đích; xác nhận tính hợp lệ doctor/khoa theo contract, không đọc DB service khác. Đã bổ sung unit test ownership khi hủy, ADMIN override và audit release actor/reason.
- [x] P1.3 Giữ check drug trùng trước mutation; test create lưu đơn + lines + reservations + pending slip atomically, thiếu available thì không lưu phần nào.
- [x] P1.4 Truyền correlation vào `publishCreated`, phản hồi HTTP, log và luồng hủy; nếu vắng thì chuẩn hóa/generate theo convention, không dùng chuỗi rỗng để lách validation. Command tự sinh UUID khi header trống và test boundary đã bổ sung.
- [x] P1.5 Chốt hợp đồng DTO: bản hiện tại đã có `status` = PrescriptionStatus và `dispenseStatus` riêng. Kiểm tra consumer cũ; không đổi nghĩa field silently. Sửa Javadoc còn mô tả trạng thái đơn là PENDING.
- [ ] P1.6 Chuẩn hóa format phần sửa, bỏ wildcard/fully-qualified lặp nếu trái chuẩn; không refactor unrelated toàn repo.

**Gate/test:** account UUID khác staff UUID vẫn cho đúng bác sĩ; giả doctorId bị 403 trước ghi DB; Admin override đúng audit; UUID lỗi/thiếu token/sai role đúng 400/401/403; duplicate line và available stock đúng lỗi theo spec; snapshot giá/tổng không bị client thay; correlation giữ xuyên suốt.

### P2 — Bổ sung API đọc chi tiết đơn

**Ưu tiên:** P1. **Phụ thuộc:** P0, hợp đồng DTO P1.5; có thể làm trong lúc chờ D1.

**File:** sửa `web/PrescriptionController.java`; đề xuất mới `application/port/in/GetPrescriptionUseCase.java`, `application/service/GetPrescriptionService.java`; dùng lại `PrescriptionDTO`, mapper và repository ports.

- [x] P2.1 Thêm `GET /api/v1/pharmacy/prescriptions/{id}`, roles ADMIN/DOCTOR/PHARMACIST như đặc tả.
- [x] P2.2 Read-only transaction trả đầy đủ lines, giá snapshot, total, status, dispenseStatus và audit; không trả JPA entity, không khóa ghi khi chỉ xem.
- [x] P2.3 Không tính lại đơn cũ từ giá thuốc hiện tại. Tên thuốc hiện chưa được snapshot vào line: thống nhất là tên hiện tại hoặc bổ sung snapshot bằng migration riêng, không khẳng định đã có snapshot tên.
- [x] P2.4 Tránh query mỗi dòng nếu có thể batch-load; xử lý dữ liệu thiếu slip như lỗi nhất quán có quan sát, không tạo phiếu mới trong GET. Drug lookup đã chuyển sang `findByIds` một lần cho toàn bộ dòng.

**Gate/test:** 200 cho đơn ở mỗi lifecycle; 404 khi không tồn tại; 401/403 đúng role; giá giữ nguyên sau thay danh mục; dữ liệu trả đúng DB thật. List/filter đơn, phân trang đơn là mở rộng riêng, không mặc định thêm vào task này.

### P3 — Làm bền việc phát event bằng transactional outbox

**Ưu tiên:** P0 về độ tin cậy saga. **Phụ thuộc:** P0, D8; phần envelope đối chiếu P1.4.

**File:** `application/port/out/PharmacyEventPublisherPort.java`, `infrastructure/messaging/PharmacyEventPublisherAdapter.java`, `infrastructure/config/RabbitConfig.java`, config ứng dụng; thêm model/port outbox và adapters persistence/worker đúng blueprint, migration mới.

- [ ] P3.1 Ghi yêu cầu phát event vào bảng outbox cùng transaction nghiệp vụ. Transaction rollback không để lại outbox; không gửi RabbitMQ trong đoạn đang khóa DB.
- [ ] P3.2 Worker claim theo batch/lease hoặc cơ chế khóa phù hợp, hỗ trợ nhiều instance; gửi persistent message, kiểm tra publisher confirms và unroutable return. Chỉ đánh dấu sent sau kết quả thành công.
- [ ] P3.3 Retry có backoff và giới hạn/cảnh báo; lỗi một message không làm kẹt cả batch. EventId/payload giữ ổn định qua retry; consumer vẫn phải idempotent vì crash sau gửi trước mark sent có thể gây trùng.
- [ ] P3.4 Chuyển created/filled/dispense.failed/cancelled/expired sang đường gửi bền; `stock.low` độc lập retry, broker lỗi không làm hỏng lần cấp đã commit hoặc ngăn filled được gửi.
- [ ] P3.5 Có metric pending/oldest age/retry/failure và quy trình replay; không ghi JWT, hồ sơ bệnh án hoặc payload nhạy cảm nguyên vẹn vào log.

**Gate/test:** broker tắt sau DB commit rồi bật lại vẫn nhận event; rollback không phát event; crash/retry không đổi eventId; hai worker không xử lý claim đang còn lease của nhau; unroutable không bị đánh dấu sent. Test DB + Rabbit thật, không chỉ verify mock `convertAndSend`.

### P4 — Hoàn thiện core cấp thuốc và ghi nhận thất bại

**Ưu tiên:** P0. **Phụ thuộc:** P0, P3; D5 và quy tắc transaction mục 4.

**File:** tách trách nhiệm khỏi `PharmacyApplicationService`; đề xuất `DispensePrescriptionService`, `DispenseTransactionService`, `RecordDispenseFailureService`, command mang prescription/payment/actor/correlation context; sửa repository ports/adapters, domain và test.

- [x] P4.1 Khóa đơn + phiếu trước check, xác nhận ACTIVE/PENDING; đơn đã FULFILLED trả kết quả cũ; terminal khác trả kết quả/lỗi phù hợp, không gọi lại trừ kho. Unit tests đã bao phủ cấp thành công, reservation hết hạn và phiếu DISPENSED idempotent.
- [x] P4.2 Khóa drug/reservation theo mục 4.3; xác minh tập drug và quantity reservation khớp toàn bộ đơn, trạng thái RESERVED, TTL còn hiệu lực; kiểm tra hạn dùng thuốc theo ngày nghiệp vụ. Đã chặn reservation thiếu/thừa, lệch quantity, hết TTL và thuốc hết hạn trước mutation; còn P6.3 theo dõi Clock cố định cho biên thời gian.
- [ ] P4.3 Trừ toàn bộ thuốc, fulfill reservation, `prescription.markFulfilled(now)` và `slip.markDispensed(...)`, lưu tất cả và outbox filled trong cùng transaction.
- [ ] P4.4 Orchestrator đợi rollback hoàn toàn rồi ghi thất bại ở bean khác; cập nhật đơn/slip/reservations và outbox compensation atomically. Không giữ self-proxy, không có transaction lớn bao ngoài cả hai bước.
- [ ] P4.5 Phân loại lỗi: lỗi nghiệp vụ đã xác định → kết quả thất bại bền; DB timeout/deadlock/network → rollback và retry; dữ liệu hỏng → cảnh báo/quarantine theo chính sách được duyệt. Không catch mọi RuntimeException rồi tự động hoàn tiền.
- [ ] P4.6 Failure context có patientId từ đơn, invoiceId từ receipt/event đáng tin, correlation, reasonCode và reason an toàn/giới hạn chiều dài; failedItems điền khi xác định được thuốc lỗi, không bịa thuốc hoặc invoice.
- [x] P4.7 Khi transaction failure chạy, kiểm tra lại trạng thái để không ghi đè kết quả thắng cuộc. Nếu ghi failure không commit được thì không ACK payment. Đã có test transaction bù trừ bỏ qua đơn FULFILLED/phiếu DISPENSED.

**Gate/test:** nhiều dòng lỗi ở dòng cuối rollback mọi stock/reservation; sau đó thất bại vẫn bền trong DB, giải phóng giữ tồn và có đúng logical failure event; thành công cập nhật đủ ba lifecycle; test hai luồng và không deadlock giữa transaction ngoài/trong. Chưa expose endpoint thủ công trước P5/P8.

### P5 — Payment receipt, idempotency và bù trừ đúng một kết quả nghiệp vụ

**Ưu tiên:** P0. **Phụ thuộc:** P3, P4; D2–D5 đã chốt.

**File:** `ReactToPaymentUseCase`, `PaymentCompletedCommand`, consumer/payload, `ProcessedEventPort` và adapter; đề xuất `ReactToPaymentService`, payment receipt model/port/persistence; migrations mới, Rabbit retry config.

- [ ] P5.1 Contract-test JSON thực từ Billing: field bắt buộc/nullable, UUID, amount/method, correlation. Tiếp tục bỏ qua invoice không có prescription theo contract; phân biệt message sai schema với invoice không liên quan.
- [ ] P5.2 Validate patient/department/prescription association trước mutation. So tiền theo D3, không so tổng invoice với đơn thuốc nếu invoice còn chứa phí khác. Event sai dữ liệu không được đổi tồn hay gắn proof paid.
- [ ] P5.3 Lưu receipt có nguồn event, invoice/payment identity và trạng thái xử lý. Unique eventId để chống redelivery; thêm unique business key theo D3 để chống hai eventId khác nhau cho cùng payment. Không hardcode một invoice chỉ có một đơn khi chưa xác nhận.
- [ ] P5.4 Claim/dedupe atomically dưới lock/unique constraint; không dựa riêng vào exists rồi save. Không bắt unique violation và tiếp tục dùng transaction đã abort; rollback rồi đọc kết quả đã có ở transaction mới.
- [ ] P5.5 Thành công: stock/lifecycle, receipt kết quả, processed event và outbox filled cùng commit. Thất bại nghiệp vụ: transaction cấp rollback rồi transaction failure commit receipt kết quả, processed event, terminal state phù hợp và outbox compensation; sau đó ACK.
- [ ] P5.6 Đơn đã hủy/hết hạn nhận payment: giữ terminal state, ghi kết quả và compensation theo D4; event lặp không tạo nhiều yêu cầu hoàn tiền logic. Đơn đã cấp không cấp lại; payment khác thật sự cho đơn đã cấp phải được Billing xử lý riêng, không xem là duplicate vô điều kiện.
- [ ] P5.7 Lỗi hạ tầng giữ khả năng retry; cấu hình retry hữu hạn + backoff + DLQ/poison-message policy, tránh requeue nóng. Message lỗi nghiệp vụ đã xử lý bền không ném lại vào Rabbit.
- [ ] P5.8 Hỗ trợ khôi phục sau crash giữa các transaction; job/consumer không bỏ qua receipt đang dang dở như thể đã hoàn tất. Định nghĩa retention đủ dài cho cửa sổ replay của Billing.

**Gate/test:** cùng event hai lần và hai luồng chỉ xuất một lần; hai eventId cùng business payment không xuất lại; crash/redelivery hội tụ một kết quả; lỗi nghiệp vụ ACK sau khi kết quả bền; lỗi DB retry không đánh dấu processed sớm; payment muộn không cấp đơn đã hủy/hết hạn.

### P6 — Gia cố hủy đơn và giải phóng giữ tồn hết hạn

**Ưu tiên:** P0 cho race, P1 cho vận hành. **Phụ thuộc:** P1 identity, P3–P5; D4/D5.

**File:** `CancelPrescriptionService`, `ExpirePrescriptionTransaction`, `ReleaseExpiredReservationsService`, `ReservationExpiryScheduler`, reservation repository/adapter, domain, config.

- [ ] P6.1 Dùng chung invariant coverage và thứ tự khóa. Hủy/hết hạn phải chuyển đủ đơn/phiếu/reservations + outbox trong transaction; không giải phóng một phần khi còn dòng không hợp lệ.
- [ ] P6.2 Chặn hủy sau khi đã cấp; hủy lặp trả kết quả hiện hữu, không tăng released count/event. Trường hợp payment đã được ghi nhận phải theo D4 và chính sách Billing; không tự xác nhận refund đã xong.
- [ ] P6.3 Inject Clock; đưa TTL, batch size và cron vào config có default/validation. Test sát biên `expiresAt == now` và ngày hết hạn thuốc bằng thời gian cố định.
- [x] P6.4 Một đơn lỗi không dừng toàn batch: catch tại ranh giới từng transaction, log identifier/reason an toàn và tiếp tục. Không nuốt lỗi đến mức báo thành công sai. Đã cố định mốc `Clock`, giới hạn batch cấu hình ở infrastructure và có test một ứng viên lỗi không chặn các ứng viên sau.
- [ ] P6.5 Query có cursor/progress hoặc cơ chế tránh starvation: 100 đơn lỗi/inconsistent đầu danh sách không chặn mãi các đơn sau. Nhiều scheduler instance không phát lặp logical expiry event.
- [ ] P6.6 Đối chiếu bất thường legacy như ACTIVE nhưng slip đã DISPENSED/FAILED, reservation thiếu/dư. Báo cáo trước và sửa bằng migration/job reconciliation được review; không tự giải phóng reservation không xác định.

**Gate/test:** cancel vs dispense, expire vs dispense, cancel vs expire chạy đồng thời chỉ một transition hợp lệ thắng; không kho âm, không giữ tồn mồ côi; đơn 101+ vẫn được xử lý khi batch có lỗi; chạy job lặp không đổi kết quả đã hoàn tất.

### P7 — Điều chỉnh kho an toàn và audit

**Ưu tiên:** P1. **Phụ thuộc:** P3; D7/D8. Có thể làm độc lập với P5 sau khi thống nhất khóa.

**File:** `Drug`, `AdjustStockRequest`, `ManageDrugUseCase`, phần drug service/controller; đề xuất command actor/correlation, stock adjustment audit port/model/adapter và migration.

- [x] P7.1 Sau khóa drug, tính reserved và không cho `newOnHand < reserved` theo D7; vẫn chặn zero/âm tồn, overflow số lượng. Không âm thầm thay quantity đã kê. Application đã kiểm tra tổng reserved bằng số học `long` và có test reserved-protection/overflow.
- [ ] P7.2 Lưu audit delta, before/after, reason, actor và timestamp trong cùng transaction; chốt reason bắt buộc cho giảm kho với người dùng API trước khi đổi validation.
- [ ] P7.3 Phát event thay đổi kho theo tên/schema đã review; stock.low theo ngưỡng nhất quán. Không tự thêm routing key mà downstream được kỳ vọng phải hiểu ngay.
- [x] P7.4 Tăng test create/update domain có sẵn: giá âm, quantity/threshold âm, hạn dùng, rounding; không mở API update/delete thuốc chỉ vì domain đang có `updateInfo`. Đồng thời bổ sung invariant ngưỡng âm cho `Drug.updateInfo`.

**Gate/test:** đang có reserved thì điều chỉnh không làm thiếu phần đã giữ; create reservation vs adjust chạy đồng thời vẫn đúng; audit/event rollback cùng tồn; chỉ ADMIN/PHARMACIST được điều chỉnh.

### P8 — Bổ sung endpoint cấp thuốc có chặn thanh toán

**Ưu tiên:** P1. **Phụ thuộc:** P1 identity, P4/P5/P6; D2/D6.

**File:** `web/PrescriptionController.java`, dispense command/in-port, error handler, HTTP/web/security tests.

- [ ] P8.1 Thêm `PUT /api/v1/pharmacy/prescriptions/{id}/dispense`, roles ADMIN/PHARMACIST, dùng cùng core với consumer, không copy logic tồn kho.
- [ ] P8.2 Chưa có payment proof hợp lệ → từ chối, không trừ tồn. Event chưa đến thì trả trạng thái/lỗi chờ được document; không tin frontend và không gọi Billing dưới DB lock.
- [ ] P8.3 Nếu automatic consumer đã cấp, trả DispenseDTO đã lưu và không tạo event mới. Nếu receipt hợp lệ còn chờ xử lý, đi qua cùng claim/lock và kết quả bền, không bỏ quên processed-event bookkeeping.
- [ ] P8.4 Admin cũng không được bỏ qua payment gate. Ghi audit staff/account đúng D1/D6 và correlation; không nhận `dispensedBy` tùy ý từ request.

**Gate/test:** chưa trả tiền/sai role/đơn đã hủy/hết hạn không cấp; đã trả tiền cấp đúng một lần; manual vs consumer đồng thời không xuất trùng; Location của API create có GET tương ứng hoạt động.

### P9 — Migration, kiểm thử tích hợp, tài liệu và nghiệm thu

**Ưu tiên:** bắt buộc trước release. **Phụ thuộc:** mọi P trong phạm vi release; không trì hoãn viết test/migration tới P9 mới bắt đầu.

- [ ] P9.1 Mỗi P có schema mới phải thêm migration cùng PR. Sau đồng bộ master chọn số V tiếp theo thực tế; **không sửa V1–V4 đã áp dụng**, không reset database để làm migration pass.
- [ ] P9.2 Test hai đường: database mới từ đầu và database V4 có dữ liệu cũ. Unique/index/check/FK nội bộ đúng, không FK sang DB service khác; startup `ddl-auto=validate` pass.
- [ ] P9.3 Dữ liệu cũ không có proof paid phải giữ trạng thái unknown, không backfill “đã thanh toán” bằng suy đoán. Reconcile lệch lifecycle có dry-run/report và quy tắc được duyệt.
- [ ] P9.4 Hoàn tất test matrix mục 7, architecture test, real PostgreSQL/RabbitMQ và contract JSON với owner. Không thay real concurrency/rollback tests bằng mock.
- [ ] P9.5 Cập nhật bounded-context doc/API examples/README của module trong phạm vi được giao; ghi các quyết định contract, hướng dẫn retry/DLQ/outbox, cấu hình và demo script.
- [ ] P9.6 Lập handoff cho phần owner khác theo mục 8; test end-to-end qua gateway. Nếu dependency chưa có implementation, ghi rõ chỉ mới contract/mock, không ghi E2E PASS.
- [ ] P9.7 Chạy verify module và reactor; ghi riêng lỗi ngoài phạm vi pharmacy. Review diff theo docs/ai, cần ít nhất một human review; chỉ đưa PR khỏi draft khi checklist của PR thực sự đạt.

**Gate:** đạt Definition of Done mục 10 với báo cáo test mới và SHA rõ ràng; không có migration phá dữ liệu hoặc bước tích hợp chưa được công bố.

## 6. API và event cần nghiệm thu

Prefix API: `/api/v1/pharmacy`. Không gọi service port trực tiếp từ frontend; request đi qua gateway.

| API | Trạng thái baseline | Quyền / điểm nghiệm thu |
| --- | --- | --- |
| GET `/drugs` | Đã có | ADMIN/DOCTOR/PHARMACIST; page envelope, keyword, giới hạn page/size |
| GET `/drugs/{id}` | Đã có | ADMIN/DOCTOR/PHARMACIST; 404 đúng envelope |
| POST `/drugs` | Đã có | ADMIN/PHARMACIST; validation và 201/Location |
| PUT `/drugs/{id}/stock` | Có, cần gia cố | ADMIN/PHARMACIST; bảo vệ reserved, audit |
| POST `/prescriptions` | Có, cần sửa | ADMIN/DOCTOR; ownership, snapshot, reserve atomically |
| GET `/prescriptions/{id}` | Cần thêm P2 | ADMIN/DOCTOR/PHARMACIST; lifecycle và giá snapshot |
| PUT `/prescriptions/{id}/cancel` | Có, cần gia cố | ADMIN/DOCTOR; ownership, terminal gate, idempotency |
| PUT `/prescriptions/{id}/dispense` | Cần thêm P8 | ADMIN/PHARMACIST; payment gate, cấp đúng một lần |

Chốt error-code/HTTP matrix trước sửa controller: request sai → 400; thiếu/xác thực token lỗi → 401; quyền sai → 403; không tồn tại → 404; nghiệp vụ theo convention/spec (ví dụ thiếu available là 422). Các mã mới như chưa trả tiền/trạng thái conflict phải được document, không trả 500 cho lỗi nghiệp vụ dự kiến.

| Routing key | Dữ liệu/chứng minh bắt buộc |
| --- | --- |
| `payment.completed` (inbound) | Envelope, invoice/prescription/patient/department, semantics amount/method theo D3; JSON fixture do Billing xác nhận |
| `prescription.created` | Giá/item/total snapshot, record/patient/department; correlation từ request |
| `prescription.filled` | Đơn/patient/department, total, dispensedItems; event chỉ ứng với cấp đã commit |
| `prescription.dispense.failed` | Invoice từ payment context, patient từ đơn, reason an toàn, failedItems khi có; logical compensation không lặp |
| `prescription.cancelled` / `prescription.expired` | Payload đối chiếu record hiện tại và Billing/Notification; audit/released items đúng; không xem hai event này tự động là refund đã hoàn tất |
| `stock.low` | Stock thực sau mutation và threshold; failure gửi không làm hỏng transaction đã thành công |

`PrescriptionFilledEvent` hiện chưa có `dispenseId`, trong khi Billing có nhu cầu gắn phiếu xuất. Nếu cần bổ sung, thực hiện additive contract được hai bên duyệt và test compatibility; không coi field mới là bắt buộc với message cũ ngay lập tức.

## 7. Ma trận kiểm thử bắt buộc

### 7.1. Đặc tả BR-D1 đến BR-D12

| Rule | Ca cần chứng minh | Tầng / giai đoạn |
| --- | --- | --- |
| BR-D1 | Thiếu stock không cấp; create dùng available đã trừ giữ tồn | Domain/application + DB; P1/P4 |
| BR-D2 | Thuốc hết hạn bị từ chối; ngày đúng hạn được xử lý theo spec | Domain với ngày cố định + transaction; P4 |
| BR-D3 | Create tạo pending slip và đúng reservation, không trừ vật lý | PostgreSQL transaction; P1 |
| BR-D4 | Cấp thành công trừ đúng một lần, kể cả gọi lại | Application + DB; P4/P5 |
| BR-D5 | Tổng nhiều dòng, số lẻ, rounding đúng BigDecimal | Domain; P1 |
| BR-D6 | Failure có event bù trừ đúng invoice/patient, chỉ sau commit failure | Application + outbox/Rabbit; P3–P5 |
| BR-D7 | Đổi giá danh mục không đổi giá/tổng đơn cũ | Application/persistence/query; P1/P2 |
| BR-D8 | Client gửi price/total không thay được giá do server quyết định | Web + application; P1 |
| BR-D9 | Redelivery tuần tự và đồng thời chỉ tạo một kết quả nghiệp vụ | PostgreSQL + Rabbit; P5 |
| BR-D10 | Hai luồng thực không bán vượt tồn; lock order không deadlock | Spring integration + PostgreSQL; P4/P6/P7 |
| BR-D11 | Đúng ngưỡng và dưới ngưỡng tạo stock.low; broker lỗi không làm sai lần cấp | Application + outbox/Rabbit; P3/P4/P7 |
| BR-D12 | Rollback cấp không cuốn theo phiếu FAILED được lưu sau đó | PostgreSQL, bean transaction thật; P4 |

### 7.2. Các ca bổ sung không được bỏ qua

| Nhóm | Ca kiểm thử tối thiểu |
| --- | --- |
| Identity | Account khác staff; giả doctorId; Admin override; missing claim; dependency identity lỗi; token/role sai |
| Reservation | Hai create cùng drug chỉ còn ít available; reservation thiếu/dư/sai quantity; TTL đã hết nhưng scheduler chưa chạy; giải phóng đúng một lần |
| Atomicity | Hai thuốc, thuốc cuối lỗi; stock của thuốc đầu không đổi sau rollback; mọi reservation nhất quán; failure outbox cùng commit với terminal state |
| Cạnh tranh | Cùng đơn cấp hai lần; hai đơn dùng chung drug; cancel vs dispense; expire vs dispense; cancel vs expire; adjust vs create; manual vs consumer |
| Payment | Cùng eventId khác payload phải cảnh báo; hai eventId cùng payment; payment muộn; payment patient sai; invoice thường không có prescription; amount nullable/mismatch theo contract |
| Khôi phục | Crash trước/sau commit; giữa rollback và ghi failure; sau send trước mark outbox sent; retry failure transaction; message poison vào DLQ sau số lần hữu hạn |
| Scheduler | Hơn 100 đơn hết hạn; một đơn lỗi không chặn các đơn sau; hai scheduler; Clock đúng boundary; batch không starvation |
| Migration | Fresh DB; nâng V4 có dữ liệu; legacy lifecycle lệch; unique violation; dữ liệu cũ thiếu payment evidence không được biến thành paid |
| API/query | 400/401/403/404/422 theo contract; endpoint có roles; lifecycle DTO; no entity leakage; query không thay DB |

Tên test mới gợi ý: `DispenseTransactionTest`, `DispenseFailureTransactionTest`, `PaymentIdempotencyTest`, `PrescriptionLifecycleConcurrencyTest`, `CancelPrescriptionServiceTest`, `ReservationExpiryTest`, `PharmacyOutboxIntegrationTest`, `PharmacyMigrationTest`. Đặt đúng package của tầng; dùng PostgreSQL thật cho locks/rollback. Test concurrency dùng barrier/latch và timeout hữu hạn, không chỉ sleep rồi đoán hai luồng đã chạy cùng lúc.

### 7.3. Lệnh kiểm chứng

Chạy tại root repository; Docker phải hoạt động cho nhóm integration:

```powershell
node scripts/changelog.js --summary
node scripts/changelog.js --limit 5
java -version
mvn -version
docker info
mvn -q -pl backend/pharmacy-service -am clean test
mvn -q -pl backend/pharmacy-service -am verify
git diff --check
```

Kiểm tra tương thích toàn reactor sau khi module xanh: `mvn -q verify`. Lỗi ở module khác phải ghi rõ module và log, không sửa ngoài quyền sở hữu để làm xanh.

POM pharmacy hiện không khai báo riêng Failsafe. Nếu đặt integration test đuôi `*IT`, phải bổ sung cấu hình chạy integration-test/verify và xác nhận test được discover; nếu dùng `*Test` như hiện tại thì kiểm tra Surefire reports. Không ghi “verify pass” khi test quan trọng bị skip do Docker hoặc không được discovery. CI gate phải kiểm tra số test/skip và bắt buộc chạy nhóm DB/Rabbit, không mặc định `disabledWithoutDocker` là đạt.

## 8. Phối hợp với các service khác

| Bên nhận | Nội dung handoff | Bằng chứng đóng phụ thuộc |
| --- | --- | --- |
| Gateway + Organization | D1/D6: account/staff identity, signed claims hoặc endpoint mapping, system actor, audit semantics | Contract đã duyệt + token/response fixture, test accountId khác staffId |
| Billing | D2–D5: payment receipt, amount, business key, nullable fields, late payment/cancel/expiry compensation, optional dispenseId | JSON fixtures hai chiều + test success/failure/redelivery/late-payment trên phiên bản thật |
| Medical Record | record/patient/doctor/department liên kết đúng; hợp đồng tạo/đọc đơn và quyền kê | Request fixture và kịch bản record hợp lệ/không hợp lệ; không truy cập DB chéo |
| Notification | Subscribe stock.low và các lifecycle cần thông báo; nhận lặp không gửi lặp ngoài chính sách | Consumer contract + kiểm thử event thực |
| Frontend | DTO lifecycle/dispenseStatus, GET detail, lỗi payment pending/403/422, cancel/dispense chỉ hiển thị đúng role | Gọi qua gateway; UX không thay backend authorization |
| CI/vận hành | PostgreSQL/Rabbit test, retry/DLQ, outbox backlog, migration/backup | Pipeline chạy test thật; runbook có replay và cảnh báo |

Khi triển khai, ghi yêu cầu cross-service trong tài liệu HANDOFF dưới `docs/` theo ownership hiện hành, gồm: provider/consumer, field/types/nullability, trigger, lỗi, idempotency, compatibility, fixture, người xác nhận và PR liên quan. Không chỉ ghi “chờ Billing” thiếu chi tiết.

## 9. Cách chia PR và theo dõi công việc

| Đợt | Nội dung gợi ý | Điều kiện trước review |
| --- | --- | --- |
| 1 | P0 + P1: sửa baseline và create/identity/correlation; cập nhật PR #74 nếu vẫn là phạm vi phù hợp | Test caller + ownership + create transaction đạt; D1 rõ |
| 2 | P2: GET chi tiết đơn | DTO contract và query/security tests đạt |
| 3 | P3: outbox hạ tầng pharmacy | Migration + broker failure/retry tests đạt |
| 4 | P4: transaction cấp/thất bại và lifecycle | Atomicity + rollback + concurrency DB thật đạt |
| 5 | P5: receipt/dedupe và Billing saga | Contract Billing + duplicate/late payment/recovery đạt |
| 6 | P6: hủy/TTL race và scheduler | Lifecycle race + batch recovery đạt |
| 7 | P7: kho/audit | Reserved protection + migration/audit tests đạt |
| 8 | P8 + phần còn lại P9: endpoint cấp, E2E, tài liệu release | Payment gate + manual/consumer race + toàn bộ DoD đạt |

Đây là thứ tự phụ thuộc kỹ thuật, không phải lịch ngày đã cam kết. Ưu tiên đợt 1 và 4–5 cho tính đúng; đợt 2 là phần độc lập nhỏ có thể hoàn thành trong thời gian chờ contract. Không tách merge một API cho phép cấp chưa thanh toán chỉ để đủ số endpoint.

Giữ `Huy` cho công việc hiện hữu khi phù hợp; nếu tạo nhánh task mới trong Codex dùng prefix mặc định `codex/`, ví dụ `codex/pharmacy-dispense-atomicity`, và ghi rõ base/dependency. Commit theo Conventional Commits, phạm vi pharmacy; không push trực tiếp master, không force push nhánh dùng chung. Merge/review theo quy trình nhóm.

Trạng thái task: TODO → IN_PROGRESS → BLOCKED hoặc READY_FOR_REVIEW → DONE. Khi cập nhật, ghi PR/SHA, lệnh test, số pass/fail/skip và dependency. Nếu cần phần trăm: số tiêu chí nghiệm thu đã chứng minh đạt / tổng tiêu chí của phạm vi đã chốt; không lấy số file/lớp/API hiện có làm phần trăm hoàn thành và không tính BLOCKED là DONE.

## 10. Definition of Done và việc bắt đầu ngay

- [ ] Baseline đã đồng bộ và thay đổi local được giữ; test sạch không còn lỗi compile/caller cũ.
- [ ] Account/staff identity đúng contract; mọi endpoint có RBAC, ownership nơi cần và audit phù hợp.
- [ ] Kê đơn snapshot giá, giữ tồn và tạo pending slip atomically; GET trả đúng lifecycle.
- [ ] Cấp chỉ khi có bằng chứng thanh toán hợp lệ; cập nhật stock/đơn/phiếu/reservation đầy đủ, đúng một lần.
- [ ] Lỗi nghiệp vụ rollback tồn rồi lưu failure/release/outbox bền; lỗi hạ tầng retry, không tự biến thành refund.
- [ ] Payment trùng, đến muộn và manual/consumer race được xử lý theo contract đã duyệt.
- [ ] Hủy/TTL không mất/giải phóng trùng reservation; scheduler không kẹt batch.
- [ ] Event quan trọng không mất khi broker lỗi; dedupe và replay an toàn; có retry/DLQ/quan sát.
- [ ] Điều chỉnh tồn không phá lượng đang giữ và có audit theo phạm vi đã chốt.
- [ ] Migration nâng dữ liệu cũ pass; không bịa payment evidence, không sửa migration đã chạy.
- [ ] BR-D1–BR-D12 cùng các ca race/rollback bắt buộc đã chạy trên DB/Rabbit thật; không còn skipped test quan trọng trong gate nghiệm thu.
- [ ] Contract cross-service, API examples và runbook cập nhật; E2E qua gateway có bằng chứng hoặc công bố dependency chưa hoàn tất, không gọi đó là release hoàn chỉnh.
- [ ] Verify module đạt, kết quả reactor được báo riêng; review tiêu chuẩn và human review hoàn tất trước merge.

**Đợt code nên giao tiếp theo:** P0 → P1, đồng thời chốt D1 với Gateway/Organization và D2–D5 với Billing. Sau đó triển khai P3 → P4 → P5; chưa mở endpoint dispense trước payment gate. Mỗi đợt dừng ở gate có thể kiểm chứng, cập nhật checklist rồi mới chuyển tiếp.

**Ngoài phạm vi kế hoạch cốt lõi:** nhà cung cấp, mua hàng, quản lý lô/FEFO, nhiều kho, trả thuốc sau cấp, dashboard/báo cáo mới, CRUD thuốc đầy đủ, frontend/mobile mới. Chỉ thêm khi có đặc tả và yêu cầu riêng; không dùng các tính năng này để trì hoãn sửa tính đúng của saga hiện tại.

## 11. Lệnh bắt buộc sau mỗi đợt code

Thực hiện tuần tự, tại root repository. Không push nếu audit còn Blocker hoặc verify thất bại.

```powershell
mvn -q -pl backend/pharmacy-service -am clean verify
git diff --check
git diff --stat
git status --short
```

Sau đó audit diff theo `docs/ai/` và đặc tả pharmacy: blueprint/package, boundary, RBAC, API envelope, event envelope/correlation, transaction/lock order, migration, Javadocs và test BR-D. Ghi kết quả theo ba nhóm Blocker / Should-fix / Nit cùng `file:line`; chỉ Blocker mới chặn push. Cuối cùng:

```powershell
git add <các file thuộc đợt triển khai>
git commit -m "feat(pharmacy): <mô tả ngắn theo Conventional Commits>"
git push origin <branch-hiện-tại>
```

Sau push, cập nhật PR bằng SHA, lệnh kiểm thử, số pass/fail/skip, các Should-fix còn lại và trạng thái Docker/Testcontainers. Không stage hoặc xóa thay đổi không thuộc đợt triển khai; không force push nhánh dùng chung.
