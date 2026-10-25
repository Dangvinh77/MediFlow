# Triển khai điều chỉnh nghiệp vụ ngoại trú — 25/10/2026

Đây là kế hoạch phái sinh từ [luồng nghiệp vụ](02-business-flows.md), không là nguồn
contract thứ hai. Người dùng giao chỉnh tài liệu; lượt này không triển khai source,
migration, cấu hình BHYT hoặc thay dữ liệu demo. Các phần việc dưới đây đều chưa nghiệm thu.
Không cộng các task này vào tỷ lệ 18 task/72 subtask của baseline triển khai 14/10.

## Các quyết định đã chốt

- Tiếp nhận tại quầy gộp đăng ký và xác nhận có mặt; lịch đặt trước vẫn cần check-in khi đến.
- Chọn lịch khám còn suất trước thu tiền; chỉ xác nhận phiếu và vào hàng chờ sau đủ nghĩa vụ tài chính.
- Tái khám là lượt mới liên kết lần trước; đọc kết quả trong lượt đang mở là tiếp tục khám.
- BHYT là hỗ trợ mô phỏng theo danh mục, mặc định mọi người đủ điều kiện/thẻ còn hạn;
  tỷ lệ do dự án tự quy định, không tra cứu hoặc quyết toán với cơ quan BHXH.
- Clinical quản lý lịch khám ngoại trú và giữ suất. Organization cung cấp staff/khoa/đủ điều kiện.
- Quản lý lịch theo buổi/sức chứa; DOCTOR xem lịch cá nhân ngày/tuần, không tự sửa/xóa ca.
  TTL giữ chỗ 15 phút không là thời lượng khám; không mở chấm công hoặc tự động phân ca.
- Không tự sửa ý nghĩa wire V1 hoặc ghi đè snapshot tiền cũ để áp dụng chính sách mới.

## Work packages theo thứ tự phụ thuộc

| ID | Phần việc, owner backend | Tiêu chí đóng |
|---|---|---|
| WF-01 | Khóa baseline và fixtures, Vinh điều phối + các owner | Manifest route/DTO/event/flags hiện tại; từng gap có fixture dự kiến, owner và test |
| WF-02 | Lịch khám theo buổi và giữ suất, Vinh/Clinical; phối hợp Hoàng Anh/Core | Nhân sự/khoa/phòng hợp lệ, phạm vi MANAGER rõ; lịch không chồng; tranh suất cuối chỉ một người giữ; hủy/đổi có audit |
| WF-03 | Tiếp nhận và xác nhận phiếu, Vinh/Clinical + Billing | Một màn hình tại quầy; đăng ký trước thanh toán; timeout/late payment không mất tiền hay vượt sức chứa |
| WF-04 | Khám mới/tái khám/hẹn tái khám, Vinh/Clinical | Liên kết lượt trước đúng bệnh nhân; bác sĩ cũ có thể thay hợp lệ; không copy đơn/kết quả/diagnosis thành chỉ định mới |
| WF-05 | Chính sách hỗ trợ và quote bất biến, Vinh/Billing | Tính theo từng dòng, version/rounding rõ; không tự tính BHYT từ frontend hoặc role PATIENT |
| WF-06 | Thu phí/miễn phần người bệnh/hoàn, Vinh/Billing | Zero payable có chứng từ eligibility riêng, không fake cash; refund không quá patient paid và không double refund |
| WF-07 | Lab và vòng quay lại đọc kết quả, Vinh/Lab + Clinical | Không hoàn tất lượt khi chỉ định bắt buộc còn pending; hủy có lý do; không sinh EXAM charge lần hai |
| WF-08 | Giá thuốc/điều kiện cấp phát, Huy/Pharmacy + Vinh/Billing | Giữ snapshot catalogue và stock guards; hỗ trợ thuốc chỉ khi có item policy, không client price |
| WF-09 | Report và Notification, Huy/Report + Vinh/Notification | Không tính hỗ trợ thành cash; đăng ký chưa thu không thông báo xác nhận; replay không double count/send |
| WF-10 | Tích hợp frontend theo owner, Gateway Hoàng Anh | Quản lý buổi khám và DOCTOR xem lịch ngày/tuần theo SCH-01–06; màn hình cùng lượt/đúng role; lỗi dependency không dùng danh sách cũ để tạo mới; human/internal routes đúng quyền |
| WF-11 | Nghiệm thu và chuyển hướng dẫn demo, toàn nhóm | Ma trận WF trong 06 đạt qua API/UI, real PG/Rabbit, source commit mới và evidence; chỉ sau đó thay nhãn baseline |

Frontend giữ ownership ở [15-frontend-ownership](../ai/15-frontend-ownership.md), không suy
quyền sửa Billing/Notification frontend từ ownership backend. Tài liệu này không giao quyền
production cho owner khác; gap producer được bàn giao theo root AGENTS.

## Source lock trước triển khai

