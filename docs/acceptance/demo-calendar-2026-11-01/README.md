# Seed nhân sự và lịch ba tháng — 01/11/2026

Yêu cầu: mỗi khoa ít nhất 10 người, có buổi khám cả ngày từ 01/11/2026 đến hết
31/01/2027. Chạy thật trên stack dùng một lần `snapshot-20261101-215729`, Gateway
28080, frontend 3000. Không đổi giờ, không tạo dữ liệu bệnh nhân/đăng ký/thu tiền
để kiểm tra seed này.

## Dữ liệu đã xác minh qua API

| Khoa/phòng | Nhân sự hoạt động sau seed | Buổi lịch ba tháng |
|---|---:|---:|
| Khoa Nội tổng quát Demo | 12 | 184 |
| Khoa Nhi Demo | 12 | 184 |
| Khoa Tim mạch Demo | 12 | 184 |
| Khoa Tai Mũi Họng Demo | 12 | 184 |
| Khoa Da liễu Demo | 12 | 184 |
| Khoa Mắt Demo | 12 | 184 |
| Khoa Xét nghiệm Demo | 12 | 0 |
| Khoa Dược Demo | 12 | 0 |
| Phòng Hành chính Demo | 12 | 0 |
| Phòng Tài chính Kế toán Demo | 12 | 0 |

- 100 nhân sự bổ sung, tổng 120 đang hoạt động; mỗi khoa vốn có 2 người.
- Mỗi khoa lâm sàng thêm 6 bác sĩ, 4 điều dưỡng và 6 tài khoản bác sĩ.
- Tổng **1.104 phân ca và 1.104 buổi** thuộc lịch ba tháng; 92 ngày × 2 buổi × 6 khoa.
- Hai buổi 07:00–12:00 và 12:00–17:00 liền nhau, 40 chỗ/buổi; gồm cuối tuần.
- Xác minh từng tháng qua API phân trang, đúng bác sĩ/phòng/ngày/giờ/sức chứa,
  trạng thái OPEN và còn chỗ; không suy ra đủ từ trang đầu.
- Chạy lại `prepare_demo_calendar.py` trên cùng stack đạt trong khoảng 2 giây;
  API đối chiếu lại cả 1.104 buổi, 120 nhân sự hoạt động, journal dùng lại lệnh đã
  xác nhận. Không thêm trùng, không đặt lại mật khẩu hoặc tự mở lại buổi bị hủy.
- Giữ fixture `demo.doctor` riêng. `prepare_department_head_demo.py --verify-revocation`
  vẫn thành công khi có toàn bộ calendar, dùng phòng khác phòng đã kín lịch.

Khoa ngoài lâm sàng chỉ có nhân sự đúng chức năng, không tạo buổi khám bác sĩ giả.
Phòng lịch là bootstrap Organization vì chưa có API tạo phòng tư vấn; nhân sự,
tài khoản, phân ca và buổi khám đều qua Gateway với journal. Journal không chứa
mật khẩu/tokens. File DEMO-CALENDAR.local.md công bố mật khẩu mẫu công khai và
danh sách tài khoản tương ứng; cần đăng nhập đúng bác sĩ của buổi đã chọn.

## Kiểm tra trực tiếp UI

1. Đăng nhập nurse → Tiếp nhận ca khám → chọn Khoa Nội tổng quát Demo.
2. Từ ngày/đến ngày **31/01/2027** → Tải buổi khám: thấy cả sáng/chiều, đúng bác sĩ,
   mỗi buổi còn 40/40. [Ảnh hai buổi](screenshots/01-nurse-last-day-sessions.jpg).
3. Bấm Chọn buổi sáng: mở đúng form Tiếp nhận vào buổi 31/01/2027 · 07:00, có loại
   Khám đợt mới. Không tạo phiếu bệnh nhân.
   [Ảnh form đã chọn](screenshots/02-nurse-selected-last-day.jpg).
4. Đăng nhập `demo.roster.532087c9b7.bs03` bằng mật khẩu mẫu → Lịch khám của tôi →
   31/01/2027: thấy đúng buổi 07:00–12:00, đúng khoa/phòng, 40 còn chỗ.
   [Ảnh lịch bác sĩ](screenshots/03-doctor-last-day-calendar.jpg).

Không thay đổi kích thước trình duyệt hoặc mở toàn màn hình. Các ảnh chỉ chứng minh
tra cứu/chọn buổi và tài khoản bác sĩ hoạt động, không chứng minh đã khám bệnh.
Hành trình bệnh nhân đầy đủ vẫn cần nghiệm thu trong giờ khám theo giờ thực đã chốt.

## Kiểm thử và cách dùng

- 22 unittest của các script prepare đạt; gồm 92 ngày bao gồm hai đầu, phân trang,
  luân phiên, chạy lại, buổi hủy, nhân sự đổi chuyên môn, thu hồi tài khoản, tách
  phòng với fixture cũ, retry cùng khóa/nội dung và không lưu mật khẩu vào journal.
- Python compile và `git diff --check` đạt; review độc lập không còn phát hiện cần sửa.
- Đã đóng tab/tiến trình frontend kiểm thử và chạy `clean-docker.bat` thành công;
  backend demo dừng, container và image của demo được dọn. Dữ liệu mẫu được tạo
  lại tự động khi khởi động backend lần sau.
- Chạy `start-backend.bat`, chờ CALENDAR READY và READY; mở DEMO-CALENDAR.local.md
  tại đường dẫn trong cửa sổ. Rồi chạy `start-frontend.bat`.
- Lần khởi động đầu ghi hơn 2.300 lệnh qua API nên cần chờ thêm vài phút; có tiến độ
  từng tháng/khoa. Lần chạy lại trên cùng stack dùng lại journal. Stack mới nạp lại
  snapshot và tạo lại dữ liệu.
- Lịch quá khứ chỉ xem; sau 17:00 có thể đăng ký trước cho ngày sau, không bắt đầu
  khám trái ngày/giờ. Ngoài khoảng 01/11–31/01 không bảo đảm có lịch.
