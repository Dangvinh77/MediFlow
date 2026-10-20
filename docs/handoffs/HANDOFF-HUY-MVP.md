# Handoff chung Huy — Pharmacy, Report và Support

## Phân công hiện hành — 2026-10-18

Giữ kết quả Huy 48/48 trong môi trường đã quan sát, không giao lại implementation đã hoàn tất. Theo chỉ đạo người dùng, **Vinh nhận toàn bộ kiểm thử backend**, gồm fresh full Pharmacy/Report/Support và sáu-app/recovery/cutover regression trên revision chung, tại [queue V-TEST-01–06](HANDOFF-VINH-BACKEND-VERIFICATION.md). Huy chỉ nhận task sửa production mới khi có lỗi/expected-actual cụ thể; không phải chạy lại toàn bộ test để bàn giao. Phân công này thay các yêu cầu owner acceptance còn mở trong phần lịch sử bên dưới.

Rà soát FE mới nhất: **2026-10-18**, baseline `025ef39e` + working diff chưa commit/push,
Pharmacy và toàn bộ Support tại mục 6. Rerun six-service API acceptance **2026-10-18** PASS (mục 6);
persisted-volume acceptance gần nhất **2026-10-17**, Support baseline
`27b8832d` cùng evidence mục 11. Baseline lịch sử `9ef3b151f895cef4c05916bf53331ff1d525f920`
cộng thay đổi owned trong workspace; implementation trước đó đã merge PR #385 (`7610cb97`,
merge `f15430dc`). User giao: hoàn tất phần độc lập trong phạm vi Huy, gom phần còn lại vào
**một file handoff**. Đây là đầu mối duy nhất thay các handoff activation, Support, Pharmacy
Core lookup, Surgery Core paths và hai retired pointers care-finance/Surgery decisions cũ;
không tự cấp quyền sửa production foreign/shared paths. **Override của người dùng ngày
2026-10-17:** Huy trực tiếp xử lý phần Billing/Notification của Lộc liên quan Pharmacy/Report,
không chờ bàn giao cho Lộc. Quyền này áp dụng `backend/billing-service/**` và
`backend/notification-service/**` trong task; không tự mở shared build/Compose/CI/Gateway.
Tiến độ triển khai/kiểm thử của override được ghi tiếp ở mục 8, không tạo handoff mới.
**Giao quyền tiếp theo cùng ngày:** hoàn thiện phần Lộc còn thiếu và tích hợp thành Support,
bao gồm `backend/support-service/**`, Notification/Report và shared root POM, Dockerfile,
Compose, CI, Gateway cần thiết. Source/verification ở mục 9; ownership lâu dài không đổi.

Nguồn: [plan Huy](../plans/2026-10-14-huy-backend-mvp-adjustment-plan.md),
[contracts](../plans/03-contracts.md), [reliability](../plans/04-data-reliability.md),
[manifest](../plans/huy-backend-mvp-baseline.md), [batch 9 evidence](../plans/huy-mvp-batch-9-acceptance.md),
[cutover runbook](../plans/huy-mvp-cutover-runbook.md).
Không copy payload thành spec mới; exact wire lấy từ DTO/serializer/fixture source đã khóa.

## 1. Chốt phần Huy — 48/48 trong môi trường local hiện có

Không còn subtask Huy cần bàn giao. Người dùng giao thực hiện tiếp H6-02.3/H6-12.3; đã cold-copy
chính các persisted Compose volumes cũ, inventory cả hai Rabbit node identities và diễn tập
actual legacy → Support → rollback legacy → Support cùng broker restart. Source không có
historical business rows trong ba context thuộc phạm vi; không tự tạo dữ liệu lịch sử.
Giữ bốn API writes mới, Report rows/histories và durable probe bytes. Original-volume SHA-256
trước/sau trùng nhau; không baseline schema Pharmacy, không dual writer, purge hoặc release HELD.

Evidence hiện hành ở mục 11 và [runbook mục 9](../plans/huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).
**2.129 unique tests / 196 class, zero failure/error/skip**, gồm selection mục 10 và một
persisted-volume IT mới; running-source refusal guard và năm JSON checks ghi riêng.
Billing/Pharmacy vẫn là selected suites. Kết quả không tự đóng global M6/paid Lab/FE hoặc
production activation. Một môi trường khác có historical data cần inventory/coverage proof
của chính nó; không dùng kết quả local để ký thay.

Phần độc lập H6-08.4 đã đóng: real PG/Rabbit same-byte replay/conflict/early refund/rollback,
fault sau commit trước ACK, legacy queue retention và actual Clinical outpatient completion.
Fault receiver trong JVM không được gọi là JVM kill; batch 8 có JVM crash proof riêng cho V0.
Current full regression: Pharmacy 822, Report 1.330; source acceptance 1; packaging probes 5,
đều 0 failures/errors/skips. Xem batch 9 để không cộng trùng rerun hoặc nhầm coverage.

## 2. Dấu vết phối hợp theo owner trước khi chốt

Các checklist mục 2–9 là lịch sử và ràng buộc hợp đồng của từng lượt. Trạng thái hiện tại ở
mục 1/11 thay các nhận định cũ về Docker, paid Rx hoặc Support chưa chạy; không giao lại
những phần code và nghiệm thu đã hoàn thành.

### Lộc — Billing và Notification

Kế hoạch code cho Billing discovery và new-outpatient issuer/payment/grant publication đã chốt
tại [handoff Lộc L1–L4](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md), source `71e2684d`.
Lộc triển khai trực tiếp checklist đó; không cần lên kế hoạch khác. Đây là deliverable Billing
để nhận các bytes/purposes đã được consumer nghiệm thu, không tự đóng Notification,
terminal/refund, Support hoặc các subtask Huy còn mở bên dưới.

- Source refresh tại `16a5e06e`: `PrescriptionChargeService.issue` đã dùng
  `appendForOutpatientRequest` cho V1 `invoice.created`; discovery và immutable **NEW outpatient**
  publication cohort đã có trong Billing. Timeout packaged probe trước đây là evidence lịch sử,
  không còn là khẳng định issuer chưa được viết. Hai Rabbit acceptance cases còn thiếu đã được
  bổ sung ở mục 8; runtime paid chain vẫn phải chạy thật. Không cấp invoiceId/paymentRequestId giả
  hoặc cập nhật Billing DB để vượt gate.
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

