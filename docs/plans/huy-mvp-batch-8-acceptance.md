# Huy MVP — batch 8: sáu subtask gốc và bằng chứng chạy thật

Ngày 2026-10-16. Source `e948075893e797be056d1344f9c9ba0072ed6dae` cộng worktree Huy
chưa commit, giữ nguyên thay đổi các batch trước. Đây là evidence của
[plan Huy](2026-10-14-huy-backend-mvp-adjustment-plan.md), không thêm backlog hay mẫu số mới.
Số ca test, số file và các lần chạy lại **không phải** số task hoàn tất.

## 1. Sáu subtask được nghiệm thu

| ID gốc | Bằng chứng | Giới hạn còn giữ |
|---|---|---|
| H6-02.4 | Packaged Pharmacy MVP: admission port/consumer/Inpatient client và legacy auto-dispense consumer đều vắng dù global/old flags bật; destination là Core, Open-in-View false. Packaged Report trả 404 ở ba full-V2 routes; profile bean/job matrix, architecture, migration và V0 regression chạy riêng. | Không triển khai profile tổng hoặc tắt Surgery app thay shared owner; historical cutover inventory thuộc H6-02.3. |
| H6-03.4 | Packaged Core/Clinical thật, API tạo department/doctor/patient/record rồi hai production Pharmacy adapters dùng service JWT lookup chính xác; absence phân biệt unavailable, correlation UUID, human/refresh/wrong caller bị từ chối. | Clinical seed dùng compatibility API với durable publisher OFF vì lỗi metadata producer. Không chứng nhận MVP exam/durable publisher hoặc public V1 creation end-to-end. |
| H6-07.1 | V0: Pharmacy JVM down, payment chờ durable queue rồi restart hoàn tất saga. V1: Billing original grant/cash fixtures, duplicate grant chỉ lưu một proof; qua hai restart vẫn stock 20, slip PENDING, không payment receipt/fill outbox. | Fixture compatibility không thay live issuer publication/current-authority chain H6-06.2/.4. Authenticated dispense có test API/PG riêng của batch 7. |
| H6-07.2 | Kill JVM tại sau committed stock/trước receipt finalize, trước ACK và sau broker publish confirm/trước DB mark; restart giữ stock 17, một filled proof với nguyên event ID/payload, receipt DISPENSED, một Report effect. | At-least-once transport, không tuyên bố broker exactly-once hoặc V1 ledger đã nghiệm thu. |
| H6-07.3 | Actual Billing V0 cancel/expiry/stock-shortage; late/duplicate payment và failure, cả delivery ID cũ/mới: một reversal đúng invoice/patient, không filled fact, không stock lặp; invoice/fees unpaid và saga REFUNDED. | Pharmacy receipt COMPENSATED khác Billing saga REFUNDED. Đây là V0 reversal, không settlement/refund completion V1. Không phát payment.refunded giả. |
| H6-07.4 | Actual listener conflicting bytes vào DLQ giữ nguyên body; own-PG outbox failure rollback stock/slip/reservation/fill proof, receipt RECEIVED còn durable; gỡ fault và redrive nguyên bytes hoàn tất một lần. Retry bounded, business queues/DLQ không giữ valid duplicates. | Không reset inbox, xóa lịch sử hay sửa foreign DB. Snapshot chỉ synthetic IDs/states/hash; không payload/token người dùng. |

H6-06.4 từng được chọn đầu lượt nhưng probe phát hiện blocker thật phía Billing. Giữ nguyên tiêu chí
và trạng thái mở, thay bằng H6-03.4 có thể nghiệm thu trong scope. Sáu ID đóng ở bảng trên không
bao gồm H6-06.4. H6-07 đạt 4/4 ca nghiệm thu local/compatibility; **activation V1 vẫn CONTRACT OPEN**.

## 2. Bộ kiểm thử và ranh giới dữ liệu

`PharmacyDistributedRuntimeAcceptanceIT` chạy bốn packaged JVM Pharmacy/Billing/Report/Notification,
bốn PostgreSQL stores độc lập và RabbitMQ. Notification nhận predecessor `invoice.created` của V0.
Commands và foreign observations qua HTTP thật; broker probes chụp/replay original producer bytes.

`PharmacyAuthorityRuntimeAcceptanceIT` chạy Core với hai context-owned stores, Clinical với store
riêng và RabbitMQ. Toàn bộ foreign seed đi qua public HTTP; không SQL/repository/DB access xuyên context.
Client adapters/DTO/strict parser/credential/correlation của Pharmacy là production implementations,
không mock authority. Core discovery destination được đối chiếu thêm trong packaged Pharmacy MVP.
Lookup suite dùng mapped HTTP target; không coi đây là Gateway/Eureka acceptance.

`PharmacyOutpatientRuntimeAcceptanceIT` chạy Pharmacy MVP, Billing, Report MVP với ba private stores
và RabbitMQ. Original `ledger-v1/clearance-prescription.json` và `payment-service.json` của Billing
không chỉnh bytes. Test seed chính aggregate Pharmacy theo exact fixture IDs; đây là fixture target,
không fake public authority hoặc bằng chứng lịch sử production.

Fault bootstrap `com.mediflow.acceptancefixture.PharmacyFaultBootApplication` chỉ ở test classpath,
ngoài production component scan. Isolated container dùng Boot PropertiesLauncher load bootstrap đó
bên cạnh actual packaged JAR. Production JAR được kiểm tra không chứa acceptancefixture. Test-only
ADMIN endpoints quan sát/inject fault vào **Pharmacy-owned** store, không là production API/gate.

