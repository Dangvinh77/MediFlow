# Handoff chung Huy — Pharmacy, Report và Support

Ngày rà soát: 2026-10-16. Baseline `9ef3b151f895cef4c05916bf53331ff1d525f920`
cộng thay đổi owned trong workspace; implementation trước đó đã merge PR #385 (`7610cb97`,
merge `f15430dc`). User giao: hoàn tất phần độc lập trong phạm vi Huy, gom phần còn lại vào
**một file handoff**. Đây là đầu mối duy nhất thay các handoff activation, Support, Pharmacy
Core lookup, Surgery Core paths và hai retired pointers care-finance/Surgery decisions cũ;
không phải quyền sửa production foreign/shared paths.

Nguồn: [plan Huy](../plans/2026-10-14-huy-backend-mvp-adjustment-plan.md),
[contracts](../plans/03-contracts.md), [reliability](../plans/04-data-reliability.md),
[manifest](../plans/huy-backend-mvp-baseline.md), [batch 9 evidence](../plans/huy-mvp-batch-9-acceptance.md),
[cutover runbook](../plans/huy-mvp-cutover-runbook.md).
Không copy payload thành spec mới; exact wire lấy từ DTO/serializer/fixture source đã khóa.

## 1. Chốt phần Huy và 10 subtask còn mở

Plan giữ **38/48**, còn 10 mục dưới đây. Không tăng số done chỉ vì đã bàn giao.
Mỗi hàng tách phần Huy đã tự hoàn tất với điều kiện cần nhận để đóng subtask gốc.

| Subtask | Phần Huy độc lập đã có | Phần cần bàn giao / owner | Điều kiện đóng |
|---|---|---|---|
| H6-02.3 | SQL inventory chỉ đọc, bounded, ROLLBACK; resource/PG preservation tests | Operator + source owners cung cấp approved historical clone và broker observations | Kiểm kê pending/HELD/reservations/jobs/queues, phân loại deferred/quarantine, lưu source/time và phê duyệt; không purge hoặc release HELD |
| H6-06.2 | Năm lifecycle fixtures, codec và API/PG producer relationship proof; Report same-byte intake | Lộc Billing/Notification; Vinh Clinical attachment | Original bytes được từng consumer chấp nhận, đúng source/episode/patient/record, một charge/attachment/notice và đúng terminal adjustment |
| H6-06.4 | Pharmacy confirms/unroutable/restart/bounded retry/quarantine/fencing/grant tests, selected new cohort | Lộc Billing thực thi/accept selective outpatient issuer và current PRESCRIPTION grant; các consumer liên quan | Live source charge → invoice/request → payment → exact grant → authenticated dispense/terminal; PG/Rabbit thật, không fixture thay issuer |
| H6-11.1 | Report lib/export và scoped composition contract ở section 3 | Shared build owner + Lộc Notification | Thống nhất/create Support host, plain module exports, launcher/profiles/discovery; không broad-scan standalone applications |
| H6-11.2 | Report named DS/Flyway/EMF/tx/JDBC và fresh/upgrade PG proof | Shared build/runtime + Lộc; Hoàng Anh Gateway | Hai stores/credentials/migrations/transaction managers đúng scope; reactor/Compose/CI và Gateway trỏ host được owner triển khai |
| H6-11.3 | Report scoped security/advice/listeners/scheduler; legacy backlog retention | Lộc Notification + shared host owner | Hai module giữ queue/DLQ/pools/jobs riêng, host default deny, không bean override hoặc mượn scheduler/transaction manager |
| H6-11.4 | Report module PG isolation/upgrade + lib/JVM probes | Shared host owner + Lộc Notification | Packaged Support chạy hai PG/Rabbit, Report replay không resend notice, lỗi Notification không rollback Report, một process và không dual writers |
| H6-12.1 | Huy API/PG, producer/consumer, authority và Report source slices | Hoàng Anh Gateway/Core + Vinh Clinical/Lab + Lộc Billing/Notification | Gateway → actual source/paid grant → authenticated dispense → Clinical/Report/notice, role/token/path negatives và đối soát effects |
| H6-12.2 | Evidence map T02/T07–T19 và batch 6–9, full regression và owned packaged/fault proof | Từng producer/consumer owner + Support/Gateway owners + operators | Bổ sung integrated host/paid/source/coverage evidence với source IDs, hashes, run/fail/skip; local tests không thay integrated acceptance |
| H6-12.3 | Read-only inventory/stop-drain-switch/rollback checklist, Report resource-location upgrade preservation | Shared runtime/operator + Lộc Notification và source owners | Rehearse standalone Report → Support → rollback trên approved current clone; giữ accepted writes/bytes/cursors/pending/HELD, không dual writers hoặc restore snapshot cũ |

