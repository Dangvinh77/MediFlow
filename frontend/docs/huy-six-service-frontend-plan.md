# Plan code FE Huy — chốt MVP 6 service

**Ngày lập:** 2026-10-18. **Owner thực thi:** Huy (`LQHuy0210`).  
**Source baseline:** `025ef39e358756a28043d9809fad165096528fbf` trên nhánh local `Huy`.  
**Trạng thái:** IN PROGRESS — 6/14 task, 39/56 subtask; checkpoint mới nhất ở mục 10.

Đây là plan thực thi hiện hành cho FE Pharmacy và Support (Report/Notification) trong MVP ngoại trú. Thay thứ tự
thực thi của [plan Huy cũ](huy-frontend-implementation-plan.md), giữ nguyên checklist và evidence
lịch sử trong file đó. Không dùng 11/30 task cũ hoặc 48/48 backend làm tiến độ plan FE mới.
Mỗi task dưới đây cần bốn subtask và điều kiện đóng; test local không thay nghiệm thu tích hợp.

## 1. Đích hoàn thành và phạm vi

Huy hoàn thiện Dược, Báo cáo và Thông báo trên frame đã duyệt, dùng dữ liệu thật qua Gateway trong hệ thống
Core, Clinical, Lab, Pharmacy, Billing, Support. Người dùng có thể kê đơn ngoại trú V1, xem
trạng thái thanh toán, hủy/xuất thuốc đúng quyền và xem báo cáo có coverage đã đối soát.
Giữ mẫu [huy-approved-ui.html](designs/huy-approved-ui.html), theme Sáng/Tối/Hệ thống và primitives
hiện có; không thiết kế lại hoặc thêm bộ UI/library.

| Deployment | Logical FE contexts | Trách nhiệm của Huy trong plan |
|---|---|---|
| Core | organization, patient | VERIFY-CONTRACT: identity và IDs thật dùng trong Dược |
| Clinical | appointment, medical-record | VERIFY-CONTRACT: nguồn record/episode ngoại trú; phối hợp owner khi cần entrypoint |
| Lab | lab | Kiểm tra khả năng cùng tồn tại của frame; không mở thêm backlog Lab |
| Pharmacy | pharmacy | IMPLEMENT feature và routes `/pharmacy/**` |
| Billing | billing | VERIFY-CONTRACT: thanh toán hóa đơn Rx từ workflow hiện có |
| Support | report, notification | IMPLEMENT Report; DELEGATED Notification và matching routes theo user 2026-10-18 |

6 deployments không đồng nghĩa 6 feature folders. Report/Notification và Organization/Patient vẫn
tách logical context; API/DTO không đổi tên theo host. Gateway/Eureka là hạ tầng.
Surgery, Inpatient, ADMISSION Rx, Report full V2 financial/operations/operator, refund UI và export
mới nằm ngoài plan. Giữ source/data hiện có và các gate mặc định đóng.

**Paths IMPLEMENT của Huy:**

- `frontend/src/features/pharmacy/**`, `frontend/src/app/(dashboard)/pharmacy/**`.
- `frontend/src/features/report/**`, `frontend/src/app/(dashboard)/reports/**`.
- `frontend/src/features/notification/**`, `frontend/src/app/(dashboard)/notifications/**`: task override của user; canonical owner vẫn Lộc.
- `frontend/src/lib/api.ts`, `frontend/src/lib/api.test.ts`: task override cho optional POST `Idempotency-Key`, giữ semantics transport hiện có.
- Plan này, các pointers trong plan cũ/workboard/service guides và evidence đúng context.

User trả lời câu hỏi giao `api.ts`/`api.test.ts`: **“làm cho cả pharma và toàn bộ support luôn đi”**.
Trong task này Huy thực hiện HF6-02 và Notification, không chờ handoff cho Lộc/Vinh.
Quyền này không đổi canonical ownership và không mở thêm shared shell/config/auth hoặc feature khác.
Mọi phụ thuộc tiếp tục gom vào [HANDOFF-HUY-MVP.md](../../docs/handoffs/HANDOFF-HUY-MVP.md),
không tạo handoff mới. Không tạo raw fetch hoặc cross-feature imports để vượt dependency.

## 2. Phần kế thừa, không code lại

| Đã có ở baseline | Cách sử dụng trong plan mới |
|---|---|
| Frame sáu nhóm, sidebar/header và theme | Kế thừa; HF6-10 kiểm tra lại trên các màn thay đổi |
| P-01/02/03/04/05/07 hoàn tất FE local | Giữ catalog, thuốc/stock, lookup/detail, V0 compatibility và outbox; thêm V1 có kiểm soát |
| P-06 hoàn tất 4/4 subtask local | Giữ confirmation/focus/race guards; HF6-06 bổ sung signed-role acceptance |
| R-01…R-05 hoàn tất FE local | Giữ ba báo cáo cũ và validation/request lifecycle; không thay bằng full V2 |
| R-MVP-01 hoàn tất 4/4 FE local | Giữ UI/API ngoại trú đã có; HF6-08/09 chốt contract, runtime và coverage |
| Gateway exact Pharmacy cancel/dispense roles đã có | Không tiếp tục giao sửa generic PUT như ghi chú lịch sử; kiểm tra live tại HF6-06 |
| Support + backend EXAM/Rx acceptance đã có | Tái sử dụng setup/runbook; chưa coi là browser FE acceptance |

Kiểm tra FE thực hiện trong lượt audit 2026-10-18 tại baseline: Pharmacy/Report **235/235 tests,
22 files**, `pnpm typecheck` và `pnpm lint` PASS. Không chạy lại build/browser trong lượt audit đó.
Full-suite/build/browser fixture lịch sử nằm ở plan cũ/workboard; cần kiểm tra lại candidate cuối.
Các số này không cộng vào 48 subtask mới.

## 3. Source contract và dependency đã khóa để lập plan

Đọc lại controllers/DTO/security/tests khi bắt đầu slice; source mới hơn có thể làm thay đổi contract.

- Pharmacy: `backend/pharmacy-service/src/main/java/com/mediflow/pharmacy/` với
  `web/PrescriptionController.java`, `application/dto/request/CreatePrescriptionRequest.java`,
  `PrescriptionLineRequest.java` và `application/dto/response/PrescriptionDTO.java`.
