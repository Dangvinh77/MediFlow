# Context clinical

Owner: Vinh. Giữ writable module scope hiện hành; deployment mapping không chuyển ownership.
Đọc [thiết kế](../../plans/01-system-design.md), [business rules](../../plans/02-business-flows.md),
[contracts](../../plans/03-contracts.md), [plan](../../plans/05-implementation-plan.md) và [coding](../README.md).
Exact DTO/roles/SQL/fixtures lấy từ module source, không invent theo tên service.

## Điều chỉnh nghiệp vụ 25/10/2026

Clinical owns lịch khám ngoại trú/giữ suất, đăng ký tại quầy, lượt khám mới/tái khám, lời hẹn và completion. Đọc kết quả trong lượt mở không tạo EXAM charge lần hai. Lịch ca và liên kết tái khám là gap thiết kế, không suy từ appointmentTime hiện có.

Đây là yêu cầu đích; triển khai theo [WF-01–11](../../plans/07-outpatient-workflow-implementation.md),
khóa wire trước đổi source và chạy [ma trận WF](../../plans/06-acceptance-runbook.md).
