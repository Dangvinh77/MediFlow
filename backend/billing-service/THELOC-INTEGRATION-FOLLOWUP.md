> Integration update (2026-09-08): the historical analysis below predates the clinical/lab
> foundation contract. This update supersedes the earlier recommendations for sections 1 and 2.
>
> - Clinical status carries nullable `recordId`; ARRIVED and medicalrecord.created share EXAM
>   deduplication by recordId. A legacy ARRIVED without recordId relies on medicalrecord.created.
> - Lab results carry `labType` and `performedDate`. Billing prefers these fields; legacy
>   projection misses throw without marking the event processed, preserving retries.
> - Ordinary invoice creation excludes fees already assigned to another invoice. Persistence
>   adapters must also lock/reserve the selected fees to prevent concurrent allocation.
> - Payment events carry invoiceId as a stable correlation; prescriptionId is null for ordinary
>   invoices, which Pharmacy filters before constructing its dispensing command.
> - Notification list reads now require JWT-derived callerPatientId/isStaff, like getById.
>
> Runtime adapters/outbox, dispenseId enrichment and per-department revenue breakdown remain
> later milestones; this integration does not claim a production-ready distributed saga.

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

---

## Cập nhật Phần 3/5 — 2026-09-08 (theloc)

Đã đối chiếu tài liệu bên phát (`03-clinical.md` §9, `04-lab.md` §8) và chỉnh
record event của billing cho khớp payload thật. Xử lý ở phía billing, không ép
service khác đổi:

### Mục 1 — `appointment.status.changed` không có `recordId`
- `AppointmentStatusChangedEvent` bỏ trường `recordId`, khai đúng payload thật
  (`appointmentId, status, patientId, departmentId`).
- `FeeAccrualService.onAppointmentStatusChanged`: **V1 không sinh phí** từ event
  này (chỉ ghi sổ đã xử lý). Phí EXAM sinh dứt điểm từ `medicalrecord.created`
  (`sourceRefId = recordId`, đủ khóa chống trùng BR-B7). Không đổi sang
  `appointmentId` để tránh tính tiền hai lần khi chưa có id chung giữa hai event.
- [ ] CÒN MỞ (cần clinical): nếu sau này muốn tính phí ngay lúc ARRIVED, clinical
  bổ sung `appointmentId` vào `medicalrecord.created` (hoặc `recordId` vào
  `appointment.status.changed`) để billing chống trùng chéo. Cập nhật đồng bộ 2
  spec + publisher + consumer.
- Test: `FeeAccrualServiceTest.onAppointmentStatusChanged_arrived_createsNoFeeButMarksProcessed`.

### Mục 2 — `lab.result.created` không có `labType`
- `LabResultCreatedEvent` bỏ `labType`, khai đúng payload thật
  (`labId, patientId, recordId, departmentId`).
- Thêm out-port `LabTestTypePort` (`Optional<String> labType(UUID labId)`).
  `FeeAccrualService.onLabResultCreated`: tra `labType` qua port; **rỗng thì bỏ
  qua sinh phí** (không đặt giá mặc định), vẫn ghi sổ đã xử lý.
- [ ] CÒN MỞ (Phần 4-5/5): adapter của `LabTestTypePort` = bảng chiếu local nạp
  từ `lab.request.created` (payload này CÓ `labType`, `04-lab.md` §8). Billing
  đăng ký thêm routing key đó. KHÔNG gọi REST đồng bộ sang lab.
- Test: `onLabResult_noLabTypeKnown_skipsFeeButMarksProcessed`,
  `onLabResult_sameEventTwice_createsOneFee` (BR-B7),
  `onLabResult_feeAlreadyExistsForSource_doesNotCreateDuplicate`.

### Mục 3
- [x] Test `RevenueMapper`: `RevenueMapperTest` (ánh xạ departmentId / totalRevenue
  / invoiceCount).
- [x] `BillingApplicationServiceTest.revenue_groupsByDepartmentIdAndDateRange` (BR-B10).
- Notification consumer thật vẫn là việc Phần 5/5 — `NotificationTemplates` +
  luồng 7 bước đã có ở tầng application, consumer sẽ đối chiếu payload publisher
  khi ráp.

### `prescription.filled` — `dispenseId`
- Payload pharmacy hiện không mang `dispenseId`. `SagaCompensationService.onPrescriptionFilled`
  chuyển saga sang `COMPLETED` (BR-B11) nhưng **chưa gán `dispenseId`**.
