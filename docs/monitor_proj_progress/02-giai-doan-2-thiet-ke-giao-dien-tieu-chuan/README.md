# Giai đoạn 2 — Chỉ dẫn thiết kế giao diện, dữ liệu và hệ thống

Cập nhật ngày **16/10/2026**. Các bản thiết kế/giải trình Markdown tháng 9 mô tả phạm vi cũ đã được bỏ. Thiết kế hiện hành của MVP ngoại trú được duy trì tại các nguồn sau:

| Nội dung | Nguồn hiện hành |
|---|---|
| Topology sáu service, CURRENT/TARGET, phạm vi hoãn | [Thiết kế hệ thống](../../plans/01-system-design.md) |
| Quy trình và điều kiện nghiệp vụ | [Luồng ngoại trú](../../plans/02-business-flows.md) |
| API, event và phân quyền | [Hợp đồng tích hợp](../../plans/03-contracts.md) |
| Quyền sở hữu dữ liệu và độ tin cậy | [Dữ liệu và độ tin cậy](../../plans/04-data-reliability.md) |
| Cấu trúc và quy tắc giao diện web | [Frontend blueprint](../../ai/12-frontend.md) |
| Trạng thái route và công việc giao diện | [Frontend workboard](../../../frontend/docs/frontend-workboard.md) |
| Yêu cầu khách hàng | [Review 1 tiếng Việt](../01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md) |

Schema và DTO thực tế phải đối chiếu với migrations và source của từng module. Core/Support đồng triển khai không đồng nghĩa hợp nhất dữ liệu các ngữ cảnh.

## Tài sản sơ đồ lịch sử

[Bộ sơ đồ cũ](assets/diagrams/README.md) giữ nguồn Mermaid và các bản SVG/PNG phục vụ đối chiếu hồ sơ đã lập. Các sơ đồ này chưa được cập nhật theo MVP sáu service và không được dùng để xác nhận topology, schema hoặc hợp đồng hiện hành. Báo cáo giai đoạn 2 DOCX/PDF ở thư mục cha cũng là bản lịch sử.

Việc cập nhật bộ sơ đồ và tái xuất báo cáo giai đoạn 2 cần được đối chiếu riêng với source và thiết kế hiện hành; trang này không ghi nhận công việc đó đã hoàn tất.
