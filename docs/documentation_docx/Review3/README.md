# Review3 refreshed mobile and screenshots

1. Báo cáo chính: [Review3.docx](Review3.docx). Phần **Screen shots** đã thay bằng 107 ảnh mới trong 18 nhóm: 56 web và 51 mobile. Chú thích theo chỉ mục; không tạo bảng ảnh. Toàn bộ 49 bảng và XML nội dung ngoài phần ảnh được giữ nguyên khi thay ảnh. Đã render và rà soát bản 239 trang; 107 ảnh nằm tại trang 99–205, mỗi trang một ảnh, không tràn khung trang.
2. Ảnh được chụp từ giao diện chạy thật qua Gateway với PostgreSQL, RabbitMQ và sáu ứng dụng nghiệp vụ. Bệnh nhân và nhân viên là dữ liệu tổng hợp phục vụ demo. Ảnh web PNG 1904/1920 × 1080; mobile PNG 1290 × 2796 từ viewport 430 × 932 ở tỷ lệ 3. Mobile là Flutter web; không xác nhận Android/iOS native.
3. Phạm vi gồm trang chung và tám vai trò: Administrator, Manager, Doctor, Nurse, Laboratory technician, Pharmacist, Cashier, Patient. Có danh sách, tìm kiếm, bộ lọc, chọn tên, chi tiết, biểu mẫu và tương tác theo quyền. Các biểu mẫu chưa gửi được ghi rõ; không suy thành công từ ảnh mở form.
4. Mobile bổ sung lịch hẹn/tiếp nhận, hồ sơ khám/chẩn đoán/nguồn chỉ định, Lab detail và lệnh nghiệp vụ, báo cáo, tham chiếu bằng tên, tài khoản có tìm/lọc/phân trang/chọn, thông báo cá nhân theo phiên. UUID tham chiếu trong các màn hình mobile đã xử lý được đặt trong phần kỹ thuật mở rộng. Quyền không cho tra cứu tên được giải thích rõ, không tự gán tên giả.
5. Bằng chứng thực tế gồm tiếp nhận chuyển sang chờ thanh toán, khoản thu EXAM 100.000 VND được Billing xác nhận PAID với số dư 0, tạo chỉ định Lab mới, điều chỉnh tồn kho web và lịch sử thông báo. Đơn Pharmacy chưa có clearance bị chặn xuất; Lab cũ bị chặn bắt đầu dù có readiness cũ; Report outpatient chưa có accepted projection coverage. Không mô tả các phản hồi bị chặn thành luồng thành công. [Handoff dev](../../handoffs/HANDOFF-REVIEW3-RUNTIME-2026-10-22.md).
6. Kiểm tra code cuối: **341 tests pass**, `flutter analyze` không có issue, build Flutter web thành công. Các kiểm tra này không thay thế native execution hoặc nghiệm thu toàn backend. Đã sửa race giữa refresh và Lab command, cùng chống phản hồi phiên cũ, lệnh lặp và nhập sai hợp đồng.
7. Bộ dữ liệu được giữ trong volumes `mediflow-review3-pgdata` và `mediflow-review3-rabbitdata`; không seed lại hoặc xóa volumes. Bản runtime gốc trong `demo-runtime/` vẫn được Git bỏ qua. Theo yêu cầu tác giả, [snapshot runtime](demo-runtime/archive/README.md) đã được đưa vào Git, gồm tài khoản demo, dump/manifest, cấu hình và log đã che thông tin bí mật; chỉ dùng trong môi trường demo biệt lập. Seed manifest là snapshot ban đầu; trạng thái hiện tại đã thay đổi qua các thao tác UI.
8. Mở web tại http://localhost:3000, mobile tại http://localhost:4300. Backend riêng cho máy này được khởi chạy bằng `runtime_review3.py start`; Gateway 127.0.0.1:58910. Trong frontend, bật hai cờ outpatient/report như cấu hình runtime hiện có. Build mobile dùng `GATEWAY_URL=http://localhost:4300/api`, `PHARMACY_MODE=outpatient`, `MEDIFLOW_OUTPATIENT_UI_ENABLED=true`; sau đó chạy `dart run tool/dev_server.dart 4300 http://127.0.0.1:58910` từ mobile. Cờ client không kích hoạt backend hoặc cấp thêm quyền.
9. Script `insert_screenshots.py` chỉ thay phần giữa Screen shots và Testing document trong **bản Word hiện tại**, sao lưu riêng trước khi ghi và kiểm tra giữ nguyên các section boundary/nội dung còn lại. Không phục hồi từ bản Word gốc cũ. `screenshots/captures.json` và `captures-new.json` cùng ghi inventory đợt này. Hai thư mục `web-new`/`mobile-new` là nguồn ảnh đang dùng; các thư mục web/mobile cũ được giữ làm lịch sử. `Review3_bak.docx` là bản sao lưu của tác giả; không thay thế bản chính. File Word tạm đã biến mất khi Word đóng, nên không có file tạm để lưu.
10. Testing document và Final Checklist hiện có được bảo toàn, bao gồm lời giải thích PASS là remark điền sẵn theo yêu cầu tác giả; ảnh và kiểm tra code không xác nhận mọi mục trong checklist đã chạy.