**Mobile Pharmacy 2026-10-20 — MP-HUY-01/02/03 local DONE:** **3/12 task**, model/DTO/Gateway
read adapter và catalog/detail/refresh đã triển khai trong feature Huy, không sửa shared mobile.
Full mobile+loader **87/87 test PASS**, analyzer/format/web build PASS; chưa có signed Gateway/native proof.
[Plan mobile](../../mobile/docs/huy-pharmacy-mobile-plan.md) và
[integration manifest](../../mobile/lib/features/pharmacy/README.md) là đầu mối code/contract.
MP-HUY-11 cần Vinh phối hợp `mobile/lib/app/dependencies.dart`, `router.dart`, `modules.dart`:
bind `pharmacyRepositoryProvider` với shared ApiClient, bind `pharmacyAccessProvider` từ reactive
actual session, opaque identity đổi theo phiên và role guard cho nested route. Local detail navigation
đã có; optional callback dùng nếu chọn deep links. Không mark module implemented trước smoke.
Read slice không cần mở API Gateway mới hoặc Report coverage. PUT/Idempotency-Key shared transport
chỉ cần trước mobile commands 04/05/08–10; không bypass client hoặc nới auth để demo read slice.
Tất cả dependency mobile vẫn ghi tại file chung này; không tạo handoff mới.

