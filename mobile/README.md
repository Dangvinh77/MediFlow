# MediFlow Mobile — Outpatient Care Management

Client cho **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**, hướng tới phòng khám đa khoa và khoa khám bệnh ngoại trú của bệnh viện. Nội trú/phẫu thuật hoãn; feature có thư mục hoặc route chưa đồng nghĩa đã hoàn tất nghiệp vụ.

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

Từ 2026-10-21, theo phân công bổ sung của người dùng, Vinh tiếp nhận hoàn thiện client
Organization/Patient và Billing/Notification. Chi tiết phạm vi và bằng chứng kiểm tra của
đợt này ở [client completion](docs/ha-loc-client-completion.md). Inpatient/Surgery vẫn deferred.

2026-10-22, Pharmacy đạt **11/12 task code (MP-HUY-01–11)**. Người dùng đã giao thêm shared
network/app/config: POST tạo V1 gửi Idempotency-Key, PUT xuất thuốc không body; repository,
reactive session, mode và nested routes đã nối vào app có sẵn. Giữ light/dark Material3,
Riverpod/GoRouter và Gateway client; không thêm package hoặc đổi backend gates.
**Task12 chưa nghiệm thu thật**, theo lựa chọn mới của người dùng: hoàn thành code, test thật sau.
Mock tests/build không xác nhận clearance, quyền issuer hoặc một lần trừ kho.
[Evidence hiện hành](docs/evidence/huy-pharmacy/2026-10-22-integration/README.md) ghi candidate/checks/giới hạn.

Pharmacy mode cấu hình lúc build bằng `--dart-define=PHARMACY_MODE=outpatient` cho backend V1
được operator xác nhận, hoặc `legacy` cho V0. Mặc định/giá trị khác là `unavailable`:
vẫn đọc danh mục nhưng khóa các command cần mode. Không suy V1 từ prescription response
hoặc tự chuyển backend profile. Cấu hình này không bật backend gates.
Checkpoint186 tests trước đây là [lịch sử task04–06](docs/evidence/huy-pharmacy/2026-10-22-local/README.md).

| Dev | Feature trong lib/features | Tiếp theo |
|---|---|---|
| Vinh | appointment, medical_record, lab | Chi tiết/form theo API thật, hồ sơ khám; điều phối app/core/auth |
| Vinh (tiếp nhận Hoàng Anh) | organization, patient | Đã bổ sung danh sách/chi tiết/form, chuyển khoa và lệnh tài khoản theo DTO Core; cần nghiệm thu Gateway thật |
| Huy | pharmacy, report | Thuốc, đơn thuốc, báo cáo MVP |
| Vinh (tiếp nhận Lộc) | billing, notification | Đã bổ sung hóa đơn/thanh toán/hoàn tiền, báo cáo và thông báo theo API hiện có; cần nghiệm thu Gateway thật |

Plan Pharmacy riêng của Huy: [12 task triển khai mobile Pharmacy](docs/huy-pharmacy-mobile-plan.md)
(2026-10-20). Bắt đầu từ danh mục/chi tiết/tra cứu đơn, sau đó các command ngoại trú V1;
phân biệt feature implementation, shared integration và nghiệm thu thật. Bảng ownership bên trên
không tự mở quyền shared hoặc mở thêm Notification cho Huy.

Đọc [hướng dẫn bổ sung feature](docs/contributing.md), AGENTS từng feature và blueprint `../docs/ai/14-flutter.md`.

## Kiểm tra

```powershell
./tool/check.ps1
```

Script kiểm tra format, analyzer, test ứng dụng và loader package, build web. Test doubles chỉ nằm trong test. Unit/widget test không thay kiểm tra đăng nhập/backend thật. Host Android/iOS/Windows đã scaffold; phải build với toolchain phù hợp trước khi xác nhận chạy native.
