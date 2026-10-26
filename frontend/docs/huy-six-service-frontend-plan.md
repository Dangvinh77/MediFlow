# Plan code FE Huy — chốt MVP 6 service

> Cập nhật điều phối 26/10/2026: đây là checkpoint thực thi theo baseline ghi bên dưới.
> Nghiệp vụ đợt tiếp theo theo [kế hoạch ngoại trú](../../docs/plans/07-outpatient-workflow-implementation.md);
> phụ thuộc hiện hành theo [registry bàn giao](../../docs/handoffs/README.md).
> Quyền giao theo task và kết quả kiểm thử cũ không tự mở rộng sang đợt triển khai mới.

**Ngày lập:** 2026-10-18. **Owner thực thi:** Huy (`LQHuy0210`).  
**Source baseline:** `025ef39e358756a28043d9809fad165096528fbf` trên nhánh local `Huy`.  
**Trạng thái:** FE ACCEPTANCE COMPLETE trong môi trường kiểm thử cô lập — **14/14 task (100%)**, **56/56 subtask (100%)**; checkpoint và giới hạn ở mục 25. Không phải production rollout approval.

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
Phụ thuộc của checkpoint này được giữ trong [Huy checkpoints](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md).
Phụ thuộc mới phải đối chiếu và đăng ký tại registry hiện hành. Không tạo raw fetch hoặc cross-feature imports để vượt dependency.

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

Owner Huy; DONE trên candidate mục 14. Liên hệ HFE-02/P-08/Q-02 cũ.

- [x] HF6-01.1 Ghi commit candidate, diff, route/method/role/error matrix và fixtures request/response từ source hiện tại.
- [x] HF6-01.2 Khóa nguồn `recordId`, `careEpisodeId`, patient/doctor/department và `priceCode`; ghi cách lấy từ public workflow đã có, không tự sinh authority IDs.
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

- [x] HF6-05.1 Lookup/deep link bằng prescriptionId thật; lifecycle/dispense giữ độc lập. Chỉ mode legacy diễn giải paymentConfirmed; V1 dùng nhãn trung tính, không tự đặt metadata V1.
- [x] HF6-05.2 V0 chặn xuất theo boolean legacy. V1 ACTIVE/PENDING cho role hợp lệ xác nhận command để backend kiểm clearance; có tải lại chủ động, không polling vô hạn hoặc tự xác nhận tiền từ UI Billing.
- [x] HF6-05.3 Giá, tên thuốc, lineTotal/totalAmount lấy Pharmacy response; không gọi sibling feature hoặc suy ra invoice/charge/grant IDs.
- [x] HF6-05.4 Kiểm V0 pending→paid bằng fresh GET; V1 GET false vẫn cho command theo runtime mode, không coi GET là proof clearance. Giữ stale response, 404/403, expired/terminal và lỗi refresh; link Billing chỉ khi contract public được xác nhận.

Đóng khi read workspace phản ánh đúng dữ liệu mới và không tự tạo financial proof; không bắt buộc
thêm Billing entrypoint nếu chưa có contract, có thể chuyển qua workflow Billing hiện có.

### HF6-06 — Hủy và xuất thuốc qua Gateway bằng role thật

Owner Huy; sau HF6-04/05 và runtime được xác nhận. Đóng phần còn lại P-06.

- [x] HF6-06.1 Recheck ADMIN/DOCTOR cancel, ADMIN/PHARMACIST dispense; unknown/null role và đường không allowlist bị deny, backend vẫn quyết định resource authority.
- [x] HF6-06.2 Browser/API thực với signed users: bác sĩ được phép tạo/hủy đúng quan hệ, bác sĩ khác không có authority bị deny; PHARMACIST không hủy, DOCTOR không xuất.
- [x] HF6-06.3 Xuất trước thanh toán bị chặn; sau thanh toán chỉ một dispense effect và tồn kho đúng; paid không tự xuất thuốc, terminal retry không tạo effect mới.
- [x] HF6-06.4 Giữ reason/releasedReservations, confirmation/focus/Escape, conflict/unknown outcome→fresh read; kiểm thử bằng role thật và lưu HTTP/correlation/effect evidence đã bỏ thông tin nhạy cảm.

Đóng khi cả allowed/denied scenarios qua Gateway thật và hiệu ứng nghiệp vụ đúng; không dùng
fixture token hoặc đổi role để bypass. Không chỉnh backend chỉ để UI test xanh.

### HF6-07 — Regression danh mục, stock và outbox

Owner Huy; có thể làm độc lập sau HF6-01. Kế thừa P-01/02/03/07, không dựng lại màn hình.

- [x] HF6-07.1 Kiểm catalog/search/page/size/deep links, stale request, empty/404 và bảng đúng mẫu trên API hiện tại.
- [x] HF6-07.2 Kiểm ADMIN/PHARMACIST tạo thuốc/stock delta, DOCTOR chỉ đọc; validation/focus, khóa gửi trùng và dùng số tồn server.
- [x] HF6-07.3 Kiểm outbox ADMIN-only, known event UUID, bodyless replay và replayed false/true; không dựng list API hoặc tự replay khi mount.
- [x] HF6-07.4 Chạy regression component/API và smoke có signed session cho các flow ảnh hưởng; replay thật chỉ dùng event test có nguồn gốc và effect được xác định.

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
- [x] HF6-09.2 Tạo dữ liệu test qua public flows, có coverage được backend chấp nhận đúng period/department/currency/zone; không ghi SQL hoặc tự chế coverage từ browser. Nghiệm thu calendar test cô lập, giới hạn ở mục 25.
- [x] HF6-09.3 Signed ADMIN/MANAGER đọc ngày/tháng thành công, role không được phép bị deny; missing coverage vẫn unavailable, periods/metrics khớp backend evidence. API và browser ở mục 25.
- [x] HF6-09.4 Kiểm server env `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED` OFF không mount/query, ON sau restart mới hiện flow; kiểm tắt lại UI và backend flags giữ trạng thái đã thỏa thuận. Evidence runtime riêng ngày 2026-10-21 tại mục 22; không đổi default gates trong source.

Đóng khi chính browser đi qua Gateway→Support thật; evidence packaged backend hoặc browser mocks
không thay điều kiện này. Chỉ bật cấu hình trong môi trường nghiệm thu đã được giao, không tự rollout production.

### HF6-10 — Đồng nhất giao diện và khả năng sử dụng

Owner Huy trong Pharmacy/Report/Notification; sau HF6-04/05/08/13, tận dụng shared frame đã có.

- [x] HF6-10.1 Đối chiếu màn thay đổi với mẫu đã duyệt: title/nav/panel/table/lookup, semantic tokens và shared primitives; không mang fixture/BẢN MẪU vào production.
- [x] HF6-10.2 Browser Sáng/Tối/Hệ thống tại desktop, tablet và mobile 320–360px; chỉ bảng cuộn ngang, UUID/tên dài vẫn đọc được.
- [x] HF6-10.3 Kiểm bàn phím/tab order/label/focus lỗi/Escape/return-focus/status text/loading/empty/error/permission trên flow V1 và Report.
- [x] HF6-10.4 Lưu screenshot/evidence theo route/theme/viewport; gap shared drawer/header ghi trong handoff chung, sửa đúng owner scope trước khi đóng.

Đóng khi các màn thuộc Huy dùng được trên thiết bị/theme đã cam kết; không lấy việc Surgery chưa
có UI làm blocker cho theme MVP như HFE-01 lịch sử.

### HF6-11 — Browser workflow ngoại trú trên hệ thống 6 service

Huy chịu trách nhiệm đoạn Pharmacy/Report; owners khác hỗ trợ setup/contract. Sau HF6-06/07/09/10.