Đã bổ sung [source audit 26/10](09-outpatient-source-lock-2026-10-26.md), manifest fingerprints và
[handoff liên owner](../handoffs/HANDOFF-WF01-OUTPATIENT-CONTRACT-GAPS-2026-10-26.md).
Baseline đã được ghi; WF-01 vẫn mở vì wire mới và golden fixture acceptance chưa hoàn tất.

Cập nhật thiết kế tiếp ngày 26/10: [03 phụ lục A](03-contracts.md#opd-scheduling-v2)
đã đặc tả TARGET `OPD-SCHED-2026-10-26` revision 1 cho schedule, Core department scope,
NEW_VISIT registration và payment lease/recovery. [Ma trận SC-01–20](10-scheduling-contract-acceptance.md)
ghi thứ tự nghiệm thu và giới hạn của schema examples. Đây là phần thiết kế của G-01–04 và
một phần G-09/G-11; chưa có producer/consumer code, chưa đóng WF-01 hoặc các gap còn lại.

| Nguồn đã đối chiếu trong lượt tài liệu | Hiện trạng quan sát | Gap với nghiệp vụ mới |
|---|---|---|
| `AppointmentApplicationService.create()` | Kiểm patient, bác sĩ/khoa và pending cùng ngày | Chưa thấy kiểm lịch khám/capacity trong đường tạo này |
| `Appointment.validateSchedule()` | Kiểm ngày không quá khứ và khung 07:00–17:00 | Không thay kiểm ca, khoảng nghỉ hoặc suất cuối |
| `Appointment.checkIn()` | Chuyển pending sang awaiting payment và mở nguồn EXAM | UI tại quầy cần orchestration, không đổi nghĩa live command |
| `Appointment.recordExamClearance()` | Cho phép ready theo exact grant | Chưa chứng minh hold/confirmation và zero-pay policy mới |
| Baseline contract manifest 03 | Có exact EXAM/LAB_TEST/PRESCRIPTION và event/idempotency | Chưa khóa wire cho schedule/revisit/support breakdown; phải audit cả producer/consumer |

Đây là nhận xét source giới hạn, không là kết quả chạy test hoặc kết luận mọi đường đi trong
service đều thiếu tính năng. WF-01 phải mở rộng source lock trước khi sửa production.

Tối thiểu đọc/khóa `CreateAppointmentRequest`, `Appointment`, `AppointmentApplicationService`,
`ClinicalCareFinanceController`, Clinical order/context DTO, Lab request/clearance/receipt DTO,
Billing quote/payment/allocation/clearance DTO và fixtures `lab.request.created`,
`financial.clearance.granted`, `prescription.created`, `payment.completed`.
Các tên khái niệm trong 02/03 chưa là field/route Java thực tế.

Không dùng kernel bảo hiểm admission/deferred làm bằng chứng đã có BHYT ngoại trú.
Không bật ADMISSION/SURGERY, sửa migration đã phát hành hoặc sửa snapshot SQL để demo đạt.

## Cập nhật tài liệu và xử lý tài liệu cũ

| Nhóm | Xử lý ngày 25/10 | Lý do |
|---|---|---|
| 01–06 và chỉ mục docs | Cập nhật phạm vi, luồng, contract gap, reliability, acceptance | Một nguồn nghiệp vụ hiện hành |
| Context guides backend và frontend Clinical/Lab/Billing/Core/Report/Notification/Pharmacy | Cập nhật trách nhiệm và chỉ dẫn tới 02/03/06 | Không tạo một đặc tả song song |
| Ba hướng dẫn demo chi tiết + README demo | Gắn rõ baseline mã nguồn, giới hạn với thiết kế mới; bỏ hướng dẫn checkout branch cũ trong README | Tránh mô tả tính năng mới là đã chạy |
| `2026-10-14-ten-to-six-service-consolidation-plan.md` | Xóa pointer dư thừa không có inbound Markdown link trong repo | Nội dung đã thay bằng 05; không còn giá trị riêng |
| Evidence, review theo ngày, plan có kết quả lịch sử | Giữ, đánh dấu vai trò lịch sử tại chỉ mục tương ứng | Không xóa bằng chứng đã kiểm thử hoặc ghi lại lịch sử thành thiết kế mới |
| Compatibility pointers được AGENTS/skills tham chiếu | Giữ đường dẫn; trỏ về nguồn canonical | Tránh làm hỏng entry point của công cụ/teammate |

Không xóa source, SQL snapshot, dữ liệu, migration, handoff chưa đạt acceptance hoặc evidence
chỉ vì có ngày cũ. Tài liệu từng đúng với binary cũ là baseline lịch sử, không phải đặc tả mới.

Đợt chuẩn hóa tiếp theo được ghi tại [biên bản 26/10/2026](08-documentation-cleanup-2026-10-26.md);
trạng thái bàn giao được quản lý tại [registry hiện hành](../handoffs/README.md).
