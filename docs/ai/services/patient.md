# Context patient

Owner: Hoàng Anh. Giữ writable module scope hiện hành; deployment mapping không chuyển ownership.
Đọc [thiết kế](../../plans/01-system-design.md), [business rules](../../plans/02-business-flows.md),
[contracts](../../plans/03-contracts.md), [plan](../../plans/05-implementation-plan.md) và [coding](../README.md).
Exact DTO/roles/SQL/fixtures lấy từ module source, không invent theo tên service.

## Điều chỉnh nghiệp vụ 25/10/2026

Patient giữ định danh lâu dài; khám mới/tái khám dùng cùng patientId. BHYT còn hạn là giả định mô phỏng, không suy mức hỗ trợ từ login role hoặc tạo patient mới mỗi lần quay lại.

Đây là yêu cầu đích; triển khai theo [WF-01–11](../../plans/07-outpatient-workflow-implementation.md),
khóa wire trước đổi source và chạy [ma trận WF](../../plans/06-acceptance-runbook.md).