Crash pause xảy ra đúng adapter boundary, giữ transaction thật; Docker stop timeout 0 giết JVM,
không chỉ ném exception rồi gọi service lần hai trong cùng process. Pause bounded 120 giây;
container restart bỏ fault state. Outbox lease test là 5 giây, không sửa cấu hình deployed.

## 3. Kết quả và cách đọc evidence

- V0 runtime: **12 passed, 0 failed/errors/skipped**, gồm ba crash windows và ba compensation variants.
- Core/Clinical runtime: **2 passed, 0 failed/errors/skipped**.
- MVP/fixture runtime: **2 passed, 0 failed/errors; 1 skipped có điều kiện rõ** — live Billing V1 issuer chain.
- Focused regression: **131 passed, 0 failed/errors/skipped**; Pharmacy 123, Report 8. Bao gồm
  gate/profile/current-authority/API-PG/cohort-broker/migration và hai module architecture guards.
- Tổng unique cases trong selection lượt này: **147 passed, 1 skipped**; rerun cùng case không cộng lại.
  Không ghi 147 như một full-repository suite hoặc một Maven invocation duy nhất.

Previous full Pharmacy 821 / Report 1,326 là evidence batch 7 / batch 6, **không vừa chạy lại full**.
62 protected migration/fixture SHA-256 không thay đổi; không Flyway repair/renumber/rewrite.

Các lần fail trước kết quả cuối được giữ nguyên ý nghĩa:

- Ba V0 cases ban đầu dùng sai expectation Billing saga COMPENSATED. Wire thực là REFUNDED,
  Pharmacy receipt mới là COMPENSATED. Sửa expectation đúng hai state machines; không sửa Billing.
- Core seed abbreviation phải uppercase và Core correlation phải UUID. Test setup sửa đúng API;
  non-UUID rewrite được giữ thành negative unavailable test, không nới parser.
- Clinical durable seed thực sự trả 500: `legacy Clinical wire payload does not match its flat event metadata`.
  Không sửa service ngoài scope; suite lookup dùng supported compatibility seed, không claim publisher đã fix.
- Live Billing V1 probe timeout 35 giây chờ `invoice.created`; source vẫn `appendHeld` dù charge consumer,
  ledger, DRUG price code và clearance lookup bật. Không release foreign row. Ca này nay opt-in, mặc định
  **SKIPPED**, không đổi thành pass. H6-06.2/.4 và end-to-end giữ mở trong
  [activation handoff](../handoffs/history/2026-10-22-huy-mvp-checkpoints.md).
- Một selector trước đó chạy Core setup lỗi rồi V0 12/12 đạt vẫn exit 1 overall. Core đã rerun riêng
  thành công; không mô tả selector cũ là Maven exit 0.

Local raw evidence ở `backend/pharmacy-service/target/failsafe-reports/TEST-*.xml`;
`target/acceptance-evidence/batch-8-v0-twelve.xml` giữ bản 12-case trước selector rerun ghi đè.
Các dòng `ACCEPTANCE_SNAPSHOT` và `ACCEPTANCE_MESSAGE` trong runtime XML ghi synthetic IDs,
stock/slip/reservations/receipts, Billing invoice state và outbox/event payload hashes. Hash không
thay original-byte assertion: DLQ body và recovered filled ID/payload còn được so trực tiếp trong test.
Artifacts target không commit; tài liệu này và source tests là hướng tái tạo được version-control.

## 4. Tái tạo

Runtime: Docker Desktop 29.6.2, PostgreSQL 16.14 (`postgres:16-alpine`), RabbitMQ 3.13 Alpine,
Java 21 (`eclipse-temurin:21-jre-alpine`), Maven 3.9.16. Images dùng tags hiện hành, không coi là
digest-pinned deployment manifest. Mỗi suite tạo mạng/stores riêng rồi tự thu hồi test containers;
không reset Docker hoặc xóa user database.

Từ repository root, package current upstream source trước; build không cấp quyền sửa module đó:

```text
mvn -q -pl backend/core-service,backend/clinical-service,backend/billing-service,backend/report-service,backend/notification-service -am -DskipTests package
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true verify
```

Selector từng suite:

```text
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true -Dit.test=PharmacyDistributedRuntimeAcceptanceIT verify
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true -Dit.test=PharmacyAuthorityRuntimeAcceptanceIT verify
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true -Dit.test=PharmacyOutpatientRuntimeAcceptanceIT verify
mvn -q -pl backend/pharmacy-service,backend/report-service -am test -Dtest=PharmacyOutpatientApiConfigurationTest,PharmacyMvpProfileAliasTest,PharmacyMvpDeferredConfigurationTest,ReportMvpDeferredConfigurationTest,PrescriptionCurrentAuthorityTest,OutpatientDeliveryPostgresTest,PrescriptionClearanceRabbitIntegrationTest,PharmacyMigrationCompatibilityTest,ArchitectureTest,OutpatientApiPostgresTest -Dsurefire.failIfNoSpecifiedTests=false
```

Chỉ sau Billing owner hoàn tất selective new-cohort publication/acceptance, rerun ca live với
`-Dmediflow.acceptance.billing-v1-published=true`. Test vẫn không được tự mở historical HELD.

## 5. Tiến độ sau batch

**37/48 subtask đóng, 11 còn mở**: H6-02.3; H6-06.2/.4; H6-08.4; H6-11.1–.4; H6-12.1–.3.
Không thay mẫu số 48, không chuyển shared/owner acceptance thành task local để tăng số.
Historical inventory/coverage, Clinical completed source, live V1 issuer, Support host/Notification,
Gateway và operator cutover vẫn cần evidence đúng scope.

Không sửa Core/Clinical/Billing/Notification/Gateway/root POM/Compose/CI production trong batch này.
Không commit/push hoặc bật gate deployed. Plan local tiến triển không chứng nhận MVP sáu-app đã rollout.
