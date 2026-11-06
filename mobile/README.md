# MediFlow Mobile — Outpatient Care Management

Cập nhật luồng hồ sơ trên baseline `d9a1635b`: tài khoản nhân sự/bệnh nhân được quản lý
ngay trong hồ sơ; điều dưỡng cấp/reset tài khoản bệnh nhân theo quyền. Tạo hồ sơ từ
tiếp nhận mở thẳng hồ sơ mới để cấp tài khoản hoặc đăng ký khám. Tiếp nhận V2 có buổi
khám/số chỗ, phiếu chờ thu tiền và tiếp nhận bệnh nhân tiếp theo; kê đơn được ghép vào
hồ sơ khám qua app composition. [Phạm vi, kiểm tra và giới hạn nghiệm thu](docs/evidence/2026-11-04-profile-workflows/README.md).

Đồng bộ 04/11/2026 với master `1ce0968e`: email login tối đa 254 ký tự, Quên mật khẩu và
Đặt lại mật khẩu (dán liên kết/mã từ email), tạo tài khoản với mật khẩu do backend sinh,
Lab cancellation lấy actor từ backend và sửa bố cục màn hình nhỏ/keyboard/large text.
[Checks và screenshots](docs/evidence/2026-11-04-ui/README.md): 376 tests, web build,
Android debug APK PASS; chưa nghiệm thu qua Gateway/device trong phiên này.
Chưa cấu hình universal links Android/iOS cho domain triển khai.

Client cho **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**, hướng tới phòng khám đa khoa và khoa khám bệnh ngoại trú của bệnh viện. Nội trú/phẫu thuật hoãn; feature có thư mục hoặc route chưa đồng nghĩa đã hoàn tất nghiệp vụ.

Flutter client dùng **backend thật qua Gateway**. Không có demo, tài khoản giả hay dữ liệu mẫu trong ứng dụng. Backend tắt thì ứng dụng báo lỗi kết nối.

## Chạy chung cho cả 4 dev

Cài Flutter stable tương thích Dart `^3.13.0`, đưa flutter/dart vào PATH. Khởi động PostgreSQL/RabbitMQ, Eureka, Gateway và các service cần dùng theo README gốc. Gateway mặc định `http://localhost:8080`; cần tài khoản thật đã có trong backend.

Từ thư mục mobile, chạy:

```powershell
./tool/run_web.ps1
# Đổi địa chỉ khi cần:
./tool/run_web.ps1 -Port 4301 -Gateway http://127.0.0.1:8080
# Demo với backend scripts/run/start-backend.bat: bật UI lệnh ngoại trú + Pharmacy V1
./tool/run_web.ps1 -Demo -Gateway http://127.0.0.1:28080
```

Riêng báo cáo có thể bật bằng `-ReportOutpatientUi`. Khi bật, màn báo cáo ưu tiên
thống kê vận hành theo khoa; báo cáo ngoại trú đã đối soát nằm trong danh sách loại
báo cáo. Bấm **Tìm kỳ có dữ liệu → Dùng kỳ này** để tải ngay kỳ đã đối soát.
Dữ liệu mô phỏng giữ nhãn riêng; kỳ chưa đối soát không được coi là hoạt động bằng 0.

Không có `-Demo` (hoặc `-OutpatientUi`, `-PharmacyMode outpatient`) thì app vẫn đọc dữ liệu
nhưng các thao tác tiếp nhận/khám/kê đơn/xuất thuốc bị khóa.

Mở `http://localhost:4300` (hoặc port đã chọn). Script lấy dependencies, build web rồi chạy static host cùng proxy `/api/*` sang Gateway thật, tránh cần sửa CORS backend. Proxy bind loopback, chỉ dùng phát triển. Ctrl+C để dừng; sau khi sửa code chạy lại script để build. Không đưa mật khẩu/token vào lệnh hoặc Git.

### Test UI nhanh có hot reload

Từ thư mục mobile, sau khi Gateway đã chạy:

```powershell
# Chrome: F12, Ctrl+Shift+M, chọn mẫu iPhone rồi tải lại trang
./tool/run_dev.ps1
# Android emulator: tự bật pixel_api36 nếu chưa chạy
./tool/run_dev.ps1 -Target android
# Backend demo và cờ UI ngoại trú, dùng được với cả hai target
./tool/run_dev.ps1 -Target android -Demo -Gateway http://127.0.0.1:28080
```