- [x] HF6-11.1 Dùng môi trường test có PostgreSQL/RabbitMQ, sáu business apps + Gateway/Eureka; ghi commit/profile/flags/health/routing và tài khoản role hợp lệ, không ghi secrets.
- [x] HF6-11.2 Workflow thật: nguồn khám ngoại trú đủ điều kiện → FE tạo Rx V1 → Billing payment qua surface hiện có → Pharmacy refresh/dispense → Report sau đối soát; IDs từ response thật. Evidence liên kết nhiều runtime cùng candidate, không tuyên bố toàn bộ là một browser E2E; mục 25.
- [x] HF6-11.3 Kiểm chưa thanh toán, source không hợp lệ, role denial, session hết hạn, API unavailable và retry; không tạo trùng Rx/dispense, không tự fallback V0/legacy report. Matrix cùng candidate, nhiều runtime thật, xem mục 23; không đồng nghĩa HF6-11.2 đã có accepted Report.
- [x] HF6-11.4 Kiểm Notification intake trong workflow được nghiệm thu: private notice đúng người theo public API, người khác bị deny; Notification FE thuộc scope user giao, nghiệm thu riêng tại HF6-14. Evidence lifecycle/payment và signed ownership ở mục 18; không đóng Report/refund.

Đóng khi acceptance Pharmacy/Report qua browser thật đạt. Không yêu cầu viết lại Clinical/Billing/Lab
FE để làm test: dùng public flow/API đã có cho setup hoặc đoạn chưa có UI, ghi rõ đoạn nào chạy bằng
browser và đoạn nào bằng API. Không gọi đó là all-browser E2E nếu còn bước API.

### HF6-12 — Regression cuối, evidence và chốt release FE

Owner Huy; sau HF6-01…11. Không đồng nghĩa chốt FE toàn nhóm hay global M6.

- [x] HF6-12.1 Candidate cuối đạt focused Pharmacy/Report tests, full FE suite, typecheck, lint và production build; ghi counts/failures/skips, commands và commit thực chạy.
- [x] HF6-12.2 Audit diff/contract: gateway-only/lib-api-only, không sibling imports, giá/authority giả, secrets/PII/fixture trong production; source chỉ đúng quyền được giao.
- [x] HF6-12.3 Cập nhật plan/workboard/guides và handoff chung theo evidence; migration/runtime/backend không được sửa ngoài scope để đóng FE.
- [x] HF6-12.4 Ghi setup/demo/rollback UI và limitations; signed-role browser PASS trên candidate cuối, release verdict rõ phần Huy; chỉ đóng handoff khi cả hai phía đạt acceptance. FE candidate ready for review; handoff production coverage/cutover vẫn mở, mục 25.

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

- [x] HF6-14.1 Browser qua Gateway→Support có signed ADMIN/NURSE/PATIENT; đọc đúng người, cross-patient deny, NURSE không detail/send; không coi mock role là proof.
- [x] HF6-14.2 Manual IN_APP gửi một effect, lịch sử/detail khớp response; EMAIL/SMS chỉ nghiệm thu với provider/test recipient được cấp, ghi giới hạn delivery rõ. IN_APP đạt; không có external provider acceptance, xem mục 18.
- [x] HF6-14.3 Lifecycle/payment/refund notice hiển thị đúng từ backend intake, giữ privacy; missing patient identity claim không fallback accountId hoặc tự orchestration từ FE. Refund command/retry → một notice, owner browser đọc được và foreign browser deny; provenance của signed TEST negative ở mục 24.
- [x] HF6-14.4 Browser Light/Dark/System desktop/mobile, keyboard/error/focus/retry, ảnh/evidence candidate và release verdict Support; không đưa secrets/PII vào repo. Verdict HOLD tích hợp, không đồng nghĩa bật gates hoặc global release GO.

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
Shared old-request 401/session race còn ở [handoff chung, mục 6](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).

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

## 11. Keyboard và focus sau đồng bộ master — 2026-10-19

Candidate: `51eedfcd` cộng diff của lượt này. Đây là phần local của HF6-10.3 và regression
HF6-04/08/13, không mở scope shared auth hoặc feature Clinical/Lab/Inpatient.

- Kê đơn: Enter trên nút thêm dòng đưa focus đến chọn thuốc mới; Tab tiếp tục qua số lượng/cách dùng.
  Xóa dòng đầu/giữa/cuối đưa focus đến dòng còn lại phù hợp, vẫn giữ tối thiểu một dòng.
- Server validation: focus theo thứ tự field/dòng hiển thị, không theo thứ tự `details` của server.
  Áp dụng form kê đơn và gửi Notification; không đổi wire DTO hoặc request.
- Kết quả tạo Rx chưa rõ: vùng lỗi nhận focus khi draft bị khóa; chỉ khi người dùng xác nhận bắt đầu
  ý định mới mới đưa focus về mã hồ sơ. Snapshot/key và explicit retry giữ nguyên.
- Notification: lỗi tổng quát, 403 và unknown outcome nhận focus sau khi kết thúc trạng thái đang gửi;
  không thêm resend/idempotency contract. Fields bị khóa vẫn không nhận focus sai.
- Cả bốn Report views: vùng lỗi có tên/mô tả truy cập được, giữ correlation/code và retry theo lỗi.
  Tab/Enter retry chỉ đọc; lỗi đến muộn không giành focus khi người dùng nhập bộ lọc hoặc làm việc
  ngoài Report. Sau lỗi, sửa bộ lọc không tự đổi focus hoặc phát thêm request.

Thêm **22 ca hồi quy**: Pharmacy 8, Notification 4, Report 10. Red runs đầu tái hiện 8 + 4 + 6 ca;
thêm 4 ca pending Report xác nhận cần giữ focus khi đang nhập. Sau sửa, ba file create/send đạt
74/74; Report lifecycle cuối đạt 24/24. Focused toàn phạm vi trước guard cuối đạt 392/392 trong
31 file; kết quả candidate cuối ghi tại workboard.

Candidate cuối: `pnpm test -- --maxWorkers=1` đạt **527/528 trong 55 file**, 133.14s,
chỉ lỗi `LabDetail.test.tsx:194` đã có trên master (fixture V1 READY thiếu clearance nhưng mong nút
bắt đầu). Cả 22 ca mới đạt; Inpatient đạt trong lượt full này. `pnpm typecheck`, `pnpm lint`,
`pnpm build` đều PASS, exit 0. Không đổi foreign source hoặc làm yếu test để có kết quả xanh.

Kiểm thử keyboard dùng Testing Library/user-event trong jsdom. Chưa chạy browser mới, screen reader
hoặc signed Gateway workflow trong lượt này; **HF6-10.3 vẫn OPEN**, tiến độ 6/14 và 39/56 giữ nguyên.
Lỗi Lab có trên master và shared late-401/session race tiếp tục tách khỏi evidence local của Huy;
phụ thuộc vẫn ở một handoff chung.

## 12. Giữ kết quả điều chỉnh kho chưa rõ qua lỗi tải lại — 2026-10-19

Candidate: `51eedfcd` cộng working diff, tiếp nối mục 11. IMPLEMENT trong Pharmacy; không đổi
API/backend hoặc shared auth. Đã đối chiếu `DrugController`, `AdjustStockRequest`,
`DrugApplicationService` và controller tests: endpoint cộng/trừ delta, có audit trong transaction,
không có command key hay public API tra kết quả riêng. Tải lại tồn không chứng minh lệnh trước rollback.

- Network error, HTTP 408/5xx và success envelope không đọc được chuyển sang kết quả chưa rõ.
  Giữ message/correlation và delta/unit của lệnh; xóa delta/reason cũ, không tự gửi lại mutation.
- Detail giữ cảnh báo ngoài vòng đời form. Lỗi refresh, retry read đang chờ, 403 và 404 không làm mất
  evidence; không hiển thị lại dữ liệu cũ như kết quả tải thành công hoặc mở retry trái quyền.
- Chỉ sau khi đọc lại thành công mới cho xác nhận đã đối chiếu. GET thành công, kể cả tồn không đổi,
  không tự đóng cảnh báo. Người dùng đối chiếu qua nguồn nghiệp vụ; nút xác nhận không gọi API hoặc
  tự kết luận lệnh cũ đã thành công. Sau đó mở draft trống, focus số lượng để lập yêu cầu mới.
