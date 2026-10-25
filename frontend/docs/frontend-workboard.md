# Frontend Workboard

## Pharmacy / Report usability recheck — 2026-10-25

Catalogue name/ingredient search now combines with server-side stock and expiry filters;
counts and pages use the complete filtered result. Prescription listing retains patient/status/page
when returning from detail. Prescribing can page through the catalogue without losing selected
lines and marks expired/empty stock, expiry dates and provisional totals. Legacy reports load the
current valid period, provide date/month presets and clear department selection, and warn when a
draft filter differs from displayed results. A visible source limitation prevents interpreting
legacy zero-fill as proof of no outpatient activity. Catalogue/detail and legacy Report reads are
no-store. Backend roles, final price/stock authority and outpatient acceptance gates are preserved.

Verification: 426/426 focused Pharmacy/Report UI tests across 34 files, TypeScript, ESLint,
production Docker builds; a subsequent ReportWorkspace notice recheck passes 7/7. Pharmacy backend
874 tests pass with no failures/errors/skips, including PostgreSQL ingredient/literal search and
filter/count/page/date-boundary checks. Live Gateway checks confirm all six catalogue filters,
ingredient search, literal wildcard handling, no-store headers and allowed/denied roles. Browser
checks cover PHARMACIST ingredient search/stock filter and Back retaining ACTIVE status, plus ADMIN
current-month auto-load, prior-month draft warning/submission, daily and medicine views.
The two prepared dispense/cancel demo prescriptions remain ACTIVE/PENDING; no business data was
changed during runtime verification.
Report backend suite completed successfully: 1,444 tests, zero failures/errors/skips, including
PostgreSQL/RabbitMQ integration and legacy response-cache/role checks.

Open dependencies remain explicit: pharmacist doctor/department reference names require the
existing Core/Gateway ownership handoff; complete outpatient Report coverage and legacy currency
metadata are not established by legacy HTTP 200/zero-filled responses.

## Pharmacy prescription usability — 2026-10-25

Prescription browsing now loads a list without an identifier, supports patient-name selection,
status filters and pagination, and links each result to its detail. Outpatient creation reads
the authoritative prescribing context through Pharmacy after choosing a patient and record;
visit and price identifiers are no longer free-text inputs. Backend Pharmacy and the two
task-authorized Gateway read routes implement these contracts. The follow-up user authorization
adds a minimal Core patient directory and its two Gateway rules for ADMIN/DOCTOR/PHARMACIST.
It closes pharmacist patient-name search/display; staff/department read permissions are unchanged.

Verification after the patient-name follow-up: Pharmacy UI tests 278/278 across 22 files,
TypeScript, ESLint and the Docker production build pass. Core 334/334 and Gateway 1561/1561
pass; Core integration tests use `-Dapi.version=1.40` for this machine's Docker compatibility.
The earlier unchanged Pharmacy backend verification passed 871/871.
Docker production build and public-Gateway checks pass for paged listing, filters, empty
results, authoritative context, and denied roles. Browser verification against the refreshed
Docker UI shows all three existing prescriptions and successful patient/record context selection.
The follow-up browser check signs in as PHARMACIST, searches Nguyễn Văn An Demo, selects the
12/04/1986 birth date and displays all three matching prescriptions with readable patient names.
Public-Gateway checks pass for ADMIN/DOCTOR/PHARMACIST and verify exactly three directory fields,
empty results, denied CASHIER access and denied PHARMACIST full-profile access. Core, Gateway and
frontend containers are healthy; the two prepared dispense/cancel prescriptions remain untouched.

## User-facing reference display — 2026-10-24

Pharmacy, Report and Notification now use names and plain-language prompts as the main display.
Shared `ReferenceText` no longer adds a technical-information control to every reference by
default; explicit diagnostic callers may still opt in. Exact request IDs and access rules are
unchanged. Pharmacy drug/prescription detail groups support references in a closed disclosure;
drug names no longer repeat their UUID in prescription lines or top-medicine reports.

Pharmacy maintenance moves from the main business tabs into a closed ADMIN-only administration
menu, with plain-language recovery instructions. The drug catalogue no longer embeds an unrelated
prescription lookup. Prescription creation selects an existing medical record through the shared
directory scoped to the selected patient, and clears dependent selections when their source changes.
Directory results open as a dropdown without stretching form grids, and can be dismissed with
Escape or the close action; pending responses cannot reopen a dismissed dropdown.

At this checkpoint public prescription search, an authoritative visit/price picker, and name
access for PHARMACIST remained contract gaps. The 2026-10-25 update above closes the first two.
No identifier, business code, permission or lookup endpoint is fabricated.
See the existing [identity handoff](handoffs/HANDOFF-IDENTITY-CONSUMERS-DISPLAY-NAMES-REVIEW3.md).

Verification: all frontend tests **952/952** across 101 files, TypeScript, ESLint and the
Docker production build pass. Local Docker browser checks cover Pharmacy navigation, patient
selection and patient-scoped record search, plus the Report department picker; no clinical
command is submitted during these read-only checks.
Report filters also align actions at the bottom on narrow grids; their final regression run
passes **137/137** after that layout adjustment.

## Hoàng Anh — Organization/Patient/Gateway usability review, 2026-10-23

User-approved follow-up adds server-paginated staff search (accent-insensitive name/title/unit,
ADMIN-only CCCD predicate), active staff picker queries and local unit name/abbreviation search.
Doctor forms require a license. Account detail resolves linked names with independent retries;
the app composes Patient lookup. Staff personal profile uses the new signed-owner minimal unit
endpoint, enforced by Core and admitted by the Gateway route-role fixture.

Shared HTTP calls time out after 15 seconds including body reads, retaining unknown-write
semantics without timeout retries. Login starts with empty credentials. Patient unknown saves
offer read-only exact-identity reconciliation with full normalized field comparison; absence,
different fields or read errors preserve the locked draft. Dirty Org/Patient forms guard browser
Back, links and unload.

Source fixtures: Core StaffControllerContractTest/OwnStaffDepartmentControllerTest,
OrganizationServiceIntegrationTest; Gateway route-role-contracts.csv; FE API, picker, form,
profile, directory, linked-name and history-hook tests. Core/Gateway must restart together;
no schema migration is added. Signed-in browser E2E and running-service rollout are not claimed.
Verification: focused Org/Patient/auth **217/217**, TypeScript, ESLint and production build pass.
The full FE run with two workers reports **948 passed / 1 failed** across 101 files. The failure
is the existing Pharmacy PrescriptionActions assertion at line 150 (expects the async detail
reload to have already called get twice; observed once). Its isolated rerun passes **24/24**.
No Pharmacy source/test is changed and the broad run is not reported as fully green. An earlier
unrestricted parallel run was stopped after widespread test timeouts under build/Maven load.
Core full-suite plus the corrected-fixture integration rerun reports **325 tests** across
52 current suites, zero failures/errors/skips; Gateway reports **1,525 tests** across 14 current
suites, zero failures/errors/skips. Old leftover XML reports are excluded from these totals.

## Hoàng Anh — mở rộng chi tiết khoa/phòng/nhân sự, 2026-10-22

User-approved Organization scope adds optional business/contact fields for departments and
personal/employment fields for staff through existing endpoints (nested request profile, flat
response). Core V11 and adapters persist them; legacy omitted-profile updates preserve values.
Department details include a named head and paginated members with counts and Action/Detail;
staff details resolve the unit name. Forms load/create/edit enrichment and retain privacy rules.
Office head selection now accepts active same-office employees; clinical heads remain doctors.
Fresh seed includes enrichment; receipts still preserve all existing rows and edits.