**Cập nhật 2026-10-20 sau merge master `8883f49d`:** người dùng giao đúng hai shared files
`frontend/src/lib/api.ts` / `api.test.ts` để bảo vệ phiên mới trước late 401. Working diff
đã sửa và bốn regression tái hiện lỗi trước sửa, sau sửa focused transport + Pharmacy/Report/
Notification **459/459**. Shared late-401 dưới đây đã giải quyết ở mức code/regression;
không còn chờ giao quyền hoặc chờ Vinh viết fix. Vẫn cần runtime matrix HF6-11.3 và release
candidate cuối; không dùng unit tests để đóng task browser. Tiến độ giữ **10/14, 49/56**;
chi tiết kết quả cuối tại [plan mục 19](../../frontend/docs/huy-six-service-frontend-plan.md#19-bảo-vệ-phiên-mới-trước-phản-hồi-401-cũ--2026-10-20)
và workboard. Core public account contract bắt buộc PATIENT có patientId; negative signed-token
thiếu claim cần bộ kiểm thử backend được cấp, không tạo invalid account hoặc tự ký token rồi
gọi là actual issuer evidence. Refund acceptance, reviewed coverage và Gateway dependency còn mở.
Giữ đúng nhánh Huy, không commit/push trong lượt này; các checkpoint dưới là lịch sử.

**Report writer bổ sung 2026-10-20 — triển khai theo yêu cầu người dùng:** đã thêm production
preview/ADMIN acceptance trong Report, V28 audit và idempotent atomic approval; contract và
quy trình ở [operator workflow](../../backend/report-service/operations/outpatient-coverage-acceptance.md).
Các dòng audit `chưa có writer` dưới đây là kết quả trước thay đổi này. Không tạo coverage giả
hoặc tự ký thay source owners; giữ tất cả gate mặc định OFF. Final clean verify ngày 2026-10-20
PASS **1.440/1.440 Report tests + 5/5 packaging acceptance**, không fail/error/skip; có 33 test
mới, gồm signed JWT qua HTTP thật và PostgreSQL. H6-13 đạt **4/4 phần Report** trong plan backend.
Candidate là working tree trên `44ff1ed7`; chưa commit/push. Đây chưa là Gateway→Support,
browser acceptance hoặc chứng nhận bộ dữ liệu production.

**Gateway dependency (người dùng chọn giữ ngoài scope):** Hoàng Anh/Vinh phối hợp thêm đúng
GET `/api/v1/reports/outpatient/coverage/preview` và POST
`/api/v1/reports/outpatient/coverage/acceptances`, chỉ ADMIN; giữ Support target, default deny
nested/wrong methods và signed access-token validation. Gateway hiện chưa allowlist hai route.
Không sửa Gateway trong task này hoặc cho FE gọi service port. Sau khi có route và reviewed
exports thật đủ năm nguồn mới chạy HF6-09/11/12 qua Gateway; module HTTP/PG không thay browser
acceptance và không tự nâng tiến độ FE 49/56.

**Checkpoint hiện hành 2026-10-20:** **10/14 task, 49/56 tiêu chí**. Chi tiết tại
[FE plan mục 18](../../frontend/docs/huy-six-service-frontend-plan.md#18-signed-notification-acceptance-và-audit-report-source--2026-10-20)
và [evidence](../../frontend/docs/evidence/huy-six-service/2026-10-20-closeout/README.md).
Các checkpoint bên dưới giữ làm lịch sử. Đã đóng HF6-11.4 và HF6-14.1/.2/.4 bằng actual issuer
ADMIN/NURSE/PATIENT qua Gateway→Support: đúng người/cross-patient deny, manual IN_APP, private
lifecycle/payment intake, theme/keyboard và actual Support unavailable→retry recovery.

Các đầu vào còn thiếu, gom tại đúng file này:

- **Report HF6-09.2/.3/.4, HF6-11.2:** writer Report đã được bổ sung tại H6-13; Gateway và
  nguồn đối soát thật vẫn OPEN. Audit trước đó trên local/master `71328cbc` chưa có writer là
  baseline lịch sử; INSERT trong isolated test vẫn không là accepted source export. Cần source owners
  CLINICAL/LAB/PHARMACY/BILLING_CASH/BILLING_REFUND cung cấp export/cursor/reconciliation và
  acceptance authority đúng scope/zone/VND/period, timestamps bao trùm ngày cuối, actor/reference.
  Vinh phối hợp coverage được backend chấp nhận trước signed success ADMIN/MANAGER và UI ON/OFF.
  Không seed coverage, tự ký thay source owners hoặc coi 404 REPORT_NOT_FOUND là zero. Signed
  ADMIN/MANAGER 404, DOCTOR 403 đã ghi; Report UI tiếp tục OFF.
- **Notification HF6-14.3 — Vinh backend:** refund consumer OFF mặc định, existing acceptance
  hold không bật; chưa nghiệm thu refund notice hoặc signed token thiếu patientId. Cần backend
  nghiệm thu các trường hợp này trong môi trường được cấp; giữ no accountId fallback. Lifecycle
  và EXAM payment privacy đã đạt, không giao lại. EMAIL/SMS chưa có provider/recipient acceptance.
- **HF6-11.3/HF6-12.4:** hoàn tất global session/unavailable/retry matrix và final browser release
  trên candidate sau dependency. Shared late-401 đã sửa theo checkpoint mới đầu mục 6. Full rerun 590/591 còn Lab
  fixture `LabDetail.test.tsx:194` ngoài scope Huy; Vinh regression phối hợp owner để xử lý.
  Focused 446/446 và typecheck/lint/build PASS không thay full-suite hoặc browser acceptance.

Verdict **HOLD integrated release**. Không bật thêm gates, không sửa backend/shared auth/Lab,
không tạo nhánh/commit/push/PR. Disposable hold đã STOP, không dùng normal refund/rollback branch
trong lượt này; không claim lặp lại toàn bộ Pharmacy workflow từ bằng chứng cũ.

**Checkpoint hiện hành 2026-10-19:** signed Gateway/browser acceptance đạt trên candidate
`51eedfcd` + diff, nâng plan lên **10/14 task; 45/56 subtask**. Đóng HF6-01/06/07/10:
actual issuer login, browser tạo Rx V1, API CASH payment, browser refresh/xuất một effect,
catalog/stock/outbox, Report legacy và keyboard/theme/responsive. Đã sửa read Pharmacy V1
không nhận verified clearance; focused backend rerun 48/48, opt-in browser hold 1/1 PASS, 770.5s.
Xem [plan mục 14](../../frontend/docs/huy-six-service-frontend-plan.md#14-nghiệm-thu-signed-gateway-và-browser--2026-10-19)
và [evidence](../../frontend/docs/evidence/huy-six-service/2026-10-19/README.md).
Các ghi chú local/runtime bên dưới giữ lịch sử; không dùng chúng thay checkpoint mới.

**Năm đầu việc local 2026-10-19:** hoàn tất B5-01…05 cho timeout tạo Rx V1/tạo thuốc/gửi Notification/
hủy-xuất Rx/replay outbox. Thêm 13 ca hồi quy, focused 124/124; HTTP 408 không mở lại blind mutation,
Rx V1 retry giữ key/payload, terminal retry chỉ đọc và outbox cần explicit confirmation cùng event.
Chi tiết và checks cuối ở [plan mục 13](../../frontend/docs/huy-six-service-frontend-plan.md#13-đợt-năm-đầu-việc-timeout--2026-10-19).
Đây là local work items, không đóng năm HF6 runtime task hoặc sinh handoff mới.

**Stock follow-up 2026-10-19:** local Pharmacy giữ kết quả điều chỉnh kho chưa rõ qua lỗi refresh,
403/404 và retry đọc; giữ correlation/delta, bỏ draft cũ và cần đối chiếu sau fresh read trước khi lập
lệnh mới. Không thêm API/idempotency key hoặc tự kết luận lệnh đã rollback. Thêm 11 ca hồi quy,
focused 46/46; candidate/checks cuối ở [plan mục 12](../../frontend/docs/huy-six-service-frontend-plan.md#12-giữ-kết-quả-điều-chỉnh-kho-chưa-rõ-qua-lỗi-tải-lại--2026-10-19).
Không tạo dependency hoặc handoff mới; signed browser/coverage/shared auth dưới đây giữ OPEN.

**Local FE follow-up 2026-10-19:** trên `51eedfcd` + diff, Huy tiếp tục keyboard/focus cho kê đơn,
gửi Notification và bốn Report views, gồm 22 ca hồi quy mới. Chi tiết/evidence candidate cuối ở
[plan mục 11](../../frontend/docs/huy-six-service-frontend-plan.md#11-keyboard-và-focus-sau-đồng-bộ-master--2026-10-19)
và workboard. Không tự đóng signed browser, Report coverage hoặc shared late-401 bên dưới.

**Refresh 2026-10-18:** plan thực thi FE hiện hành ở
[FE Huy — 6 service](../../frontend/docs/huy-six-service-frontend-plan.md).
Gateway exact Pharmacy cancel/dispense policy và Support/backend EXAM/Rx acceptance đã có ở
source hiện tại; các mô tả generic PUT/chưa có Support trong bảng lịch sử bên dưới không còn là
gap implementation. Còn nghiệm thu browser FE bằng signed users và cấu hình/coverage của môi trường.
User giao Huy **Pharmacy và toàn bộ Support FE**, gồm Notification, đồng thời trả lời câu hỏi giao
`frontend/src/lib/api.ts`/`api.test.ts` cho optional POST `Idempotency-Key`. Shared header đã nối;
form V1, retry cùng snapshot/key, runtime mode và manual payment refresh đã triển khai. Notification
lookup/detail/send đã có contract/validation/role/privacy/session/race guards và tests. Không còn
chờ Lộc/Vinh code các phần này; ownership lâu dài không đổi. Read Rx DTO vẫn không có metadata V1.
Public outpatient creation yêu cầu exact record ở `PublicOutpatientPrescriptionCreationService`,
dù DTO V1 tổng quát cho phép null; không dùng nullability DTO để nới public form.
Report đã refresh local contracts/regression (HF6-08), runtime HF6-09 vẫn OPEN. Các dòng dưới giữ
evidence/checklist lịch sử, không tự đóng mới hoặc mở flags theo số task backend.

**Các việc còn thực sự cần nghiệm thu FE:**

**Source audit PR #446, base `5ccecf83` — checkpoint lịch sử cho Vinh:**

| Input public Rx V1 | Nguồn public đã xác nhận | Phần còn thiếu / điều kiện sử dụng |
|---|---|---|
| `recordId`, `patientId`, `doctorId`, `departmentId` | Clinical `GET /api/v1/records/{id}` hoặc `/patient/{patientId}` trả `MedicalRecordDTO` | Lấy cùng một record đúng bệnh nhân/actor; không lấy account UUID làm staff/doctor UUID |
| Staff/department identity | Core `GET /api/v1/org/staff/{id}`, `/api/v1/org/departments/{id}` và list public theo role | Dùng identity trả từ source, không gọi các lookup chỉ dành cho service; Pharmacy vẫn revalidate signed prescriber |
| `careEpisodeId` | `ClinicalLabOrderDTO` trả khi public `POST /api/v1/records/{id}/lab-orders` tạo chỉ định Lab có thật | MedicalRecord DTO chưa có explicit episode. Không lấy `appointmentId`/`recordId` thay episode, không tạo chỉ định xét nghiệm chỉ để có ID kê đơn. Vinh cần phối hợp public Clinical entrypoint cho workflow chỉ kê thuốc |
| `priceCode` | Billing config `mediflow.billing.prescription-charges.allowed-price-codes` xác định allowlist; harness cô lập dùng `DRUG` | Chưa tìm thấy public danh mục giá/selection contract cho production. Không coi `DRUG` trong test là giá trị production mặc định; Vinh phối hợp Billing cung cấp mã hợp lệ theo môi trường |

`ClinicalLabOrderContextDTO` và prescription context lookup là contract service-to-service,
không phải nguồn browser được phép gọi. Form tiếp tục yêu cầu đầu vào riêng, không sinh authority
IDs và không tự tính tiền/clearance. Audit này chỉ chốt nguồn có thật; **HF6-01.2 còn OPEN tại checkpoint này** cho
hai dependency episode/priceCode ở workflow public tổng quát. Nghiệm thu visit test và trạng thái
HF6-01 mới hơn được ghi bên dưới; vẫn không suy diễn nguồn ID hoặc priceCode cho production. Không tạo handoff trùng lặp.

Evidence V1 riêng của PR #446 được giữ tại [plan mục 15](../../frontend/docs/huy-six-service-frontend-plan.md#15-checkpoint-pr-446-sửa-dispense-v1-theo-runtime-contract--2026-10-19).
Tiến độ hiện hành theo mục 14: **10/14 task, 45/56 subtask**; checkpoint 6/14, 41/56 của PR này là lịch sử.

- HF6-01/06/07/10 đã đóng: public source record/Core responses và explicit episode từ public
  Clinical order của visit test; `DRUG` chỉ là allowlist của Billing test. Không suy appointmentId
  thành episode hoặc biến Lab order thành bước bắt buộc production. Signed role/payment/dispense,
  stock/replay và keyboard/theme có evidence mới; không còn chờ các gate này.
- HF6-09: accepted outpatient coverage đúng scope, ADMIN/MANAGER, reader ON/OFF có restart.
  Report legacy ADMIN 200/DOCTOR 403 và recovery Support đã đạt; chưa thay accepted coverage.
- HF6-11: nối tiếp Pharmacy workflow đã đạt tới Report sau đối soát, hoàn tất session/unavailable
  matrix và Notification private notice bằng public API, đúng người/cross-patient deny.
- HF6-14: browser Notification signed ADMIN/NURSE/PATIENT, owner/cross-patient denial,
  manual IN_APP và lifecycle/payment/refund privacy. EMAIL/SMS cần test provider/recipient
  được cấp; không gửi người thật để lấy evidence. Intake một notice sau replay chưa đóng privacy.
- HF6-12.4: release cuối sau các gate trên; shared late-401 dependency bên dưới vẫn OPEN.
- Server FE: `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE` unset/`outpatient`→V1, `legacy`→V0 explicit,
  giá trị khác đóng form; route dynamic. `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED` vẫn OFF mặc định,
  chỉ ON sau reader/coverage acceptance. Hai env không bật profile/flags backend.
- Runtime API rerun 2026-10-18: `SixServiceOutpatientAcceptanceIT` 1/1, zero failure/error/skip,
  211.5s, actual staff logins, PG16/Rabbit disposable, six apps + Gateway/Eureka; Gateway→Support
  hai stores và rollback standalone trên test stores đạt. Rx paid 30.000 VND không auto dispense,
  manual dispense stock 20→17, duplicate commands không thêm effect. Không đổi original volumes.
  Bootstrap ADMIN/PATIENT/MANAGER privacy tokens là harness-signed, không phải issuer PATIENT login.
  Đây là API harness, chưa signed FE browser hoặc accepted outpatient Report coverage. Fresh outputs:
  `backend/support-service/target/six-service-acceptance/` và Failsafe reports, summary ở plan mục 9.

Candidate B10 đạt **501/501 FE tests, 55 files**, zero failures/skips; typecheck/lint/build PASS.
B20 denial/focus tiếp theo focused 108/108, full **506/506 / 55 files**; final checks ở plan mục 10/evidence.
Plan hiện hành **10/14 task; 45/56 subtask** (Pharmacy/Report gốc 9/12; 41/48, Notification 4/8).
Tiến độ/checks candidate hiện hành ghi ở plan mục 14 và workboard; chỉ dùng một handoff này.

**Shared dependency — old 401 clears new session (đã sửa code/regression 2026-10-20; mô tả lỗi gốc):**
`frontend/src/lib/api.ts` captures token khi dispatch nhưng line 81 clearSession vô điều kiện khi response 401.
Request của A có thể trả 401 sau khi B đã login và xóa session B. Feature-local guards/remount hiện tại
bỏ callback cũ nhưng không ngăn side effect global của wrapper. Lifecycle tests mock feature facades,
không phải bằng chứng transport đã an toàn cho race này. Scope shared của lượt hiện tại chỉ optional
POST Idempotency-Key, không tự đổi semantics auth để đóng task. Acceptance khi được giao shared fix:
wrapper test A pending → store session B → A 401 phải giữ B; current-session 401 vẫn clear/redirect
đúng; kiểm anonymous/login/refresh callers không regress. Không log tokens/credentials hoặc lưu intents mới.

Theo yêu cầu chỉ dùng một handoff, phần phụ thuộc frontend tiếp tục ghi tại đây. Không tạo
handoff FE mới hoặc coi browser fixture là bằng chứng backend chạy thật. FE local checklist nằm ở
[plan Huy, mục 14](../../frontend/docs/huy-frontend-implementation-plan.md#14-checkpoint-kê-đơn-và-terminal-actions--2026-10-16).
P-05 đã đóng FE local; P-06 xong 4/4 subtask local và còn real-role smoke, không giao lại phần
source Pharmacy cho owner khác. Tổng FE local 11/30 task chính, 50/123 subtask; mẫu số lịch sử
có Surgery/full V2 deferred, không phải tỷ lệ MVP backend.

| Phụ thuộc | Owner / bằng chứng cần nhận | Điều kiện nghiệm thu FE |
|---|---|---|
| Signed identity và Gateway runtime | Hoàng Anh + runtime operator; actual roles ADMIN/PHARMACIST/DOCTOR/MANAGER, accountId/staffId đúng source | Login → catalog/detail/create/adjust/replay/reports qua same-origin Gateway; allowed/denied paths, expired session và correlation đúng. Token fixture không thay signed staff proof |
| GW-01 — exact Pharmacy cancel/dispense roles | Policy source đã đúng: Gateway/controller cancel ADMIN/DOCTOR, dispense ADMIN/PHARMACIST; còn nghiệm thu signed actors thật | DOCTOR kê đơn đúng signed actor được cancel đơn của mình; foreign doctor/PHARMACIST bị deny cancel; ADMIN/PHARMACIST dispense chỉ khi lifecycle/payment đủ. Ghi actual allowed/denied HTTP và correlation; không đổi DOCTOR thành PHARMACIST để qua Gateway |
| Outpatient Rx source/paid flow | Vinh Clinical + Lộc Billing, theo H6-06.2/.4 và H6-12.1 ở trên | Chỉ dùng IDs trả về từ source, current charge/grant và signed actor; nối UI public outpatient sau activation acceptance, không tự fallback legacy V0 để báo V1 thành công |
| P-08 — hỗ trợ header cho public Rx V1 | DONE local 2026-10-18: user giao Huy `api.ts`/`api.test.ts`, Pharmacy đã dùng public wrapper | Optional POST key, bearer/envelope/errors/correlation và bodyless callers đã kiểm thử. Intent giữ key/payload khi retry; command mới dùng key mới. Còn kiểm header qua Gateway thật trong workflow HF6-11 |
| Support host và endpoint routing | Backend host/routing đã tích hợp và nghiệm thu local ở mục 11; Huy trực tiếp làm Notification FE theo override mới | Còn browser Report/Notification qua Gateway→Support thật trên candidate FE; không đổi feature folder theo deployment hoặc coi sáu nhóm sidebar là proof sáu process |
| Report currency, department và coverage | Huy Report + Lộc Billing + Hoàng Anh Organization và source owners | Legacy DTO giữ numeric format/department UUID khi chưa có display contract. Current accepted period/zone/currency/coverage có owner evidence trước UI snapshot mới; unavailable không biến thành zero |

Đã có local test/browser evidence cho Reports, drug management và outbox; build/check results
ghi ở workboard. Các dòng này không đóng 10 backend subtask, không cấp quyền sửa foreign/shared
production paths và không bật Surgery/Inpatient/full-V2 operator UI.

Batch kê đơn/terminal actions bổ sung 51 tests; full FE suite **262/262 tests, 34 files**,
typecheck/lint/build đạt. Browser với gateway fixture cô lập đã kiểm tạo → response ID, hủy →
releasedReservations, xuất → terminal read; focus/Escape/return-focus và desktop/mobile themes.
Đã khôi phục theme System, đăng xuất và dừng server thử. Đây là bằng chứng FE local dùng mock,
không đóng GW-01, paid/source V1, Support host hoặc real-role release acceptance.

Bổ sung FE MVP cùng ngày — `R-MVP-01` đã có route `/reports/outpatient`, daily inclusive
range/monthly whole-year facade, exact OutpatientReportDTO và UI coverage/currency/zone/periods.
Xem [plan mục 15](../../frontend/docs/huy-frontend-implementation-plan.md#15-checkpoint-report-ngoại-trú-theo-backend-mvp--2026-10-16).
29 tests mới; full FE suite **291/291, 36 files**, typecheck/lint/build đạt; browser fixture đã kiểm
gate OFF không request Report, gate ON ngày/tháng/12 kỳ, Back/Forward, thiếu coverage và mobile.
Đây là local FE acceptance, không thay các điều kiện source/coverage/Support/Gateway trong bảng.

Owner/runtime chỉ mở `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true` trên server frontend rồi restart
sau khi reader chạy dưới profile outpatient, hai backend flags hiện hành đã được owner nghiệm thu,
coverage thật đúng khoa/period/currency/zone và signed ADMIN/MANAGER qua Gateway được xác nhận.
Không cần thêm capability endpoint hoặc mở Financial/Operational/Surgery V2. UI mặc định đóng cả
direct URL và entry, không fallback sang legacy nếu `REPORT_NOT_FOUND`; unavailable không thành zero.
Không đóng thêm H6-11/H6-12 hoặc sửa topology/foreign paths trong batch FE này.

Ghi chú lịch sử, đã được thay bởi refresh 2026-10-18 ở đầu mục 6: ngày **2026-10-17**, source baseline `ebef4508`, public
`api.post(path, data)` chưa nhận headers, dù transport nội bộ đã dùng `Headers`.
`PrescriptionController` yêu cầu canonical UUID `Idempotency-Key` khi
`careContractVersion=1`; thiếu/sai key bị từ chối. Cần shared owner cung cấp khả năng này
trước khi Huy nối P-08; chưa có quyền sửa shared API trong task hiện tại. Response
`PrescriptionDTO` hiện không xuất careContractVersion/careContext/episode/priceCode;
FE không thêm metadata suy diễn hoặc dùng `recordId=null` làm bằng chứng V1.
Đây là phụ thuộc FE bổ sung trong cùng file, không đóng gate activation hoặc backend subtask.

## 7. Backend continuation — Pharmacy disposition guard (2026-10-16)

Đã sửa trong scope Huy: relationship Clinical có disposition `ADMISSION` hoặc `TRANSFER`
không được tạo đơn ngoại trú hoặc replay creation command. Pharmacy trả
`PHARMACY_CARE_CONTEXT_INVALID` (HTTP 422), không tạo thêm prescription, creation receipt,
reservation, dispense slip hay outbox. Completed outpatient `PRESCRIPTION`,
`OUTPATIENT_FOLLOW_UP`, `OTHER` vẫn được dùng để replay đúng đơn. Valid deferred disposition
là business denial, không đổi thành upstream outage. Wire DTO, golden fixtures, migrations,
V0 và activation/delivery gates giữ nguyên.

Verification của lượt này: **694 tests / 85 class không cần container, tất cả đạt, không skip**.
Phạm vi gồm observation, orchestration/writer, Clinical adapter qua HTTP và controller phân quyền;
không phải full suite có PostgreSQL/RabbitMQ. Core/Clinical packaged sources đã build thành công
để kiểm tra source contract, nhưng không có bằng chứng runtime tích hợp mới.

Đã bổ sung 5 PostgreSQL cases vào `OutpatientApiPostgresTest`: hai disposition bị chặn cho cả
key đã dùng và key mới (HTTP 422 + exact error code, không thêm dữ liệu/giảm stock), cùng ba
completed outpatient dispositions replay đúng prescription. `-DskipTests test-compile` đã đạt.
**Chưa chạy 5 case này**: Docker Desktop lỗi startup tại `dockerInference`; người dùng chọn
tiếp tục phần không cần Docker.

Huy/runtime operator chạy lại khi Docker hoạt động, từ repository root:

```powershell
mvn -q -pl backend/pharmacy-service -am '-Dtest=OutpatientApiPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Ghi run/fail/skip và source commit vào chính file này trước khi coi database guard được nghiệm thu.
Không đóng thêm checkbox: **38/48**; mười subtask external/operator còn mở theo các mục trên.

## 8. Trực tiếp xử lý Billing/Notification liên quan Huy (2026-10-17)

Theo override của người dùng, phần code dưới đây đã được thực hiện ngay trên nhánh Huy;
không giao lại cho Lộc và không tạo thêm file handoff. Baseline `16a5e06e`, các thay đổi trong
working tree chưa commit/push. Không thay golden producer fixtures, migrations, default rollout
flags, money authority hoặc shared paths.

**Notification:** sửa đường nhận V1 trước đây đưa sự kiện đơn thuốc vào bộ đọc
`payment.completed` rồi từ chối. Một reader trên `notification.q` nhận đủ năm original Pharmacy
outpatient lifecycle fixtures, kiểm logical producer/version/source/record/patient/department/episode
và full snapshot. Event claim, semantic source claim, private IN_APP history và HELD sent-outbox
cùng transaction; same operation/new event ID không sinh notice thứ hai, changed snapshot là conflict.
Thông báo hủy/hết hạn/lỗi cấp thuốc không được hiểu thành đã hoàn tiền. Không chép tên thuốc,
giá hoặc lý do terminal vào nội dung lifecycle notice, không suy contact hoặc account=patient.

Billing PRESCRIPTION `invoice.created` cũng được nhận trên queue hiện có, tái sử dụng decoder,
template và service đã có, không cần bật Surgery. Reader này không nhận deferred Surgery purpose.
Hai flag bắt buộc là `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED` và
`MEDIFLOW_NOTIFICATION_OUTPATIENT_PRESCRIPTION_CONSUMER_ENABLED`, đều default OFF.
Transient storage errors retry tối đa ba lần rồi DLQ; wire/conflict errors reject ngay.
Đã kiểm cả lỗi permanent bọc trong listener exception; không requeue vô hạn hoặc tự release DLQ.

**Billing:** kiểm source xác nhận issuer, exact source discovery, selected publication và original-linked
terminal/refund obligation đã có; không viết một money flow song song. Bổ sung hai actual
PG/Rabbit acceptance cases vào `OutpatientPublicationRabbitIntegrationTest`:

- New delivery IDs cho cùng prescription trước/sau thanh toán vẫn một charge/request/payment/grant,
  không phát invoice/receipt/grant lần hai; request tìm qua signed public HTTP, bytes nhận từ dispatcher.
- Issue outpatient Rx bằng một application context thật với publication OFF; sau đó dùng context ON
  để replay source và retry payment. Historical request không được enroll, cả ba facts vẫn HELD và
  original invoice bytes giữ nguyên. Không SQL seed request hoặc mock cohort để thay prior issuance.

**Verification thực chạy:** Billing **734 tests / 93 class** không cần container đạt; Notification
**221 tests / 23 class** không cần container đạt, gồm **37** contract/config/retry cases mới.
Tất cả đều zero failure/error/skip; không cộng trùng các lần rerun. Các class còn lại có
Testcontainers được loại khỏi selection này, vì vậy đây không phải full PG/Rabbit suite.
`test-compile` của hai module đạt, bao gồm integration tests mới.

**Chưa chạy:** 11 cases trong `PrescriptionNoticeRabbitIntegrationTest`, hai Billing Rabbit cases
mới và actual paid Pharmacy/Report/Notification chain. Docker Desktop vẫn chưa sẵn sàng; tiếp tục
theo lựa chọn trước đó của người dùng là làm phần không cần Docker. Khi có runtime, chạy:

```powershell
mvn -q -pl backend/notification-service -am '-Dtest=PrescriptionNoticeRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl backend/billing-service -am '-Dtest=OutpatientPublicationRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

H6-06.2/.4 vẫn cần runtime acceptance trước khi tick; không còn chờ Lộc viết intake Rx Notification
hoặc viết hai Billing cases ở trên. Support export/host/shared wiring, source coverage và operator
cutover là các phần khác chưa được chứng minh bởi batch này. Tổng plan giữ **38/48** cho tới khi
các tiêu chí gốc đạt, không đổi tiêu chí hoặc bật deployment để tăng tiến độ.

## 9. Support host — 2026-10-17

Người dùng giao trực tiếp phần Lộc còn thiếu và tích hợp Support. Đã thực hiện code trong
Notification/Report, tạo `backend/support-service` và sửa các shared paths cần thiết:
root POM, backend Dockerfile, Compose, Service integration CI và Gateway destinations.
Không chờ Lộc viết module export, không tạo handoff thứ hai. Source tại nhánh **Huy**,
HEAD `27b8832d25daee8218250224af3f8334318a37df`, cộng working tree chưa commit/push.

**Phần đã triển khai:**

- Notification xuất plain `lib` bên cạnh executable cũ, entry point
  `NotificationModuleConfiguration`; Support import hai entry points rõ ràng, không broad-scan
  launcher hoặc chép business code. Một executable/discovery name `support-service`, port 8087.
  Host dùng skill new-microservice theo blueprint composition-only.
- Hai named DS/Flyway/EMF/tx/JDBC/pools, credentials bắt buộc và resource locations riêng.
  Không default tx/datasource, không cross-store SQL/transaction. Giữ nguyên 24 Report và
  4 Notification SQL files, không repair/renumber checksum hoặc merge schema/data.
- Notification transaction/JDBC/Rabbit beans được qualify; repository trùng simple name được
  đặt tên riêng. JWT filters không tự đăng ký servlet filter; hai security chains/advice giữ
  path/role/patient semantics. Fallback host default deny. Report role không mở quyền Notification.
- Listener factories/queues/DLQs giữ tên cũ, budget tách module; Report giữ scheduler riêng.
  Không purge, auto redrive hay release HELD. Host chặn CareProjection/Surgery/deferred Report
  features; ngay cả khi outpatient/global gates ON, receipt/refund ADMISSION bị reject trước
  effects. Standalone compatibility vẫn nhận các contract đã có như trước.
- Root reactor có 13 module, Compose mặc định sáu business deployments: Core, Clinical, Lab,
  Pharmacy, Billing, Support. Hai legacy Report/Notification launchers ở `legacy-support`,
  Inpatient/Surgery ở `deferred`. Dockerfile dùng đúng executable filename, tránh copy cả lib.
  Gateway giữ route IDs/public paths; hai logical Support routes mặc định `lb://support-service`,
  destinations có override để rollback. Compose Gateway bật outpatient-mvp, không route deferred.
- New intake/read/delivery gates vẫn default OFF. Shared CI chạy `support-acceptance`, yêu cầu
  Docker, kiểm cả Surefire/Failsafe, không nhận skips hoặc thiếu packaged acceptance report.

**Verification thực chạy**, không cộng trùng reruns:

| Module | Concrete non-container classes | Tests | Failure / error / skip |
|---|---:|---:|---|
| Report | 96 | 940 | 0 / 0 / 0 |
| Notification | 24 | 226 | 0 / 0 / 0 |
| Gateway | 12 | 257 | 0 / 0 / 0 |
| Support | 4 | 13 | 0 / 0 / 0 |
| Tổng | **136** | **1.436** | **0 / 0 / 0** |

Selection loại rõ các Testcontainers classes; `ReportPersistenceTest` là annotation hỗ trợ,
không là một suite. Một Report unit class trùng tên với Notification container class được
chạy bổ sung bằng fully qualified name, không dùng XML lịch sử để tính. Kết quả aggregate
ở local ignored `backend/support-service/target/non-container-results.json`; logs
`non-container-regression.log`, `outpatient-regression.log`, `report-persistence-regression.log`.
Không gọi selection này là full suite PostgreSQL/RabbitMQ.

Build Support/Gateway với test selection đạt; **toàn reactor 13 module** cũng đạt
`mvn -q -DskipTests package` (biên dịch test sources, không chạy container tests). Actual boot JAR
inspection xác nhận đúng Start-Class, hai nested plain lib không có BOOT-INF riêng và mọi
SQL namespaced copy nguyên bytes. SHA-256 của toàn bộ 29 original migration resources
(28 SQL + .gitkeep) khớp trước/sau. Actual `java -jar` guard smoke load hai module profiles và
reject khi bật deferred Surgery trước store startup; đây không là real DB startup acceptance.
Static `docker compose config` đạt, danh sách default có đúng sáu business deployments.

**Đã code/biên dịch, chưa chạy do Docker chưa sẵn sàng:**

- Năm `SupportStoreIsolationTest` cases: hai stores/histories/pools không lẫn; original Pharmacy
  bytes cùng tới hai context, Report replay không thêm notice/outbox; Notification outbox fault
  rollback/DLQ độc lập với Report, original bytes recover, PATIENT other-owner denied.
  Hai pool-exhaustion cases giữ toàn bộ connections của từng context, kiểm context kia vẫn
  commit, context bị nghẽn bounded timeout/DLQ rồi recover từ original bytes mà không double effect.
- Một `SupportPackagedAcceptanceIT` case: migrate hai stores từ original standalone locations,
  launch actual JAR, accepted notification write + Report read, stop/restart, giữ write và
  Flyway checksums. Failsafe profile chạy sau package, không dùng IDE classpath làm proof.
- 11 Rx Notification integration cases và hai Billing Rabbit cases ở mục 8 vẫn chưa chạy.
  Current paid-chain/coverage/inventory/cutover/rollback cũng chưa có evidence mới.

Chạy khi có Docker, từ repository root:

```powershell
mvn -q -pl backend/support-service -am -Psupport-acceptance verify
mvn -q -pl backend/billing-service -am '-Dtest=OutpatientPublicationRabbitIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

[Support README](../../backend/support-service/README.md) có exact env namespaces và stop/drain/
switch/rollback: trước khi chạy Support phải dừng hai standalone writers/jobs; rollback dừng
Support trước rồi mới chạy hai explicit legacy services và đổi Gateway destinations. Giữ
current stores/queues/accepted writes, không restore snapshot cũ. Chưa thao tác cutover hoặc
khởi động lại Docker trên máy người dùng.

Đóng **H6-11.1**, hiện **39/48**. H6-11.2/.3 implementation và mock-infrastructure composition
đã đạt nhưng còn real-store/broker acceptance; H6-11.4 chưa đạt. Không tick runtime items từ
unit/web/mocked component tests hoặc từ việc compile container tests.

## 10. Integrated backend closeout — 2026-10-17

User giao trực tiếp hoàn thiện Billing/Notification liên quan và Support/shared integration.
HEAD `27b8832d25daee8218250224af3f8334318a37df` cộng working tree hiện tại. Không sửa
production Core/Clinical/Lab; các app thật được dùng qua public Gateway APIs trong disposable
acceptance. Không chờ Lộc viết export hoặc bổ sung code cho luồng Rx/Support đã nghiệm thu.

- Support import hai plain libraries, hai stores/Flyway/EMF/tx/pools, JWT/advice/listeners/jobs
  tách biệt và host default deny. Real PG/Rabbit/pool-exhaustion/fault/DLQ isolation và actual
  packaged upgrade/restart đều đạt. Default Compose sáu business apps, một Support discovery.
- Actual Gateway flow: đăng nhập/Core identity → Clinical check-in → EXAM payment/grant →
  khám/record → Rx create → Billing payment/current grant → authenticated dispense → Clinical
  attachment/Report/private notice. Pharmacy dừng trước payment rồi restart nhận durable grant;
  stock không tự giảm khi payment đến, 20 → 17 chỉ sau dispense; retries không nhân effect.
- Ledger một Rx charge/payment/current grant; Clinical một attachment, Report một filled
  journal và hai cash receipts tổng 130.000 VND, Notification một private filled notice.
  Wrong roles/other patient/refresh token/unsupported nested Pharmacy paths đều bị từ chối.
- Current-store rollback: stop Support → hai legacy launchers → nhận write mới → stop legacy
  → restart Support. Giữ original bytes, journal, notices mới/cũ và HELD outbox; không restore
  snapshot cũ, không dual writers. Chưa thay approved historical-data rehearsal của H6-12.3.
- Sửa Billing exact Pharmacy caller/current Rx allocation eligibility và original grant Instant
  để tránh DB rounding mismatch; Report scheduled refund poll dùng named read transaction;
  Gateway exact Pharmacy method/path/role matrix. Không đổi SQL migration bytes hoặc fixtures.

Verification selection **2.128 unique tests / 195 class, 0 fail/error/skip**: Notification
279, Report 1.331, Support 18, Billing selected 133, Pharmacy selected 26, Gateway 339,
packaged Support 1, six-app acceptance 1. Năm JSON contract checks đạt. Không gọi selected
Billing/Pharmacy là full module run; Report impacted rerun thay kết quả cũ, không cộng trùng.
Lab chỉ startup/discovery trong bài sáu-app; không claim paid Lab/FE/global M6 release.

Commands, raw artifact paths, actual patient/episode/record/Rx/payment/event IDs và original-byte
hashes nằm trong [runbook mục 8](../plans/huy-mvp-cutover-runbook.md#8-integrated-backend-closeout--2026-10-17).
Generated local XML/log/JSON evidence được giữ dưới `target/`, CI upload artifacts; không biến
test data thành lịch sử vận hành. Source harness SHA-256:
`749bc9b465b83cfa8da7beffc002feff7356b8deadf68dc234b059d4e33f520d`.

Đóng H6-06.2/.4, H6-11.2/.3/.4 và H6-12.1/.2: **46/48**. Chỉ còn **H6-02.3/H6-12.3**
với đầu vào/checklist mục 1. Historical completeness/coverage cần export/cursor/operator proof;
legacy-version rejected traffic vẫn giữ ở durable DLQ, không purge hoặc ký backlog đã drained.
Default activation/publication gates không được bật bởi lượt này. Không tạo thêm handoff.

## 11. H6-02.3 / H6-12.3 hoàn tất trên persisted local volumes — 2026-10-17

Người dùng yêu cầu thực hiện luôn hai task còn lại. Đã chọn đúng hai volume Compose có sẵn
`mediflow_mediflow-pgdata` và `mediflow_mediflow-rabbitdata`, xác nhận không có running source
writer, cold-copy bằng read-only mounts rồi chạy trên bản sao. Không dùng audit/test volume cũ
hoặc DB tạo mới để thay inventory. Hai Rabbit node identities cũ đều được boot đúng tên và
inventory riêng trước khi chọn một node cho bài diễn tập.

| Mục | Kết quả quan sát / nghiệm thu |
|---|---|
| Pharmacy nguồn | Năm bảng legacy pre-Flyway đều zero rows; các bảng outbox/reservation/receipt/job chưa tồn tại. Ghi ABSENT_IN_LEGACY_SCHEMA, không đoán zero từ query lỗi, không baseline/repair/migrate schema đó. Rows/digests trước và sau giữ nguyên. |
| Report và Notification nguồn | Cả hai chưa có public tables/Flyway history. Không có lịch sử nghiệp vụ để di chuyển ở hai stores này; original stores giữ nguyên. Standalone launchers initialize chính copied stores. |
| Broker nguồn | Hai nodes rabbit@95d905c52c13 và rabbit@fb86c4142a76: mỗi node vhost /, zero queues. Không có backlog/DLQ được purge hoặc tự xác nhận drained. |
| Chuyển đổi | Legacy pair → Support → broker restart → legacy rollback → Support return. Bốn API writes mới tồn tại, row SHA-256 giữ nguyên; PATIENT khác bị denied. Report read đạt, all Report rows và hai Flyway histories khớp qua các lần chuyển. |
| Broker retention | Một administrative durable probe queue giữ đúng một persistent message và bytes qua actual container restart; không là fabricated business event. |
| Bảo vệ nguồn | Original PG/Rabbit volume SHA-256 trước/sau trùng nhau. Source guard từ chối volume đang có writer đã chạy đạt. Clone containers dừng sau bài test, volumes/evidence giữ lại. |

Run `mediflow-h6-20261017211337-8ca8df`, hoàn tất 21:17:17 +07:00.
`HistoricalSupportCutoverIT`: **1 test / 1 class, 0 failure/error/skip**, Maven exit 0.
Tổng selection cùng mục 10: **2.129 unique tests / 196 class**, zero failure/error/skip;
không gọi đây là fresh full Billing/Pharmacy/backend suite. Running-source guard và năm JSON
checks ghi riêng. Lượt fail trước đó do IDE build stubs và Docker restart đổi ephemeral port
không tính passed; source snapshot build và port rediscovery đã xử lý, lượt cuối chạy lại đầy đủ.

H6-02.3 và H6-12.3 đóng: **48/48 cho môi trường phát triển hiện có**. Source thật rỗng là
kết quả inventory, không là lịch sử giả; không ký thay external coverage hoặc một production
dataset khác. Không bật default activation/publication gates hoặc thay schema volume gốc.
Schema Pharmacy legacy pre-Flyway vẫn là bản lưu nguyên trạng; bài này không chứng nhận chạy
nguyên schema cũ đó bằng artifact mới. Không còn task Huy chờ code hoặc handoff owner khác.

Reproduce từ repo root:

```powershell
& ./backend/support-service/operations/Invoke-HistoricalSupportRehearsal.ps1
```

Script từ chối source có running writer và existing output directory; không tự stop user apps.
Build dùng source snapshot byte-identical để tránh IDE ghi error stubs vào Maven outputs.
Hash/source IDs/raw artifact locations và đầy đủ giới hạn ở
[runbook mục 9](../plans/huy-mvp-cutover-runbook.md#9-local-persisted-volume-inventory-and-cutover--2026-10-17).
Giữ đúng một file handoff chung này; các mục 2–10 là lịch sử, không giao lại hai task vừa đóng.