## Indexed screenshot inventory


1. **Shared web screens**

   1.1. [MediFlow sign-in screen.](screenshots/web-new/01-shared-login.png)

   1.2. [Public MediFlow landing page links the outpatient workspaces.](screenshots/web-new/02-shared-home.png)


2. **Administrator — web**

   2.1. [Administrator searches and opens patient identity records.](screenshots/web-new/03-admin-patients.png)

   2.2. [Patient identity detail shows the patient name, demographics and contact data.](screenshots/web-new/04-admin-patient-detail.png)

   2.3. [Administrator organization workspace lists departments and staff with management controls.](screenshots/web-new/05-admin-organization.png)

   2.4. [Administrator filters the eight role accounts used for Review 3.](screenshots/web-new/06-admin-accounts.png)

   2.5. [Selecting the doctor account populates account-management controls; credentials are not changed.](screenshots/web-new/07-admin-account-selected.png)

   2.6. [Administrator appointment list resolves patient, doctor and department names.](screenshots/web-new/08-admin-appointments.png)

   2.7. [Appointment creation uses name lookup for patient, department and doctor.](screenshots/web-new/09-admin-appointment-form.png)


3. **Manager — web**

   3.1. [Manager monthly report returns the current Report aggregate; zero values are not presented as reconciled outpatient coverage.](screenshots/web-new/10-manager-monthly.png)

   3.2. [Outpatient report reports missing reconciled coverage (REPORT_NOT_FOUND) for the demo date; no audited figures are fabricated.](screenshots/web-new/11-manager-outpatient-report.png)

   3.3. [Manager reads departments and staff without administrator-only account controls.](screenshots/web-new/12-manager-organization.png)

   3.4. [Manager appointment overview respects the role’s identity lookup permissions.](screenshots/web-new/13-manager-appointments.png)

   3.5. [Manager views the laboratory queue and financial states without processing commands.](screenshots/web-new/14-manager-lab.png)


4. **Doctor — web**

   4.1. [Doctor appointment queue with resolved clinical reference names](screenshots/web-new/15-doctor-appointments.png)

   4.2. [Visit timeline with check-in clearance and examination milestones](screenshots/web-new/16-doctor-visit.png)

   4.3. [Clinical record with symptoms and resolved patient doctor and department](screenshots/web-new/17-doctor-record.png)

   4.4. [ICD diagnosis and controlled visit completion form](screenshots/web-new/18-doctor-diagnosis.png)

   4.5. [Draft source-linked laboratory order uses the configured LAB catalogue code; this draft is not submitted.](screenshots/web-new/19-doctor-lab-order.png)

   4.6. [Clinical source order created with configured LAB price code; second-step Lab submission remains explicit.](screenshots/web-new/20-doctor-source-order.png)

   4.7. [Patient name based clinical record search](screenshots/web-new/21-doctor-record-search.png)

   4.8. [Doctor reads seeded patient identities](screenshots/web-new/22-doctor-patients.png)

   4.9. [Patient detail with synthetic identity and contact information](screenshots/web-new/23-doctor-patient-detail.png)

   4.10. [Doctor reads departments and staff references](screenshots/web-new/24-doctor-organization.png)

   4.11. [Laboratory queue with clinical record labels and financial readiness](screenshots/web-new/25-doctor-lab.png)

   4.12. [Doctor views the medicine catalogue with stock and prices.](screenshots/web-new/26-doctor-drugs.png)

   4.13. [Doctor prepares the prescription context using the record, patient and department selectors.](screenshots/web-new/27-doctor-prescription-form.png) — Draft form before submission; the prescriber still needs selection.

   4.14. [Doctor prepares a medicine line with quantity and instructions; draft has not been submitted.](screenshots/web-new/28-doctor-prescription-lines.png)

   4.15. [Successful second-step Lab request creation through the actual web form. Financial clearance remains a separate workflow.](screenshots/web-new/45-doctor-lab-created.png)