- Cảnh báo nhận focus nếu control trước đã biến mất; không lấy focus đang ở ngoài. State chỉ ở bộ nhớ
  của drug/session hiện tại, không ghi localStorage; đổi thuốc/tài khoản bỏ state và response cũ.
- Validation 422 giữ input để sửa như trước, không buộc đối chiếu. Definite stock conflict vẫn refresh
  theo flow cũ; signed role và backend vẫn là authority.

Thêm **11 ca hồi quy**: 8 DrugDetail, 1 validation stock, 2 session lifecycle. Red run tái hiện đúng
8 ca DrugDetail fail trước sửa, 10 ca cũ vẫn đạt. Focused candidate cuối **46/46 trong 3 file**.
Candidate cuối: full suite **538/539 trong 55 file**, 136.68s, exit 1; chỉ lỗi đã có ở
`LabDetail.test.tsx:194`, không có local diff Lab. Typecheck cuối (sau build), lint và build đều
**PASS, exit 0**. Lần typecheck chạy song song build trước đó gặp generated routes đang tái tạo;
không sửa source/config để né lỗi và lần tuần tự đã đạt. Logs chi tiết ở workboard.

Đây là bổ sung local, không đóng signed Gateway/browser hoặc Report coverage. Tiến độ giữ
**6/14 task, 39/56 subtask**; HF6-10.3 vẫn OPEN. Không có browser/screen-reader acceptance mới.

## 13. Đợt năm đầu việc timeout — 2026-10-19

User giao “chạy 5 task tiếp theo”. Candidate `51eedfcd` + working diff, giữ các thay đổi mục 11/12.
Đây là năm đầu việc local trong HF6-04/06/07/13, không phải năm HF6 task đã nghiệm thu runtime.
Huy IMPLEMENT Pharmacy, DELEGATED Notification theo scope mục 1. Chỉ VERIFY-CONTRACT backend/Gateway.

| ID | Trạng thái | Kết quả |
|---|---|---|
| B5-01 — tạo Rx V1 | DONE local | HTTP 408/5xx không bị coi là validation; khóa draft, giữ snapshot/key và correlation. Chỉ explicit retry mới gửi lại nguyên intent, không fallback V0. |
| B5-02 — tạo thuốc | DONE local | HTTP 408 đưa vào kết quả chưa rõ; giữ input, khóa field/handler và focus vùng lỗi sau pending. Không map detail của phản hồi chưa rõ thành lỗi field; có đường tra cứu danh mục. |
| B5-03 — gửi Notification | DONE local | HTTP 408 khóa gửi để tra lịch sử, không resend/idempotency tự chế. Giữ correlation, focus feedback và không hiển thị validation giả từ timeout details. |
| B5-04 — hủy/xuất Rx | DONE local | HTTP 408 đóng confirmation, GET trạng thái mới trước thao tác tiếp. Nếu GET lỗi, giữ evidence lệnh chưa rõ; explicit retry chỉ đọc, không hủy/xuất lại hoặc khẳng định effect. |
| B5-05 — replay outbox | DONE local | HTTP 408/5xx/unreadable success hiển thị kết quả chưa rõ và focus cảnh báo, không focus nút gửi lại. Giữ event UUID; Enter trên cảnh báo không replay; chỉ explicit confirmation mới replay bodyless cùng event. |

Pharmacy dùng helper context-local `mutationOutcome.ts` để đồng nhất 408, 5xx và status của success
không đọc được; AdjustStock tái sử dụng nhưng giữ nguyên flow đối chiếu mục 12. Notification giữ
classification trong feature riêng, không import chéo hoặc sửa shared transport/auth. Business 4xx,
401/403 và known duplicate/stock conflict giữ semantics cũ. Public controllers/DTO/Gateway role matrix
đã đối chiếu; wire/body/header và capability không đổi. Replay vẫn là requeue immutable event,
không chứng minh consumer đã xử lý thành công.

Thêm **13 ca hồi quy / 5 file**: Rx V1 2, tạo thuốc 3, Notification 2, terminal Rx 2, outbox 4.
Red run **12 fail / 112 pass**, 27.82s, tái hiện lỗi ở cả năm nhóm; case Notification 504 đã đạt trước sửa.
Sau sửa **124/124 trong 5 file**, 20.55s. Full suite candidate cuối **551/552 trong 55 file**,
143.16s, exit 1; chỉ lỗi Lab đã có tại `LabDetail.test.tsx:194`, không có local diff Lab.
Typecheck, lint và build đều **PASS, exit 0**; typecheck chạy trước build. Full suite bao gồm cả
stock và Report regressions trước đó; Inpatient đạt. Commands/logs candidate cuối ở workboard.

Không có browser hoặc signed Gateway acceptance mới; không mở profile/env/coverage, không đổi
source ngoài Pharmacy/Notification và docs được giao, không tạo handoff mới. Tiến độ HF6 giữ
**6/14 task, 39/56 subtask**; các tiêu chí runtime còn mở không được tick từ component fixtures.

## 14. Nghiệm thu signed Gateway và browser — 2026-10-19

Candidate `51eedfcd` trên `Huy` + working diff. Sau khi user khởi động Docker, chạy sáu business
executables thật + Gateway/Eureka với PostgreSQL/RabbitMQ disposable; actual issuer staff login.
Môi trường `63684242eda5`, manifest lúc 14:38 giờ địa phương. Không dùng mock Gateway hoặc fixture
token cho các browser actors. [Evidence và ảnh](evidence/huy-six-service/2026-10-19/README.md)
ghi rõ browser/API steps và các lần chạy lỗi trước đó.

**Tiến độ: 10/14 task, 45/56 subtask.** Sáu tiêu chí mới là HF6-01.2, HF6-06.2/.3/.4,
HF6-07.4 và HF6-10.3; đóng thêm bốn task HF6-01/06/07/10. Pharmacy/Report gốc đạt
9/12 task, 41/48 tiêu chí; Notification giữ 1/2 task, 4/8 tiêu chí.

| Task đóng mới | Bằng chứng trên candidate |
|---|---|
| HF6-01 | Source record/patient/doctor/department từ public workflow/Core responses; public Clinical order của visit test trả explicit episode. `DRUG` được Billing test allowlist xác nhận. Không suy episode từ appointment hoặc bắt production tạo Lab order. |
| HF6-06 | DOCTOR browser tạo Rx V1; assigned-doctor cancel API 200/releasedReservations=1, foreign-doctor create/cancel và PHARMACIST cancel 403. DOCTOR dispense 403, unpaid dispense 422. CASHIER API thanh toán 30.000 VND; browser refresh rồi PHARMACIST xác nhận xuất. |
| HF6-07 | Signed catalog/search/empty/page-size/deep link, ADMIN browser tạo thuốc và stock 5→12. Xuất Rx giảm stock 40→37; duplicate payment/dispense không thêm effect. Replay event có nguồn gốc giữ nguyên payload hash, stock 37, một Clinical attachment/Report journal/Notification notice/dispense slip trong kiểm tra quan sát. |
| HF6-10 | V1 validation/tab/add-remove line/submit/loading, cancel/dispense focus/Escape/return-focus/status; Report invalid date/UUID/limit, empty, permission, unavailable/error focus và keyboard retry. Light/Dark/System, mobile 320px, tablet 768px, desktop 1366px có ảnh. Outpatient active view có component keyboard evidence mục 11; browser route vẫn OFF. |

Rx browser `26161972-6b4e-433e-84a6-bc8d98d0f4c7`: payment không tự xuất, manual dispense chỉ
một effect. Fresh read sau thanh toán đã phát hiện lỗi BE V1 chỉ đọc legacy receipts. Pharmacy nay
đọc projection clearance VERIFIED đúng Rx/patient/episode, còn hạn và không future-dated; read-only,
không lock/write/receipt giả. `paymentConfirmed` là chỉ báo xác nhận cục bộ, command xuất vẫn kiểm
current Billing authority sau locks. DTO và FE guard không đổi.

