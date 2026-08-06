# Reference boilerplate (chung cho mọi microservice)

Nơi tập trung các file cấu hình/boilerplate **giống hệt nhau ở mọi service**.
Khi scaffold một service, copy vào đúng vị trí và sửa cho khớp (thay `<service>` = tên module,
routing key = đúng spec). **Đừng sửa bản gốc ở đây** — nó là bản chuẩn.

## File

| File | Copy đến | Sửa gì |
|------|----------|--------|
| [`GlobalExceptionHandler.java`](GlobalExceptionHandler.java) | `<svc>-service/.../web/GlobalExceptionHandler.java` | chỉ package (`com.mediflow.<svc>`) |
| [`RabbitConfig.java`](RabbitConfig.java) | `<svc>-service/.../infrastructure/config/RabbitConfig.java` | package; routing-key hằng số; `QUEUE`/`DLQ`; bindings theo spec |
| [`EventPublisherAdapter.java`](EventPublisherAdapter.java) | `<svc>-service/.../infrastructure/messaging/XxxEventPublisherAdapter.java` | package; tên port/payload; nội dung từng event |

## Nguyên tắc giữ nguyên (không bỏ)

- **Publish sau khi commit** qua `TransactionSynchronization` — quan trọng nhất với saga billing/pharmacy:
  một `payment.completed` gửi trước commit có thể làm pharmacy xuất thuốc cho một lần thanh toán sau đó
  bị rollback. Không có ngoại lệ.
- Application layer **không bao giờ** nhìn thấy `RabbitTemplate` — chỉ thấy out-port
  (`XxxEventPublisherPort`). Adapter này là nơi duy nhất chạm AMQP.
- `GlobalExceptionHandler` bắt đúng 3 exception base trong `common` + validation + phần còn lại → 500
  chung, không lộ stack trace.

Nguồn: patient-service (demo) từng là reference implementation; các file này được tổng quát hóa từ đó.
