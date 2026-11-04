# Demo tài khoản qua email — 04/11/2026

Tài liệu này hướng dẫn chạy nhánh email-account-recovery. [Kết quả kiểm thử và chạy tích hợp](../acceptance/email-account-recovery-2026-11-04/README.md) đã được ghi nhận; việc xác thực SMTP không đồng nghĩa thư đã xuất hiện trong Gmail.

## Cấu hình riêng trên máy

- `smtp.env.example` là mẫu được phép đưa lên Git, chỉ chứa giá trị minh họa.
- `smtp.local.env` chứa cấu hình thật trên máy này, đã được `.gitignore` loại trừ bởi `*.env` và `.dockerignore` loại khỏi build context. Không đưa vào Git hoặc nội dung báo lỗi.
- Gmail dùng cổng 587, xác thực và STARTTLS bắt buộc. Tài khoản gửi được cấu hình trong file local.
- `MEDIFLOW_SECURITY_MAIL_KEY` là khóa Base64 của 32 byte ngẫu nhiên để mã hóa nội dung thư đang chờ gửi. Giữ nguyên khóa khi khởi động lại để đọc/gửi lại thư cũ.
- `MEDIFLOW_PUBLIC_WEB_URL=http://localhost:3000` hiện trỏ về frontend. Sau deploy đổi thành origin HTTPS của Vercel, không thêm `/login` phía sau.
- `MEDIFLOW_DEMO_MAILBOX_ENABLED=true` chỉ dành cho môi trường demo. Giữ false khi deploy thật.

Khi dùng `scripts/run/start-backend.bat`, launcher tự nạp file local vào cùng tiến trình trước khi build/chạy dịch vụ; không cần nạp thủ công. Mặc định launcher chọn IP riêng của adapter mạng thật, mở Gateway demo cổng **28080**, và đổi origin email localhost thành `http://<IP-máy>:3000` trong tiến trình chạy. Giá trị trong file local không bị sửa. URL Vercel đã cấu hình sẽ được giữ nguyên.

Chạy `scripts/run/start-frontend.bat` để phục vụ web/link email trên cùng Wi-Fi. Lần đầu mở `scripts/run/start-backend.bat` bằng **Run as administrator** để thêm firewall LocalSubnet trên adapter được chọn cho hai cổng 28080/3000. Các dịch vụ và hạ tầng nội bộ vẫn chỉ nhận kết nối loopback. Để chạy riêng trên máy, dùng `-LocalOnly` cho cả hai launcher; để chọn IP cụ thể, dùng cùng `-LanAddress <IP>` cho cả hai.