Report legacy DAILY/MONTHLY/TOP_MEDICINES qua Support thật: signed ADMIN 200, DOCTOR 403 ở cả
ba API; browser ngày/tháng/top empty-zero và DOCTOR permission đạt. Tạm dừng riêng Support test
để kiểm unavailable/correlation/focus, restart cùng stores/flags rồi Enter retry tải thành công.
`MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=false` suốt lượt; route outpatient chỉ status/backlink.
Chưa accepted coverage, MANAGER, outpatient ON hoặc privacy Notification browser.

Verification:

- Focused backend đầu 40/40. Full Pharmacy đầu 846 tests/108 classes: zero assertion failures,
  tám lỗi class-file ở event-writer suite khi IDE cùng ghi output; invocation exit 1.
- Snapshot source-identical, hashes sáu Java files khớp: rerun service/persistence/event-writer
  **48/48 PASS**, zero failures/errors/skips. Counts chồng nhau, không cộng vào 846.
- Isolated reactor `clean package` với `maven.test.skip=true` **exit 0** để dựng fresh executables;
  package không phải full regression run.
- Opt-in `SixServiceOutpatientAcceptanceIT` browser hold **1/1 PASS, 770.5s, exit 0**, zero
  failures/errors/skips. PASS bao gồm explicit verification commands; hold không chạy nhánh
  standalone Support rollback. Nguồn khám và payment là public API, không gọi đây là all-browser E2E.
- FE production không đổi trong lượt này: kết quả cuối mục 13 **551/552, 55 files**, lỗi Lab fixture
  đã có `LabDetail.test.tsx:194`; typecheck/lint/production build PASS. Không ghi full FE xanh.

Còn **HF6-09 (1/4), HF6-11 (1/4), HF6-12 (3/4), HF6-14 (0/4)**: accepted Report coverage và
ADMIN/MANAGER/ON-OFF, workflow Report sau đối soát và session/privacy, signed Notification
ADMIN/NURSE/PATIENT/manual IN_APP/lifecycle, rồi release cuối. Shared late-401 vẫn ở
[handoff chung](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md); không mở shared auth scope trong lượt này.
Đã STOP hold, dọn disposable processes/containers, dừng FE test và trả theme System/logout;
Docker Desktop vẫn chạy. Chưa commit/push hoặc rollout production.

## 15. Checkpoint PR #446: sửa dispense V1 theo runtime contract — 2026-10-19

Base `5ccecf83` đã có PR #439 (clock/publisher) và #443 (Notification byte identity).
Backend security và FE được tách thành hai PR. Phần FE chỉ sửa Pharmacy feature và route tương ứng;
không sửa backend `paymentConfirmed`, API DTO, shared auth/transport, hoặc bật rollout gates.

- Detail route dynamic truyền `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE` qua resolver hiện hành:
  unset/`outpatient` dùng V1, `legacy` dùng V0, giá trị khác đóng xuất thuốc.
- V1 ACTIVE/PENDING cho ADMIN/PHARMACIST mở confirmation bất kể boolean V0, nhãn trung tính;
  backend command kiểm quyền/clearance. Giữ cancel/dispensed guards và compatibility V0.
- Timeout 408 đọc lại trạng thái trước lần xác nhận mới; không tự retry mutation. Session/mode
  remount bỏ dialog và callback cũ. Không đánh dấu paid/dispensed khi server trả lỗi.
- Source audit và dependency episode/priceCode cho Vinh ở [handoff chung, mục 6](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).

**Code verification:** Pharmacy và routes **233/233 tests, 21 files**; typecheck/lint/build PASS.
Full FE lần đầu **529/532**: một lỗi Lab fixture và hai timeout Inpatient. Chạy lại riêng hai file:
**19/20** đạt, hai ca Inpatient đã đạt; ca Lab `Bắt đầu xét nghiệm` vẫn không tìm thấy control
với fixture hiện hành. Không sửa feature/test ngoài scope để làm xanh. Đây là kết quả unit/component,
không phải browser acceptance và không tự đóng HF6.

**Trạng thái nghiệm thu tại checkpoint PR #446 (lịch sử, được mục 14 cập nhật):** baseline master **6/14 task, 39/56 subtask**; proof mới bên dưới đóng
HF6-06.2/.3 thành **6/14 task, 41/56 subtask**. Không lấy kết quả
browser cũ có sửa boolean backend làm bằng chứng cho candidate này. Report chưa có accepted
outpatient coverage nên UI gate giữ OFF; Notification cần issuer PATIENT và cross-patient proof
do Vinh phối hợp backend. Security handoff và dispense handoff chỉ đóng sau review/acceptance.

**Browser/API thật trên code FE `9a00d7f`, backend JWT `5334aa8c`:** tạo Rx V1 bằng issuer DOCTOR,
DOCTOR không thấy nút xuất và public command trả 403; PHARMACIST thử unpaid nhận 422/correlation.
Issuer CASHIER trả tiền qua public API harness, duplicate payment cùng transaction; GET paid vẫn
`paymentConfirmed=false`, PENDING, stock 40. Browser refresh rồi PHARMACIST xác nhận xuất thành công;
retry API trả phiếu đã có, stock 37, một dispense slip/outbox và một Clinical attachment.
Signed API checks cũng kiểm foreign-doctor create/cancel deny, PHARMACIST cancel deny và assigned
doctor cancel/releasedReservations=1; browser ADMIN thấy đơn canceled không có terminal actions.

Enter mở confirmation, focus Đóng, Escape trả trigger, Tab tới xác nhận và success nhận focus đã kiểm.
Sáng/Tối/Hệ thống, desktop và 320px trên detail/confirmation; document width 305 ≤ viewport 320.
NURSE browser đọc thông báo đơn/thu tiền/đã cấp thuốc đúng patient qua Gateway→Support; đây chưa là
issuer PATIENT/cross-patient acceptance. NURSE bị chặn trang Report; ADMIN outpatient giữ unavailable
khi UI gate OFF, không hiển thị số 0. Chưa chạy browser accepted Report coverage ADMIN/MANAGER,
expired/revoked/Billing-down matrix, V0 signed runtime hoặc toàn bộ keyboard matrix; không đóng
HF6-06.4/09/10.3/11/14 từ riêng các proof một phần này. Kết quả nghiệm thu tiếp theo
và tiến độ hiện hành 10/14 task, 45/56 subtask được giữ ở mục 14.

[Evidence candidate và giới hạn nghiệm thu](evidence/huy-six-service/2026-10-19-jwt-v1/README.md)
chứa ảnh/AX, public response/correlation, source IDs, JAR hashes và local test overlay. Payment
được thao tác bằng API harness; không gọi đây là toàn bộ luồng Billing qua trình duyệt. Các profile
test vốn có chỉ chạy trên stores disposable; không sửa Compose/env/gate rollout hoặc mở scope deferred.

## 16. Notification detail: tải lại trạng thái và focus — 2026-10-20

Candidate `b7146306` + diff của lượt này. Huy DELEGATED trong Notification FE theo phạm vi
Support đã giao; chỉ sửa detail, feature tests và tài liệu. Đối chiếu public NotificationController,
NotificationDTO và API facade: ADMIN/PATIENT được GET detail, NURSE chỉ list, không có command
resend/read-state. Không gọi backend nội bộ hoặc thêm authority từ JWT subject.

- Thêm tải lại chủ động bằng GET đúng notificationId để xem trạng thái delivery mới; không polling
  hoặc gửi lại thông báo. Trong lúc tải, bỏ nội dung cũ và nút refresh cũ để tránh hiển thị dữ liệu cũ.
- Lỗi detail có vùng feedback được đặt tên/mô tả, giữ correlation; HTTP 403 không hiện retry.
- Sau retry/refresh, focus đến lỗi hoặc tiêu đề mới nếu trigger đã biến mất và focus đang ở body.
  Nếu người dùng đã chuyển sang control khác thì không lấy focus. Tab/Enter retry chỉ đọc.
