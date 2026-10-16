# Huy MVP batch 9 — source acceptance and owner handoff

Ngày: 2026-10-16. Source baseline: `9ef3b151`. User chốt: chỉ sửa phạm vi Huy,
phần phụ thuộc owner khác đặt handoff. Không thay production foreign/shared paths hoặc bật gate.

## 1. Phần kiểm chứng bổ sung

- Pharmacy: public dispense tạo `prescription.filled` từ persisted prescription/slip; khóa đúng
  record/patient/department/episode/dispense IDs và exact payload bytes khi retry. Upstream authority
  là test doubles; đây không phải Billing V1 acceptance.
- Report: actual Lab producer fixture qua PostgreSQL/RabbitMQ; duplicate và eventId mới giữ một
  operation effect, conflict đi DLQ. Actual Clinical ADMISSION fixture vẫn bị từ chối trên durable DLQ.
- Report: legacy `report.q` không có consumer MVP, backlog còn nguyên sau khai báo topology và
  listener restart; queue MVP xử lý riêng, không cộng legacy payment contribution.
- Report: fault sau transaction commit trước listener ACK gọi lại receiver thật; không mock store.
  Đây là lỗi delivery trong JVM, không phải kill/restart JVM hoặc Support host acceptance.
- Report source profile chạy packaged Core/Clinical hiện tại, setup chỉ public HTTP, không query/seed
  foreign SQL. Completion OUTPATIENT_FOLLOW_UP dùng audited emergency override có sẵn; không
  chứng minh paid flow. Giữ nguyên producer bytes, kiểm tra contribution, listener restart/replay,
  conflict DLQ và coverage vẫn thiếu. Producer fixture ADMISSION không bị sửa thành outpatient.

## 2. Lệnh tái lập

Yêu cầu Docker và Java 21. Package source hiện tại trước khi chạy source acceptance:

```powershell
mvn -q -pl backend/core-service,backend/clinical-service -am -DskipTests package
mvn -q -pl backend/pharmacy-service,backend/report-service -am clean test
mvn -q -pl backend/report-service -am -Preport-source-acceptance '-Dtest=OutpatientReportRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
mvn -q -pl backend/report-service -am -Preport-packaging-acceptance -DskipUnitTests=true verify
```

Source acceptance lưu original body ở
`backend/report-service/target/acceptance-evidence/medicalrecord.completed.outpatient.actual.json`
và in SHA-256 trong test output. File này là evidence từ một run disposable, không là fixture
producer mới hoặc phê duyệt rollout.

## 3. Đường bàn giao đến 45/48

Kết quả đã chạy: Pharmacy 822 và Report 1.330 tests trong Surefire, failures/errors/skips đều 0.
Pharmacy gồm full regression 821, rồi rerun lớp API 16 thay kết quả lớp cũ 15; Report gồm full
regression 1.329, rồi rerun lớp Rabbit 9 thay kết quả lớp cũ 8. Không cộng lặp các rerun.
Source acceptance Failsafe: 1 passed, 0 failures/errors/skips; Maven exit 0.
Packaging acceptance Failsafe: 5 passed (2 artifact checks + 3 forked JVM probes),
0 failures/errors/skips; Maven exit 0. Các probe này xác nhận Report library/resource isolation,
không phải packaged Support acceptance. Tổng unique tests hai module và hai profile: 2.158.
Original completion SHA-256:
`37682b98b9e33300efce4d50c6aeceddb7b180be6146c12532d8dc3fe0a1c572`.
Accepted delivery IDs: 2; completed-visit contribution: 1; conflicting body retained on DLQ: 1;
accepted coverage rows: 0. Đây là disposable run hiện tại, không operator sign-off.

Sau khi H6-08.4 đạt, mốc local là 38/48. Bảy mục tiếp theo cần owner acceptance:

| Mục | Owner và bằng chứng cần nhận |
|---|---|
| H6-06.2 | Clinical/Billing/Notification nhận đúng original producer bytes, exact persisted relationships và một business effect. |
| H6-06.4 | Billing selective outpatient publication và chuỗi charge/payment/grant/terminal thật qua PostgreSQL/RabbitMQ. |
| H6-11.1–11.4 | Shared owner tạo Support host; Lộc cung cấp Notification export; hai stores/transactions/migrations/workers, security và packaged runtime được nghiệm thu. |
| H6-12.1 | Gateway/Core/Clinical/Billing/Support owners cùng nghiệm thu flow ngoại trú tích hợp. |

H6-02.3, H6-12.2 và H6-12.3 vẫn cần approved inventory, evidence tổng và cutover/rollback.
Không chuyển checkbox sang done chỉ vì đã đặt handoff. Chi tiết owner queue và acceptance nằm ở
[handoff chung Huy](../handoffs/HANDOFF-HUY-MVP.md).

## 4. Chốt phần độc lập và gộp handoff — 2026-10-16

Đã audit đủ 10 subtask chưa đóng: mỗi mục còn thiếu external-owner integrated acceptance hoặc
approved historical/operator evidence; phần owned implementation/tests đã có. Không đổi tiêu chí
hoặc tự tick local evidence thay acceptance còn thiếu. Một handoff chung chứa đủ 10 hàng, owner,
source gap và điều kiện đóng; sáu file handoff/pointer backend Huy cũ đã gộp/xóa, các liên kết được cập nhật.

Loại workaround cũ ở `PharmacyAuthorityRuntimeAcceptanceIT`: Clinical durable publisher bật true
trong API setup. Rerun packaged Core/Clinical + private PG stores/Rabbit: **2 passed, 0 failures/
errors/skips, Maven exit 0**. Không sửa producer hoặc foreign DB. Đây là refresh của H6-03.4 đã
đóng, không thêm checkbox. Giữ progress 38/48.

```powershell
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true '-Dit.test=PharmacyAuthorityRuntimeAcceptanceIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Raw rerun evidence: `backend/pharmacy-service/target/failsafe-reports/TEST-com.mediflow.pharmacy.PharmacyAuthorityRuntimeAcceptanceIT.xml`.
Các full-regression/source/packaging counts ở section 3 là lượt batch 9 trước đó, không phải
vừa chạy lại toàn bộ chỉ vì chỉnh documentation và test setup. Không cộng lặp lượt lookup batch 8.
