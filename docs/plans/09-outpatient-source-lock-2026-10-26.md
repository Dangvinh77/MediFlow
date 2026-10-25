# WF-01 — Đối chiếu hợp đồng ngoại trú với mã nguồn ngày 26/10/2026

## Phạm vi và mức xác nhận

Baseline Git: `dc2b8f2e5c9eb3c373a6feb0b2047ca22adf8317`, nhánh `master`, working tree có thay đổi
chưa commit. Đây là kiểm tra source tại local, không phải xác nhận remote mới nhất, binary đang chạy
hay nghiệm thu PostgreSQL/RabbitMQ. Không sửa production code, migration, fixture nguồn hoặc bật gate.

Nghiệp vụ do [02](02-business-flows.md) quyết định; hợp đồng chung do [03](03-contracts.md) quyết định.
Tài liệu này là evidence và danh sách chênh lệch để thực hiện [WF-01–11](07-outpatient-workflow-implementation.md),
không phát hành một bộ API mới. `CURRENT` nghĩa là có source; `TARGET GAP` nghĩa là còn phải thiết kế/code/test.

[Manifest SHA-256](source-lock/2026-10-26-outpatient.json) khóa các file được chọn, blob tại HEAD,
trạng thái khác HEAD, DTO records và hình dạng quan sát từ JSON fixtures. Hash tính trên **raw file bytes**;
đổi LF/CRLF cũng đổi hash. Hình dạng fixture chỉ là ví dụ quan sát, không chứng minh field luôn bắt buộc,
nullable hoặc consumer đã nghiệm thu. Danh sách không tuyên bố bao phủ toàn bộ repository.

`codebase-memory/list_projects` trả `Transport closed`; lượt này dùng đọc source/`rg` thay thế.
Không có kết quả graph được dùng làm bằng chứng vắng mặt tính năng.

**Kết luận:** đã ghi baseline đọc source; WF-01 chưa CLOSED. Hợp đồng mới cho schedule/reservation,
payment lease, revisit, support policy và consumer còn chưa có wire/fixtures được nghiệm thu.

## 1. Các phát hiện ảnh hưởng trực tiếp tới giao diện

| ID | CURRENT đã đọc | Hệ quả với nghiệp vụ mới |
|---|---|---|
| S-01 | `CreateAppointmentRequest` chỉ có patient/doctor/department/date/time/reason | Chưa truyền schedule, reservation, loại khám hay lượt trước |
| S-02 | `AppointmentApplicationService.create()` kiểm patient, eligible doctor/khoa, pending cùng ngày; `Appointment.validateSchedule()` kiểm ngày và 07:00–17:00 | Chưa chứng minh bác sĩ có ca, phòng phù hợp, sức chứa, khoảng nghỉ hoặc tranh suất cuối |
| S-03 | `create()` và `checkIn()` là hai request riêng; create chưa nhận idempotency key | Một màn hình ghép hai request có thể để lại phiếu PENDING khi request hai lỗi; chưa là đăng ký tại quầy nguyên tử |
| S-04 | `checkIn()` mở V1 EXAM, clearance đưa phiếu tới READY_FOR_EXAM | Đã có ranh giới thu phí trước khám; chưa có giữ suất và fact xác nhận lịch sau thanh toán |
| S-05 | `GET /appointments` nhận khoa, một ngày và phân trang | Chưa là lịch cá nhân ngày/tuần từ signed doctor identity; lọc ở frontend không bảo vệ dữ liệu |
| S-06 | Ledger thu số tiền dương, cấm INSURANCE như cash; cấp clearance khi request đủ tiền | Không gửi amount=0 để giả lập miễn phần người bệnh; chưa có eligibility riêng cho hỗ trợ toàn phần |
| S-07 | Discovery trả requested/paid/remaining; payment không trả trạng thái Clinical | Không hiển thị 'đã xác nhận lịch' chỉ từ payment HTTP 200 |
| S-08 | Clinical có order Lab đúng record, bác sĩ được phân công và IN_EXAM; Lab có create/start/result/cancel | Có nền tảng cùng lượt; cần đóng vòng đời chỉ định để chặn complete khi Lab bắt buộc chưa xong |
| S-09 | `ClinicalCareFinanceApplicationService.complete()` kiểm record/appointment/doctor/disposition; không gọi port kiểm chỉ định Lab pending trong đường này | Phải bổ sung rule/test trước khi tuyên bố không hoàn tất lượt có Lab bắt buộc còn chờ |
| S-10 | Notification còn handler `appointment.created`; Report có consumer/read API ngoại trú riêng | Phải phân biệt thông báo tiếp nhận với xác nhận đã thu; HTTP 200 của report không chứng minh đủ coverage |
| S-11 | FE AppointmentDTO thiếu cancellation audit fields có ở backend; FE change-status type chỉ có status | Khi sửa UI hủy cần đối chiếu `ChangeStatusRequest.reason`, không chỉ thêm nút hủy theo kiểu cũ |