- Đổi session hoặc notification vẫn bỏ response cũ; không lưu thêm thông tin bệnh nhân hay người nhận.

Thêm bảy ca component regression: refresh PENDING→SENT không send, focus 403/503, giữ focus ngoài
khi lỗi/tải lại, Tab/Enter retry và late response sau đổi session. Không dùng các fixture này để
đóng HF6-14 hoặc thay signed PATIENT/cross-patient acceptance. Report accepted coverage và các
dependency đã ghi trong handoff chung vẫn mở; không tạo handoff mới hoặc bật gate.

Sau đồng bộ `origin/master` tại `b7146306`: `pnpm test -- src/features/notification` đạt **62/62
tests, 3 files**; `pnpm typecheck`, `pnpm lint`, `pnpm build` đều **PASS, exit 0**. Rà diff theo
review checklist: không phát hiện blocker trong slice này, đủ gửi review FE; chưa phải go cho
release tích hợp. Không chạy lại full-suite hoặc browser/signed Gateway trong lượt này; không
ghi nhận thêm nghiệm thu HF6 hoặc thay kết quả lỗi Lab đã lưu ở checkpoint trước.

## 17. Notification history: tải lại theo scope đã tra cứu — 2026-10-20

Candidate base `26bf2ef7` + diff Notification history. Trong scope Support FE đã giao cho Huy:

- Tải lại chủ động bằng GET cùng bệnh nhân và trang đã gửi; sửa ô UUID chưa submit không đổi
  scope của bảng. Hiển thị mã bệnh nhân của kết quả để phân biệt với draft đang nhập.
- Cả kết quả rỗng cũng có tải lại để phát hiện thông báo mới. Trong lúc tải, xóa nội dung cũ;
  không polling, gửi lại hay tự tạo notification.
- Sau phân trang/tải lại/retry, focus kết quả hoặc vùng lỗi có mô tả/correlation khi focus đang
  ở body. Giữ focus nếu người dùng đang sửa mã hoặc thao tác control khác. HTTP 403 không retry;
  đổi session vẫn bỏ response cũ.
- Sáu regression cases bổ sung kiểm scope/trang, empty→new notice, focus/draft, 403/503 với
  Tab/Enter retry và late response sau đổi session. Notification **68/68 tests, 3 files PASS**;
  typecheck/lint/build **PASS, exit 0**. Rà scope/contract/diff theo review checklist: không có
  blocker cho slice FE này; đủ gửi review, chưa chốt release tích hợp. Không chạy full-suite mới.

Tiến độ checklist giữ nguyên; local improvement này không đóng tiêu chí tích hợp:

| Hạng mục còn mở | Đạt | Còn lại |
|---|---:|---|
| HF6-09 Report thật | 1/4 | Coverage backend chấp nhận, signed ADMIN/MANAGER, ON/OFF UI |
| HF6-11 Workflow liên service | 1/4 | Workflow hoàn chỉnh, failure/retry matrix, Notification privacy |
| HF6-12 Chốt release | 3/4 | Browser/evidence/release verdict trên candidate cuối |
| HF6-14 Notification thật | 0/4 | Signed users/ownership, manual delivery, intake, UI/browser evidence |

Mười task còn lại đã đủ 4/4: tổng **10/14 task, 45/56 subtask**, còn **11 tiêu chí**.
Docker API hiện không kết nối được `dockerDesktopLinuxEngine`; chưa có browser/Gateway/Support
acceptance mới trong lượt này. Giữ các dependency trong handoff chung hiện có, không bật gates.

## 18. Signed Notification acceptance và audit Report source — 2026-10-20

Candidate `44ff1ed7` + một sửa test Notification chờ focus effect. Giữ nguyên nhánh hiện tại theo
yêu cầu người dùng; không tạo nhánh, commit, push hoặc PR. Fetched master `71328cbc` chỉ để so
sánh, chưa merge. [Evidence và ảnh browser](evidence/huy-six-service/2026-10-20-closeout/README.md)
ghi kết quả/giới hạn, signed API responses không chứa credentials và trạng thái môi trường đã dọn.

- Chạy tám packaged JVMs (sáu business apps + Gateway/Eureka) từ source archive, PG16/RabbitMQ
  disposable bằng hold harness có sẵn. Hệ thống thật, signed issuer login; không chỉnh backend
  harness hoặc bật thêm default gates. Hold **1/1 PASS, exit 0**; không chạy normal refund/rollback
  branch trong lượt này. Backend/user stores có sẵn không bị dùng làm dữ liệu giả.
- Browser ADMIN gửi IN_APP đúng một entry; history/detail khớp public response, pending khóa
  submit. PATIENT owner đọc history/detail; foreign PATIENT 403, không lộ private content.
  NURSE đọc history, không detail/send; route và backend role denial đều được kiểm.
- Lifecycle Rx cancel và EXAM payment từ backend intake hiển thị đúng người. Signed public API
  kiểm owner 200, foreign/NURSE/MANAGER detail 403. Đóng **HF6-11.4**, **HF6-14.1/.2**.
  Không coi payment request là receipt; EMAIL/SMS local SENT không là provider delivery proof.
- Light/Dark detail 320px không overflow; System history 320px cuộn bảng cục bộ, desktop System
  1366×900. Enter submit/refresh, Support pause→focus lỗi→Tab/Enter retry→focus heading sau resume
  thực sự đạt. Đóng **HF6-14.4** với verdict **HOLD integrated release**, không phải release GO.
- Full rerun **590/591 tests, 57 files**: chỉ còn Lab fixture `LabDetail.test.tsx:194` ngoài scope.
  Notification focus test đợi effect thay vì assert ngay khi DOM vừa xuất hiện; production không đổi.
  Focused Pharmacy/Report/Notification **446/446, 31 files**; typecheck/lint/build **PASS**.
  Không dùng focused PASS thay full-suite/browser acceptance; không sửa Lab để tăng tiến độ.

| Task còn mở | Đạt | Điều kiện còn thiếu |
|---|---:|---|
| HF6-09 | 1/4 | Accepted coverage, signed successful ADMIN/MANAGER reports, ON/OFF nghiệm thu |
| HF6-11 | 2/4 | Workflow tới Report sau đối soát; đầy đủ session/unavailable/retry matrix |
| HF6-12 | 3/4 | Release cuối sau các dependency và full regression |
| HF6-14 | 3/4 | Refund intake và signed missing-patient-claim no-fallback runtime |

Tổng **10/14 task (71,4%), 49/56 subtask (87,5%)**, còn **7 tiêu chí**. Mục 17 và các counts
cũ là lịch sử. Chưa thể ghi bốn task còn lại hoàn tất chỉ từ source/build hoặc dữ liệu fixture:

1. Đã dò local và master: V27 chỉ tạo bảng coverage rỗng, production adapter chỉ đọc/check.
   Chưa có public/operation acceptance writer; INSERT chỉ thấy trong test fixture. Cần export và
   đối soát đủ CLINICAL/LAB/PHARMACY/BILLING_CASH/BILLING_REFUND, đúng period/scope/zone/VND,
   actor/reference và thời gian quan sát bao trùm ngày cuối. Không seed hoặc giả accepted coverage.
   Signed ADMIN/MANAGER hiện 404 REPORT_NOT_FOUND, DOCTOR 403; không hiển thị thành báo cáo zero.
2. Notification refund consumer OFF mặc định và hold hiện tại không bật. Không tự bật consumer
   để đóng HF6-14.3; lifecycle/payment không thay refund hoặc missing-claim signed denial.
3. Shared late-401 dependency và Lab fixture vẫn giữ trong handoff chung. Outpatient Report UI
   OFF; không kích hoạt thêm Surgery/Inpatient/full V2. Đối chiếu phụ thuộc mới tại [registry hiện hành](../../docs/handoffs/README.md); checkpoint cũ được giữ trong history.

