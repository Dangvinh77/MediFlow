# Lab normal execution — bản sửa và rehearsal 25/10/2026

Ngày kiểm chứng: 24/10/2026, Asia/Saigon. Nhánh: `codex/fix-lab-clearance-identity`.
Base sau `git fetch origin master`: `64a1f04340efe07d324a1335d44c2b90baac68fd`.
Bản vá được giao trong nhánh PR trên. Không dùng SHA base để nói binary gốc đã hết lỗi.

## Root cause và phạm vi sửa

`LabApplicationService.onFinancialClearance()` lưu `clearanceTargetId=UUID.randomUUID()` làm khóa
projection, còn `testId` là định danh nghiệp vụ riêng. Adapter đã kiểm request bằng `grant.testId()`
nhưng lại kiểm thêm request bằng `grant.clearanceTargetId()`. Projection hợp lệ vì vậy bị từ chối
trước HTTP Billing, ảnh hưởng cả `start()` và `requireCurrent()` khi nhập kết quả.

Bản sửa chỉ thay điều kiện thứ hai thành `grant.clearanceTargetId() == null`. Không ép hai ID
giống hoặc khác nhau. `purpose=LAB_TEST`, outpatient context, Billing execution receipt/current lookup,
source/patient/account/invoice/episode/timing/correlation và role/state guards giữ nguyên.
Execution-start giữ `operationId=sourceId=testId`. Results vẫn gọi `executionAuthority.requireCurrent()`.
Không migration, không sửa consumer key hoặc database rows. Phần Lab không đổi Billing/Gateway/frontend;
follow-up Billing riêng được ghi trong [FOLLOWUP-VERIFICATION.md](FOLLOWUP-VERIFICATION.md).

Khi chạy thật đã phát hiện thêm lỗi độc lập ở bước results: PostgreSQL làm tròn `granted_at`
về microsecond, trong khi Billing trả nguyên `occurredAt` nanosecond của envelope. Start đã nhận
HTTP 200, nhưng lookup khi ghi kết quả bị guard thời gian từ chối. `LabClearancePersistenceAdapter`
được sửa để đọc lại `grantedAt`/`expiresAt` nguyên gốc từ inbox bất biến của chính Lab, khóa bằng
consumer + eventId + route + operation key của clearance. Dữ liệu cũ không có inbox giữ fallback
TIMESTAMPTZ. Không thay strict equality trong adapter, không thêm tolerance và không nới expiry/freshness.

## Regression và red/green

Fixture adapter dùng projection ID `00000000-0000-0000-0000-000000000040` và test ID
`00000000-0000-0000-0000-000000000041` ổn định, độc lập.

| Lượt chạy | Kết quả thực tế |
|---|---|
| Chỉ đổi fixture, production chưa sửa | 8 tests: 2 failures, 3 errors, 0 skipped |
| Thêm regression và nối tầng, production chưa sửa | 16 tests: 11 failures, 4 errors, 0 skipped |
| Sau sửa ID, gồm ca hai ID bằng nhau/null | 18 tests: 0 failures/errors/skipped |
| Full Lab suite sau phục hồi Docker | 255 tests: 0 failures/errors/skipped |
| Lượt cuối, gồm regression thời gian | Adapter 19/19; full Lab 257/257; 0 failures/errors/skipped |

Các ca đỏ tiêu biểu: `start_sendsStableExactTestIdentityAndAcceptsOnlyMatchingDurableReceipt`,
`start_acceptsOriginalReceiptTimestampOnAnIdempotentRetry`,
`requireCurrent_acceptsOnlyExactCurrentOutpatientGrant`,
`consumerProjectionCanStartAndCompleteThroughRealExecutionAdapter`.
Các ca revoked/conflict giờ verify Billing lookup thực sự được gọi; trước đây có thể pass
do bị local guard từ chối sai. Các ca source/context sai, malformed response, expired grant,
stale observation đều tiếp tục bị chặn. Request khác `grant.testId()` bị chặn trước HTTP,
kể cả khi request bằng projection ID. Test retry gọi adapter hai lần với cùng operation/source ID,
nhận receipt có timestamp gốc; không mở lại public start từ `IN_PROGRESS`.

Test nối tầng dùng application service, domain và execution adapter thật với HTTP client được mock:
`onFinancialClearance()` → projection được capture → READY → start → IN_PROGRESS → results → COMPLETED.
Đây là regression nối tầng local; bằng chứng PostgreSQL/RabbitMQ nằm ở packaged acceptance riêng.

Regression thời gian riêng trên PostgreSQL, sau `entityManager.clear()` để bắt buộc reload:
`clearanceReloadRetainsOriginalNanosecondTimesFromItsInboxEvidence` đỏ trước sửa persistence:
expected `2026-10-24T16:23:49.467347200Z`, actual `2026-10-24T16:23:49.467347Z`.
Adapter có thêm ca từ chối một grant time khác; không chấp nhận sai thời điểm để che lỗi lưu trữ.

