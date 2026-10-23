# MediFlow — Task Sheet

**Ngày lập / Created:** 16/10/2026 · **Cập nhật / Updated:** 23/10/2026
**Kế hoạch dự án / Project plan:** 01/10/2026–24/10/2026 · **Source:** `master` tại / at `768a6b47`.

## Tiếng Việt

Bản rút gọn gộp 35 công việc thành 15 nhóm, đối chiếu cấu trúc và các điểm triển khai backend, frontend, mobile cùng tài liệu Review3. **Status “Hoàn thành” ghi nhận phần triển khai mã nguồn và tài liệu trong phạm vi từng dòng**, theo yêu cầu cập nhật tasksheet; không thay thế nghiệm thu toàn hệ thống. Thời gian bên dưới giữ lịch dự kiến của các việc đã gộp; bỏ ngày bắt đầu/số ngày thực tế vì bản cũ chưa có số liệu thực hiện được xác nhận. Nội trú và phẫu thuật tiếp tục hoãn.

| STT | Thành viên | Nội dung công việc đã triển khai | Phạm vi | Lịch dự kiến | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Cả nhóm; Vinh tổng hợp | Thiết kế MVP ngoại trú, phân công, hợp đồng API/sự kiện và kế hoạch tích hợp. | Thiết kế chung | 01–08/10/2026 | Hoàn thành |
| 2 | Hoàng Anh | Tài khoản, nhân sự, khoa/phòng, người bệnh; xác thực, định tuyến và phân quyền API. | Backend Core, Gateway | 01–14/10/2026 | Hoàn thành |
| 3 | Vinh | Lịch hẹn, tiếp nhận, hồ sơ khám, chẩn đoán, chỉ định và kết quả xét nghiệm; kiểm tra điều kiện thanh toán. | Backend Clinical, Lab | 01–17/10/2026 | Hoàn thành |
| 4 | Huy | Danh mục thuốc, tồn kho, đơn thuốc, cấp/hủy và kiểm tra quyền, thanh toán. | Backend Pharmacy | 01–15/10/2026 | Hoàn thành |
| 5 | Vinh (tiếp nhận Lộc) | Khoản thu theo nguồn, hóa đơn, thanh toán, điều chỉnh/hoàn tiền và sự kiện tài chính. | Backend Billing | 01–16/10/2026 | Hoàn thành |
| 6 | Huy: Report; Vinh: Notification, điều phối | API báo cáo, thông báo theo người nhận; ghép Report/Notification vào Support với kho dữ liệu riêng. | Backend Report, Notification, Support | 04–18/10/2026 | Hoàn thành |
| 7 | Vinh điều phối; Hoàng Anh phối hợp | Đăng nhập, phiên, menu theo vai trò, bố cục thích ứng và xử lý lỗi API chung. | Frontend dùng chung | 01–11/10/2026 | Hoàn thành |
| 8 | Hoàng Anh | Danh sách, tìm kiếm, bộ lọc, phân trang, chi tiết và biểu mẫu tài khoản, nhân sự, khoa/phòng, người bệnh. | Web Organization, Patient | 03–16/10/2026 | Hoàn thành |
| 9 | Vinh | Màn hình lịch hẹn/tiếp nhận, hồ sơ/chẩn đoán, tạo chỉ định, chi tiết và kết quả xét nghiệm. | Web Appointments, Records, Lab | 03–17/10/2026 | Hoàn thành |
| 10 | Huy | Giao diện thuốc, tồn kho, đơn thuốc/cấp thuốc và truy vấn báo cáo theo API. | Web Pharmacy, Reports | 03–17/10/2026 | Hoàn thành |
| 11 | Vinh (tiếp nhận Lộc) | Tra cứu khoản thu, hóa đơn/thanh toán/hoàn tiền; danh sách, chi tiết và gửi thông báo theo quyền. | Web Billing, Notifications | 03–18/10/2026 | Hoàn thành |
| 12 | Vinh điều phối; Hoàng Anh phối hợp xác thực | Cấu hình Gateway, đăng nhập/đăng xuất, lưu phiên an toàn, menu/route theo vai trò và trạng thái tải/lỗi. | Mobile app, core, auth | 01–13/10/2026 | Hoàn thành |
| 13 | Vinh; Huy: Pharmacy, Report | Các workspace người bệnh, tổ chức, lịch hẹn, hồ sơ khám, Lab, thuốc/đơn thuốc, thanh toán, thông báo và báo cáo. | Mobile features; Flutter web | 04–18/10/2026 | Hoàn thành |
| 14 | Cả nhóm; Vinh tổng hợp kiểm tra | Mã kiểm thử nghiệp vụ, API/phân quyền, UI và hồi quy; tài khoản/dữ liệu demo, kịch bản và ghi nhận kết quả Review3. | Backend, web, mobile — test và demo | 06–23/10/2026 | Hoàn thành |
| 15 | Cả nhóm; Vinh tổng hợp | Source, cấu hình, hướng dẫn chạy/sử dụng, báo cáo Review3, ảnh minh họa và danh sách giới hạn bàn giao. | Tài liệu và bộ source bàn giao | 09–24/10/2026 | Hoàn thành |