### Cập nhật sau audit: writer Report H6-13 — 2026-10-20

Theo yêu cầu mới, Report đã có ADMIN preview/acceptance và V28 audit; nhận định chưa có writer
ở checkpoint trên là baseline trước thay đổi. Contract/quy trình tại
[Report operator workflow](../../backend/report-service/operations/outpatient-coverage-acceptance.md),
verification tại H6-13 trong plan backend. Gateway allowlist đã được bổ sung theo giao quyền
ngày 2026-10-21; source owners vẫn cần reviewed exports/completeness thật và packaged acceptance.
Module HTTP/PostgreSQL với fixtures không đóng HF6-09/11/12 hoặc thay browser acceptance;
tiến độ FE giữ **10/14 task, 49/56 tiêu chí**, các gate và Report UI vẫn OFF.

## 19. Bảo vệ phiên mới trước phản hồi 401 cũ — 2026-10-20

Base `8883f49d` trên nhánh Huy, working diff chưa commit/push. Người dùng giao rõ
`frontend/src/lib/api.ts` và `frontend/src/lib/api.test.ts` để sửa race phiên đăng nhập.
Wrapper chỉ clear session nếu bearer của yêu cầu bị 401 vẫn là token hiện hành, kiểm tra
sau khi đọc body. Yêu cầu cũ vẫn trả lỗi gốc; không retry command hoặc đổi hợp đồng API.
Anonymous request và request của phiên đã logout không được xóa phiên đăng nhập mới.

Bốn regression mới tái hiện lỗi trước sửa (4 fail / 9 pass); sau sửa bộ transport cùng
Pharmacy/Report/Notification đạt **459/459 tests, 32 files**. Test focus Notification detail
403/503 cũng đợi focus effect trước assertion; không thay production Notification.
Typecheck/lint/build PASS. Full-suite cuối chạy hai workers: **594/595 tests, 57 files**, còn
Lab fixture `LabDetail.test.tsx:194` ngoài scope. Lượt đầu có sáu lỗi (Lab, Notification focus,
bốn test Inpatient); sau sửa timing Notification và giới hạn concurrency, chỉ Lab còn lỗi.
Không sửa Lab/Inpatient; kết quả chi tiết và local report paths ở workboard. Không thay browser acceptance.

Shared late-401 đã sửa và có regression, nhưng **HF6-11.3 chưa đóng**: còn matrix thật trên
candidate cuối. Bốn task HF6-09/11/12/14 vẫn mở; tổng **10/14, 49/56**, không cộng criterion
cho một bản sửa con. Report writer đã merge; Gateway allowlist ADMIN cho hai route coverage đã
bổ sung ngày 2026-10-21. Cần reviewed source exports đủ năm nguồn và packaged runtime/browser
trước success ADMIN/MANAGER/đối soát chỉ số.
Refund Notification vẫn OFF. Core `Account.validate` bắt buộc PATIENT có patientId; public
account workflow không phát hành được PATIENT thiếu claim. Negative signed-token acceptance
cần bộ kiểm thử bảo mật được backend cấp, không nới Core hoặc tự tạo token để gọi là issuer proof.

Không có signed Gateway/browser acceptance mới trong lượt này. Không đổi gates, Gateway,
Lab, Inpatient hoặc Surgery. Toàn bộ dependency vẫn ở handoff chung hiện có.

## 20. Nối Gateway coverage — 2026-10-21

Người dùng giao kiểm tra BE và nối hai API Gateway. Source hiện có ADMIN-only GET
`/api/v1/reports/outpatient/coverage/preview` và POST
`/api/v1/reports/outpatient/coverage/acceptances`, đi theo Report route hiện có tới Support.
Không còn dependency chờ code Gateway. HTTP regression dùng JWT ký và Support peer giả lập;
không thay packaged Gateway→Support, reviewed exports năm nguồn hoặc browser acceptance.
Docker engine chưa kết nối được trong lượt này. Tiến độ FE giữ **10/14, 49/56**;
không bật gates hoặc tự duyệt coverage. Trạng thái BE/test cuối ở handoff chung.

## 21. Fresh Pharmacy/Support browser acceptance — 2026-10-21

Base `42aab198` trên Huy, working Gateway allowlist diff và một sửa timing assertion trong
Pharmacy stock-form test; không tạo nhánh, commit hoặc push. Docker đã hoạt động trở lại.
[Evidence có ảnh và signed API responses](evidence/huy-six-service/2026-10-21-final/README.md)
thay nhận định môi trường chưa chạy ở mục 20 cho checkpoint này.

- Chạy source-snapshot artifacts, sáu business apps + Gateway/Eureka, PostgreSQL/RabbitMQ thật.
  Hold harness 1/1 PASS, exit 0; không chạy normal refund/rollback branch. Runtime đã dừng.
- Browser DOCTOR tạo Rx V1 từ public record/source-order/catalog authority; PHARMACIST unpaid
  command bị từ chối đúng clearance. Cashier public API payment và same-key retry trả cùng một
  transaction. Browser refresh → confirm dispense thành công. Stock **40 → 37 → 37** khi lặp
  terminal command qua API. DOCTOR không có dispense action, cancelled Rx không có dispense.
  Paid legacy boolean thực tế là true; không gọi đây là runtime tái hiện paid-but-false.
- Owner PATIENT xem notice từ Rx/payment/dispense; foreign PATIENT browser không đọc được.
  Signed API kiểm owner 200, foreign/NURSE/MANAGER detail 403. Support pause/error focus rồi
  resume/Tab/Enter retry/heading focus đạt trên candidate này.
- Report ADMIN/MANAGER vẫn 404 thiếu accepted coverage, DOCTOR 403; UI ngoại trú OFF.
  Packaged Gateway coverage preview: ADMIN 404 vì approval gate OFF, MANAGER/DOCTOR 403.
  Hai route đã có source; không còn chờ code Gateway. Thiếu reviewed exports/completeness/
  cursors đủ CLINICAL/LAB/PHARMACY/BILLING_CASH/BILLING_REFUND để phê duyệt đúng sự thật.
- Full FE **594/595 tests, 57 files**: Lab fixture READY thiếu clearanceId là lỗi duy nhất.
  Xin giao đúng test file, chưa có trả lời nên không sửa Lab. Pharmacy focused 17/17 PASS
  sau khi chờ focus effect; typecheck/lint/build PASS. Không dùng các kết quả này thay browser.

Tiến độ **10/14 task (71,4%), 49/56 tiêu chí (87,5%)**; kết quả runtime mới củng cố phần
đã triển khai nhưng chưa đủ để đóng bảy tiêu chí còn lại:

| Task | Đạt | Còn thiếu để đóng |
|---|---:|---|
| HF6-09 | 1/4 | .2 coverage thật sau đối soát năm nguồn; .3 ADMIN/MANAGER success + metrics; .4 UI ON/OFF/rollback được phép |
| HF6-11 | 2/4 | .2 workflow tới Report đã đối soát; .3 toàn bộ matrix thật, gồm session hết hạn và source-invalid/unavailable còn thiếu |
| HF6-12 | 3/4 | .4 final integrated acceptance/release sau dependencies và full regression PASS |
| HF6-14 | 3/4 | .3 refund intake và issuer-supported signed missing-patient-claim negative |

Không tự bật gates. Notification refund consumer OFF; public Core bắt buộc PATIENT patientId,
không tự ký token để gọi là proof của issuer. Không seed coverage hoặc xem giao dịch trong ngày
là completeness của ngày cuối/năm. Release **HOLD**; toàn bộ dependency ở handoff chung hiện có.

## 22. Chạy tiếp sau khi Docker bật — 2026-10-21

[Evidence follow-up](evidence/huy-six-service/2026-10-21-followup/README.md) thay trạng thái
hiện hành ở mục 21 cho những điểm dưới đây. Base/artifacts giữ nguyên; không tạo nhánh,
commit, push hoặc thay default gates trong source. Chỉ bật gates cần thiết trong runtime
disposable theo yêu cầu chạy các tiến trình nghiệm thu.

