# H-10 — Hợp đồng liên owner cho điều chỉnh ngoại trú

**Status:** NEEDS REVALIDATION. **Ngày:** 26/10/2026.
**Điều phối/resume:** Vinh. **Baseline:** `dc2b8f2e5c9eb3c373a6feb0b2047ca22adf8317` + working tree local.
**Phạm vi:** hợp đồng và acceptance WF-02–10. Người dùng đã giao quyền task-scoped
triển khai các module liên quan trên toàn repo; source revision 2 nằm trong PR triển khai.
Các mục cần chốt/fixture dự kiến bên dưới ghi nguồn gốc gap, không là kết luận source mới còn thiếu.
Đối chiếu [trạng thái triển khai](../plans/07-outpatient-workflow-implementation.md) và
[runbook V2](../../scripts/demo_25_10/MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md) trước khi giao sửa tiếp.

Đọc [source audit và G-01–11](../plans/09-outpatient-source-lock-2026-10-26.md),
[manifest raw SHA-256](../plans/source-lock/2026-10-26-outpatient.json), [03](../plans/03-contracts.md)
và [registry](README.md). Tài liệu này chưa có producer acceptance; không đánh CLOSED từ số test lịch sử.

**Cập nhật thiết kế 26/10:** [03 phụ lục A](../plans/03-contracts.md#opd-scheduling-v2) đã đặc tả
TARGET `OPD-SCHED-2026-10-26` revision 1 cho schedule, department scope, NEW_VISIT và payment
lease/recovery; [10](../plans/10-scheduling-contract-acceptance.md) có schema/ví dụ và SC-01–20.
Các mục “cần chốt” bên dưới phải được triển khai/đối chiếu theo wire này, không mở một schema thứ hai.
G-06/07/08/10 và phần follow-up, consumer còn lại chưa được đóng. Handoff vẫn ACTIVE:
thiết kế cụ thể không thay producer fixtures, owner acceptance hoặc runtime evidence.

## 1. Hoàng Anh/Core/Gateway → Vinh/Clinical và các consumer frontend

**Đã có, không yêu cầu làm lại:**

- StaffController `GET /api/v1/org/staff/{id}/exists` trả exists/eligibleDoctor/departmentId;
  Clinical OrganizationFeignClient/StaffLookupAdapter đang dùng đúng đường này.
- `GET /api/v1/org/staff/{id}/lookup` có active/jobTitle/departmentId/eligibleTeamRoles.
- RoomController `GET /api/v1/org/rooms/{id}/lookup` có exists/active/roomId/departmentId/roomName/roomType.
- OwnStaffDepartmentController đọc khoa của signed staffId, không là quyền quản lý mọi khoa.
- Gateway RouteAuthorizationFilter có rules appointments hiện tại; chưa phải schedule API mới.

**Cần owner chốt:**

1. Nguồn authoritative cho phạm vi khoa MANAGER được quản lý: lấy từ identity/lookup nào,
   thời điểm kiểm lại, trả missing khác unavailable, hành vi khi nhân sự đổi khoa hoặc mất quyền.
   Không suy MANAGER luôn là trưởng khoa hay staff.departmentId luôn cấp quyền quản lý.
2. Khả năng dùng room lookup cho Clinical với đúng service credential, room types được nhận
   làm phòng khám và tính hợp lệ cùng khoa. Lookup phòng đã tồn tại; không yêu cầu xây lại.
3. Nếu UI quản lý ca cần chọn phòng: chốt directory tối thiểu, method/path/query/roles/DTO;
   không cho UI gọi internal lookup hoặc nhận toàn bộ thông tin nhân sự nhạy cảm.
4. Khi Clinical đã chốt schedule/self-calendar routes: Gateway cho đúng method/role;
   không nới wildcard, không để PATIENT đọc danh sách khám của bác sĩ.

**Fixture dự kiến, chưa phát hành:** manager-own-department, manager-other-department,
manager-scope-unavailable, doctor-own-calendar, doctor-other-calendar, room-active-same-department,
room-inactive, room-other-department, room-missing, room-provider-unavailable.

**Acceptance:** exact request/response/error/nullable/service-subject trong 03; producer fixture và
consumer test dùng cùng bytes; Gateway/service role allow/deny, signed identity và changed-scope cases.
Không đồng nhất directory hiển thị ở H-01 với authority cấp quyền ở mục này.

## 2. Vinh/Clinical/Billing/Lab/Notification → Huy/Pharmacy/Report

**Pharmacy hiện có:** outpatient prescription.created V1 giữ giá catalogue theo items và totalAmount,
record/episode/source. Đọc fixture
`backend/pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.outpatient.v1.json`.
Không yêu cầu Pharmacy tự tính BHYT hoặc đổi totalAmount V1 thành tiền người bệnh phải đóng.

**Report hiện có:** OutpatientReportFactConsumer và OutpatientReportController; có coverage gates.
Không dùng số liệu legacy hoặc HTTP 200 làm bằng chứng đủ coverage cho nghiệp vụ mới.

**Cần phối hợp trước thay producer:**

1. Vinh khóa mô hình quote/policy/eligibility của Billing, visit/revisit/confirmation của Clinical.
   Huy xác nhận Pharmacy giữ nguồn giá/stock và cách nhận quyền cấp phát khi liability bằng 0;
   không suy clearance cũ đã hỗ trợ zero-payable.
2. Huy khóa consumer Report phân biệt gross/support/patient payable/cash received/refund,
   loại lượt và coverage. Support mô phỏng không là cash hoặc khoản phải thu BHXH.
3. Vinh/Notification dùng confirmation fact sau eligibility và reservation hợp lệ; appointment.created
   chỉ có thể là notice tiếp nhận theo hợp đồng mới, không chứng minh lịch đã thanh toán.
4. Chốt event name/version mới hoặc additive compatibility có kiểm thử; không đổi ý nghĩa V1 totalAmount,
   không sửa raw fixture cũ và không dựa vào tên class V2 để suy integer envelope version.

**Fixture dự kiến:** supported-exam, supported-lab, uncovered-prescription, zero-patient-payable,
patient-cash-refund, support-adjustment-not-cash, followup-new-visit, same-visit-result-return,
registration-before-payment, appointment-confirmed, confirmation-replay, late-fact-coverage.

**Acceptance:** producer serializer → exact fixture → consumer effect dùng cùng bytes; same eventId
same bytes no-op, changed bytes conflict, cùng business operation không double effect; Report đối soát
cash/support/refund và thiếu coverage; Notification không gửi xác nhận trước eligibility.

## 3. Vinh/Clinical ↔ Vinh/Billing: payment lease cần được khóa trước tích hợp

Dù cùng owner, đây vẫn là hai context/store riêng. LedgerPaymentService hiện chỉ xác minh Billing
charge/target; execution-starts là quyền bắt đầu khám/Lab sau có clearance, không là reservation lease.

Phải chốt operation identity/revision, acquire/replay/reject/expire/reconcile và hành vi unknown payment
outcome. Clinical capacity lock là local; Billing payment/allocation/outbox cũng local. Không XA,
không query DB của nhau, không tự trả lại suất đang gắn payment chưa rõ kết quả.

Fixture dự kiến: last-seat-concurrent, lease-replay, expired-before-payment, provider-unavailable,
payment-timeout-then-replay, paid-but-confirmation-delayed, cancel-vs-pay, late-payment-reconciliation.
Test bắt buộc có concurrency/rollback/restart trên PostgreSQL và delivery/replay RabbitMQ của hai phía.

## 4. Điều kiện đóng và bàn giao tiếp

- G-02/G-04/G-06/G-09/G-10 và các phần liên quan có exact contracts trong 03, source paths và
  producer/consumer fixtures; ghi rõ contract nào đã code và contract nào vẫn target.
- Owner từng phía xác nhận test acceptance tương ứng; runtime evidence gắn candidate commit/artifact/flags.
- Không tự đóng H-01–09, không mở Inpatient/Surgery, không tự mở production gates.
- Sau acceptance hai phía, đưa quy tắc lâu dài vào 02/03/04 và service runbook, giữ evidence,
  rồi xóa handoff này và registry entry theo lifecycle hiện hành.

**Trạng thái hiện tại:** chưa có wire mới được phát hành hoặc consumer acceptance trong lượt tài liệu.
Không có message được gửi sang chat của owner; đây là hồ sơ bàn giao trong repository.
