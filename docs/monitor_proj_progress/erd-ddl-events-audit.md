# Rà soát ERD, DDL và luồng sự kiện

## 1. Trạng thái ERD

Bộ ERD hiện có 11 góc nhìn, mỗi góc nhìn được lưu ở ba định dạng Mermaid source (`.mmd`), SVG và PNG. Đây là phần mạnh nhất của hồ sơ tháng 8 và có thể gửi kiểm tra ngay.

| # | Sơ đồ | Trạng thái |
|---:|---|---|
| 01 | Tổng quan hệ thống | Có source + SVG + PNG |
| 02 | Quy trình khám | Có source + SVG + PNG |
| 03 | Saga thanh toán–cấp thuốc | Có source + SVG + PNG |
| 04 | Vòng đời bệnh nhân | Có source + SVG + PNG |
| 05 | Tổ chức theo khoa | Có source + SVG + PNG |
| 06 | Bản đồ sự kiện | Có source + SVG + PNG |
| 07 | Dược–tồn kho | Có source + SVG + PNG |
| 08 | Report read model | Có source + SVG + PNG |
| 09 | Xác thực/RBAC | Có source + SVG + PNG |
| 10 | Hồ sơ bệnh án | Có source + SVG + PNG |
| 11 | Liên kết tổng hợp | Có source + SVG + PNG |

Nguồn: [`docs/eproject_general_plan/erd/README.md`](../eproject_general_plan/erd/README.md).

## 2. Trạng thái DDL

### DDL ở mức đặc tả

| Service | Bảng nghiệp vụ | Bảng dedupe | Tổng CREATE TABLE trong spec |
|---|---:|---:|---:|
| organization | 3 | 0 | 3 |
| patient | 1 | 0 | 1 |
| clinical | 3 | 0 | 3 |
| lab | 2 | 1 | 3 |
| pharmacy | 4 | 1 | 5 |
| billing | 2 | 1 | 3 |
| notification | 1 | 1 | 2 |
| report | 3 | 1 | 4 |
| **Tổng** | **19** | **5** | **24** |

### DDL có thể chạy bằng Flyway

| Service | Migration SQL thật | Trạng thái |
|---|---:|---|
| pharmacy | 1 file / 5 bảng | Có |
| 7 service còn lại | 0 | Chưa có |

Kết luận: **mô hình DDL đã được viết trong Markdown, nhưng deliverable DDL triển khai mới đạt 1/8 service**. Cần tách code block DDL sang migration, chạy trên PostgreSQL sạch và để `ddl-auto=validate` xác minh.

## 3. Event topology đã thiết kế

- Exchange: `mediflow.events`, loại topic, durable.
- Routing key: dot.case.
- Mỗi consumer sở hữu durable queue, DLX `mediflow.events.dlx` và DLQ riêng.
- Event envelope: `eventId`, `occurredAt`, `correlationId` + domain fields.
- Publish sau DB commit; với billing/pharmacy cần outbox hoặc cơ chế tương đương.
- Consumer bắt buộc idempotent theo `eventId`.

## 4. Catalog publish/subscribe hiện hành

| Event | Publisher | Subscriber |
|---|---|---|
| `department.created` | organization | — |
| `staff.created` | organization | — |
| `staff.department.changed` | organization | report |
| `patient.created` | patient | notification |
| `patient.updated` | patient | — |
| `appointment.created` | clinical | notification |
| `appointment.status.changed` | clinical | billing |
| `medicalrecord.created` | clinical | lab, billing, report |
| `diagnosis.added` | clinical | — |
| `lab.request.created` | lab | — |
| `lab.result.created` | lab | clinical, billing, notification, report |
| `prescription.created` | pharmacy | billing |
| `prescription.filled` | pharmacy | clinical, notification, report |
| `stock.low` | pharmacy | ops/notification |
| `invoice.created` | billing | — |
| `payment.completed` | billing | pharmacy, lab, patient(log), notification, report |
| `payment.failed` | billing | notification, saga compensation |
| `notification.sent` | notification | — |

## 5. Luồng saga dự kiến

```text
pharmacy: prescription.created
    → billing tạo invoice AWAITING_PAYMENT
    → CASHIER thanh toán
    → billing phát payment.completed
    → pharmacy khóa tồn kho và xuất thuốc
        → prescription.filled → billing COMPLETED + report/notification/clinical
        → prescription.dispense.failed → billing REFUNDED → payment.failed
```

Không có transaction phân tán. Mỗi bước local transaction + event; mỗi consumer phải chống trùng; nhánh thất bại phải có bù trừ.

## 6. Các mâu thuẫn cần chốt trước khi ký Design v1.0

1. **Số bảng:** ERD README ghi 25 bảng nghiệp vụ; backend spec chỉ có 19 bảng nghiệp vụ + 5 bảng dedupe.
2. **Tên bảng/cột:** root rule yêu cầu DB Vietnamese snake_case, trong khi ERD/spec dùng cả tiếng Việt và English UPPER_SNAKE, đặc biệt branch C.
3. **Tên event:** sơ đồ event/saga dùng `prescription.dispense.failed`; catalog chuẩn 18 event không có dòng này.
4. **Routing key prefix:** tài liệu topology minh họa `billing.payment.completed`/`pharmacy.stock.low`, catalog lại dùng `payment.completed`/`stock.low`.
5. **Đơn vị phát `prescription.filled`:** catalog ghi pharmacy/prescription, sơ đồ có lúc nối từ `DISPENSE_SLIP`; cần chốt payload và aggregate source.
6. **Trạng thái saga:** tên trạng thái trong các tài liệu phải đồng nhất với enum/code trước khi viết consumer.

## 7. Khoảng cách giữa thiết kế và code

Codebase-memory và source search tại 02/09 cho kết quả:

- 0 `RabbitTemplate`;
- 0 `@RabbitListener`;
- 0 `@PreAuthorize` trong toàn backend;
- 1 controller thật (gateway auth);
- 5 entity và 5 bảng migration, đều ở pharmacy.

Vì vậy luồng event hiện là **thiết kế**, chưa phải integration đã kiểm chứng.

## 8. Gate hoàn tất ERD + DDL + events

- [ ] Chốt 6 mâu thuẫn ở mục 6 bằng decision log.
- [ ] Tạo migration V1 cho đủ 8 service và chạy trên 8 database sạch.
- [ ] Đối chiếu migration ↔ ERD bằng danh sách bảng/cột/FK/check/index.
- [ ] Chốt JSON schema/event record cho mọi event và versioning rule.
- [ ] Có contract test publisher/consumer + test idempotency + test DLQ.
- [ ] Có integration test saga happy path và compensation path.
- [ ] Render lại 11 sơ đồ sau quyết định cuối và ghi version/ngày duyệt.

