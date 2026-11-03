# Mobile sync và UI — 04/11/2026

Candidate: working tree trên `master` **1ce0968e** (PR #564, #565), cộng phần mobile
MOB-02/MOB-03 chưa commit được giữ từ phiên trước. Không coi đây là nghiệm thu toàn bộ mobile.

## Cập nhật Git

- Fetch và fast-forward từ `2e3fa734` lên `1ce0968e` (5 commits).
- Local changes và untracked files được stash với tên `mobile-before-master-sync-2026-11-04`,
  sau đó apply thành công, không conflict. Giữ stash dự phòng; không commit/push.

## Source lock và thay đổi

| Contract | Producer đã đối chiếu | Mobile |
|---|---|---|
| Login username là email, tối đa 254 ký tự | Gateway AuthDtos và login UI mới trên master | Bỏ giới hạn 50 ký tự cũ; giữ wire key username |
| POST `/api/v1/auth/forgot-password` `{email}` → `{accepted:true}` | Gateway AccountSecurityProxyController; Core SecurityAccountOperationsService | Quên mật khẩu, thông báo trung tính, không gửi bearer |
| POST `/api/v1/auth/reset-password` `{token,newPassword}` → `{success:true}` | Cùng producer; token tối đa 256, password 8–72 | Nhận query token hoặc liên kết/mã được dán, xác nhận mật khẩu; không tự replay |
| POST `/api/v1/org/accounts` | Core CreateAccountRequest, AccountResponse | Email tối đa 254; bỏ password trong request; backend tạo và gửi mật khẩu tạm; không yêu cầu thay active account |
| PUT `/api/v1/lab/{id}/cancel` | Lab CancelLabTestRequest, service/controller từ PR #564 | Chỉ gửi reason (1–1000); actor lấy từ signed session tại backend; bỏ điều kiện staffId phía UI |

UI: SafeArea ở shell; title đổi mật khẩu rõ ràng; error state cuộn trong vùng thấp;
reference dialog cuộn khi bàn phím chiếm chỗ, pagination xuống dòng; kết quả Lab dài
nằm trong nội dung thay vì trailing. Giữ role guards, session boundaries, Gateway-only và flags.

## Kiểm tra

- Format `lib test tool`: PASS, 153 files, 0 changed ở lần kiểm tra cuối.
- `flutter analyze`: PASS, no issues.
- `flutter test test packages/mediflow_loader/test`: **376 passed**, không skip/fail.
- Regression mới: email dài, exact public recovery wire/no bearer, malformed payload,
  validation, route signed-out, duplicate-submit, confirm-password, 320px/large text,
  keyboard/landscape và reference picker.
- Web build với `GATEWAY_URL=http://localhost:4300/api`: PASS.
- Android debug APK với `GATEWAY_URL=http://10.0.2.2:8080/api`: PASS.
- APK: `mobile/build/app/outputs/flutter-apk/app-debug.apk`, 197372777 bytes.
- SHA256: `8EFC0818A37CC7A4116A15055B6EEB198068245D50DC565E0B346C13078279F4`.
- Web còn cảnh báo CupertinoIcons từ baseline; Gradle có cảnh báo native access; cả hai build thành công.
- Logs local ignored trong mobile/docs: mobile-check, mobile-analyze, mobile-build-web,
  mobile-build-android với hậu tố `-2026-11-04.log`.

## Kiểm tra giao diện trên browser

Bản Flutter web chạy tại localhost:4300, không đưa mock backend vào ứng dụng.

- [Login 320×568](login-320.jpg): Email/password/buttons hiển thị đủ.
- [Forgot password](forgot-320.jpg): điều hướng từ login hoạt động.
- [Validation](forgot-validation-320.jpg): email trống báo lỗi, không gửi request.
- [Reset password](reset-320.jpg): đủ trường liên kết/token, password và xác nhận.
- [Landscape 568×320](reset-landscape.jpg): cuộn tới được các nút cuối form.

Không nhập hay lưu credentials/token thật trong screenshots.

## Giới hạn nghiệm thu

Không có Gateway lắng nghe tại 8080/28080 và `adb devices` không có thiết bị khi kiểm tra.
Chưa nghiệm thu gửi email thật, login/reset/create/cancel qua backend mới hoặc install/launch
Android. iOS chưa build trên Windows. APK dùng địa chỉ host dành cho emulator; điện thoại
thật cần build lại với Gateway truy cập được. Native build và tests dùng doubles không thay
acceptance qua Gateway.

Scheduling/đăng ký khám/queue Billing V2 vẫn ở workboard; không đóng bằng thay đổi auth/Lab/UI này.