5. **Nurse — web**

   5.1. [Nurse reception queue displays patient, doctor and department names.](screenshots/web-new/29-nurse-appointments.png)

   5.2. [Reception detail before check-in: appointment is pending.](screenshots/web-new/30-nurse-before-checkin.png)

   5.3. [Successful check-in records the reception time and moves the appointment to payment pending; examination remains locked until clearance.](screenshots/web-new/31-nurse-after-checkin.png)

   5.4. [Nurse searches notification history for the selected patient by name.](screenshots/web-new/32-nurse-notifications.png)

   5.5. [Nurse patient directory and registration entry point.](screenshots/web-new/46-nurse-patients.png)

   5.6. [Patient registration form with identity, contact and insurance fields.](screenshots/web-new/47-nurse-patient-form.png)

   5.7. [Nurse reads department and staff reference data.](screenshots/web-new/48-nurse-organization.png)

   5.8. [Nurse can search clinical records using a patient name.](screenshots/web-new/49-nurse-records.png)

   5.9. [Nurse laboratory queue includes the newly created CRP request.](screenshots/web-new/50-nurse-lab.png)


6. **Laboratory technician — web**

   6.1. [Lab technician queue shows financial status; names are explicitly unavailable under this role’s current lookup permissions.](screenshots/web-new/33-lab-queue.png)

   6.2. [Lab request detail before processing, with financial confirmation and no result yet.](screenshots/web-new/34-lab-detail.png)

   6.3. [Backend rejects processing because Billing does not authorise this exact Lab request (LAB_CLEARANCE_REQUIRED). Result entry is not claimed as completed.](screenshots/web-new/35-lab-clearance-block.png)


7. **Pharmacist — web**

   7.1. [Pharmacist medicine catalogue with current stock and expiry dates.](screenshots/web-new/36-pharmacist-catalog.png)

   7.2. [Medicine detail and stock adjustment controls; no stock change is submitted.](screenshots/web-new/37-pharmacist-stock.png)

   7.3. [Existing seeded prescription is completed and dispensed. Web prescription detail still exposes technical references when role lookup is denied; tracked for developer handoff.](screenshots/web-new/38-pharmacist-dispensed.png) — Known existing web identity display limitation; mobile hides these references by default.

   7.4. [Stock adjustment confirmation adds ten demo units of Loratadin (28 to 38).](screenshots/web-new/51-pharmacist-stock-confirm.png)

   7.5. [Loratadin stock is updated to 38 after confirmation.](screenshots/web-new/52-pharmacist-stock-success.png)

   7.6. [Pharmacist opens an existing prescription using its supplied reference.](screenshots/web-new/53-pharmacist-rx-search.png)

   7.7. [Active prescription awaiting dispense; payment will be revalidated by the backend.](screenshots/web-new/54-pharmacist-rx-ready.png)

   7.8. [Pharmacist reviews the dispense confirmation before backend revalidation.](screenshots/web-new/55-pharmacist-dispense-confirm.png)

   7.9. [Unpaid demo prescription is correctly blocked from dispensing; an already completed prescription is documented separately.](screenshots/web-new/56-pharmacist-dispense-block.png)


8. **Cashier — web**

   8.1. [Invoice lookup returns no legacy invoice for the selected patient; outpatient charges use the payment-request flow.](screenshots/web-new/39-cashier-invoice-lookup.png)

   8.2. [Billing returns a 100,000 VND examination charge for the just checked-in demo visit.](screenshots/web-new/40-cashier-charge.png)

   8.3. [Demo cash payment of 100,000 VND succeeds and returns a transaction receipt. Charge summary shown above is the pre-payment lookup snapshot.](screenshots/web-new/41-cashier-payment-receipt.png)


