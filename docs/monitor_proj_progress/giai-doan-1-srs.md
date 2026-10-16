# Giai đoạn 1 — Software Requirements Specification (bản phục hồi để chốt)

**Phiên bản:** 0.9-draft  
**Ngày phục hồi:** 02/09/2026  
**Trạng thái:** Bản nháp tổng hợp từ design/backend spec; chưa thay thế SRS v1.0 đã duyệt.  
**Owner phê duyệt:** Phạm Đăng Vinh (Leader)  
**Reviewer:** Trần Hoàng Anh, Lê Quang Huy, Nguyễn Hoàng Phúc

## 1. Mục đích và bài toán

MediFlow là hệ thống quản lý bệnh viện/phòng khám phân tán, hỗ trợ quản lý tổ chức, bệnh nhân, lịch hẹn, hồ sơ bệnh án, xét nghiệm, dược, viện phí, thông báo và báo cáo. Hệ thống phải tách quyền sở hữu dữ liệu theo bounded context, giao tiếp qua API gateway và RabbitMQ, đồng thời theo dõi quy trình khám–xét nghiệm–kê đơn–thanh toán–cấp thuốc.

## 2. Phạm vi

### Trong phạm vi v1

- Web Next.js và Mobile Flutter truy cập duy nhất qua gateway.
- 8 business service: organization, patient, clinical, lab, pharmacy, billing, notification, report.
- Eureka service discovery, PostgreSQL riêng từng service, RabbitMQ topic exchange.
- JWT authentication, RBAC, audit/correlation cơ bản.
- Saga thanh toán–cấp thuốc có bù trừ.

### Ngoài phạm vi hoặc chưa được chốt

- Tích hợp bảo hiểm/y tế quốc gia, thanh toán ngân hàng thật, SMS/email production.
- PACS/DICOM, thiết bị xét nghiệm, chữ ký số, multi-tenant và disaster-recovery đa vùng.
- Chỉ tiêu SLA/SLO production chưa được phê duyệt.

## 3. Tác nhân

| Actor | Trách nhiệm chính |
|---|---|
| `ADMIN` | Quản trị tổ chức, nhân sự, tài khoản và toàn bộ nghiệp vụ được cấp quyền |
| `DOCTOR` | Tra cứu bệnh nhân, khám, hồ sơ, chẩn đoán, chỉ định xét nghiệm, kê đơn |
| `NURSE` | Tiếp nhận bệnh nhân, tạo/cập nhật lịch hẹn, hỗ trợ hồ sơ và thông báo |
| `PHARMACIST` | Quản lý thuốc, tồn kho và xuất thuốc |
| `CASHIER` | Tạo, tra cứu và thanh toán hóa đơn |
| `LAB_TECH` | Tiếp nhận, cập nhật trạng thái và nhập kết quả xét nghiệm |
| `MANAGER` | Xem hoạt động khoa, doanh thu và báo cáo |
| `PATIENT` | Xem tài nguyên của chính mình, đặc biệt thông báo |
| `SYSTEM` | Gọi nội bộ cho xác thực/thông báo và tác vụ event-driven |

> Quyết định cần chốt: thay thế hoàn toàn ba actor cũ `Administrator/Customer/Staff-Expert` bằng 9 role trên, hay giữ chúng như nhóm actor cấp cao. Khuyến nghị: dùng 9 role làm RBAC, gom thành 4 nhóm trình bày: quản trị, chuyên môn, bệnh nhân, hệ thống.

## 4. Danh sách Use Case

### Xác thực và truy cập

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-AUTH-01 | Đăng nhập | Tất cả người dùng | Nhận access token, refresh token và role |
| UC-AUTH-02 | Làm mới access token | Người dùng đã xác thực | Nhận access token mới |

### Tổ chức và tài khoản

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-ORG-01 | Tra cứu khoa/phòng | ADMIN, MANAGER, DOCTOR, NURSE | Danh sách/chi tiết khoa |
| UC-ORG-02 | Tạo/cập nhật khoa | ADMIN | Khoa được lưu và phát event |
| UC-ORG-03 | Tra cứu nhân viên | ADMIN, MANAGER, DOCTOR, NURSE | Danh sách/chi tiết nhân viên |
| UC-ORG-04 | Tạo/cập nhật/chuyển khoa nhân viên | ADMIN | Hồ sơ nhân sự và khoa công tác được cập nhật |
| UC-ORG-05 | Tạo/kích hoạt-vô hiệu tài khoản | ADMIN | Tài khoản có role hợp lệ |
| UC-ORG-06 | Xác minh thông tin đăng nhập nội bộ | SYSTEM | Trả tài khoản đã xác minh cho gateway |

