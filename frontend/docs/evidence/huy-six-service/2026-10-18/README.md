# Evidence FE Huy — 2026-10-18

Candidate: `025ef39e` cộng working diff của Pharmacy và Support (Report/Notification).
Đây là evidence của checkpoint B10 trong [plan FE Huy](../../../huy-six-service-frontend-plan.md),
trước khi đồng bộ `master` ngày 2026-10-19.

Browser dùng HTTP fixtures ở môi trường local cô lập. Các ảnh và matrix không thay thế
signed-role browser/Gateway acceptance, dữ liệu backend thật hoặc release approval.

- [Matrix chín trường hợp](ui-matrix.json): Sáng/Tối/Theo hệ thống tại 320, 768 và 1280px
  cho kê đơn, Report ngoại trú và chi tiết Notification.
- Pharmacy: [chi tiết thuốc mobile](pharmacy-drug-mobile-system.png),
  [thêm thuốc desktop](pharmacy-create-desktop-dark.png),
  [frame desktop](pharmacy-frame-desktop-dark.png),
  [chi tiết đơn thuốc desktop](pharmacy-prescription-desktop-light.png).
- Report: [báo cáo tháng tablet](report-monthly-tablet-light.png),
  [ngoại trú tablet](report-outpatient-tablet-light.png),
  [ngoại trú mobile](report-outpatient-mobile-dark.png),
  [unavailable mobile](report-unavailable-mobile-dark.png).
- Notification: [chi tiết mobile](notification-detail-mobile-system.png).

B10: full suite 501/501 trong 55 file; typecheck/lint/build PASS.
B20: full suite 506/506 trong 55 file; typecheck/lint/build PASS trên candidate trước đồng bộ.
Chưa chạy lại browser cho B20 hoặc candidate sau đồng bộ; không dùng các ảnh B10 để khẳng định
đã nghiệm thu các thay đổi mới hơn. Keyboard matrix và signed workflow còn mở theo plan.
