# Hoàn thiện toàn bộ mobile — 03/11/2026

## Cập nhật 04/11/2026

Đã fast-forward lên `1ce0968e` và giữ local work 03/11. Mobile cập nhật email login,
public forgot/reset-password, tạo tài khoản không nhập mật khẩu tạm và Lab cancel lấy actor
ở backend. Sửa SafeArea/error/reference-dialog/long-result layout. **376 tests**, analyzer,
web build và Android debug APK PASS. Browser visual 320px và landscape PASS.
Không có Gateway/device kết nối trong phiên này; runtime acceptance mới chưa đóng.
[Source lock, checks và screenshots](evidence/2026-11-04-ui/README.md).
Các khoảng trống MOB-04–10 trong bảng dưới vẫn còn; cập nhật này không hoàn tất toàn bộ V2.

Owner: **Vinh** cho toàn bộ `mobile/`, theo chỉ định trực tiếp của người dùng từ leader.
Baseline Git sạch tại `2e3fa734` (PR #563). Đây là bảng điều phối triển khai, không thay
business design hoặc hợp đồng backend. Phạm vi là toàn bộ mobile của MVP ngoại trú hiện hành;
Inpatient/Surgery vẫn deferred theo `docs/README.md`, không bật runtime từ yêu cầu này.

## Kết quả rà soát ban đầu

Mobile không bắt đầu từ số 0: có auth/SecureStorage, session theo Gateway, shell/router theo
role và code cho 8 active contexts. Cờ `implemented` trong menu chỉ nói có màn hình; không
chứng minh đầy đủ nghiệp vụ. Nhiều màn hình vẫn dựa trên V1 trước các contract cuối tháng 10.
`ApiClient` hiện chỉ nhận `/v1/`: cần mở hỗ trợ V2 có kiểm soát trước khi nối scheduling.
Không có lời gọi V2 trong mobile production ở baseline.

| Context | Source hiện có | Công việc còn thiếu/chưa nghiệm thu |
|---|---|---|
| Auth/shared | Login/logout, JWT SecureStorage, 401 theo token, shell/role | Bổ sung đổi mật khẩu; kiểm phiên hết hạn, offline, khôi phục và điều hướng trên thiết bị |
| Organization | Khoa/nhân viên, chuyển khoa, tài khoản/status | Cấp tài khoản PATIENT tại quầy bằng idempotency; capabilities, trưởng khoa, phòng khám theo live authority |
| Patient | Tìm, chi tiết, tạo/sửa/xóa theo DTO Core | Nối onboarding NURSE → cấp tài khoản; đối chiếu validation/BHYT mô phỏng hiện hành, nghiệm thu Gateway |
| Clinical | Lịch hẹn V1, tiếp nhận, hồ sơ, chẩn đoán/kết luận/chỉ định Lab | Lịch V2, ca bác sĩ, session/capacity, đăng ký/giữ suất, thu phí khám, có mặt/bắt đầu khám, tái khám, patient mine |
| Lab | Hàng đợi, chi tiết, bắt đầu/trả kết quả/hủy | Patient mine; đối chiếu current clearance/lifecycle và pending Lab khi hoàn tất lượt khám; nghiệm thu thật |
| Pharmacy | Danh mục/tồn kho, kê đơn qua source context, hủy/cấp thuốc | Đối chiếu contract hiện hành và chạy lại đủ luồng DOCTOR/PHARMACIST, unpaid/paid/zero/replay; giữ mode gate |
| Billing | Hóa đơn V1, discovery theo source, payment/refund ledger, doanh thu | Hàng khoản thu Lab/Rx, queue phí khám V2, quote hỗ trợ/zero payable, uncertain payment và reconciliation |
| Report | Daily/monthly/top medicines, outpatient daily/monthly | Đối chiếu DTO hiện hành, scope khoa và gross/support/payable/refund; dữ liệu chưa tới khác 0 |
| Notification | Danh sách/chi tiết, patient scope, send/replay theo role | Nối thông báo kết quả đến trang patient mine; sự kiện lịch/xác nhận/tái khám và bảo mật phiên |
| Platform | Android/iOS/Windows hosts, web proxy và check script | Android SDK/licenses, build APK và chạy máy/emulator; iOS trên macOS; signing/release nếu thuộc bàn giao |

Đây là rà soát source ban đầu, không phải audit đầy đủ từng endpoint. Mỗi task tiếp theo phải
khóa controller/DTO/serializer/security/fixtures đang chạy trước khi thêm client, kể cả khi
thiết kế có contract TARGET. Không tự đổi backend flags, đoán UUID/giá hay truy cập service port.

## Thứ tự triển khai và acceptance

| ID | Công việc | Trạng thái |
|---|---|---|
| MOB-01 | Nhận ownership toàn mobile, baseline checks và inventory | Đã ghi nhận; chi tiết bằng chứng bên dưới |
| MOB-02 | Đổi mật khẩu bản thân, mandatory redirect, revoke-session UX | Web và Android mandatory redirect PASS; chờ người dùng đổi mật khẩu và nghiệm thu phiên sau đổi |
| MOB-03 | PATIENT xem kết quả khám và xét nghiệm hoàn tất qua mine | Web/Android empty, menu theo role và restore PASS qua Gateway; ca có dữ liệu chờ bước đổi mật khẩu của fixture |
| MOB-04 | NURSE tạo hồ sơ/cấp tài khoản idempotent, giao credential đúng người | Chưa triển khai luồng onboarding mới |
| MOB-05 | Shared V2 transport + capability/department authority + reference labels | Chưa triển khai |
| MOB-06 | Lịch bác sĩ/ca/buổi khám và quản lý khoa theo capability | Chưa triển khai |
| MOB-07 | Đăng ký khám/tái khám, giữ suất, hàng chờ, arrival | Chưa triển khai V2 |
| MOB-08 | Thu phí khám/Lab/thuốc theo queue, quote/zero/refund/recovery | Có V1 từng phần; thiếu queue và scheduled V2 |
| MOB-09 | Hồ sơ khám/chỉ định/Lab/kê đơn/cấp thuốc theo luồng mới | Có code từng phần; cần đối chiếu/hoàn thiện và test liên thông |
| MOB-10 | Report/Notification theo flow mới và patient navigation | Có code từng phần; cần đối chiếu/hoàn thiện |
| MOB-11 | Kiểm thử role, lỗi mạng, phiên, accessibility và màn hình nhỏ toàn app | Có regression nền và web/Android smoke một phần; còn offline/expiry/accessibility/tổng thể |
| MOB-12 | Build/chạy Android, iOS, cấu hình phát hành và hướng dẫn bàn giao | Android debug build/install/launch và UI smoke một phần PASS trên Pixel_7; iOS/release chưa nghiệm thu |

Mỗi luồng chỉ đóng khi có: đúng wire qua Gateway; quyền và ownership do backend kiểm;
loading/empty/error/retry/permission; bỏ response cũ khi đổi session/filter; không tự replay
mutation chưa rõ kết quả; contract/use-case/widget tests phù hợp; smoke thật đúng role;
bằng chứng trên nền tảng bàn giao. Không quy đổi số tests hay số thư mục thành % hoàn thành.

## Source lock MOB-02–03

Paths dưới đây tính từ root repository; chỉ đọc producer, không sửa backend trong đợt này.

| Method/path | Producer wire source | Consumer |
|---|---|---|
| PUT `/api/v1/org/accounts/me/password` | Core `AccountManagementController`, `ChangePasswordRequest`: `{currentPassword,newPassword}` → envelope boolean true; 8 human roles | Auth password repository/use case/page; đăng xuất sau xác nhận thành công, không auto replay khi mất phản hồi |
| Access JWT `mustChangePassword` | Gateway `AuthController`, `JwtTokenService`, `AccountSessionValidationFilter` | `Session.mustChangePassword`, router `/account/password`; chỉ là UX hint, Gateway giữ quyền quyết định |
| GET `/api/v1/records/mine?page=&size=` | Clinical `PatientResultsController`, `PatientExamResultDTO`, `DiagnosisDTO`, `RecordDisposition` | Medical record patient summary riêng; không parse bằng staff DTO |
| GET `/api/v1/lab/mine?page=&size=` | Lab `PatientResultsController`, `PatientLabResultDTO`, `LabResultDTO` | Lab patient summary riêng; textual value giữ nguyên |

Cả hai mine routes chỉ PATIENT; query chỉ page/size. Server xác định patient từ verified
principal. Client không yêu cầu patientId trong token để tự tạo scope và không tra danh sách
staff/patient. Phân trang dùng `PageResult`, invalid response không biến thành empty. Domain
pure Dart; adapter bind tại `app/dependencies.dart`; widget chỉ dùng domain use case.
Session identity đổi sẽ hủy cây hiển thị cũ, bỏ phản hồi cũ. 401/403 không có nút retry.

## Bằng chứng và giới hạn môi trường

- Baseline check trước thay đổi: format 137 files, analyzer không lỗi; suite 355 tests passed.
  Build web được khởi chạy trong lúc triển khai, không coi là snapshot baseline bất biến.
- Flutter doctor: Flutter 3.47.0, Dart 3.13.0. Có Chrome/Edge/Windows targets.
- Android SDK 36 có sẵn nhưng doctor báo thiếu cmdline-tools và license status unknown.
  Đợt sau đã khởi động Pixel_7 Android 16, build/install/launch debug PASS với license có sẵn;
  [Android evidence](evidence/2026-11-03-android/README.md) ghi UI smoke PASS một phần sau
  khi người dùng di chuyển cửa sổ để khôi phục capture.
  Windows thiếu C++ workload. iOS cần macOS/Xcode.
- Các kiểm tra tự động dùng test doubles. Đã chạy thêm login/mine empty/mandatory redirect,
  khôi phục phiên và staff read qua Gateway thật trên web và Android Pixel_7.
  Đổi mật khẩu thành công và mine có dữ liệu còn chờ bước người dùng. iOS chưa nghiệm thu.
  Không ghi password/token vào evidence.
- Check cuối đợt `mobile/tool/check.ps1`: PASS, exit 0. Format 148 files (0 changed),
  analyzer không lỗi, toàn bộ **369 tests passed**, build web PASS (96.6 giây).
  Suite bao gồm 14 tests mới về exact wire, malformed payload, phân trang, role/deep link,
  composition, session switch, mandatory password change và uncertain mutation; không cộng
  lượt focused vào số test toàn suite. Assertion owner cũ trong `clinical_models_test.dart`
  được đổi từ Vinh/Huy sang Vinh theo chỉ định mới; các assertion quyền vẫn giữ nguyên.
- Web build còn cảnh báo font CupertinoIcons đã xuất hiện ở baseline; build vẫn thành công.
  Log local ở `mobile/docs/mobile-check-2026-11-03.log` (ignored, không chứa credentials).
- Probe ban đầu tại port 8080 không truy cập được. Sau đó đã build và chạy rehearsal riêng
  trên cùng máy tại Gateway 28080, UI 4300; Gateway smoke PASS và các ca UI một phần PASS.
  Kết quả, screenshots, giới hạn và bước handoff ở
  [web acceptance](evidence/2026-11-03-web/README.md). Không coi smoke là hoàn thành toàn bộ mobile.
