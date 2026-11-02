# Review UI/UX và độ trưởng thành sản phẩm ngoại trú

Đọc [bài review đầy đủ](REVIEW_UI_UX_NGOAI_TRU.md): phán quyết khách hàng, đánh giá theo từng role, bố cục/màu sắc, bàn giao liên quầy, microservices, đối chiếu sản phẩm thực tế, 30 mục backlog và tiêu chí nghiệm thu.

Đợt sửa tiếp theo được theo dõi riêng tại [Tiến độ triển khai](IMPLEMENTATION_STATUS.md); ảnh review bên dưới giữ nguyên thời điểm trước khi sửa.

Thời điểm: tối 01/11 đến rạng sáng 02/11/2026, giờ Việt Nam. Source `f3850620`, nền `7e6a21d7`. Ảnh là dữ liệu tổng hợp trong môi trường demo đã được dọn sau review. Không có dữ liệu bệnh nhân thật.

**Đoạn mới chạy được liên tục:** Nurse đăng ký trước Nguyễn Văn An Demo ngày 02/11 → Cashier nhận hàng chờ và thu 50.000 VND mô phỏng → Doctor thấy phiếu đã xác nhận/chưa đến → Patient thấy phiếu và thông báo đúng lượt. Không coi các form/hồ sơ lịch sử còn lại là cùng một hành trình mới đã hoàn tất.

## Chỉ mục ảnh

Ảnh `fullPage` có thể dài hơn vùng nhìn thực tế. Ảnh viewport là cơ sở chính đánh giá mật độ và thứ tự hiển thị. Không suy ra hiệu năng từ thời gian điều khiển trình duyệt.

