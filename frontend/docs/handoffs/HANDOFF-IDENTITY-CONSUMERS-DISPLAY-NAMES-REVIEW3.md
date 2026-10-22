# HANDOFF: Identity → nghiệp vụ web/mobile — hiển thị tên thay UUID

- **Status:** ACTIVE — người dùng giao dev xử lý sau; chưa triển khai trong phiên này.
- **Ngày:** 2026-10-22.
- **Producer owner:** Hoàng Anh (`TranHoangAnh94`) — đối chiếu contract và quyền đọc Patient/Organization qua Gateway; Vinh (`Dangvinh77` / `Harori`) — điều phối phần hiển thị dùng chung nếu cần.
- **Consumer owner:** Vinh — Appointment/Medical Record/Lab; Huy (`LQHuy0210`) — Pharmacy/Report; Lộc (`locgit-89`) — Billing/Notification web, theo ownership hiện hành. Mobile theo phân công trong `mobile/README.md` và nearest AGENTS tại thời điểm nhận việc.
- **Affected contexts:** tham chiếu bệnh nhân, bác sĩ/nhân viên, khoa trên các trang nghiệp vụ đang hoạt động.
- **Affected paths đã xác minh:** `frontend/src/features/appointment/components/AppointmentDetail.tsx`, `frontend/src/features/lab/components/LabDetail.tsx`.
- **Phạm vi cần rà soát thêm:** `frontend/src/features/{appointment,medical-record,lab,pharmacy,billing,notification,report}/`; các màn tương ứng trong `mobile/lib/features/`. Đây là phạm vi audit, chưa kết luận mọi trang đều có lỗi.
- **Verified baseline:** ảnh Review 3 được chụp trên candidate dựa trên `14f6a414`; xác nhận lại hai vị trí render trực tiếp UUID trên HEAD `2cffd765`. Chưa xác nhận thiếu endpoint hoặc quyền backend cụ thể.
- **Required action or decision:** thống nhất cách lấy tên qua API hiện hữu được phân quyền và thay phần hiển thị mã tham chiếu bằng tên dễ đọc. Nếu cần lookup chung, Vinh điều phối boundary dùng chung; nếu thiếu public contract/quyền đọc, ghi gap cụ thể cho owner backend trước khi triển khai consumer.
- **Compatibility expectation:** UUID vẫn là định danh trong request/response, URL và command; thay đổi cách hiển thị không đổi wire contract hay quyền truy cập.
- **Resume owner:** Vinh điều phối kiểm tra và tổng hợp; từng dev nhận phần feature thuộc quyền mình. Bộ ảnh/Word chỉ cập nhật sau nghiệm thu.

## 1. Hiện tượng và bằng chứng

1. `AppointmentDetail.tsx` hiện đưa `appointment.patientId`, `appointment.doctorId`, `appointment.departmentId` trực tiếp vào `Identifier`, dưới nhãn Bệnh nhân/Bác sĩ/Khoa. Trang chưa ghép tên cho ba trường này.
2. `LabDetail.tsx` hiện đưa `test.patientId` và `test.requestingDepartmentId` trực tiếp vào `Identifier`. Các định danh hồ sơ/đợt chăm sóc/xác nhận tài chính là trường riêng, không đánh đồng với tên người/khoa.
3. Bộ demo đã có thông tin tên trong dữ liệu danh mục; seed lại không tự thay đổi code render trực tiếp ID. Không sửa database để dùng tên thay UUID.
4. Ảnh liên quan nằm trong [inventory Review 3](../../../docs/documentation_docx/Review3/screenshots/captures.json), ví dụ [chi tiết lịch hẹn](../../../docs/documentation_docx/Review3/screenshots/web/15-doctor-visit.jpg) và [chi tiết xét nghiệm](../../../docs/documentation_docx/Review3/screenshots/web/37-labtech-detail.jpg). Ảnh mobile của bộ này là Flutter web ở viewport điện thoại, chưa phải native Android/iOS.

## 2. Việc giao dev

1. Rà soát các trang danh sách, chi tiết, form chọn và xác nhận hành động để lập danh sách vị trí đang dùng UUID cho tên bệnh nhân, bác sĩ hoặc khoa. Ghi rõ màn/role và owner; không mở rộng sang Inpatient/Surgery deferred.
2. Đối chiếu live DTO và public API qua Gateway để đọc đúng tên. Patient dùng wire name riêng như `hoTen`, không tự giả định mọi context đều có `fullName` hoặc `patientName`. Xác minh từng role được phép đọc dữ liệu nào; handoff này không cho phép mở rộng quyền backend.
3. Hiển thị tên làm thông tin chính; mã bệnh nhân/mã nhân viên hoặc UUID có thể là thông tin phụ khi có nhu cầu đối chiếu. Form chọn người/khoa dùng nhãn dễ đọc nhưng vẫn gửi đúng UUID.
4. Tránh import chéo feature trên web; nếu cần tái sử dụng, thống nhất phần chung trong scope shared của Vinh. Không tạo request lặp cho từng ô/hàng; dedupe/cache trong phiên theo context và tránh giữ dữ liệu của tài khoản trước khi đổi người dùng.
5. Với lookup không được phép, không tồn tại hoặc tạm lỗi: có fallback trung thực, không đoán tên, không làm mất dữ liệu nghiệp vụ chính. Chỉ hiện tên khi lấy được từ nguồn có thẩm quyền.
6. Mobile thực hiện cùng mục tiêu theo Clean Architecture và ownership hiện hành; không giả định bản sửa web tự sửa được Flutter.

