# Context billing

Owner backend: Vinh (tiếp nhận từ 18/10/2026). Giữ writable module scope hiện hành; deployment mapping không chuyển ownership.
Đọc [thiết kế](../../plans/01-system-design.md), [business rules](../../plans/02-business-flows.md),
[contracts](../../plans/03-contracts.md), [plan](../../plans/05-implementation-plan.md) và [coding](../README.md).
Exact DTO/roles/SQL/fixtures lấy từ module source, không invent theo tên service.

## Điều chỉnh nghiệp vụ 25/10/2026

Billing owns quote và snapshot hỗ trợ BHYT mô phỏng theo item, nghĩa vụ người bệnh, cash/refund và exact clearance. Mặc định BHYT còn hạn; tỷ lệ tự quy định, không quyết toán BHXH. Zero payable không tạo tiền giả; refund không hoàn phần hỗ trợ.

Đây là yêu cầu đích; triển khai theo [WF-01–11](../../plans/07-outpatient-workflow-implementation.md),
khóa wire trước đổi source và chạy [ma trận WF](../../plans/06-acceptance-runbook.md).
