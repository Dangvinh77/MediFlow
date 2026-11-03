# Mobile web acceptance — 03/11/2026

Candidate: working tree MOB-02/MOB-03 trên baseline `2e3fa734`. Owner: Vinh.
Đây là nghiệm thu một phần trên Flutter web, không phải chứng nhận hoàn thành toàn bộ mobile.

## Môi trường

- UI: http://localhost:4300, viewport 390 × 844.
- Gateway: http://127.0.0.1:28080, qua proxy của `mobile/tool/dev_server.dart`.
- Backend build từ source hiện tại bằng Maven package, gồm Eureka/Gateway và sáu business apps.
- Runtime dùng `scripts/demo_25_10/rehearse_snapshot.py --scheduling --keep`, PostgreSQL/RabbitMQ
  riêng, dữ liệu tổng hợp của snapshot. Không sử dụng dữ liệu bệnh nhân thật, không sửa DB trực tiếp.
- Manifest/hash: [environment.json](environment.json). Runtime tự dừng sau thời hạn rehearsal
  khoảng hai giờ từ 18:25 giờ địa phương; sau đó cần khởi động lại môi trường.
- Backend snapshot evidence: `scripts/demo_25_10/evidence/snapshot-20261103-182514`.
- Gateway smoke evidence: `scripts/demo_25_10/evidence/gateway-20261103-182949`.

## Kết quả qua giao diện

| Ca | Kết quả | Bằng chứng |
|---|---|---|
| Login bỏ trống | Hiện validation hai trường | Quan sát UI |
| Gateway chưa chạy | Hiện lỗi kết nối, cho phép thử lại | gateway-unavailable.png |
| Login sai mật khẩu | Hiện lỗi xác thực từ backend | invalid-login.png |
| Login PATIENT | Thành công, menu theo role | Quan sát UI |
| Kết quả khám của demo.patient | Empty đúng dữ liệu mine, phân trang 1/1 bị khóa | patient-exams-empty.png |
| Kết quả Lab của demo.patient | Empty đúng dữ liệu mine | patient-lab-empty.png |
| Reload trang PATIENT | Giữ phiên và trang kết quả Lab | Quan sát UI |
| PATIENT mở /patients | Bị chuyển /forbidden | patient-staff-route-denied.png |
| Đăng xuất | Trở về login | Quan sát UI |
| ADMIN mở /my-results/exams | Bị chuyển /forbidden | staff-patient-route-denied.png |
| Organization | Tải danh sách khoa thật | organization.png |
| Patient | Tải danh sách thật, có phân trang | patients-list.png |
| Appointment | Tải danh sách và nhãn bệnh nhân | appointments.png |
| Lab staff | Mở chi tiết CBC hoàn tất, kết quả DEMO_INDEX 7.2 demo | lab-detail.png |
| Pharmacy | Tải danh mục và tồn kho từ backend | pharmacy.png |
| Billing | Mở form tra cứu và các tab; chưa thực hiện thu/hoàn tiền | billing-form.png |
| Notification | Mở form chọn bệnh nhân; chưa nghiệm thu gửi/lịch sử | notification-form.png |
| Report | Tra cứu ngày 2026-11-03, nhận báo cáo 0 từ backend | report-daily.png |
| Tài khoản lần đầu | Login chuyển bắt buộc sang /account/password | mandatory-password-change.png |
| Deep link trước đổi mật khẩu | /my-results/exams quay lại /account/password | mandatory-password-change.png |

Ảnh chụp từ cùng viewport; chỉ chứa dữ liệu tổng hợp. Báo cáo ngày 0 không chứng minh
aggregation đúng ở ca có phát sinh. Billing/Notification mới là kiểm tra mở màn hình.

## Kiểm tra API bổ sung

`Smoke-Gateway.ps1` PASS: đăng nhập ADMIN và bảy GET routes (Patient, Clinical, Lab,
Pharmacy, Billing, Notification, Report) qua Gateway. Đây là API smoke riêng, không thay thế UI.
Hai tài khoản PATIENT có sẵn trả mine success với 0 items; xem [patient-api-baseline.json](patient-api-baseline.json).
Không dùng empty list để kết luận đã kiểm được tính tách biệt hai bộ kết quả có dữ liệu.

## Bước còn chờ người dùng

Đã cấp tài khoản PATIENT cho fixture tổng hợp lịch sử có một kết quả khám và một Lab hoàn tất
bằng API NURSE hợp lệ. Metadata: [completed-fixture-account.json](completed-fixture-account.json).
Đây là ca `EMERGENCY_DEMO_COMPLETED` có sẵn; không tính là một lượt khám thanh toán mới.
Một tài khoản tổng hợp trống khác cũng được tạo trong quá trình chuẩn bị; xem initial-account.json.
Không lưu password/token trong evidence.

Tab đang ở màn bắt buộc đổi mật khẩu của fixture có dữ liệu. Người dùng cần tự chọn,
xác nhận, gửi mật khẩu mới và đăng nhập lại. Computer Use yêu cầu handoff ở bước đổi credential;
agent chưa nhập mật khẩu mới và chưa submit. Sau đó mới kiểm tra:

1. Đổi mật khẩu thành công và phiên cũ bị kết thúc.
2. Đăng nhập lại không còn bị mandatory redirect.
3. PATIENT xem đủ kết quả khám và Lab, nội dung đúng fixture.
4. Đăng xuất/chuyển tài khoản không để lộ dữ liệu của phiên trước.

## Chưa nghiệm thu

- Mutation nghiệp vụ, toàn bộ role, mất phản hồi khi mutation, expiry/401 thực tế.
- Luồng ngoại trú mới xuyên suốt tiếp nhận → thu phí → khám → Lab → thuốc → kết thúc.
- Scheduling V2 và các task MOB-04–10 còn mở trong workboard.
- Native Android/iOS, signing và bản phát hành. Web acceptance không đóng các mục này.

Check trước runtime đã PASS: format, analyze, 369 tests, web build. Đợt runtime này chỉ thêm
bằng chứng/tài liệu, chưa thay đổi production code.
