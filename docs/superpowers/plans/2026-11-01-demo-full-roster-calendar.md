# Seed nhân sự và lịch demo ba tháng

**Goal:** Mỗi khoa đang hoạt động có ít nhất 10 nhân sự mẫu; mọi khoa lâm sàng có buổi khám liên tục 07–17 mỗi ngày từ 01/11/2026 đến hết 31/01/2027.

**Architecture:** Tiếp tục runner cách ly hiện có. Phòng tư vấn là bootstrap Organization (chưa có API tạo); nhân sự, tài khoản bác sĩ, phân ca và buổi khám qua Gateway, journal giữ nguyên lệnh khi chạy lại. Không tạo hồ sơ/bệnh nhân/thu phí, không đổi giờ hoặc bỏ guards chạy thật.

**Tech:** Python unittest, public API Core/Clinical, fixture SQL Organization hiện có.

- [x] Test kế hoạch 92 ngày, hai ca liền nhau, luân phiên 6 bác sĩ/4 điều dưỡng mỗi khoa lâm sàng; nhân sự đúng chức danh cho khoa khác.
- [x] Bootstrap một phòng lịch demo riêng cho mỗi khoa lâm sàng, tránh lịch demo.doctor đang có.
- [x] Thêm script seed, xác minh đủ nhân sự và lịch bằng API phân trang theo từng tháng; journal không lưu mật khẩu tài khoản.
- [x] Tích hợp khởi động demo, xuất danh sách tên khoa/phòng/tài khoản và cập nhật hướng dẫn.
- [x] Test đơn vị, chạy thật trên stack cách ly, chạy lại không thêm trùng, kiểm tra UI chọn lịch; cleanup. Chuẩn bị PR trên nhánh `codex/demo-full-roster-calendar`.

Kết quả: 22 test đạt; runtime xác minh 120 nhân sự hoạt động/10 khoa, 1.104 buổi/6 khoa lâm sàng. Nurse chọn ngày cuối và tài khoản bác sĩ bổ sung xem đúng buổi trên UI. Biên bản và ảnh: `docs/acceptance/demo-calendar-2026-11-01/README.md`.

Hai cách khác đã loại: chèn toàn bộ DB (bỏ qua kiểm tra/event/receipt), hoặc tạo một buổi xuyên ngày/ngoài 07–17 (sai nghiệp vụ). Lịch kín ngày nghĩa có buổi và chỗ, không phải đặt kín bệnh nhân. Khoa ngoài lâm sàng không có lịch khám bác sĩ giả.