| Ảnh | Nội dung / giới hạn |
|---|---|
| [01](screenshots/01-startup-unavailable.jpg) | Backend chưa sẵn sàng; chỉ minh họa trạng thái khởi động, không tính là lỗi vận hành |
| [02](screenshots/02-login.jpg) | Đăng nhập, tài khoản demo đã autofill |
| [03](screenshots/03-nurse-reception.jpg) | Landing nurse, chọn khoa/ngày |
| [04](screenshots/04-nurse-registration-full.jpg) | Buổi và form đăng ký, toàn trang |
| [05](screenshots/05-nurse-registration-viewport.jpg) | Vùng nhập đăng ký trong viewport |
| [06](screenshots/06-nurse-handoff.jpg) | Đăng ký thành công cùng danh sách cũ “chưa có phiếu” |
| [07](screenshots/07-registration-detail.jpg) | Chi tiết phiếu đặt trước vừa tạo |
| [08](screenshots/08-patient-directory.jpg) | Danh sách patient tại quầy |
| [09](screenshots/09-new-patient.jpg) | Form patient mới và quy ước BHYT hiện tại |
| [10](screenshots/10-cashier-queue.jpg) | Phiếu nurse mới tạo xuất hiện trong hàng thu |
| [11](screenshots/11-cashier-exam-quote.jpg) | Chi tiết khoản thu và báo giá mô phỏng |
| [12](screenshots/12-cashier-paid.jpg) | Thu mẫu được ghi nhận, hàng chờ đã bỏ khoản hoàn tất |
| [13](screenshots/13-cashier-lab-rx-lookup.jpg) | Trang menu thu Lab/Rx mặc định loại Khám |
| [14](screenshots/14-cashier-lab-fields.jpg) | Các mã phải nhập/chọn để thu Lab |
| [15](screenshots/15-invoice-list.jpg) | Tra cứu hóa đơn và nghĩa vụ hoàn tiền |
| [16](screenshots/16-doctor-calendar.jpg) | Lịch doctor ngày 02/11 với 1 phiếu đã xác nhận |
| [17](screenshots/17-doctor-session-patients.jpg) | Patient mới đăng ký hiện chưa đến, số 1 |
| [18](screenshots/18-doctor-record.jpg) | Hồ sơ lịch sử mẫu, không thuộc phiếu mới của review |
| [19](screenshots/19-doctor-lab-order.jpg) | Form loại Lab/mã giá và cảnh báo hai bước |
| [20](screenshots/20-doctor-prescription.jpg) | Kê đơn từ hồ sơ seed, đã mang theo patient/record |
| [21](screenshots/21-lab-queue.jpg) | LAB_TECH: hàng đợi, cột trạng thái, bộ lọc khoa không có quyền tìm |
| [22](screenshots/22-lab-detail.jpg) | Chi tiết Lab seed đang thực hiện |
| [23](screenshots/23-lab-result-entry.jpg) | Mở form nhập kết quả, chưa lưu |
| [24](screenshots/24-pharmacist-landing.jpg) | Dược sĩ mặc định vào danh mục thuốc |
| [25](screenshots/25-pharmacy-prescriptions.jpg) | Hàng đơn thuốc, gồm lịch sử |
| [26](screenshots/26-pharmacy-prescription-detail.jpg) | Đơn seed có tên patient, thiếu label khoa/bác sĩ theo quyền UI |
| [27](screenshots/27-pharmacy-dispense-confirm.jpg) | Bước xác nhận chỉ có tiền/số dòng, chưa xác nhận xuất |
| [28](screenshots/28-patient-registration.jpg) | Patient đọc đúng phiếu vừa tạo và hướng dẫn đến quầy |
| [29](screenshots/29-patient-exam-results.jpg) | Patient chưa có kết quả khám hoàn tất |
| [30](screenshots/30-patient-lab-results.jpg) | Patient chưa có kết quả Lab hoàn tất |
| [31](screenshots/31-patient-notifications.jpg) | Thông báo từ chính đăng ký/thu khám vừa làm |
| [32](screenshots/32-manager-landing.jpg) | Landing MANAGER là form quản lý buổi |
| [33](screenshots/33-manager-scope-result.jpg) | Chọn Nội nhận lỗi chưa được giao khoa |
| [34](screenshots/34-reports-hub.jpg) | Báo cáo cũ có cảnh báo giới hạn nguồn |
| [35](screenshots/35-outpatient-report.jpg) | Form báo cáo ngoại trú khi ở role manager |
| [36](screenshots/36-admin-organization.jpg) | Quản trị khoa/phòng/nhân sự |
| [37](screenshots/37-admin-head-appointment.jpg) | Chi tiết khoa và form bổ nhiệm nằm sau danh sách nhân sự |
| [38](screenshots/38-admin-appointment-preview.jpg) | Admin xem trước bổ nhiệm; không xác nhận quyền |
| [39](screenshots/39-admin-accounts.jpg) | Danh sách account, hồ sơ liên kết và trạng thái |
| [40](screenshots/40-admin-account-create.jpg) | Form account, role tự xác định, mật khẩu demo ban đầu |
| [41](screenshots/41-admin-manager-scope.jpg) | Tài khoản manager và link tới nơi quản trị scope |
| [42](screenshots/42-admin-scope-editor.jpg) | Tải phạm vi: revision 0, 0 khoa; không thay đổi quyền |
| [43](screenshots/43-outpatient-report-result.jpg) | Báo cáo ngày hiện tại chưa đủ điều kiện, không trả số 0 |
| [44](screenshots/44-dark-report.jpg) | Ảnh bổ sung ngay sau đổi theme; chưa dùng để chứng nhận contrast |
| [45](screenshots/45-admin-staff-create.jpg) | Form định danh/chức danh/khoa/giấy phép nhân sự |
| [46](screenshots/46-manager-hospital-report.jpg) | Manager không scope vẫn nhận báo cáo cũ toàn viện tháng 6/2025; số liệu hiện 0, không chứng minh có doanh thu thật bị lộ |

## Kiểm chứng ngoài UI

- Source đã đọc cho các phát hiện quan trọng được liệt kê tại mục 22 của bài review.
- Tham chiếu sản phẩm/chuẩn là tài liệu chính thức công khai, có link ngay tại phần nhận định; không coi đây là benchmark dùng thử đối thủ.
- Kiểm tra link nội bộ, file ảnh mở được, nội dung phân biệt UI/SRC/OLD/ĐX và giới hạn phạm vi.
- Môi trường đã dừng và chạy `clean-docker.bat` thành công; kiểm tra không còn listener 3000/28080 hoặc container chạy sau đó.
- Chỉ thêm tài liệu/ảnh review, không thay đổi source ứng dụng và không chạy lại test ứng dụng để tuyên bố nghiệm thu những nhánh chưa thao tác.