## English

This compact version consolidates 35 tasks into 15 groups based on the current backend, frontend, mobile implementation points and Review3 documentation. **“Completed” records source and documentation implementation within each row**, as requested for this tasksheet; it does not certify full-system acceptance. Dates retain the consolidated planned schedule. Unverified actual start dates and durations have been removed. Inpatient care and surgery remain deferred.

| No. | Member | Implemented work | Scope | Planned schedule | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | All members; Vinh consolidates | Outpatient MVP design, ownership, API/event contracts and integration plan. | Shared design | 01–08 Oct 2026 | Completed |
| 2 | Hoàng Anh | Accounts, staff, departments and patients; authentication, routing and API authorization. | Backend Core, Gateway | 01–14 Oct 2026 | Completed |
| 3 | Vinh | Appointments, check-in, records, diagnoses, lab orders/results and payment preconditions. | Backend Clinical, Lab | 01–17 Oct 2026 | Completed |
| 4 | Huy | Medicine catalogue, stock, prescriptions, dispensing/cancellation, permission and payment checks. | Backend Pharmacy | 01–15 Oct 2026 | Completed |
| 5 | Vinh (taking over from Lộc) | Source-based charges, invoices, payments, adjustments/refunds and financial events. | Backend Billing | 01–16 Oct 2026 | Completed |
| 6 | Huy: Report; Vinh: Notification and coordination | Report APIs, recipient-scoped notifications and Support composition with separate stores. | Backend Report, Notification, Support | 04–18 Oct 2026 | Completed |
| 7 | Vinh coordinates; Hoàng Anh supports | Login, sessions, role-based menus, responsive layout and shared API error handling. | Shared frontend | 01–11 Oct 2026 | Completed |
| 8 | Hoàng Anh | Lists, search, filters, pagination, details and forms for accounts, staff, departments and patients. | Web Organization, Patient | 03–16 Oct 2026 | Completed |
| 9 | Vinh | Appointment/check-in, record/diagnosis, order creation, lab detail and result screens. | Web Appointments, Records, Lab | 03–17 Oct 2026 | Completed |
| 10 | Huy | Medicine, stock, prescription/dispensing screens and API-backed report queries. | Web Pharmacy, Reports | 03–17 Oct 2026 | Completed |
| 11 | Vinh (taking over from Lộc) | Charge discovery, invoices/payments/refunds and authorized notification list, detail and send screens. | Web Billing, Notifications | 03–18 Oct 2026 | Completed |
| 12 | Vinh coordinates; Hoàng Anh supports authentication | Gateway configuration, login/logout, secure session storage, role-based routing and loading/error states. | Mobile app, core, auth | 01–13 Oct 2026 | Completed |
| 13 | Vinh; Huy: Pharmacy, Report | Patient, organization, appointment, record, lab, medicine/prescription, payment, notification and report workspaces. | Mobile features; Flutter web | 04–18 Oct 2026 | Completed |
| 14 | All members; Vinh consolidates verification | Business, API/authorization, UI and regression test code; demo accounts/data, scenarios and recorded Review3 results. | Backend, web, mobile — tests and demo | 06–23 Oct 2026 | Completed |
| 15 | All members; Vinh consolidates | Source, configuration, setup/user guides, Review3 report, screenshots and documented handover limitations. | Documentation and source handover package | 09–24 Oct 2026 | Completed |

## Căn cứ và giới hạn / Evidence and limits

- Đối chiếu / Reviewed: `backend/*/src`, `frontend/src`, `mobile/lib` và mã kiểm thử liên quan / related test code. Lượt này rà soát source và tài liệu, không chạy lại test/build / This update reviews source and documentation without rerunning tests or builds.
- [Frontend workboard](../../../../frontend/docs/frontend-workboard.md): kết quả kiểm tra gần nhất và giới hạn; toàn bộ frontend suite vẫn ghi nhận một lỗi bất đồng bộ Pharmacy dù chạy riêng đã pass / latest verification and limits, including one Pharmacy async failure in the full frontend suite despite a passing isolated rerun.
- [Mobile README](../../../../mobile/README.md) và / and [Review3 evidence](../../../documentation_docx/Review3/README.md): chức năng, kiểm tra và ảnh Flutter web; chưa xác nhận nghiệm thu Android/iOS native / features, checks and Flutter web screenshots; native Android/iOS acceptance remains unverified.
- [Runtime handoff](../../../handoffs/HANDOFF-REVIEW3-RUNTIME-2026-10-22.md): còn đối soát Lab clearance, Report coverage và quyền tra cứu tham chiếu; không coi phản hồi bị chặn là luồng thành công / Lab clearance, Report coverage and reference permissions still need reconciliation; blocked commands are not successful journeys.
- [Support README](../../../../backend/support-service/README.md): cấu hình sáu business deployments đã có; nghiệm thu DB/broker và cutover là bước riêng / six business deployments are configured; DB/broker acceptance and cutover remain separate.