Xem [hướng dẫn điện thoại Android/iOS](../../mobile/README.md#demo-điện-thoại-cùng-wi-fi-với-máy-windows). Từ điện thoại, dùng đúng URL IP mà launcher in; `localhost` trên điện thoại là chính điện thoại.

Chạy trực tiếp từ PowerShell tại thư mục gốc:

```powershell
. .\scripts\load-smtp-local.ps1
# Khởi động Core và Gateway từ chính cửa sổ đã nạp biến môi trường này.
# IDE đã mở trước đó không tự nhận các biến mới.
```

Nếu dùng Compose, các biến đã được khai báo cho Core và Gateway:

```powershell
. .\scripts\load-smtp-local.ps1
docker compose up -d --build core-service gateway
```

Không dùng `docker compose config` không có `--quiet` khi chụp log vì cấu hình đã nội suy có thể chứa mật khẩu SMTP. Không dùng `docker compose down -v` khi chỉ cần chạy tính năng này.

Một máy mới có thể sao chép file mẫu sang `smtp.local.env`, điền thông tin SMTP và tạo khóa bằng:

```powershell
$smtpKey = New-Object byte[] 32
$smtpRng = [Security.Cryptography.RandomNumberGenerator]::Create()
$smtpRng.GetBytes($smtpKey)
$smtpRng.Dispose()
[Convert]::ToBase64String($smtpKey)
```

Chỉ lưu kết quả khóa vào file local. Script nạp cấu hình không in credential.

## Thao tác demo

1. ADMIN tạo hồ sơ nhân viên có email nhận thư thật, chọn cấp tài khoản. Mật khẩu tạm do hệ thống tạo; kiểm tra trạng thái thư tại chi tiết tài khoản. Người nhận mở nút đăng nhập trong email và đổi mật khẩu bắt buộc.
2. Khi tiếp nhận bệnh nhân, điền email trước khi cấp tài khoản. Cấp tài khoản không đòi nhập mật khẩu mặc định; hướng dẫn đăng nhập được gửi qua email.
3. ADMIN cấp lại mật khẩu trong chi tiết tài khoản. Nếu thư gửi lỗi, chọn gửi lại thư hiện tại; thao tác này không tạo mật khẩu thứ hai.
4. Tại trang đăng nhập chọn Quên mật khẩu, nhập email. Giao diện luôn trả thông báo chung để không công khai tài khoản tồn tại. Người nhận dùng đường dẫn có hạn dùng để tự đặt mật khẩu mới.
5. Với dữ liệu seed, mở `/demo-mailbox`, chọn vai trò, tìm theo tên/email, chọn tài khoản rồi chọn thư. Chỉ tài khoản seed được backend xác minh mới xuất hiện. Thư HTML hiển thị màu sắc/logo/nút tương ứng; iframe không chạy script hoặc tải tài nguyên bên ngoài.
6. Chỉnh CCCD tại form hồ sơ và nhập lý do khi được yêu cầu. Email đăng nhập không thay đổi theo CCCD.
7. Muốn sửa email của hồ sơ đã có tài khoản, ADMIN mở chi tiết tài khoản, điền email mới và lý do. Form hồ sơ thông thường không thay thế thao tác có audit này.

Đường dẫn trong thư đi qua `/account-access`: đăng nhập chuyển đến `/login`; khôi phục chuyển đến `/reset-password` với token. Không nhận URL chuyển hướng ngoài hệ thống.

## Email đăng nhập của seed mặc định

Giá trị dưới đây lấy từ `backend/core-service/src/main/resources/demo-seed.json`. Hồ sơ đã sửa email hoặc mật khẩu trước đó giữ dữ liệu thực tế sau migration; không dùng bảng này để ghi đè dữ liệu.

| Vai trò | Email seed |
|---|---|
| ADMIN | demo.staff01@example.test |
| MANAGER | demo.staff02@example.test |
| DOCTOR | demo.staff03@example.test |
| DOCTOR | demo.staff05@example.test |
| NURSE | demo.staff04@example.test |
| LAB_TECH | demo.staff15@example.test |
| PHARMACIST | demo.staff17@example.test |
| CASHIER | demo.staff19@example.test |
| PATIENT | demo.patient01@example.test |
| PATIENT | demo.patient02@example.test |

## Khi gặp lỗi

- Chưa gửi thư: kiểm tra trạng thái PENDING/FAILED tại tài khoản; kiểm tra cấu hình đã nạp vào đúng tiến trình Core, khóa mã hóa và quyền Gmail. Không chụp nội dung credential/token để báo lỗi.
- Mất kết nối sau thao tác: dùng chức năng đối soát/kiểm tra hiện có; không tạo một lần cấp tài khoản hoặc cấp mật khẩu khác chỉ để thử lại.
- Hộp thư demo bị tắt: bật cờ ở cả Core và Gateway, khởi động lại hai ứng dụng. Dữ liệu seed vẫn phải có email và đủ chứng cứ seed; email có đuôi giả đơn thuần không cấp quyền xem hộp thư.
- Yêu cầu khôi phục trả 429: Gateway giới hạn chung 5 lần gọi quên/đặt lại mật khẩu trong 15 phút theo kết nối nguồn; chờ hết khoảng giới hạn trước khi tiếp tục demo. Core còn giới hạn yêu cầu gửi link theo từng email.
- Link mở trên thiết bị khác: localhost chỉ truy cập máy đang chạy frontend. Cần đổi public origin thành URL triển khai có thể truy cập trước khi gửi thư cho thiết bị đó.
