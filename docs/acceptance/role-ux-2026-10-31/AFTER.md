# Kết quả sửa UX theo vai trò — 31/10/2026

Nhánh `codex/role-ux-refinements`, dựa trên `5be32324`. Đây là kết quả triển khai các điểm trong [audit trước sửa](README.md), không phải biên bản đã chạy lại trọn ca khám mới.

## Đã sửa và kiểm chứng

| Điểm | Thay đổi | Minh chứng trực tiếp sau sửa |
|---|---|---|
| UX01 | Hai ngày mặc định hôm nay Việt Nam; kiểm tra bắt buộc, ngày thật, thứ tự và giới hạn khoảng trước khi gọi API. Clinical trả 400 INVALID_REQUEST kèm trường khi thiếu query bắt buộc. | [Nurse mặc định](after/01-nurse-defaults.png), [xóa ngày bằng calendar rồi tải: lỗi tại trường](after/02-date-validation.png). Backend thiếu từng/hai ngày kiểm bằng MockMvc cho cả nurse/manager. |
| UX02, UX06 | Trang đầu theo vai trò khi scheduling bật; lịch bác sĩ, phiếu cá nhân, hàng chờ thu tự đọc lần đầu, vẫn có làm mới. Giữ cổng tính năng và ưu tiên đổi mật khẩu bắt buộc. | [Patient](after/03-patient-autoload.png), [cashier](after/06-cashier-queue.png), [doctor](after/07-doctor-autoload.png), [manager](after/11-manager-browse-first.png), [admin](after/13-admin-organization.png). |
| UX03, UX09 | Đưa nút mở bệnh án lên đầu phiếu, giữ ngữ cảnh khi cuộn; triệu chứng/chẩn đoán trước metadata thu gọn. Bác sĩ có lối kê đơn từ hồ sơ ngoại trú OPEN; chỉ chuyển patient/record, vẫn đọc prescribing context từ server và giữ kiểm tra quyền/trạng thái/idempotency hiện có. | [Phiếu](after/08-doctor-patient-context.png), [bệnh án](after/09-doctor-record-content.png), [cuộn](after/10-doctor-sticky-context.png). Lối kê đơn mới kiểm bằng test thành phần, chưa click trên ca OPEN trong phiên này. |
| UX04, UX12 | Thông báo patient dạng nội dung dễ đọc, bỏ nhật ký kỹ thuật và lookup danh bạ bị từ chối; dẫn tới danh sách kết quả khám, lab và phiếu chính chủ. Dùng “điều dưỡng”; nhãn shell theo bệnh nhân. | [Danh sách](after/04-patient-notifications.png), [chi tiết](after/05-patient-detail.png). DTO chưa có nguồn nên chưa thể dẫn chính xác một kết quả; không suy record từ notification ID. |
| UX05 | REPORT_NOT_FOUND thành trạng thái chưa sẵn sàng, giữ phạm vi đã gửi kể cả khi sửa bộ lọc; không thay bằng số 0; giữ 403/5xx là lỗi. | [Readiness](after/12-report-readiness.png). Xem [điều kiện đối soát dữ liệu](../../../scripts/demo_25_10/REPORT_READINESS.md). |
| UX07, UX08 | Chỉ mục điều hướng cụ thể active; picker dùng đủ cột, có viền/nhãn nhất quán; thêm lối thu phí lab/thuốc. | [Cashier](after/06-cashier-queue.png), [manager](after/11-manager-browse-first.png). |
| UX10, UX11 | Danh sách buổi trước, form tạo thu gọn; quyền lập lịch là phần riêng. Tài khoản có người liên kết; chi tiết manager dẫn đúng account sang scope, không ghi quyền khi mở trang. | [Danh sách tài khoản](after/14-admin-linked-profiles.png), [manager được điền sẵn](after/15-admin-manager-scope.png). |
| UX13 | Nhãn nguồn tái khám mô tả ngày lượt nguồn; giữ ngữ cảnh bệnh nhân đã chọn. | Regression tests ReceptionWorkspace/AppointmentDetail; chưa lặp lại tạo tái khám trong phiên sửa. |
| OPS01 | Chịu container biến mất giữa list/inspect, chỉ bỏ qua đúng lỗi thiếu object; giữ ứng viên image trước khi runner dừng, bảo vệ image dùng chung. | 7 kịch bản PowerShell pass; chạy clean-docker.bat thực tế thành công ngay lần đầu. |

## Môi trường và bằng chứng kiểm tra

- Demo mới `snapshot-20261031-154509`, backend READY + FOLLOW-UP SOURCE READY; frontend dev cổng 3000. Thử đăng nhập/read/search/filter bằng Edge cho nurse, patient2, cashier, doctor, manager, admin. Không ghi hồ sơ/đơn/thanh toán/scope trong vòng này.
- 15 ảnh sau sửa ở `after/`, Edge toàn màn hình 1536×864 để bỏ thanh trình duyệt cá nhân. 35 ảnh trước sửa trong `screenshots/` chỉ giữ cục bộ và được gitignore. Không so pixel giữa hai kích thước khác nhau.
- Frontend: 659 tests / 60 files pass cho các thành phần thay đổi và prescription; thêm 4 tests route kê đơn pass. TypeScript, ESLint, production build pass. Một số test vẫn in React act warnings; không coi đây là đo hiệu năng UX.
- Clinical: full reactor test suite pass; báo cáo Surefire sau bổ sung có 453 tests, 0 fail/error/skip; focused GlobalExceptionHandler 13 tests pass. Notification PatientResultNotice 2 tests pass.
- Cleanup: đã đăng xuất, dừng frontend do phiên này khởi động, chạy clean-docker.bat exit 0. Không còn container hoặc listener demo ở 3000/28080/28761/28089/28082/28084/28085/28086/28087. Hai image demo postgres/rabbitmq đã gỡ; image công cụ MCP và Testcontainers Ryuk giữ nguyên, không prune toàn máy hoặc xóa volume.

## Các phần còn cần nghiệm thu thực tế

- Chạy lại một bệnh nhân mới xuyên suốt tiếp nhận → khám → thu tiền → lab → nhận kết quả → thuốc. Phiên này chỉ xác nhận các thao tác đọc và bố cục đã nêu, không thay thế ca nghiệp vụ đó.
- Mobile, bàn phím/screen reader toàn luồng, locale vi-VN/en-US, tên dài, chuyển nhanh nhiều bệnh nhân và hiệu năng production chưa được nghiệm thu đầy đủ. Không có số đo tải/Core Web Vitals.
- Manager vẫn chọn khoa qua picker có kiểm quyền server; chưa có dashboard mức tải tất cả khoa được cấp. Không suy quyền từ khoa công tác.
- Báo cáo hôm nay chưa thể có accepted coverage trọn ngày trước khi ngày đóng. Cần năm nguồn đối soát và ADMIN acceptance đúng quy trình; không tự xác nhận số liệu demo.
