# Hồ sơ Review và tiến độ MediFlow

Cập nhật ngày **16/10/2026** theo phạm vi **MVP ngoại trú, sáu service đích**. Thư mục này chứa hồ sơ trình bày và tài sản nộp bài; thiết kế và kế hoạch kỹ thuật hiện hành nằm trong [docs/plans](../plans/README.md).

## Bộ tài liệu đang duy trì

| Hồ sơ | Nội dung |
|---|---|
| [Giai đoạn 1 — Khởi động và đặc tả](01-giai-doan-1-khoi-dong-dac-ta/README.md) | Mục tiêu, phạm vi, biểu mẫu ban đầu và hướng dẫn sử dụng Review 1 |
| [Review 1 — Standard tiếng Việt](01-giai-doan-1-khoi-dong-dac-ta/review-1/02-review-1-standard-vi.md) | Phần A: xác định vấn đề; phần B: CRS với chức năng theo role và yêu cầu phần cứng |
| [Review 1 — Standard English](01-giai-doan-1-khoi-dong-dac-ta/review-1/01-review-1-standard-en.md) | Bản tiếng Anh tương ứng cùng phạm vi yêu cầu |
| [Review 1 — Frontend Task Sheet](01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md) | Phân công và trạng thái frontend MVP, phụ thuộc tích hợp và phạm vi hoãn |
| [Giai đoạn 2 — Chỉ dẫn thiết kế](02-giai-doan-2-thiet-ke-giao-dien-tieu-chuan/README.md) | Liên kết thiết kế hiện hành và ghi chú tài sản sơ đồ lịch sử |
| [Giai đoạn 3 — Chỉ dẫn phân công và nghiệm thu](03-giai-doan-3-phan-chia-hoan-thien-ho-so/README.md) | Liên kết ownership, kế hoạch và tiêu chí nghiệm thu hiện hành |

## Nguồn thông tin hiện hành

- [Thiết kế hệ thống](../plans/01-system-design.md): sáu service đích, CURRENT/TARGET và phạm vi hoãn.
- [Luồng nghiệp vụ ngoại trú](../plans/02-business-flows.md): các điều kiện khám, xét nghiệm, thanh toán và cấp thuốc.
- [Hợp đồng tích hợp](../plans/03-contracts.md): định danh, token, clearance và chế độ tương thích.
- [Kế hoạch triển khai](../plans/05-implementation-plan.md) và [runbook nghiệm thu](../plans/06-acceptance-runbook.md): công việc và bằng chứng cần có.
- [Frontend workboard](../../frontend/docs/frontend-workboard.md): trạng thái route và công việc frontend có ngày đối chiếu.

## Tài sản lịch sử và nội dung đã dọn

Các báo cáo DOCX/PDF, `TeacherPlans.docx` và sơ đồ cũ được giữ để đối chiếu hồ sơ đã lập. Chúng có thể mô tả topology, phạm vi hoặc tiến độ trước MVP ngày 14/10/2026; chưa được tái xuất theo nội dung Review 1 mới và không dùng làm đặc tả hiện hành.

Đã bỏ các Markdown audit, recovery roadmap, checklist, SRS/GUI nháp tháng 9; các bản giải trình và kế hoạch refresh cũ; bản CRS trùng; thiết kế/phân công cũ của giai đoạn 2–3; và các bản Detailed, Presentation, Comparison của Review 1. Lịch sử Git giữ các phiên bản này nếu cần đối chiếu. Review 1 duy trì hai bản báo cáo Standard Việt/Anh cùng task sheet frontend được cập nhật ngày 16/10; lịch dự kiến cũ của task sheet ngày 12/10 đã được thay bằng thứ tự phụ thuộc và trạng thái có nguồn đối chiếu.

Các mục tiêu trong hồ sơ là yêu cầu cần nghiệm thu. Việc có mã nguồn, sơ đồ hoặc tài liệu không tự chứng minh luồng đã chạy xuyên suốt hay mọi chức năng đã hoàn tất.
