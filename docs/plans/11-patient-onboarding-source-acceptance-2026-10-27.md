# Nghiệm thu source — tiếp nhận NURSE và cổng bệnh nhân, 27/10/2026

Baseline: `f28d25b4`, nhánh `codex/nurse-patient-onboarding`. Biên bản áp dụng cho commit
triển khai kèm tài liệu này; commit chính xác lấy từ PR. Không thay bằng chứng runtime ngày 26/10.
Quy tắc nghiệp vụ ở [02 §4.1](02-business-flows.md), wire ở [03](03-contracts.md),
ma trận toàn luồng ở [06 ONB-A01–12](06-acceptance-runbook.md).

## Source đã triển khai

| Slice | Bằng chứng trong source | Giới hạn nghiệm thu |
|---|---|---|
| ONB-01/02 | `PatientAccountController`, `ProvisionPatientAccountService`; Gateway policy; service/controller tests và `PatientServiceIntegrationTest` | Không thay ADMIN-only account management; không reset mật khẩu người cũ |
| ONB-03 | `PatientAccountIssuance` và component tests; giữ key khi timeout/remount, patientId được chuyển sang reception | Người bệnh không phải login để đăng ký/khám; cần tổng duyệt UI thật |
| ONB-04 | Clinical/Lab `PatientResultsController`/service/persistence tests; `/records/mine`, `/lab/mine`; frontend list paging/session/retry tests | Chỉ completed results; query không nhận scope patient; không công bố draft |
| ONB-05 | `PatientResultNoticeDecoder`/service; producer fixtures; `RegistrationNoticeIntegrationTest` gửi qua broker | IN_APP, generic availability; không thêm SMS/email/push |
| ONB-06 | Module tests, frontend tests, typecheck/lint/build và source review | Packaged Gateway/UI ONB-A01–12 còn mở |

Core integration dùng PostgreSQL và RabbitMQ Testcontainers: hai key đồng thời cùng patient chỉ
có một account; account inactive vẫn chặn cấp mới; cùng key giữ account/username sau sửa CCCD,
archive và đổi mật khẩu; khác patient cùng key bị conflict. Không đọc chéo database.

Clinical/Lab persistence tests phân trang riêng dữ liệu người bệnh, loại draft và dữ liệu người khác.
Gateway matrix kiểm role trên route `mine` và giữ staff detail routes theo UUID. Lab JWT tests
kiểm claim patientId hợp lệ, thiếu/sai UUID, không fallback từ account subject.

Notification integration nạp đúng producer fixtures của `medicalrecord.completed` ngoại trú,
`lab.result.created`, `followup.recommendation.created`, gửi mỗi event hai lần qua RabbitMQ:
chỉ ba notification, ba inbox claims và ba `notification.sent` outbox, trước bất kỳ lần login nào.
Fixture tests so JSON với serializer thật; không dùng ADMISSION fixture để suy nghiệm thu ngoại trú.

## Kiểm tra backend

Chạy từ repository root:

```text
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/gateway,backend/notification-service -am test
```

Lần tổng chạy Core/Clinical/Lab/Gateway đạt; Notification có một failure do assertion topology
cũ còn kỳ vọng 13 bindings trong khi source mới có 15. Cập nhật test để kiểm cả hai binding mới,
chạy riêng `NotificationEventConsumerTest` đạt rồi chạy lại toàn bộ Notification:

```text
mvn -q -f backend/notification-service/pom.xml test
```

Core chạy bổ sung sau sửa review, có exit 0:

```text
mvn -q -f backend/core-service/pom.xml -Dtest=ProvisionPatientAccountServiceTest,PatientServiceIntegrationTest,PatientAccountControllerTest test
```

Các số dưới là tổng JUnit reports của từng module sau các lượt chạy trên, không phải số test mới.

| Module | Tests | Failures/errors/skipped |
|---|---:|---:|
| Core | 395 | 0/0/0 |
| Clinical | 436 | 0/0/0 |
| Lab | 274 | 0/0/0 |
| Gateway | 1.649 | 0/0/0 |
| Notification | 309 | 0/0/0 |

## Kiểm tra frontend

- `pnpm exec vitest run --maxWorkers=2`: **120 files, 1.098 tests passed**, không skipped.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: exit 0; hai patient result routes có trong build.
- Lần chạy mặc định worker đồng thời với backend gặp ba timeout trong deferred Inpatient tests;
  chạy lại toàn bộ với hai workers đạt. Không sửa production Inpatient.

## Source review

Reviewer độc lập đã thông qua source sau hai sửa đổi: replay không phụ thuộc CCCD/archive hiện tại;
hướng dẫn mật khẩu ban đầu chỉ hiện khi account vẫn cần đổi mật khẩu. Bổ sung test account
inactive trước archive và kiểm chính xác conflict code khi đổi patient trên key đã thành công.

## Phần còn lại

Chưa chạy ONB-A01–12 trên stack packaged cuối qua Gateway và trình duyệt, chưa có ảnh UI và
effect counts toàn hành trình mới → thanh toán → khám → Lab → lời hẹn → login muộn.
H-11 giữ **NEEDS REVALIDATION**; không tuyên bố đóng WF-11. Tái khám dùng hồ sơ/account cũ,
lượt mới liên kết lượt trước; lời hẹn không tự biến thành lịch khám đã đặt. Scheduling UI giữ gate
`MEDIFLOW_SCHEDULING_UI_ENABLED`; không đổi default flags để demo đạt.