Phạm vi tìm literal mới: `ConsultationSchedule`, `FollowUpRecommendation`, `reservationId`, `scheduleId`,
`previousVisit`, `visitType`, `supportAmount`, `policyVersion` trong main source Clinical/Billing/Core.
Không thấy các tên này trong lượt đọc; đây không là chứng minh mọi cách đặt tên tương đương đều vắng mặt.
Các kết luận S-01–09 dựa thêm vào request, domain và đường use case cụ thể bên dưới.

## 2. Manifest API CURRENT liên quan

Các path trong bảng là upstream Gateway `/api/v1/...`; frontend dùng shared `api` với `/v1/...`
để tạo same-origin `/api/v1/...`. Không gọi service port từ trình duyệt. Quyền bảng là controller;
Gateway có rule tương ứng nhưng resource ownership vẫn phải kiểm ở use case.

| Method/path | Quyền controller | Input và kết quả quan trọng |
|---|---|---|
| `POST /api/v1/appointments` | ADMIN, NURSE | `CreateAppointmentRequest`; 201, Location, `ApiResponse<AppointmentDTO>` |
| `GET /api/v1/appointments` | ADMIN, MANAGER, DOCTOR, NURSE | `departmentId?`, `appointmentDate?`, `page?`, `size?`; PageResult; không có doctor/week query |
| `GET /api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE | AppointmentDTO; MANAGER không tự được đọc detail từ quyền list |
| `GET /api/v1/appointments/patient/{patientId}` | ADMIN, DOCTOR, NURSE | List; PATIENT chưa được cấp quyền qua endpoint này |
| `PUT /api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE | Date/time/reason; domain chỉ cho PENDING, không đổi bác sĩ/khoa bằng request này |
| `PUT /api/v1/appointments/{id}/status` | ADMIN, DOCTOR, NURSE | `ChangeStatusRequest`; MVP tắt legacy non-CANCELLED write; V1 cancellation có audit |
| `PUT /api/v1/appointments/{id}/check-in` | ADMIN, NURSE | Không request body; trả AppointmentDTO; gate Clinical care-finance |
| `PUT /api/v1/appointments/{id}/start-exam` | ADMIN, DOCTOR | `StartExamRequest`; normal `{ "emergencyOverride": null }`; kiểm assigned doctor ở use case |
| `PUT /api/v1/records/{id}/complete` | ADMIN, DOCTOR | `CompleteRecordRequest`; disposition/note; không tự mở lại record cũ để tái khám |
| `POST /api/v1/records/{id}/lab-orders` | ADMIN, DOCTOR | `labType`, `requestedDate`, `priceCode`; 201 ClinicalLabOrderDTO |
| `GET /api/v1/lab-orders/{sourceOrderId}/context` | SYSTEM và ClinicalServicePrincipal | Correlation bắt buộc, actor ADMIN/DOCTOR phải khớp requestingDoctorId; internal API |
| `POST /api/v1/lab` | ADMIN, DOCTOR | CreateLabRequest; sourceOrderId và context V1 phải qua kiểm tra use case; 201 LabTestDTO |
| `PUT /api/v1/lab/{id}/start` | ADMIN, LAB_TECH | StartLabTestRequest; clearance hoặc explicit audited override |
| `PUT /api/v1/lab/{id}/results` | ADMIN, LAB_TECH | Results/conclusion/performedDate; không tự nhập trước điều kiện thực hiện |
| `PUT /api/v1/lab/{id}/cancel` | ADMIN, DOCTOR, LAB_TECH | CancelLabTestRequest; cancellation khác refund |
| `GET /api/v1/billing/payment-requests/by-source` | ADMIN, CASHIER | Đúng một giá trị cho mỗi query `purpose`, `sourceId`, `patientId`, `careEpisodeId`; key thừa/thiếu/nhân đôi bị 400 |
| `POST /api/v1/billing/payment-requests/{id}/payments` | ADMIN, CASHIER | Idempotency key trong **body**, không phải header; trả LedgerPaymentDTO |
| `POST /api/v1/billing/financial-clearances/{id}/execution-starts` | SYSTEM | EXAM chỉ clinical-service; LAB_TEST chỉ lab-service; correlation bắt buộc |
| `GET /api/v1/org/staff` | ADMIN, MANAGER, DOCTOR, NURSE | departmentId/jobTitle/keyword/activeOnly/page/size; directory không phải lịch làm việc |
| `GET /api/v1/org/staff/{id}/exists` | ROLE_SYSTEM_SERVICE | exists/eligibleDoctor/departmentId; Clinical hiện dùng endpoint này |
| `GET /api/v1/org/staff/{id}/lookup` | ROLE_SYSTEM_SERVICE | exists/active/jobTitle/departmentId/eligibleTeamRoles; không suy MANAGER scope từ role này |
| `GET /api/v1/org/rooms/{id}/lookup` | ROLE_SYSTEM_SERVICE | exists/active/roomId/departmentId/roomName/roomType; đã có lookup, chưa chứng minh đủ directory/quyền UI schedule |
| `GET /api/v1/org/staff/{id}/department` | Các staff roles, signed staffId phải bằng id | Own department; không là quyền quản lý mọi khoa |
| `GET /api/v1/notifications/patient/{patientId}` | ADMIN, NURSE, PATIENT | PATIENT kiểm ownership bằng signed patientId, không dùng account sub |
| `GET /api/v1/reports/outpatient/daily` | ADMIN, MANAGER | fromDate/toDate/departmentId?; profile + read gate; cần kiểm coverage |
| `GET /api/v1/reports/outpatient/monthly` | ADMIN, MANAGER | year 2000–2100, departmentId?; không suy role DOCTOR được xem báo cáo quản trị |