Phần độc lập H6-08.4 đã đóng: real PG/Rabbit same-byte replay/conflict/early refund/rollback,
fault sau commit trước ACK, legacy queue retention và actual Clinical outpatient completion.
Fault receiver trong JVM không được gọi là JVM kill; batch 8 có JVM crash proof riêng cho V0.
Current full regression: Pharmacy 822, Report 1.330; source acceptance 1; packaging probes 5,
đều 0 failures/errors/skips. Xem batch 9 để không cộng trùng rerun hoặc nhầm coverage.

## 2. Giao việc theo owner

### Lộc — Billing và Notification

Kế hoạch code cho Billing discovery và new-outpatient issuer/payment/grant publication đã chốt
tại [handoff Lộc L1–L4](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md), source `71e2684d`.
Lộc triển khai trực tiếp checklist đó; không cần lên kế hoạch khác. Đây là deliverable Billing
để nhận các bytes/purposes đã được consumer nghiệm thu, không tự đóng Notification,
terminal/refund, Support hoặc các subtask Huy còn mở bên dưới.

- `PrescriptionChargeService.issue` hiện dùng `LedgerEventPort.appendHeld` cho V1 `invoice.created`.
  Packaged probe trước đó timeout 35 giây khi chờ invoice. Cần selective **NEW outpatient**
  publication boundary có confirms/retry/fencing; giữ historical HELD. Không cấp invoiceId hoặc
  paymentRequestId giả, không cho Huy update Billing DB để vượt gate.
- Chạy actual PRESCRIPTION/OUTPATIENT_VISIT issuer, allocation/payment, grant, current eligibility,
  revocation và original-linked cancel/expiry/failure adjustment/refund. Pending refund không completed.
  Billing lookup fixture SURGERY/ADMISSION không phải Rx permission proof.
- Pharmacy gọi `GET /api/v1/billing/financial-clearances/{id}/lookup`, SYSTEM/service subject
  `pharmacy-service`. DTO source có exists/clearanceId/eligible/invoiceId/accountId/patientId/purpose/
  careEpisodeType/careEpisodeId/admissionId/surgeryCaseId/grantedAt/expiresAt/observedAt;
  không có prescriptionId/revokedAt. Local immutable grant bind Rx; eligible bao gồm revocation.
  Recheck sau local locks là observation, không distributed lease chống revocation sau response.
  Gate `mediflow.billing.clearance-lookup.enabled` thuộc Billing owner.
- Notification accept original lifecycle/Clinical/Lab/payment/refund bytes, dùng authoritative
  account/patient recipient mapping và owner-only human reads; Report replay không tạo notice mới.
  Export plain module với named persistence/security/advice/listeners/delivery scheduler.

Sau producer acceptance, chạy prepared live test `PharmacyOutpatientRuntimeAcceptanceIT` với
`-Dmediflow.acceptance.billing-v1-published=true`. Không bật switch để biến fixture test thành live
proof. Khi chưa có owner boundary, live method mặc định SKIPPED và H6-06.2/.4 vẫn mở.

### Vinh — Clinical/Lab

Refresh 2026-10-16: Clinical đã nhận original outpatient filled fixture trong V-CLOSE-02;
exact relationship, per-dispense inbox/effect và delayed terminal replay có PG/Rabbit proof.
Xem [evidence Vinh](HANDOFF-VINH-OUTPATIENT-MVP.md). Đây chỉ đóng Clinical attachment side;
Billing/Notification intake và actual paid Rx flow vẫn là điều kiện H6-06.2/06.4 còn mở.

- Accept Rx attachment từ original Pharmacy fixtures và real producer bytes: record/patient/
  department/episode đúng persisted relationship, một operation per dispense; duplicate/conflict tests.
  Các outpatient fixtures Pharmacy đã có `recordId`; không suy recordId từ careEpisodeId.
- Clinical timestamp regression đã được sửa ở `4b652e9d`; **không còn giao sửa lỗi này**.
  Report source acceptance dùng rebuilt current Core/Clinical, public API và original broker bytes;
  hoàn tất OUTPATIENT_FOLLOW_UP, listener restart/replay chỉ một contribution, conflict vào DLQ.
  Source SHA-256: `37682b98b9e33300efce4d50c6aeceddb7b180be6146c12532d8dc3fe0a1c572`.
  Audited emergency override trong isolated test không đóng actual EXAM paid flow.
- Checked-in Clinical `medicalrecord.completed.v1.json` vẫn ADMISSION, giữ làm negative/deferred
  evidence; không sửa thành outpatient cho test qua. Actual Lab fixture được Report đọc trực tiếp.
- Source owners cung cấp export/cursors, period/department/zone và reconciliation sign-off để
  coverage có bằng chứng đầy đủ; local contribution digest không chứng minh external completeness.

### Hoàng Anh — Core/Gateway và frontend liên quan

