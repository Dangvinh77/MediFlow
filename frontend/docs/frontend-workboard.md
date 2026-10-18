# Frontend Workboard

## Vinh — outpatient UI implementation, 2026-10-18

Scope: appointment/medical-record/Lab and shared API, as authorized after the FE ownership audit.
This checkpoint supersedes older statements that these command controls have no UI implementation.
It does not close authenticated browser/Gateway acceptance.

- `FE-VINH-MVP-02`: check-in (ADMIN/NURSE), normal examination start (ADMIN/DOCTOR), waiting for
  server clearance, manual refresh, and server-created record lookup by exact appointmentId.
  Legacy arrival is hidden in outpatient UI mode. No emergency override UI.
- `FE-VINH-MVP-03`: `/records/[recordId]/lab-order` composes Clinical source-order creation and
  Lab creation with the exact returned UUID/episode/price fields. No feature-to-feature import,
  guessed order IDs, service-only browser calls, patient data in query strings or localStorage.
  Clinical create with unknown outcome is not blindly repeated. Lab errors preserve the order
  in the mounted workspace; navigation loses page memory and requires reconciliation.
- Record completion offers outpatient follow-up, prescription and other; requires an OPEN,
  appointment-backed record and diagnoses, explicit confirmation, backend error/field/correlation
  feedback. Backend enforces actor and appointment authority.
- Shared `api.post/postRaw/put` accept a third options argument `{ idempotencyKey }` only.
  Caller signatures remain compatible, bodyless calls stay bodyless and bearer stays session-owned.
  Huy's consumer implementation and producer/consumer behavioral acceptance remain open.
- Lab actions share synchronous submission locks and ignore late responses after unmount,
  handle 401 through login, and block blind repeats on unknown outcome. Manual read refresh
  resets the action workspace. Version-1 start requires server READY plus clearanceId.
- Server env `MEDIFLOW_OUTPATIENT_UI_ENABLED=true` exposes the new controls. Default false.
  Configure only after accepting the matching backend runtime; the UI flag does not activate
  backend modes/publication. Direct legacy record/Lab creation is replaced in enabled UI mode.
- `FE-VINH-MVP-01/04`: runtime role/HTTP/browser smoke remains OPEN. Clinical's public record DTO
  does not expose Rx/Lab attachment collections; no guessed read endpoint or Pharmacy import added.
- Final `pnpm build`: PASS, exit 0; compile, integrated TypeScript and route generation passed.
  Static peer review: GO for PR.
- Session verification is restricted by the user to `pnpm build`. No unit/component suite,
  standalone lint/typecheck, browser smoke or backend tests were run for this change.
  See [implementation record](vinh-outpatient-implementation-2026-10-18.md).


## Vinh — Clinical/Lab lists and forms, 2026-10-17

Source baseline `d80d23b2`; scope appointment/medical-record/Lab của Vinh.
Tiếp tục **FE-VINH-MVP-01/04**, chưa đóng toàn task hoặc mở V1 controls FE-02/03.

- Giữ nguyên các regression detail đã thêm: appointment detail remount theo `appointmentId`,
  medical-record detail remount theo `recordId`, và diagnosis của hồ sơ cũ không gắn vào hồ sơ mới.
  Mutation/401 sau unmount không đổi trang hoặc gọi callback cũ.
- Appointment và Lab list loại bỏ response/error/loading từ request cũ; medical-record lookup
  thêm stale/unmount guard. Pagination và retry dùng bộ lọc đã submit; validation UUID/episode,
  role visibility, retry, correlation ID và lỗi backend được kiểm tra.
- Create/edit appointment, record, diagnosis và Lab request khóa submit đồng bộ để ngăn gửi trùng,
  bỏ qua kết quả sau unmount, và giữ state edit gắn với `appointmentId`/`recordId` hiện tại.
  Server field errors và correlation ID vẫn hiển thị; API/role contracts, theme và V1 gates giữ nguyên.
- Red runs tái hiện stale query, late 401, duplicate mutation và navigation từ edit identity cũ.
  Focused suite đạt **57/57 tests, 8 files**; full suite `pnpm test -- --maxWorkers=1` đạt
  **363/363 tests, 45 files**. `pnpm typecheck`, `pnpm lint`, `pnpm build` đều PASS.
  Chỉ còn jsdom warning `Not implemented: navigation to another Document`; exit code vẫn 0.
