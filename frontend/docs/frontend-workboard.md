# Frontend Workboard

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