- Report: `backend/report-service/src/main/java/com/mediflow/report/` với
  `web/OutpatientReportController.java`, `application/dto/response/OutpatientReportDTO.java`,
  read service và controller tests. Gateway: `backend/gateway/.../filter/RouteAuthorizationFilter.java`.
- Shared transport: `frontend/src/lib/api.ts`; `post(path, data, { idempotencyKey })` gửi header qua wrapper hiện có.
- Notification: `NotificationController`, `SendNotificationRequest`, `NotificationDTO`, `Notification.create`
  và controller tests trong `backend/notification-service`; public API giữ logical context khi chạy Support.
- Runtime: `backend/pharmacy-service/src/main/resources/application-mvp-outpatient.yml`,
  `backend/support-service/src/main/resources/application.yml`, Compose và
  [runbook backend Huy](../../docs/plans/huy-mvp-cutover-runbook.md).

**V1 Rx request:** các field cũ giữ exact wire; bổ sung `careContractVersion: 1`,
`careContext: "OUTPATIENT"`, `careEpisodeType: "OUTPATIENT_VISIT"`, `careEpisodeId`,
`admissionId: null`, `priceCode`. DTO V1 tổng quát cho phép `recordId: null`, nhưng
`PublicOutpatientPrescriptionCreationService` bắt buộc exact `recordId` cho API ngoại trú công khai;
form phải yêu cầu hồ sơ. Không suy ra episode từ
patient/record/account. `priceCode` là Billing catalog key được xác nhận; backend quyết định giá.
POST yêu cầu header canonical UUID `Idempotency-Key`. Profile `mvp-outpatient`/`outpatient-mvp`
từ chối V0; không tự fallback V0 khi V1 lỗi. Response `PrescriptionDTO` hiện không có metadata
care version/context/episode/priceCode; không tự thêm vào kiểu response hoặc dựng nhãn từ nullability.

**Report ngoại trú:** daily dùng `fromDate`, `toDate`, optional `departmentId`; monthly dùng `year`
và optional department, trả kỳ cả năm. DTO có coverage, currency, zone, source/reconciliation times
và bảy metrics mỗi period. `netCash`/totals đọc từ response; không tự tổng hợp authoritative values.
Coverage thiếu/stale/unaccepted là unavailable, không thành report số 0 hoặc fallback legacy.

**Dependency hiện tại:** header transport đã được user giao và triển khai; source IDs/catalog/signed staff cần
evidence môi trường; Report cần runtime flags và coverage phù hợp môi trường. Backend đã có code và
acceptance local, vẫn cần xác nhận cấu hình của lần chạy FE. Không ghi lại các dependency này
thành “backend chưa implement”.

## 4. Checklist thực thi — 14 task / 56 subtask

HF6-01…12 giữ nguyên 48 tiêu chí gốc. HF6-13/14 thêm tám tiêu chí cho Notification theo user
2026-10-18, không reset tiến độ hoặc gộp vào 48/48 backend. Đóng task khi đủ bốn subtask.

### HF6-01 — Refresh hợp đồng và chốt cách chạy MVP

Owner Huy; READY. Liên hệ HFE-02/P-08/Q-02 cũ.

- [x] HF6-01.1 Ghi commit candidate, diff, route/method/role/error matrix và fixtures request/response từ source hiện tại.
- [ ] HF6-01.2 Khóa nguồn `recordId`, `careEpisodeId`, patient/doctor/department và `priceCode`; ghi cách lấy từ public workflow đã có, không tự sinh authority IDs.
- [x] HF6-01.3 Chốt cách FE chọn mode ngoại trú từ cấu hình runtime được xác nhận; nếu cần env mới, ghi exact name/path/semantics và scope trước khi code; không đoán mode từ DTO hoặc lỗi API.
- [x] HF6-01.4 Ghi dependency/owner/acceptance trong handoff chung; phân biệt gap FE hiện tại với backend đã nghiệm thu và giữ scope deferred.

Đóng khi mọi field, mode và nguồn dữ liệu cho create/read/cancel/dispense/report có căn cứ;
nếu chưa có entrypoint Clinical thì chốt cách nhập IDs thật được kiểm chứng, không dựng lookup nội bộ.

### HF6-02 — Shared transport hỗ trợ Idempotency-Key

Canonical shared owner Vinh; user DELEGATED Huy trong lượt này. Bounded paths:
`frontend/src/lib/api.ts` và `frontend/src/lib/api.test.ts`; deliverable optional POST key,
không thêm domain endpoints, raw fetch trong feature hoặc tùy chọn ghi đè Authorization.

- [x] HF6-02.1 Có public POST API nhận options/header theo convention shared owner, tương thích mọi caller hiện có.
- [x] HF6-02.2 Header `Idempotency-Key` được gửi nguyên qua `/api/*`; bearer/envelope/correlation/error semantics hiện tại được giữ.
- [x] HF6-02.3 Kiểm thử POST có/không body, header tùy chọn, 401/403/error và các caller cũ; không cho caller vô tình ghi đè Authorization bằng input untrusted.
- [x] HF6-02.4 Huy xác nhận Pharmacy dùng API public mới; ghi evidence shared dependency đã đạt, không thêm fetch riêng.

Đóng khi thay đổi shared đã có trên candidate và contract tests chứng minh khả năng truyền header.
Task-scoped scope được ghi ở mục 1; các shared paths khác giữ ownership cũ.

### HF6-03 — DTO, API và validation kê đơn ngoại trú V1

Owner Huy; phụ thuộc HF6-01, transport integration sau HF6-02. Paths Pharmacy types/api/validation/tests.

- [x] HF6-03.1 Tách request V0 compatibility và OUTPATIENT V1 theo exact discriminator, nullability và enums; không thêm V1 metadata vào read DTO.
- [x] HF6-03.2 API create V1 nhận command key riêng header và serialize đúng body; không gửi giá, command key trong JSON hoặc fields ADMISSION không thuộc MVP.
- [x] HF6-03.3 Validation canonical UUID/ngày/lines/dosage/priceCode theo DTO; OUTPATIENT_VISIT và admission null; dữ liệu thiếu không được chọn record/episode gần nhất.
- [x] HF6-03.4 Contract tests kiểm path, body/header, nullable record, invalid context và V0 compatibility; lỗi V1 không gọi lại V0.