- Follow-up review found a missing type-only `LabTestDTO` import in the Lab form test. The prior
  typecheck log predated its removal; a fresh run reproduced TS2304 at both deferred-promise types.
  Restored the import and reran `pnpm typecheck`, `pnpm build`, and the focused Lab form suite
  (4/4 PASS). `tsconfig.json` includes `**/*.ts` and `**/*.tsx` and excludes only `node_modules`.
- Đây là kiểm thử với API/session mocks; authenticated browser/Gateway smoke còn mở.
  Logs ở `%TEMP%/mediflow-vinh-frontend-continuation-{tests,typecheck,lint,build}.log`.

## Huy — Report ngoại trú MVP, rà soát mới nhất 2026-10-17

- `R-MVP-01`: **4/4 FE local**, release OPEN. Route `/reports/outpatient` dùng đúng API
  daily range/monthly year và DTO coverage/currency/zone/periods của backend mới.
  Theo mẫu/theme hiện có; URL filters, Back/Forward, validation/focus, stale/unmount guards,
  401/403/correlation/retry; accepted zero khác unavailable, netCash lấy nguyên response.
- Entry và direct route mặc định đóng, không gọi API. Server chỉ mở UI khi
  `MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true`, sau runtime/coverage/Gateway acceptance.
  Không bật backend flags, không fallback legacy khi endpoint chưa sẵn sàng.
- Verification: typecheck/lint/build PASS; full suite **291/291 tests, 36 files**.
  Browser production build với Gateway fixture kiểm gate OFF/ON, ngày/tháng, 12 kỳ/năm,
  Back/Forward, thiếu coverage; desktop Sáng/mobile Tối 360px không tràn ngang.
  Các lượt kiểm tra này chạy ngày 2026-10-16; ngày 2026-10-17 rà soát source và phụ thuộc tiếp theo.
  Đây là local evidence; signed-role/real-source/Support acceptance còn mở.