- Normal six-service profile **PASS exit 0**, Surefire **18/18**, Failsafe **1/1**. Đã chạy
  refund/rollback branch: refund race/retry/over-refund và terminal guards, durable clearance
  qua Pharmacy restart, Support → standalone → Support. Đây là API harness, không browser.
- Hold harness **1/1 PASS exit 0**; public cashier refund 201 và retry cùng transaction.
  Notification refund consumer ON vẫn không nhận refund notice. Đã truy source: Billing
  `LedgerRefundService` dùng `appendHeld(payment.refunded)`; outbox publication allowlist
  không có refund và normal harness còn assert HELD. Cần Vinh chốt publication/gate và
  regression Billing trước delivery; không tiếp tục quy lỗi chỉ vì consumer OFF.
- Signed TEST credentials trên notification ID thật: positive owner 200, missing patientId
  401 tại Gateway, wrong patientId 403 tại Notification, expired/missing-exp 401. Subject
  bằng patientId và header giả không vượt guard. Không gọi token tự ký là issuer proof;
  attempt đầu nhầm undefined ID không được tính. Privacy negative đạt, refund intake chưa.
- Browser actual issuer session hết hạn → login. Report UI ON hiện form, submit hiển thị
  REPORT_NOT_FOUND có focus lỗi, không số 0. Restart UI OFF chặn form ở direct route;
  OFF không mount query component. Đóng **HF6-09.4**, backend test flags giữ đúng runtime,
  không rollout. ADMIN preview qua packaged Gateway→Support 200; chưa ký acceptance vì
  preview không thay reviewed export/completeness/cursor năm nguồn.

Hiện **10/14 task (71,4%), 50/56 tiêu chí (89,3%)**. HF6-09 **2/4**, HF6-11 **2/4**,
HF6-12 **3/4**, HF6-14 **3/4**. Còn sáu tiêu chí: 09.2/09.3/11.2/11.3/12.4/14.3.
Session-expiry subcase đã có evidence; chưa tick toàn bộ 11.3 khi matrix chưa hoàn chỉnh.
Full FE gần nhất vẫn 594/595, Lab fixture ngoài scope; không sửa/rerun trong lượt này.
Runtime/FE test đã dừng, browser logout, release **HOLD**. Dependency ở handoff chung.

## 23. Matrix nguồn không hợp lệ và retry — 2026-10-21

[Evidence và mapping đầy đủ](evidence/huy-six-service/2026-10-21-matrix/README.md).
Chạy lại cùng artifacts, hold **1/1 PASS exit 0**, actual Core/Clinical/Pharmacy/Gateway.
Browser DOCTOR ghép real foreign patient với real record: backend từ chối, error/correlation
và focus đúng, giữ form; gửi lại vẫn deny, không fallback V0. Signed API cùng body/key
**422 PHARMACY_CARE_CONTEXT_INVALID** cả hai lần; public stock **40 → 40**.

Đóng **HF6-11.3** sau khi nối evidence mới với unpaid/role/dispense-repeat/Support-retry ở
mục 21 và issuer-session-expiry/normal-six-service recovery ở mục 22. Cùng source candidate,
nhiều runtime disposable; không tuyên bố tất cả bước là browser hoặc một phiên E2E.
Không sửa production source hoặc outside-scope test. Browser logout và tiến trình test dừng.

Tiến độ hiện hành **10/14 task, 51/56 tiêu chí (91,1%)**: 09 2/4, 11 3/4, 12 3/4, 14 3/4.
Còn 09.2/09.3/11.2 (accepted five-source coverage + Report success), 14.3 (Billing refund
publication/intake), 12.4 (final release sau dependencies + full regression). Không còn chờ
Docker hoặc Gateway route. Full FE gần nhất 594/595 (Lab fixture ngoài scope); release HOLD.

## 24. Refund tới người nhận thật và hoàn tất regression FE — 2026-10-21

[Evidence mới nhất](evidence/huy-six-service/2026-10-21-close/README.md) thay các blocker
Billing refund và Lab fixture ở mục 21–23. User giao làm phần thiếu để chốt FE; đã sửa đúng
publication Billing và fixture Lab được nêu, giữ nhánh Huy, không tạo branch/commit/push.

- Billing thêm gate mặc định OFF cho **refund mới** thuộc immutable outpatient cohort, có
  acceptance reference và kiểm tra persisted transaction/source trong cùng transaction.
  V24 không sửa migration cũ hoặc thả historical HELD. Giữ nguyên public payload, quyền,
  lý do redacted, retry và deferred scope. Harness opt-in riêng, không rollout source gates.
- Public Rx V1 → payment → cancel → refund 201, retry trả cùng refundTx và đúng một notice.
  Core-issued owner PATIENT mở refund notice bằng browser/keyboard; foreign PATIENT mở
  cùng URL bị deny, ẩn nội dung và focus lỗi. Report nhận refund APPLIED 30.000 VND.
  Signed TEST negative credentials được ghi rõ nguồn, không gọi là Core issuer proof.
  Hold harness 1/1 PASS, Support unit 18/18; runtime đã dừng và browser logout.
- Full FE **595/595 trong 57 files**, focused Lab **15/15**, typecheck/lint/production build
  PASS sau khi fixture READY có clearanceId. Không nới production validation. Billing Docker
  **1.083/1.083**, normal six-service **1/1**, đều exit 0, không failure/error/skip. Billing
  có cảnh báo timeout khi dừng fork JVM sau System.exit(0), giữ rõ trong `verification.json`;
  không gọi là test failure hoặc che cảnh báo cleanup. Phân biệt browser/API ở evidence.

Đóng **HF6-14.3**. Tiến độ hiện hành **11/14 task (78,6%), 52/56 tiêu chí (92,9%)**:
HF6-09 **2/4**, HF6-11 **3/4**, HF6-12 **3/4**, HF6-14 **4/4**. Còn đúng bốn tiêu chí
**09.2/.3, 11.2, 12.4**: accepted five-source coverage, Report success/metrics và integrated
release. Không còn chờ Docker, Gateway, refund publication hoặc full FE regression.

Preview ADMIN qua packaged Gateway→Support đã 200, nhưng chưa có reviewed exports/cursors
và completeness của năm nguồn cho cùng kỳ đã kết thúc. Monthly reader yêu cầu cả năm;
giao dịch thật ngày 21/10/2026 không chứng minh coverage cả năm 2026. Không dùng preview
rỗng, tự sinh evidence, backdate clock hoặc seed coverage để tick checklist. Release **HOLD**;
dependency còn lại chỉ nằm trong handoff chung hiện có.

## 25. Chốt nghiệm thu FE bằng calendar test cô lập — 2026-10-21

**14/14 task, 56/56 tiêu chí** cho FE candidate trong môi trường kiểm thử cô lập.
[Evidence cuối](evidence/huy-six-service/2026-10-21-close/README.md) và JSON/screenshot đi kèm
đóng 09.2/.3, 11.2, 12.4. Kết luận trước đây gắn nghiệm thu FE với việc đợi hết năm production
được thay bằng kiểm thử calendar có phạm vi rõ; không xác nhận source-owner production exports.

Harness tạo nguồn/IDs qua public API, chạy payment/dispense/refund với PostgreSQL/RabbitMQ,
đợi projection rồi dừng cả bốn source writers. Năm journal giữ nguyên receipts và SHA-256:
CLINICAL 0 completed visits, LAB 0 completed tests, PHARMACY 2 evidence rows, CASH 3,
REFUND 2. Hai số 0 là kết quả thực của scenario hữu hạn: record chưa completed và không tạo
Lab order; không là proof production completeness hoặc paid Lab acceptance. ADMIN đối soát
và gọi approval qua Gateway → Support, exact-key approval retry giữ kết quả.