Log đỏ và lần packaged fixture cũ thất bại được giữ trong [evidence/lab-identity-20261024](evidence/lab-identity-20261024).

## Lệnh kiểm chứng

Chạy tại root repository bằng PowerShell:

```powershell
git fetch origin master
git rev-parse origin/master
node scripts/changelog.js --summary
mvn -q -pl backend/lab-service -am '-Dtest=LabBillingExecutionAuthorityAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl backend/lab-service -am test
mvn -q -pl backend/core-service,backend/gateway,backend/eureka-server,backend/clinical-service,backend/lab-service,backend/billing-service -am -DskipTests package
$billingJar = Join-Path $PWD 'backend/billing-service/target/billing-service-0.0.1-SNAPSHOT.jar'
mvn -q -pl backend/lab-service -am -Ppackaged-slice '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dlab.acceptance.billing.jar=$billingJar" verify
```

Profile `packaged-slice` cung cấp năm JAR còn lại; tham số Billing JAR phải truyền rõ.
`mvn test` không chạy `PackagedOutpatientLabAcceptanceIT` và không thay thế lệnh `verify` trên.

Để rehearsal và lưu evidence của chính máy trình bày:

```powershell
& ./scripts/demo_25_10/Run-LabNormalAcceptance.ps1
```

Script dừng khi Maven/Docker lỗi, kiểm tests/failures/errors/skipped trong Failsafe XML và
kiểm thời gian tạo XML cùng hai file JSON để từ chối evidence cũ. Mỗi lần chạy lưu base SHA,
bản vá Lab, logs và reports trong thư mục evidence có timestamp riêng.

## Packaged runtime hiện tại

**PASS**, runner kết thúc khoảng 23:33 ngày 24/10/2026. Evidence mới tại
[evidence/20261024-232903](evidence/20261024-232903). Failsafe thực thi
`PackagedOutpatientLabAcceptanceIT`: **1 test, 0 failures, 0 errors, 0 skipped**, 170,9 giây.
Script kiểm freshness của XML và paid-lab JSON; không dùng evidence lịch sử ngày 18/10.

Ca fresh đã chạy đủ thanh toán thật → READY → start IN_PROGRESS → results COMPLETED →
Clinical attached result → hoàn tất record. Lab log: execution-start HTTP 200 lúc 23:33:01.310,
lookup HTTP 200 lúc 23:33:01.560. Adapter vẫn kiểm durable receipt và authority hiện hành.

| Định danh của lượt PASS | Giá trị |
|---|---|
| Clinical sourceOrderId | `41645810-0cd2-45fa-9f41-075ae39dddb0` |
| Lab testId | `d4cd97e9-8164-4c03-bfe1-1bb63a075187` |
| Lab projection ID | `4cc5c97a-c47f-4aa6-84fd-9b5614abdead` |
| Billing clearanceId | `6843ff12-d12e-49e0-9f75-72339e49829f` |
| Clinical recordId | `391fa8a9-e2a6-4ac4-b4a6-fcee7253842a` |
| Lab result eventId | `75a4c6b9-b8e9-454f-b29e-91819378f229` |

`paid-lab-evidence.json` giữ hai payment, clearance `emergencyOverride=false`, result event và
completed record. `lab-projection-observation.json` là SELECT chỉ đọc trên store Lab trong lượt chạy,
cho thấy hai ID độc lập, status COMPLETED/resultVersion 1. `service-logs/lab.log` giữ HTTP status;
`jar-sha256.json` khóa sáu binary thực tế. `integrated-recovery/*/result.json` chứng minh ba lần
kill sau commit trước ACK đã redeliver, inbox chỉ một row, queue ready/unacked về 0; bao gồm
Clinical nhận result đúng một lần. Không SQL UPDATE state/ID hoặc clearance giả.

Runtime cũng tái hiện precision loss thật: event grant `2026-10-24T16:32:32.246097700Z`,
TIMESTAMPTZ `2026-10-24T16:32:32.246098Z`; results vẫn đạt nhờ đọc instant gốc từ inbox,
không làm tròn Billing response hay bỏ so khớp.

Harness tạo ca mới qua Gateway: nhân viên/account/patient → appointment/check-in → trả EXAM thật
→ khám → Clinical source order → Lab request → Billing thu đủ → clearance Rabbit → READY
→ start IN_PROGRESS → results COMPLETED → Clinical attached result. Giá trong harness isolated:
EXAM 100.000 VND, LAB-CBC 150.000 VND; không thay bảng giá LAB 80.000 VND của cấu hình demo chính.

Harness dùng PostgreSQL 16 và RabbitMQ 3.13, actual JAR processes Core/Clinical/Lab/Billing/Gateway/Eureka.
Không emergency override, không fabricated clearance, không SQL chỉnh state/ID. Replay kiểm idempotency
dùng exact bytes của clearance Billing thật đã nhận. Harness có đọc store để assert effects và tạo
database isolated; đó không là cách client truy cập dữ liệu liên context.