Source controllers, DTOs, filters và relevant tests có path/hash trong JSON manifest. Source-only không
khẳng định request thực tế trả 200; gate tắt, service unavailable hoặc downstream deny vẫn có thể xảy ra.

## 3. DTO, identity và lỗi CURRENT phải giữ

Các nguồn chính để review trực tiếp:

- [AppointmentController](../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/AppointmentController.java),
  [ClinicalCareFinanceController](../../backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalCareFinanceController.java),
  [AppointmentApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/AppointmentApplicationService.java),
  [Appointment](../../backend/clinical-service/src/main/java/com/mediflow/clinical/domain/model/Appointment.java).
- [ClinicalCareFinanceApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalCareFinanceApplicationService.java),
  [ClinicalLabOrderApplicationService](../../backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/ClinicalLabOrderApplicationService.java),
  [LabController](../../backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java).
- [LedgerPaymentService](../../backend/billing-service/src/main/java/com/mediflow/billing/application/service/LedgerPaymentService.java),
  [LedgerPaymentConfiguration](../../backend/billing-service/src/main/java/com/mediflow/billing/infrastructure/config/LedgerPaymentConfiguration.java),
  [LedgerPaymentRepositoryAdapter](../../backend/billing-service/src/main/java/com/mediflow/billing/infrastructure/persistence/adapter/LedgerPaymentRepositoryAdapter.java).
- [Gateway role rules](../../backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java),
  [StaffController](../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/StaffController.java),
  [RoomController](../../backend/core-service/src/main/java/com/mediflow/organization/web/controller/RoomController.java).

`CreateAppointmentRequest`: UUID `patientId`, `doctorId`, `departmentId` bắt buộc;
`appointmentDate` LocalDate FutureOrPresent; `appointmentTime` LocalTime `HH:mm` bắt buộc;
`reason` tối đa 1000. Không có `visitType`, `scheduleId`, `previousVisitId` hay `idempotencyKey`.