Đóng khi facade/validation đúng source và test đạt; chưa cần mở control production để hoàn thành slice local.

### HF6-04 — Form tạo đơn V1 và retry cùng ý định

Owner Huy; phụ thuộc HF6-01/02/03. Paths Pharmacy create form/line editor/route và tests.

- [x] HF6-04.1 Dùng form/theme đã có, thêm dữ liệu nguồn ngoại trú rõ ràng; IDs/catalog từ nguồn đã chốt, không lấy doctorId từ accountId.
- [x] HF6-04.2 Tạo một canonical UUID cho mỗi ý định tạo đơn, khóa submit đồng bộ; retry payload không đổi giữ key, ý định mới dùng key mới.
- [x] HF6-04.3 Quản lý draft/submitted/unknown outcome: giữ nguyên snapshot/key cho retry chủ động, thay payload phải là ý định mới; không auto-retry mutation hoặc lưu token/PII vào localStorage.
- [x] HF6-04.4 Kiểm thử success dùng prescriptionId server, duplicate click, replay, changed intent, lỗi authority/validation/401/403/conflict/timeout và unmount; profile MVP không render V0 như phương án thay thế.

Đóng khi tạo V1 qua transport thật đã nối và hành vi retry được kiểm chứng; browser signed-role nằm HF6-11.
V0 chỉ giữ compatibility nơi mode legacy được xác nhận, không xuất hiện trong đường tạo đơn MVP.

### HF6-05 — Detail và trạng thái chờ thanh toán

Owner Huy; sau HF6-03. Tái sử dụng lookup/detail đang có; phối hợp Billing read-only.

- [x] HF6-05.1 Lookup/deep link bằng prescriptionId thật; render lifecycle, dispense và paymentConfirmed độc lập, không tự đặt metadata V1.
- [x] HF6-05.2 Chưa xác nhận thanh toán thì không cho submit xuất thuốc; có thao tác tải lại chủ động, không polling vô hạn hoặc tự xác nhận tiền từ UI Billing.
- [x] HF6-05.3 Giá, tên thuốc, lineTotal/totalAmount lấy Pharmacy response; không gọi sibling feature hoặc suy ra invoice/charge/grant IDs.
- [x] HF6-05.4 Kiểm thử pending→paid bằng fresh GET, stale response, 404/403, expired/terminal và lỗi refresh; chốt link Billing chỉ khi public route/query được owner xác nhận.

Đóng khi read workspace phản ánh đúng dữ liệu mới và không tự tạo financial proof; không bắt buộc
thêm Billing entrypoint nếu chưa có contract, có thể chuyển qua workflow Billing hiện có.

### HF6-06 — Hủy và xuất thuốc qua Gateway bằng role thật

Owner Huy; sau HF6-04/05 và runtime được xác nhận. Đóng phần còn lại P-06.

- [x] HF6-06.1 Recheck ADMIN/DOCTOR cancel, ADMIN/PHARMACIST dispense; unknown/null role và đường không allowlist bị deny, backend vẫn quyết định resource authority.
- [ ] HF6-06.2 Browser/API thực với signed users: bác sĩ được phép tạo/hủy đúng quan hệ, bác sĩ khác không có authority bị deny; PHARMACIST không hủy, DOCTOR không xuất.
- [ ] HF6-06.3 Xuất trước thanh toán bị chặn; sau thanh toán chỉ một dispense effect và tồn kho đúng; paid không tự xuất thuốc, terminal retry không tạo effect mới.
- [ ] HF6-06.4 Giữ reason/releasedReservations, confirmation/focus/Escape, conflict/unknown outcome→fresh read; kiểm thử bằng role thật và lưu HTTP/correlation/effect evidence đã bỏ thông tin nhạy cảm.

Đóng khi cả allowed/denied scenarios qua Gateway thật và hiệu ứng nghiệp vụ đúng; không dùng
fixture token hoặc đổi role để bypass. Không chỉnh backend chỉ để UI test xanh.

### HF6-07 — Regression danh mục, stock và outbox

Owner Huy; có thể làm độc lập sau HF6-01. Kế thừa P-01/02/03/07, không dựng lại màn hình.

- [x] HF6-07.1 Kiểm catalog/search/page/size/deep links, stale request, empty/404 và bảng đúng mẫu trên API hiện tại.
- [x] HF6-07.2 Kiểm ADMIN/PHARMACIST tạo thuốc/stock delta, DOCTOR chỉ đọc; validation/focus, khóa gửi trùng và dùng số tồn server.
- [x] HF6-07.3 Kiểm outbox ADMIN-only, known event UUID, bodyless replay và replayed false/true; không dựng list API hoặc tự replay khi mount.
- [ ] HF6-07.4 Chạy regression component/API và smoke có signed session cho các flow ảnh hưởng; replay thật chỉ dùng event test có nguồn gốc và effect được xác định.

Đóng khi code cũ hoạt động trong topology mới và không regress sau bổ sung V1.

### HF6-08 — Khóa Report legacy và ngoại trú với backend mới

Owner Huy; độc lập với Rx header. Kế thừa R-01…05/R-MVP-01.

- [x] HF6-08.1 Đối chiếu DTO/query/error/roles của năm read endpoints đang dùng; legacy tháng/năm khác outpatient whole-year, không gộp APIs.
- [x] HF6-08.2 Giữ URL/tab/draft/submitted scope/Back/Forward, 401/403/correlation/retry và stale/unmount guard ở mọi view.
- [x] HF6-08.3 Kiểm currency/zone/sourceObservedAt/reconciledAt/coverageId/covered và period metrics từ response; legacy không tự gắn currency chưa có contract.
- [x] HF6-08.4 Tests accepted zero, thiếu/stale coverage, netCash âm, monthly 12 kỳ, range/UUID/limit và lỗi sau success; không tính lại netCash hoặc fallback legacy.

Đóng khi local Report regressions đạt và đã xác nhận contract hiện tại; runtime acceptance tách HF6-09.

