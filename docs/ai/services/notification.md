# Context notification

Owner backend: Vinh (tiếp nhận từ 18/10/2026). Giữ writable module scope hiện hành; deployment mapping không chuyển ownership.
Đọc [thiết kế](../../plans/01-system-design.md), [business rules](../../plans/02-business-flows.md),
[contracts](../../plans/03-contracts.md), [plan](../../plans/05-implementation-plan.md) và [coding](../README.md).
Exact DTO/roles/SQL/fixtures lấy từ module source, không invent theo tên service.

## Điều chỉnh nghiệp vụ 25/10/2026

Đăng ký chờ thu không là phiếu đã xác nhận. Chỉ thông báo xác nhận khi Clinical xác nhận suất sau đủ tài chính; lời hẹn tái khám chưa là lịch đã đặt. Đổi lịch/đã hoàn tiền cần fact và audit tương ứng; replay không gửi trùng.

Đây là yêu cầu đích; triển khai theo [WF-01–11](../../plans/07-outpatient-workflow-implementation.md),
khóa wire trước đổi source và chạy [ma trận WF](../../plans/06-acceptance-runbook.md).
