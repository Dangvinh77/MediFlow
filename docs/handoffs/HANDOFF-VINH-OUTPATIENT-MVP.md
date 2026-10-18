# Vinh Clinical/Lab outpatient MVP closeout

## Nghiệm thu owned backend — 2026-10-18

Vinh đã hoàn thiện phần Billing/Notification theo ủy quyền người dùng để đóng phụ thuộc của
Clinical/Lab. Baseline `e44ddf487523f8e781e53196d28f17db32beed1e`, working branch
`codex/vinh-loc-billing-closeout`; source diff/hash và dữ liệu test có trong
[bundle nghiệm thu](../../backend/billing-service/acceptance-evidence/2026-10-18/README.md)
và [manifest](../../backend/billing-service/acceptance-evidence/2026-10-18/manifest.json).
Mục này thay các trạng thái LOC-04/05 và integrated Clinical/Lab recovery còn mở bên dưới.

V-CLOSE-03 đã có actual paid EXAM/LAB_TEST qua Gateway, đúng source công khai, Billing thật,
Rabbit thật, hoàn tất khám và ba điểm kill/restart sau commit trước ACK: Clinical EXAM
clearance, Lab clearance, Clinical Lab-result attachment. Cùng JAR/stores nhận lại exact bytes,
PID mới, unacked trước kill, một inbox/effect, queue ready/unacked 0 sau recovery.
Không inject clearance hay SQL discovery. Paid Rx và Pharmacy restart giữ nghiệm thu Huy
17/10 đã dẫn ở lịch sử; standalone Clinical Rx consumer recovery được chạy lại trong lượt này.

Fresh exit 0, không failure/error/skip: Clinical 339, Lab 216, Billing 1060, Notification 279,
Gateway 1449 Surefire tests. Failsafe tách riêng: Billing recovery 1; Clinical EXAM 1 và
consumer recovery 2; Lab consumer recovery 1; integrated paid Lab/Clinical recovery 1.
Không cộng targeted reruns vào full counts. First/retry payment data nay phải bằng nhau chính xác,
không còn tolerance dưới 1µs; expiry và original authority grant giữ full precision.

V-CLOSE-01/02/03 và Billing LOC-01–05 có đủ bằng chứng trong phạm vi được giao; LOC-06 dùng
fresh Billing producer regression cộng actual Huy acceptance được dẫn rõ nguồn.
Global rollout/activation toàn MVP vẫn là quyết định phối hợp riêng; không bật deployment
flags, không nghiệm thu thay full Core/Pharmacy mới hoặc deferred Inpatient/Surgery.
Các mục bên dưới giữ lịch sử từng lượt chạy, không ghi đè nghiệm thu mới này.

**Cập nhật:** 2026-10-17, audit tại `d80d23b2`; giữ các gate consumer/release chưa nghiệm thu mở.
**Trạng thái:** owned implementation đã có; actual paid EXAM/LAB_TEST slices đạt;
**paid Rx đã có evidence tích hợp Huy ngày 2026-10-17; integrated recovery / release toàn MVP còn mở**.
Production scope của Vinh: Clinical/Lab. Inpatient/Surgery deferred.

## Owner queue trước nghiệm thu 2026-10-18

| Gate | Source và kết luận | Việc còn lại / owner |
|---|---|---|
| V-CLOSE-01 reliability | Standalone Clinical/Lab PG/Rabbit/outbox/consumer JVM recovery đã có | Không làm lại; integrated paid recovery thuộc V-CLOSE-03 |
| V-CLOSE-02 Rx V1 | Huy đã giao outpatient recordId/dispense relationship; Clinical consumer đã merge ở `c4cf810f`; actual sáu-app paid Rx/Clinical attachment đạt ngày 2026-10-17 | Không giao lại implementation hoặc happy path; integrated paid recovery vẫn mở |
| Public Lab-order | Hoàng Anh `0a8636cd` đã giao exact route và sửa Core migration test paths; Vinh đã chạy packaged public slice | Không giao lại route Lab-order hoặc migration-path fix; không suy full Core suite đã đạt |
| L1 discovery | `9b89a355`: Billing đã có source-bound GET, DTO, authorization/validation và PG tests | Actual paid EXAM Gateway/Billing slice PASS 2026-10-17; không thay full Billing acceptance |
| L2 selective publication | `1624cbd3`: cohort + eligible writer; V22/V23 fences giữ historical HELD | Đã có implementation/DB proof + partial broker/recovery PR #401; chưa đóng toàn L3 |
| G1 Gateway discovery | Source đã có exact GET by-source cho ADMIN/CASHIER; #417 sửa Billing error correlation | Không giao lại implementation; full G1 owner evidence theo H-VINH-01 |
| L3/L4 publication delivery | LOC-03 continuation 2026-10-18: controlled NACK/timeout + PG fencing/replay đạt; full Billing 1056 + packaged recovery 1, 0 failure/error/skip | LOC-04 timestamp decision và L4 full artifacts còn mở; xem handoff Lộc |
| V-CLOSE-03 | Actual paid EXAM/LAB_TEST + outpatient completion PASS 2026-10-17; paid Rx/Clinical attachment có evidence Huy cùng ngày | Không inject grant hoặc SQL discovery; full integrated recovery vẫn mở |