### HF6-09 — Nghiệm thu Report trên Support và mở UI đúng môi trường

Owner Huy/Report phối hợp runtime operator; sau HF6-08 và source/coverage được nghiệm thu.

- [x] HF6-09.1 Xác nhận Gateway routes tới Support đang chạy với hai stores tách biệt; profile ngoại trú và các flags consumer/reader cần thiết có owner evidence.
- [ ] HF6-09.2 Tạo dữ liệu test qua public flows, có coverage được backend chấp nhận đúng period/department/currency/zone; không ghi SQL hoặc tự chế coverage từ browser.
- [ ] HF6-09.3 Signed ADMIN/MANAGER đọc ngày/tháng thành công, role không được phép bị deny; missing coverage vẫn unavailable, periods/metrics khớp backend evidence.
- [ ] HF6-09.4 Kiểm server env `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED` OFF không mount/query, ON sau restart mới hiện flow; kiểm tắt lại UI và backend flags giữ trạng thái đã thỏa thuận.

Đóng khi chính browser đi qua Gateway→Support thật; evidence packaged backend hoặc browser mocks
không thay điều kiện này. Chỉ bật cấu hình trong môi trường nghiệm thu đã được giao, không tự rollout production.

### HF6-10 — Đồng nhất giao diện và khả năng sử dụng

Owner Huy trong Pharmacy/Report/Notification; sau HF6-04/05/08/13, tận dụng shared frame đã có.

- [x] HF6-10.1 Đối chiếu màn thay đổi với mẫu đã duyệt: title/nav/panel/table/lookup, semantic tokens và shared primitives; không mang fixture/BẢN MẪU vào production.
- [x] HF6-10.2 Browser Sáng/Tối/Hệ thống tại desktop, tablet và mobile 320–360px; chỉ bảng cuộn ngang, UUID/tên dài vẫn đọc được.
- [ ] HF6-10.3 Kiểm bàn phím/tab order/label/focus lỗi/Escape/return-focus/status text/loading/empty/error/permission trên flow V1 và Report.
- [x] HF6-10.4 Lưu screenshot/evidence theo route/theme/viewport; gap shared drawer/header ghi trong handoff chung, sửa đúng owner scope trước khi đóng.

Đóng khi các màn thuộc Huy dùng được trên thiết bị/theme đã cam kết; không lấy việc Surgery chưa
có UI làm blocker cho theme MVP như HFE-01 lịch sử.

### HF6-11 — Browser workflow ngoại trú trên hệ thống 6 service

Huy chịu trách nhiệm đoạn Pharmacy/Report; owners khác hỗ trợ setup/contract. Sau HF6-06/07/09/10.

- [x] HF6-11.1 Dùng môi trường test có PostgreSQL/RabbitMQ, sáu business apps + Gateway/Eureka; ghi commit/profile/flags/health/routing và tài khoản role hợp lệ, không ghi secrets.
- [ ] HF6-11.2 Workflow thật: nguồn khám ngoại trú đủ điều kiện → FE tạo Rx V1 → Billing payment qua surface hiện có → Pharmacy refresh/dispense → Report sau đối soát; IDs từ response thật.
- [ ] HF6-11.3 Kiểm chưa thanh toán, source không hợp lệ, role denial, session hết hạn, API unavailable và retry; không tạo trùng Rx/dispense, không tự fallback V0/legacy report.
- [ ] HF6-11.4 Kiểm Notification intake trong workflow được nghiệm thu: private notice đúng người theo public API, người khác bị deny; Notification FE thuộc scope user giao, nghiệm thu riêng tại HF6-14.

Đóng khi acceptance Pharmacy/Report qua browser thật đạt. Không yêu cầu viết lại Clinical/Billing/Lab
FE để làm test: dùng public flow/API đã có cho setup hoặc đoạn chưa có UI, ghi rõ đoạn nào chạy bằng
browser và đoạn nào bằng API. Không gọi đó là all-browser E2E nếu còn bước API.

### HF6-12 — Regression cuối, evidence và chốt release FE

Owner Huy; sau HF6-01…11. Không đồng nghĩa chốt FE toàn nhóm hay global M6.

- [x] HF6-12.1 Candidate cuối đạt focused Pharmacy/Report tests, full FE suite, typecheck, lint và production build; ghi counts/failures/skips, commands và commit thực chạy.
- [x] HF6-12.2 Audit diff/contract: gateway-only/lib-api-only, không sibling imports, giá/authority giả, secrets/PII/fixture trong production; source chỉ đúng quyền được giao.
- [x] HF6-12.3 Cập nhật plan/workboard/guides và handoff chung theo evidence; migration/runtime/backend không được sửa ngoài scope để đóng FE.
- [ ] HF6-12.4 Ghi setup/demo/rollback UI và limitations; signed-role browser PASS trên candidate cuối, release verdict rõ phần Huy; chỉ đóng handoff khi cả hai phía đạt acceptance.

Đóng release Pharmacy/Support khi các task có evidence truy vết được và không còn dependency bắt buộc của MVP.
Nếu Docker/runtime chưa sẵn sàng, tiếp tục phần local và ghi BLOCKED đúng test; không tick giả để đủ số.

### HF6-13 — Hoàn thiện Notification FE theo public contract Support

Huy DELEGATED; canonical owner Lộc. Local implementation/contract tests, không đồng nghĩa delivery thật.

- [x] HF6-13.1 Khóa DTO/roles/privacy và logical `/v1/notifications` paths; không đổi folder/path theo Support host, không thêm read-state/retry API chưa tồn tại.
- [x] HF6-13.2 Lookup UUID/pagination theo submitted patient, latest response, empty/error/401/403/correlation/retry; NURSE list-only, ADMIN/PATIENT mở detail theo quyền server.
- [x] HF6-13.3 ADMIN send theo backend channel/address/length rules, field errors/focus, khóa gửi trùng; unknown outcome không resend vì endpoint chưa có idempotency contract.
- [x] HF6-13.4 Detail đổi ID xóa dữ liệu cũ; draft/results/callback reset khi đổi session hoặc unmount; tests roles/privacy/stale responses/pagination/send và semantic theme tokens.

