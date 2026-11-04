# Android emulator acceptance — 03/11/2026

Candidate: working tree MOB-02/MOB-03 trên baseline `2e3fa734`. Owner: Vinh.
Người dùng yêu cầu chuyển nghiệm thu sang emulator Android Studio; dùng AVD Pixel_7 có sẵn.

## Đã xác nhận

- Khởi động Pixel_7, Android 16 / API 36, x86_64, device `emulator-5554`.
- `flutter run -d emulator-5554 --dart-define=GATEWAY_URL=http://10.0.2.2:28080/api`.
- Gradle `assembleDebug` PASS, 239.5 giây. APK 84,124,352 bytes, cài thành công.
- Flutter đồng bộ ứng dụng và mở VM service; Android trả process ID 4220 và
  `topResumedActivity=com.mediflow.mediflow_mobile/.MainActivity` lúc kiểm tra.
- Gateway rehearsal của đợt web vẫn đang listen tại port 28080 trên máy chủ.
  Login PATIENT/ADMIN và các màn đọc dữ liệu đã thành công từ native qua Gateway.
- Định danh và SHA256 APK ở [environment.json](environment.json).

Toolchain sử dụng Android Studio JBR 25, SDK có sẵn tại LocalAppData/Android/Sdk.
Gradle bổ sung Platform 35 và CMake 3.22.1 dựa trên license đã được chấp nhận trên máy.
`flutter doctor` vẫn báo thiếu cmdline-tools/license status unknown; build/install thực tế
vẫn thành công. Không nâng Flutter hoặc thay Gradle/project source để làm xanh build.
Đây là debug APK, chưa có release signing/acceptance. Pharmacy mode mặc định unavailable;
không bật command gates ngoài cấu hình build mặc định.

## Khôi phục quan sát giao diện

Computer Use nhận diện được cửa sổ emulator và toolbar nhưng capture trả
`encode latest capture frame failed: window crop is outside captured monitor`.
Đã chọn lại cửa sổ và khởi động lại emulator với scale 0.3, cold boot; capture vẫn lỗi.
Người dùng đã kéo cửa sổ emulator vào màn hình; sau đó capture và input đã hoạt động.
Ảnh PNG trong thư mục này được chụp từ cửa sổ Android Emulator, không lấy từ web.
Toolbar emulator là screenshot region riêng, không ghép vào ảnh màn hình ứng dụng.

## Kết quả giao diện native

| Ca | Kết quả | Ảnh |
|---|---|---|
| Login bỏ trống | Hiện lỗi bắt buộc cho hai trường | login-required.png |
| Login PATIENT | Thành công qua Gateway, workspace đúng role | patient-home.png |
| Menu PATIENT | Chỉ tổng quan, kết quả khám, kết quả Lab, hỗ trợ | patient-menu.png |
| Mine khám | Empty, 0 kết quả, trang 1/1 | patient-exams-empty.png |
| Mine Lab | Empty, 0 kết quả, trang 1/1 | patient-lab-empty.png |
| Khởi chạy lại cùng APK | Khôi phục phiên PATIENT, không yêu cầu login | patient-session-restored.png |
| Logout PATIENT | Form login trống | logout.png |
| Mật khẩu sai | Lỗi xác thực backend và mã hỗ trợ | invalid-login.png |
| Login ADMIN | Workspace staff, không giữ menu PATIENT | admin-home.png |
| Organization | Tải khoa thật | organization.png |
| Patient | Tải danh sách, đổi trang 1 → 2 thành công | patients.png, patients-page2.png |
| Appointment | Tải 14 lịch và nhãn tham chiếu | appointments.png |
| Lab staff | Tải 8 requests và mở chi tiết CBC COMPLETED | lab-list.png, lab-detail.png |
| Lab detail | Kết luận demo, clearance đã xác nhận, DEMO_INDEX 7.2 demo | lab-detail.png |
| Pharmacy | Danh mục 10 thuốc, tồn kho/giá/cảnh báo | pharmacy.png |
| Billing | Mở form và các tab; chưa thu/hoàn tiền | billing-form.png |
| Report | GET báo cáo ngày 2026-11-03 trả các giá trị 0 | report-daily.png |
| Login lần đầu | Bắt buộc đổi mật khẩu | mandatory-password-change.png |
| Điều hướng trước đổi mật khẩu | Chọn kết quả khám trong menu vẫn quay về form đổi mật khẩu | mandatory-password-navigation-blocked.png |

Khôi phục phiên được kiểm bằng `flutter run --no-resident --use-application-binary=...`
với cùng APK, cài đè và chạy lại; process đổi từ 4220 sang 6219. Không phải reboot Android,
không chứng minh expiry/refresh/revocation. Menu theo role chỉ xác nhận UX, chưa kiểm
native deep-link denial hay thay thế kiểm RBAC backend. Báo cáo ngày 0 chỉ xác nhận đọc/hiển thị,
không chứng minh phép tổng hợp ở ngày có phát sinh. Mọi dữ liệu là dữ liệu tổng hợp trong rehearsal.

## Chưa nghiệm thu

- PATIENT mine có dữ liệu, đổi tài khoản giữa hai bệnh nhân có dữ liệu khác nhau.
- Bàn phím ảo đầy đủ, accessibility, loading lâu, offline, expiry/401/revocation thực tế.
- Native deep links và RBAC của các lệnh staff theo từng role.
- Notification, hồ sơ khám staff, mutation và workflow ngoại trú xuyên suốt native.
- Đổi mật khẩu thành công: người dùng cho phép thao tác nhưng Computer Use vẫn yêu cầu
  người dùng tự nhập/xác nhận/gửi credential mới. Agent chưa thực hiện bước này.
- Thiết bị thật, iOS, release/signing. Không suy 100% mobile từ build/install thành công.

Emulator đang để ở form bắt buộc đổi mật khẩu của tài khoản fixture có kết quả hoàn tất.
Sau khi người dùng tự đổi và đăng nhập lại: kiểm mine khám/Lab có dữ liệu và so fixture,
logout/chuyển tài khoản không lộ dữ liệu; tiếp tục các ca chưa nghiệm thu ở trên.
