# Theo dõi hợp đồng sự kiện sau khi tích hợp theloc

Ngày review: 2026-09-08. Phạm vi: hai commit `bb8da17` (Billing) và
`1a60888` (Notification), phần application DTO/port/mapper/event (2/5).

Maintainer đồng ý tích hợp với các việc còn mở dưới đây. Tài liệu này là lời
nhắc, không thay thế đặc tả và không khẳng định các luồng liên-service đã chạy
đúng. Chưa tái hiện lỗi runtime; các điểm dưới đây là chênh lệch hợp đồng.

## 1. Billing ↔ Clinical: recordId của phí khám

- File: `src/main/java/com/mediflow/billing/application/event/AppointmentStatusChangedEvent.java`, trường `recordId`.
- Consumer dự kiến dùng `recordId` làm `sourceRefId` khi lịch khám ARRIVED.
- [Clinical §Events](../../docs/eproject_general_plan/backend-spec/03-clinical.md#9-events)
  và [thiết kế Clinical](../../docs/eproject_general_plan/clinical-service.html)
  khai báo `appointment.status.changed` chỉ có envelope, appointmentId, status,
  patientId, departmentId; không có recordId.
- [Billing §7, §10](../../docs/eproject_general_plan/backend-spec/06-billing.md)
  và [thiết kế Billing](../../docs/eproject_general_plan/billing-service.html)
  yêu cầu phí EXAM không trùng theo nguồn (BR-B7), kể cả khi nhận sự kiện tạo hồ sơ.
- Rủi ro: nếu xử lý dựa trên trường không được gửi, khóa chống trùng sẽ thiếu;
  tùy consumer có thể từ chối xử lý hoặc tạo phí không được bảo vệ đúng.

Việc cần phối hợp (Billing + Clinical):

- [ ] Thống nhất cách xác định hồ sơ tại thời điểm ARRIVED. Không tự giả định
  recordId luôn tồn tại, không đổi sang appointmentId mà bỏ qua chống trùng
  với `medicalrecord.created`.
- [ ] Chọn và ghi rõ hợp đồng: bổ sung dữ liệu ở publisher khi hợp lệ hoặc lấy
  dữ liệu qua API/port được thống nhất; không truy cập DB service khác.
- [ ] Đồng bộ thiết kế HTML, backend-spec, publisher và consumer trong cùng thay đổi.
- [ ] Test JSON thật từ publisher, thiếu recordId, phát lại cùng eventId,
  và hai sự kiện ARRIVED / medicalrecord.created cho cùng hồ sơ chỉ tạo một phí EXAM.

## 2. Billing ↔ Lab: labType để tra giá

- File: `src/main/java/com/mediflow/billing/application/event/LabResultCreatedEvent.java`, trường `labType`.
- Billing cần `PriceListPort.labFee(labType)` để sinh phí LAB, sourceRefId = labId
  ([Billing §7](../../docs/eproject_general_plan/backend-spec/06-billing.md)).
- [Lab §Events](../../docs/eproject_general_plan/backend-spec/04-lab.md)
  và [thiết kế Lab](../../docs/eproject_general_plan/lab-service.html) khai báo
  `lab.result.created` có labId, patientId, recordId, departmentId, results,
  conclusion; không có labType. `lab.request.created` có labType nhưng là sự kiện khác.
- Rủi ro: không đủ dữ liệu tra giá khi nhận payload đúng đặc tả bên Lab.

Việc cần phối hợp (Billing + Lab):

- [ ] Thống nhất bổ sung labType vào sự kiện kết quả hoặc cơ chế lấy loại xét
  nghiệm qua API/port; không tự đặt giá mặc định khi thiếu dữ liệu.
- [ ] Đồng bộ đặc tả hai bên, publisher và consumer; xác định xử lý payload cũ.
- [ ] Test deserialize JSON publisher, loại xét nghiệm hợp lệ/thiếu/không có giá,
  giá phí đúng và phát lại sự kiện chỉ sinh một phí LAB theo BR-B7.

## 3. Kiểm tra bổ sung cho phần việc của theloc

- [ ] Thêm test `RevenueMapper` cho ánh xạ khoa, tổng tiền và số hóa đơn.
- [ ] Khi hoàn thiện consumer Notification, kiểm tra payload thực tế của các
  publisher theo [Notification spec](../../docs/eproject_general_plan/backend-spec/07-notification.md)
  và [thiết kế Notification](../../docs/eproject_general_plan/notification-service.html);
  không hiểu việc merge phần 2/5 là đã hoàn thành toàn bộ nghiệp vụ.
- [ ] Đính kèm kết quả contract/integration test và commit sửa vào tài liệu này
  trước khi đánh dấu các mục đã xong.

Quy tắc áp dụng: [Events/RabbitMQ](../../docs/ai/06-events-rabbitmq.md)
(payload theo thiết kế, consumer idempotent), [Architecture](../../docs/ai/01-architecture.md)
(không đọc DB xuyên service), [Testing](../../docs/ai/09-testing.md).
Nếu các nguồn khác nhau, thống nhất và cập nhật đặc tả; không chỉ sửa DTO của Billing.