### HF6-14 — Nghiệm thu Notification trong Support thật

Huy thực hiện cùng môi trường test; không tự gửi email/SMS tới người thật để đóng task.

- [ ] HF6-14.1 Browser qua Gateway→Support có signed ADMIN/NURSE/PATIENT; đọc đúng người, cross-patient deny, NURSE không detail/send; không coi mock role là proof.
- [ ] HF6-14.2 Manual IN_APP gửi một effect, lịch sử/detail khớp response; EMAIL/SMS chỉ nghiệm thu với provider/test recipient được cấp, ghi giới hạn delivery rõ.
- [ ] HF6-14.3 Lifecycle/payment/refund notice hiển thị đúng từ backend intake, giữ privacy; missing patient identity claim không fallback accountId hoặc tự orchestration từ FE.
- [ ] HF6-14.4 Browser Light/Dark/System desktop/mobile, keyboard/error/focus/retry, ảnh/evidence candidate và release verdict Support; không đưa secrets/PII vào repo.

## 5. Thứ tự triển khai và điều kiện chuyển bước

| Đợt | Tasks | Kết quả phải có |
|---|---|---|
| A — hợp đồng | HF6-01; phối hợp HF6-02 | Nguồn IDs/catalog/mode rõ, shared header có contract |
| B — kê đơn | HF6-03 → HF6-04 → HF6-05 | Tạo V1, giữ key/payload khi retry và detail đúng trạng thái |
| C — công việc độc lập | HF6-07; HF6-08; phần local HF6-10 | Giữ workflow cũ và Report, xử lý UI mà không chờ header |
| D — tích hợp thật | HF6-06; HF6-09 → HF6-11 | Quyền/thanh toán/xuất/coverage qua Gateway thật |
| E — đóng gói | HF6-12 | Candidate build/test/browser evidence, cập nhật docs và release verdict |

Có thể chuyển giữa các việc độc lập trong cùng lượt; thứ tự này không yêu cầu spawn agents.
Không cần Docker để làm DTO/validation/form/component tests. PostgreSQL/RabbitMQ/runtime thật
cần cho các bước integration; các test bị skip không tính PASS.

## 6. Lệnh kiểm tra và cách ghi tiến độ

Từ `frontend/`, dùng scripts/dependencies sẵn có:

```powershell
pnpm exec vitest run src/features/pharmacy src/features/report --maxWorkers=2
pnpm test -- --maxWorkers=2
pnpm typecheck
pnpm lint
pnpm build
```

Chỉ chạy focused checks trong từng slice; chạy full suite/build trên candidate cuối hoặc khi
thay đổi có ảnh hưởng rộng. Tách full-suite failures foreign/flaky khỏi kết luận task Huy;
không sửa tests/config của owner khác hoặc giảm assertions để có kết quả xanh.

Evidence mỗi task gồm ngày, commit/diff, scope, test commands/counts, browser thật hay mock,
HTTP/correlation/effect đã loại secrets/PII, dependency còn mở. Artifact thô dùng output local
không commit dữ liệu nhạy cảm; các kết luận lâu dài ghi ngay trong plan/handoff chung.

| Checkpoint | Task / subtask đóng của plan mới | Verification | Dependency còn lại |
|---|---|---|---|
| 2026-10-18 — lập plan | 0/12; 0/48 | Audit baseline: focused 235/235, typecheck/lint PASS; không có production diff mới | Shared header, source/mode xác nhận và browser runtime acceptance |

Không cộng lại task baseline như thành quả mới. Bảng kế thừa mô tả phần đã có; checklist HF6 đo
phần còn lại và acceptance cần bổ sung để chốt FE Huy cho topology sáu service.

## 7. Checkpoint triển khai — 2026-10-18

Checkpoint foundation trước khi user mở rộng scope; trạng thái mới nhất tại mục 8.

Candidate: baseline `025ef39e` + working diff; chưa commit/push. Production changes chỉ trong
Pharmacy feature. Shared API, routes/runtime/backend và các feature khác chưa sửa.

**HF6-03 IN PROGRESS (2/4):** thêm `CreateOutpatientPrescriptionRequest` riêng request V0;
read DTO giữ nguyên. `outpatientPrescriptionForm.ts` dùng lại line/date/UUID validation hiện có,
yêu cầu exact record theo public use case, episode được cung cấp rõ và priceCode không có default.
Serializer chỉ gửi wire fields OUTPATIENT, không gửi giá/command ID/admission UUID từ browser.
Không mở ADMISSION hoặc fallback V0. Transport integration và API header tests còn mở HF6-02/03.2/.4.

`outpatientPrescriptionIntent.ts` chuẩn bị submitted snapshot bất biến cùng canonical UUID:
payload giống giữ key khi retry chủ động; payload thay đổi cần key mới; draft mutation không sửa
bản đã gửi. Đây là helper chuẩn bị cho HF6-04, chưa tính đã hoàn tất form UI hoặc dispatch qua Gateway.
Caller tương lai phải xóa intent khi đổi actor, thành công, bắt đầu lệnh mới hoặc unmount;
không lưu intents/PII vào localStorage và không tự resend khi chưa rõ kết quả.

**HF6-08 DONE — local contract/regression (4/4):** đã đọc lại `ReportController`,
`OutpatientReportController`, bốn response DTO và `OutpatientReportReadService`, so với FE API,
types, workspace/request/result và test suite. Legacy daily/monthly/top dùng ADMIN/MANAGER,
query giữ source; outpatient daily range/monthly year riêng. Reader chỉ trả accepted coverage,
thiếu/stale/history mismatch là unavailable. FE giữ server metrics/currency/zone/provenance,
không thay Report production code chỉ để có task mới. HF6-09 runtime acceptance vẫn OPEN.

Verification đã đạt trên diff này:

- Focused validation/intent + legacy validation: **47/47 tests, 3 files**; 33 tests mới.
- Pharmacy/Report: **268/268 tests, 24 files**; không failures/skips.
- Full FE suite: **396/396 tests, 47 files**, không failures/skips. Có jsdom warning
  `Not implemented: navigation to another Document` từ suite hiện có; exit 0.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: PASS; chưa có authenticated browser/Gateway acceptance mới.

