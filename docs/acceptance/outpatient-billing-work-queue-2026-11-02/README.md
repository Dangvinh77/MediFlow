# Nghiệm thu hàng khoản thu Lab/thuốc — 02/11/2026

Nhánh `codex/outpatient-billing-work-queue`, nền `c94044b9` (PR #562 đã merge).
Phạm vi MF-03 theo [thiết kế](../../superpowers/specs/2026-11-02-outpatient-billing-work-queue.md).
Chỉ dữ liệu tổng hợp trong runtime cách ly; giữ giờ thực Việt Nam.

## Kiểm tra mã nguồn

| Kiểm tra | Kết quả |
|---|---|
| Frontend Billing | 109 kiểm thử / 16 tệp đạt, `--maxWorkers=2` |
| Kiểm tra kiểu, lint, production build | Đạt trên mã nguồn cuối trước phiên UI |
| Billing đầy đủ, profile mặc định | 1.221 kiểm thử đạt; 0 lỗi/bỏ qua |
| Billing chạy tập trung sau bổ sung kiểm thử count/page đồng thời | 55 kiểm thử đạt: PostgreSQL 26, HTTP JWT 24, cấu hình 5 |
| Gateway đầy đủ, profile mặc định | 1.792 kiểm thử đạt; 0 lỗi/bỏ qua |
| Rà soát độc lập theo yêu cầu rồi chất lượng/bảo mật | Đạt sau sửa các lỗi khóa thao tác, đổi phiên và phục hồi khoản thu |

XML hiện có của Billing tổng cộng 1.222 kiểm thử không lỗi/bỏ qua: gồm lượt đầy đủ
và lượt tập trung bổ sung một ca đồng thời. Không gọi đây là một lượt đầy đủ 1.222.
Chưa chạy profile nghiệm thu publication tùy chọn. Một lượt frontend chạy nhiều
worker gặp lỗi đổi tên tệp tạm trên Windows; chạy lại giới hạn 2 worker đạt, không
nới timeout hay bỏ kiểm thử. Lượt Billing đầy đủ có cảnh báo Surefire khi dừng sau
kiểm thử do listener reconnect tới container đã dừng; Maven kết thúc thành công.

Các ca hồi quy bao gồm: sai patient/source/episode/request bị chặn; đọc muộn;
khóa đổi khoản/lọc khi pending hoặc chưa rõ kết quả; hết hạn báo giá không làm mất
recovery; đổi phiên trước gửi không gửi lệnh; kết quả phiên A không xóa recovery
phiên B; hủy trước khi replay không xóa recovery cũ; lỗi lưu recovery lúc replay
giữ nguyên khóa chống trùng; xác nhận thành công giữ biên nhận khi đọc lại lỗi.
Lab nghĩa vụ 0 không tạo khoản thu tiền giả. PostgreSQL thật kiểm tra lọc/phân trang,
snapshot count/page, khoản đã thu/hủy/hết hạn/đóng, số dư và mapping lỗi.

## Phạm vi nghiệm thu UI

Phần chuẩn bị lượt khám, phí khám, hồ sơ, chỉ định Lab và đơn thuốc sử dụng API công
khai qua Gateway, các tài khoản demo đúng vai trò. Phần chọn khoản và thu Lab/thuốc
được bấm trên trình duyệt. Đây không phải nghiệm thu lại toàn bộ hành trình bằng UI.

Phiên 16:30–16:37 ngày 02/11, runtime `snapshot-20261102-163007`, Gateway 28080,
frontend 3000. Trình duyệt nền, khung nhìn thông thường 1265×720, không toàn màn hình.
Đăng nhập `demo.cashier`; tìm CCCD tổng hợp `999881000001`, chọn Nguyễn Văn An Demo,
12/04/1986, Khoa Nội tổng quát Demo. Không nhập UUID trên UI thu ngân.

| Thao tác trực tiếp | Quan sát |
|---|---|
| Chọn CBC, lọc cùng lượt | Hai khoản Lab/thuốc đúng người; chi tiết đọc lại nguồn, CBC 36.000 VND |
| Mở chi tiết báo giá | 120.000 trước hỗ trợ, 84.000 hỗ trợ mô phỏng, 36.000 người bệnh trả |
| Xác nhận thu CBC | 36.000 thành công; số dư 0; CBC rời hàng chờ, biên nhận còn ở chi tiết |
| Lọc Đơn thuốc, chọn và thu thuốc cảnh báo tồn thấp | 4.000 thành công; hàng chờ về 0, không còn nút thu |
| Tạo thêm đúng Thuốc minh họa A qua API, cập nhật hàng chờ | Khoản mới 20.000 xuất hiện, biên nhận khoản cũ vẫn giữ trước khi chọn khoản mới |
| Chọn thuốc A và xác nhận thanh toán | 20.000 thành công; số dư 0; hàng chờ về 0 |
| Đọc lại khoản vừa thu | Giữ biên nhận, trạng thái đã thanh toán, không mở lại nút thu |

Khoản thuốc 4.000 là một fixture bổ sung, không phải thuốc A trong hướng dẫn demo.
API tìm thuốc trả nhiều kết quả cùng từ khóa; sau lần thử đầu đã chọn chính xác tên
thuốc A và thử lại 20.000. Hướng dẫn demo được bổ sung tên cần tránh để không nhầm.

Ảnh gốc trong thư mục này:

- [Chọn CBC, bố cục hai cột](01-lab-selected-full.png), [chi tiết giá](02-lab-quote-full.png).
- [CBC đã thu, thuốc còn chờ](03-lab-paid-full.png).
- [Thuốc cảnh báo tồn thấp đã thu](05-rx-paid-full.png).
- [Thuốc A 20.000 trước thu](06-rx-A-selected-full.png), [sau thu](07-rx-A-paid-full.png).
- [Biên nhận thuốc A trong khung nhìn thực](08-rx-A-receipt.png).

Định danh phục vụ đối soát bằng chứng, không phải dữ liệu cần gõ để demo:
registration `ab2d67e6-991f-4a19-8c50-5c57ae0a4ab3`, record
`4d75e79b-c866-486c-aa66-3639f6986f9d`, Lab `747e3888-10a8-41ef-a4a0-f4d2d9fabbe4`,
đơn A `1e2b7622-5521-439b-815f-9fe8b8f0877f`.

Đánh giá bố cục: danh sách trái và số dư/người/khoa bên phải đọc được trong khung
thông thường; mã hỗ trợ thu gọn, kết quả thu không biến mất khi hàng chờ cập nhật.
Màn chi tiết vẫn khá dài, thông báo Rx chưa có báo giá còn nhiều ngôn ngữ kỹ thuật;
thẻ chưa có tên chỉ định/thuốc để phân biệt hai nguồn cùng thời điểm và số tiền.
Các điểm này cần contract nhãn nghiệp vụ và vòng cải thiện tiếp theo, không giả lập
tên nguồn từ UUID hoặc gọi nhiều service để dựng số dư bên frontend. Bản dev có
lần biên dịch trang đầu khoảng 9 giây; chưa dùng số này để kết luận hiệu năng bản production.

## Giới hạn

Sau nghiệm thu đã dừng đúng hai tiến trình Next của frontend này, chạy
`clean-docker.bat` thành công. Kiểm tra lại cả 9 cổng demo (3000, 28080, 28089,
28082, 28084, 28085, 28086, 28087, 28761) không còn listener; Docker không còn
container tên `mediflow`. Script giữ volumes và không chạy global prune.

- Phí khám vẫn dùng hàng chờ V2 riêng; chưa có thu gộp Khám/Lab/thuốc.
- `Phát sinh` là thời điểm Billing lập khoản thu, không thay cho ngày khám.
- Danh mục chỉ định, phục hồi chỉ định bền vững, bàn khám và UI Pharmacy/Report/
  Patient/Organization còn theo backlog và owner; không đóng toàn bộ MF-03 hay review.
- Chưa đo hiệu năng production với dữ liệu lớn hoặc nghiệm thu nhiều quầy đồng thời
  bằng trình duyệt. Các tình huống Lab0/mất phản hồi/đổi phiên được kiểm tra tự động,
  không trình bày như ảnh UI đã thao tác.
- Không thay đổi phạm vi Surgery/Inpatient.