- Chi tiết [plan mục 15](huy-frontend-implementation-plan.md#15-checkpoint-report-ngoại-trú-theo-backend-mvp--2026-10-16).
  Đây là task MVP bổ sung; giữ mẫu số lịch sử 11/30 và 50/123, không mở Surgery/full V2.
  Phụ thuộc dùng [handoff chung](../../docs/handoffs/HANDOFF-HUY-MVP.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).
  Thay đổi batch mới chưa commit/push.
- P-08 còn cần shared API hỗ trợ header `Idempotency-Key`, cùng source/paid/activation acceptance.
  DTO đọc đơn hiện chưa xuất metadata V1; không suy diễn fields hoặc tự mở public mutation.
  Yêu cầu shared owner và acceptance được ghi tiếp trong cùng handoff Huy.

## Vinh — Lab read/async regression, 2026-10-16

**IMPLEMENT**, actor Vinh; source paths chỉ `src/features/lab/components/LabDetail.tsx` và
`LabDetail.test.tsx`. Không đụng shared frame/config, foreign feature/route hoặc backend production.
Task: một phần **FE-VINH-MVP-01/04 / FE-LAB-04**, theo component lifecycle/permission coverage.

- Cô lập detail state theo testId: phản hồi start của xét nghiệm cũ sau khi chuyển sang xét nghiệm
  khác không còn làm mất detail hiện tại và kẹt màn hình loading. Tái hiện bằng failing regression
  trước fix; remount detail workspace theo identity để callback cũ chỉ thuộc instance đã unmount.
- Thêm 15 component tests: loading, retry chủ động/correlation, 401/403/404, stale read/401,
  stale mutation, đổi hồ sơ xóa cancel draft, quyền NURSE/DOCTOR/LAB_TECH/ADMIN, terminal states,
  payment/lifecycle riêng biệt và giữ nguyên textual results. Mock API/session; không paid E2E.
- Backend dependencies đã refresh: Lộc L1/L2 đã có; Lộc L3/L4 và Hoàng Anh G1 còn mở tại
  [handoff backend](../../docs/handoffs/HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md).
  Không mở lại Lab-order/Rx producer implementation; FE-02/03 V1 controls vẫn chờ runtime acceptance.
- Verification final: `pnpm typecheck`, `pnpm lint`, `pnpm build` đều exit 0;
  `pnpm test -- --maxWorkers=2`: **277/277 tests, 35 files**, không failures/skips;
  focused Lab **19/19** gồm 15 component + 4 API tests. Red regression trước fix: 14 pass,
  1 fail do old start response làm mất current detail; sau fix đạt.
  Full suite có một jsdom warning `Not implemented: navigation to another Document`, exit 0.
  Logs ở `%TEMP%/vinh-frontend-closeout-{tests,typecheck,lint,build}.log` và
  `%TEMP%/vinh-lab-detail-{red,green}.log`. Chưa chạy signed-role browser/Gateway smoke;
  FE-VINH-MVP-01/04 và FE-LAB-04 vẫn IN PROGRESS, không đánh dấu toàn task DONE.


## Huy — Pharmacy/Report theo mẫu, checkpoint trước 2026-10-16

Owner: Huy (`LQHuy0210`). Scope IMPLEMENT: Pharmacy/Report features và tài liệu tương ứng;
shared frame chỉ trong những paths được giao ở mục kế tiếp. Không sửa foreign feature/backend.

- Đóng thêm FE local: **R-01, R-02, R-04, R-05, P-02, P-03, P-05, P-07**.
  Report ba view dùng URL/submitted scope, validation và request/error lifecycle thống nhất.
  Drug detail/create/stock dùng theme/primitives sẵn có, khóa mutation trùng, focus lỗi,
  cập nhật tồn bằng DTO server và refetch sau conflict/unknown stock outcome.
  Outbox là inline confirmation ADMIN, Escape/return-focus, kết quả theo `replayed`.
  Form kê đơn dùng Field/Button, validation và lỗi server theo dòng, khóa submit trùng và giữ wire V0.
- **P-06 xong 4/4 subtask local**, gồm inline hủy/xuất thuốc, focus/Escape/return-focus,
  duplicate-submit lock và xử lý conflict/unknown outcome; task chính còn real-role smoke GW-01.
- [Plan Huy, checkpoint hiện tại](huy-frontend-implementation-plan.md#14-checkpoint-kê-đơn-và-terminal-actions--2026-10-16):
  **11/30 task chính, 50/123 subtasks FE local**. Mẫu số lịch sử có Surgery/full V2 deferred,
  không dùng tỷ lệ đó để báo MVP hoàn thành. P-08 cần đối chiếu activation ngoại trú trước khi nối UI;
  các activation/release gates chưa đóng.
- Source cuối: `pnpm typecheck`, `pnpm lint`, `pnpm build` đều **PASS**;
  `pnpm test -- --maxWorkers=2` **262/262 tests, 34 files**; thêm 51 tests kê đơn/terminal actions.
  Build cuối đạt sau khi chuyển log kiểm tra ra ngoài `.next` để tránh EBUSY khi dọn output.
  Lượt mặc định lịch sử ở mục 13 chạy cùng lint có hai lỗi CreateAdmissionForm của Inpatient
  (timeout/nhập form), 209/211; nhóm flaky này đã có ở checkpoint 2026-10-15.
  Không sửa Inpatient hoặc shared test config để làm lượt chạy đó xanh.
- Browser fixture smoke: Daily submit/Back/Forward, Top deep link, outbox confirmation/replay,
  drug detail/create/adjust, focus lỗi/Escape và bố cục 360×800 Sáng/Tối đạt.
  Header nhỏ đã hết chữ phụ bị cắt; các màn hình đã đo không tràn ngang.
  Batch mới kiểm kê đơn → ID server, hủy → releasedReservations, xuất → terminal read;
  xác nhận bằng bàn phím và bố cục desktop/mobile dùng cùng theme Sáng/Tối/Hệ thống.
  Fixture/session cô lập, không dùng DB/token thật, đã đăng xuất và dừng server kiểm tra.
- Phụ thuộc runtime/Gateway/source/paid/Support/coverage gom vào
  [một handoff Huy](../../docs/handoffs/HANDOFF-HUY-MVP.md#6-frontend-mvp--gate-tích-hợp-còn-lại-2026-10-16).
  Local UI/test không thay signed-role hoặc sáu-app backend acceptance. Chưa commit/push.

## Khung dùng chung 6 service — 2026-10-16

Theo yêu cầu trực tiếp của người dùng, Huy được giao áp dụng mẫu đã chốt vào frame
frontend toàn hệ thống. Phạm vi shared của lượt này: `src/app/page.tsx`,
`src/components/layout/{DashboardSidebar,DashboardHeader,PageShell}.tsx`,
`src/components/ui/DataTableShell.tsx`, `src/components/theme/ThemeToggle.tsx`
và hai test cạnh Sidebar/ThemeToggle. Không thay API/DTO hay nghiệp vụ feature của owner khác.

- Dùng mẫu tại `designs/huy-approved-ui.html` cho header, sidebar, tiêu đề trang,
  panel và bộ chọn theme; giữ semantic tokens và ThemeProvider Light/Dark/System hiện có.
- Sáu nhóm: Core → Tổ chức & bệnh nhân; Clinical → Khám ngoại trú; Lab → Xét nghiệm;
  Pharmacy → Dược; Billing → Viện phí; Support → Báo cáo & thông báo.
  Chín logical routes vẫn riêng biệt; menu lọc theo role hiện có, active cả route con.
- Bỏ Nội trú khỏi menu MVP, Surgery tiếp tục không xuất hiện; giữ nguyên source deferred.
- Trang đầu giới thiệu sáu nhóm nghiệp vụ, bỏ hướng dẫn kỹ thuật và tài khoản demo cũ.
- Verification batch frame ban đầu: typecheck/lint/build đạt; Vitest **115/115, 25 files** đạt.
  Browser kiểm tra trang đầu và chọn theme sáng/tối đạt. Chưa smoke dashboard có signed session,
  mobile drawer hoặc nghiệp vụ liên thông qua Gateway; không coi khung này là nghiệm thu backend.


## Ưu tiên hiện tại — 2026-10-15

Phần route-state bên dưới là baseline 2026-10-09, không phải bằng chứng đã chạy với backend
MVP mới. Scope canonical đã chuyển về ngoại trú; Inpatient/Surgery deferred. Không tiếp tục
FE-INPATIENT-03 để đóng MVP. Không xóa code/UI lịch sử trong lượt cập nhật workboard này.

Backend source lock, việc Vinh còn phải làm và handoffs cho các owner nằm tại
[Vinh backend closeout](../../docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md).
Giữ một registry cho backend gaps; frontend không tự suy contract thay producer.

| Thứ tự | Việc tiếp theo của Vinh | Điều kiện |
|---|---|---|
| 1 | `FE-VINH-MVP-01`: đối chiếu appointment/record/Lab DTO/API/error và role states với controllers hiện tại; chuẩn bị shared UX | `IN PROGRESS`: đã bổ sung API/types và kiểm thử biên HTTP; shared UX/role states chưa nghiệm thu qua runtime |
| 2 | `FE-VINH-MVP-02`: check-in → start exam → completion ngoại trú | Sau exact EXAM Gateway/Billing slice và gates được nghiệm thu; chưa thanh toán phải hiện blocked |
| 3 | `FE-VINH-MVP-03`: tạo Clinical Lab order → tạo Lab bằng returned sourceOrderId → start/results | Sau H-VINH-01/03 và Lab slice; không browser-generated order ID hoặc human internal lookup |
| 4 | `FE-VINH-MVP-04`: authenticated role/error/loading/retry smoke qua Gateway; tích hợp trạng thái Rx khi contract có | Rx attachment phụ thuộc H-VINH-02 + Clinical consumer; không fallback V0 trong MVP |

Đợt chuẩn bị contract 2026-10-15 đã bổ sung `appointmentApi.checkIn/startExam`,
`medicalRecordApi.complete/createLabOrder`, DTO source order và kiểu request emergency audit
đúng backend Clinical/Lab. Mặc định start gửi `emergencyOverride: null`; chưa có UI cấp override.
Các API dùng cùng gateway wrapper; giữ nguyên sourceOrderId do Clinical trả về, không gọi internal
context từ trình duyệt, không tự thử lại mutation hoặc fallback V0. Kiểm thử HTTP dùng phản hồi
giả lập, không phải bằng chứng backend/Gateway đã cấp quyền hay luồng EXAM/LAB đã chạy liên thông.
Chỉ expose mutations đúng mode runtime đã nghiệm thu; route-state DONE bên dưới không bật V1 thay backend.

Verification đợt contract preparation: `pnpm typecheck`, `pnpm lint`, `pnpm build` đạt;
`pnpm test -- --maxWorkers=2` đạt **104/104 tests, 21 files**, gồm 11 test API mới Clinical/Lab.
Lượt `pnpm test` mặc định chạy đồng thời với typecheck/lint có 2 lỗi ở bộ
`CreateAdmissionForm.test.tsx` (timeout và assertion sau khi nhập form bị dang dở);
chạy riêng bộ Inpatient đạt 5/5, chạy lại toàn bộ với 2 workers đạt. Chưa thay test/config
Inpatient vì đây là scope deferred; cần giữ ghi nhận độ ổn định của lượt chạy mặc định.
API/types đã được review đối chiếu backend; chưa chạy authenticated browser smoke.

> Audited 2026-10-09 against the live Inpatient controller and implemented on
> `codex/inpatient-create-command`. Ownership rules remain in
> [`docs/ai/15-frontend-ownership.md`](../../docs/ai/15-frontend-ownership.md).

## Shipped route state

| Context | Routes and workflow | State |
|---|---|---|
| Shared | Responsive role-filtered sidebar, compact header, field/button/table primitives | `DONE` |
| Clinical | Appointment list/detail/create/edit plus legacy arrival/cancel; record lookup/detail/create/edit/diagnosis | `DONE` for always-on controllers |
| Lab | Queue/detail/create plus start, result entry and cancel with live role/status guards | `DONE` |
| Inpatient | Admission list/detail, admission create, and bed list with exact Vietnamese wire names | `IN PROGRESS` command workspace |
| Organization | Department and staff list/detail/create/update; staff transfer; account create/status | `DONE` |
| Patient | List/detail/create/update/delete with immutable identity number | `DONE` |
| Pharmacy | Drug, stock, prescription, dispense/cancel and ADMIN outbox replay workflows | `DONE` |
| Billing | Patient invoice lookup/detail/create/payment (V0); V1 selected-charge ledger payment, revenue-by-department, outbox replay | `DONE`, see `frontend/docs/services/billing.md` |
| Notification | Patient history, secured detail and ADMIN manual send without exposing recipient PII | `DONE` |
| Report | Daily, monthly and top-medicine reports | `DONE` for always-on controllers |

## Deliberately unavailable

- Clinical check-in, examination start, record completion and admission referral remain hidden
  because `mediflow.features.care-finance-v2` defaults to `false` in Clinical and Docker runtime.
- Inpatient deposit, top-up, settlement and Surgery composition remain blocked on Billing/Surgery
  activation contracts. The read workspace never derives financial clearance in the browser.
- Report operational and Surgery snapshots remain hidden because Report Care/Finance V2 defaults
  to `false`.
- Surgery has no navigation entry while its Gateway route is default-off and its production
  authority handoff is open.
- Refund, notification read-state/retry and other commands absent from live controllers are not
  represented as UI controls.
- Billing's prescription-adjustment refund-due view is not exposed: the controller is live but the
  Gateway has no allowlist rule for it yet (`docs/handoffs/HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md`).
  Its Surgery-cancellation counterpart stays unavailable because Surgery itself is deferred.

## Next tasks tại baseline 2026-10-09 (historical; ưu tiên hiện tại ở trên)

1. Continue `FE-INPATIENT-03` with bed assignment as the next bounded transition after reconfirming
   its controller roles, request DTO, status precondition and conflict codes.
2. Extend the shared Vitest harness across each service for role denial, validation, terminal
   states and API error/correlation-ID rendering.
3. Enable and smoke-test the Care/Finance V2 services through Gateway, then expose the already
   specified Clinical and Report command surfaces as one bounded batch.
4. Run an authenticated browser smoke for each role against Docker Compose before release.

Every new UI mutation must still confirm the owner controller, request/response DTO, Gateway
method/role matrix and error codes. Missing producer behavior becomes a handoff, never a guessed
frontend contract.
