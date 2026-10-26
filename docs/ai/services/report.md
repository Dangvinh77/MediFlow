# Context report

Owner: Huy. Giữ writable module scope hiện hành; deployment mapping không chuyển ownership.
Đọc [thiết kế](../../plans/01-system-design.md), [business rules](../../plans/02-business-flows.md),
[contracts](../../plans/03-contracts.md), [plan](../../plans/05-implementation-plan.md) và [coding](../README.md).
Exact DTO/roles/SQL/fixtures lấy từ module source, không invent theo tên service.

## Điều chỉnh nghiệp vụ 25/10/2026

Report đếm lượt khám mới/tái khám từ fact có nguồn; quay lại đọc kết quả không tăng lượt. Tách giá gốc/hỗ trợ/nghĩa vụ người bệnh khỏi cash received/refund/net cash; không gọi hỗ trợ là tiền đã thu. Thiếu field/coverage là unavailable.

Đây là yêu cầu đích; triển khai theo [WF-01–11](../../plans/07-outpatient-workflow-implementation.md),
khóa wire trước đổi source và chạy [ma trận WF](../../plans/06-acceptance-runbook.md).