9. **Patient — web**

   9.1. [Patient views their own seeded notification history.](screenshots/web-new/42-patient-notifications.png)

   9.2. [Patient reads a follow-up reminder and its sent status.](screenshots/web-new/43-patient-notification-detail.png)

   9.3. [Patient cannot access the staff patient registry.](screenshots/web-new/44-patient-access-denied.png)


10. **Mobile GUI — shared**

   10.1. [Mobile login screen at 430 × 932 CSS pixels, rendered at 3x density.](screenshots/mobile-new/01-shared-login.png)


11. **Mobile GUI — administrator**

   11.1. [Administrator workspace exposes all active outpatient contexts.](screenshots/mobile-new/02-admin-workspace.png)

   11.2. [Department directory displays department names without UUIDs.](screenshots/mobile-new/03-admin-departments.png)

   11.3. [Department detail and edit entry point.](screenshots/mobile-new/04-admin-department-detail.png)

   11.4. [Department head picker lists staff names and job titles from the authorised Gateway endpoint.](screenshots/mobile-new/05-admin-head-picker.png)

   11.5. [Staff directory filtered to Internal Medicine shows names and job titles without UUIDs.](screenshots/mobile-new/06-admin-staff.png)

   11.6. [Administrator searches the live account directory with role and status filters.](screenshots/mobile-new/07-admin-accounts.png)

   11.7. [Patient list shows names and synthetic identity references without UUIDs.](screenshots/mobile-new/08-admin-patients.png)

   11.8. [Patient detail presents the name prominently and keeps technical ID collapsed.](screenshots/mobile-new/09-admin-patient-detail.png)

   11.9. [Mobile appointment queue resolves patient, doctor and department names through the Gateway.](screenshots/mobile-new/10-admin-appointments.png)

   11.10. [Appointment form uses name-based patient, department and doctor selection instead of manual UUID fields.](screenshots/mobile-new/11-admin-appointment-form.png)

   11.11. [Selecting the doctor account populates status management by account identity.](screenshots/mobile-new/51-admin-account-selection.png) — No account status change is submitted.


12. **Mobile GUI — manager**

   12.1. [Manager workspace includes organization, appointment, laboratory and reporting views.](screenshots/mobile-new/12-manager-workspace.png)

   12.2. [Manager selects the report query and reporting period.](screenshots/mobile-new/13-manager-report-types.png)

   12.3. [Monthly Report aggregate loads through the real Gateway; zero projection values are shown as returned.](screenshots/mobile-new/14-manager-monthly.png)

   12.4. [Outpatient report preserves the backend missing-coverage response instead of inventing reconciled figures.](screenshots/mobile-new/15-manager-outpatient-coverage.png)

   12.5. [Manager appointment monitoring hides unavailable patient identity and displays authorised doctor/department names.](screenshots/mobile-new/16-manager-appointments.png)


13. **Mobile GUI — doctor**

   13.1. [Doctor workspace exposes clinical and pharmacy functions authorised to the role.](screenshots/mobile-new/17-doctor-workspace.png)

   13.2. [Doctor queue shows real patient, doctor and department names.](screenshots/mobile-new/18-doctor-appointments.png)

   13.3. [Mobile visit detail shows the in-progress state with resolved identity labels.](screenshots/mobile-new/19-doctor-visit.png)

   13.4. [Name-based clinical record search returns the current outpatient record.](screenshots/mobile-new/20-doctor-record-search.png)

   13.5. [Clinical detail shows named patient, doctor, department, appointment, symptoms and ICD diagnosis.](screenshots/mobile-new/21-doctor-record-detail.png)

   13.6. [Doctor diagnosis dialog accepts diagnosis, ICD code and description.](screenshots/mobile-new/22-doctor-diagnosis.png)

   13.7. [Doctor medicine catalogue shows prices, stock and expiry dates.](screenshots/mobile-new/23-doctor-drugs.png)

   13.8. [Prescription draft links named patient, department, doctor, record and visit through scoped selectors; technical UUIDs remain hidden.](screenshots/mobile-new/24-doctor-prescription-context.png)

   13.9. [Doctor prepares a Paracetamol line with quantity and instructions; the draft is not submitted without an authorised price context.](screenshots/mobile-new/25-doctor-prescription-line.png)