`AppointmentDTO`: identity/date/time/status/reason/timestamps; `careContractVersion` short;
examClearanceId/examClearanceAt/emergencyOverrideId/examPriceCode/checkedInAt/examinationStartedAt/completedAt;
cancelledBy/cancellationReason/cancelledAt. Có constructor compatibility không nhận audit cancellation.
Nullability cuối cùng phải xem serializer, trạng thái và validation; primitive/annotation không đủ mô tả mọi response.

`CreateLabRequest`: recordId/patientId/requestingDepartmentId/labType/requestedDate; thêm sourceOrderId,
careEpisodeType/careEpisodeId/priceCode cho V1. Constructor V0 không làm V1 fields trở thành tùy ý
trong mode ngoại trú. Clinical order là nguồn, không nhận sourceOrderId do UI tự bịa.

`CompleteLedgerPaymentRequest`: idempotencyKey nonblank tối đa 120; amount BigDecimal >0,
17 chữ số phần nguyên/2 phần lẻ; currency `[A-Z]{3}`; paymentMethod bắt buộc;
providerReference tối đa 120. Service cấm `INSURANCE`. Replay cùng key phải cùng request, actor,
amount/currency/method/providerReference; khác nội dung là `BILLING_IDEMPOTENCY_CONFLICT`.

LedgerPaymentDTO trả transactionId/accountId/paymentRequestId/classification/status/amount/currency/
paymentMethod/completedAt. Không có doctor/time/confirmed reservation. Discovery có expiresAt và observedAt,
nhưng chỉ là snapshot, không phải lease. Không lấy khoản phí bất kỳ cùng patient để mở lịch khác.

Identity khóa: appointment EXAM dùng sourceId=appointmentId và careEpisodeId=appointmentId;
record phát sinh khi start không được đổi episode. Lab sourceId là test identity (`labId` trong event);
Clinical `sourceOrderId` là identity khác. Prescription sourceId=prescriptionId; không đồng nhất accountId
trong ledger với accountId đăng nhập. JWT `sub` là account đăng nhập, `staffId`/`patientId` là claims riêng.

Các mã lỗi quan sát cần giữ hoặc version rõ khi đổi: `PATIENT_NOT_FOUND_REMOTE`,
`DOCTOR_NOT_FOUND_REMOTE`, `DOCTOR_WRONG_DEPARTMENT`, `APPOINTMENT_PAST_DATE`,
`APPOINTMENT_TIME_OUT_OF_HOURS`, `CLINICAL_V0_STATUS_WRITE_DISABLED`,
`CLINICAL_EXAM_CLEARANCE_REQUIRED`, `CLINICAL_CLEARANCE_EXPIRED`,
`CLINICAL_LAB_ORDER_CONTEXT_MISMATCH`, `BILLING_REQUEST_NOT_PAYABLE`, `BILLING_REQUEST_EXPIRED`,
`BILLING_PAYMENT_EXCEEDS_REQUEST`, `BILLING_INSURANCE_IS_NOT_CASH`, `BILLING_IDEMPOTENCY_CONFLICT`.
Không tự gán HTTP status cho mọi business code từ tên lỗi; controller advice/filter là nguồn mapping.

### Ports trong đường đi đã đối chiếu

`port in` là giao diện use case nhận lệnh/đọc; `port out` là phụ thuộc do application khai báo.
Đây là ranh giới code Clean Architecture, không phải cổng mạng 8080/8082. Trong các file được chọn,
manifest khóa raw source của các interface Clinical/Lab và các Ledger ports liên quan.

