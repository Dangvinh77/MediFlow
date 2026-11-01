# Sửa UX trưởng khoa sau kiểm tra trực tiếp

Phạm vi được người dùng duyệt: các phát hiện trong DIRECT-BROWSER-RETEST.md; không thêm lưới lịch tuần hoặc đổi quyền/API.

- [x] Seed: kiểm tra hồi quy 404/503 trong lúc chờ projection; chỉ coi phản hồi thành công là dữ liệu, giữ nguyên journal/khóa lệnh; chạy toàn bộ tests Python.
- [x] Trình bày: ngày dd/mm/yyyy, timestamp giờ Việt Nam 24h, giải thích định dạng ô native và hiển thị ngày đã chọn; đổi tiêu đề khối bị lặp. Kiểm tra timezone và nhãn truy cập.
- [x] Ca: ánh xạ mã lỗi sang tiếng Việt; khi mở form cuộn/focus vào form; sau ghi tải lại đúng khoảng ngày và giữ ngày đang làm. Nếu tải lại thất bại, báo riêng, không gửi lại lệnh đã thành công.
- [x] Buổi mới: có liên kết mở danh sách khoa đã lọc đúng ngày; URL chỉ truyền ngày, backend tiếp tục quyết định phạm vi khoa. Kiểm tra ngày không hợp lệ fallback an toàn.
- [x] Kiểm chứng: tests hồi quy trước/sau; frontend typecheck/lint/build; kiểm tra trình duyệt với seed đã sửa, lưu ảnh và dọn demo.

Thực hiện: seed_fix sửa script/tests Python; agent chính xử lý toàn bộ presentation, ca, route và tích hợp; reviewer độc lập kiểm tra thay đổi. Worker presentation dừng trước khi chỉnh sửa nên không có phần việc bị bỏ dở. Không sửa frontend thuộc owner khác hoặc mã deferred Surgery/Inpatient.

Bằng chứng và giới hạn: [UX-FIXES](../../acceptance/department-head-2026-11-01/UX-FIXES.md). Các mục trên hoàn tất trong phạm vi sửa; không thay cho nghiệm thu admin hoặc hành trình bệnh nhân đầy đủ.