14. **Mobile GUI — nurse**

   14.1. [Nurse workspace includes reception, patient registration and notification history.](screenshots/mobile-new/26-nurse-workspace.png)

   14.2. [Nurse appointment queue provides reception actions.](screenshots/mobile-new/27-nurse-appointments.png)

   14.3. [Nurse views the named patient appointment before reception.](screenshots/mobile-new/28-nurse-checkin-before.png)

   14.4. [Reception succeeds and the appointment moves to payment pending.](screenshots/mobile-new/29-nurse-checkin-after.png)

   14.5. [Nurse searches notification history using the patient name.](screenshots/mobile-new/30-nurse-notification-history.png)


15. **Mobile GUI — laboratory technician**

   15.1. [Laboratory technician workspace shows the permitted Lab workflow.](screenshots/mobile-new/31-lab-workspace.png)

   15.2. [Lab queue contains real requests and their billing states.](screenshots/mobile-new/32-lab-queue.png) — Name lookup is not authorized for this role; the UI states the limitation without exposing UUIDs.

   15.3. [Lab detail shows reconciliation and available workflow actions with technical identifiers hidden.](screenshots/mobile-new/33-lab-detail.png)

   15.4. [Starting the test is rejected when Billing no longer authorizes the exact request.](screenshots/mobile-new/34-lab-billing-guard.png) — Observed backend limitation; this is evidence of the guard, not a successful test start.


16. **Mobile GUI — pharmacist**

   16.1. [Pharmacist workspace provides drug and prescription access.](screenshots/mobile-new/35-pharmacist-workspace.png)

   16.2. [Pharmacist searches the live catalog by drug name and sees current stock.](screenshots/mobile-new/36-pharmacist-drug-search.png)

   16.3. [Drug detail shows price, stock, expiry and manufacturer; technical identifiers are collapsed.](screenshots/mobile-new/37-pharmacist-drug-detail.png)

   16.4. [Stock adjustment uses the drug name and current server stock, with technical identifiers collapsed.](screenshots/mobile-new/47-pharmacist-stock-form.png) — Form shown before submission.

   16.5. [Prescription detail presents the drug, dose, quantity, price and workflow state.](screenshots/mobile-new/48-pharmacist-prescription-detail.png) — Reference-name lookups are restricted for this role; UUIDs remain in collapsed technical controls.

   16.6. [Dispense confirmation explains server-side stock and Billing clearance checks.](screenshots/mobile-new/49-pharmacist-dispense-confirm.png)

   16.7. [Dispense is rejected for the unpaid prescription without current Billing clearance.](screenshots/mobile-new/50-pharmacist-dispense-guard.png) — This demonstrates the payment guard; no dispense success is claimed.


17. **Mobile GUI — cashier**

   17.1. [Cashier workspace provides invoice and payment workflows.](screenshots/mobile-new/38-cashier-workspace.png)

   17.2. [Invoice workspace keeps reception references collapsed in normal use.](screenshots/mobile-new/39-cashier-invoices.png)

   17.3. [Cashier selects the charge type and looks up the reception reference.](screenshots/mobile-new/40-cashier-charge-pending.png) — This screenshot shows the lookup workspace, not a pending payment result.

   17.4. [The completed demo payment is confirmed by Billing: PAID, collected 100,000 VND, balance zero.](screenshots/mobile-new/41-cashier-charge-paid.png)

   17.5. [Refund workspace explains reconciliation and provides obligation lookup.](screenshots/mobile-new/42-cashier-refund-workspace.png) — No refund is executed in this screenshot.


18. **Mobile GUI — patient**

   18.1. [Patient workspace contains the authorized personal notification workflow.](screenshots/mobile-new/43-patient-workspace.png)

   18.2. [The patient sees personal notification history resolved from the signed-in identity.](screenshots/mobile-new/44-patient-own-notifications.png)

   18.3. [Patient opens an authorized notification detail with delivery state and collapsed technical identifiers.](screenshots/mobile-new/45-patient-notification-detail.png) — Recipient lookup is restricted for this role; address and retry data are not exposed by this API.

   18.4. [Patient navigation only exposes authorized destinations.](screenshots/mobile-new/46-patient-navigation.png)
