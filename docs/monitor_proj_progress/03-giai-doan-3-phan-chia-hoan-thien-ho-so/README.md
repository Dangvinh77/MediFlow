# Giai đoạn 3 — Chỉ dẫn phân công và nghiệm thu

Cập nhật ngày **16/10/2026**. Bản phân công/giải trình Markdown tháng 9 đã hết hiệu lực và được bỏ. Không dùng các mốc tiến độ cũ để kết luận trạng thái MVP hiện tại.

## Phân công theo ngữ cảnh

| Thành viên | Phạm vi backend | Phạm vi hoãn |
|---|---|---|
| Vinh | Clinical, Lab | Inpatient |
| Huy | Pharmacy, Report | Surgery |
| Hoàng Anh | Organization, Patient, Gateway | — |
| Lộc | Billing, Notification | — |

Core đóng gói Organization + Patient; Support đích đóng gói Report + Notification. Việc đóng gói không chuyển ownership. Shared build, common, CI và scripts chỉ thay đổi khi task giao rõ. Ownership frontend và ngoại lệ shared scope theo [ma trận frontend](../../ai/15-frontend-ownership.md).

## Nguồn quản lý công việc và nghiệm thu

- [Kế hoạch triển khai MVP](../../plans/05-implementation-plan.md): task, subtask, phụ thuộc và bằng chứng cần có.
- [Nghiệm thu và vận hành](../../plans/06-acceptance-runbook.md): luồng demo, kiểm thử lỗi từng phần, rollout và rollback.
- [Frontend workboard](../../../frontend/docs/frontend-workboard.md): trạng thái route và công việc theo ngày.
- [Review 1](../01-giai-doan-1-khoi-dong-dac-ta/README.md): bộ yêu cầu Standard Việt/Anh đang duy trì.
- [Frontend Task Sheet](../01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md): phân công frontend MVP, trạng thái có bằng chứng và các phụ thuộc còn mở.

Chỉ xác nhận hoàn thành khi có bằng chứng tương ứng với commit, cấu hình/profile và kết quả kiểm thử. Phân biệt yêu cầu đã thiết kế, mã đã tồn tại và luồng đã nghiệm thu. Các thao tác tiền/kho phải được kiểm tra khi gửi lặp, restart và lỗi giữa các dịch vụ.

Báo cáo giai đoạn 3 DOCX/PDF ở thư mục cha là tài sản lịch sử, chưa được tái xuất theo MVP hiện tại. Lịch sử Git giữ các Markdown cũ nếu cần đối chiếu phân công hoặc hồ sơ đã nộp.