- [ ] CÒN MỞ (Phần 5/5): thống nhất với pharmacy bổ sung `dispenseId` vào
  `prescription.filled`, hoặc billing bỏ hẳn việc gán.

### Kiểm tra
`mvn -pl backend/billing-service,backend/notification-service -am test` →
billing 48/48 (gồm 5 ArchitectureTest), notification 18/18. BUILD SUCCESS.

---

## Cập nhật Phần 4/5 — 2026-09-08 (theloc) · nhánh `theloc-phan4`

Tầng infrastructure/persistence: JPA entity + Spring Data repo + adapter hiện thực các out-port,
`V1__init.sql` (Flyway), `PriceListAdapter` (@ConfigurationProperties), và adapter cho
`LabTestTypePort`. Map entity ↔ domain làm **thủ công trong adapter** (không MapStruct) — theo
pharmacy-service, vì `Fee`/`Invoice`/`Notification` dùng static factory `restore(...)` với field
`final`, hợp với map tay hơn. Coding map §12.3/§13.3 gợi ý `*PersistenceMapper` MapStruct; đây là
sai khác có chủ ý, ghi lại ở đây.

### Billing
- `FEE`, `INVOICE`, `PROCESSED_EVENT` đúng lược đồ §1 — giữ nguyên **partial unique index**
  `uq_fee_source` (BR-B7) và `uq_invoice_prescription` (BR-B6) dạng `WHERE ... IS NOT NULL`.
- `FeePersistenceAdapter`, `InvoicePersistenceAdapter`, `ProcessedEventPersistenceAdapter`,
  `PriceListAdapter`, `LabTestTypeProjectionAdapter`.
- `sumRevenueByDepartment` (BR-B10): JPQL theta-join `INVOICE`×`FEE` qua `fee.invoice_id`, gom theo
  `fee.department_id`, `SUM(fee.amount)` + `COUNT(DISTINCT invoice_id)`, lọc `is_paid = true` và
  `paid_at ∈ [fromDate 00:00Z, toDate+1 00:00Z)`. `departmentId` null = mọi khoa.
- Bảng chiếu `LAB_TEST_TYPE` (mục 2 dưới đây): entity + repo + `LabTestTypeProjectionAdapter`
  hiện thực `LabTestTypePort.labType(labId)`, thêm `record(labId, labType)` cho consumer Phần 5/5.

### Notification
- `NOTIFICATION`, `PROCESSED_EVENT` đúng lược đồ §1 (không có `updated_at` — thông báo chuyển
  `PENDING → SENT|FAILED` một lần).
- `NotificationPersistenceAdapter` (lưu được cả `PENDING` lẫn bản kết thúc, cập nhật giữ
  `created_at`), `ProcessedEventPersistenceAdapter`.
- **Chưa** làm bảng chiếu email/phone của §10 — Phần 5/5 mới quyết (bảng chiếu từ
  `patient.created`/`patient.updated`, hoặc chấp nhận IN_APP cho các event còn lại).

### Ảnh hưởng tới các mục còn mở
- Mục 2 (`labType`): phần persistence của bảng chiếu **đã xong**. CÒN MỞ: consumer
  `lab.request.created` gọi `LabTestTypeProjectionAdapter.record(...)` + đăng ký routing key —
  Phần 5/5.
- Mục 1 (`recordId`) và `dispenseId`: không đổi, vẫn chờ chốt cross-team ở Phần 5/5.

### Kiểm tra
`mvn -pl backend/billing-service,backend/notification-service -am test` (không có Docker local):
billing 68 chạy / 17 skip (persistence slice cần Testcontainers), notification 22 chạy / 4 skip.
0 failure. `PriceListAdapterTest` + 5 ArchitectureTest xanh. Slice test persistence
(`@DataJpaTest` + Testcontainers Postgres, `disabledWithoutDocker = true`) chạy trên CI —
cùng khuôn với `pharmacy-service`.


## Integration of Part 4 (2026-09-08)

- Fee selection now locks unassigned unpaid rows until invoice creation commits.
- Payment and saga changes use explicit locked invoice lookups; read-only queries stay unlocked.
- Both processed-event adapters use insert-only SQL so a duplicate marker cannot silently overwrite
  an existing row and permit duplicate side effects. Notification inserts before sending.
- PostgreSQL migration/adapter tests require Docker. The service-integration GitHub workflow runs
  them on a Docker-capable runner and fails if tests are skipped.
- The LAB_TEST_TYPE projection is a compatibility fallback for legacy results without labType;
  the new Lab producer supplies labType and performedDate directly.
