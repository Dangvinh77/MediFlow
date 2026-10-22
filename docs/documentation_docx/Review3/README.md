# Review 3 — bộ dữ liệu và ảnh minh chứng

1. **Báo cáo:** [Review3.docx](Review3.docx), mục **Screen shots**, trang hiển thị 89–167 trong bản render PR đã kiểm tra (201 trang toàn bộ báo cáo). Có 79 ảnh: 51 web, 28 mobile; chia 18 nhóm, chú thích theo chỉ mục. Không thêm bảng mới; các bảng có sẵn của tài liệu được giữ nguyên.
2. **Nguồn ảnh:** chụp trực tiếp hệ thống đang chạy với Gateway, các ứng dụng nghiệp vụ, PostgreSQL và RabbitMQ. Thông tin bệnh nhân/nhân viên là dữ liệu demo tổng hợp, không phải hồ sơ người bệnh thật. Ảnh mobile là **Flutter web, viewport 390 × 844**, chưa phải bản Android/iOS native.
3. **Dữ liệu demo:** 8 tài khoản theo role, 4 khoa/phòng, 7 nhân viên, 12 bệnh nhân, 12 lịch hẹn, 6 thuốc, 4 hồ sơ khám, 5 yêu cầu xét nghiệm và 4 đơn thuốc. Sau thao tác: 4 lịch hẹn IN_EXAM, 3 READY_FOR_EXAM, 5 PENDING; 2 đơn thuốc FULFILLED, 2 ACTIVE. Ngoài bộ demo còn có dữ liệu kiểm tra ban đầu của môi trường acceptance.
4. **Tương tác đã thực hiện:** đăng nhập đủ 8 role; tìm kiếm/xem chi tiết; check-in; tạo chỉ định xét nghiệm; nhập thêm tồn thuốc; cấp thuốc; ghi nhận khoản thu trên web và Flutter; xem nhắc hẹn của bệnh nhân. Các thao tác thay đổi trạng thái đi qua API, không sửa cờ thanh toán hay kết quả xét nghiệm trực tiếp trong database.
5. **Mở tại máy hiện tại:** web [localhost:3000](http://localhost:3000), mobile [localhost:4300](http://localhost:4300), Gateway [127.0.0.1:58910](http://127.0.0.1:58910). Tài khoản/mật khẩu nằm trong [demo-runtime/accounts.txt](demo-runtime/accounts.txt), chỉ lưu cục bộ và bị Git bỏ qua.
6. **Database được giữ lại:** hai container `mediflow-review3-postgres` và `mediflow-review3-rabbitmq`; named volumes `mediflow-review3-pgdata` và `mediflow-review3-rabbitdata`. PostgreSQL ở cổng 15432, RabbitMQ ở 15673. Bản dump và manifest seed nằm trong thư mục riêng `demo-runtime/`, bị Git bỏ qua. Giữ nguyên Docker volumes để tái sử dụng dữ liệu. Không chạy seed lần nữa trên bộ hiện tại.
7. **Mở lại backend sau khi khởi động máy:** tại thư mục gốc repository chạy `python docs/documentation_docx/Review3/runtime_review3.py start`; chờ các ứng dụng đăng ký rồi đăng nhập. `status` xem tiến trình; `stop` dừng đúng các tiến trình Java demo; `backup` cập nhật `demo-runtime/seeded.sql`. Manifest khởi chạy là bản dành riêng cho máy hiện tại và cần các JAR đã build. Helper dùng credential demo cục bộ, không dành cho triển khai production.
8. **Mở lại web:** trong PowerShell tại `frontend/`, đặt `$env:GATEWAY_URL='http://127.0.0.1:58910'`, `$env:MEDIFLOW_OUTPATIENT_UI_ENABLED='true'`, `$env:MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED='true'`, rồi chạy `pnpm dev --port 3000`.
9. **Mở lại Flutter web:** bản build hiện tại dùng `GATEWAY_URL=http://localhost:4300/api`. Tại `mobile/`, chạy `dart run tool/dev_server.dart 4300 http://127.0.0.1:58910`. Nếu cần build lại: `flutter build web --dart-define=GATEWAY_URL=http://localhost:4300/api`.
10. **Giới hạn được thể hiện trung thực:** Lab trả `LAB_CLEARANCE_REQUIRED` khi bắt đầu một yêu cầu đã READY và đã thanh toán; chưa có ảnh kết quả xét nghiệm hoàn tất. Báo cáo outpatient trả `REPORT_NOT_FOUND`; số liệu báo cáo legacy không thay thế dữ liệu ledger V1. Một số workspace mobile còn placeholder hoặc unavailable. Trường legacy paid của Lab mobile có thể khác clearance hiện hành. Chú thích cụ thể nằm trong báo cáo và captures.json.
11. **Kiểm chứng:** đã index lại code mới; build backend và Flutter web thành công; 245 test Lab đạt sau khi sửa lỗi chọn CircuitBreaker khi khởi động. Test setup nhân viên đã bổ sung identityNumber theo hợp đồng mới. Đăng nhập lại cả 8 role và đọc lại trạng thái nghiệp vụ qua Gateway sau khi chuyển database. Chi tiết cục bộ: `demo-runtime/verification.json`.
12. **Tái tạo báo cáo:** `insert_screenshots.py` dùng bản gốc `.git/review3-original.docx` đã lưu của phiên này và `screenshots/captures.json`. Giữ bản gốc trước khi chạy lại; script không nối chồng ảnh lên bản đã chèn. `seed_review3.py` dành cho môi trường acceptance mới, có chốt tránh seed trùng theo manifest. Đường đi Lab bị chặn được ghi nhận thay vì tạo kết quả giả.

## Danh mục ảnh theo chỉ mục

1. **Shared web screens**

   1.1. [Landing page and outpatient service groups](screenshots/web/01-shared-home.jpg)

   1.2. [Login form with account and password validation](screenshots/web/02-shared-login.jpg)

   1.3. [Access denied when a patient opens the organization page](screenshots/web/51-common-access-denied.jpg)

2. **Administrator — web**

   2.1. [Patient directory with seeded Vietnamese patient records](screenshots/web/03-admin-patients.jpg)

   2.2. [Department and staff directory](screenshots/web/04-admin-organization.jpg)

   2.3. [Eight seeded role accounts and account creation controls](screenshots/web/05-admin-accounts.jpg)

   2.4. [Appointment queue with pending, payment pending and in-progress visits](screenshots/web/06-admin-appointments.jpg)

   2.5. [Appointment creation form and required patient, doctor and department references](screenshots/web/07-admin-appointment-form.jpg)

3. **Manager — web**

   3.1. [Monthly report filter and returned legacy projection](screenshots/web/09-manager-monthly.jpg) — This legacy monthly projection shows zero values; outpatient V1 evidence is captured separately.

   3.2. [Outpatient daily report and missing reconciliation coverage notice](screenshots/web/10-manager-outpatient-report.jpg) — REPORT_NOT_FOUND is the observed response for this date range; no reconciled report was fabricated.

   3.3. [Manager view of departments and staff](screenshots/web/11-manager-organization.jpg)

   3.4. [Manager appointment monitoring without clinical write actions](screenshots/web/12-manager-appointments.jpg)

   3.5. [Manager laboratory queue and status filters](screenshots/web/13-manager-lab.jpg)

4. **Doctor — web**

   4.1. [Doctor appointment queue and role-specific navigation](screenshots/web/14-doctor-appointments.jpg)

   4.2. [Visit timeline after check-in, payment clearance and start of examination](screenshots/web/15-doctor-visit.jpg)

   4.3. [Clinical record with symptoms and ICD diagnosis](screenshots/web/16-doctor-record.jpg)

   4.4. [Two-step laboratory order workflow after creating a source order](screenshots/web/17-doctor-lab-order.jpg)

   4.5. [Successful laboratory request created through the web form](screenshots/web/18-doctor-lab-order-success.jpg)

   4.6. [Patient-based clinical record search](screenshots/web/19-doctor-record-search.jpg)

   4.7. [Patient directory available to the doctor](screenshots/web/20-doctor-patients.jpg)

   4.8. [Patient identity and contact details using synthetic data](screenshots/web/21-doctor-patient-detail.jpg)

   4.9. [Doctor read access to department and staff directory](screenshots/web/22-doctor-organization.jpg)

   4.10. [Laboratory queue including the request created through the doctor UI](screenshots/web/23-doctor-lab.jpg)

   4.11. [Drug catalogue with prices and stock availability](screenshots/web/24-doctor-drugs.jpg)

   4.12. [Prescription form linked to an outpatient record and visit](screenshots/web/25-doctor-prescription-form.jpg)

   4.13. [Drug selection, quantity and dosage interaction before submission](screenshots/web/26-doctor-prescription-lines.jpg)

5. **Nurse — web**

   5.1. [Nurse appointment queue with reception actions](screenshots/web/27-nurse-appointments.jpg)

   5.2. [Visit awaiting reception with check-in action](screenshots/web/28-nurse-checkin-before.jpg)

   5.3. [Successful check-in moves the visit to awaiting payment](screenshots/web/29-nurse-checkin-after.jpg)

   5.4. [Nurse patient directory and registration entry point](screenshots/web/30-nurse-patients.jpg)

   5.5. [Patient registration form with identity, contact and insurance fields](screenshots/web/31-nurse-patient-form.jpg)

   5.6. [Nurse access to organization reference data](screenshots/web/32-nurse-organization.jpg)

   5.7. [Nurse read access to clinical record lookup](screenshots/web/33-nurse-records.jpg)

   5.8. [Nurse laboratory queue view](screenshots/web/34-nurse-lab.jpg)

   5.9. [Patient notification lookup and reminder history](screenshots/web/35-nurse-notifications.jpg)

6. **Laboratory technician — web**

   6.1. [Technician laboratory queue and financial readiness states](screenshots/web/36-labtech-queue.jpg)

   6.2. [Laboratory test detail with exact financial clearance reference](screenshots/web/37-labtech-detail.jpg)

   6.3. [Observed start rejection despite local financial readiness](screenshots/web/38-labtech-start-blocker.jpg) — LAB_CLEARANCE_REQUIRED is the observed rejection. Billing lookup reports eligible=true. Timestamp precision across Lab persistence is a suspected cause requiring a regression test; the test remains READY and no results were fabricated.

7. **Pharmacist — web**

   7.1. [Drug inventory and low-stock warning](screenshots/web/39-pharmacist-drugs.jpg)

   7.2. [Low-stock drug detail and stock adjustment controls](screenshots/web/40-pharmacist-drug-detail.jpg)

   7.3. [Stock adjustment confirmation from 8 to 28 units](screenshots/web/41-pharmacist-stock-confirm.jpg)

   7.4. [Updated drug stock after the confirmed adjustment](screenshots/web/42-pharmacist-stock-success.jpg)

   7.5. [Prescription lookup by its public identifier](screenshots/web/43-pharmacist-rx-search.jpg)

   7.6. [Paid outpatient prescription awaiting dispense](screenshots/web/44-pharmacist-rx-ready.jpg)

   7.7. [Dispense confirmation with payment and stock revalidation](screenshots/web/45-pharmacist-dispense-confirm.jpg)

   7.8. [Successful dispense with the prescription completed](screenshots/web/46-pharmacist-dispense-success.jpg)

8. **Cashier — web**

   8.1. [Legacy patient invoice lookup and refund-obligation entry point](screenshots/web/47-cashier-invoices.jpg) — The legacy invoice list is empty for this V1 outpatient patient. Actual EXAM, LAB_TEST and PRESCRIPTION payments use exact-source payment requests, illustrated next.

   8.2. [Exact EXAM payment request resolved from the checked-in visit](screenshots/web/48-cashier-request.jpg)

   8.3. [Successful demo payment receipt in the web form](screenshots/web/49-cashier-payment-success.jpg)

9. **Patient — web**

   9.1. [Patient role notification history and follow-up reminder](screenshots/web/50-patient-notifications.jpg)

   9.2. [Follow-up reminder content and delivery status](screenshots/web/52-patient-reminder-detail.jpg)

10. **Mobile GUI — shared**

   10.1. [Flutter login screen in a 390 × 844 phone viewport](screenshots/mobile/01-login.jpg)

11. **Mobile GUI — administrator**

   11.1. [Administrator mobile workspace and available modules](screenshots/mobile/02-admin-home.jpg)

   11.2. [Administrator navigation drawer with role identity](screenshots/mobile/03-admin-menu.jpg)

   11.3. [Department list loaded through the real Gateway](screenshots/mobile/04-admin-departments.jpg)

   11.4. [Staff list and department/job filters](screenshots/mobile/05-admin-staff.jpg)

   11.5. [Mobile account creation and access controls](screenshots/mobile/06-admin-accounts.jpg)

12. **Mobile GUI — manager**

   12.1. [Manager workspace and role-filtered modules](screenshots/mobile/07-manager-home.jpg)

   12.2. [Report workspace marked as under development](screenshots/mobile/08-manager-reports.jpg) — The mobile report feature is a placeholder in this build.

13. **Mobile GUI — doctor**

   13.1. [Doctor workspace and available mobile modules](screenshots/mobile/09-doctor-home.jpg)

   13.2. [Outpatient appointment list loaded from Clinical through Gateway](screenshots/mobile/10-doctor-appointments.jpg)

   13.3. [Appointment date filter interaction](screenshots/mobile/11-doctor-calendar.jpg)

   13.4. [Clinical record workspace marked as under development](screenshots/mobile/12-doctor-records.jpg) — Clinical record editing is demonstrated on web; this mobile workspace remains unfinished.

14. **Mobile GUI — nurse**

   14.1. [Nurse mobile workspace and reception modules](screenshots/mobile/13-nurse-home.jpg)

   14.2. [Patient list with real seeded records and search controls](screenshots/mobile/14-nurse-patients.jpg)

   14.3. [Patient detail loaded on Flutter](screenshots/mobile/15-nurse-patient-detail.jpg)

   14.4. [Editable patient form populated from the existing record](screenshots/mobile/16-nurse-patient-edit.jpg)

15. **Mobile GUI — laboratory technician**

   15.1. [Technician mobile workspace restricted to laboratory](screenshots/mobile/17-labtech-home.jpg)

   15.2. [Laboratory list showing READY and AWAITING_PAYMENT states](screenshots/mobile/18-labtech-queue.jpg) — The mobile payment label still reads the legacy paid field. READY status and web financial-clearance evidence must be interpreted separately.

   15.3. [READY laboratory status filter returns three seeded tests](screenshots/mobile/19-labtech-filter-ready.jpg)

16. **Mobile GUI — pharmacist**

   16.1. [Pharmacist mobile workspace](screenshots/mobile/20-pharmacist-home.jpg)

   16.2. [Pharmacy workspace reports unavailable functionality](screenshots/mobile/21-pharmacist-unavailable.jpg) — Inventory management and dispensing are verified through the web client. Mobile pharmacy is unavailable in this build.

17. **Mobile GUI — cashier**

   17.1. [Cashier mobile workspace restricted to billing](screenshots/mobile/22-cashier-home.jpg)

   17.2. [Billing workspace with invoice, payment-request and refund tabs](screenshots/mobile/23-cashier-billing.jpg)

   17.3. [Billing-authorized cash receipt form for a 100,000 VND demo payment](screenshots/mobile/24-cashier-payment-form.jpg)

   17.4. [Successful Flutter payment changes the request to PAID with zero remaining balance](screenshots/mobile/25-cashier-payment-success.jpg)

18. **Mobile GUI — patient**

   18.1. [Patient mobile workspace restricted to notifications](screenshots/mobile/26-patient-home.jpg)

   18.2. [Patient notification history from Support through Gateway](screenshots/mobile/27-patient-notifications.jpg)

   18.3. [Reminder detail and delivery state in Flutter](screenshots/mobile/28-patient-reminder.jpg)
