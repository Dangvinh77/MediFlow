# Known limitations

Thư mục này ghi lại **các giới hạn thiết kế đã biết** của MediFlow ở phiên bản MVP hiện tại:
những chỗ mô hình, quyền truy cập hoặc cách trình bày chưa phản ánh đầy đủ nghiệp vụ
bệnh viện thực tế. Mỗi tài liệu phân biệt hành vi đã kiểm tra trong source với đề xuất chưa triển khai;
việc ghi nhận ở đây không chứng nhận hành vi hiện tại đã đạt yêu cầu bảo mật hoặc nghiệm thu.

Mỗi tài liệu gồm: hiện trạng (dẫn tới mã nguồn), vấn đề nghiệp vụ, đề xuất thiết kế, kế hoạch
chuyển đổi và câu hỏi còn mở. Đây là **tài liệu kế hoạch**; quyết định người dùng được ghi riêng từng mục.
Đồng ý đề xuất không đồng nghĩa đã chốt mọi chi tiết hợp đồng, đã triển khai hoặc nghiệm thu. Khi một giới hạn được xử lý, chuyển quy tắc
lâu dài vào tài liệu canonical (`docs/plans/*`) và xóa/đánh dấu tài liệu tương ứng ở đây.

| # | Giới hạn | Context | Trạng thái |
|---|---|---|---|
| 01 | [Chưa có cấp quá trình điều trị để nhóm nhiều lượt khám / tái khám của nhiều bác sĩ](01-multi-doctor-medical-record.md) | Clinical | Đề xuất — chưa triển khai; hiện trạng hiệu chỉnh 2026-11-09 |
| 02 | [Đọc lịch sử liên khoa, quyền ghi, hồ sơ hoàn tất và nhiều ca trong ngày](02-medical-record-read-access.md) | Clinical / Gateway / Frontend | Đã đối chiếu source 2026-11-09 — chưa nghiệm thu runtime, chưa sửa production |
| 03 | [Checklist xác nhận Clinical và Lab: lịch, hồ sơ, phân loại và template kết quả](03-clinical-service-improvement-checklist.md) | Clinical / Lab / giao diện và hợp đồng liên quan | Đã đồng ý toàn bộ 117 mục phần 1–21 ngày 10/11/2026; đang triển khai trên nhánh `develop/mediflow-v2.0`. Checkbox là quyết định, không phải nghiệm thu |
