# UI bác sĩ, Lab và thu ngân — 03/11/2026

Ảnh chụp từ Next.js hiện tại với API fixture chạy cục bộ, role DOCTOR,
LAB_TECH và CASHIER; người bệnh ghi rõ **dữ liệu thử**. Không có dữ liệu bệnh
nhân thật, mật khẩu hoặc token trong thư mục này. Fixture chỉ phục vụ kiểm tra
bố cục và tương tác; không thay thế [acceptance liên dịch vụ thật](../README.md).
Hai tiến trình phục vụ kiểm tra được tắt sau khi hoàn tất.

| Màn | Máy tính | Màn hình hẹp |
| --- | --- | --- |
| Hồ sơ bác sĩ | [doctor-desktop.png](doctor-desktop.png) | [doctor-mobile.png](doctor-mobile.png) |
| Chi tiết Lab | [lab-desktop.png](lab-desktop.png) | [lab-mobile.png](lab-mobile.png) |
| Hàng thu Lab/Rx | [cashier-desktop.png](cashier-desktop.png) | [cashier-mobile.png](cashier-mobile.png) |

Kiểm tra ở chiều rộng 1440 và 390 pixel. Các màn hồ sơ/chi tiết Lab/hàng thu
được quan sát không tràn ngang ở cấp trang; bảng Lab có vùng cuộn riêng khi hẹp.
DOM `innerText` ở trạng thái bình thường trên ba màn không có chuỗi UUID.
Đây là kiểm tra những màn đã mở, không phải bảo đảm mọi error text từ backend
hoặc mọi feature của ứng dụng đều đã được duyệt bằng browser.

Các tương tác đã quan sát:

- LAB_TECH đăng nhập vào `/lab?status=READY`; dữ liệu thử lúc này IN_PROGRESS nên
  empty state đúng. Chọn IN_PROGRESS và Lọc cập nhật URL và trả một phiếu.
  Mở phiếu giữ returnTo cùng bộ lọc. Hủy và hỗ trợ đóng mặc định.
- Mở nhập kết quả và nhập bản nháp: refresh bị khóa trong lúc sửa. Kiểm thử
  tự động còn xác nhận bản nháp/khóa unknown outcome vẫn giữ sau lần đọc lại.
- Hồ sơ bác sĩ hiển thị tên/ngày sinh/khoa/bác sĩ, không cần mã định danh; form
  thêm chẩn đoán/hoàn tất và đối chiếu đóng mặc định. Bản nháp chẩn đoán giữ sau
  đọc lại dữ liệu thử; kiểm thử tự động kiểm tra cả pending read/write và thay đổi
  trạng thái từ máy khác.
- Thu ngân chọn hàng thu bằng bệnh nhân/khoa/thời điểm/số tiền; không còn nút
  Chọn khoản trên item đang chọn. Không thực hiện thu/hoàn tiền qua fixture.
- Menu Tài khoản mở/đóng, Escape đóng và trả focus về summary; sidebar đóng
  mobile không còn các link nghiệp vụ trong accessibility tree.

## Kiểm chứng mã

Kết quả cuối trên working tree sau khi đóng băng source:

- Toàn frontend: **145 files, 1.299 tests, 0 failed, 0 skipped** với
  `pnpm test --maxWorkers=4 --testTimeout=20000`. Giới hạn worker và thời gian chờ
  này áp dụng cho lượt kiểm tra, không thay cấu hình test hoặc source sản phẩm.
- Lượt consumer trước đó: 29 files / 288 tests đạt, gồm Lab/Billing, các màn
  appointment liên quan và consumers Notification/Report của khung lỗi chung.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`, `git diff --check` đạt.
- Review độc lập: Go, không còn phát hiện cần sửa. Đã sửa identity báo giá khi
  khôi phục giao dịch và thứ tự bàn phím của retry trước disclosure hỗ trợ.

Các lượt broad trước từng lỗi do assertion cũ yêu cầu mã luôn hiện và do thứ tự
Tab bị thay đổi; những lỗi này đã được sửa trước lượt cuối. Không lấy lượt chạy
đơn lẻ của một file thay cho kết quả toàn frontend nêu trên. Không sửa source
hoặc tests feature frontend thuộc owner khác; đã chạy các consumer đó để kiểm
tra ảnh hưởng của thành phần dùng chung.

Tóm tắt máy đọc được: [verification-summary.json](verification-summary.json).
Packaged backend acceptance trước đó vẫn là bằng chứng API thật; chưa thực hiện
browser acceptance liên dịch vụ thật cho toàn bộ bố cục mới, chưa commit/deploy.
