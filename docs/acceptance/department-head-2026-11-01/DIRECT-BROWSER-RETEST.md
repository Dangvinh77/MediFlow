# Kiểm tra bằng trình duyệt kết nối trực tiếp — 01/11/2026

Code: master `8832d47f`. Môi trường dùng một lần `snapshot-20261101-132206`, Gateway 28080, scheduling bật; dữ liệu trưởng khoa chuẩn bị bằng API công khai. Không có bệnh nhân trong buổi mới. Kiểm tra trên trình duyệt trong ứng dụng, viewport mặc định khoảng 1280×720; không điều khiển/phóng to cửa sổ Edge.

## Đã kiểm chứng bằng UI

| Thao tác | Kết quả |
| --- | --- |
| Đăng nhập bác sĩ → Quản lý khoa | Vào đúng dashboard, giữ DOCTOR, hiện khoa được bổ nhiệm. |
| Tìm bác sĩ, tải phòng, lưu ca | Thành công; ca đầu tiên thực tế được lưu ngày 01/11 (xem giới hạn tự động điền ngày bên dưới). |
| Sửa ca chưa liên kết | Đổi sang 04/11, 08:00–12:00, nhập lý do, lưu thành công; tải lại thấy phiên bản 2. |
| Tạo buổi từ ca | Thành công ngày 04/11, 08:00–12:00, sức chứa 8; giữ bác sĩ/phòng theo ca. |
| Lịch cá nhân | Lịch khám thấy buổi 04/11, 08:00–12:00, 0 xác nhận/8 còn; lịch làm việc thấy ca cùng ngày/giờ/phòng. |
| Thống kê và danh sách khoa | Ngày 04/11 có 1 buổi mở, sức chứa 8, còn 8; bảng hiển thị đúng buổi vừa tạo. |
| Thu ngắn ca đã có buổi hoạt động | Yêu cầu đổi kết thúc thành 11:00 bị từ chối; thông báo “Cancel linked consultation sessions before changing the shift”. Không tự hủy buổi hoặc bệnh nhân. |
| Rời form chưa lưu → ở lại | Hộp thoại confirm mở; dismiss giữ URL quản lý ca và bản nháp kết thúc 11:00. |
| Rời form chưa lưu → rời trang | Accept chuyển về Lịch khám của tôi, menu bác sĩ xuất hiện. Tải lại 04/11 vẫn thấy buổi gốc 08:00–12:00, còn 8 chỗ. |
| Console cuối lượt | Không có error/warn trong kết quả công cụ ghi nhận tại thời điểm kiểm tra; không phải bằng chứng cho toàn bộ lỗi mạng hoặc hiệu năng. |

Hộp thoại đã xử lý được bằng kết nối trực tiếp: thao tác chuột CDP phát sinh confirm và lệnh nhả chuột timeout trong lúc dialog mở; API lấy dialog và dismiss/accept hoạt động. Không sửa DOM, không vô hiệu hóa cảnh báo và không bỏ qua cơ chế xác nhận của ứng dụng.

## Phát hiện và ưu tiên

1. **Cần sửa:** lỗi nghiệp vụ hiện nguyên tiếng Anh ở form tiếng Việt. Nên giải thích bằng tiếng Việt rằng ca đã có buổi khám hoạt động, giữ nguyên ca và hướng dẫn kiểm tra các buổi liên quan; không hướng người dùng hủy thiếu cân nhắc.
2. **Cần cải thiện:** tiêu đề trang và khối bị lặp; ngày/giờ hiển thị lẫn ngày-tháng/ISO và AM/PM theo trình duyệt; timestamp UTC dài khó đọc.
3. **Cần cải thiện:** mở form từ dòng ca chưa tự cuộn/focus tới form. Sau lưu phải tải lại và chọn lại ngày; thông báo tạo buổi chưa có liên kết mở ngay buổi mới. Danh sách ca bị xóa sau thao tác ghi nên người dùng mất ngữ cảnh.
4. **Đề xuất:** chế độ tuần có lưới bác sĩ/ngày, tổng giờ và cảnh báo trùng để rà phân công nhanh. Hiện tại chế độ tuần là danh sách theo khoảng ngày.

## Giới hạn công cụ điền ngày

Lệnh tự động `fill` cập nhật giá trị DOM của input date nhưng không cập nhật state React trong lượt thử này: ca gửi đi vẫn dùng ngày 01/11; bộ lọc cũng quay về ngày cũ khi render lại. Dùng bàn phím ArrowUp rồi ArrowDown sau khi điền cập nhật state đúng; lưu sửa và tải lại xác nhận ngày 04/11. Chưa tái hiện bằng thao tác ngày thực tế của người dùng nên không kết luận đây là lỗi sản phẩm và không sửa nguồn dựa riêng vào hiện tượng này.

## Khởi động và phần còn lại

Lần gọi `start-backend.bat` đầu tiên của lượt này thất bại ở `prepare_ui_demo.py`: `KeyError: 'paymentRequestId'` khi chuẩn bị quote thanh toán trong rehearsal `snapshot-20261101-131937`. Sau đó chạy rehearsal mới `--scheduling --keep` thành công, đủ 8 ứng dụng UP/READY và seed trưởng khoa thành công. Lỗi seed tổng quát cần điều tra riêng; không coi luồng tiếp nhận/thanh toán đầy đủ là đã đạt.

Chưa nghiệm thu ở lượt này: admin bổ nhiệm/thay thế/miễn nhiệm bằng UI, hủy ca chưa liên kết, xử lý xung đột từ nhiều phiên, toàn bộ hành trình bệnh nhân và hiệu năng tải. Các kiểm chứng tự động/API trong báo cáo gốc không thay thế những thao tác UI này.

## Ảnh bằng chứng

- [Ca lưu sửa ngày 04/11, phiên bản 2](screenshots/05-direct-browser-shift-saved.jpg).
- [Buổi mới trong lịch khám cá nhân, 8 chỗ](screenshots/06-direct-browser-my-calendar.jpg).
- [Ca trong lịch làm việc cá nhân](screenshots/07-direct-browser-my-shifts.jpg).
- [Thống kê và buổi khoa](screenshots/08-direct-browser-department-summary.jpg) (ảnh toàn trang có thể có đặc điểm chụp sticky).
- [Chặn sửa ca đã liên kết và thông báo tiếng Anh](screenshots/09-direct-browser-linked-shift-block.jpg).
- [Bản nháp còn sau khi chọn ở lại](screenshots/10-direct-browser-draft-retained.jpg).
- [Lịch khám sau khi chọn rời trang](screenshots/11-direct-browser-after-dialog.jpg).

Đã đăng xuất, đóng tab kiểm tra, dừng frontend và chạy `clean-docker.bat` thành công. Đối chiếu `docker ps` không còn container chạy; script giữ volume và tài nguyên dự án khác, không global prune. `git diff --check` không có lỗi khoảng trắng. Chỉ cập nhật tài liệu/ảnh nghiệm thu, chưa sửa mã sản phẩm hoặc commit các thay đổi này.
