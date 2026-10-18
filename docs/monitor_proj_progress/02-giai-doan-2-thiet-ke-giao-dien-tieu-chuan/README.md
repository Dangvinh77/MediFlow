# Giai đoạn 2 — Chỉ dẫn thiết kế giao diện, dữ liệu và hệ thống

Cập nhật ngày **19/10/2026**. Các bản thiết kế/giải trình Markdown tháng 9 mô tả phạm vi cũ đã được bỏ. Thiết kế hiện hành của MVP ngoại trú được duy trì tại các nguồn sau:

## Review 2 — thiết kế đối chiếu source hiện tại

- [Review 2 tiếng Việt](review-2/02-review-2-standard-vi.md)
- [Review 2 English](review-2/01-review-2-standard-en.md)

Hai bản có cùng bố cục: Architecture Design (system/application, use cases, sequences,
classes), DFD, ERD, Table Design, SiteMap và Algorithm. Mỗi bản có 28 sơ đồ Mermaid,
theme Sky/Teal thống nhất, actor use case hình người và logo stack công nghệ.
Nguồn Mermaid, SVG và logo nằm trong `review-2/assets/`, tách khỏi bộ sơ đồ lịch sử.
Baseline ngày 19/10/2026 là source và working tree đã quan sát; tài liệu phân biệt
SOURCE/GATED/DEFERRED, không thay acceptance runtime hoặc tài liệu contract chính.

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

Bộ sơ đồ hiện hành mới nằm trong Review 2 ở trên. Các bản DOCX/PDF lịch sử chưa được
tái xuất trong tác vụ lập Review 2 Markdown này.

## Tùy chọn sơ đồ cho Word

- [Bảng chọn A4 dọc và ngang](review-2/word-compact/README.md)
- [Review 2 option Word tiếng Việt](review-2/04-review-2-word-vi.md)
- [Review 2 Word layout option English](review-2/03-review-2-word-en.md)

Bộ assets và báo cáo gốc giữ nguyên. Bản bổ sung có PNG, SVG, Mermaid và các phần chi tiết cho sơ đồ lớn.