### Bệnh nhân

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-PAT-01 | Tìm kiếm/xem bệnh nhân | ADMIN, DOCTOR, NURSE | Danh sách phân trang hoặc chi tiết |
| UC-PAT-02 | Tiếp nhận bệnh nhân mới | ADMIN, NURSE | Bệnh nhân được tạo, CMND không trùng |
| UC-PAT-03 | Cập nhật bệnh nhân | ADMIN, NURSE | Thông tin hợp lệ được cập nhật |
| UC-PAT-04 | Xóa bệnh nhân | ADMIN | Bệnh nhân được xóa theo chính sách dữ liệu đã chốt |

### Khám bệnh và hồ sơ

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-CLI-01 | Tạo lịch hẹn | ADMIN, NURSE | Lịch hợp lệ được tạo |
| UC-CLI-02 | Tra cứu lịch hẹn | ADMIN, MANAGER, DOCTOR, NURSE | Danh sách/chi tiết theo bệnh nhân, khoa, ngày |
| UC-CLI-03 | Cập nhật lịch và trạng thái | ADMIN, DOCTOR, NURSE | Lịch chuyển trạng thái hợp lệ |
| UC-CLI-04 | Tạo/cập nhật hồ sơ bệnh án | ADMIN, DOCTOR | Hồ sơ gắn bệnh nhân, bác sĩ, khoa và lịch hẹn |
| UC-CLI-05 | Tra cứu lịch sử hồ sơ | ADMIN, DOCTOR, NURSE | Danh sách/chi tiết hồ sơ |
| UC-CLI-06 | Thêm chẩn đoán | ADMIN, DOCTOR | Chẩn đoán được gắn vào hồ sơ |

### Xét nghiệm

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-LAB-01 | Tạo chỉ định xét nghiệm | ADMIN, DOCTOR | Yêu cầu xét nghiệm được tạo |
| UC-LAB-02 | Tra cứu xét nghiệm | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH | Danh sách/chi tiết đúng phạm vi role |
| UC-LAB-03 | Cập nhật trạng thái xét nghiệm | ADMIN, LAB_TECH | Chuyển trạng thái hợp lệ |
| UC-LAB-04 | Nhập kết quả xét nghiệm | ADMIN, LAB_TECH | Kết quả được lưu và phát event |

### Dược

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-PHA-01 | Tra cứu danh mục thuốc | ADMIN, DOCTOR, PHARMACIST | Danh sách/chi tiết thuốc |
| UC-PHA-02 | Tạo thuốc và điều chỉnh tồn | ADMIN, PHARMACIST | Danh mục/tồn kho được cập nhật |
| UC-PHA-03 | Kê đơn | ADMIN, DOCTOR | Đơn có ít nhất một dòng, tổng tiền tính từ snapshot giá |
| UC-PHA-04 | Xem đơn thuốc | ADMIN, DOCTOR, PHARMACIST | Chi tiết đơn và trạng thái phiếu xuất |
| UC-PHA-05 | Xuất thuốc | ADMIN, PHARMACIST, SYSTEM qua event | Trừ kho nguyên tử; thành công hoặc phát event thất bại |

### Viện phí và saga

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-BIL-01 | Tạo hóa đơn từ khoản phí chưa trả | ADMIN, CASHIER | Hóa đơn tổng hợp đúng số tiền |
| UC-BIL-02 | Tra cứu hóa đơn bệnh nhân | ADMIN, CASHIER | Danh sách/chi tiết hóa đơn |
| UC-BIL-03 | Thanh toán hóa đơn | ADMIN, CASHIER | Hóa đơn chuyển đã trả và phát `payment.completed` |
| UC-BIL-04 | Xem doanh thu theo khoa/thời gian | ADMIN, MANAGER | Tổng hợp doanh thu |
| UC-BIL-05 | Điều phối/bù trừ saga | SYSTEM | Invoice hoàn tất hoặc hoàn tiền, phát `payment.failed` khi cần |

### Thông báo và báo cáo

| ID | Use Case | Actor | Kết quả |
|---|---|---|---|
| UC-NOT-01 | Xem thông báo của bệnh nhân | ADMIN, NURSE, PATIENT | Chỉ trả dữ liệu đúng quyền sở hữu |
| UC-NOT-02 | Gửi thông báo chủ động | ADMIN, SYSTEM | Lưu lịch sử và gửi qua kênh được chọn |
| UC-NOT-03 | Tạo thông báo từ event | SYSTEM | Consumer chống trùng tạo đúng một thông báo |
| UC-REP-01 | Xem báo cáo khám theo ngày | ADMIN, MANAGER | Báo cáo toàn viện hoặc theo khoa |
| UC-REP-02 | Xem doanh thu tháng | ADMIN, MANAGER | Báo cáo toàn viện hoặc theo khoa |
| UC-REP-03 | Xem top thuốc | ADMIN, MANAGER | Danh sách theo khoảng ngày/khoa/giới hạn |