Verification: Organization/profile **117/117**, TypeScript, ESLint and production build pass.
Core **300 tests** pass across the full Docker suite and added HTTP contract checks,
including V11 persistence, omission/clear semantics,
head eligibility, personal-field redaction, idempotency and seed restart safety, passes.
HTTP nested-profile validation/serialization checks and final Core jar packaging also pass.
No signed-in browser acceptance or running-service restart is claimed.

## Hoàng Anh — nhãn trang chi tiết phòng, 2026-10-22

Department detail now derives its page heading, breadcrumb, section labels, head-of-unit
link and success notice from the loaded departmentType. ADMINISTRATIVE uses **phòng**;
CLINICAL/PARACLINICAL use **khoa**. Loading/invalid-ID states use neutral **khoa/phòng**.
The detail component owns its loaded page heading to avoid duplicate requests or trusting
a URL label. OrganizationDetails **16/16** and TypeScript pass.

## Hoàng Anh — bỏ các khối Thông tin hồ sơ, 2026-10-22

User clarification targets the metadata sections rather than the profile navigation link.
Removed **Thông tin hồ sơ** blocks from DepartmentDetail (Khoa/Phòng), StaffDetail and
PatientDetail, including their UUID/audit timestamp rows. A source-wide TSX search finds
no remaining section with this title. Business information and actions remain available.
OrganizationDetails/PatientDetail regression tests **22/22** and TypeScript pass.

## Hoàng Anh — bỏ mục hồ sơ khỏi trang quản trị admin, 2026-10-22

User-assigned Organization change removes the **Xem hồ sơ cá nhân** link from the ADMIN
management page. The separate profile route and header entry remain available.
ProfileWorkspace regression suite **19/19** and TypeScript pass.

## Hoàng Anh — cột Action / Detail cho Organization và Patient, 2026-10-22

User confirmed scope is Organization and Patient only. DepartmentList (both Khoa and
Phòng), StaffTable and PatientTable now render names as text and put the detail link
in a final **Action** column with a **Detail** button. The staff department name is
also text; account lists already follow this convention. Existing detail routes,
permissions, filters, pagination and empty/error/retry behavior remain.
Updated list tests use the explicit Detail accessible names. OrganizationLists and
PatientTable **14/14** pass; TypeScript passes. No signed-in browser acceptance claimed.

## Hoàng Anh — chọn danh sách Khoa / Phòng / Nhân sự, 2026-10-22

User-assigned Organization IMPLEMENT UI change: three responsive selection tiles show
one corresponding directory below, defaulting to Khoa. Khoa includes CLINICAL and
PARACLINICAL; Phòng includes ADMINISTRATIVE, using the existing departmentType contract
rather than parsing names. Selected tiles expose pressed state and keyboard focus.
Department data remains available for all staff-filter options when switching to Nhân sự.
Existing detail links, status, retry and staff paging remain. No new backend API.
TypeScript and ESLint pass; OrganizationLists **8/8** including category switching pass.
Signed-in browser verification was not performed.

## Hoàng Anh — tìm kiếm nhân viên liên kết, 2026-10-22

User-assigned Organization IMPLEMENT change: StaffPicker now exposes the same search input
pattern as PatientPicker. Search matches name, Vietnamese job title and department name,
ignoring case and Vietnamese accents. The current staff API has no keyword contract, so
nonempty searches read all filtered staff pages (100 per request) and paginate matching
active staff locally in groups of 20. Empty searches retain existing server pagination.
Later-page errors fail the whole search with retry, never present partial results; session
changes stop further page requests. No persistent directory cache or new backend API.
TypeScript and ESLint pass; real picker tests **4/4** cover later-page matching, UUID
selection, title/department search, inactive exclusion and failed-search retry.
Signed-in browser acceptance was not performed; large directories may need server search later.

## Hoàng Anh — nhãn bộ chọn nhân viên, 2026-10-22

User-assigned IMPLEMENT change in Organization `StaffPicker.tsx`: options now show
**Họ tên · Chức danh · Khoa/phòng**, using the existing staff page and department list
APIs. All departments are loaded so references to inactive departments retain their name.
Missing department references display **Chưa có thông tin khoa/phòng**; request failures
use the existing picker retry state. Selection still sends the original staff UUID.
TypeScript and ESLint pass; AccountAdmin/DepartmentForm regression tests **14/14** pass.
These form tests mock the picker; signed-in browser verification was not performed.

## Hoàng Anh — quản lý tài khoản và hồ sơ cá nhân, 2026-10-22

User-assigned Organization/Patient/Gateway work. Account list now has a **Tạo tài khoản**
link beside its heading and an **Action / Detail** column. Creation lives at
`/organization/accounts/new`; account detail at `/organization/accounts/[accountId]`
contains password reset and active/inactive actions. ADMIN detail uses the new private,
password-free Organization endpoint through Gateway.

Staff role is derived from the selected staff member's job title, including
`ADMINISTRATIVE → ADMIN` per the user's answer; there is no manual role selector.
Patient account creation retains the fixed PATIENT role. Existing active accounts for
the same person require explicit confirmation before replacement. Core serializes owner
writes and atomically deactivates previous accounts with creation/activation; cancellation,
transaction rollback and idempotent replay preserve the existing account state.
Unknown creation outcomes retain the original command key for safe retry.

Personal profiles no longer display **Định danh tài khoản** or **Thông tin kỹ thuật**.
Inactive accounts cannot log in or refresh; previously issued access tokens retain their
existing expiry semantics, which the detail page explains.

Verification: Organization/profile frontend suite **106/106 / 13 files**; subsequent
focused profile/account checks and final creation recovery tests pass. TypeScript,
ESLint and production build pass. Core **293/293 / 49 files**, including **22/22**
Organization PostgreSQL integration tests for concurrent creation/reactivation, replay
and rollback; Gateway **1527/1527 / 15 files**. Signed-in browser end-to-end acceptance
is not claimed: the browser could not connect to the local frontend and live Gateway
was not running during that check.

## Hoàng Anh — tách quản trị và hồ sơ cá nhân, 2026-10-22

User-assigned Organization/Patient/Gateway work; explicit delegation also covers
`src/components/layout/DashboardHeader.tsx` and its test to add the personal-profile link.
ADMIN retains management at `/organization`; other human roles land on their own read-only
profile. All human roles can open `/organization/profile` and the existing change-password
form. MANAGER/DOCTOR/NURSE keep the work directory at `/organization/directory`.

Existing detail endpoints are reused. Core exposes full staff fields only to ADMIN or the
signed owner; the new staff self-read roles do not gain directory/foreign-detail/write access.
PATIENT may read only the signed patient UUID, with a use-case ownership check before storage.
The profile never fetches a private directory to filter locally. Unlinked identities, retry,
expired sessions and stale responses have explicit handling.

Verification: focused Organization/Patient/profile/header **177/177 / 20 files**, with
`--maxWorkers=2`; TypeScript, lint and production build pass. One existing PatientTable test
timed out during the earlier concurrent build/test run and passed with two workers.
Core non-Docker suite and final **55/55** focused web/use-case tests pass; Gateway suite and
Core/Gateway JAR packaging pass. Full Core integration remains blocked by the unavailable
Docker engine (four Testcontainers classes); no signed live Gateway/browser acceptance claimed.

## Hiển thị tên và bộ chọn tham chiếu — 2026-10-22

Task `FE-REF-01` (Vinh shared/Clinical/Lab, Lộc Billing/Notification): đã có local
implementation tìm/chọn Patient, bác sĩ, khoa, hồ sơ/lịch hẹn bằng public read API;
request vẫn gửi ID từ server. Đổi khoa xóa bác sĩ, đổi bệnh nhân xóa hồ sơ/lịch hẹn
phụ thuộc; kết quả tìm kiếm cũ và nhãn phiên cũ không được dùng lại.