## 3. Tiêu chí nghiệm thu

1. Trên dữ liệu có thể tra cứu và role được cấp quyền, các màn đã liệt kê hiển thị đúng tên bệnh nhân, bác sĩ và khoa, không chỉ UUID dưới nhãn tên.
2. Thử ít nhất hai bệnh nhân, hai nhân viên và hai khoa để chứng minh ghép đúng theo ID, kể cả khi chuyển trang, lọc hoặc đổi bản ghi; phản hồi cũ không ghi đè tên của bản ghi mới.
3. Kiểm tra loading, lookup lỗi/404/403 và logout/đổi tài khoản; không lộ tên từ phiên trước, không né quyền đọc bằng nguồn dữ liệu khác.
4. Request tạo/cập nhật/check-in/chỉ định/thanh toán/cấp thuốc tiếp tục gửi nguyên định danh theo contract; không thay ID bằng tên.
5. Có test phù hợp cho mapping, fallback và session/stale behavior; chạy typecheck/lint và kiểm tra Flutter tương ứng với phần thực sự thay đổi.
6. Nghiệm thu qua Gateway với role thật trên dữ liệu seed; chụp lại những ảnh bị ảnh hưởng rồi thay đúng chỉ mục trong Review 3. Không chỉnh chữ đè lên ảnh cũ để giả trạng thái đã sửa.

## 4. Giới hạn bàn giao

### Tiến độ consumer Huy — 2026-10-22

- Đã triển khai web Pharmacy: tên bệnh nhân/bác sĩ/khoa trên chi tiết đơn; tra tên bên cạnh
  các UUID trong form kê đơn. UUID và nút chép mã vẫn giữ để đối chiếu; không đổi command/mode.
- Đã triển khai web Report: tên khoa cho filter và phạm vi kết quả daily/monthly/top-medicines/
  outpatient. Phạm vi bệnh viện vẫn tường minh, lookup lỗi không đổi số liệu hoặc coverage.
- Mobile Pharmacy có domain reader, adapter public Gateway, provider theo phiên và nhãn tên
  trong chi tiết/form kê đơn. Mobile Report hiện chỉ có `FeatureWorkspace`, chưa có identity
  consumer để thay; không mở thêm workspace nghiệp vụ trong task sửa tên này.
- Dedupe theo resource trong context/phiên, xử lý phản hồi cũ, 403/404/unavailable và đổi tài
  khoản; không import chéo feature hoặc sửa shared paths. Cache tên không dùng chung giữa các phiên.
- Contract đã đối chiếu Core DTO/controller và Gateway role matrix: Patient `maBenhNhan/hoTen`,
  staff `staffId/fullName`, department `departmentId/departmentName`. PHARMACIST chưa được phép
  đọc cả ba public detail API; UI giữ UUID và báo giới hạn, không gọi lookup service-only.
  Gap/decision được gom trong [handoff chung Huy](../../../docs/handoffs/HANDOFF-HUY-MVP.md).
- Đây là **implementation + automated verification**, chưa phải nghiệm thu Gateway/ảnh Review3
  của candidate mới. Không thay inventory/screenshot cũ hoặc đóng handoff của các owner khác.

Các giới hạn bên dưới mô tả phiên tạo tài liệu ban đầu; tiến độ Huy ở trên là cập nhật sau đó.

1. Phiên này chỉ tạo tài liệu giao việc; chưa sửa UI, API, database hay ảnh Review 3.
2. Không kết luận đây là lỗi thiếu tên trong toàn bộ backend. Hai consumer đã xác minh đang render ID trực tiếp; khả năng lookup và quyền từng role cần dev xác nhận.
3. Không tự mở quyền sửa feature của owner khác. Nếu cần thay contract backend, tạo/link handoff backend đúng registry và chỉ rõ endpoint/DTO/role còn thiếu, không nhân bản cùng một gap.
4. Sau khi implementation và nghiệm thu đạt, chuyển quy tắc lâu dài vào tài liệu chuẩn, rồi đóng/xóa handoff cùng entry theo lifecycle của registry.