## Lỗi khác đã phát hiện

- Ban đầu Docker daemon không chạy, socket `sailor-ingest.sock` cũ không truy cập được.
  Full suite lúc đó: 245 tests, 0 assertion failures, 2 container errors, 19 skipped; không phải pass.
  Đổi tên thư mục runtime socket cũ sang `run-before-lab-20261024230132`, khởi động lại Docker;
  giữ nguyên business databases/volumes. Suite chạy lại đạt 255/255, không skip.
- Lần packaged đầu ngày 24/10 dừng trước Lab do `createStaff()` trong harness thiếu
  `identityNumber`, Core trả `400 VALIDATION_ERROR`. Đã đối chiếu live `CreateStaffRequest`
  và bổ sung 12 chữ số trong fixture của Lab. Đây là lỗi fixture acceptance lỗi thời, khác lỗi ID;
  không sửa Core production hoặc nới validation.
- Lần packaged tiếp theo dừng ở start-exam: Clinical trả `503 UPSTREAM_UNAVAILABLE`.
  Billing log ghi `NoResourceFoundException` cho execution-start route vì harness chưa bật
  `mediflow.billing.clearance-lookup.enabled` (default false). Đã bổ sung cờ vào launch arguments
  của harness Lab; không sửa Billing production, không retry bằng override. Cẩm nang demo chính
  đã có `MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED=true`. Lần thất bại này được lưu trong
  `evidence/20261024-230658`; runner dừng đúng, không tạo PASS từ reports lịch sử.
- Các lượt sau đã start Lab được (Billing execution-start HTTP 200), nhưng ghi results 422 vì
  mất nanosecond khi reload projection. Lookup trực tiếp tại thời điểm đó `exists=true`, `eligible=true`;
  log Feign cho thấy cả start/lookup đều 200. Bằng chứng thất bại này ở
  `evidence/runtime-time-mismatch-20261024`; nó khác lỗi ID local và đã dẫn tới sửa persistence
  theo contract giữ instant gốc. Không sửa Billing ledger hoặc dùng override.
- Lượt đầu còn lỗi legacy `medicalrecord.created` fee listener: `Khoản phí phải có khoa
  và loại phí xác định`. Theo yêu cầu follow-up, listener đã được sửa và kiểm tra riêng trước PR;
  xem [FOLLOWUP-VERIFICATION.md](FOLLOWUP-VERIFICATION.md) cho regression và kết quả chạy mới.

## Dùng bộ demo ngày 25/10

1. Build/chạy JAR Lab có bản vá này; không dùng JAR baseline chỉ vì tên file giống nhau.
2. Chạy script rehearsal, đọc `result.txt` và fresh paid-lab evidence trong thư mục mới.
3. Trên môi trường UI trình bày, làm LAB-01 đến LAB-06 của cẩm nang bằng một ca mới,
   có thanh toán đầy đủ và đúng signed roles; lưu IDs/correlation/ảnh thực tế.
4. SQL đi kèm là snapshot synthetic lịch sử tạo ở baseline. Các ca hoàn tất mang audit
   `EMERGENCY_DEMO` vẫn giữ nguyên; không đổi tên/authority thành normal sau sửa code.
   Chỉ phần comment/manifest được cập nhật để giải thích bản vá. Không ép `clearance_target_id=test_id`.
5. Packaged proof bao phủ Core/Clinical/Lab/Billing và Gateway/Eureka. Rehearsal riêng của sáu
   deployments, restore SQL và browser/client checks được ghi trong FOLLOWUP-VERIFICATION.md;
   không suy ra mọi hoàn tiền, Pharmacy dispense hoặc native device đều đã đạt.

## File thay đổi

- `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/client/LabBillingExecutionAuthorityAdapter.java`
- `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/adapter/LabClearancePersistenceAdapter.java`
- `backend/lab-service/src/test/java/com/mediflow/lab/infrastructure/client/LabBillingExecutionAuthorityAdapterTest.java`
- `backend/lab-service/src/test/java/com/mediflow/lab/infrastructure/persistence/LabPersistenceAdapterTest.java`
- `backend/lab-service/src/test/java/com/mediflow/lab/acceptance/PackagedOutpatientLabAcceptanceIT.java`
- `scripts/demo_25_10/MediFlow_Demo_4_Dev_Chi_Tiet_2026-10-25.md`
- `scripts/demo_25_10/MediFlow_Demo_Database_2026-10-25.sql` (comments/manifest)
- `scripts/demo_25_10/Run-LabNormalAcceptance.ps1`
- Tài liệu này và evidence kèm theo. Hai file demo có sẵn là untracked lúc bắt đầu; đã giữ lại
  schema/data/audit và đưa vào cùng PR theo yêu cầu tiếp theo của người dùng.
