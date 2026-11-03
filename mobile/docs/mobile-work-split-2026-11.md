# Phân công mobile — Hoàng Anh & Huy (11/2026)

Lập ngày 2026-11-03 sau khi review `mobile/` so với backend/web hiện tại.
Nguyên tắc chia: mỗi người nhận client của context mình sở hữu ở backend —
**Hoàng Anh: Auth / Tổ chức / Bệnh nhân (Core, Gateway)**,
**Huy: Dược / Báo cáo / Thu ngân / Lịch**.

## Quy ước chung

- File dùng chung (`lib/app/dependencies.dart`, `router.dart`, `modules.dart`,
  `lib/core/`, `pubspec.yaml`/lock) do **Hoàng Anh** điều phối, vì
  auth/session nằm trong đó. Huy cần nối route, bind adapter hoặc thêm quyền
  thì báo Hoàng Anh hoặc mở PR nhỏ riêng cho phần dùng chung.
- Mỗi người chỉ sửa trong `lib/features/<feature của mình>`; test đặt tên theo
  feature để tránh conflict.
- Trước khi merge: chạy `tool/check.ps1`, thử qua Gateway thật bằng đúng role,
  ghi kết quả vào `mobile/docs/evidence/<tên>/`. Test giả lập không thay cho
  nghiệm thu thật.

## Hoàng Anh — Auth, Tổ chức, Bệnh nhân, nền tảng

### Giai đoạn 1 — gỡ lỗi chặn demo (làm trước)

| ID | Việc | Ghi chú |
|---|---|---|
| HA-01 | Đổi mật khẩu lần đầu | Đọc `mustChangePassword` trong `SessionDto`; router ép vào màn đổi mật khẩu (`PUT /v1/org/accounts/me/password`); bắt 403 `AUTH_PASSWORD_CHANGE_REQUIRED` và chuyển sang màn này. Hiện tài khoản mới đăng nhập mobile sẽ bị 403 ở mọi màn. |
| HA-02 | Cờ build cho demo | Thêm `MEDIFLOW_OUTPATIENT_UI_ENABLED=true` và `PHARMACY_MODE=outpatient` vào lệnh demo trong README và `tool/run_web.ps1` (cho phép truyền tham số). Huy cần trước HY-01. |

### Giai đoạn 2

| ID | Việc | Ghi chú |
|---|---|---|
| HA-03 | Refresh token | Gọi `/v1/auth/refresh` khi access token hết hạn; chỉ tự gửi lại request đọc, không tự gửi lại lệnh thanh toán/tạo mới. |
| HA-04 | Quên / đặt lại mật khẩu | `/v1/auth/forgot-password`, `/v1/auth/reset-password` từ màn đăng nhập. |
| HA-05 | Điều dưỡng cấp tài khoản bệnh nhân | `/v1/org/patient-accounts`, theo handoff H-11. |
| HA-06 | Trang của bệnh nhân | `records/mine`, `lab/mine` cho role PATIENT (hiện chỉ thấy Thông báo). |

### Giai đoạn 3

| ID | Việc | Ghi chú |
|---|---|---|
| HA-07 | Hồ sơ cá nhân, danh bạ | `/v1/org/me/capabilities` để hiện menu đúng quyền; hồ sơ cá nhân, đổi email. |
| HA-08 | Nghiệm thu Organization/Patient qua Gateway thật | Đóng handoff H-01, H-11. |
| HA-09 | Build Android native, dọn dẹp | Cập nhật hướng dẫn chạy máy thật; xóa thư mục chỉ có `.gitkeep`. |

## Huy — Dược, Báo cáo, Thu ngân, Lịch

### Giai đoạn 1

| ID | Việc | Ghi chú |
|---|---|---|
| HY-01 | Nghiệm thu Pharmacy Task 12 (MP-HUY-12) | Qua Gateway thật: kê đơn → thu tiền → xuất thuốc; kho chỉ bị trừ một lần; chặn xuất khi chưa thanh toán. Cần HA-02. Đóng H-05, H-06. |
| HY-02 | Báo cáo ngoại trú | Kiểm tra màn Báo cáo; bổ sung `/v1/reports/outpatient/daily|monthly` nếu còn thiếu. |

### Giai đoạn 2

| ID | Việc | Ghi chú |
|---|---|---|
| HY-03 | Hàng đợi thu ngân | `/v1/billing/payment-requests/queue` — chọn khoản cần thu từ danh sách thay vì nhập mã (như PR #563 trên web). |
| HY-04 | Điều chỉnh viện phí Lab | `/v1/billing/lab-adjustments`, đặt cạnh màn hoàn tiền đơn thuốc. |
| HY-05 | Nghiệm thu Billing/Notification qua Gateway thật | Hiện mới có test giả lập. |

### Giai đoạn 3

| ID | Việc | Ghi chú |
|---|---|---|
| HY-06 | Lịch cá nhân và ca trực | Màn `my-calendar`, `my-shifts` cho bác sĩ/điều dưỡng, chỉ đọc. |
| HY-07 | Build iOS | Cần máy Mac; nếu Huy không có thì người có Mac nhận. |

## Phụ thuộc giữa hai người

- HA-02 phải xong trước HY-01.
- Sau khi merge HA-01/HA-03 (thay đổi phiên), Huy chạy lại test Dược và Thu ngân
  để xác nhận reset phiên vẫn đúng.
- HY-03 và HA-06 đều thêm mục menu trong `modules.dart` — gộp chung một PR.

Hoàng Anh nhiều việc hơn ở giai đoạn 1–2 vì lỗi chặn nằm ở auth; Huy bù bằng
hai đợt nghiệm thu thật và màn lịch.