Nhấn `r` để hot reload, `R` để hot restart, `q` để thoát. Với Chrome, script sinh
`web_dev_config.yaml` (đã gitignore) để Flutter proxy `/api/*` sang Gateway, không cần sửa CORS.
Với emulator, Gateway loopback được đổi thành `10.0.2.2`. Đổi AVD bằng `-Emulator <id>`;
log emulator nằm ở `%TEMP%\mediflow-emulator`. Chrome giả lập iPhone chỉ kiểm tra được layout:
không có font SF Pro, bàn phím iOS và safe area thật, nên vẫn phải kiểm tra trên iPhone trước khi demo.

## Thiết bị và cấu hình

GATEWAY_URL phải có hậu tố `/api`, không chứa credentials/query/fragment. Cấm gọi cổng service trực tiếp. Ví dụ:

```powershell
flutter pub get
flutter run -d <device-id> --dart-define=GATEWAY_URL=https://gateway.example.org/api
# Android emulator dùng 10.0.2.2 để tới máy chủ:
flutter run -d <android-device-id> --dart-define=GATEWAY_URL=http://10.0.2.2:8080/api
```

Với backend demo ngoại trú đã bật consumer/read gates, `tool/run_web.ps1 -Demo`
bật cả `MEDIFLOW_OUTPATIENT_UI_ENABLED` và `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED`.
Bản native cần truyền riêng hai `--dart-define=...=true` tương ứng. Report mở mặc định
biểu đồ doanh thu khám/xét nghiệm/thuốc và lượt khám theo ngày, theo khoa. Đây là số liệu
đã đồng bộ, chưa đối soát; số 0 không chứng minh không có hoạt động. Báo cáo đã đối soát
vẫn cần phạm vi coverage hợp lệ và được chọn riêng trong loại báo cáo.

- Android cần SDK và licenses (`flutter doctor --android-licenses`). Debug cho phép HTTP local; release dùng HTTPS. Signing scaffold hiện phục vụ phát triển, cần cấu hình riêng trước phát hành.
- Điện thoại thật dùng địa chỉ Gateway truy cập được. HTTP LAN cần `--dart-define=ALLOW_INSECURE_HTTP=true` và vẫn chịu chính sách nền tảng; nên dùng HTTPS.
- iOS cần macOS/Xcode. Bản Debug và Profile dùng `Runner/Info-Debug.plist`, có quyền mạng nội bộ và cho phép HTTP để demo bằng IP; Release dùng `Runner/Info.plist` với ATS mặc định. Cờ Dart không tự bỏ qua ATS. Khi iOS hỏi quyền Local Network, chọn cho phép.
- Windows native cần Visual Studio Desktop development with C++, MSVC và CMake.
- Web chạy trực tiếp bằng flutter run cần Gateway cho phép browser origin. Script proxy giải quyết cho local. SecureStorage web cần HTTPS hoặc localhost; đổi port là đổi browser origin.

SecureStorage lưu phiên theo Gateway. Phiên hết hạn/401 yêu cầu đăng nhập lại; chưa tự refresh hoặc gửi lại lệnh nghiệp vụ. Backend quyết định quyền truy cập.

### Demo điện thoại cùng Wi-Fi với máy Windows

1. Chạy `scripts/run/start-backend.bat` từ thư mục gốc (hoặc mở file trong `scripts/run`). SMTP được nạp tự động từ `smtp.local.env`. Lần đầu chạy bằng **Run as administrator** để tạo/cập nhật firewall cho TCP 28080 và 3000, giới hạn LocalSubnet trên đúng adapter đang chọn. Không cần đổi loại mạng Windows đang dùng.
2. Chờ `READY`. Launcher in `Mobile Gateway: http://<IP-máy>:28080/api`. Cổng demo là **28080**, khác cổng 8080 của cách chạy dịch vụ thông thường.
3. Chạy `scripts/run/start-frontend.bat` nếu cần mở web, mailbox hoặc link trong email trên điện thoại. Dùng URL IP cổng 3000 mà launcher in ra cho cả desktop và điện thoại để nút trong mailbox có cùng origin.
4. Trên máy có Flutter và thiết bị Android/iOS kết nối, thay IP bằng địa chỉ được in ở bước 2:

```powershell
cd mobile
flutter run --debug --dart-define=GATEWAY_URL=http://192.168.1.3:28080/api --dart-define=ALLOW_INSECURE_HTTP=true --dart-define=MEDIFLOW_OUTPATIENT_UI_ENABLED=true --dart-define=MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true --dart-define=MEDIFLOW_SCHEDULING_UI_ENABLED=true --dart-define=MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true --dart-define=MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true --dart-define=PHARMACY_MODE=outpatient
```

