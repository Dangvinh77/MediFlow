# Vinh Clinical/Lab outpatient MVP closeout

**Cập nhật:** 2026-10-16, source `c8024921` (đã có PR #393/#396 Billing và PR #383 Gateway/Core).
**Trạng thái:** owned implementation đã có; **chưa đóng actual paid acceptance / toàn MVP**.
Production scope của Vinh: Clinical/Lab. Inpatient/Surgery deferred.

## Current owner queue

| Gate | Source và kết luận | Việc còn lại / owner |
|---|---|---|
| V-CLOSE-01 reliability | Standalone Clinical/Lab PG/Rabbit/outbox/consumer JVM recovery đã có | Không làm lại; integrated paid recovery thuộc V-CLOSE-03 |
| V-CLOSE-02 Rx V1 | Huy đã giao outpatient recordId/dispense relationship; Clinical consumer đã merge ở `c4cf810f` | Không giao lại producer/consumer implementation; actual paid Rx vẫn cần nghiệm thu |
| Public Lab-order | Hoàng Anh `0a8636cd` đã giao exact route và sửa Core migration test paths; Vinh đã chạy packaged public slice | Không giao lại route Lab-order hoặc migration-path fix; không suy full Core suite đã đạt |
| L1 discovery | `9b89a355`: Billing đã có source-bound GET, DTO, authorization/validation và PG tests | Đã có implementation; HTTP Billing thật qua Gateway và bàn giao fixtures vẫn còn |
| L2 selective publication | `1624cbd3`: cohort + eligible writer; V22/V23 fences giữ historical HELD | Đã có implementation/DB proof; chưa thay actual broker/recovery proof |
| G1 Gateway discovery | Allowlist hiện chưa có exact GET by-source | Hoàng Anh: mục H-VINH-01 bên dưới |
| L3/L4 publication delivery | Chưa có outpatient Rabbit integration/process recovery IT hoặc acceptance profile theo handoff | Lộc: mục H-VINH-03 bên dưới |
| V-CLOSE-03 | Actual paid EXAM/LAB_TEST và paid Rx chưa đóng | Vinh nhận sau producer + Gateway evidence; không inject grant hoặc SQL discovery |

## Verification refresh — 2026-10-16

Rerun tại `c8024921`, Java 21/Maven 3.9.16, Docker PostgreSQL thật cho Billing:

| Targeted verification | Tests | Failures / errors / skips |
|---|---:|---|
| Billing discovery service/controller/PG | 27 | 0 / 0 / 0 |
| Billing publication cohort + migration PG | 34 | 0 / 0 / 0 |
| Gateway care role matrix + public Lab-order web | 183 | 0 / 0 / 0 |
| Tổng | 244 | 0 / 0 / 0 |

Hai commands exit 0; đây không phải full module regression hoặc paid cross-service E2E.
Billing test `actualIssuanceAndPaymentPublishExactOriginalEvents` assert eligible outbox rows
bằng SQL, chưa chạy dispatcher/Rabbit. Không gọi persisted eligibility là broker delivery.
Logs: `%TEMP%/vinh-closeout-billing-check.log`, `%TEMP%/vinh-closeout-gateway-check.log`.
Reproduce từ root:

```powershell
mvn -q -pl backend/billing-service -am '-Dtest=PaymentRequestDiscoveryPostgresTest,PaymentRequestDiscoveryControllerTest,PaymentRequestDiscoveryServiceTest,OutpatientPublicationPostgresTest,OutpatientPublicationMigrationPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl backend/gateway -am '-Dtest=CareRouteAuthorizationTest,ClinicalLabOrderGatewayWebTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### H-VINH-01 — Hoàng Anh / Gateway discovery

**OPEN — G1**, public client → Gateway → Billing. Implement chính xác
`GET /api/v1/billing/payment-requests/by-source`, ADMIN/CASHIER signed human access only;
giữ POST `/{id}/payments`, không GET wildcard. Query bắt buộc: purpose/sourceId/patientId/
careEpisodeId, giữ nguyên query/correlation khi forward. Billing sở hữu validation/DTO.

Acceptance: authorized forward, unauthenticated/denied role/service/refresh, wrong method và
nested path không forward. Full Gateway tests; sau đó dùng actual Billing discovery/payment
qua Gateway. Downstream double chỉ là routing/security evidence.
Chi tiết duy nhất tại [G1](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md#g1--hoàng-anh--gateway-dependency-riêng-không-phải-việc-code-của-lộc).
Lab-order và migration-path fixes đã giao; không mở lại chúng thành task mới.

### H-VINH-02 — Huy / Pharmacy + Report; Vinh nhận consumer Clinical

**Implementation dependency resolved:** outpatient filled relationship + Clinical Rx V1 attach
đã có. Không yêu cầu Huy giao lại recordId/dispense fixture hoặc Vinh code lại attachment.
Actual paid Rx và Report/Support activation/coverage theo
[handoff chung Huy](HANDOFF-HUY-MVP.md); không dùng Clinical/Lab local tests để đóng Report.

### H-VINH-03 — Lộc / Billing; phối hợp slice với Vinh

**OPEN — L3/L4**, không làm lại L1/L2 đã merge. Thực hiện
[checklist producer còn lại](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md#l3--actual-rabbit-publication-và-recovery):
real dispatcher + confirm/returns, original broker bytes/hash, HTTP discovery/payment,
partial/full payment, duplicates, retry/quarantine và packaged JVM crash recovery.
Giao full Billing regression/package/recovery evidence, flags/catalog, actual IDs/hashes và
fixtures cho Vinh/Huy/Hoàng Anh. L1/L2 targeted tests chưa chứng minh mọi acceptance item đạt.
Không release historical HELD hoặc tự bật deployment gates để làm acceptance.

## Điều kiện chốt và frontend

Vinh hoàn thiện normal paid EXAM/LAB_TEST public slices: actual source → Gateway discovery →
HTTP payment → actual dispatcher/Rabbit grant → Clinical start-exam / Lab start-result và
Clinical result attachment; kiểm wrong target/episode/expiry/role, duplicate/recovery.
Paid Rx nhận actual Billing grant và original Pharmacy filled bytes. Chỉ đóng khi có source SHA,
commands, counts (không tính skip), IDs/body hashes và reconciliation; không dùng Billing SQL
để tìm request hoặc synthetic grant thay actual payment. Global M6 không tự đóng theo owner tests.

Frontend Vinh tiếp tục FE-VINH-MVP-01/04 trong appointment/medical-record/Lab theo canonical
ownership. V1 controls FE-02/03 vẫn chờ acceptance/runtime gates; không fallback V0 khi V1 lỗi.
Không đụng Pharmacy/Billing/Patient hoặc shared frame Huy đang triển khai.

## Lasting safety boundaries

Giữ compatibility-v0 default và outpatient-mvp opt-in; profile không tự mở all-purpose publication.
Clinical/Lab receipts lưu original bytes + operation/fingerprint; claim/effect/outbox atomic.
Giữ exact signed actor/assigned doctor và Clinical-owned sourceOrderId authority, không suy UUID.
Rollback pre-Clinical V7/pre-Lab V3: old consumers phải tắt sau receipt writes; pre-Clinical V5
outbox scheduler phải tắt sau HELD/quarantine writes. Không reset receipts, xóa evidence hoặc
release historical rows. Canonical rules ở contracts/reliability/service docs.

## Historical evidence retained

Các mục backlog lặp/sai baseline PR #366/#370/#375/#381 được bỏ khỏi handoff hoạt động.
Git history giữ nguyên evidence cũ; xem phiên bản file tại `c8024921` khi cần raw logs và các
lượt chạy cũ. Evidence gần nhất của implementation Vinh được giữ dưới đây, không tính là rerun hôm nay.

## Evidence lượt tiếp tục Vinh — 2026-10-16

Source: baseline `71e2684d` + branch `codex/vinh-rx-v1-public-lab-acceptance`.
Java 21.0.11, Maven 3.9.16, Docker 29.8.0; isolated PostgreSQL `16-alpine` và RabbitMQ
`3.13-alpine`. Không sửa production Billing/Pharmacy/Gateway, shared build hoặc deployment flags.

| Fresh verification | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Full Clinical `test` | 339 | 0 | 0 | 0 |
| Full Lab `test` | 216 | 0 | 0 | 0 |
| Clinical `process-recovery` (consumer Lab + Rx, outbox) | 3 | 0 | 0 | 0 |
| Lab `packaged-slice`, public Gateway create-order | 1 | 0 | 0 | 0 |
| Tổng, không cộng lại focused reruns | 559 | 0 | 0 | 0 |

Tất cả commands cuối exit 0. Package reactor Eureka/Core/Gateway/Clinical/Lab cũng exit 0.
Review độc lập không còn blocker/should-fix sau hai regression fixes: delayed first fill được
append vào completed outpatient record và precise decimal Rx snapshot fingerprint. Red run
đã tái hiện hai lỗi trước sửa; bảng chỉ ghi kết quả cuối. Lượt packaged đầu dừng vì assertion
context path đòi 403; actual Gateway không khai báo route đó nên trả 404. Đã đối chiếu route
source rồi assert exact 404; wrong-role/method/nested public command vẫn assert exact 403.

Rx tests đọc **trực tiếp original Pharmacy fixture bytes**, không chép/sửa producer để đạt test:
`prescription.filled.outpatient.v1.json`, SHA-256
`b0db35a17d80b1cbdb30d0de7b7549592bc960c67365363634e1f2c1366981a6`.
Fixture identities: prescription `10000000-0000-4000-8000-000000000001`, patient suffix `002`,
department `003`, visit `004`, record/dispense `005`; test riêng đổi dispense độc lập record để
chứng minh không suy ID. Chỉ seed Clinical-owned target của fixture, không query Pharmacy store.
PG/Rabbit chứng minh một attachment, event/new-event replay, snapshot conflict (kể cả decimal
precision), mismatch target, historical effect, concurrency, rollback và exact conflict bytes
trong durable DLQ. Consumer process test dùng actual packaged Clinical/inbox/attachment:
commit effect, giữ ACK, kill JVM, start PID mới, broker redelivers original body một lần, một effect.

Packaged Lab slice tạo staff/accounts/patient qua real Core/Gateway, login signed doctor;
check-in/start exam bằng audited **test-only EXAM override**, public POST Lab-order 201,
NURSE/wrong assigned doctor/wrong method/nested path 403, internal context public path 404.
Denied commands giữ order count=0; accepted command persist đúng record/patient/sourceOrderId.
Lab gọi service-only context bằng real signed delegation; unpaid start denied, exact synthetic
LAB_TEST grant unlock, result outbox → Rabbit → Clinical giữ một attachment; terminal edit denied.
Đây đóng **public Lab-order command/attachment acceptance**, không thay actual Billing payment.
Core full-suite ownership acceptance và global M6 vẫn theo owner; không tick toàn bộ H-VINH-01.

Reproduce từ repository root sau khi Docker sẵn sàng:

```powershell
mvn -q -pl backend/clinical-service,backend/lab-service -am test
mvn -q -pl backend/eureka-server,backend/core-service,backend/gateway,backend/clinical-service,backend/lab-service -am -DskipTests package
mvn -q -f backend/clinical-service/pom.xml -Pprocess-recovery failsafe:integration-test failsafe:verify
$taskRoot=(Get-Location).Path
mvn -q -f backend/lab-service/pom.xml -Ppackaged-slice test-compile `
  "-Dlab.acceptance.core.jar=$taskRoot/backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.gateway.jar=$taskRoot/backend/gateway/target/gateway-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.eureka.jar=$taskRoot/backend/eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.clinical.jar=$taskRoot/backend/clinical-service/target/clinical-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.lab.jar=$taskRoot/backend/lab-service/target/lab-service-0.0.1-SNAPSHOT.jar" `
  failsafe:integration-test failsafe:verify
```

Raw logs local: `%TEMP%/mediflow-vinh-final-tests.log`, `mediflow-vinh-package.log`,
`mediflow-rx-recovery.log`, `mediflow-public-lab-acceptance-final.log`; XML ở module targets.
Không cộng report ClinicalExamPackagedAcceptanceIT/Lab process cũ chưa rerun vào 559 tests.
