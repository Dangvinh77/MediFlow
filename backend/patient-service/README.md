# patient-service

Hồ sơ bệnh nhân gốc (`BENH_NHAN`) — master patient index. Reference: [`docs/ai/services/patient.md`](../docs/ai/services/patient.md) · design doc [`EProject/patient-service.html`](../EProject/patient-service.html) · spec triển khai [`EProject/backend-spec/02-patient.md`](../EProject/backend-spec/02-patient.md).

> ✅ **Đây là reference implementation của dự án.** Xây service khác mà thấy blueprint chưa rõ chỗ nào thì mở module này ra xem.
>
> | Tầng | File | Được phép import |
> |---|---|---|
> | `domain/` | 4 | chỉ `java.*` + `common` — **không một framework nào** |
> | `application/` | 11 | thêm `@Service`, `@Transactional`, MapStruct, `jakarta.validation` |
> | `infrastructure/` | 15 | mọi thứ |
>
> 30 test xanh, trong đó test domain chạy **không cần Spring context**.

## Vài chỗ đáng xem khi học theo

- **`domain/model/Patient`** — không có setter. Mọi thay đổi trạng thái đi qua `capNhat()`, nên bất biến không thể bị né.
- **`PatientEventPublisherAdapter`** — publish **sau khi transaction commit**, qua `TransactionSynchronization`. Publish thẳng trong transaction sẽ thông báo về một bệnh nhân mà rollback vừa xóa mất.
- **`PatientRepositoryPort`** — không có `Pageable`, không có `Page`, không có JPA. Việc quy đổi `PageQuery ↔ Pageable` nằm trọn trong `PatientPersistenceAdapter`.
- **Hai tầng validation cố ý**: Bean Validation trên DTO cho 400 kèm chi tiết từng field; bất biến trong domain cho 422 và chặn **mọi** người gọi, kể cả event consumer hay test.

- **Port:** 8081 · **Base path:** `/api/v1/patients` · **DB:** `mediflow_patient` (PostgreSQL)
- Built to the mandatory blueprint: [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md).

## Run locally

1. Create the database once (see root `README.md`): `CREATE DATABASE mediflow_patient;`
2. Start `eureka-server` (8761), then optionally `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/patient-service -am spring-boot:run
```

Swagger UI: http://localhost:8081/swagger-ui.html

## Events
Publishes `patient.created`, `patient.updated` to the `mediflow.events` topic exchange. See [`docs/ai/06-events-rabbitmq.md`](../docs/ai/06-events-rabbitmq.md).

## Tests
```bash
mvn -pl backend/patient-service test        # unit
mvn -pl backend/patient-service verify      # + integration (Testcontainers, needs Docker)
```
