# Kiểm tra lại bằng Edge InPrivate — 01/11/2026

Code: master `8832d47f` (PR #557 đã merge). Dữ liệu dùng một lần: rehearsal `snapshot-20261101-131006`, scheduling bật. Chỉ dùng tài khoản và dữ liệu demo; không đăng ký bệnh nhân vào buổi mới.

## Kết quả thao tác thực tế

| Thao tác | Kết quả quan sát |
| --- | --- |
| Đăng nhập `demo.doctor` | Vào Lịch khám của tôi; vẫn là bác sĩ, có nút Sang Quản lý khoa. |
| Chuyển sang Quản lý khoa | Hiện đúng Khoa Nội tổng quát Demo, menu quản lý riêng và nút Về Khám bệnh. |
| Xem ca trong tuần 02–08/11 | Tải được ca 03/11/2026, 08:00–12:00, đúng bác sĩ/phòng, đang hiệu lực. |
| Mở Tạo buổi khám từ giờ làm việc | Giữ bác sĩ/phòng theo ca, nhập được giờ và sức chứa. Form xuất hiện bên dưới, phải cuộn tới. |
| Rời form chưa lưu | Hộp thoại cảnh báo xuất hiện. Lần trước công cụ dừng khi bấm Cancel vì không xác minh được URL; chưa kết luận đã hoàn tất thao tác Cancel. Khi tiếp tục, form vẫn còn 11:00–12:00, sức chứa 6. |
| Thu cửa sổ Edge | Đã bấm Restore, xác nhận nút cửa sổ đổi thành Maximize; review tiếp ở cửa sổ thường khoảng 939 px rộng, không phóng to. Menu chuyển sang nút hamburger, form vẫn dùng được. |
| Lưu buổi khám từ ca | Thành công qua nút Xác nhận tạo buổi khám; UI hiển thị “Đã tạo buổi khám. Xem buổi khám trong tổng quan khoa.” Form trở về phân công mới. |
| Về Khám bệnh sau khi lưu | Tiêu đề cửa sổ chuyển thành Lịch khám của tôi; dev server ghi GET `/appointments/my-calendar` 200. Chụp trang đích thất bại nên chưa xác nhận buổi mới trong lịch cá nhân. |

Các ảnh đã được quan sát trực tiếp từ công cụ trong phiên; không có ảnh mới được lưu vào repository. Không thay thế các phần chưa kiểm chứng bằng kết quả API/unit test.

## Điểm UX cần xử lý tiếp

1. Tiêu đề “Lịch khám của tôi” và “Giờ làm việc bác sĩ” bị lặp giữa trang và khối nội dung.
2. Ô ngày hiển thị tháng/ngày/năm theo Edge trong khi các kết quả dùng ngày/tháng/năm hoặc ISO; giờ nhập dùng AM/PM còn kết quả dùng 24 giờ. Cần diễn giải định dạng rõ và thống nhất phần hiển thị cho người dùng Việt Nam.
3. Dashboard hiện thời điểm quan sát bằng UTC có phần thập phân dài; nên hiển thị giờ Việt Nam dễ đọc và đưa giá trị kỹ thuật vào chi tiết khi cần.
4. Khi mở form từ một ca, chưa tự cuộn hoặc chuyển focus đến form; người dùng dễ tưởng nút không hoạt động.
5. Sau tạo buổi, thông báo chỉ hướng dẫn bằng chữ; nên có liên kết mở ngay buổi vừa tạo/ngày tương ứng. Danh sách ca đang xem cũng biến mất sau khi lưu, cần tải lại.
6. Chế độ tuần hiện là danh sách ca theo khoảng ngày; chưa có lưới bác sĩ/ngày hoặc tổng giờ để rà phân công nhanh. Đây là đề xuất cải tiến, không phải chức năng đã nghiệm thu.

## Hiệu năng và giới hạn

Trong dev server, các GET trang đã quan sát trả 200 với thời gian xử lý khoảng 42–119 ms sau đăng nhập; trang login đầu tiên khoảng 633 ms. Đây là thời gian request trang do Next.js ghi nhận, không phải thời gian tương tác đầu-cuối hoặc kiểm thử tải. UI có trạng thái khóa nút trong lúc lưu.

Lần thử tiếp tục gặp lỗi chụp `encode latest capture frame failed: window crop is outside captured monitor`. Đã lấy lại danh sách cửa sổ, gắn lại đúng cửa sổ InPrivate và thử khôi phục một lần; lỗi vẫn lặp nên dừng điều khiển UI. Chưa nghiệm thu admin bổ nhiệm/miễn nhiệm, tạo/sửa/hủy ca bằng UI, lịch cá nhân có buổi mới và toàn bộ tương tác hộp thoại chưa lưu.

Lần này `start-backend.bat` hoàn tất cả `--prepare-ui-demo`, in FOLLOW-UP SOURCE READY và READY; không tái hiện lỗi Billing 503 của phiên trước. Chưa kết luận nguyên nhân lỗi trước và không suy ra toàn bộ luồng bệnh nhân đã đạt.

Đã dừng frontend và chạy `clean-docker.bat` thành công; script giữ volume và tài nguyên dự án khác, không global prune.
