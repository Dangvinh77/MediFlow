# Nghiệm thu trưởng khoa và hai khu vực làm việc — 01/11/2026

Các lỗi UX đã sửa và kiểm tra lại nằm trong [kết quả sau sửa](UX-FIXES.md), kèm ảnh, tests và giới hạn nghiệm thu.

Cập nhật kiểm tra trực tiếp sau merge: [Edge InPrivate](EDGE-INPRIVATE-RETEST.md) và [trình duyệt kết nối trực tiếp](DIRECT-BROWSER-RETEST.md). Lượt kết nối trực tiếp đã xác nhận lưu/sửa ca, tạo buổi từ ca, lịch cá nhân, thống kê và cả hai lựa chọn của cảnh báo chưa lưu. Các giới hạn bên dưới là trạng thái của lượt nghiệm thu ban đầu; xem báo cáo mới để biết phần đã được kiểm chứng bổ sung.

Phạm vi PR: bác sĩ được bổ nhiệm trưởng khoa CLINICAL giữ tài khoản/role DOCTOR, chuyển giữa Khám bệnh và Quản lý khoa; quản lý ca bác sĩ, tạo buổi khám từ ca và theo dõi số chỗ trong khoa. Không phải nghiệm thu lại toàn bộ hành trình bệnh nhân, thu tiền, xét nghiệm và phát thuốc.

## Kết quả đã kiểm chứng

| Hạng mục | Bằng chứng | Kết quả |
| --- | --- | --- |
| Bổ nhiệm, thay thế, miễn nhiệm; giữ định danh bác sĩ | Core unit/integration/PostgreSQL: eligibility, uniqueness, revision, audit, idempotency, projection và concurrent writes | Đạt kiểm tra tự động |
| Thu hồi quyền ngay với token cũ | Rehearsal `snapshot-20261101-123019`, seed `--verify-revocation` qua public Gateway | Cùng token: đọc lịch khoa bị 403; quyền DOCTOR, lịch cá nhân còn nguyên; khôi phục bổ nhiệm revision 3 |
| Ca làm việc và liên kết buổi khám | Clinical PostgreSQL: trùng bác sĩ/phòng, cạnh tranh sửa/hủy/tạo liên kết, rollback receipt, deadlock regression | Đạt kiểm tra tự động |
| Phạm vi quyền và route | Core live authority + Gateway role/route contract tests | Đạt; không cấp thêm quyền khám, Lab, Pharmacy, Billing hoặc Reports từ chức vụ trưởng khoa |
| Chuyển dashboard, menu và danh tính | Đăng nhập thật `demo.doctor`, bấm Sang Quản lý khoa | Đã quan sát UI và chụp ảnh |
| Thống kê khoa và buổi khám | UI ngày 03/11: 1 buổi mở, sức chứa 12, còn 12; buổi 09:00–11:00 | Đã quan sát UI; aggregate toàn khoảng còn có test PostgreSQL 25 hàng |
| Ca mẫu của khoa | UI tuần 02–08/11: ca 03/11, 08:00–12:00, đúng bác sĩ/phòng, ACTIVE | Đã quan sát UI; mở form Tạo buổi khám từ ca thành công |
| Cảnh báo dữ liệu chưa lưu | Regression frontend cho chuyển dashboard, thêm/xóa giờ nghỉ | Đạt test; hộp thoại trình duyệt thực tế chỉ được kích hoạt, chưa hoàn tất chọn ở lại/rời trang |

## Kiểm tra kỹ thuật

- Core: full reactor 413 tests thành công; sau sửa bắt buộc có trường `staffId`, bộ HTTP/PostgreSQL liên quan 12 tests thành công. Tổng báo cáo Surefire hiện tại 414, không lỗi/bỏ qua.
- Clinical: full module 490 tests thành công, gồm PostgreSQL concurrency và liên kết ca/buổi khám.
- Gateway: full 1.761 tests thành công, sau bổ sung summary/role contract, tổng báo cáo hiện tại 1.770 không lỗi/bỏ qua.
- Frontend: full 134 files / 1.199 tests thành công; sau các chỉnh sửa cuối, bộ liên quan 20 files / 107 tests và regression guard 8 tests thành công. Các số này có phần giao nhau, không cộng dồn.
- Frontend typecheck, lint và production build thành công. Đóng dev server trước build.
- Đóng gói 8 ứng dụng backend thành công; cả 8 ứng dụng UP/READY trong rehearsal dùng một lần.
- Seed Python: 10 tests thành công; chạy seed và chạy lại idempotent qua Gateway thành công. Thời gian hợp đồng ca dùng `HH:mm`.
- `git diff --check` không có lỗi khoảng trắng.

## Giới hạn nghiệm thu UI còn lại

1. Chưa hoàn tất thao tác ghi bằng UI: bổ nhiệm/miễn nhiệm từ trang admin; lưu/sửa/hủy ca; lưu buổi khám từ ca. API, transaction và UI component tests đã chạy, nhưng không thay cho việc click ghi trên trình duyệt.
2. Chưa hoàn tất kiểm tra lịch ca cá nhân và vòng chuyển về Khám bệnh sau khi xử lý hộp thoại dữ liệu chưa lưu. Hộp thoại native làm automation timeout; công cụ điều khiển Edge sau đó bị chặn vì không xác minh được URL. Dừng điều khiển máy ở thời điểm đó.
3. Lần chạy rehearsal tổng quát với `--prepare-ui-demo` gặp Billing start-exam 503 sau khởi động. Phiên nghiệm thu trưởng khoa thành công dùng rehearsal mới không có tùy chọn này. Chưa kết luận nguyên nhân lỗi Billing và không đánh dấu hành trình khám đầy đủ là đạt.
4. Cache frontend dev cũ từng gây lỗi tải trang; khởi động với cache dev mới khắc phục được. Production build cuối thành công.

Hướng dẫn kiểm tra phần còn lại: [Demo trưởng khoa](../../../scripts/demo_25_10/DEMO_TRUONG_KHOA.md). Giữ các bước UI trên là chưa kiểm chứng, không đóng bằng kết quả unit test.

## Ảnh quan sát

- [Khám bệnh, có nút chuyển khu vực](screenshots/01-doctor-workspace.jpg).
- [Dashboard quản lý khoa](screenshots/02-department-workspace.jpg).
- [Thống kê và buổi khám](screenshots/03-department-summary-and-session.jpg): ảnh full-page có vị trí sticky header/sidebar do cách chụp; không dùng hiện tượng đó để kết luận lỗi bố cục sản phẩm.
- [Lịch làm việc bác sĩ](screenshots/04-department-doctor-shifts.jpg).

Sau demo đã dừng frontend/backend và chạy `clean-docker.bat` thành công. Script xác nhận giữ volume dữ liệu và tài nguyên dự án khác, không global prune; không còn container chạy tại thời điểm kiểm tra.