| Context | Port in / lệnh | Port out / phụ thuộc cần giữ |
|---|---|---|
| Clinical đăng ký | `ManageAppointmentUseCase.create/update/search` | `AppointmentRepositoryPort`, `PatientLookupPort`, `StaffLookupPort`, `ClinicalEventPublisherPort` |
| Clinical tiếp nhận/khám | `ManageExamGateUseCase.checkIn/startExam` | `ExamPricePolicyPort`, `ExamClearanceRepositoryPort`, `FinancialExecutionAuthorityPort`, `ClinicalOutboxPort` |
| Clinical kết thúc | `CompleteMedicalRecordUseCase.complete` | Record/appointment stores, actor/staff authority, outbox; chưa có dependency kiểm Lab pending trong đường complete đã đọc |
| Clinical chỉ định Lab | `CreateClinicalLabOrderUseCase`, `GetClinicalLabOrderContextUseCase` | `ClinicalLabOrderRepositoryPort`, record/appointment stores và staff authority |
| Lab | `ManageLabTestUseCase.create/start/addResults/cancel` | Boundary lookup/clearance/store/event của Lab; exact interface paths được khóa trong manifest, không truy cập store Clinical |
| Billing thu | `ProcessLedgerPaymentUseCase.complete` | `LedgerPaymentRepositoryPort`, `LedgerEventPort`; TransactionTemplate tại composition config |

Ví dụ `AppointmentRepositoryPort.search(departmentId, appointmentDate, page)` hiện không nhận
doctorId hoặc khoảng tuần. Đổi FE filter không tự bổ sung khả năng own-calendar ở port này.

## 4. Event/fixture CURRENT

| Fact | Producer fixture cần giữ nguyên | Điều được chứng minh bởi ví dụ và source |
|---|---|---|
| appointment.status.changed V1 | Clinical `contracts/appointment.status.changed.v1.json` | PENDING → AWAITING_PAYMENT; EXAM source, episode, priceCode; chưa có reservation hoặc visitType |
| lab.request.created V1 | Lab `contracts/lab.request.created.v1.json` | labId, recordId, patient, department, sourceOrderId, sourceType/sourceId, priceCode, episode |
| prescription.created V1 outpatient | Pharmacy `contracts/care-finance-v1/prescription.created.outpatient.v1.json` | Items drugId/drugName/quantity/unitPrice, totalAmount, source/episode; Pharmacy owns catalogue snapshot |
| payment.completed V1 | Billing `contracts/ledger-v1/payment-service.json` | Transaction/invoice/request/ledger account, cash totalAmount/currency/method/classification/completedAt; không cấp quyền khám |
| financial.clearance.granted V1 EXAM | Billing `contracts/ledger-v1/clearance-exam.json` | Exact purpose/target/amount/currency/expiry; consumer Clinical còn đối chiếu target |
| financial.clearance.granted V1 Lab | Lab `contracts/financial.clearance.granted.v1.json` | Ví dụ consumer Lab; cần đối chiếu với producer serializer, không suy hai fixture bằng bytes |
| medicalrecord.completed V1 | Clinical `contracts/medicalrecord.completed.v1.json` | Completion fact hiện có; chưa đủ phân loại khám mới/tái khám theo mô hình mới |
| appointment.exam.cancelled / lab.request.cancelled | Clinical/Lab các thư mục cancellation V1 | Cancellation có audit; Billing ghi nghĩa vụ xử lý, không là tiền đã hoàn |

Mỗi fixture trong manifest có raw SHA-256 và shape quan sát. Không sao chép fixture rồi thay ID/tiền
để gọi đó là producer acceptance. Tên thư mục/class chứa `V2` không đổi integer envelope `version: 1`.
Không dùng fixture V0 phẳng cho event V1 envelope hoặc tự thêm suffix `.v1` vào routing key.

`LedgerPaymentConfiguration` bọc service bằng TransactionTemplate; repository và event adapter yêu cầu
transaction hiện hữu. Payment/request/allocation/clearance/outbox là hiệu ứng local cần giữ cùng commit.
`LedgerPaymentRepositoryAdapter.verifyRequestTargets()` đối soát charge/target trong Billing store;
đây **không** là lời gọi Clinical giữ suất. `execution-starts` đang bảo vệ bắt đầu thực hiện sau có
clearance, không phải quyền giữ chỗ trước thu tiền của nghiệp vụ mới.

## 5. Feature gates và phạm vi chứng cứ

