# Huy MVP — baseline, source manifest và evidence batch đầu

Ngày 2026-10-14. Baseline: `5f40b32c6c24333ff46f9ae9826ffdd9403ba788` (Huy đã nhận master `86efe636`).
Worktree trước implementation chỉ có plan Huy và index docs chưa commit; đã giữ nguyên.
Thay đổi batch này chưa commit/push. Đây là evidence/workboard của
[plan Huy](2026-10-14-huy-backend-mvp-adjustment-plan.md), không schema/approval mới.

## 1. Phân loại source và migrations

| Phần | Mode | Reuse / gap |
|---|---|---|
| PrescriptionApplicationService / DispenseApplicationService / PaymentCompletedConsumer | V0 | Giữ regression và rollback; không dùng làm fallback V1 |
| ContextCheckedPrescriptionCreationService → CarePrescriptionCreationService | Outpatient V1 kernel | Có context/identity proof, receipt/fingerprint, ordered stock locks; batch này bổ sung driving API opt-in |
| CareDispenseTransactionService / PrescriptionClearanceAuthorizationService | V1 kernel | Batch 2 nối public lifecycle; current revocation/PG acceptance còn mở H6-05 |
| PrescriptionCareEventWriterAdapter | V1 HELD | `delivery_enabled=false`; không release historical rows hoặc mở publisher V1 |
| Pharmacy Admission consumer/authority | Deferred | Bị exclude trong mvp-outpatient, dù property/global override true |
| ReportEventConsumer + daily/monthly/top-medicines | V0 | Flat payload; legacy zero-fill/revenue labels giữ semantics, không coi đó là V1 coverage |
| CashReceipt/Refund services + replay | V1 kernel | Reuse ở H6-08/09, không earned revenue |
| Report mixed operational / settlement / final recognition / publication/read/archive APIs | Deferred | Config và endpoints full V2 exclude trong mvp-outpatient; kernel/data giữ nguyên |
| Report/Notification Boot applications | Current separate deploys | Chưa có Support host; H6-10/11 mở, không chuyển ownership Notification |

Đã chụp SHA-256 của **62 file migration/fixture hiện hữu** trong
[protected-files manifest](huy-backend-mvp-protected-files.json). Không sửa released migrations,
DDL, checksum, original producer fixtures hoặc thực hiện Flyway repair.
Tên Java/JSON/SQL của Pharmacy/Report tiếp tục English như source.

## 2. Public API inventory phần dùng lại

Các DTO dưới đây nằm trong `backend/<module>/src/main/java/com/mediflow/<context>/application/dto/`.
Giữ `ApiResponse`, correlation/error envelope và price/money/UUID/date types từ source.

| Method/path | Roles | Request → response | Mode/gate |
|---|---|---|---|
| GET /api/v1/pharmacy/drugs | ADMIN/DOCTOR/PHARMACIST | keyword,page,size → PageResult<DrugDTO> | Catalogue hiện hành |
| GET /api/v1/pharmacy/drugs/{id} | ADMIN/DOCTOR/PHARMACIST | UUID → DrugDTO | Catalogue hiện hành |
| POST /api/v1/pharmacy/drugs | ADMIN/PHARMACIST | CreateDrugRequest → DrugDTO, 201 | Server catalogue |
| PUT /api/v1/pharmacy/drugs/{id}/stock | ADMIN/PHARMACIST | AdjustStockRequest → DrugDTO | Ledger/audit hiện hành |
| POST /api/v1/pharmacy/prescriptions | ADMIN/DOCTOR | CreatePrescriptionRequest → PrescriptionDTO, 201 + Location | V0 giữ ở compatibility; V1 opt-in theo mục 3 |
| GET /api/v1/pharmacy/prescriptions/{id} | ADMIN/DOCTOR/PHARMACIST | UUID → PrescriptionDTO | Read giữ nguyên wire; không suy paymentConfirmed thành quyền V1 |
| PUT /api/v1/pharmacy/prescriptions/{id}/cancel | ADMIN/DOCTOR | CancelPrescriptionRequest(reason) → CancelPrescriptionResult | MVP gated V1; default compatibility V0 |
| PUT /api/v1/pharmacy/prescriptions/{id}/dispense | ADMIN/PHARMACIST | Signed actor + UUID → DispenseDTO | MVP gated V1; default compatibility V0, no fallback |
| POST /api/v1/pharmacy/admin/outbox/{eventId}/replay | ADMIN | UUID → replay result DTO nguồn | Không là authority release HELD |
| GET /api/v1/reports/daily | ADMIN/MANAGER | date,departmentId? → DailyReportDTO | Legacy compatibility |
| GET /api/v1/reports/monthly | ADMIN/MANAGER | month,year,departmentId? → MonthlyReportDTO | Legacy compatibility |
| GET /api/v1/reports/top-medicines | ADMIN/MANAGER | fromDate,toDate,departmentId?,limit → List<TopMedicineDTO> | Legacy compatibility |

Full-V2 Report inventory giữ ở source controllers và baseline commit: FinancialSnapshotReportController,
FinancialRebuildController, FinancialPublicationController, FinancialSourceArchiveController,
OperationalPublicationController, OperationalRebuildController, OperationalSnapshotReportController,
ReportCapabilityController. Không thuộc MVP allowlist mới; chưa có replacement reader H6-09.

CreateDrugRequest giữ drugName/activeIngredient/unit/price/stockQuantity/expiryDate/manufacturer/
lowStockThreshold; AdjustStockRequest giữ quantity/reason. CreatePrescriptionRequest giữ
recordId/patientId/doctorId/departmentId/prescribedDate/lines(drugId,quantity,dosage), optional
careContractVersion/careContext/careEpisodeType/careEpisodeId/admissionId/priceCode.
V1 là OUTPATIENT + OUTPATIENT_VISIT, recordId và visit episode là hai reference riêng;
không thêm giá client, commandId hoặc actor field vào JSON.

