# MediFlow Mobile

Flutter client dùng **backend thật qua Gateway**. Không có demo, tài khoản giả hay dữ liệu mẫu trong ứng dụng. Backend tắt thì ứng dụng báo lỗi kết nối.

## Chạy chung cho cả 4 dev

Cài Flutter stable tương thích Dart `^3.13.0`, đưa flutter/dart vào PATH. Khởi động PostgreSQL/RabbitMQ, Eureka, Gateway và các service cần dùng theo README gốc. Gateway mặc định `http://localhost:8080`; cần tài khoản thật đã có trong backend.

Từ thư mục mobile, chạy:

```powershell
./tool/run_web.ps1
# Đổi địa chỉ khi cần:
./tool/run_web.ps1 -Port 4301 -Gateway http://127.0.0.1:8080
```

Mở `http://localhost:4300` (hoặc port đã chọn). Script lấy dependencies, build web rồi chạy static host cùng proxy `/api/*` sang Gateway thật, tránh cần sửa CORS backend. Proxy bind loopback, chỉ dùng phát triển. Ctrl+C để dừng; sau khi sửa code chạy lại script để build. Không đưa mật khẩu/token vào lệnh hoặc Git.

## Thiết bị và cấu hình

GATEWAY_URL phải có hậu tố `/api`, không chứa credentials/query/fragment. Cấm gọi cổng service trực tiếp. Ví dụ:

```powershell
flutter pub get
flutter run -d <device-id> --dart-define=GATEWAY_URL=https://gateway.example.org/api
# Android emulator dùng 10.0.2.2 để tới máy chủ:
flutter run -d <android-device-id> --dart-define=GATEWAY_URL=http://10.0.2.2:8080/api
```

- Android cần SDK và licenses (`flutter doctor --android-licenses`). Debug cho phép HTTP local; release dùng HTTPS. Signing scaffold hiện phục vụ phát triển, cần cấu hình riêng trước phát hành.
- Điện thoại thật dùng địa chỉ Gateway truy cập được. HTTP LAN cần `--dart-define=ALLOW_INSECURE_HTTP=true` và vẫn chịu chính sách nền tảng; nên dùng HTTPS.
- iOS cần macOS/Xcode, ATS mặc định yêu cầu HTTPS. Cờ Dart không bỏ qua ATS.
- Windows native cần Visual Studio Desktop development with C++, MSVC và CMake.
- Web chạy trực tiếp bằng flutter run cần Gateway cho phép browser origin. Script proxy giải quyết cho local. SecureStorage web cần HTTPS hoặc localhost; đổi port là đổi browser origin.

SecureStorage lưu phiên theo Gateway. Phiên hết hạn/401 yêu cầu đăng nhập lại; chưa tự refresh hoặc gửi lại lệnh nghiệp vụ. Backend quyết định quyền truy cập.

## Phần hiện có và chia việc

Có đăng nhập thật, đăng xuất, lưu phiên, menu/route theo role. Lịch hẹn đọc `/v1/appointments`, lọc ngày/phân trang; Lab đọc `/v1/lab`, lọc trạng thái/phân trang. Có loading/empty/error/retry, correlation ID và bỏ qua phản hồi cũ. Lab hiển thị trạng thái nghiệp vụ riêng với thanh toán.

Các context khác có thư mục và route thông báo đang phát triển, chưa có nghiệp vụ. Inpatient/Surgery vẫn deferred.

| Dev | Feature trong lib/features | Tiếp theo |
|---|---|---|
| Vinh | appointment, medical_record, lab | Chi tiết/form theo API thật, hồ sơ khám; điều phối app/core/auth |
| Hoàng Anh | organization, patient | Danh sách/chi tiết/form từ DTO Core |
| Huy | pharmacy, report | Thuốc, đơn thuốc, báo cáo MVP |
| Lộc | billing, notification | Hóa đơn/thanh toán, thông báo và handoff backend |

Đọc [hướng dẫn bổ sung feature](docs/contributing.md), AGENTS từng feature và blueprint `../docs/ai/14-flutter.md`.

## Kiểm tra

```powershell
./tool/check.ps1
```

Script kiểm tra format, analyzer, test ứng dụng và loader package, build web. Test doubles chỉ nằm trong test. Unit/widget test không thay kiểm tra đăng nhập/backend thật. Host Android/iOS/Windows đã scaffold; phải build với toolchain phù hợp trước khi xác nhận chạy native.