- Pharmacy Core retarget/fixture fix đã có packaged lookup acceptance H6-03.4, exact identities,
  absence khác unavailable và Pharmacy service-token allowlist. Không còn backlog caller path fix.
  Core correlation normalize UUID: dùng canonical UUID, không nới strict response verification.
  Lượt chốt độc lập này bỏ workaround durable-events=false: chạy lại
  `PharmacyAuthorityRuntimeAcceptanceIT` với durable publisher **true**, 2 passed, 0 failures/
  errors/skips, Maven exit 0. Đây là direct packaged lookup acceptance, không Gateway/paid flow.
- Gateway giữ exact method/path/role/token rules, pass-through canonical UUID `Idempotency-Key`
  cho V1 POST prescriptions; thiếu/sai key không sinh commandId. JSON/URL/envelope V0 giữ nguyên.
- Sau host acceptance: `/api/v1/reports/**`, `/api/v1/notifications/**` → `lb://support-service`.
  Human endpoints deny service/refresh token, unknown/nested/deferred paths deny theo policy.
- Report additive reader ADMIN/MANAGER: GET `/api/v1/reports/outpatient/daily` (fromDate/toDate,
  optional departmentId, max 366 days); GET `/api/v1/reports/outpatient/monthly` (year 2000–2100,
  optional departmentId). Exact DTO ở source: periods counts/Rx/units/cash/refunds/netCash và
  coverageId/sourceObservedAt/reconciledAt/currency/zoneId. Cash không phải earned revenue.
  Invalid period: 400 REPORT_VALIDATION_ERROR; missing/unaccepted/stale coverage:
  404 REPORT_NOT_FOUND. Frontend phải hiển thị unavailable khác zero.

## 3. Support composition — hợp đồng Report đã cung cấp

- Depend `com.mediflow:report-service:0.0.1-SNAPSHOT`, classifier **lib**, không executable BOOT-INF
  artifact. Import duy nhất `com.mediflow.report.infrastructure.config.ReportModuleConfiguration`.
  Profiles `report-module,outpatient-mvp`; alias `mvp-outpatient` còn hỗ trợ.
- Không scan `ReportServiceApplication` hoặc toàn `com.mediflow`; không import Notification entities,
  repositories, contacts, ledger hoặc foreign SQL vào Report.
- Explicit `mediflow.report.datasource.url/username/password`, PostgreSQL, không fallback host
  datasource; maximum-pool-size mặc định 4, giới hạn 1..16, minimum idle 0, timeout 3 giây.
- Named beans `reportDataSource`, `reportFlyway`, `reportEntityManagerFactory` (unit report),
  `reportTransactionManager`, `reportJdbcTemplate`; không @Primary hoặc generic manager alias.
- Flyway `classpath:mediflow/report/db/migration`: nguyên bytes 24 SQL files, latest V27.
  Không scan generic db/migration vì versions overlap Notification; không repair/renumber/copy data.
  Fresh PG và V26 → namespaced V27 → standalone validate giữ checksums và new writes đã có proof.
- `reportSecurityFilterChain` order 20 match `/api/v1/reports/**`; named JWT filter/registration,
  servlet auto-registration disabled; advice `reportGlobalExceptionHandler` scoped Report web.
  Host phải có management/default-deny chain sau module chains, không mở quyền bằng catch-all.
- Rabbit bean names riêng: reportEventsExchange/reportDeadLetterExchange,
  reportRabbitJsonMessageConverter/reportLegacyListenerFactory. Wire queues giữ nguyên;
  selected V1 queue `report.outpatient-v1.q`, DLQ `report.outpatient-v1.dlq`.
- Jobs bind `reportTaskScheduler`, 1..2 threads, prefix report-recovery-; qualified reportClock/
  reportZoneId. Notification phải dùng scheduler riêng. ONE host common ObjectMapper theo wire
  contract; không competing unqualified mappers hoặc full-V2 customizers.
- Shared owner tạo/register `backend/support-service/` (hiện chưa tồn tại), ONE process/discovery,
  explicit imports, không default JPA/repository bootstrap lẫn module. Đăng ký root POM/Compose/CI
  và sáu-app profile đúng source hiện tại; standalone Report/Notification không chạy song song.
- Required proof: packaged host + hai disposable PG16 + Rabbit, đúng credentials/migrations/tx;
  role/owner-only reads; Report replay/load không resend notice hoặc chiếm toàn pool; Notification
  timeout không rollback Report; restart/upgrade/rollback giữ writes và pending state.
  Cùng JVM vẫn chung failure domain, không hứa isolation tuyệt đối.

## 4. Activation, coverage và cutover