| Lớp cấu hình | Quan sát từ file |
|---|---|
| Clinical outpatient profile | outpatient-mvp=true, durable events=true; `mediflow.features.care-finance-v2.enabled` từ MEDIFLOW_CARE_FINANCE_V2_ENABLED mặc định false; admission-referral=false |
| Lab outpatient profile | outpatient-mvp=true; `mediflow.features.care-finance-v2` từ MEDIFLOW_CARE_FINANCE_V2 mặc định false |
| Billing application.yml | ledger/discovery/clearance-lookup/exam/lab consumers/outpatient publication mặc định false theo environment |
| Root compose | Bật ledger và exam/lab charge consumers cho Billing; các gate khác phải đọc compose/environment hiệu lực |
| compose.demo.local.yml hiện có ở local | Bật Clinical/Lab care-finance, Lab context, discovery/publication, Pharmacy outpatient, Report consumer/read, Notification care V1, UI outpatient |

Hai tên property Clinical/Lab khác nhau phải giữ chính xác. Chỉ có source override không chứng minh
container dùng override hoặc đã rebuild. Trước acceptance cần `docker compose ... config` và kiểm
artifact/flags thực tế; không export secrets vào evidence. Lượt này không thực hiện runtime check.

## 6. Khoảng trống cần đóng trước implementation tương ứng

Các tên fixture dưới đây là **fixture dự kiến**, chưa tồn tại/chưa được ký nghiệm thu. Không đưa vào
thư mục producer contracts như bằng chứng đã phát hành. Exact route/field/schema mới phải được owner
chốt ở 03 và fixture cùng thay đổi implementation, không tự dựng endpoint từ tên use case.

| Gap | Owner / consumer | Fixture dự kiến và kiểm thử phải có | Gate bị chặn |
|---|---|---|---|
| G-01 Schedule session/capacity/revision/room/doctor; không chồng ca | Vinh/Clinical; HA/Core/Gateway | schedule-valid, overlap-doctor, overlap-room, last-capacity-race, dependency-unavailable; PG concurrency | WF-02/SCH-01–03 |
| G-02 Signed MANAGER department scope và DOCTOR own day/week; room directory tối thiểu | HA/Core/Gateway → Clinical/FE | manager-own/other-department, doctor-own/other-week, room-inactive/wrong-department; service/human JWT, gateway allow/deny | WF-02/WF-10/SCH-04–06 |
| G-03 Đăng ký tại quầy idempotent; booked check-in riêng | Vinh/Clinical → Billing/FE | reception-first/replay/conflicting-intent; concurrent key; rollback không orphan reservation | WF-03 |
| G-04 Reservation expiry và payment-operation lease trước nhận tiền | Vinh/Clinical + Billing; HA nếu cần internal route | hold-expired, lease-replay, payment-timeout, paid-after-expiry, cancel-vs-pay, capacity-not-oversold; PG/Rabbit/recovery | WF-03/WF-06 |
| G-05 New visit/follow-up link/recommendation/window/doctor replacement | Vinh/Clinical → Billing/Report/FE | revisit-same-patient, wrong-patient, old-doctor-unavailable, result-return-same-visit; không copy order/EXAM | WF-04 |
| G-06 Server quote/policy snapshot/gross-support-patient payable | Vinh/Billing; Huy/Pharmacy/Report | support-exam-50, followup-50, CBC-70, glucose-50, uncovered-0; line rounding/replay/policy-change | WF-05/WF-08–09 |
| G-07 Zero-liability eligibility riêng, refund chỉ patient cash | Vinh/Billing → Clinical/Lab/Pharmacy/Support | zero-payable-no-payment, source-bound-eligibility, partial-refund, duplicate-refund, support-reversal-not-cash | WF-06 |
| G-08 Clinical theo dõi chỉ định bắt buộc kết thúc và quay lại đọc kết quả | Vinh/Clinical + Lab | pending-required-blocks-complete, cancelled-with-reason, result-replay, read-result-no-second-exam-charge | WF-07 |
| G-09 Confirmation/change facts và notice sau financial eligibility + reservation | Vinh/Clinical/Notification; Huy/Report | received-not-confirmed, confirmed-once, rebook-after-paid, cancelled-before-confirm, duplicate/out-of-order | WF-09 |
| G-10 Report coverage/visit type/support/cash/refund không double count | Huy/Report ← Vinh producers | new-vs-followup-count, support-not-cash, late-fact-coverage, refund-once, missing-coverage-unavailable | WF-09 |
| G-11 FE contracts và role views theo server, cancellation reason/audit | Các owner FE, HA/Gateway | API mirror tests, NURSE reception, CASHIER quote/payment, DOCTOR own day/week, PATIENT private notice, role deny | WF-10 |