Tiến độ mới **1/12 task, 6/48 subtask** không cộng lại baseline UI. Việc tiếp theo là nối
shared header khi có owner/scope, hoàn tất HF6-03/04; các bước runtime giữ nguyên điều kiện đóng.

## 8. Pharmacy + toàn bộ Support — candidate local 2026-10-18

**Authority:** user trả lời câu hỏi về `frontend/src/lib/api.ts`/`api.test.ts` và giao tiếp
“làm cho cả pharma và toàn bộ support luôn đi”. Huy triển khai Pharmacy/Report theo owned scope,
Notification theo task override, shared API chỉ cho optional POST key. Không sửa shell/auth/config,
foreign features, backend hoặc bật deferred contexts. Candidate: `025ef39e` + working diff, chưa commit/push.

**Tiến độ:** HF6-02/03/04/05/08/13 đóng local (6 task); **35/56 subtask**. Riêng 48 tiêu chí
Pharmacy/Report ban đầu: **5/12 task, 31/48 subtask**; Notification bổ sung 4/8. Đây là tiến độ
implementation/contract và regression theo checklist, không phải 35 tính năng mới hay release 100%.

| Task | Đạt | Evidence / phần còn mở |
|---|---:|---|
| HF6-01 | 3/4 | Matrix dưới đây, runtime mode và single handoff đã chốt; IDs/catalog thật của môi trường chưa được cấp/xác nhận |
| HF6-02 | 4/4 | Shared `PostOptions.idempotencyKey`, bearer/JSON/envelope/error/correlation/bodyless compatibility tests; Pharmacy dùng wrapper |
| HF6-03 | 4/4 | DTO/validation/serializer riêng V1; frozen intent qua header, API error không fallback V0 |
| HF6-04 | 4/4 | Form V1 yêu cầu episode/priceCode, synchronous lock, exact retry, new intent, actor/unmount guard; dynamic mode route tests |
| HF6-05 | 4/4 | Explicit fresh GET pending→paid, server amounts, 403/404/terminal/failed refresh tests; không suy invoice hoặc thêm Billing link |
| HF6-06 | 1/4 | Roles/controller/Gateway và local terminal tests khớp; signed actors và effects thật chưa nghiệm thu browser |
| HF6-07 | 3/4 | Catalog/stock/outbox local regression giữ nguyên; signed-session smoke còn mở |
| HF6-08 | 4/4 | Report contract/local regressions đã refresh ở mục 7; không viết lại reports đang hoạt động |
| HF6-09 | 0/4 | Gateway→Support với đúng profiles/flags/coverage và browser roles thật chưa chạy |
| HF6-10 | 1/4 | Form/panel/table/status dùng semantic tokens và frame/primitives đã duyệt; browser theme/mobile/keyboard mới chưa chạy |
| HF6-11 | 0/4 | Chưa runtime/browser workflow sáu apps trên candidate FE này |
| HF6-12 | 3/4 | Tests/checks và audit scope/docs local; release verdict thật còn mở |
| HF6-13 | 4/4 | Notification API/validation/lookup/detail/send/session/roles/privacy tests |
| HF6-14 | 0/4 | Signed Notification/IN_APP/lifecycle/provider và browser acceptance thật còn mở |

### Public contract matrix

Paths là HTTP qua same-origin Gateway; facade truyền phần `/v1/...` vào wrapper tự thêm `/api`.
UI permissions phản ánh controller/Gateway, còn resource ownership và financial/source authority do server quyết định.

| Surface | HTTP resource | Roles browser | Contract/error cần giữ |
|---|---|---|---|
| Dược catalog/detail | GET `/api/v1/pharmacy/drugs`, `/{id}` | ADMIN/DOCTOR/PHARMACIST | page/size/keyword, empty/404; lấy stock/price từ server |
| Thêm thuốc / stock | POST drugs; PUT drugs `/{id}/stock` | ADMIN/PHARMACIST | body đúng DTO, stock delta, validation/conflict/fresh read |
| Tạo Rx | POST `/api/v1/pharmacy/prescriptions` | ADMIN/DOCTOR | V1 OUTPATIENT + canonical header key, signed staff/source proof; 401/403/409/5xx; không downgrade |
| Đọc Rx | GET prescriptions `/{id}` | ADMIN/DOCTOR/PHARMACIST | paymentConfirmed độc lập status/dispenseStatus, nullable read record; 404/403/failed refresh |
| Hủy Rx | PUT prescriptions `/{id}/cancel` | ADMIN/DOCTOR | reason, result releasedReservations, actor/resource authority; conflict→fresh GET |
| Xuất Rx | PUT prescriptions `/{id}/dispense` | ADMIN/PHARMACIST | không body; payment server, terminal result; không auto retry mutation |
| Replay known event | POST `/api/v1/pharmacy/admin/outbox/{id}/replay` | ADMIN | không body; UUID đã biết, replayed false/true; không invent list |
| Report legacy | GET `/api/v1/reports/daily`, `/monthly`, `/top-medicines` | ADMIN/MANAGER | query/DTO riêng, server metrics, zero hợp lệ; 401/403/correlation |
| Report outpatient | GET `/api/v1/reports/outpatient/daily`, `/monthly` | ADMIN/MANAGER | range hoặc year, accepted coverage/currency/zone; unavailable không thành zero/fallback |
| Notification history | GET `/api/v1/notifications/patient/{patientId}` | ADMIN/NURSE/PATIENT | page/size, signed patient ownership, 401/403/correlation, submitted scope |
| Notification detail | GET `/api/v1/notifications/{id}` | ADMIN/PATIENT | exact public fields; không recipientAddress/retryCount; 403/404/5xx |
| Notification send | POST `/api/v1/notifications/send` | ADMIN | backend còn SYSTEM cho service, browser không nhận role này; channel/address validation, không idempotency/retry API |

Request/response test fixtures nằm cạnh production: `pharmacy/api.test.ts`,
`components/prescription/{outpatientPrescriptionForm,CreateOutpatientPrescriptionForm,PrescriptionDetail}.test.*`,
`outpatientPrescriptionIntent.test.ts`, `notification/{api,sendNotificationValidation}.test.ts`,
`notification/components/NotificationWorkspace.test.tsx` và Report suites hiện có. Chúng là fixture unit/component,
không phải IDs/token/coverage để dùng trong runtime. Wire authority vẫn là backend DTO/controller source.