Chỉ Clock ứng dụng Report trong test được đưa tới 02/01/2027 Asia/Bangkok để đóng kỳ 2026.
Initializer ở `src/test`, nạp qua helper JAR riêng, vắng trong production Support JAR.
Giờ hệ thống/JWT/source không đổi, không seed DB, không đổi validation hoặc source gates.
Trả Clock về giờ thực: cùng daily request **404 REPORT_NOT_FOUND**; phục hồi Clock kiểm thử:
**200**. Đây là accepted **test** coverage, không gọi là coverage production hoặc exports do
source owners ký. Ràng buộc không backdate server ở mục 24 vẫn giữ.

Actual Core-issued ADMIN/MANAGER đọc daily và monthly bằng browser, 12 tháng, tháng 10 khớp
**1 Rx, 3 đơn vị thuốc, thu 160.000, hoàn 30.000, ròng 130.000 VND**. DOCTOR bị chặn; API403.
MANAGER không approve được; scope chưa approve trả404. Browser missing coverage không hiện
success results/số 0, focus lỗi; retry giữ deny và sửa scope phục hồi. Enter submit, mobile
390×844 Sáng/Tối không tràn ngang; đã trả theme Hệ thống, reset viewport và logout.

Workflow proof nối public API packaged harness với các browser Pharmacy/Notification đã lưu
và browser Report ở candidate cuối; không gọi mọi bước là một phiên browser E2E. Calendar
profile **1/1 PASS, exit0**, không lỗi/bỏ qua (737,548s gồm thời gian giữ browser).
FE source không đổi sau regression **595/595**, typecheck/lint/build PASS ở mục 24.
Tiến trình kiểm thử đã dừng. Nhánh Huy giữ nguyên, không commit/push.

Verdict: **FE READY FOR REVIEW / isolated acceptance complete**. Production rollout vẫn
**HOLD**, gates mặc định OFF. Handoff chung giữ riêng việc source-owner exports/completeness
của dữ liệu production và cutover; không dùng test journal này để bật production.

## 26. Review3 — tên bệnh nhân, bác sĩ, khoa trong scope Huy — 2026-10-22

Đợt thay đổi mới sau mốc 14/14, theo
[handoff identity](handoffs/HANDOFF-IDENTITY-CONSUMERS-DISPLAY-NAMES-REVIEW3.md).
Không lấy acceptance của candidate trước để đóng acceptance mới.

| Phần việc | Trạng thái |
|---|---|
| Audit Pharmacy/Report web và Pharmacy/Report mobile; đối chiếu Core/Gateway live DTO + role | DONE |
| Web Pharmacy: tên trên chi tiết đơn và preview tên trong form; ID/command giữ nguyên | CODE DONE |
| Web Report: tên khoa ở filter + phạm vi kết quả của bốn view | CODE DONE |
| Mobile Pharmacy: domain reader + Gateway adapter + provider theo phiên + UI tên | CODE DONE |
| Mobile Report | Chưa có identity consumer; workspace placeholder, không mở rộng nghiệp vụ |
| Gateway thật, keyboard/theme và ảnh Review3 trên candidate mới | PENDING, chưa dùng mock/build để đóng |

Các lookup dedupe theo context/phiên; lỗi/403/404 có fallback và giữ ID, không đoán tên.
PHARMACIST không được gọi public Patient/Staff/Department detail theo role matrix hiện tại;
UI báo giới hạn. Quyết định contract nếu cần tên cho dược sĩ nằm trong
[handoff chung Huy](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md), không tạo handoff mới.
Production gates vẫn OFF; không sửa backend/shared hoặc mở Surgery/Inpatient.

Verification của đợt này trên working tree nhánh Huy, baseline `acee395f`:

- Web: Pharmacy/Report **396/396 tests PASS** (30 files, gồm 18 tests identity mới);
  `pnpm typecheck`, `pnpm lint`, `pnpm build` PASS. Test cũ vẫn kiểm API/query/body và
  chỉ cập nhật hai assertion scope để kiểm UUID ở dòng phụ thay vì chuỗi render cũ.
- Mobile: full suite **306/306 PASS**; sau bổ sung tên trong hộp xác nhận kê đơn, chạy lại
  **87/87 tests liên quan PASS** (identity, workflow, validation, prescription, Gateway commands).
  Format 18 files, 0 changed. Test kiểm exact `hoTen/fullName/departmentName`, hai resource IDs,
  response mismatch, dedupe, 403/404/503, logout/new-session và late response.
- Candidate mobile cuối: `flutter analyze --no-pub` PASS, `flutter build web --no-pub`
  PASS (95,3s). Hộp xác nhận kê đơn dùng cùng resolver với form; ID/intent/key giữ nguyên.
- Typecheck/lint/build hoặc widget doubles không chứng minh quyền/seed/browser thật.
  Chưa có ảnh Review3 mới; giữ acceptance candidate mới PENDING.


## 27. Đồng bộ bộ chọn tên với master — 2026-10-22

Baseline đã fast-forward từ `3100d7b5` tới `95542f6c` (bao gồm PR #502).
Thay đổi source giới hạn ở hai feature Pharmacy/Report của Huy; sử dụng shared
ReferenceInput/ReferenceDirectory hiện có, không sửa shared source hoặc feature owner khác.

- Pharmacy: tìm/chọn bệnh nhân, khoa, bác sĩ theo tên; danh sách bác sĩ nhận scope khoa.
  Command tiếp tục gửi UUID server và giữ nguyên V1 intent/Idempotency-Key, V0 compatibility,
  frozen retry, vai trò và các điều kiện xuất thuốc do backend quyết định.
- Đổi bệnh nhân xóa recordId/careEpisodeId cũ; đổi khoa xóa doctorId. Tên gõ chưa chọn
  không trở thành authority ID. recordId/careEpisodeId/priceCode vẫn cần nguồn đã xác nhận.
- Report: cả Daily/Monthly/TopMedicines/Outpatient dùng bộ chọn khoa chung; URL và API
  vẫn nhận UUID. Phạm vi kết quả lấy từ response/query đã gửi, không từ draft đang sửa;
  giữ coverage/error/retry và gate ngoại trú hiện hành.
- Nhãn tên Pharmacy/Report dùng directory/cache của dashboard khi có. Consumer độc lập
  vẫn giữ public API fallback, kiểm tra ID và xử lý 403/404/unavailable. Không mở quyền
  PHARMACIST hoặc đổi các producer contract.
- Thêm regression tích hợp feature + shared picker cho exact V1 IDs, doctor scope,
  xóa phụ thuộc, search deny, query UUID của cả bốn view Report và lỗi missing data.

Nghiệm thu Gateway/browser/keyboard/theme/ảnh Review3 mới vẫn OPEN. Kiểm tra runtime
trong lượt này không thấy listener Gateway8080 hoặc web3000/3180/3182; in-app browser
không có phiên đăng nhập sẵn. Không dùng kết quả test/build để đóng acceptance thật.
Không tạo nhánh, commit hoặc push; code để người dùng review và đẩy lên.

Verification trên candidate local sau thay đổi:

- Pharmacy/Report focused: **402/402 PASS**; sau đó bổ sung ba ca selector query
  Monthly/TopMedicines/Outpatient và kiểm trong full suite bên dưới.
- Full frontend: **880/880 PASS, 94 files**, `pnpm exec vitest run --maxWorkers=2`.
  Lần chạy mặc định song song với build có ba ca Inpatient timeout5s; chạy riêng
  Inpatient **5/5 PASS**, rồi full suite với hai worker đạt toàn bộ. Không sửa source,
  test timeout/config hoặc validation của Inpatient để có kết quả này.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: PASS. Sau ba ca test bổ sung:
  typecheck và lint Pharmacy/Report PASS; không có source production thay đổi sau build.
- `git diff --check`: PASS. Không thay backend, shared source, mobile hoặc gate.

Verdict: **CODE + AUTOMATED VERIFICATION COMPLETE cho đồng bộ web Pharmacy/Report**;
Gateway/browser/ảnh Review3 mới vẫn OPEN và mobile giữ nguyên tiến độ riêng.