Địa chỉ trên chỉ là ví dụ. Khi đổi Wi-Fi/IP, chạy lại launcher và build lại app với URL mới. Đăng nhập bằng **email** của tài khoản. Build iOS thực hiện trên Mac/Xcode, backend vẫn chạy trên máy Windows cùng Wi-Fi.

Có thể chọn adapter bằng `scripts/run/start-backend.bat -LanAddress 192.168.1.3` và truyền cùng tham số cho `scripts/run/start-frontend.bat`. Dùng `-LocalOnly` ở cả hai launcher để chạy riêng trên máy. Các service/DB/broker nội bộ của demo vẫn bind loopback; mobile gọi Gateway.

Kiểm tra kết nối bằng trình duyệt điện thoại tại `http://<IP-máy>:28080/actuator/health`. Nếu không kết nối được, kiểm tra cùng Wi-Fi, firewall đã được tạo và mạng không bật cách ly thiết bị/guest Wi-Fi. Quy tắc firewall có tên `MediFlow-Lan-Demo-28080` và `MediFlow-Lan-Demo-3000`.

Thiết lập iOS Debug dựa trên [Apple ATS](https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsallowsarbitraryloads) và [quyền mạng nội bộ](https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription). Đây là cấu hình demo HTTP; bản phát hành dùng Gateway HTTPS.

## Build IPA chưa ký bằng GitHub Actions

Workflow [Mobile iOS unsigned IPA](../.github/workflows/mobile-ios-unsigned.yml) dùng
macOS/Xcode và Flutter **3.47.0**, không cần chứng chỉ hoặc Apple secrets. Workflow chạy
khi push vào `master` hoặc mở/cập nhật PR có thay đổi `mobile/**` hay chính workflow.
Cũng có thể vào **Actions → Mobile iOS unsigned IPA → Run workflow** để build thủ công.

- `gateway_url`: URL Gateway kết thúc bằng `/api`. Nếu bỏ trống, lấy repository variable
  `MOBILE_GATEWAY_URL`; nếu chưa đặt variable thì dùng `http://192.168.1.3:28080/api`.
  Đặt variable tại **Settings → Secrets and variables → Actions → Variables**. URL này
  được đóng gói vào app; IP backend đổi thì phải build lại. Không dùng localhost/cổng service.
- `build_mode`: mặc định `profile`, mở được từ màn hình chính iPhone, dùng plist cho phép HTTP LAN
  và có mô tả quyền Local Network. `debug` cũng dùng plist đó nhưng từ iOS 14 chỉ khởi động được
  bằng Flutter tooling hoặc Xcode, mở từ icon sẽ báo lỗi. `release` yêu cầu Gateway HTTPS; workflow chặn Release với HTTP để tránh artifact không kết nối được.
- Sau khi thành công, tải artifact `mediflow-ios-unsigned-<mode>-<run>` ở cuối trang run.
  Bên trong có `mediflow-unsigned.ipa`, checksum SHA-256, `build-info.json` và hướng dẫn.
  Artifact được giữ 14 ngày. Các feature gate ngoại trú/Report và Pharmacy outpatient đã bật.

Workflow chạy mobile tests, build `flutter build ios --no-codesign`, rồi đóng gói
`Payload/Runner.app` thành IPA. Không dùng `flutter build ipa --no-codesign` để mong có
IPA tự động: Flutter bỏ qua bước export IPA khi tắt ký.

**IPA này chưa ký, chưa cài trực tiếp lên iPhone và chưa đưa lên TestFlight được.**
Muốn cài cần bước ký ứng dụng và provisioning profile phù hợp. Bản Debug/Profile chưa ký cũng
không phải bản phát hành; workflow chưa cấu hình Apple signing hoặc upload App Store.
Runner GitHub không cần kết nối tới IP LAN để biên dịch; backend chỉ cần truy cập được
khi chạy app trên điện thoại cùng mạng. iOS runtime vẫn cần người dùng cấp Local Network.

## Phần hiện có và chia việc

**Phân công hiện hành 03/11/2026:** Vinh nhận toàn bộ mobile từ leader qua chỉ định trực tiếp
của người dùng, bao gồm Pharmacy/Report, mọi context đang hoạt động và shared app/platform.
Các bảng phân công theo đợt bên dưới là lịch sử. Theo dõi phần còn thiếu và tiêu chí hoàn tất
tại [workboard toàn mobile](docs/mobile-completion-workboard.md).