Bảng/chi tiết hiển thị tên hoặc nhãn nghiệp vụ/ngày khi có dữ liệu. UUID để trong
“Thông tin kỹ thuật” đóng mặc định, mở lại khi cần đối soát. Không suy tên khi thiếu
quyền hoặc tải thất bại. Cache chỉ sống trong dashboard session.

**Còn mở:** các vai trò chưa có Patient/Organization read dùng fallback thu gọn;
financial source/event/transaction, đợt chăm sóc và đơn thuốc chưa có đủ lookup
contract. [Backend handoff](../../docs/handoffs/HANDOFF-HA-FRONTEND-REFERENCE-DIRECTORY-2026-10-22.md).
Staff và lịch hẹn lọc trong từng trang vì API không có keyword; UI giải thích và có
Trang trước/Trang sau. Chưa đánh DONE toàn bộ selector hoặc signed-role runtime.

Kiểm chứng của đợt sửa này ghi tại
[implementation plan](../../docs/superpowers/plans/2026-10-22-frontend-human-readable-references.md).
Các task kiểm thử rộng và resend/read-state dưới đây vẫn là việc riêng.

## Task hiện hành — Vinh/Lộc frontend, đồng bộ 2026-10-22

Áp dụng cập nhật bàn giao Billing/Notification ngày 22/10 bên dưới và
[ownership canonical](../../docs/ai/15-frontend-ownership.md). Bảng này thay các trạng thái
OPEN/IN PROGRESS lịch sử của Vinh bên dưới trong phạm vi đã có evidence 20/10;
không biến kết quả kiểm thử cũ thành kết quả trên revision hiện tại.

| Dev | Task | Trạng thái hiện hành / việc tiếp theo | Điều kiện đóng hoặc giới hạn |
|---|---|---|---|
| Vinh | `FE-VINH-MVP-01`, `FE-CLINICAL-04`, `FE-LAB-04` | DONE trong phạm vi DTO/API, role/session và regression/runtime ngoại trú đã nghiệm thu 20/10 | [Evidence](evidence/vinh-loc/2026-10-20/README.md); enrichment/selectors ngoài contract chưa được chứng nhận |
| Vinh | `FE-VINH-MVP-02`, `FE-CLINICAL-05` | DONE cho UI check-in → start → completion có gate và matched opt-in runtime | Check-in/start có API harness evidence; không ghi là đã thao tác browser hai bước này; rollout production còn OFF |
| Vinh | `FE-VINH-MVP-03` | DONE cho Clinical source order → Lab → thanh toán → clearance → kết quả | Dùng nguyên sourceOrderId từ server và clearance V1; không mở lại producer/UI đã hoàn tất |
| Vinh | `FE-VINH-MVP-04` | DONE cho role/session/error và signed Gateway/browser scope đã ghi nhận | Rx attachment/enrichment ngoài public contract và provider/release acceptance là dependency riêng |
| Lộc | `FE-BILLING-04` | Đã có suite riêng RevenueReport/InvoiceForm/OutboxReplayForm trong source hiện tại; đối chiếu acceptance thay vì tạo lại test | Test quyền truy cập, validation, loading/empty/error/retry và response/correlation phù hợp từng flow; giữ regression duplicate/uncertain command; chạy focused Billing và các gate frontend trước khi đánh DONE |
| Lộc | `FE-BILLING-01/02/03/05/06/07/08` | DONE cho implementation MVP hiện có; tiếp nhận bảo trì | Dùng duy nhất RefundDueLookup trên /billing; không dựng lại route /billing/prescription-adjustments đã bỏ; Surgery deferred |
| Lộc → Vinh backend | `FE-NOTIFICATION-03` | BLOCKED — thiếu contract resend/read-state | Lộc chỉ tiếp tục FE khi có endpoint/DTO/roles/error và semantics retry/idempotency được backend owner Vinh chốt; đây không tự giao việc thêm API backend |
| Lộc | `FE-NOTIFICATION-01/02/04` | DONE cho UI/tests và signed IN_APP scope đã có evidence | HF6-14 đã có [closeout 21/10](evidence/huy-six-service/2026-10-21-close/README.md); không giao lại phần refund intake đã nghiệm thu; EMAIL/SMS provider và rollout vẫn riêng |

Shared frontend tiếp tục thuộc Vinh. Billing/Notification frontend thuộc Lộc; việc Vinh tiếp nhận
hai backend tương ứng từ 18/10 không chuyển ownership frontend. Handoff shared → Surgery vẫn
deferred, không đưa thành việc chặn MVP. Các số test và checklist ở các checkpoint dưới đây là
lịch sử theo ngày, không cộng dồn để kết luận task hiện tại DONE.

## Lộc — resumes Billing/Notification ownership, 2026-10-22

Vinh's 2026-10-20/21 closeout (`2026-10-20-vinh-loc-frontend-closeout.md`,
`2026-10-21-ha-loc-web-mobile-completion.md`) was a `DELEGATED` task, not a permanent ownership
change; `frontend/src/features/billing/AGENTS.md` still names Lộc canonical owner. Lộc resumes
active `IMPLEMENT` work on Billing and Notification from this date.

Merged master into `feat/billing-prescription-adjustment-refund-due`. Resolved the refund-due
duplicate: kept Vinh's `RefundDueLookup.tsx` (already in master, already used by the live
`/billing` route) and removed Lộc's own 2026-10-19 parallel implementation
(`PrescriptionAdjustmentRefundDue.tsx` + `PrescriptionAdjustmentLookupForm.tsx` + their tests +
the `/billing/prescription-adjustments` route pages) — both did the exact same business function;
keeping two would confuse the live UI and future reviewers. The now-unused duplicate `api.ts`
helpers (`getPrescriptionAdjustment`, `refundLedgerPayment`) were dropped in favor of the
pre-existing `refundsDue`/`recordRefund` that `RefundDueLookup.tsx` already calls.

> Source recheck của lượt sửa UI 22/10: source hiện tại đã track đủ ba suite
> RevenueReport/InvoiceForm/OutboxReplayForm. Nhận định thiếu test trong đoạn lịch sử
> dưới đây không còn là backlog tạo file test mới. Kết quả trên revision hiện tại
> xem implementation plan `FE-REF-01` ở đầu workboard.