PrescriptionDTO giữ prescriptionId/recordId/patientId/doctorId/departmentId/prescribedDate/
totalAmount/lines/status/dispenseStatus/paymentConfirmed/cancelledAt/cancelledBy/
cancellationReason/createdAt/updatedAt. Không đổi response envelope hoặc semantics legacy.
Line DTO/DrugDTO/terminal DTO chính xác theo record nguồn, không copy thành schema độc lập ở docs.

## 3. Batch này thêm gì vào activation/API

Profile **`mvp-outpatient`** có file module riêng cho Pharmacy và Report; không phải root six-app
runtime profile, không tạo Core/Support, không cấu hình Gateway/Eureka hoặc bật producer.

- Pharmacy Patient/Organization discovery lấy `mediflow.pharmacy.patient.service-id` và
  `mediflow.pharmacy.organization.service-id`. Defaults giữ patient-service/organization-service;
  profile chọn core-service. Feign contextId và signed logical caller pharmacy-service không đổi.
  Base-url override cũ giữ nguyên; khi switch phải audit override, không chỉ đổi discovery name.
- Clinical vẫn clinical-service và exact `/api/v1/records/{id}/prescription-context`;
  source identity paths `/api/v1/patients/{id}/exists`, `/api/v1/org/staff/{id}/lookup`,
  `/api/v1/org/staff/{id}/exists`, `/api/v1/org/departments/{id}/lookup` giữ nguyên.
- Public V1 creation/lifecycle chỉ có bean khi **profile + cả sáu gate true** (batch 7):
  `mediflow.features.care-finance-v2`, `mediflow.pharmacy.identity.enabled`,
  `mediflow.pharmacy.clinical-context.enabled`, `mediflow.pharmacy.outpatient-api.enabled`,
  `mediflow.pharmacy.clearance-consumer.enabled`, `mediflow.pharmacy.clearance-authority.enabled`.
  Gate mới mặc định false (`MEDIFLOW_PHARMACY_OUTPATIENT_API_ENABLED`). Thiếu checked writer
  khiến wiring fail-closed, không lấy legacy writer thay thế.
- POST V1 dùng **`Idempotency-Key: <canonical UUID>`** (header mới chỉ cho V1);
  trim/case canonical UUID được chấp nhận, short UUID/missing/blank không tạo command ID ngẫu nhiên.
  Header chuyển tới receipt kernel như commandId; actor và intent vẫn được fingerprint/guard nguồn.
  Header không phải correlationId. V0 compatibility không bắt buộc header.
- V1 dispatch sang PublicOutpatientPrescriptionCreationService → context-checked writer;
  reader trả cùng PrescriptionDTO. DOCTOR cần signed staffId khớp doctorId, ADMIN vẫn phải
  qua identity/context proofs. Request admission hoặc không record bị reject trước upstream.
- Trong profile MVP, V0 POST bị reject, không fallback khi provider timeout/5xx/stale/absent.
  Dispense/cancel và scheduler expiry V1 có wiring batch 2; **chưa bật profile/gate này để chạy thật**.

Stable error codes kiểm trong tests:

| Error | HTTP | Ý nghĩa |
|---|---|---|
| UNAUTHORIZED / FORBIDDEN | 401 / 403 | Security/actor denial, guards hiện hữu |
| VALIDATION_ERROR | 400 | DTO constraint/malformed request, không thay payload |
| PHARMACY_CARE_FINANCE_V2_UNAVAILABLE | 422 | V1 API bean/gates chưa enabled |
| PHARMACY_OUTPATIENT_V1_REQUIRED | 422 | Profile MVP từ chối creation V0 |
| PHARMACY_IDEMPOTENCY_KEY_REQUIRED | 422 | V1 cần canonical UUID command key |
| PHARMACY_CARE_CONTEXT_INVALID / PHARMACY_CARE_CREATION_INVALID | 422 | Exact outpatient record/context hoặc V1 intent không hợp lệ |
| PHARMACY_UPSTREAM_UNAVAILABLE | 503 | Fail-closed provider failure, không confirmed absence |

Domain receipt/stock/lifecycle conflict codes vẫn giữ theo source và tests hiện hữu; batch này
không rename errors hoặc tự đổi domain conflict thành HTTP mapping khác.

## 4. Event/queue/receipt manifest

Exchange `mediflow.events`, DLX `mediflow.events.dlx`; không tạo exchange/producer identity mới.

| Logical pipeline | Routing | Queue/DLQ | Status |
|---|---|---|---|
| Pharmacy V0 payment saga | payment.completed flat payload | pharmacy.q / pharmacy.dlq | Kernel compatibility giữ nguyên; listener OFF ở MVP |
| Pharmacy clearance V1 | financial.clearance.granted | pharmacy.financial-clearance.q / pharmacy.financial-clearance.dlq | Independent opt-in, exact PRESCRIPTION target; không receipt auto-dispense V1 |
| Pharmacy Admission | admission.started / discharge.medically.approved / admission.closed | pharmacy.admission-lifecycle.q / pharmacy.admission-lifecycle.dlq | Excluded ở MVP |
| Report legacy | medicalrecord.created / lab.result.created / prescription.filled / payment.completed / payment.failed | report.q / report.dlq | Flat parser compatibility OFF ở MVP; default profile giữ behavior |
| Report cash receipt | payment.completed V1 | report.cash-receipts-v2.q / report.cash-receipts-v2.dlq | Existing opt-in kernel |
| Report cash refund | payment.refunded V1 | report.cash-refunds-v2.q / report.cash-refunds-v2.dlq | Existing opt-in kernel/recovery |
| Report mixed operational | completion/Lab/Rx cùng Surgery/Admission | report.operational-v2.q / report.operational-v2.dlq | Excluded ở MVP, chờ selected intake H6-08 |
| Report settlement / final recognition | settlement facts | Topology giữ trong source configs | Excluded ở MVP |