### Runtime mode và nguồn dữ liệu

- Route dynamic `/pharmacy/prescriptions/new` đọc server env `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE`:
  unset hoặc `outpatient` dùng V1; đúng `legacy` dùng V0 compatibility; giá trị khác đóng creation.
  Cấu hình trong server environment rồi restart FE; không cần sửa shared config hoặc bật backend flags.
- V1 có exact record và episode riêng, patient/staff/department riêng, priceCode do catalog xác nhận.
  Drug IDs từ catalog public có sẵn; các authority IDs và priceCode phải được cung cấp rõ từ source
  workflow của môi trường, không random/default hoặc suy từ account/record/appointment.
  Chưa có public Clinical entrypoint cấp toàn bộ values rõ ràng; HF6-01.2 vẫn OPEN.
- Read `PrescriptionDTO` không có care metadata; frontend không tự thêm hoặc suy diễn.
- Report gate `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED` tiếp tục OFF mặc định; ON chỉ sau actual
  reader/coverage acceptance. Notification dùng logical endpoint cũ trên Support, không cần FE host selector.
- Cổng local 3000/8080 không có listener trong lượt kiểm tra này; chưa chạy signed-browser acceptance.
  Không dùng backend 48/48 hoặc mock unit tests thay proof runtime của FE.

### Verification

Focused Pharmacy/Report/Notification/transport/route batch trước sửa focus cuối: **341/341 tests,
30 files**; bốn cases V1/detail thêm sau đó và case keyboard focus Notification được chạy trong
full suite cuối. Notification component follow-up: **30/30**.

Final candidate sau sửa focus:

- `pnpm test -- --maxWorkers=2`: **474/474 tests, 53 files**, zero failures/skips, exit 0.
  Có warning jsdom có sẵn `Not implemented: navigation to another Document`; không coi đây là browser test.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: **PASS**, exit 0. Build xác nhận route kê đơn dynamic.
- `git diff --check`: PASS; chưa signed-browser/Gateway acceptance mới và chưa commit/push.

Full suite tăng từ foundation checkpoint 396 lên 474 tests; so với baseline 363 tăng 111.
Không cộng dồn các lượt rerun/focused vào tổng unique tests.

Audit: production changes chỉ trong paths được giao; feature vẫn chỉ dùng `lib/api`, không raw fetch,
sibling-feature imports, direct service ports, client-derived money/authority hoặc dữ liệu giả trong UI.
Backend không đổi; một handoff Huy tiếp tục chứa toàn bộ dependency thật.

## 9. Đợt 10 đầu việc liên tục — 2026-10-18

User giao “chạy liên tục 10 task”. Đây là **10 đầu việc triển khai/verification có phạm vi nhỏ**
trong các HF6 còn mở, không phải đóng thêm 10 task cấp cao của checklist 14 task. Candidate vẫn
`025ef39e` + working diff chưa commit/push. Không đổi backend source hoặc shared auth/transport semantics.

| ID | Hoàn thành | Evidence |
|---|---|---|
| B10-01 | Pharmacy catalog/lookup remount khi đổi tài khoản, kể cả cùng role | Session boundary, request scope, lifecycle tests |
| B10-02 | Rx detail/cancel/dispense bỏ callback của phiên cũ | Session/capability recheck trước dispatch và sau response |
| B10-03 | Drug detail và stock draft gắn đúng drugId/session | Keyed content; logout/late-read regression |
| B10-04 | Tạo thuốc khóa sau success hoặc kết quả chưa rõ | Network/503 reconciliation, không gửi lại lệnh không idempotent |
| B10-05 | Stock network/5xx tải lại tồn và xóa delta cũ | Hai HTTP uncertainty cases, không tự mutation retry |
| B10-06 | Outbox replay không giữ draft/result/callback của actor cũ | Lifecycle test, live ADMIN capability guard |
| B10-07 | Ba Report legacy đổi session xóa dữ liệu; invalid submit focus và hủy pending result | Daily/monthly/top invalid-pending regressions |
| B10-08 | Report ngoại trú cùng lifecycle; invalid filter không hiện response cũ | Allowed/denied views, unmount-before-microtask, focus regression |
| B10-09 | Browser themes/responsive/focus; sửa overflow drug detail | Nine-case matrix, screenshots, 320px long-name layout từ 1314px xuống 305px |
| B10-10 | Rerun packaged runtime và final regression/audit | SixServiceOutpatientAcceptanceIT 1/1; FE 501/501, typecheck/lint/build PASS |

**FE verification:** focused lifecycle/create/stock 54/54; final full suite **501/501 tests, 55 files**,
zero failures/skips (`pnpm test -- --maxWorkers=2`); typecheck/lint/build PASS. Một lượt full suite
trước đó chạy cùng nhiều JVM bị bốn timeout ở deferred Inpatient; không sửa source/tests của owner khác.
Sau khi runtime kết thúc, suite cuối ở trên đạt. Warning jsdom navigation vẫn có; không gọi nó là browser proof.

**Browser UI evidence** ở [evidence 2026-10-18](evidence/huy-six-service/2026-10-18/README.md).
HTTP fixtures tách biệt tại 3180/3181, fake ADMIN chỉ dùng layout/focus; không commit fixtures vào production.
Matrix chín cases: form V1, outpatient Report, Notification detail, mỗi màn qua Light/Dark/System
và 320/768/1280px. Mọi document scrollWidth ≤ viewport; bảng lịch sử cuộn trong container.
Đã kiểm empty V1 focus record, Report invalid filter focus department và bỏ số cũ khi unavailable,
cancel/dispense Escape trả focus đúng trigger. HF6-10.3 còn OPEN vì chưa hoàn tất toàn bộ keyboard/status matrix.

**Runtime thật, tách với browser fixture:** clean packaged artifacts vì class output cũ có
`ClassFormatError`, không sửa BE. Chạy:

```powershell
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/gateway,backend/eureka-server -am -DskipTests clean package
mvn -q -pl backend/support-service -am -Psix-service-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

`SixServiceOutpatientAcceptanceIT`: **1 test, 0 failures/errors/skips, 211.5s**. Disposable PG16/RabbitMQ
và packaged Core/Clinical/Lab/Pharmacy/Billing/Support + Gateway/Eureka, actual staff login. Source
`backend/support-service/src/test/java/com/mediflow/support/SixServiceOutpatientAcceptanceIT.java`
khóa profile/flags; fresh output ở `backend/support-service/target/six-service-acceptance/` và Failsafe reports.
Gateway registry có đúng sáu business apps + Gateway; Report/Notification dùng hai stores; actual
Support→standalone rollback→Support trên test stores đạt, không dual writer. Rx payment 30.000 VND,
stock 20 giữ nguyên khi paid và xuống 17 sau dispense ba đơn vị; duplicate payment/dispense không
thêm effect. Report receipts 130.000 VND (EXAM + Rx), refunds 0 không chứng minh completed refund.
Bootstrap ADMIN và PATIENT/MANAGER privacy probes dùng harness-signed tokens; không tính là issuer
PATIENT login hoặc FE signed-browser acceptance. Chưa có public accepted outpatient coverage proof.
Original Compose volumes không bị thay đổi; test containers đã tự dọn.

Đóng thêm HF6-09.1, HF6-10.2/.4, HF6-11.1: **6/14 task, 39/56 subtask**; Pharmacy/Report gốc
**5/12 task, 35/48 subtask**, Notification vẫn 4/8. HF6-06/09/11/14 không đóng toàn task chỉ từ API harness.
Shared old-request 401/session race còn ở [handoff chung, mục 6](../../docs/handoffs/HANDOFF-HUY-MVP.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).

## 10. Đợt tiếp theo — 10 đầu việc denial/keyboard

User giao tiếp “chạy tiếp 10 task” trong khi chốt đợt trên. Những việc dưới đây sửa UX khi server
đã từ chối quyền; backend vẫn là authority. Không thêm API/role và không sửa shared AsyncState/auth.
Denial reset theo session hoặc resource content remount; transient read errors vẫn được retry.

| ID | Hoàn thành | Kiểm thử bắt lỗi trước sửa |
|---|---|---|
| B20-01 | Lookup Notification invalid UUID focus về input, xóa pagination và bỏ pending response | Invalid submit khi request trước chưa xong |
| B20-02 | History Notification 403 không retry cùng patient; vẫn tìm patient khác | Cùng denied scope không dispatch, scope mới có dispatch |
| B20-03 | Detail Notification 403 giữ correlation, bỏ retry, có đường về tra cứu | Denied read chỉ một request; 404/503 vẫn retry |
| B20-04 | Send Notification 403 khóa form và handler, giữ đường quay lại | Direct form resubmit không gửi lần hai |
| B20-05 | Drug catalog 403 bỏ retry và chặn submit tìm kiếm lại | Button và direct submit đều bị khóa |
| B20-06 | Drug detail 403 bỏ retry sai ngữ cảnh | Correlation giữ nguyên; transient refresh test vẫn đạt |
| B20-07 | Rx detail 403 bỏ retry và giữ các paid actions đóng | Denial không mở cancel/dispense/read retry |
| B20-08 | Create drug 403 khóa fields/button/handler | Form resubmit không tạo request thứ hai |
| B20-09 | Adjust stock 403 khóa prepare/confirm và fields | Enter/submit không mở lại confirmation |
| B20-10 | Outbox 403 không vượt disabled button bằng Enter | Direct submit không mở lại replay confirmation |

Red run tái hiện đúng **10 failing cases / 7 files**, 98 cases cũ vẫn đạt. Sau sửa focused
**108/108 / 7 files** đạt. Kết quả final và browser follow-up được ghi tiếp tại evidence README;
không cộng các đầu việc B10/B20 vào mẫu số 14 HF6 hoặc tick thêm runtime criteria chỉ vì test giả lập.

## 11. Sửa dispense V1 theo runtime contract — 2026-10-19

Base `5ccecf83` đã có PR #439 (clock/publisher) và #443 (Notification byte identity).
Backend security và FE được tách thành hai PR. Phần FE chỉ sửa Pharmacy feature và route tương ứng;
không sửa backend `paymentConfirmed`, API DTO, shared auth/transport, hoặc bật rollout gates.

- Detail route dynamic truyền `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE` qua resolver hiện hành:
  unset/`outpatient` dùng V1, `legacy` dùng V0, giá trị khác đóng xuất thuốc.
- V1 ACTIVE/PENDING cho ADMIN/PHARMACIST mở confirmation bất kể boolean V0, nhãn trung tính;
  backend command kiểm quyền/clearance. Giữ cancel/dispensed guards và compatibility V0.
- Timeout 408 đọc lại trạng thái trước lần xác nhận mới; không tự retry mutation. Session/mode
  remount bỏ dialog và callback cũ. Không đánh dấu paid/dispensed khi server trả lỗi.
- Source audit và dependency episode/priceCode cho Vinh ở [handoff chung, mục 6](../../docs/handoffs/HANDOFF-HUY-MVP.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).

**Code verification:** Pharmacy và routes **233/233 tests, 21 files**; typecheck/lint/build PASS.
Full FE lần đầu **529/532**: một lỗi Lab fixture và hai timeout Inpatient. Chạy lại riêng hai file:
**19/20** đạt, hai ca Inpatient đã đạt; ca Lab `Bắt đầu xét nghiệm` vẫn không tìm thấy control
với fixture hiện hành. Không sửa feature/test ngoài scope để làm xanh. Đây là kết quả unit/component,
không phải browser acceptance và không tự đóng HF6.

**Trạng thái nghiệm thu:** baseline master vẫn **6/14 task, 39/56 subtask**. Không lấy kết quả
browser cũ có sửa boolean backend làm bằng chứng cho candidate này. Report chưa có accepted
outpatient coverage nên UI gate giữ OFF; Notification cần issuer PATIENT và cross-patient proof
do Vinh phối hợp backend. Security handoff và dispense handoff chỉ đóng sau review/acceptance.