**Correction to Vinh's 2026-10-20 "DONE" claim for `FE-BILLING-04`:** verified directly against
code on 2026-10-21/22, not just the doc. `RevenueReport.tsx` has zero test coverage (no component
test, no `api.test.ts` coverage of `revenueByDepartment`). `InvoiceForm.tsx` and
`OutboxReplayForm.tsx` only have the two safety-focused cases in `BillingCommands.test.tsx`
(duplicate-submit / ambiguous-response blocking), not full component coverage. Everything else
listed under `FE-BILLING-04` (`InvoiceDetail`, `InvoiceLookup`, `LedgerPaymentForm`,
`PaymentRequestLookupForm`, `RefundDueLookup`) does have real component tests — confirmed by
running `pnpm vitest run src/features/billing src/features/notification` against `origin/master`
directly (103/103 passed, including the 68/68 Notification tests Vinh's doc also claimed).
`FE-NOTIFICATION-03` stays `BLOCKED`: confirmed by grepping `backend/notification-service` — no
resend/mark-read endpoint exists yet.

Lộc is now closing the real `FE-BILLING-04` gap: `RevenueReport.tsx` tests first, then
`InvoiceForm.tsx`/`OutboxReplayForm.tsx`.

## Lộc — Billing prescription-adjustment refund-due view, 2026-10-19 (superseded 2026-10-22)

> **Superseded:** this view was removed on 2026-10-22 in favor of Vinh's `RefundDueLookup.tsx`,
> which already implements the same `refunds-due`/`refunds` contract on the live `/billing` route.
> Entry kept for history; do not use `/billing/prescription-adjustments` as a reference anymore.

Scope: `frontend/src/features/billing/**` and `frontend/src/app/(dashboard)/billing/**`, per canonical
ownership in `frontend/docs/services/billing.md`. Closes the `FE-BILLING-07` prescription-adjustment
half, unblocked by the Gateway allowlist fix merged the night before (`HA-CODE-01/02`,
`docs/handoffs/HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md`).

- New routes `/billing/prescription-adjustments` (lookup by prescription UUID) and
  `/billing/prescription-adjustments/[prescriptionId]` (detail + refund). Detail calls
  `GET /api/v1/billing/prescription-adjustments/{id}/refunds-due` on mount; each refund-due row opens
  an inline form calling `POST /api/v1/billing/transactions/{originalTransactionId}/refunds`.
- Refund method is restricted to CASH/TRANSFER (matches live `LedgerRefundService.validate`'s
  `BILLING_INVALID_REFUND_METHOD` rule — INSURANCE is rejected server-side here too, same as the
  existing ledger-payment form). Refund amount is validated client-side against the due amount; the
  server's `BILLING_REFUND_EXCEEDS_PAYMENT`/idempotency rules remain authoritative. A successful
  refund shows a confirmation banner and refetches the adjustment so a partial refund's remaining
  due amount reflects the server's state, not a client guess.
- A 404 (`BILLING_PRESCRIPTION_ADJUSTMENT_NOT_FOUND`) renders a neutral empty state, not an error —
  most prescriptions never have a terminal rejection/cancellation event. 401 redirects to `/login`;
  other errors show the message and correlation id with retry, matching existing Billing forms.
- `FE-NOTIFICATION-03` (mark-as-read) was NOT picked up: `backend/notification-service/AGENTS.md`
  moved that module's ownership to Vinh on 2026-10-18, so adding the missing endpoint is no longer
  this owner's call to make; it stays `BLOCKED` pending a Vinh-side contract.
- New component tests: 11 tests across `PrescriptionAdjustmentLookupForm.test.tsx` and
  `PrescriptionAdjustmentRefundDue.test.tsx` (loading, 404-empty, 401 redirect, error+retry, render,
  empty-due state, client validation, success+refetch, refund API error+correlation id). Broader
  `FE-BILLING-04` coverage for the older Billing components remains open — see
  `frontend/docs/services/billing.md`.
- `pnpm typecheck`, `pnpm lint` (scoped to Billing), and `pnpm build`: PASS, exit 0. Full
  `pnpm test -- --maxWorkers=2`: **516/517 PASS, 57 files**; the one failure is the pre-existing
  `LabDetail.test.tsx:194` fixture gap already logged above (2026-10-19 sync note), unrelated to this
  change and not touched.
## Huy — FE isolated acceptance complete, 2026-10-21

**14/14 tasks, 56/56 criteria** for the FE test candidate; plan section 25 supersedes the
11/14 checkpoint below. [Final evidence](evidence/huy-six-service/2026-10-21-close/README.md).
Actual Core ADMIN/MANAGER browser daily/monthly checks pass through Gateway → Support,
including 12 months, correct cash/refund/net metrics, forbidden role, missing coverage,
error focus/retry/recovery, keyboard and mobile Light/Dark. Existing Pharmacy/Notification
browser evidence and final 595/595 FE regression remain part of the same source candidate.

The packaged calendar acceptance passed 1/1, exit0, no failures/errors/skips. Its fresh public
workflow is sealed with five source command journals and a **test-only Report Clock** advanced
to close the calendar year. System/JWT/source clocks remain unchanged. These are finite test
receipts, not production source-owner exports. The helper is absent from the production JAR;
using wall time still refuses the future coverage with404. No validation/gates were weakened.

**FE READY FOR REVIEW; production rollout HOLD.** Default gates stay OFF. Source-owner
production completeness/cutover remains operational work in the single Huy handoff, not an
unfinished FE implementation task. All test processes stopped; browser logout/theme reset.
No branch/commit/push.

## Huy — refund delivered; FE regression complete, 2026-10-21

Current checkpoint supersedes the historical blockers below: **11/14 tasks, 52/56 criteria**.
Closed **HF6-14.3**. [Evidence](evidence/huy-six-service/2026-10-21-close/README.md), plan
section 24; all remaining dependencies stay in the single Huy handoff.

- User assigned the concrete missing Billing publication and Lab fixture work. New outpatient
  refund publication has an independent default-OFF gate, retained approval and persisted-fact
  validation; historical HELD rows stay unchanged. No branch/commit/push or rollout.
- Public refund command/retry yielded one transaction/one notice. Actual Core-issued owner
  browser read the notice; actual foreign patient direct URL denied with error focus. Refund
  reached Report APPLIED. Signed malformed TEST credentials are labeled separately.
- Hold harness 1/1 and Support units 18/18 passed. FE **595/595 in 57 files**, focused Lab
  15/15, typecheck/lint/build passed. Final Docker reruns: Billing **1083/1083**, normal
  six-service **1/1**, both exit 0 with no failed/errored/skipped tests. Billing fork-JVM
  shutdown warning is recorded in evidence verification, not hidden as a clean teardown.
- Remaining **HF6-09.2/.3, HF6-11.2, HF6-12.4** need reviewed five-source coverage for a closed
  period, successful report metrics and final integrated release. Gateway preview works;
  today's intake does not establish a completed calendar year's source completeness.

Release **HOLD**. No fake coverage, producer evidence or historical refund release. Earlier
594/595 and refund-HELD observations below are historical, superseded by this checkpoint.

## Huy — source-invalid matrix complete, 2026-10-21

Current: **10/14 tasks, 51/56 criteria**; closed HF6-11.3. Same candidate, no production
source change, branch/commit/push. [Matrix evidence](evidence/huy-six-service/2026-10-21-matrix/README.md)
maps prior unpaid/role/session/unavailable/retry observations plus today's real browser
source mismatch. DOCTOR form keeps errors/values, no V0 fallback; exact-key API retry returns
422 twice, on-hand stock 40 → 40. Hold 1/1 PASS exit 0, frontend stopped, browser logout.

Four tasks still open: Report five-source coverage/daily-monthly success (HF6-09), complete
workflow through Report (HF6-11), Billing refund publication → Notification intake (HF6-14),
final release/full regression (HF6-12). Previous full FE remains 594/595, Lab fixture outside
scope. See plan section 23 and single backend handoff; no new environment blocker invented.

## Huy — Docker follow-up: normal six-service + Report gate rollback, 2026-10-21

Current checkpoint supersedes the section below: **10/14 tasks, 50/56 criteria**.
[Evidence](evidence/huy-six-service/2026-10-21-followup/README.md), plan section 22.
Same Huy candidate; no new branch/commit/push or default gate changes.

- Normal packaged acceptance: Surefire 18/18, Failsafe 1/1, exit 0; actual refund
  race/exact retry/over-refund, durable clearance/restart and Support rollback passed.
  Hold browser runtime 1/1, exit 0, stopped after checks.
- Closed HF6-09.4: isolated Report UI ON → actual missing-coverage error → restart OFF
  blocks direct-route form. Actual issuer expired session returns to login. Packaged
  ADMIN coverage preview 200 with test gate ON; no unsupported acceptance was submitted.
- Refund command and exact retry passed; delivery did not. Billing appends payment.refunded
  HELD and publication allowlist excludes it, so consumer ON alone cannot deliver a notice.
  Vinh publication/contract regression required; no fake event or database release.
- Valid real-notice positive control plus signed TEST negative credentials: missing patient
  claim 401, wrong claim 403, missing-exp/expired 401. Explicitly not Core-issued tokens;
  an earlier undefined-ID helper attempt is retained and excluded from acceptance.
- Remaining: accepted five-source coverage/successful reports, complete workflow/matrix,
  refund intake and final release. Previous full FE remains 594/595 (Lab fixture outside
  assigned scope); typecheck/lint/build PASS at prior checkpoint. Release HOLD.

## Huy — fresh Pharmacy/Support browser checkpoint, 2026-10-21

Candidate `42aab198` plus Gateway coverage allowlist working diff, existing Huy branch.
No new branch/commit/push. Docker now works; six packaged business apps + Gateway/Eureka
and disposable PostgreSQL/RabbitMQ ran successfully. [Final-candidate evidence](evidence/huy-six-service/2026-10-21-final/README.md)
distinguishes browser steps, actual issuer API checks, regressions and missing acceptance.

- Browser DOCTOR created Rx V1 from public Clinical/catalog authority; PHARMACIST unpaid
  dispense denied, actual cashier API payment/retry returned one transaction, browser refresh
  and dispense succeeded. Stock **40 → 37 → 37** after terminal command repeat. Cancelled Rx
  has no dispense button. Paid legacy boolean was true in this run; paid-false is covered by
  contract/component tests, not claimed as reproduced in this runtime.
- Generated lifecycle/payment/dispense notices reached the owner. Foreign PATIENT denied in
  browser; signed owner/foreign/NURSE/MANAGER API matrix passed. Support stop/error focus →
  resume/Tab/Enter retry → heading focus passed without resending a command.
- Report ADMIN/MANAGER missing-coverage 404, DOCTOR 403; UI OFF retained. Packaged coverage
  preview ADMIN 404 with approval gate OFF, other roles 403. Gateway code is complete; no
  five-source completeness/export proof or coverage approval was fabricated.
- Pharmacy test focus assertion now awaits the existing effect: **17/17** focused PASS.
  Full FE **594/595 tests / 57 files**, only Lab's READY-without-clearance fixture fails.
  Exact Lab test-file delegation pending; no Lab production or test change. Typecheck/lint/
  production build PASS. Hold harness **1/1 PASS, exit 0**; normal refund branch did not run.
- **10/14 tasks, 49/56 criteria**; four tasks remain open for the specific evidence in
  [plan section 21](huy-six-service-frontend-plan.md#21-fresh-pharmacysupport-browser-acceptance--2026-10-21).
  Integrated release HOLD. Refund/Report approval/Report UI gates unchanged. Runtime stopped,
  browser logged out, no new handoff file.

## Hoàng Anh/Lộc client completion — 2026-10-21

The user delegates Organization/Patient and Billing/Notification frontend/mobile
completion to Vinh. Existing web Billing/Notification workflows remain in place;
this frontend slice closes Organization/Patient form/error-path and FE-04 test gaps.
The corresponding mobile implementations and app composition are recorded in
[mobile completion](../../mobile/docs/ha-loc-client-completion.md).

Organization edit forms must not become editable after a failed initial read.
Staff profile update and department transfer are separate commands: if transfer
fails after saving the profile, the UI reports the saved portion and retries only
against refreshed authoritative state. Patient identity is editable on update; the
service retains the existing UUID and creation time and rejects duplicate corrections.
The API has no account-list, notification resend or mark-read command; this closeout
does not invent those workflows or enable deferred Surgery/Inpatient.

Verification before the latest Patient logic merge: full frontend suite **701/701
tests, 75 files PASS** (two workers, Node 26 with experimental web storage disabled);
Organization/Patient **39/39, 9 files PASS**. Typecheck, lint and production build PASS.
Independent review found no actionable defect in that web slice. Mobile evidence is
recorded separately; older dated evidence below refers to its original candidate.

## Hoàng Anh — đồng bộ master và khôi phục Patient, 2026-10-21

Master sync `07a87477` → `63b5f233` includes PR #477 / Patient logic commit `ac7ed78b`; local
Patient BE/FE work remains intact. The `patient-cccd-before-master-update-2026-10-21` stash
remains as a backup. Core/Patient source matches the Docker-verified revision (**194/194**); the
backend source did not change in this merge. The Patient + shared API regression after the prior
sync passed **71/71 tests across 7 files**. Master also includes the shared late-401 fix, so earlier
handoff blockers describe the state before that fix. Final merged frontend verification:
**745/745 tests across 77 files**, typecheck, lint and production build PASS. The merged
Patient regression subset passes **64/64 tests across 6 files**.

## Vinh/Lộc — outpatient frontend closeout, 2026-10-20

User delegated completion of both Vinh and Lộc frontend scope. Candidate is based on master
`8883f49d`, preserving Huy's latest Notification/history and Report work. The earlier sections
below retain their dated evidence and counts. Master now includes the Report coverage acceptance
controller/service/adapter; older statements that no writer exists describe the audit at `71328cbc`.
Accepted five-source reconciliation and runtime approval remain separate from implementing that writer.

- Clinical/Lab: session/resource boundaries, exact source order composition, check-in/start and
  completion regressions; READY V1 requires clearance, independent of legacy paid boolean.
- Billing: discover request by source/patient/episode; server-owned remaining amount/currency;
  duplicate/uncertain payment locks and exact-key retry; authoritative fee refresh after payment;
  prescription refund obligation lookup and recording with explicit reconciliation confirmation.
  Gateway rules for adjustment/refund already exist; the old route blocker is superseded.
- Shared: late old-session 401 cannot clear a new account; command uncertainty includes 408;
  dashboard hydration preserves valid login and clears private workspaces on account change;
  role login lands in an authorized workspace (including CASHIER and PATIENT).
- Signed browser: Clinical source → Lab → cashier payment → clearance → results COMPLETED;
  diagnosis/record completion reflected on the appointment; prescription refund recorded;
  manual IN_APP and own/cross-patient privacy checked with actual issuer accounts.
- Integrated full suite **673/673 tests / 70 files**, typecheck/lint/production build **PASS**.
  Independent review reports no actionable P1/P2 findings. Complete evidence is in
  [acceptance evidence](evidence/vinh-loc/2026-10-20/README.md) and its checks.txt.
  Packaged held acceptance ended **1/1 PASS, 1189s, exit 0**; test containers/processes stopped.

Implemented Vinh/Lộc MVP frontend is ready for review. Production UI stays OFF. Provider delivery,
Notification resend/read-state commands and external selectors remain separate contract/release
work; the existing Huy handoff tracks broader Report/source-coverage integration. No foreign
production backend, deferred Surgery/Inpatient scope, or shared dependency was changed.

## Huy — shared session race fix, 2026-10-20

Base `8883f49d` synchronized with master; current Huy working diff, no branch/commit/push.
Explicit human DELEGATED scope: `src/lib/api.ts` and `src/lib/api.test.ts`, late-401 session
protection only. A rejected request clears the session only if its bearer is still current,
after reading the response body; a newer login survives. Original errors and no-auto-retry remain.
Four added regression cases failed before the fix and pass after it. Notification error-focus
test now waits for the focus effect, preserving the actual accessibility assertion.

- Transport + Pharmacy/Report/Notification: **459/459 tests, 32 files**.
- Final full suite with two workers: **594/595 tests, 57 files**, only existing Lab fixture
  `LabDetail.test.tsx:194` failing; not a full PASS. Initial unrestricted run had six failures
  (Lab, Notification focus timing, four deferred Inpatient tests); after the Notification test
  correction and bounded concurrency, only Lab remains. No Inpatient/Lab source changes.
- Typecheck/lint/build PASS; no signed Gateway/browser acceptance added in this checkpoint.
  Raw full-suite reports: `.git/huy-fe-remaining-tests.json` and
  `.git/huy-fe-remaining-tests-final.json` (local, not committed).
- **10/14 tasks (71.4%), 49/56 criteria (87.5%)**. HF6-09/11/12/14 remain open. Shared 401 fix
  is complete at code/regression level; HF6-11.3 still needs the complete runtime matrix.
- Report writer is now merged. Gateway's two ADMIN coverage policy entries and five-source
  reviewed reconciliation remain dependencies. Refund stays OFF. Core public account creation
  requires PATIENT patientId; missing-claim signed negative testing needs backend test support.
  Report UI stays OFF; integrated release **HOLD**. Current details: [plan section 19](huy-six-service-frontend-plan.md#19-bảo-vệ-phiên-mới-trước-phản-hồi-401-cũ--2026-10-20).

Earlier checkpoints below describe their historical candidates and evidence.

## Huy — signed Support browser closeout, 2026-10-20

Current candidate `44ff1ed7` + Notification focus-test timing correction. **10/14 tasks (71.4%),
49/56 criteria (87.5%)**, up four criteria: HF6-11.4 and HF6-14.1/.2/.4. Existing branch retained;
no commit/push/PR. [Plan section 18](huy-six-service-frontend-plan.md#18-signed-notification-acceptance-và-audit-report-source--2026-10-20)
and [runtime evidence](evidence/huy-six-service/2026-10-20-closeout/README.md) supersede prior counts.

- Actual issuer ADMIN/NURSE/PATIENT via Gateway→Support; owner/cross-patient deny, manual IN_APP,
  real lifecycle/payment privacy, themes and Support failure→keyboard retry recovery observed.
- Focused Pharmacy/Report/Notification **446/446 / 31 files**, typecheck/lint/build PASS. Full
  rerun **590/591 / 57 files**, one existing foreign Lab fixture failure; not a full PASS.
- Report audit includes fetched master `71328cbc`: no production accepted-coverage writer;
  five-source export/reconciliation remains missing. Signed ADMIN/MANAGER receive 404, DOCTOR 403.
  Report UI stays OFF; fixtures are not accepted coverage.
- Notification refund/missing-claim, full workflow/failure matrix and final release remain open.
  Refund consumer stays OFF; no EMAIL/SMS provider delivery claim. Verdict **HOLD integration**.
  Single consolidated Huy handoff updated; disposable runtime stopped, browser logged out.

## Huy — Notification history refresh, 2026-10-20

DELEGATED Support FE, base `26bf2ef7`: explicit GET refresh preserves the submitted patient/page
even when the UUID draft changes; empty history can discover new notices. Result scope is visible,
old content clears during loading, and keyboard focus returns to result/error without interrupting
another control. HTTP 403/session/stale guards remain enforced. Six additional regression cases;
Notification **68/68 tests / 3 files**, typecheck/lint/build **PASS, exit 0**.
Plan progress remains **10/14 tasks (71.4%), 45/56 criteria (80.4%)**; see section 17 for the
remaining 11 criteria. Docker engine is unavailable during this run; no new signed/browser proof.
## Hoàng Anh — Docker retest CCCD, 2026-10-21

`mvn -q -pl backend/core-service -am test` **PASS, exit 0** với Docker Engine 28.5.1.
Core **194/194 tests**, zero failures/errors/skips. Ba test `PatientServiceIntegrationTest`
đạt trên PostgreSQL/RabbitMQ Testcontainers: tạo/publish/lookup, sửa CCCD và giữ UUID/ngày tạo,
giữ nguyên số hoặc request cũ thiếu số, từ chối số thuộc bệnh nhân khác; hai cập nhật đồng thời
tranh cùng CCCD chỉ một thành công, hồ sơ thua giữ số cũ. Hai integration classes Organization
cũng đạt. Blocker Docker ở checkpoint 2026-10-20 đã được giải quyết.

FE checkpoint đã hoàn tất: Patient **58/58**, typecheck/lint/production build **PASS**.
Không chạy lại FE hôm nay vì không đổi source; chưa có signed Gateway/browser acceptance mới.
Log backend ignored: `.patient-cccd-docker-retest.log`; Surefire reports ở Core `target/`.
Chưa commit/push.

## Hoàng Anh — sửa CCCD ở Core Patient và FE, 2026-10-20

Theo yêu cầu người dùng, mở cập nhật `soCmnd` trên chính UUID bệnh nhân. Core giữ quyền
ADMIN/NURSE, kiểm tra trùng với bệnh nhân khác và giữ unique constraint PostgreSQL. Request cũ
thiếu/null `soCmnd` giữ nguyên; request mới không được gửi số rỗng/trắng hoặc quá 20 ký tự.
FE gửi số đã trim, giữ draft/correlation khi bị 409 và cho sửa lại. Chỉ mở khóa trường có sẵn;
không dựng màn hình/layout mới. Event contract và mã bệnh nhân không đổi.

Patient FE **58/58 tests / 6 files**. Core tests không cần Docker **183/183**, zero skipped;
full Core chưa đạt vì ba integration classes không khởi tạo được Docker engine. Đã bổ sung test
PostgreSQL cho lưu CCCD, tương thích request cũ và hai cập nhật tranh cùng số, chưa có runtime
evidence cho các ca này. Chi tiết build/typecheck/lint cuối ghi ở Patient guide.
Không chạy lại full FE trong đợt sửa CCCD; kết quả 623/628 dưới đây thuộc checkpoint trước.
Chưa commit/push.

## Hoàng Anh — Patient logic checkpoint, 2026-10-20

IMPLEMENT on base `26bf2ef7`, branch `codex/patient-fe-logic`. Only Patient feature logic,
its regression fixtures and Patient documentation change. Existing markup is wired to headless
logic; no new screen, layout or styling. Backend, shared transport/auth and other features stay
outside this change.

- Exact public Gateway CRUD/search mapping, normalized paging and request field whitelists.
- Create/edit validation against live Core Patient DTO/domain, editable `soCmnd`, role checks,
  preserved draft, server field errors and correlation IDs.
- Synchronous duplicate mutation guard; unknown network/408/5xx/unreadable-success outcomes block
  replay. Unmount, patient change and session replacement discard stale local callbacks.
- Delete requires ADMIN and confirmation; error retry reads the patient rather than repeating DELETE.
- Focused Patient **56/56 tests / 6 files**; typecheck, lint and production build **PASS**.
  Full FE suite **623/628 tests / 60 files**, exit 1: one known Lab fixture failure at
  `LabDetail.test.tsx:194` and four Inpatient `CreateAdmissionForm` timeouts. All Patient cases pass;
  no Lab/Inpatient source changes or full-suite PASS claim. Logs: ignored `.patient-*.log` files.

Signed Gateway/browser/packaged acceptance remains open. Shared old-request 401 session clearing
remains tracked in `docs/handoffs/HANDOFF-HUY-MVP.md`; Patient guards cannot fix shared transport.
Headless entry points, mounting requirements and limits: [Patient guide](services/patient.md).
Changes are local; no commit or push at this checkpoint.

## Huy — Notification detail refresh/focus, 2026-10-20

IMPLEMENT under the existing user delegation for Support FE. Only Notification detail and its
feature tests change; backend, shared transport/auth, routes and gates stay unchanged.
Explicit GET refresh observes delivery status without resend/polling, clears old content while
pending, and restores keyboard focus after retry. HTTP 403 stays closed; outside focus and session
boundaries are preserved. Seven additional component regression cases cover these behaviors.
Verification and limits are recorded in the current plan's 2026-10-20 checkpoint. No additional
HF6 acceptance criterion is closed by component tests or build.
Final base `b7146306`: Notification **62/62 tests / 3 files**, typecheck/lint/build **PASS**.
No new full-suite, signed Gateway, browser or provider acceptance is claimed.

## Huy — signed Gateway/browser acceptance, 2026-10-19

Candidate `51eedfcd` + working diff: **10/14 task, 45/56 subtask**. Đóng thêm
**HF6-01, HF6-06, HF6-07, HF6-10**; chi tiết ở [plan mục 14](huy-six-service-frontend-plan.md#14-nghiệm-thu-signed-gateway-và-browser--2026-10-19)
và [evidence](evidence/huy-six-service/2026-10-19/README.md). Các mục bên dưới là lịch sử.

- Sáu packaged business apps + Gateway/Eureka, PostgreSQL/RabbitMQ dùng dữ liệu test độc lập;
  actual issuer login DOCTOR/PHARMACIST/ADMIN, CASHIER payment bằng public API.
  Browser tạo Rx V1 → refresh đã thanh toán → xác nhận xuất; stock 40→37, duplicate không thêm effect.
  Allowed cancel và foreign-doctor/pharmacist denial có HTTP/correlation evidence.
- Catalog/search/page-size, tạo thuốc và chỉnh stock 5→12, replay known event được kiểm trên browser;
  Clinical attachment/Report journal/Notification intake/dispense slip vẫn một effect sau replay.
- Report legacy ngày/tháng/top-medicines qua Support thật: ADMIN 200, DOCTOR 403;
  Support unavailable → focus lỗi → restart → keyboard retry thành công. Kiểm theme/320px/768px/desktop.
  Outpatient UI giữ OFF, chưa có accepted coverage hoặc MANAGER acceptance.
- Phát hiện và sửa Pharmacy V1 read chỉ kiểm V0 receipt nên không hiện thanh toán đã xác nhận.
  Nay đọc verified clearance đúng target/patient/episode và còn hạn; dispense vẫn kiểm Billing authority.
  Focused backend rerun **48/48 PASS** trên snapshot source-identical, tránh IDE ghi đè class output.
  Full Pharmacy lần đầu có 846 tests, tám lỗi class-file; không ghi là full PASS.
- Opt-in signed browser acceptance **1/1 PASS, 770.5s, exit 0**, zero failures/errors/skips.
  FE production không đổi trong lượt runtime: checkpoint gần nhất **551/552 / 55 files**,
  còn lỗi Lab fixture `LabDetail.test.tsx:194`; typecheck/lint/build PASS.
- Còn **HF6-09/11/12/14**: Report coverage và ON/OFF, workflow Report/Notification privacy,
  nghiệm thu Notification bằng signed users và release cuối. Dùng một handoff Huy hiện có.
  Môi trường test đã dọn; Docker Desktop vẫn chạy. Chưa commit/push.

## Huy — hoàn tất 5 đầu việc timeout, 2026-10-19

Candidate `51eedfcd` + working diff; [plan mục 13](huy-six-service-frontend-plan.md#13-đợt-năm-đầu-việc-timeout--2026-10-19)
ghi chi tiết B5-01…05. Năm work items local này không cộng thành năm HF6 runtime tasks.

1. **B5-01 DONE:** Rx V1 HTTP 408 giữ snapshot/key, khóa input; explicit retry giữ payload và không gọi V0.
2. **B5-02 DONE:** tạo thuốc HTTP 408 khóa gửi, giữ input/correlation và focus feedback; tra cứu danh mục
   trước yêu cầu mới. Unknown reply details không tạo lỗi field giả.
3. **B5-03 DONE:** Notification timeout khóa gửi để tra lịch sử, giữ correlation/focus và không resend.
4. **B5-04 DONE:** hủy/xuất Rx timeout chuyển sang fresh GET; GET lỗi vẫn giữ evidence; retry chỉ đọc.
5. **B5-05 DONE:** outbox ambiguous response focus cảnh báo, giữ UUID; Enter không replay, cần xác nhận lại
   chủ động. Kết quả chỉ nói requeue, không xác nhận consumer effect.

Thêm **13 ca hồi quy trong 5 file**. Red run **12 fail / 112 pass**, 27.82s; case Notification 504
đã đạt trước sửa. Green focused **124/124**, 20.55s. `pnpm typecheck`, `pnpm lint`, `pnpm build`
**PASS, exit 0**; typecheck chạy trước build để không tranh chấp generated routes. Logs ignored trong
`frontend/`: `.fe-five-{red,green,full,typecheck,lint,build}.log`.

Full suite candidate cuối **551/552 trong 55 file**, 143.16s, exit 1. Chỉ lỗi đã có từ master
`LabDetail.test.tsx:194` (READY V1 thiếu clearance nhưng fixture mong nút bắt đầu); không có diff Lab.
Cả 13 ca mới và hồi quy Pharmacy/Support/stock đều đạt; Inpatient đạt trong lượt này.

Pharmacy dùng helper feature-local cho 408/5xx/unreadable success, stock giữ flow đối chiếu đã có.
Không đổi wire/API/shared auth/backend/feature foreign, không đổi theme/frame hoặc runtime flags.
Chưa có browser/signed Gateway/coverage acceptance mới: **6/14 task, 39/56 subtask** giữ nguyên.
Các dependency tiếp tục ở một handoff Huy; chưa commit/push.

## Huy — điều chỉnh kho chưa rõ kết quả, 2026-10-19

Candidate `51eedfcd` + working diff; xem [plan mục 12](huy-six-service-frontend-plan.md#12-giữ-kết-quả-điều-chỉnh-kho-chưa-rõ-qua-lỗi-tải-lại--2026-10-19).

- Pharmacy giữ message/correlation và delta/unit của lệnh chưa rõ kết quả qua lỗi refresh,
  retry read, 403/404. HTTP 408/5xx, unreadable success và network error bỏ draft cũ, không tự gửi lại.
- Fresh GET không tự xác nhận kết quả mutation. Form chỉ mở draft trống sau khi người dùng xác nhận
  đã đối chiếu; trong lúc refresh hoặc đọc thất bại không được bỏ trạng thái này. Focus về field số lượng
  sau đối chiếu; đổi drug/session và response cũ không làm lẫn kết quả giữa tài khoản.
- **11 ca hồi quy mới đạt**; focused **46/46 trong 3 file**. Red run tái hiện 8 case mới trước sửa.
- Full suite cuối **538/539 trong 55 file**, 136.68s, exit 1: chỉ lỗi có sẵn
  `LabDetail.test.tsx:194` (V1 READY fixture thiếu clearance nhưng mong nút bắt đầu), không có diff Lab.
  Cả 11 ca mới và các hồi quy Pharmacy/Support trước đó đạt; Inpatient đạt trong lượt này.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: **PASS, exit 0**. Typecheck cuối chạy sau build;
  lần chạy đồng thời trước đó gặp generated `.next/types/routes.js` đang được tái tạo, không phải lỗi TS source.
  Logs ignored trong `frontend/`: `.fe-stock-{red,green,final,full,typecheck,lint,build}.log`.
- Chỉ thêm code/tests Pharmacy và cập nhật docs/handoff chung, không commit/push. Không có browser,
  screen-reader hoặc signed Gateway acceptance mới; giữ **6/14 task, 39/56 subtask**, HF6-10.3 OPEN.

## Huy — keyboard/focus Pharmacy và Support, 2026-10-19

Candidate `51eedfcd` + working diff; xem [plan mục 11](huy-six-service-frontend-plan.md#11-keyboard-và-focus-sau-đồng-bộ-master--2026-10-19).

- Kê đơn giữ focus khi thêm/xóa dòng, chọn lỗi đầu tiên theo thứ tự hiển thị, focus phản hồi khi
  kết quả chưa rõ và đưa về mã hồ sơ khi người dùng chủ động bắt đầu ý định mới.
- Gửi Notification focus đúng field hoặc vùng lỗi sau khi pending kết thúc; không resend tự động.
- Cả bốn Report views có vùng lỗi truy cập được và keyboard retry. Không giành focus khi người dùng
  đang nhập phạm vi khác hoặc làm việc bên ngoài Report; không phát thêm request khi chỉ sửa draft.
- **22 ca hồi quy mới đạt** (Pharmacy 8, Notification 4, Report 10). Focused trước guard cuối:
  392/392 trong 31 file; Report lifecycle cuối 24/24. Full suite cuối:
  **527/528 PASS, 55 files**, chỉ còn `LabDetail.test.tsx:194` từ master như checkpoint dưới.
  Hai lỗi Inpatient ở lượt đồng bộ không tái hiện trong lượt full này.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: **PASS, exit 0**. Commands/logs local:
  `.fe-accessibility-{full,typecheck,lint,build}.log`, `.fe-report-focus-final.log` trong `frontend/`
  (ignored). Không sửa source Lab/Inpatient/shared auth/backend.
- Chưa browser/screen-reader/Gateway acceptance mới; HF6-10.3 vẫn OPEN, giữ **6/14 task; 39/56 subtask**.
  Plan/guides và handoff chung được cập nhật; không thêm handoff mới hoặc mở UI/backend gates.

## Đồng bộ master vào Huy — 2026-10-19

- Fast-forward từ `025ef39e` tới `3cc3439b` (12 commit); local `Huy` và `master` cùng
  `origin/master`. Giữ working diff Pharmacy/Support, không commit/push phần đang làm.
- Ghép workboard/handoff của cả hai phía. Shared API dùng nguyên `MutationOptions` của master
  cho POST/POST raw/PUT; consumer Pharmacy và regression header của Huy được giữ lại.
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: PASS, exit 0 sau đồng bộ.
- Full suite: **503/506 PASS, 55 files**, ba lỗi ở Inpatient/Lab. Chạy riêng hai file đó:
  **19/20 PASS**; toàn bộ năm test Inpatient đạt khi chạy lại. Lab còn lỗi tái hiện được tại
  `LabDetail.test.tsx:194`: fixture V1 READY có `clearanceId: null` nhưng mong nút bắt đầu;
  implementation mới chỉ mở nút khi có clearance. Hai context này không có local diff.
- Chưa có browser/Gateway acceptance mới. Giữ nguyên số tiêu chí HF6 và phân công backend
  testing cho Vinh; các checkpoint dưới đây là lịch sử trên candidate được ghi tại từng mục.

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

## Huy — plan chốt FE trên 6 service, 2026-10-18

Plan thực thi hiện hành: [huy-six-service-frontend-plan.md](huy-six-service-frontend-plan.md).
User mở rộng thành Pharmacy + **toàn bộ Support FE (Report/Notification)**. Plan hiện có
**14 task / 56 subtask**, đạt **6/14 task; 39/56 subtask** theo checkpoint mục 9/10. Trong 48 tiêu chí
Pharmacy/Report gốc: **5/12 task; 35/48 subtask**. Tám tiêu chí Notification thêm riêng theo scope mới;
không dùng 48/48 backend làm tiến độ FE hoặc reset lịch sử.

- User giao Huy `frontend/src/lib/api.ts`/`api.test.ts` cho optional POST `Idempotency-Key` và
  Notification feature/routes trong task. Canonical shared owner Vinh, Notification owner Lộc giữ nguyên;
  không còn chờ handoff hai phần code đã được giao này.
- `HF6-02/03/04/05` DONE local: shared transport, request/API/validation V1, frozen command snapshot,
  explicit same-key retry, new-intent/session/unmount/duplicate-submit guards và payment fresh GET.
  Server route default OUTPATIENT; explicit `MEDIFLOW_PHARMACY_PRESCRIPTION_MODE=legacy` mới dùng V0.
  Invalid setting đóng creation; không tự fallback khi V1 lỗi. IDs/catalog thật còn HF6-01.2.
- `HF6-08` DONE local: giữ legacy/outpatient reports, server metrics/coverage/currency/zone;
  outpatient UI gate vẫn OFF mặc định. Không mở full V2 để đóng Support.
- `HF6-13` DONE local: Notification lookup/detail/send có roles/privacy, pagination theo submitted
  patient, channel/address validation, stale/session/unmount guards, 401/403/correlation/field focus;
  unknown send outcome không blind retry vì backend không có idempotency contract.
- `HF6-06/09/11/12` chốt signed-role hủy/xuất thuốc, Report qua Support có coverage, browser
  workflow thật và release. Exact Pharmacy Gateway roles/Support backend đã có; ghi chú chờ
  implementation ở các checkpoint cũ không còn là trạng thái hiện tại.
- Audit 2026-10-18, baseline `025ef39e`: Pharmacy/Report **235/235 tests, 22 files**,
  typecheck/lint PASS. Không chạy build/browser trong lượt audit; chưa có FE production diff mới.
- Surgery/Inpatient/full V2 deferred; logical feature folders giữ riêng. Dependency dùng
  [một handoff Huy](../../docs/handoffs/HANDOFF-HUY-MVP.md), không tạo file bàn giao mới.
- Foundation checkpoint trước mở rộng scope: 268/268 Pharmacy/Report, 24 files; full 396/396,
  47 files và typecheck/lint/build PASS. **Final candidate: 474/474 tests, 53 files**, zero failures/skips;
  typecheck/lint/build PASS, exit 0. Chỉ có jsdom navigation warning hiện có. Chi tiết ở plan mục 8.
- Hai đợt liên tiếp B10/B20: 10 đầu việc lifecycle/uncertainty/layout/runtime và 10 đầu việc denial/focus,
  ghi riêng trong plan mục 9/10, không cộng thành 20 HF6 task cấp cao. Đợt B10 full **501/501 / 55 files**,
  typecheck/lint/build PASS; đợt B20 focused **108/108**, final full **506/506 / 55 files**.
- Rerun backend thật `SixServiceOutpatientAcceptanceIT` **1/1, zero failure/error/skip**, PG/Rabbit
  disposable, six apps + Gateway/Eureka, actual staff logins, Rx payment/dispense và Support rollback.
  Đóng HF6-09.1/11.1; chưa accepted Report coverage hoặc signed FE workflow. Harness-signed PATIENT
  privacy probe không thay issuer PATIENT login. Không sửa BE source hoặc persisted Compose volumes.
- Browser HTTP fixtures kiểm ba theme và 320/768/1280px; đã sửa long-name drug detail overflow,
  lưu matrix/ảnh ở [evidence](evidence/huy-six-service/2026-10-18/README.md), đóng HF6-10.2/.4.
  HF6-10.3/14 vẫn còn keyboard matrix/signed Notification acceptance, không coi fixture là runtime proof.
- Shared transport còn old-request 401 có thể clear session mới; feature guards không xử lý được
  side effect global này. Ghi acceptance/scope cần sửa trong **một handoff Huy**, không đổi auth/transport
  ngoài optional Idempotency-Key đã giao. Không giao lại implementation Notification cho Lộc.

Các mục cũ bên dưới là checkpoint lịch sử, không thay thế plan này.

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
| Patient | List/detail/create/update/delete; CCCD correction on the same patient UUID | `DONE` |
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
- Billing's prescription-adjustment refund-due view shipped 2026-10-19 (`/billing/prescription-adjustments`)
  once the Gateway allowlist rule landed (`docs/handoffs/HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md`).
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