## 5. Yêu cầu phi chức năng cho hệ thống phân tán

| ID | Nhóm | Yêu cầu có thể kiểm thử | Trạng thái chốt |
|---|---|---|---|
| NFR-SEC-01 | Xác thực | Mọi API trừ login/refresh/health yêu cầu JWT hợp lệ | Đã đặc tả |
| NFR-SEC-02 | Phân quyền | Mỗi endpoint có `@PreAuthorize`; role không liệt kê bị 403; PATIENT chỉ xem dữ liệu của mình | Đã đặc tả, chưa triển khai |
| NFR-SEC-03 | Bảo vệ dữ liệu | Không log token, mật khẩu hoặc PII đầy đủ; secret lấy từ môi trường | Đã đặc tả |
| NFR-SEC-04 | Mật khẩu | Tài khoản thật phải dùng password hash; gateway stub không được dùng ngoài dev | Đã đặc tả, chưa triển khai |
| NFR-PERF-01 | Rate limit | Gateway giới hạn 100 request/phút/client IP | Đã có số, chưa triển khai |
| NFR-PERF-02 | Timeout | REST chéo service: connect timeout 2 giây, read timeout 3 giây | Đã đặc tả cho clinical |
| NFR-PERF-03 | Phân trang | Mặc định 20, tối đa 100 record/trang | Đã đặc tả |
| NFR-PERF-04 | Response SLO | Phải chốt p95/p99 theo nhóm endpoint và tải đồng thời | **Chưa chốt số** |
| NFR-AVL-01 | Chịu lỗi | REST chéo service dùng circuit breaker và fallback không làm sập dây chuyền | Đã đặc tả, chưa triển khai |
| NFR-AVL-02 | Messaging | Queue/exchange durable; poison message đi DLQ | Đã đặc tả, chưa triển khai |
| NFR-AVL-03 | Availability | Phải chốt uptime mục tiêu, cửa sổ bảo trì, RTO và RPO | **Chưa chốt số** |
| NFR-CON-01 | Dữ liệu | Mỗi service sở hữu database riêng; cấm join/FK xuyên service | Đã chốt thiết kế |
| NFR-CON-02 | Event | Consumer idempotent theo `eventId`; publish sau commit; saga có bù trừ | Đã đặc tả, chưa triển khai |
| NFR-CON-03 | Tương tranh | Xuất kho dùng khóa ghi; không để tồn kho âm hoặc xuất trùng | Đã đặc tả, mới có repository lock |
| NFR-OBS-01 | Truy vết | Mọi request/event mang `X-Correlation-Id`; log có correlation id | Đã đặc tả, chưa hoàn chỉnh |
| NFR-OBS-02 | Sức khỏe | Mỗi service có `/actuator/health`; gateway/service discovery giám sát instance | Đã có cấu hình nền |
| NFR-OBS-03 | Logging/audit | Phải chốt trường audit, thời gian giữ log và cảnh báo | **Chưa chốt số/chính sách** |
| NFR-COMP-01 | Tương thích | API version `/api/v1`; breaking change chuyển `/api/v2`; response theo envelope chung | Đã đặc tả |
| NFR-TEST-01 | Chất lượng | Mỗi business rule và failure path có test; consumer có test gửi trùng; endpoint có security test | Đã đặc tả, chưa đạt |
| NFR-UX-01 | Khả dụng giao diện | Web/Mobile có loading, error, empty state; tương phản và thao tác rõ ràng cho môi trường lâm sàng | Đã đặc tả định tính |
| NFR-UX-02 | Accessibility | Phải chốt chuẩn WCAG, thiết bị/màn hình hỗ trợ và ngôn ngữ mặc định | **Chưa chốt** |

## 6. Hợp đồng kiến trúc

- Client → gateway: REST `/api/v1/*`.
- Service cần dữ liệu để hoàn tất request → REST qua Eureka + timeout/circuit breaker.
- Service thông báo thay đổi trạng thái → event qua `mediflow.events`.
- Không dùng event để cập nhật database của chính service phát event.
- Billing điều phối saga kê đơn → thanh toán → xuất thuốc → hoàn tất/hoàn tiền.

## 7. Tiêu chí để nâng lên SRS v1.0

- [ ] Chốt roster và 9 role/4 nhóm actor.
- [ ] Review 38 Use Case; bổ sung precondition, main flow, alternate flow cho các UC rủi ro cao.
- [ ] Chốt quyền xóa bệnh nhân và chính sách lưu giữ hồ sơ y tế.
- [ ] Chốt response SLO, tải đồng thời, uptime, RTO/RPO, log retention và WCAG.
- [ ] Đồng bộ tên bảng/field/event giữa SRS, ERD, backend spec và API.
- [ ] Gán reviewer và ghi biên bản phê duyệt có ngày/phiên bản.
