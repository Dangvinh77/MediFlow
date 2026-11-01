# Nghiệm thu bổ nhiệm và phân ca tuần — 01/11/2026

[PR #559](https://github.com/Dangvinh77/MediFlow/pull/559).

Base master `3f9f5147`. Môi trường demo dùng một lần `snapshot-20261101-182953`,
Gateway 28080, frontend 3000, trình duyệt IAB kết nối trực tiếp. Không phóng toàn
màn hình; ảnh viewport ghi cửa sổ khoảng 659 × 653. Dữ liệu đều tổng hợp.

Người dùng chọn **giữ giờ thực, nghiệm thu khám trong 07:00–17:00**. Phiên này diễn
ra từ 18:29 giờ Việt Nam, seed báo PARTIAL (lịch ngày mai, chưa có lượt nguồn tái khám).
Không thay đồng hồ hoặc nới điều kiện nghiệp vụ. **Chưa nghiệm thu trọn hành trình
bệnh nhân trên cùng một lượt**, kể cả khi các kiểm thử tự động đã đạt.

## Thao tác UI đã xác minh

| Thao tác | Kết quả quan sát |
|---|---|
| Admin bổ nhiệm Lê Bác Sĩ Demo vào Khoa Nội tổng quát Demo | Có xem trước, lý do, xác nhận, phiên bản và lịch sử |
| Đồng bộ thông tin trưởng khoa | Trước sửa: khối thông tin khoa còn “Chưa phân công”; sau sửa: cập nhật ngay cùng khối bổ nhiệm |
| Admin miễn nhiệm | Hiện thông báo thành công, không còn người đương nhiệm ở cả hai khối, lịch sử đóng nhiệm kỳ |
| Bác sĩ đăng nhập sau miễn nhiệm | Còn lịch khám/lịch làm việc cá nhân và nghiệp vụ bác sĩ; không có nút sang quản lý |
| Bác sĩ được bổ nhiệm, tải lại quyền | Có “Sang Quản lý khoa”; vào đúng khoa được bổ nhiệm, menu quản lý riêng; “Về Khám bệnh” quay về lịch cá nhân |
| Tuần 02–08/11 | Bảy cột thứ Hai–Chủ nhật, ca mẫu 03/11 08:00–12:00, tổng 4 giờ |
| Tạo ca bằng UI ngày 04/11 08:00–12:00 | Thành công, lưới tự tải lại, tổng hai ca = 8 giờ |
| Hủy ca 04/11 bằng UI | Đọc đúng ngày trước xác nhận, ca chuyển “Đã hủy”, phiên bản 2, tổng còn 4 giờ |
| Sửa ca 03/11 đã có buổi khám | Bị chặn bằng thông báo tiếng Việt, giữ ca gốc 08:00–12:00; không báo lưu thành công |
| Tạo rồi sửa ca 05/11 chưa có buổi khám | Lưu ca 08:00–12:00, sửa thành 08:00–13:00 thành công, tổng tuần 9 giờ (4 + 5, ca hủy không cộng) |
| Tạo buổi khám từ ca 05/11 | Thành công 08:00–13:00, sức chứa 12; liên kết “Xem buổi khám vừa tạo” mở đúng 05/11, hiển thị 12 còn, 0 xác nhận |
| Cửa sổ hẹp | Có cuộn ngang, cột bác sĩ được tăng chiều rộng và giữ cố định; có chế độ ngày |

Menu trên cửa sổ hẹp cần mở bằng nút ba gạch trước khi chọn mục. Snapshot DOM có
thể chứa cả menu đang đóng; không coi việc tìm thấy link trong DOM là bằng chứng
người dùng đã nhìn thấy hoặc bấm được menu.

## Ảnh trước và sau

- [Trước sửa: bổ nhiệm nhưng thông tin khoa cũ](screenshots/01-admin-appointed-before-fix.jpg)
- [Trước sửa: miễn nhiệm](screenshots/02-admin-revoked-before-fix.jpg)
- [Sau sửa: thông tin khoa và bổ nhiệm đồng bộ](screenshots/03-admin-appointed-fixed.jpg)
- [Sau sửa: miễn nhiệm và lịch sử giờ Việt Nam](screenshots/04-admin-revoked-fixed.jpg)
- [Bác sĩ sau miễn nhiệm vẫn có lịch khám](screenshots/05-doctor-after-revocation.jpg)
- [Tuần có ca mẫu](screenshots/06-weekly-grid.jpg)
- [Thông báo chặn sửa ca liên kết](screenshots/07-linked-shift-edit-blocked.jpg)
- [Hai ngày có ca, tổng 8 giờ](screenshots/08-weekly-two-days-eight-hours.jpg)
- [Hủy một ca, tổng hiệu lực còn 4 giờ](screenshots/09-weekly-cancelled-excluded.jpg)
- [Sửa ca chưa liên kết, tổng 9 giờ](screenshots/10-weekly-edited-nine-hours.jpg)
- [Tạo buổi khám từ ca thành công](screenshots/11-session-created-from-shift.jpg)
- [Ảnh viewport trang bàn giao buổi khám](screenshots/12-session-handoff-viewport.jpg)
- [Cột bác sĩ dễ đọc trên cửa sổ hẹp](screenshots/13-weekly-readable-doctor.jpg)
- [Cuộn đến tổng 9 giờ vẫn giữ tên bác sĩ](screenshots/14-weekly-sticky-total.jpg)

Ảnh toàn trang lưu bằng chứng dữ liệu ngoài phần nhìn thấy ban đầu; không thay
đổi kích thước cửa sổ để chụp. Một số ảnh lưới sớm được chụp trước khi tăng chiều
rộng/giữ cố định cột bác sĩ.

## Bằng chứng API và tự động, tách khỏi UI

`prepare_department_head_demo.py --verify-revocation` đã chạy thành công trên
stack này: cùng token bác sĩ cũ bị từ chối đọc lịch toàn khoa sau miễn nhiệm,
vẫn đọc lịch cá nhân được, và quyền quản lý trở lại khi bổ nhiệm lại. Script
chuẩn bị ca 03/11 và buổi 09:00–11:00; không đăng ký bệnh nhân. Đây là kiểm chứng
API, không được tính thành các click UI.

Kiểm thử frontend bao phủ tải nhiều trang trước khi hiện lưới/tổng, lỗi ở trang
sau không hiện tổng thiếu, phản hồi cũ khi đổi ngày/phiên, tải lại đủ trang sau ghi,
hủy đúng revision, ca hủy không cộng giờ và đồng bộ bổ nhiệm với thông tin khoa.
Kết quả cuối cùng được ghi ở phần xác minh trước PR.

## Xác minh trước PR

- `npm test -- src/features/appointment src/features/organization`: **41 file, 267 test đạt**.
- `npm run typecheck`, `npm run lint`, `npm run build`: đạt.
- Review độc lập cả lưới và đồng bộ bổ nhiệm, gồm tải lại sau xung đột phiên bản:
  không phát hiện hồi quy cần sửa. Phản hồi đọc cũ không được đè kết quả lưu mới.
- Đã đóng frontend/tab kiểm thử; chạy `clean-docker.bat` thành công, dừng backend
  demo và dọn container/image thuộc phạm vi dự án. Kiểm tra sau đó không có
  container còn lại và không có tiến trình lắng nghe 3000/28080.

Lệnh build chạy sau khi dừng frontend; không chạy build và dev cùng cache đồng thời.

## Còn phải nghiệm thu

- [ ] UI thay trưởng khoa bằng **bác sĩ thứ hai** đủ điều kiện; không dùng bổ nhiệm lại cùng người để đánh dấu đạt.
- [ ] UI tập dữ liệu nhiều trang/hơn 100 ca (đã có kiểm thử tự động).
- [x] Sửa thành công ca chưa có buổi khám và tạo buổi khám từ ca bằng UI ở bản này.
- [ ] Ba hành trình mới/cũ khám đợt mới/tái khám, mỗi hành trình xuyên suốt từ nurse đến cấp thuốc trong giờ khám.
- [ ] Patient xem đúng kết quả của hành trình vừa thực hiện; bệnh nhân mới tự đổi mật khẩu.

Phiếu thao tác và bàn giao mã nằm trong
[DEMO_UI_TUNG_BUOC.md](../../../scripts/demo_25_10/DEMO_UI_TUNG_BUOC.md), mục 12.
Không đóng các mục này bằng dữ liệu seed hoặc test API.