G-06 dùng chính sách **mô phỏng** đã chốt trong 02: giả định thẻ hợp lệ; tỷ lệ do đồ án chọn,
không BHXH settlement. Giá/tỷ lệ/ví dụ trong thiết kế không thay bảng giá đang chạy.
Không áp admission insurance kernel cho ngoại trú chỉ vì source tồn tại.

Phần cần owner khác được đăng ký tại [handoff WF-01](../handoffs/HANDOFF-WF01-OUTPATIENT-CONTRACT-GAPS-2026-10-26.md).
Không giao lại task H-01–09 vốn là baseline verification và không tự sửa production Core/Gateway/Pharmacy/Report.

## 7. Điều kiện đóng WF-01 và thứ tự làm tiếp

| Điều kiện | Trạng thái ngày 26/10 |
|---|---|
| HEAD và raw source/fixture fingerprints, route/DTO/gate baseline, giới hạn chứng cứ | Đã ghi nhận trong lượt source audit |
| Các gap có owner, consumer, fixture dự kiến và test acceptance | Đã ghi G-01–11 và handoff |
| Wire mới có method/path/roles/ownership/errors/version/nullable/replay/expiry đã chốt trong 03 | TARGET G-01–04 và phần G-09/G-11 đã đặc tả ở phụ lục A; các gap còn lại chưa đạt |
| Golden producer fixtures và consumer nghiệm thu cùng bytes, nguồn giá/authority đủ | Chưa đạt |
| Phạm vi MANAGER, room và payment lease được xác nhận từ producer, không suy từ UI | Chưa đạt |
| Runtime/API/UI/PG/Rabbit acceptance của luồng mới | Chưa chạy; thuộc WF tương ứng và WF-11 |

Tiếp nối source audit, [03 phụ lục A](03-contracts.md#opd-scheduling-v2) đã đặc tả target wire
`OPD-SCHED-2026-10-26` revision 1 và [10](10-scheduling-contract-acceptance.md) ghi các mốc C-01–06,
SC-01–20 cùng schema examples. Đây là cập nhật thiết kế, không sửa baseline raw bytes hoặc xác nhận
producer đã phát hành. Tiếp theo nghiệm thu Core authority ở C-01, rồi Clinical session/hold và Billing
operation/recovery; giữ gates OFF tới acceptance. Sau đó WF-04/05 và consumer/UI theo dependency.
Không chạy một đợt viết lại toàn backend/frontend khi hợp đồng liên service còn mơ hồ.

Các suite hiện có như ClinicalCareFinanceApplicationServiceTest, ClinicalLabOrderContextContractFixtureTest,
LedgerPaymentServiceTest, LedgerPaymentIntegrationTest và LabCareFinanceV2ContractFixtureTest là điểm regression.
Tên file/test trong manifest chứng minh source tồn tại, không chứng minh vừa chạy hay pass.

## 8. Kiểm chứng lượt tài liệu

Kiểm raw SHA-256 của từng file được khóa, JSON parse, DTO record snapshot khớp source, liên kết nội bộ và
`git diff --check`. Không dùng kiểm tra Markdown/hash để thay kiểm thử nghiệp vụ hoặc E2E.
Khi source thay đổi sau đợt này: tạo manifest revision mới với provenance; không ghi đè baseline để che drift.

Kết quả kiểm tra lượt này: 265 file khớp SHA-256 đã ghi; 57 JSON fixtures parse được;
77 snapshot khai báo record khớp source; 92 liên kết nội bộ trong 7 tài liệu liên quan có đích tồn tại;
`git diff --check` đạt. Không xác minh mọi fragment anchor. File manifest duy nhất khác raw blob HEAD
là `compose.demo.local.yml` đang untracked, được ghi rõ trong manifest, không thuộc baseline đã commit.
Không chạy lại suite ứng dụng hoặc acceptance runtime trong lượt này.