Refresh ngày 2026-10-18: đối chiếu [Huy integrated closeout](HANDOFF-HUY-MVP.md#10-integrated-backend-closeout--2026-10-17)
và [runbook mục 8](../plans/huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).
Các dòng paid Rx chưa chạy trong lịch sử verification bên dưới chỉ áp dụng lượt Vinh trước đó.
Không suy bài sáu-app đã nghiệm thu paid Lab/integrated Clinical-Lab JVM recovery hoặc global release.
Người dùng giao Vinh tiếp tục Billing/Notification của Lộc trong tác vụ này; tiến độ producer LOC-03
được ghi tại [handoff Lộc](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md).

## Fresh Vinh source verification — 2026-10-17

Baseline `d80d23b2` + working changes in Clinical/Lab acceptance and Vinh frontend.
Java 21.0.11, Maven 3.9.16, Docker 29.8.0, PostgreSQL `16-alpine`, RabbitMQ `3.13-alpine`.
Package fresh Eureka/Core/Gateway/Billing/Clinical/Lab; không sửa foreign production modules,
root build hoặc deployment flags. Billing flags/catalog chỉ bật trong disposable child processes.

| Verification | Tests | Failures / errors / skips |
|---|---:|---|
| Full Clinical `test` | 339 | 0 / 0 / 0 |
| Full Lab `test` | 216 | 0 / 0 / 0 |
| Clinical `packaged-acceptance`, actual paid EXAM | 1 | 0 / 0 / 0 |
| Lab `packaged-slice`, actual paid EXAM/LAB_TEST + completion | 1 | 0 / 0 / 0 |
| Frontend full Vitest, one worker | 327 | 0 / 0 / 0 |

Commands cuối của các rows trên exit 0. Frontend typecheck/lint/build exit 0, independent review
không có finding cần sửa. Không cộng process-recovery reports cũ vào counts lượt này.

EXAM đi toàn bộ source check-in → public Gateway exact-source discovery → signed CASHIER payment
→ actual Billing dispatcher/Rabbit → Clinical clearance/READY → assigned doctor start-exam.
Kiểm unpaid/role/assigned doctor/concurrent check-in/repeated start và one record/outbox;
payment retry giữ nguyên transaction và mọi business field, thời gian dưới 1µs như note Billing.
Chỉ đọc Clinical-owned tables để đối soát receipt/effect/outbox, không đọc Billing business DB.

EXAM identities: appointment `2eed353a-44f7-4d7a-ac8f-45ddaa3e3677`, record
`2031fd09-dcd9-44e6-b87b-b2efb1b98538`, payment request
`31dddcdb-2cac-4162-8573-18afe4715236`, transaction
`bb8bbf24-6291-467d-a0b0-f6ea6874f24b`, clearance
`54af2767-4348-4404-a088-39efda4ff003`, event
`48597459-46ef-46a9-a7e3-acf6a1d5b707`. Original grant body SHA-256:
`7acec4acef2994349e2d091ae24c1794606cce4e4be0bdb00e0ed7c066bb8380`.
Local evidence `backend/clinical-service/target/paid-exam-evidence.json` và failsafe XML.

Lab slice không dùng test-only EXAM override hoặc synthetic LAB_TEST grant nữa. Tạo Clinical
sourceOrderId qua public API, Lab xác thực source bằng signed service call, unpaid start bị chặn;
thanh toán EXAM 100000 VND và LAB_TEST 150000 VND qua Gateway/Billing. Phát lại original received
clearance bytes hai lần vẫn một clearance/inbox effect. Public Lab start/result → Rabbit → một
Clinical attachment; final result edit bị chặn. Chỉ assigned doctor hoàn tất OUTPATIENT_FOLLOW_UP;
nurse/wrong doctor bị từ chối, record/appointment COMPLETED, một completion outbox, terminal edit bị chặn.

Đối soát quarantine đúng MVP: `lab.q` rỗng, `lab.dlq` đúng hai `payment.completed` receipts
của hai giao dịch, kiểm transaction/request/account/amount/patient/episode. Đọc original bytes
rồi nack/requeue, không purge/ACK-drop; không có unexpected clearance DLQ. Receipt không thay V1 grant.
Đây là assertion mới cần thiết khi thay synthetic payment bằng Billing thật; production consumer giữ nguyên.

Lab identities: appointment `a73208a5-fead-45a3-a8e4-069a65274510`, record
`2511e359-2cda-4d1a-85de-e0c912497d4f`, sourceOrderId
`646503fd-64ee-41da-a5ea-e1e4b3487265`, labTestId
`1150b0e1-11ba-4c78-b7ec-0137295ab35c`, LAB payment request
`d30cfe94-acee-487b-a8a9-792f2e6e3933`, transaction
`95d15803-361a-439e-a4f8-f3e7f459edec`, clearance
`4bba292e-8a6e-427a-a9f1-9113ce7ece3a`, grant event
`bc9945e6-d3d1-4e10-9767-94545f96b835`, result event
`685f76de-60bf-4792-943f-7b2758669aad`.
Local `backend/lab-service/target/paid-lab-evidence.json`, original `paid-lab-clearance.json`
SHA-256 `fbd2b2cda13e232f9f2dd68daac23cedd5d7d31e49d9ee5639702251e86e4786`.
Original quarantine receipt files `quarantined-payment-{transactionId}.json`:
EXAM transaction `f61f5c87-5e56-4f4a-ac52-70da2bf14bae`, hash
`d787c8efba7e8e5b0fd61eea426a34bfbc33a8b879b772dc16fcd9b8e195de15`;
LAB transaction ở trên, hash
`aceb554c610d6f406ba3ebf1bf0b3fd576dc553acdb2812df22d70aad048eaca`.

Reproduce EXAM sau khi package dependencies từ cùng source:

```powershell
mvn -q -pl backend/eureka-server,backend/core-service,backend/gateway,backend/billing-service,backend/clinical-service,backend/lab-service -am -DskipTests package
mvn -q -f backend/clinical-service/pom.xml -Ppackaged-acceptance test-compile failsafe:integration-test failsafe:verify
$taskRoot=(Get-Location).Path
mvn -q -f backend/lab-service/pom.xml -Ppackaged-slice test-compile `
  "-Dlab.acceptance.core.jar=$taskRoot/backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.gateway.jar=$taskRoot/backend/gateway/target/gateway-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.eureka.jar=$taskRoot/backend/eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.clinical.jar=$taskRoot/backend/clinical-service/target/clinical-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.lab.jar=$taskRoot/backend/lab-service/target/lab-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.billing.jar=$taskRoot/backend/billing-service/target/billing-service-0.0.1-SNAPSHOT.jar" `
  failsafe:integration-test failsafe:verify
```

Logs local `%TEMP%/mediflow-vinh-{audit-backend,paid-exam,paid-lab}.log`;
frontend `%TEMP%/mediflow-vinh-final-{frontend-tests,typecheck,lint,build}.log`.
Lượt đầu EXAM đụng ephemeral port và sau đó khác timestamp precision; sửa reservation/check như
audit plan. Lab harness thêm mapping Billing registration và kiểm quarantine receipts đúng MVP.
Chỉ các lượt cuối PASS được tính. Tổng backend fresh 557 tests, không cộng historical recovery.
Không đóng paid Rx, integrated recovery, full G1/L3/L4 hoặc signed-role browser/six-app release.

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

**Implementation delivered; full G1 acceptance còn mở.** Audit 2026-10-17 thấy exact
`GET /api/v1/billing/payment-requests/by-source` trong RouteAuthorizationFilter, ADMIN/CASHIER.
Không yêu cầu Hoàng Anh code lại route. Giữ POST `/{id}/payments`, không GET wildcard.
Query bắt buộc: purpose/sourceId/patientId/careEpisodeId, giữ nguyên query/correlation khi forward.
Billing sở hữu validation/DTO; PR #417 đã sửa correlation cho lỗi thiếu dữ liệu discovery.

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

**Phiếu thực thi hiện hành cho Lộc:** [mục 0, LOC-01 → LOC-06](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md#0-phiếu-thực-thi-hiện-hành--2026-10-17-lộc-đọc-mục-này-trước).
Đối chiếu source thấy unroutable/retry/quarantine/admin replay/expired lease/equal-timestamp
đã có tests; không giao làm lại. Còn duplicate new-event/historical HELD broker proof,
NACK/timeout riêng và audit stale-owner fencing/replay eligibility, fresh artifact bundle,
timestamp decision và Billing package cho paidRx. Các yêu cầu failure/ordering bên dưới là
acceptance tổng; không có nghĩa mọi case đều chưa được implement.

Audit HTTP payment/replay 2026-10-17: cùng transactionId/accountId/amount/status nhưng
`completedAt` của first response `2026-10-17T10:04:52.023375300Z`, replay
`2026-10-17T10:04:52.023375Z`. PostgreSQL lưu microseconds; Vinh acceptance so mọi field khác
chính xác, riêng thời gian chênh dưới 1 microsecond. Lộc cần xác nhận/chuẩn hóa response precision
nếu contract yêu cầu replay byte-equivalent; không suy payment bị thực hiện lần hai.
Không chỉnh production Billing trong scope Vinh.

**PARTIAL — L3/L4**. PR #401 đã giao actual Rabbit tests, process recovery và discovery fixtures;
không làm lại L1/L2 hoặc happy-path/recovery đã có. Hoàn thiện
[checklist producer còn lại](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md#l3--actual-rabbit-publication-và-recovery):
unroutable/NACK/confirm timeout, bounded retry/quarantine, expired lease/stale owner,
admin replay eligibility và equal-timestamp/multi-request ordering.
L3 duplicate-new-event/historical HELD coverage phải đối chiếu đúng tầng test; không suy từ
case payment retry/deferred admission.
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
