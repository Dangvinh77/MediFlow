# Frontend Workboard

## Huy — Pharmacy/Report theo mẫu, checkpoint 2026-10-16

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