Canonical profile `outpatient-mvp` chỉ là module mode, không chứng minh topology sáu-app.
Public Pharmacy cần sáu gates: global V2, identity, clinical-context, clearance-consumer,
clearance-authority, outpatient-api; default OFF. V20 enrollment thêm
`mediflow.pharmacy.outpatient-events.enabled` và `.acceptance-reference` với đủ profile/gates.
Acceptance reference ghi quyết định operator, không thay owner acceptance. Historical HELD không
join bằng flag/replay, immutable cohort fence vẫn giữ; enrolled terminal flows giữ original proof.

Report coverage V27 chưa có accepted historical coverage. Cần sign-off CLINICAL/LAB/PHARMACY/
BILLING_CASH/BILLING_REFUND theo exact period/department/zone/VND và source exports/cursors.
Không fixture seed historical coverage, không dùng quiet queue làm zero. New facts/late refunds
làm stale witness phải reconcile/reaccept; pending/conflict vẫn unavailable.

Operator chạy read-only inventory trên approved clone và quan sát broker trước stop/drain/switch;
lưu source/hash/time/owner. Rehearse rollback trên current DB, giữ pending receipts/cursors/HELD/
accepted writes, không purge/reset inbox hoặc restore old snapshot đè payments mới.
Chi tiết commands/checklist giữ ở cutover runbook; đó là evidence/checklist, không handoff thứ hai.

## 5. Deferred compatibility và cách đóng bàn giao

Surgery không là blocker MVP. Ba Surgery fixture-path tests đã trỏ Core giữ nguyên bytes;
production Organization/Patient clients vẫn dùng legacy discovery names. Khi được tái kích hoạt,
Huy retarget Core và verify exact paths/JWT/strict response, giữ logical event producer
`organization-service` cho tới versioned contract. Không bật Surgery/Inpatient để đóng task Huy.

Các link trong frontend plan/historical specs từng trỏ retired care-finance/Surgery pointers
được chuyển về đây để có một đầu mối backend Huy. Các yêu cầu Surgery commands/shared header/nav
và full-V2 Report operator publication trong tài liệu lịch sử vẫn deferred theo scope canonical;
không trở thành acceptance mới của 48 subtask ngoại trú hoặc quyền sửa shared frontend.

Khi nhận việc, owner ghi source commit, exact scenario/IDs/hashes và run/fail/skip evidence vào
chính file này. Mỗi subtask chỉ tick khi toàn bộ acceptance đạt. Đưa lasting rules vào canonical
contracts/service docs; chỉ gỡ file handoff khi mọi active gate được cả producer/consumer nghiệm thu.

## 6. Frontend MVP — gate tích hợp còn lại (2026-10-16)

Theo yêu cầu chỉ dùng một handoff, phần phụ thuộc frontend tiếp tục ghi tại đây. Không tạo
handoff FE mới hoặc coi browser fixture là bằng chứng backend chạy thật. FE local checklist nằm ở
[plan Huy, mục 13](../../frontend/docs/huy-frontend-implementation-plan.md#13-checkpoint-pharmacyreport-và-frame-6-service--2026-10-16);
phần kê đơn/hủy/xuất thuốc còn trong scope Huy tiếp tục thực hiện theo plan, không giao lại owner khác.

| Phụ thuộc | Owner / bằng chứng cần nhận | Điều kiện nghiệm thu FE |
|---|---|---|
| Signed identity và Gateway runtime | Hoàng Anh + runtime operator; actual roles ADMIN/PHARMACIST/DOCTOR/MANAGER, accountId/staffId đúng source | Login → catalog/detail/create/adjust/replay/reports qua same-origin Gateway; allowed/denied paths, expired session và correlation đúng. Token fixture không thay signed staff proof |
| Outpatient Rx source/paid flow | Vinh Clinical + Lộc Billing, theo H6-06.2/.4 và H6-12.1 ở trên | Chỉ dùng IDs trả về từ source, current charge/grant và signed actor; nối UI public outpatient sau activation acceptance, không tự fallback legacy V0 để báo V1 thành công |
| Support host và endpoint routing | Lộc Notification + shared build/runtime owner + Hoàng Anh Gateway, theo H6-11 | Report/Notification logical routes tiếp tục đúng qua một Support host đã nghiệm thu; không đổi feature folder theo deployment hoặc coi sáu nhóm sidebar là proof sáu process |
| Report currency, department và coverage | Huy Report + Lộc Billing + Hoàng Anh Organization và source owners | Legacy DTO giữ numeric format/department UUID khi chưa có display contract. Current accepted period/zone/currency/coverage có owner evidence trước UI snapshot mới; unavailable không biến thành zero |

Đã có local test/browser evidence cho Reports, drug management và outbox; build/check results
ghi ở workboard. Các dòng này không đóng 10 backend subtask, không cấp quyền sửa foreign/shared
production paths và không bật Surgery/Inpatient/full-V2 operator UI.
