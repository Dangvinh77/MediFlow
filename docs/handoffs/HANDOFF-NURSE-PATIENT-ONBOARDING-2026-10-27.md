# HANDOFF — cấp tài khoản PATIENT tại quầy và quyền cổng bệnh nhân

Trạng thái: **NEEDS REVALIDATION**. Source đã triển khai từ baseline `f28d25b4` theo
quyết định nghiệp vụ 27/10/2026 và yêu cầu người dùng code/tạo PR. Quy tắc authoritative tại
[02 mục 4.1](../plans/02-business-flows.md); contract đã khóa tại [03](../plans/03-contracts.md).
[Bằng chứng source](../plans/11-patient-onboarding-source-acceptance-2026-10-27.md) phân biệt
tests đã chạy với packaged/Gateway/UI acceptance còn mở. Đây không là nguồn spec thứ hai.

## Owner và consumer

- Hoàng Anh/Core Organization/Patient/Gateway: producer hồ sơ, PATIENT account và quyền route;
  frontend Patient/Organization theo ownership canonical.
- Vinh/Clinical/Lab: producer own-resource kết quả khám, kết quả Lab và lời hẹn;
  frontend Appointment/Medical-record/Lab theo ownership canonical.
- Vinh/Notification: producer in-app history; frontend Notification theo owner ở docs/ai/15.
- Consumer: màn hình tiếp nhận và cổng bệnh nhân; Vinh phụ trách verification liên module.

## Kết quả triển khai trong phạm vi task

- Command riêng `POST /api/v1/org/patient-accounts` cho ADMIN/NURSE, chỉ nhận patientId;
  endpoint quản trị account tổng quát vẫn ADMIN. Application port đọc Patient public use case.
- Khóa owner và receipt trong Organization, chặn account trùng kể cả inactive; cùng key
  khôi phục outcome sau sửa CCCD/archive và sau đổi mật khẩu, không reset credential.
- Giao diện sau lưu hồ sơ giữ patientId và key khi lỗi/timeout; tiếp tục đăng ký khám dù chưa
  dùng web. Người cũ dùng hồ sơ/account cũ. Password gate dựa trên account flag hiện hành.
- PATIENT đọc own completed exam/Lab qua route `mine`; mapping từ verified principal,
  database paging, không nhận scope bệnh nhân từ client; không trả dữ liệu tài chính.
- Notification nhận ba event kết quả/lời hẹn, lưu IN_APP và dedupe trong transaction trước
  bất kỳ lần login nào. Producer fixtures và real PostgreSQL/RabbitMQ tests được bổ sung.

User đã giao code và tạo PR cho slice liên module này. Quyền triển khai trong task không
thay ownership dài hạn. Kế hoạch và tiêu chí tại
[07 ONB-01–06](../plans/07-outpatient-workflow-implementation.md).

## Điều kiện đóng

[ONB-A01–12](../plans/06-acceptance-runbook.md) đạt trên cùng final candidate qua Gateway/UI;
có PostgreSQL/RabbitMQ thật cho race/replay, recorded commit/flags, effect counts và evidence
đã che thông tin nhạy cảm. Source tests không tự chứng minh packaged/UI acceptance. Khi cả
producer/consumer đạt, chuyển quy tắc bền vững vào canonical docs, xóa handoff và cập nhật
registry theo root AGENTS. Không đóng WF-11 toàn bộ chỉ từ slice onboarding.