V1 Pharmacy source fixtures: contracts/care-finance-v1/*; created outpatient fixture độc lập,
không dùng năm terminal alternative scenarios như một transition chain. Lifecycle routing keys
giữ prescription.created/filled/cancelled/expired/dispense.failed; payload bytes/hash có trong
protected-files manifest. Default HELD immutable, original IDs/bytes/order/fencing phải giữ khi retry.

Golden slice: exact outpatient Clinical record/visit → Pharmacy creation proof/receipt/reservation
→ Billing Rx charge/payment/PRESCRIPTION grant → authenticated dispense → Clinical attachment +
Report items/cash/refund + Notification. Local source/fixture progress không là acceptance của
cả chain. Billing/Clinical/Lab source fixtures đọc read-only ở cùng baseline commit.

Receipt/effect keys: V1 commandId + authenticated account + canonical intent fingerprint;
Pharmacy stock effect khóa prescription/slip/reservations/drugs; clearance exact target và expiry.
Report phải dedupe eventId lẫn operation/source ở H6-08; cash/refund giữ original payment linkage.
Không cross-service DB lookup hoặc nhân đôi contribution vì cùng operation có eventId mới.

## 5. Historical jobs và các phần cố ý còn mở

- Pharmacy expiry `ExpirePrescriptionTransaction` hiện từ chối non-LEGACY trước effect;
  reconciliation là dry-run/read-only. Không chuyển V1 qua legacy expiry/failure publisher.
  Batch 2 gated facade nhận V1 outpatient expiry trong chính leased REQUIRES_NEW wrapper; clocks/locks
  do V1 kernel kiểm lại, không dùng batch timestamp để quyết định. Không blanket-release reservations.
- Không chạm DB/broker live, không drop queue/binding, purge pending/HELD, reset inbox hoặc sửa migration.
  Tắt cấu hình chỉ dừng module consumers/jobs, không chứng minh broker đã drain hay hết historical backlog.
  Hai legacy listeners OFF ở MVP; queue/topology declarations không xóa. Publisher cutover phải audit
  routing/backlog để không đưa V1 bytes vào queue legacy đang giữ, không coi dormant queue đã drained.
- Chưa đọc inventory DB thật/rollout credentials/cursors. H6-02.3 vẫn mở; packaged MVP portion của
  H6-02.4 được đóng ở batch 8, profile tổng/integrated owner acceptance vẫn mở.
- Chưa chạy packaged Core/Support/Gateway hoặc owner-accepted activation; public V1 lifecycle mới ở local tests.
  Gate API false và HELD giữ nguyên cho đến H6-05/06/12; không claim topology sáu service đã triển khai.

## 6. Baseline tests trước chỉnh code

Full command: `mvn -q -pl backend/pharmacy-service,backend/report-service -am test`.
Pharmacy: **612 tests, 0 failures, 2 errors, 96 skipped**; hai errors là
AdmissionLifecycleRabbitIntegrationTest và PrescriptionClearanceRabbitIntegrationTest do Docker
engine không kết nối. Reactor dừng trước Report; không coi full suite pass.

Baseline phân biệt không-container sau đó chạy bằng selector:
`-Dtest=!**/*IntegrationTest,!**/*PostgresTest,!**/*IT -Dsurefire.failIfNoSpecifiedTests=false`.
Một số class PG tên khác vẫn được chọn nhưng self-skip do Docker; ghi skipped riêng:

| Module | Selected tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 534 | 514 | 0 | 0 | 20 |
| Report | 897 | 885 | 0 | 0 | 12 |

Unit/use-case/web/ArchUnit chạy được; excluded/skipped PG/Rabbit/IT không tính là passed.
`PharmacyDistributedRuntimeAcceptanceIT` còn cần Docker, profile distributed-acceptance và
packaged dependency tests. Suite baseline này không thay proof PostgreSQL locking/broker recovery.

### Batch implementation verification

Batch 2 contract/wiring decision before implementation: the existing outpatient API gate now
also requires `mediflow.pharmacy.clearance-consumer.enabled=true` (five gates total), so create
cannot be activated without wiring the lifecycle/clearance pipeline. No JSON or URL changes.
MVP dispense/cancel route through a profile-gated application port, classify persisted care mode,
and refuse V0/admission rather than consult legacy receipts. Expiry reuses the existing leased
batch and REQUIRES_NEW boundary; V1 dispatch remains gated and outpatient-only.
Stock failure may return a persisted FAILED DispenseDTO after rollback/re-evaluation; terminal
retry must read exact stored HELD proof, never recreate effects or reinterpret financial refund.

Selector không-container như baseline, chạy lại sau batch code; Maven exit 0:

| Module | Selected tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 572 | 552 | 0 | 0 | 20 |
| Report | 899 | 887 | 0 | 0 | 12 |

Tổng **1.439 passed / 32 skipped**; thêm **40 tests** so với baseline selector.
PharmacyOutpatientApiConfigurationTest kiểm 16 tổ hợp gate + không-profile + missing proof writer;
Pharmacy/ReportMvpDeferredConfigurationTest kiểm global override; PharmacyCoreDestinationTest
kiểm default/Core profile; OutpatientPrescriptionControllerTest và disabled-controller test
kiểm actor/context/command key/deny/fail-closed. Hai ArchitectureTest và regression V0 đều đạt.

Test mới dùng web/context/use-case boundary với mock checked writer; không thay proof PG transaction
hoặc Core/Billing/Gateway end-to-end. 62 protected file hashes so khớp, `git diff --check` đạt.
Package hai module + common bằng `mvn -q -pl backend/pharmacy-service,backend/report-service -am
-DskipTests package` đạt exit 0; đây là build artifact, không packaged runtime startup proof.
Không sửa Gateway/foreign services/shared build/CI/FE; chưa commit/push hoặc triển khai profile live.
Full Docker-dependent suite chưa rerun vì engine còn unavailable. Task closure: H6-01 4/4,
H6-02 2/4, H6-03 2/4, H6-04 3/4 — tổng **11/48**, không đóng integrated task chính H6-02/03/04.

## 7. Batch 2 — lifecycle routing và terminal recovery

Source tiếp tục worktree trên `5f40b32c`, không commit/push/gate activation. H6-05.1/.3 đóng
local implementation: `OutpatientPrescriptionLifecycleApplicationService` chỉ cho signed
PHARMACIST/ADMIN dispense; ADMIN account actor có kiểu rõ ràng, không giả staff ID. Read persisted
mode dùng để phân loại, không là permission; kernels kiểm lại mode/state dưới locks.
Controller profile MVP dùng lifecycle port cho dispense/cancel; profile compatibility dùng V0.

Dispense facade có propagation NEVER: failed stock writer phải rollback trước khi port
REQUIRES_NEW re-evaluate. Chỉ RESERVATION_EXPIRED/DRUG_OUT_OF_STOCK/DRUG_EXPIRED có thể kích hoạt
business-failure evaluation; permission/evidence/infra errors không tạo failure/financial adjustment.
Failure đã persist trả FAILED DispenseDTO, không có refunded flag/claim. Failed retry đọc đúng
stored HELD terminal proof kể cả grant đã hết hạn; thiếu proof không invent outcome, không stock effect.

Cancel gọi V1 ownership/state kernel. Expiry dùng leased scheduler hiện hữu và wrapper
REQUIRES_NEW với optional lifecycle port; disabled V1/deferred admission vẫn no-op, không V0 fact.
V1 expiry kernel dùng clock sau locks, không batch timestamp. Không thay reservation lease/cursor,
event bytes hoặc schema. H6-05.2 mở: Billing authority đã có revokedAt, Pharmacy snapshot chỉ có
grant/expiry; cần current/revocation semantics + acceptance trước mở API thực tế.

Fresh selected regression sau batch 2, cùng selector không-container như mục 6, Maven exit 0:

| Module | Selected | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 619 | 599 | 0 | 0 | 20 |
| Report | 899 | 887 | 0 | 0 | 12 |

Tổng **1.486 passed / 32 skipped**, thêm **47 tests** từ batch 1 (gồm gate matrix tăng lên 32 tổ hợp).
Outpatient lifecycle facade/expiry routing/web tests và failed proof retry/admission fence tests đạt.
PG/Rabbit/IT bị exclude hoặc skip không tính pass; H6-05.4/integrated recovery acceptance vẫn mở.
Protected hashes 62 file không đổi. Tiến độ mới **13/48**: H6-05 thêm 2/4 local subtask,
không đóng task chính H6-05 hoặc global M6. Shared/foreign/Gateway/FE không được sửa.
Package Pharmacy/Report/common (`-DskipTests package`) sau batch 2 đạt exit 0; diff check và
link/checkbox/scope audit đạt. Đây không phải PostgreSQL/broker hoặc packaged runtime acceptance.

## 8. Batch 3 — 10 subtask selected, 9 closed local

Source: worktree trên `5f40b32c`, 2026-10-14; chưa commit/push/rollout/database mutation.
Selected: H6-03.3, H6-06.1, H6-08.1/.2/.3, H6-09.1/.2/.3/.4, H6-10.1.
Closed LOCAL: cả chín mục trừ H6-09.4. Tổng **22/48**, task chính H6-01 duy nhất đóng đủ.
Checkbox local không là external owner acceptance hoặc runtime sáu-app.

### Source/API/gate decisions before activation

- Reuse 40 PrescriptionIdentityHttpTest + 26 PharmacyClinicalContextHttpTest cases via real
  local HttpServer/Feign, exact credentials/correlation and negative/fail-closed source checks.
  Checked creation tests prove denied/unavailable evidence has no writer effects. Not packaged Core.
- Pharmacy codec has 23 cases, including five outbound outpatient alternative round-trips.
  Four NEW fixtures supplement the existing outpatient creation, old admission fixtures unchanged.
  Filled bytes are consumed directly by Report; cross-owner acceptance/HELD delivery remains open.
- New `report.outpatient-v1.q`/`.dlq`, five exact keys only, MVP + global + outpatient-consumer
  gates default OFF. Clinical completion/Lab/Rx map into existing operation receipts; cash/refund
  reuse original-linked receipts, partial limit and recovery. Invalid/deferred/conflict goes durable
  DLQ; storage failure gets three bounded retries, no ACK-drop. Historical queues not deleted.
- Recovery selects only outpatient SERVICE_PAYMENT originals; persistence pending/defer queries
  in MVP also filter outpatient so admission backlog cannot be quarantined/changed by this worker
  or receipt-triggered recovery. Standalone compatibility retains the original all-mode behavior.
- Actual Clinical completion fixture is ADMISSION; kept as deferred negative. Outpatient Clinical
  positive uses LOCAL VARIANT only, never claimed owner source bytes. Actual Lab, outpatient filled
  and Billing service-payment/refund bytes accepted locally; deposit/admission negatives reject.
- New ADMIN/MANAGER GET `/api/v1/reports/outpatient/daily?fromDate&toDate&departmentId` and
  `/outpatient/monthly?year&departmentId`; profile + global + outpatient-read gates default OFF.
  ApiResponse<OutpatientReportDTO> with coverage/time/zone/VND metadata and period visits/Lab/Rx/
  units/cashReceived/refunds/netCash. At most 366 days, years 2000–2100, monthly full calendar year.
  Date validation => REPORT_VALIDATION_ERROR (400), missing/drift/unaccepted => REPORT_NOT_FOUND (404).
  Legacy API unchanged; cash is not earned revenue. Route/header/client acceptance in active handoff.
- V27 accepted coverage table is EMPTY; no writer/admin acceptance API or production seed.
  Five real source-export references, exact date/department/zone and reconciled owner acceptance
  required before rollout. One REPEATABLE READ snapshot binds local witness to operation/cash/refund
  rows; sourceObservedAt must cover complete final day, acceptance cannot be future. Null legacy
  fingerprints/pending/conflicts/new facts withhold stale totals. Local digest is not a source cursor
  or proof of external completeness; importer/source reconciliation acceptance remains an owner gate.
- Report POM preserves main executable filename/launcher and attaches plain classifier `lib`.
  Support dependency must explicitly select `lib`, exclude ReportServiceApplication from scans.
  Multi-store/tx/security/host wiring remains H6-10.2–11, not packaged Support readiness.

### Locked outpatient fixture SHA-256 (alternative TEST examples only)

Under `backend/pharmacy-service/src/test/resources/contracts/care-finance-v1/`:

| Fixture | SHA-256 |
|---|---|
| prescription.created.outpatient.v1.json (unchanged) | `3083b5d244d147dd50025fa608243ed11de52fa76f6b007170aa4cb0363898ed` |
| prescription.filled.outpatient.v1.json | `b0db35a17d80b1cbdb30d0de7b7549592bc960c67365363634e1f2c1366981a6` |
| prescription.cancelled.outpatient.v1.json | `9bef905dd51be4c107ce256fd3022b3caac791c6e01afa8b8bc5bbecf3f80537` |
| prescription.expired.outpatient.v1.json | `5bd77085ef0db79686adc20ff5bb213e6a8da4b185bfc75fe34411a4ba5d770b` |
| prescription.dispense.failed.outpatient.v1.json | `deefc1be799c64437a81eef482b422daca2fa54baff4fad3b23d38efc0f2a888` |

62 original baseline hashes remain unchanged; new fixture hashes do not replace old hashes.
The old protected fixture README is historical; OUTPATIENT-MVP.md supplies current links/proposal.

### Verification

Selected regression command (not full container acceptance), fresh final run exit 0:

```text
mvn -q -pl backend/pharmacy-service,backend/report-service -am test
  -Dtest=!**/*IntegrationTest,!**/*PostgresTest,!**/*IT
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Module | Selected | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 624 | 604 | 0 | 0 | 20 |
| Report | 947 | 935 | 0 | 0 | 12 |

Tổng **1.539 passed / 32 skipped**, thêm **53 passed** so với batch 2. Both ArchUnit suites,
legacy controller/consumer/kernel tests, new web RBAC/gates, reader scope/date/pending/zero/drift/
money precision/overflow tests passed. HTTP tests above are already included, not double-counted.

ReportPackagingAcceptanceIT: two separate artifact cases PASS after package (no skipped); root
class layout, unchanged standalone launcher, exact V1/V27 resource bytes. Command:
`mvn -q -pl backend/report-service -am -Preport-packaging-acceptance -DskipUnitTests=true verify`.
Own module Surefire skip here is deliberate artifact-only verification after unit regression;
Failsafe still executes the two artifact checks. No packaged host or real DB startup claim.

Explicit `OutpatientReadPostgresTest` selected: **1 selected / 1 skipped / 0 executed** because
Docker engine unavailable. Source test migrates an isolated new PG16 schema then checks absent
coverage, accepted test-only legitimate zero, late cash invalidating witness and pending refund.
Its TEST_EXPORT_ONLY/ISOLATED_TEST_ONLY evidence never represents production history or acceptance.
H6-09.4 stays OPEN; real PostgreSQL timezone/source-scope/duplicate/replay and Rabbit fault acceptance
also remain H6-08.4/12. No Docker reset, queue purge, DB writes or invented coverage performed.

`git diff --check`, 48-checkbox/22-closed and new-doc links/protected-hash audits pass.
All writes remain Pharmacy/Report and Huy documentation; no Gateway/foreign/shared/FE edits.

## 9. Batch 4 — align with Core and prepare Report composition (2026-10-15)

Current HEAD `e948075893e797be056d1344f9c9ba0072ed6dae` after fast-forward master, plus uncommitted
Huy batches. User explicitly selected **Huy modules only; handoff shared work**. No new Support
host, Notification, Gateway, root build, Compose, CI or FE edits. Surgery changes are three
fixture-path test fixes only; no feature/client/job/route activation or payload changes.

### Implemented adjustments

1. Pharmacy reads exact Patient/Organization lookup fixtures from new Core source location.
   PrescriptionIdentityHttpTest: 40 PASS, PharmacyCoreDestinationTest: 2 PASS. Missing-file errors
   reproduced before the fix are gone; no weakening of identity/timeout/credential assertions.
2. Canonical team profile `outpatient-mvp` imports the existing owned configuration; old
   `mvp-outpatient` stays an alias. Positive/negative profile conditions and persisted-mode dispatch
   support both. Core destination and deferred/V0 listener exclusions tested for both names even
   with global overrides. Clinical's distinct V1 feature/env keys are not silently remapped.
3. ReportModuleConfiguration exports scoped Report components, not the Boot launcher. It requires
   report-module plus outpatient mode. Own credentials are mandatory, no default/foreign DS fallback;
   own bounded pool, Flyway, EMF/unit/repositories/tx/JDBC. No @Primary masking. Standalone retains
   standard aliases and uses the same qualified tx/JDBC paths. JPA slice tests import this wiring.
4. All Report transaction annotations select reportTransactionManager (including a fully-qualified
   annotation found by the new guard); injectable native adapters select reportJdbcTemplate.
   ReportContextBindingTest enforces every transaction/injectable JdbcTemplate/scheduled job, not
   only one happy-path service. Recording two-manager test proves actual Spring proxy selection
   and rollback/isolation without claiming database atomicity.
5. Namespaced beans/security/advice/Rabbit factories, Report-only co-host HTTP chain, explicit zone
   and optional reportClock, own bounded scheduler. Shared host must supply one common JSON mapper.
   Scoped advice no longer catches foreign contexts; a path-checked resolver retains Report's
   redacted unknown-route 404 without claiming notifications/lookalike paths or masking DB errors.
6. Maven resource packaging copies unchanged migrations to mediflow/report/db/migration, separate
   from Notification's default path, while retaining standalone originals. Both artifact checks
   compare original/namespaced V1 and V27 resource bytes and keep the standalone launcher.

The actual Core/Clinical/Billing/Notification contracts and immutable event bytes are preserved.
H6-05.2 revocation/current-authority, source completion fixture, cross-owner lifecycle acceptance,
real coverage and PostgreSQL/Rabbit/Gateway remain explicit gates, not guessed or faked.

### Fresh verification

Final regression selector as batch 3, exit 0:

| Module | Selected | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 626 | 606 | 0 | 0 | 20 |
| Report | 960 | 948 | 0 | 0 | 12 |

**1,554 passed / 32 skipped**; added 15 passing local cases over batch 3. Current fresh results
supersede pre-pull results for the adjusted code. Intermediate regression caught a missing qualified
transaction, test bean names and missing-route envelope handling; those were repaired, no tests
disabled or guards loosened. Both architecture suites and existing business/web/kernel tests pass.

Separate Surgery selector SurgeryAuthorityChangeDecoderTest + SurgeryAuthorityLookupHttpTest:
**73 passed / 0 failed / 0 skipped**. Not full Surgery reactivation or production readiness.

ReportModulePostgresTest added for isolated Report + another disposable test-owned store, scoped
migration/JPA read/tx/no standalone launcher assertions: **1 selected / 1 skipped / 0 executed**
because Docker engine cannot connect to dockerDesktopLinuxEngine. No actual Notification DB is
accessed. This is not host acceptance, migration/upgrade proof, or a reason to tick H6-10.4/11.

Artifact profile runs two ReportPackagingAcceptanceIT checks after package, separately from unit
regression. Original 62 protected hashes remain unchanged. git diff --check, new links and scope
audits pass. No DB reset, restore, Flyway repair or HELD/broker state changes.

### Closure and remaining owners

H6-10.2/.3 close LOCAL preparation; plan is **24/48**. H6-10.4 and H6-11 still require real
stores, producer/consumer acceptance and Lộc/shared/Hoàng Anh integration. Main H6-10 not closed.
[Support handoff](../handoffs/HANDOFF-HUY-SUPPORT-COMPOSITION.md) records exact entry point/bean/
property/resource names, host security/scheduler obligations, runtime and Gateway changes. Do not
sum these local subtasks into the global M6 backlog or reopen deferred Surgery/full-V2 tasks.

## 10. Batch 5 — packaged library probes and cutover preparation (2026-10-15)

HEAD remains `e9480758` + uncommitted Huy worktree. Scope unchanged: Pharmacy/Report and owned
source guides only; no Support host, Notification/Gateway/shared build/Compose/CI/FE writes.
Docker Desktop Linux engine still cannot be reached. No deploy, real inventory, backup/restore,
queue purge, data correction or HELD activation was attempted.

### Implementation / proof

- Report's artifact profile now runs ReportPackagedModuleAcceptanceIT in addition to the existing
  structure/launcher checks. Three NEW JVMs load Report from the actual lib JAR with Report's
  target/classes/test-classes removed; only a small test shim and dependency classpath are supplied.
  The shim asserts code-source origin, exercises the scoped reader (another zone exists and missing
  coverage is unavailable), rejects missing outpatient mode, and refuses foreign spring.datasource
  credentials instead of inheriting them. No real database/broker or full Support startup claim.
- The packaging check compares **all 24 current Report SQL files** against standalone and namespaced
  copies, not just V1/V27. A wrong count assumption (latest version 27 versus 24 actual files) was
  found and fixed in both artifact and pending module-PG tests. Existing V3–V5 gaps are preserved;
  no migrations were invented, renumbered, repaired or modified.
- Two operator resources at each module's src/main/resources/operations/mvp-cutover-inventory.sql
  use bounded REPEATABLE READ/READ ONLY snapshots ending ROLLBACK. Pharmacy groups mode/outbox/
  receipt/reservation/lease state, including HELD and UNKNOWN; Report groups pending refund/source/
  coverage/rebuild state. No source payload/PII selections, DML, migration or acceptance writer.
- Resource guard tests enforce the read-only/bounded/no-payload statement set. Two disposable-PG
  tests were added to check script execution, read-only metadata and preservation of held bytes,
  scheduler fences/pending refund/empty coverage. They are test-only scenarios, not real history.
- Owned runbook: [inventory/cutover/rollback](huy-mvp-cutover-runbook.md). Source guides/.http/plan
  and Support handoff point to canonical profiles and explicit owner gates. Surgery README/AGENTS
  now prominently mark deferred MVP scope; historical dead links are not revived as new backlog.

### Fresh verification

Same selected regression command as batch 4, Maven exit 0:

| Module | Selected | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pharmacy | 627 | 607 | 0 | 0 | 20 |
| Report | 961 | 949 | 0 | 0 | 12 |

**1,556 passed / 32 skipped** in the selected regression. Separate artifact profile:
**5 passed / 0 failed / 0 skipped**, including three real forked-JVM probes and two artifact
structure/bytes/launcher cases. These numbers do not double-count the previous batch's tests.

Separate explicit selection of MvpCutoverInventoryPostgresTest in both modules,
ReportModulePostgresTest and OutpatientReadPostgresTest: **4 selected / 4 skipped / 0 executed**,
due to unavailable Docker engine. They are excluded from the non-container selector, not passes.
H6-02.3 (real inventory), H6-10.4 (real schema/upgrade/host proof), H6-12.1–.3 remain open.

Original 62 protected hashes unchanged; 48-checkbox count and new links/scope/diff checks pass.
H6-12.4 owned documentation closes, total **25/48**; no new main/integrated task is declared done.
This batch improves delivery preparation, not the deployed number of business processes.

## 11. Batch 6 — PostgreSQL and RabbitMQ acceptance (2026-10-15)

Docker Desktop 29.6.2 is now running. Ten executable work items and exact test names are recorded
in [batch 6 acceptance](huy-mvp-batch-6-acceptance.md). Changes remain in Pharmacy/Report tests
and owned docs; no foreign/shared production module, feature activation or operator DB mutation.

- Public Pharmacy creation/lifecycle facades now have real-PG concurrent command/dispense and
  rollback-before-terminal/replay proof. Context/identity providers are mocked ports in this
  isolated harness, not a claim of actual Core/Clinical/Billing authority acceptance.
- Report's selected outpatient consumer now has actual PG/Rabbit same-byte filled, duplicate/
  new delivery, conflicting fact, early refund/listener restart, deferred deposit DLQ and injected
  DB fault tests. Three bounded attempts leave no partial receipt/projection and retain original
  bytes for redrive. No new queue activation on a real environment.
- Coverage PG tests prove exact department/zone, midnight, unavailable/missing ranges, accepted
  zero, partial refund/net cash and full accepted-window witness drift. Test-only acceptance rows
  are not real source exports, production history or an automatic coverage writer.
- V26 standalone → namespaced V27 → original resource-location validation preserves migration
  checksums, legacy rows and a newly accepted test write. Report module starts with two disposable
  stores and no standalone launcher/default transaction manager; the other store stays unmigrated.
- Report Docker tests use Pharmacy's existing docker-java API pin. An explicit test host outside
  Report's component scan avoids Spring accidentally loading the standalone boot application.
- Full regression exposed a V26-only test incorrectly targeting latest after adding V27. It now
  targets V26 exactly; all unchanged-row/checksum and one-migration assertions remain, V27 is
  tested separately. No Flyway repair, renumbering or released SQL changes.

Focused container selection: **36 passed / 0 failed / 0 skipped**. Artifact profile Maven exit 0:
**5 passed / 0 failed / 0 skipped**. Full Surefire selection ran 722 Pharmacy + 1,326 Report cases;
one V26 test expectation initially failed (full command exit 1), its corrected two-case suite
reran exit 0. Latest unique per-suite results: **2,048 passed / 0 failed / 0 skipped**. The 36
focused cases are included in 2,048, not added twice; artifact/Failsafe tests are separate.

Original 62 protected hashes remain unchanged. H6-09.4/H6-10.4 close LOCAL; plan **27/48**.
Report library preparation H6-10 is complete, not the unassigned Support/Notification host H6-11.
Reader rollout still requires accepted source coverage/Gateway. No commit/push or scope expansion.

## 12. Current clearance and selective outpatient delivery

2026-10-15, baseline `e9480758` plus the preserved uncommitted Huy worktree. Production writes
remain confined to Pharmacy; Report/foreign/shared runtime code is unchanged in this continuation.
No operator database, deployed feature flag, commit or remote branch was changed.

### Current Billing observation

The source lock is Billing's existing `FinancialClearanceLookupDTO` and
`GET /api/v1/billing/financial-clearances/{id}/lookup`. Its wire fields are exists, clearanceId,
eligible, invoiceId, accountId, patientId, purpose, careEpisodeType, careEpisodeId, admissionId,
surgeryCaseId, grantedAt, expiresAt and observedAt. The response does not contain prescriptionId
or revokedAt. The local immutable grant supplies the prescription target; Billing's eligible decision
includes revocation and ledger/time eligibility. No new producer field, guessed identity or foreign SQL.

Pharmacy sends a 60-second SYSTEM/service JWT with logical subject pharmacy-service, exact request
correlation and configurable Billing URL/discovery. Connect/read timeouts are one second each.
Malformed/duplicate/trailing JSON, oversized response, stale/future observation, mismatched correlation,
noncanonical IDs or transport failure is unavailable, never implicit permission. The locked transaction
compares invoice/account/patient/PRESCRIPTION/OUTPATIENT_VISIT/episode and original grant timestamps
immediately before effects; it also rechecks expiry after verification. This is a current observation,
not a distributed reservation/lease against a later revocation. Billing-owned live acceptance remains
in the [activation handoff](../handoffs/HANDOFF-HUY-MVP-OUTPATIENT-ACTIVATION.md).

The public API now requires six gates: global V2, identity, clinical-context, clearance-consumer,
clearance-authority and outpatient-api. Missing current-authority provider fails closed in the MVP
profile. Existing internal compatibility tests keep the explicit two-argument authorization constructor;
the public MVP wiring never selects it as a fallback. Billing's original lookup fixtures remain
SURGERY/ADMISSION compatibility examples, not fabricated prescription-positive producer fixtures.

### Selective delivery and historical preservation

V20 adds pharmacy_outpatient_delivery_cohort and an outbox cohort reference. It does not rewrite
historical rows or released V1–V19 SQL. New enrollment requires the MVP profile, all six gates,
outpatient-events.enabled and a bounded acceptance-reference. The reference records an operator
decision; it does not itself prove external acceptance. Defaults remain OFF/empty.

Creation enrolls only a persisted V1 outpatient aggregate in its caller's transaction. Terminal events
inherit the creation cohort even after new enrollment is disabled. Replaying historical HELD does
not enroll it, and a database trigger rejects changes to a V1 row's cohort/enabled state. Lifecycle
order, not wall-clock order, sequences V1 creation before terminal events. Published V1 proof stays
available to terminal retries and is excluded from automatic retention purge. Transport remains
at-least-once: a lost publish mark can resend the same ID/bytes, while the old lease owner is fenced.

The MVP profile explicitly disables Open EntityManager in View, matching standalone production
configuration. This prevents a preliminary classification read from leaving an obsolete managed
prescription in the later locked writer. The full HTTP race exposed this test/profile configuration
gap; the regression compares business data rather than unrelated envelope response timestamps.

### Verification scope

Fresh targeted selection: **130 passed, zero failed/errors/skipped**, Maven exit 0. This included
13 signed HTTP/real-PG cases, six real outbox/broker cases, the V19→V20 upgrade, eight real Feign
HTTP cases, twelve adapter cases, fourteen current-authority cases, ten activation cases and 66 API
gate cases. Upstream API/PG tests use isolated test ports; HTTP transport uses unchanged producer
fixtures. Neither is a claim of packaged Core/Clinical/Billing outpatient acceptance.

The upgrade preserves exact historical payload/state/lease snapshots and all nineteen original
Flyway checksums, leaves the cohort empty and rejects historical activation. Broker stop/start
preserves the pending event and resumes the same message identity/bytes. Full regression adds
bounded three-attempt quarantine, causal blocking until explicit replay and untrusted client-price
assertions. The seven outbox/broker cases pass in full regression, including these assertions.

Full Pharmacy regression selected **819 tests across 108 suites, zero skipped**. The first command
exited 1 on one stale migration test expecting latest V19; it now asserts V20 and explicitly checks
both new context-owned foreign keys. No SQL/history was rewritten and external-FK denial remains.
The corrected selection reran migration compatibility, API/PG, cohort/broker and architecture:
**33 passed, zero failed/errors/skipped, Maven exit 0**. Latest unique results across the full run
and corrected rerun are **819 passed, zero failed/errors/skipped**; the rerun is not added twice.
This is not a claim that the original all-tests command exited zero. Report's earlier 1,326 results
belong to batch 6, not a fresh full Report run here. All 62 protected hashes remain unchanged.

```text
mvn -q -pl backend/pharmacy-service -am test
mvn -q -pl backend/pharmacy-service -am test -Dtest=PharmacyMigrationCompatibilityTest,ArchitectureTest,OutpatientApiPostgresTest,OutpatientDeliveryPostgresTest -Dsurefire.failIfNoSpecifiedTests=false
```

A final PG selection then added two scenarios: expiry-vs-authenticated-dispense and late duplicate
grant after cancellation. OutpatientApiPostgresTest **15/15**, CareDispensePostgresTest **25/25**
and PrescriptionClearancePostgresTest **8/8** pass, Maven exit 0. Latest unique Pharmacy results
are therefore **821 passed across 108 suites, zero failed/errors/skipped**. These 48 rerun cases
are included, not additional. A late grant is not a cash receipt or Billing compensation proof.

H6-04.4, H6-05.2, H6-05.4 and H6-06.3 close LOCAL implementation/test acceptance: **31/48
subtasks closed, 17 open**. H6-04/H6-05 have four local subtasks complete but still need packaged
upstream/chain acceptance. Individual test cases/files are not counted as new original H6 subtasks.
Remaining issuer/consumer/coverage/Support/Gateway/cutover gates retain their criteria; denominator 48.

### Packaged compatibility acceptance

Freshly packaged Pharmacy, Billing, Report and Notification ran with four private PostgreSQL stores
and RabbitMQ. PharmacyDistributedRuntimeAcceptanceIT: **4 passed, zero failed/errors/skipped**,
Maven exit 0. No foreign production source edits or foreign DB inspection/seed. All commands and
observations use actual business HTTP; probes replay captured producer bytes.

- Duplicate original delivery and new event ID for the same source retain one stock/report effect;
  business queues drain without valid messages being poisoned to DLQ.
- Report JVM stops while payment/dispense completes, then catches up without another stock effect.
- Rabbit application stops while Pharmacy commits a prescription; producer JVM restarts and its
  committed outbox resumes after broker recovery.
- Pharmacy JVM stops before payment, payment remains durable in its queue and Billing does not
  claim completion early; restart completes the V0 saga once.

This is V0 compatibility, not public V1 issuer/current-authority acceptance or the six-app MVP.
At batch 7 H6-07 remained open for compensation and exact crash windows; batch 8 below supersedes
that status for H6-07.1–.4 local/compatibility acceptance. V1 issuer and rollout gates stay open.

```text
mvn -q -pl backend/report-service,backend/billing-service,backend/notification-service -am -DskipTests package
mvn -q -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true verify
mvn -q -pl backend/pharmacy-service -am test -Dtest=OutpatientApiPostgresTest,CareDispensePostgresTest,PrescriptionClearancePostgresTest -Dsurefire.failIfNoSpecifiedTests=false
```

The four Failsafe tests are separate from 821 Surefire cases: **825 unique Pharmacy cases passed**
in this continuation's full selection plus corrected/final reruns and packaged profile.

## 13. Batch 8 packaged acceptance

Six original plan subtasks close from this evidence: H6-02.4 packaged MVP negative/startup matrix;
H6-03.4 actual packaged Core/Clinical lookup; H6-07.1 Pharmacy restart and V1 proof-without-
auto-dispense; H6-07.2 three actual JVM crash boundaries; H6-07.3 Billing V0 terminal/stock
compensation and duplicate late payment; H6-07.4 listener conflict, DB rollback, bounded retry,
DLQ and redrive. Full criteria, commands, execution counts and retained failure history are in the
[batch 8 acceptance record](huy-mvp-batch-8-acceptance.md).

Executed selection totals 147 unique passes and one explicitly skipped live V1 issuer chain:
Pharmacy/Billing/Report/Notification packaged V0 **12/12**; packaged Core/Clinical lookups **2/2**;
MVP deferred-route/consumer and original fixture grant restart **2/2**, plus one conditionally skipped
Billing issuer publication case; focused Pharmacy/Report unit, web, PG, Rabbit and architecture
regression **131/131** (Pharmacy 123, Report 8). These selections are not a full-module test run and
the unique total does not count the same case twice across retries. Existing full Pharmacy 821 and
Report 1,326 counts above are prior batches, not fresh batch 8 runs.

The first Billing V1 live probe timed out waiting for `invoice.created`: Billing's owned source
still marks the new invoice event HELD. No foreign store was queried or changed. The case is now
opt-in and reported skipped until producer acceptance. Actual Clinical durable seed also returned
HTTP 500 because legacy wire payload metadata mismatched; packaged lookup uses its supported
compatibility seed with durable publication disabled, and this is disclosed in the activation handoff.
Neither producer issue is marked fixed by Huy's owned acceptance. H6-06.2/.4 and all end-to-end
rollout remain open.

H6-02.4, H6-03.4, H6-07.1–.4 add six original subtasks: **37/48 closed, 11 open**. Counts are
plan subtask IDs only; individual tests are not additional tasks. H6-02.3 historical inventory,
H6-06 producer gates, H6-08.4 Clinical producer bytes, Support, Gateway and cutover remain open.
The 62 protected hashes are unchanged. No foreign/shared production modules, databases, commit or
remote branch were changed.