Đợt đầu bổ sung màn hình đổi mật khẩu tại `/account/password`, tự điều hướng khi access JWT
có `mustChangePassword=true`; thành công yêu cầu đăng nhập lại. PATIENT có hai mục
`/my-results/exams` và `/my-results/lab` dùng API `mine` từ signed principal, không nhập mã
bệnh nhân hoặc gọi endpoint hồ sơ dành cho staff. Đã có
[nghiệm thu web một phần qua Gateway](docs/evidence/2026-11-03-web/README.md) và
[Android debug build/install/launch và UI smoke một phần](docs/evidence/2026-11-03-android/README.md).
Ca mine có dữ liệu, đổi mật khẩu thành công và nghiệm thu native toàn diện vẫn chưa đóng;
iOS chưa nghiệm thu.

Đợt Review3 ngày 22/10/2026 bổ sung thao tác lịch hẹn, tiếp nhận, hồ sơ khám,
chẩn đoán, nguồn chỉ định Lab, chi tiết và lệnh xét nghiệm, truy vấn báo cáo,
chọn tham chiếu bằng tên theo quyền, cùng liên kết phiên cho thông báo cá nhân.
Clinical command UI dùng cờ build `MEDIFLOW_OUTPATIENT_UI_ENABLED=true`; cờ này
không thay đổi quyền hoặc kích hoạt backend. Pharmacy vẫn cần mode xác nhận riêng.
Các trường không được cấp quyền tra cứu hiển thị thông báo rõ ràng; không suy tên
hoặc mã nhân viên/bệnh nhân từ JWT subject. Mã tham chiếu cần cho đối soát được
giữ trong phần kỹ thuật mở rộng khi phù hợp.

Ảnh Review3 được chụp từ Flutter web ở khung mobile qua Gateway thật, sử dụng
dữ liệu demo trong backend. Đây không phải bằng chứng đã chạy Android/iOS native.
Các giới hạn thực tế về Core reference permissions, Lab clearance và Report
coverage nằm trong [runtime handoff](../docs/handoffs/HANDOFF-REVIEW3-RUNTIME-2026-10-22.md).

Có đăng nhập thật, đăng xuất, lưu phiên, menu/route theo role. Lịch hẹn đọc `/v1/appointments`, lọc ngày/phân trang; Lab đọc `/v1/lab`, lọc trạng thái/phân trang. Có loading/empty/error/retry, correlation ID và bỏ qua phản hồi cũ. Lab hiển thị trạng thái nghiệp vụ riêng với thanh toán.

Billing follows the web task set by role: `ADMIN`/`CASHIER` can use the collection queue,
optional exam-fee collection, payment history and refunds; `ADMIN` sees revenue reports; an appointed department head opens the department report from department management, locked to their department.
The exam-fee V2 client is disabled by default; `tool/run_web.ps1 -Demo`, the demo launcher command and the iOS workflow enable it. Dart defines are compile-time, so a build without them cannot show the exam-fee tab. Enable its mobile UI with
`--dart-define=MEDIFLOW_SCHEDULING_UI_ENABLED=true`; optional quotes use
`--dart-define=MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true`, and zero-liability confirmation
also requires `--dart-define=MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true`. These UI defines do not
enable backend routes or grant access: Billing must enable `mediflow.billing.ledger.enabled`
and `mediflow.billing.scheduled-payments.enabled`; quotes also require
`mediflow.billing.outpatient-quotes.enabled`, and zero-liability confirmation requires
`mediflow.billing.zero-payable-eligibility.enabled`. The cashier amount always comes from
Billing, and uncertain commands keep their idempotency key in secure storage until reconciled.

Mọi vai trò đã đăng nhập đều có thể mở hồ sơ cá nhân tại `/profile`. Nhân sự chỉ cập nhật
số điện thoại và địa chỉ của chính mình; email đăng nhập chỉ do quản trị viên quản lý.
Bệnh nhân xem hồ sơ của mình ở chế độ chỉ đọc. Màn hình này không thay thế chức năng
quản lý tài khoản dành cho quản trị viên.

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

Phân công hiện hành (11/2026) giữa Hoàng Anh và Huy: [mobile work split](docs/mobile-work-split-2026-11.md).

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

Kiểm tra LAB/Report sau chỉnh giờ 04/11/2026: [UI, biểu đồ và kết quả nghiệm thu](../docs/acceptance/2026-11-04-lab-report/README.md).
