# Plan triển khai frontend của Huy — Pharmacy, Report, Surgery

**Ngày:** 2026-10-12. **Owner:** Huy (`LQHuy0210`).
**Baseline đã đọc:** `c110671bd2eaa7f1e948879162506df106cbf27d`.
**Trạng thái:** IN PROGRESS — đã triển khai batch đầu cho Pharmacy và Report theo mẫu đã duyệt.
**Checkpoint 2026-10-12:** 3/30 task chính (P-01, P-04, R-03), 17/123 subtask đã đóng ở mức FE local;
nghiệm thu Gateway/backend thật và các task release vẫn mở.
**Thiết kế:** người dùng đã chốt bản dùng theme Sáng / Tối / Hệ thống của MediFlow.

## 1. Phạm vi và cách dùng plan

Plan này là thứ tự thực thi mới cho FE của Huy, bám **rule đã chia sẵn**, không tạo quy tắc ownership mới.
Đọc [frontend blueprint](../../docs/ai/12-frontend.md),
[ownership](../../docs/ai/15-frontend-ownership.md), [source rules](../src/AGENTS.md),
[workboard](frontend-workboard.md), service guide và `AGENTS.md` gần file đích trước khi sửa.

| Context | Production feature được sửa | Production route được sửa | Rule/guide sẵn có |
|---|---|---|---|
| Pharmacy | `src/features/pharmacy/**` | `src/app/(dashboard)/pharmacy/**` | [feature rule](../src/features/pharmacy/AGENTS.md), [guide](services/pharmacy.md) |
| Report | `src/features/report/**` | `src/app/(dashboard)/reports/**` | [feature rule](../src/features/report/AGENTS.md), [guide](services/report.md) |
| Surgery | planned `src/features/surgery/**` | planned `src/app/(dashboard)/surgery/**` | [parent feature rule](../src/features/AGENTS.md), [guide](services/surgery.md) |

Đường dẫn `src/...` trong plan tính từ `frontend/`. Tài liệu, fixture và test đi cùng context.
Shared FE (`src/components/**`, `src/lib/**`, `src/test/**`, shell, global CSS, packages/config)
do Vinh điều phối; Gateway thuộc Hoàng Anh. Huy **tiêu thụ** API/component/theme sẵn có.
Sửa shared production chỉ khi có task giao đúng path; chốt mockup không tự cấp quyền sửa shell.
Không import sibling feature; không dựng `features/huy`, domain/application/infrastructure hay store chung.

Plan có **30 task chính**: 2 nền tảng, 8 Pharmacy, 8 Report, 10 Surgery, 2 nghiệm thu.
Checklist ở đây đo công việc FE mới, không cộng vào/chỉnh tỷ lệ hoàn thành backend.
Các checkbox đều mở khi lập plan; thiết kế được duyệt không đồng nghĩa đã triển khai production.

- **READY:** có thể code và test trong scope Huy ngay sau task phụ thuộc.
- **LOCAL + GATE:** có thể làm DTO, validation, presenter, test fixture ngay; chưa mở route/control thật
  cho tới khi các gate ghi trong task đạt. LOCAL không được đánh dấu là nghiệm thu tích hợp.
- **VERIFY:** cần xác nhận hợp đồng/release thực tế; không tự suy luận từ mockup hoặc role.
- Chỉ đóng task khi subtask, kiểm tra và acceptance của chính task đã đạt. Lưu lệnh/kết quả/commit.
- Nếu phát hiện contract thay đổi, cập nhật task và nguồn bằng chứng trước khi viết serializer.

## 2. Hiện trạng source và phần được tái sử dụng

| Phần | Đã có | Công việc còn lại trong plan |
|---|---|---|
| Pharmacy | 21 file kể cả AGENTS; catalog, drug detail/create/stock; Rx create/lookup/detail/cancel/dispense; outbox | Áp dụng mẫu mới, giữ workflow; sửa nhãn thanh toán/xuất thuốc; test và smoke |
| Report | 6 file kể cả AGENTS; daily/monthly/top-medicines | Workspace theo tab; biểu đồ; validation/error/retry/stale request; V2 tách riêng |
| Surgery | Chưa có feature/route FE | Đối chiếu DTO → read views → từng command; bật route sau release gate |
| Theme | `globals.css`, `ThemeProvider`, `ThemeToggle`; light/dark/system | Dùng semantic Tailwind tokens; kiểm tra 2 theme và system change |
| UI dùng chung | `Button`, `Field`, `StatusBadge`, `AsyncState`, `Pagination`, `DataTableShell`, `PageShell`, `RoleGate` | Dùng lại API có sẵn, không fork thành bộ UI thứ hai |
| Test | `vitest.config.mts`, jsdom, Testing Library, script `pnpm test` và test Inpatient | Thêm test cạnh feature Huy, không cần dựng lại harness |

Những sai lệch tài liệu/source phải ghi nhớ:

1. [Plan Pharmacy cũ](pharmacy-frontend-implementation-plan.md) có PH-FE-00…09 đã có code;
   PH-FE-10 còn confirmation accessibility, PH-FE-11/12 còn test/release. Giữ lịch sử đó.
   Dòng cũ “chưa có test script/harness” không còn đúng tại baseline này.
2. Gateway đã có code phát signed `staffId`; không coi claim hoàn toàn chưa được implement.
   Vẫn cần token thật và test theo role để chứng minh flow đang chạy.
3. `MonthlyReportView`/`TopMedicinesView` còn handler/JSX dồn dòng; lỗi truy vấn mới có thể để lại
   kết quả cũ. Phải gắn kết quả với bộ lọc **đã gửi**, không gắn ngầm vào input đang sửa.
4. `dispenseStatus=PENDING` đang có nhãn “Chờ thanh toán / xuất thuốc”; phải tách khỏi
   `paymentConfirmed`, vì chờ xuất thuốc không đồng nghĩa chưa thanh toán.
5. `PrescriptionDTO.recordId` ở TS hiện luôn là string; phải kiểm tra và phản ánh nullability của
   response V1. Không dùng `recordId=null` để tự suy luận admission hoặc bỏ điều kiện thanh toán.
6. Shared API public methods chưa nhận custom headers, dù hàm private bên trong có `RequestInit`.
   Surgery command cần `Idempotency-Key`; chưa thể nối bằng API public hiện tại.

## 3. Giao diện đã chốt — chuẩn đối chiếu cho code

### 3.1 Bản mẫu được lưu cùng repo

[Mở bản mẫu được duyệt](designs/huy-approved-ui.html).
Đây là bản tham chiếu tương tác chứa dữ liệu minh họa, không kết nối backend và không phải source Next.js.
Chọn Dược / Báo cáo / Phẫu thuật, đổi Sáng / Tối / Hệ thống; có thể tìm thuốc, xem detail minh họa.
Không copy fixture, số tổng, tên thuốc mẫu, UUID rút gọn hoặc cờ khả dụng từ bản này vào production.
Sidebar có Surgery trong bản mẫu để chuyển màn hình; production sidebar vẫn phải qua gate SH-02.

Giữ bố cục cuối đã chốt, không quay lại mẫu dashboard KPI lớn trước đó:

```text
Pharmacy                              Report
Tiêu đề + Thêm thuốc                  Tiêu đề theo loại báo cáo
Danh mục | Kê đơn* | Đơn | Outbox*     Hàng ngày | Hàng tháng | Thuốc xuất nhiều
Panel: số thuốc + tìm theo tên        Bộ lọc ngày/tháng/năm/khoa + Xem báo cáo
Bảng 6 cột + phân trang               Biểu đồ doanh thu 70% | Tổng hợp 30%
Panel tra đơn theo UUID               Chi tiết theo ngày bên dưới

Surgery detail
Tiêu đề + trạng thái + mã ca
Mã bệnh nhân | Mã admission/episode | Mã thủ thuật
Lịch dự kiến + tiến trình | Điều kiện trước mổ / bằng chứng
Các thao tác hợp lệ theo role, revision và trạng thái
Thông tin kỹ thuật mở rộng khi cần

* Chỉ hiện khi role có capability; Kê đơn phải tiếp tục truy cập được.
```

Các form/detail chưa được vẽ riêng kế thừa cùng typography, khoảng cách, nút, field và status.
Mockup không cấp thêm quyền, không định nghĩa dữ liệu y khoa, không thay controller contract.

### 3.2 Ánh xạ theme bắt buộc

Nguồn runtime là [globals.css](../src/app/globals.css) và
[ThemeProvider](../src/components/theme/ThemeProvider.tsx), không hardcode palette ở feature.
Giá trị sau chỉ là snapshot đối chiếu; nếu shared theme đổi, feature kế thừa token mới.

| Thành phần | Tailwind token | Sáng | Tối |
|---|---|---|---|
| Nền workspace | `bg-background` | `#f8fafc` | `#0f172a` |
| Panel/input | `bg-surface` | `#ffffff` | `#1e293b` |
| Header bảng/nền phụ | `bg-surface-muted` | `#f1f5f9` | `#273449` |
| Chữ chính | `text-foreground` | `#0f172a` | `#f8fafc` |
| Chữ phụ | `text-muted-foreground` | `#475569` | `#cbd5e1` |
| Border panel | `border-border` | `#e2e8f0` | `#475569` |
| Border field | `border-control-border` | `#64748b` | `#94a3b8` |
| Nút chính/bar/active tab | `bg-primary` / `text-primary` | `#1d4ed8` | `#93c5fd` |
| Chữ trên nút chính | `text-primary-foreground` | `#ffffff` | `#0f172a` |
| Hover nút chính | `hover:bg-primary-hover` | `#1e40af` | `#bfdbfe` |
| Lỗi/hết hàng | `danger` | `#b91c1c` | `#f87171` |
| Cảnh báo | `warning` | `#a16207` | `#facc15` |
| Thành công | `success` | `#15803d` | `#4ade80` |
| Thông tin | `info` | `#1d4ed8` | `#60a5fa` |

- Font kế thừa stack Inter/system của app, không tải thêm font/dependency.
- Tiêu đề trang khoảng `text-2xl font-semibold`; nội dung `text-sm`, chú thích `text-xs`;
  số dùng `tabular-nums`, mã dài cho phép wrap/copy đầy đủ.
- Panel bo nhẹ (`rounded-lg`/`rounded-xl`), border mảnh; không gradient hay shadow lớn.
- Dùng `Button`, `Field/controlClassName`, `StatusBadge`; không style đè semantic màu của shared component.
- Status phải có chữ, không chỉ chấm màu. Focus nhìn rõ cả hai theme; thao tác chạm tối thiểu 44px.
- Desktop 1024–1440px: giữ hai cột theo mẫu; tablet/phone: stack panel. Chỉ bảng được cuộn ngang.
- Feature không thêm ThemeProvider, localStorage key hoặc nút theme riêng; dùng cơ chế app đã có.

### 3.3 Điều chỉnh cần thiết từ hình mẫu sang dữ liệu thật

- Pharmacy tìm **theo tên thuốc**: query hiện tại không tìm active ingredient. Số thuốc/pagination lấy
  từ `PageResult`, page 0-based, size 10/20/50 hiện có; không cố định 6 dòng như mockup.
- Bảng thuốc: tên + hoạt chất phụ, đơn vị, tồn kho, hạn dùng, giá, thao tác. Manufacturer/ngưỡng
  vẫn có ở detail hoặc nội dung phụ; không làm mất khả năng đọc dữ liệu đang có.
- Report monthly thật hiển thị đủ 28–31 ngày từ response. 4 cột trong mockup là phần xem trước,
  không phải giới hạn truy vấn. Tổng doanh thu/số hóa đơn dùng field backend, không cộng lại ở FE.
- Legacy Report zero-filled là dữ liệu hợp lệ; V2 unavailable/thiếu coverage không được biến thành 0.
- Pharmacy giữ format VND đang có. Legacy Report chưa có currency: không tự gắn VND.
  V2 dùng `currency` trong response, không gộp nhiều currency.
- Sáu dòng “điều kiện trước mổ” là nhóm trình bày. Chỉ đánh dấu đạt nếu có fact tương ứng từ
  contract; không có bằng chứng thì hiển thị chưa xác minh, không sinh 6 boolean giả trong DTO.
- Lịch Surgery là draft **của ca đang xem**, không phải bảng occupancy toàn bộ phòng mổ.
  `snapshotValidNow` không phải quyền START; backend xác thực lại khi nhận command.
- Bản production có loading/error/empty/forbidden và dữ liệu thật; không có nhãn “BẢN MẪU”.

## 4. Hợp đồng đã đối chiếu và các gate

Tất cả feature gọi `api` bằng `/v1/...`; wrapper tự thêm `/api`. Các bảng dưới dùng path HTTP đầy đủ.
Role UI chỉ hỗ trợ UX. Service là nơi xác thực quyền, quan hệ dữ liệu và invariant.

### 4.1 Pharmacy

Nguồn: `backend/pharmacy-service/.../web/{DrugController,PrescriptionController,PharmacyOutboxAdminController}.java`.

| HTTP dưới `/api/v1/pharmacy` | Role downstream | Lưu ý |
|---|---|---|
| GET `/drugs`, `/drugs/{id}` | ADMIN, DOCTOR, PHARMACIST | Search tên; chi tiết UUID |
| POST `/drugs`; PUT `/drugs/{id}/stock` | ADMIN, PHARMACIST | Stock là delta; lấy kết quả server |
| POST `/prescriptions` | ADMIN, DOCTOR | FE hiện gửi legacy/V0; V1 tách gate |
| GET `/prescriptions/{id}` | ADMIN, DOCTOR, PHARMACIST | Known-ID lookup, không có list API |
| PUT `/prescriptions/{id}/cancel` | ADMIN, DOCTOR | Gateway hiện generic PUT chỉ ADMIN/PHARMACIST: DOCTOR bị chặn |
| PUT `/prescriptions/{id}/dispense` | ADMIN, PHARMACIST | Bodyless; signed actor; payment/lifecycle do backend quyết định |
| POST `/admin/outbox/{eventId}/replay` | ADMIN | Bodyless, known ID, maintenance/outbox capability |

### 4.2 Report

Nguồn: `backend/report-service/.../web/*Controller.java`, response DTO và canonical care projections.

| Query/command | Tham số quan trọng | Role / gate |
|---|---|---|
| GET `/api/v1/reports/daily` | `date`, optional `departmentId` | ADMIN/MANAGER; always-on |
| GET `/api/v1/reports/monthly` | `month` 1–12, `year` 2000–2100, optional department | ADMIN/MANAGER; always-on |
| GET `/api/v1/reports/top-medicines` | `fromDate`, `toDate`, `limit` 1–50, optional department | ADMIN/MANAGER; inclusive range |
| GET `/api/v1/reports/financial/daily` | `from`, `to`, optional department | ADMIN/MANAGER; V2 + financial-read |
| GET `/api/v1/reports/financial/monthly` | `year`, optional department; không phải legacy month/year | ADMIN/MANAGER; V2 + financial-read |
| GET `/api/v1/reports/operations/daily`, `/operations/surgery` | `from`, `to`, optional department | ADMIN/MANAGER/DOCTOR; V2 |
| GET `/api/v1/reports/financial/capabilities` | capability/history policy | ADMIN; V2 + financial-publication |
| GET `/api/v1/reports/financial/coverage` | `from`, `to` | ADMIN/MANAGER; V2 + financial-publication |
| Financial/operational rebuild, publication, rollback | Mỗi controller có DTO riêng, generation và expected pointer | ADMIN; default-off; Gateway chưa có POST rule |

Capabilities trên không phải endpoint cờ chung cho mọi role. Không gọi bằng MANAGER rồi coi 403
là “V2 tắt”. Lộ trình public phải có cấu hình release rõ ràng, không probe command để đoán capability.
Archive là binary evidence backup/restore, **không phải CSV export báo cáo**; chưa đưa vào task UI ban đầu.

### 4.3 Surgery

Base `/api/v1/surgery/cases`; mọi route có business flag `mediflow.features.surgery.enabled`.
Creation/preop mutation/lifecycle còn cờ API riêng; Gateway route có cờ
`mediflow.routes.surgery.enabled`, mặc định false. Xem controllers trong
`backend/surgery-service/src/main/java/com/mediflow/surgery/web/`.

| HTTP | Roles | Gate thêm / Gateway tại baseline |
|---|---|---|
| GET base, GET `/{id}` | ADMIN, MANAGER, DOCTOR, NURSE | Đã có RBAC GET; route/runtime chưa được chứng minh bật |
| POST base | ADMIN, DOCTOR | creation API; Gateway chưa có rule |
| POST `/{id}/preop` | ADMIN, DOCTOR | Đã có Gateway rule |
| PUT `/{id}/checklist`; POST `/{id}/consents` | ADMIN, DOCTOR, NURSE | preop API; Gateway chưa có rule |
| PUT `/{id}/schedule` | ADMIN, MANAGER, DOCTOR | Đã có Gateway rule; chỉ chuẩn bị draft |
| POST `/{id}/readiness/evaluate` | ADMIN, DOCTOR | lifecycle API; Gateway chưa có rule |
| POST `/{id}/schedule/finalize` | ADMIN, MANAGER, DOCTOR | lifecycle API; Gateway chưa có rule |
| POST `/{id}/start`, `/{id}/complete` | ADMIN, DOCTOR | lifecycle API; Gateway chưa có rule |
| POST `/{id}/cancel` | ADMIN, DOCTOR | Đã có Gateway rule |

Mutation dùng `Idempotency-Key`, expected revision và signed identity; creation key phải khớp
`surgeryRequestId`. Readiness/permission không nhận boolean tự xác nhận từ browser.
Internal consent withdrawal và safety evidence kernel đã có không đồng nghĩa có public endpoint.
Không tạo nút revoke-consent/safety-pause hoặc phê duyệt pháp lý từ internal use case.

### 4.4 Sổ dependency — không thay ownership

| Gate | Owner / việc phải có | Unblocks | Theo dõi |
|---|---|---|---|
| SH-01 | Vinh: public API wrapper nhận header Idempotency-Key tương thích ngược | Nối Surgery commands qua Gateway | [FE handoff](handoffs/HANDOFF-SHARED-HUY-IDEMPOTENCY-HEADERS.md) |
| SH-02 | Vinh: shared nav/activation composition sau khi Surgery runtime được duyệt | Sidebar/khả dụng Surgery | Task release Q-02; không cần sửa shared để làm presenter |
| GW-01 | Hoàng Anh: exact cancel/dispense Pharmacy role/method policy | DOCTOR cancel smoke | [Huy care-finance handoff](../../docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) |
| GW-02 | Hoàng Anh: các Surgery command còn thiếu; giữ default-off và deny unknown paths | Surgery command E2E | [Surgery handoff](../../docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) |
| GW-03 | Hoàng Anh: reviewed ADMIN-only Report operator POST ingress | Rebuild/publication/rollback E2E | [Huy care-finance handoff](../../docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md) |
| BE-01 | Huy + producer owners: Pharmacy V1 authority/activation, exact care context | P-08 public activation | Huy care-finance handoff, không đoán từ generic staff/record |
| BE-02 | Huy + producer owners: Surgery authority/intake/consent/team/financial freshness và release | Surgery live workflow | Surgery handoff và canonical contracts |
| BE-03 | Huy: Report published snapshot/coverage và capability gates đúng môi trường | R-06…08 public activation | Huy care-finance handoff; acceptance lịch sử theo quyết định supported release mới nhất |

Không gán trạng thái CI hiện tại từ dòng “CI RED” lịch sử trong handoff; cần kiểm tra lại ở Q-02.
Các phát hiện Gateway ở trên là đọc source baseline, không phải kết quả chạy CI mới.

## 5. Cây file thực thi

Giữ file đang có, chỉ thêm file khi task sử dụng. Không tạo empty scaffold hoặc chép bộ UI chung.
`*.test.*` nằm cạnh unit/component cần chứng minh hành vi.

```text
src/features/pharmacy/
  api.ts, types.ts, permissions.ts, presentation.ts, utils.ts       [giữ + điều chỉnh có bằng chứng]
  components/{PharmacyNav,PharmacyPageShell}.tsx                   [local nav + page composition]
  components/drug/{DrugCatalog,DrugCatalogActions,DrugTable,DrugDetail,CreateDrugForm,AdjustStockForm}.tsx
  components/drug/drugFormValidation.ts
  components/prescription/{PrescriptionLookup,PrescriptionDetail,CreatePrescriptionForm,
                          PrescriptionLinesEditor,CancelPrescriptionDialog,DispensePrescriptionDialog}.tsx
  components/prescription/prescriptionFormValidation.ts
  components/outbox/OutboxReplayForm.tsx
  api.test.ts, permissions.test.ts, presentation.test.ts           [thêm theo task]

src/features/report/
  api.ts, types.ts                                               [giữ; V2 types riêng theo contract]
  reportQuery.ts, validation.ts, presentation.ts                  [thêm helper có nhu cầu thực]
  useReportRequest.ts                                            [local request lifecycle; hiện dùng monthly]
  components/ReportWorkspace.tsx                                 [mới: local nav, composition]
  components/{DailyReportView,MonthlyReportView,TopMedicinesView}.tsx
  components/{MonthlyRevenueChart,DailyDetailsTable}.tsx           [mới]
  components/financial/FinancialSnapshotView.tsx                  [LOCAL + GATE]
  components/operational/OperationalSnapshotView.tsx              [LOCAL + GATE]
  components/operator/{RebuildWorkspace,PublicationWorkspace}.tsx [LOCAL + GATE]

src/features/surgery/                                            [mới; theo parent rules đã có]
  AGENTS.md                                                     [pointer owner theo convention, không rulebook mới]
  api.ts, types.ts, permissions.ts, presentation.ts, validation.ts
  commandState.ts                                                [key/revision/request intent, không transport riêng]
  components/case/{SurgeryCaseList,SurgeryCaseDetail,CreateSurgeryForm}.tsx
  components/preop/{ChecklistEditor,ConsentRecorder}.tsx
  components/schedule/{ScheduleEditor,ReadinessPanel}.tsx
  components/execution/{StartSurgeryDialog,CompleteSurgeryForm,CancelSurgeryDialog}.tsx

src/app/(dashboard)/
  pharmacy/**                                                   [routes cũ, compose lại]
  reports/page.tsx, loading.tsx, error.tsx                        [trong scope Report]
  reports/{financial,operational}/page.tsx                       [chỉ thêm sau gate]
  reports/admin/{rebuild,publication}/page.tsx                    [chỉ thêm sau gate]
  surgery/{AGENTS.md,page.tsx,loading.tsx,error.tsx}               [chỉ mount sau gate]
  surgery/[caseId]/page.tsx                                      [chỉ mount sau gate]
```

`/surgery/new` chỉ cần nếu intake UX thực sự dùng HTTP create và đã đạt S-05; không mặc định
dựng một form tạo ca tự do cạnh intake referral. Shell, global navigation và shared files ngoài cây này
không phải deliverable production của Huy trong plan hiện tại.

## 6. Task nền tảng

### HFE-01 — Áp dụng design/theme trong context

**READY.** File: layout/page thuộc Pharmacy/Report; presenter Surgery khi được thêm.

- [ ] HFE-01.1 Đối chiếu spacing, typography và semantic color của ba màn hình với mục 3.
- [ ] HFE-01.2 Dùng `PageShell`/shared primitives hiện có; thống nhất bề rộng local nav và page.
- [ ] HFE-01.3 Chuẩn hóa idle/loading/error/empty/forbidden, focus và status text theo shared API.
- [ ] HFE-01.4 Xác nhận light/dark/system hoạt động qua provider chung, không hardcode màu ở feature.

**Đạt khi:** không lệch theme khi đổi chế độ; không có shared source diff; screenshot desktop/mobile
của từng màn đã triển khai được đối chiếu với bản mẫu. Chỉ test hành vi, không snapshot từng class.

### HFE-02 — Khóa DTO, API và role matrix cho từng lát cắt

**READY; activation ghi riêng.** File: `api.ts`, `types.ts`, `permissions.ts` trong mỗi feature, test tương ứng.

- [ ] HFE-02.1 Lập fixture response/request từ controller/DTO/test hiện hữu; giữ field/nullability/enums.
- [ ] HFE-02.2 Test path `/v1`, method, query optional/encoding, bodyless mutation, envelope unwrap usage.
- [ ] HFE-02.3 Đối chiếu roles UI với downstream và Gateway; unknown/null role deny, 401/403 phân biệt.
- [ ] HFE-02.4 Ghi gate đang mở và bằng chứng đóng vào sổ dependency; không sửa role để vượt Gateway.

**Đạt khi:** contract test của lát cắt pass và mỗi command biết rõ request/response/error/role/flag.
Task này được cập nhật xuyên suốt; không coi một lần audit là nghiệm thu mọi API tương lai.

## 7. Pharmacy — tiếp tục source hiện tại

### P-01 — Catalog và điều hướng theo mẫu

**DONE — source, unit/contract tests và browser mock.** HFE-01/02 phần legacy đã được đối chiếu; File: `PharmacyNav`, `DrugCatalog`, `DrugTable`,
`pharmacy/layout.tsx`, `pharmacy/drugs/page.tsx`; test cùng thư mục.

- [x] P-01.1 Nav underline theo mẫu; giữ link Kê đơn cho ADMIN/DOCTOR, Outbox chỉ ADMIN;
  active route theo longest match, không vừa active `/new` vừa lookup.
- [x] P-01.2 Đặt title/Thêm thuốc, panel count + search + size, bảng 6 cột, pagination theo mục 3.
- [x] P-01.3 Giữ URL keyword/page/size, reset page khi search/size đổi, retry và stale-response guard;
  dữ liệu cũ không được hiện như kết quả đã tải của query mới.
- [x] P-01.4 Hết hàng (`stock=0`) có nhãn danger; tồn thấp theo `lowStockThreshold`, expiry theo helper;
  initials trung tính, không dùng màu riêng cho từng thuốc để ám chỉ ý nghĩa không có thật.
- [x] P-01.5 Test URL bookmark, page out-of-range, search không có kết quả, response đến sai thứ tự,
  quyền thêm thuốc; browser table/keyboard/2 themes.

**Đạt khi:** count/rows/page do backend quyết định; tìm đúng tên; mọi dữ liệu cũ vẫn có đường xem detail.

### P-02 — Chi tiết thuốc

**READY sau P-01.** File: `drug/DrugDetail.tsx`, `drugs/[drugId]/page.tsx` và tests.

- [ ] P-02.1 Layout thông tin thuốc, giá, tồn/ngưỡng, hạn, hoạt chất, manufacturer theo theme.
- [ ] P-02.2 Giữ UUID guard, 404, 403, correlation ID, retry; không biến 404 thành thuốc rỗng.
- [ ] P-02.3 Hiện slot điều chỉnh tồn đúng capability; refresh từ server sau mutation thành công.
- [ ] P-02.4 Test invalid-ID không gọi API, role read-only, expiry/low-stock và lỗi refresh.

**Đạt khi:** đọc đủ field backend, timestamp/date đúng loại, không trộn state thuốc cũ khi đổi route.

### P-03 — Tạo thuốc và điều chỉnh tồn

**READY sau P-02.** File: `CreateDrugForm`, `AdjustStockForm`, `drugFormValidation`, create route.

- [ ] P-03.1 Chuyển presentation sang shared Field/Button, lỗi gắn field và focus; giữ bounds DTO.
- [ ] P-03.2 Tạo thuốc: tên/unit bắt buộc, tiền không âm/precision đúng, stock nguyên không âm,
  expiry theo validation backend; backend field errors vẫn hiển thị được.
- [ ] P-03.3 Adjust: gửi delta có dấu, lý do theo contract, confirmation chỉ rõ tăng/giảm;
  khóa submit trùng, không optimistic stock, xung đột refetch trước thao tác mới.
- [ ] P-03.4 Test overflow, số thập phân stock, expiry invalid, âm làm vượt tồn, 403/409/network,
  mutation thành công rồi detail reload thất bại không tự gửi mutation lần nữa.

**Đạt khi:** không tạo số tồn/giá có tính authoritative ở browser; signed actor do backend xác định.

### P-04 — Lookup và chi tiết đơn thuốc

**DONE — compact lookup, status evidence, nullable record và regression tests.** File: `PrescriptionLookup`, `PrescriptionDetail`, `presentation.ts`, `types.ts`, lookup/detail routes.

- [x] P-04.1 Thêm compact lookup dưới catalog bằng prop/variant, giữ standalone lookup;
  input IDs duy nhất khi có nhiều instance, copy dễ hiểu, validation UUID và route navigation.
- [x] P-04.2 Giữ ba trạng thái độc lập: prescription lifecycle, dispense lifecycle, payment proof;
  đổi nhãn PENDING thành “Chờ xuất thuốc”, không làm mất `paymentConfirmed`.
- [x] P-04.3 Mirror recordId nullable nếu response V1 cho phép; hiển thị “Không có” phù hợp,
  không suy luận episode hoặc quyền thuốc từ field bị thiếu.
- [x] P-04.4 Detail hiển thị giá/lineTotal/totalAmount server, action slot đúng trạng thái;
  test ACTIVE/PENDING đã/chưa thanh toán, terminal, missing dispense và stale detail.

**Đạt khi:** không có API liệt kê Rx giả, không nói đã thanh toán chỉ vì status ACTIVE hoặc PENDING.

### P-05 — Form kê đơn legacy/V0

**READY sau P-04.** File: `CreatePrescriptionForm`, `PrescriptionLinesEditor`, `prescriptionFormValidation`, new route.

- [ ] P-05.1 Áp dụng form grid/line editor theo theme; giữ record/patient/doctor/department IDs rõ ràng.
- [ ] P-05.2 Ít nhất một dòng, drug ID không trùng, quantity nguyên dương, dosage bounded,
  prescribedDate đúng calendar và không tương lai; không gửi giá do người dùng tính.
- [ ] P-05.3 Hiện server field errors và signed-identity denial; không lấy doctorId từ accountId.
- [ ] P-05.4 Test add/remove line, lỗi từng dòng, serialization V0 không lẫn V1,
  duplicate submit và giữ input khi lỗi; success chuyển đúng prescriptionId server trả.

**Đạt khi:** V0 đang có không regress; không tự chọn hồ sơ/latest admission theo patient ID.

### P-06 — Hủy đơn và xuất thuốc

**READY local; DOCTOR cancel E2E chờ GW-01.** File: cancel/dispense dialogs, detail action composition, permissions/tests.

- [ ] P-06.1 Confirmation bằng primitive hiện có hoặc native dialog phù hợp; label, focus ban đầu,
  Escape/return-focus và trạng thái đang gửi rõ ràng.
- [ ] P-06.2 Cancel reason theo DTO; hiển thị releasedReservations từ response; không tự xóa đơn.
- [ ] P-06.3 Dispense bodyless PUT; canDispense UX phải xét role + ACTIVE/PENDING + paymentConfirmed;
  refresh lifecycle/stock từ server, không tự đánh dấu đã xuất hoặc trả tiền.
- [ ] P-06.4 Test terminal/replay/race 409, thất bại nghiệp vụ, 401/403, timeout chưa rõ outcome;
  xử lý lỗi refresh tách lỗi mutation, không tự retry command với intent mới.

**Đạt khi:** local test đủ matrix và real role smoke đạt; DOCTOR không được đổi thành PHARMACIST để qua Gateway.

### P-07 — Outbox replay và accessibility

**READY.** File: `outbox/OutboxReplayForm.tsx`, outbox route, tests.

- [ ] P-07.1 Giữ ADMIN gate, known event UUID, không dựng list/monitor outbox chưa có API.
- [ ] P-07.2 Chọn inline confirmation dạng section có heading và focus hợp lý;
  bỏ `alertdialog` nếu không thực sự modal. Không cần thêm shared modal chỉ cho task này.
- [ ] P-07.3 Submit một lần; success theo `replayed`, không claim consumer đã xử lý xong.
- [ ] P-07.4 Test cancel confirm, keyboard, double-click, 404 field focus, 403/correlation,
  network không rõ outcome và retry có chủ đích.

**Đạt khi:** thao tác recovery rõ mục đích, không kích hoạt chỉ do mount/refresh trang.

### P-08 — Care context V1 cho Pharmacy

**LOCAL + GATE BE-01; sau P-05/06.** File: Pharmacy types/api/validation/form và tests khi được kích hoạt.

- [ ] P-08.1 Tách shape V0/V1 theo discriminator thực: careContractVersion/context/episode/admission/priceCode;
  không gửi field V1 rỗng vào V0.
- [ ] P-08.2 Validate ADMISSION exact equality và OUTPATIENT admission null theo DTO;
  ID đầu vào phải đến từ hợp đồng được xác nhận hoặc nhập rõ, không infer.
- [ ] P-08.3 Chỉ nối public V1 sau khi authority/activation được nghiệm thu; admission command không
  tái sử dụng điều kiện “prepaid V0” một cách máy móc.
- [ ] P-08.4 Contract fixture V0/V1, quyền/race medical discharge/clearance; ghi bằng chứng GW/backend
  trước khi render choice V1 cho người dùng.

**Đạt khi:** V0 giữ nguyên tương thích, V1 không mở khi mới có internal kernel/fixture.

## 8. Report — workspace và báo cáo

### R-01 — Workspace theo tab, route mỏng

**READY sau HFE-01/02 phần Report.** File: `ReportWorkspace.tsx`, `reportQuery.ts`, `/reports/page.tsx`.

- [x] R-01.1 Tab Hàng ngày / Hàng tháng / Thuốc xuất nhiều; URL `view=daily|monthly|medicines`,
  default monthly theo mẫu, unknown view về default, Back/Forward nhất quán.
- [x] R-01.2 Page giữ RoleGate ADMIN/MANAGER, compose workspace; chỉ active view gọi query.
- [ ] R-01.3 Query ngày/tháng/năm/department phản ánh lần submit; input draft tách khỏi result scope;
  tab đổi giữ state đã chọn theo URL, không làm ba view gọi API cùng lúc.
- [x] R-01.4 Test deep link, invalid query, back/forward, denied role, tab keyboard và pending transition.

**Đạt khi:** reports page không có form logic/API call; có một vùng báo cáo rõ ràng theo loại đã chọn.

### R-02 — Validation và vòng đời request thống nhất trong Report

**READY; làm trước R-03…05.** File: report `validation.ts`, `presentation.ts`, query helper, ba view và tests.

- [ ] R-02.1 Calendar-valid date, range from<=to, month/year/limit bounds và UUID department;
  ngày mặc định theo local date, không `toISOString().slice(0,10)` gây lệch ngày.
- [ ] R-02.2 Lưu submitted request identity, latest-request guard/unmount guard;
  kết quả query cũ không được render dưới tiêu đề query mới sau lỗi.
- [ ] R-02.3 Idle/loading/error/empty, retry đúng last submitted request; 401 redirect,
  403 giữ context, error code/correlation hỗ trợ tra cứu.
- [ ] R-02.4 Test deferred responses đảo thứ tự, đổi tab khi pending, lỗi sau success,
  sửa filter chưa submit và retry không lấy nhầm draft mới.

**Đạt khi:** mỗi result có bộ lọc nguồn chính xác; không lặp request vô hạn hoặc ẩn dữ liệu 0 hợp lệ.

### R-03 — Báo cáo tháng: chart, tổng hợp, daily table

**DONE — monthly source, tests và browser mock; R-01/02 các view khác tiếp tục riêng.** File: `MonthlyReportView`, `MonthlyRevenueChart`, `DailyDetailsTable`, tests.

- [x] R-03.1 Dựng filter panel + chart/summary 70:30 và daily table theo mẫu; mobile stack.
- [x] R-03.2 Chart dùng toàn bộ `dailyDetails` của 28/29/30/31 ngày; trục/nhãn đọc được,
  màu primary theo theme, all-zero domain không chia 0; table là alternative đầy đủ.
- [x] R-03.3 Render `totalRevenue`, `invoiceCount` trực tiếp từ response, không tính lại từ chart;
  month/year/department heading lấy từ response/submitted scope đã kiểm tra.
- [x] R-03.4 Dùng SVG thuộc tính hình học hoặc HTML semantic + Tailwind, không chart/UI dependency,
  không style/CSS module riêng; tooltip có keyboard/touch nếu thêm interaction.
- [x] R-03.5 Test leap year/all-zero/large values, result failure/race, token theme;
  giữ formatDecimal, không gắn currency chưa có trong legacy DTO.

**Đạt khi:** biểu đồ và bảng khớp response, tổng không đổi khi ẩn/hiện phần trình bày.

### R-04 — Báo cáo ngày

**READY sau R-01/02.** File: `DailyReportView`, tests.

- [ ] R-04.1 Filter ngày + khoa theo style chung; tiêu đề/phạm vi phản ánh reportDate/departmentId.
- [ ] R-04.2 Bốn số liệu gọn theo source, không thêm biểu đồ/KPI hoặc so sánh ngày chưa có contract.
- [ ] R-04.3 Dữ liệu 0 vẫn là report thành công; lỗi không thành 0; missing department name giữ UUID.
- [ ] R-04.4 Test ngày bắt buộc/không hợp lệ, 0, 401/403, correlation/retry và stale response.

**Đạt khi:** giữ hành vi daily đã có và thống nhất request/error semantics với R-02.

### R-05 — Thuốc xuất nhiều

**READY sau R-01/02.** File: `TopMedicinesView`, validation/tests.

- [ ] R-05.1 Filter inclusive from/to, optional department, limit 1–50; rank list/table dễ đọc.
- [ ] R-05.2 Tên và `totalQuantity` từ Report; không gọi Pharmacy để tự tổng hợp hoặc đoán tên thiếu.
- [ ] R-05.3 Empty state đúng range đã submit; giữ drugId đầy đủ có thể xem/copy;
  không thêm đơn vị thuốc nếu response không có.
- [ ] R-05.4 Test empty, thứ tự response, rank limit, date/timezone, lỗi sau kết quả cũ.

**Đạt khi:** FE không tạo ranking từ Rx/dispense khác context và không hiển thị quantity như doanh thu.

### R-06 — Financial snapshots V2

**LOCAL + GATE BE-03; sau R-01/02.** File: report types/api, `financial/FinancialSnapshotView`, financial route sau gate.

- [ ] R-06.1 Mirror FinancialSnapshotReportDTO, coverage/capabilities DTO riêng;
  daily query `from/to`, monthly query `year`, không reuse serializer legacy.
- [ ] R-06.2 Render cashReceived/depositLiability/earnedRevenue/refunds/outstandingReceivable,
  currency/reportZone và generationId/sourceObservedAt/reconciledAt/publishedAt/snapshotOnly.
- [ ] R-06.3 Hiển thị coverage AVAILABLE_SNAPSHOT / UNAVAILABLE_HISTORY / UNAVAILABLE_PUBLICATION
  đúng response; 404 chưa phân loại thì lỗi không khả dụng, không tự gọi là kỳ có 0.
- [ ] R-06.4 Tách rõ legacy revenue với earned revenue V2; chỉ hiển thị recognition mà backend
  đã ghi nhận theo quyết toán hoàn tất, không tính tại browser.
- [ ] R-06.5 Test số 0 hợp lệ, unavailable, history gaps, currency, reportZone, MANAGER không gọi
  ADMIN capabilities; route chỉ bật khi snapshot + flags + role smoke đạt.

**Đạt khi:** số tài chính có provenance/coverage; không khôi phục lịch sử bằng fixture demo hoặc suy từ KPI.

### R-07 — Operational và Surgery snapshots V2

**LOCAL + GATE BE-03.** File: `operational/OperationalSnapshotView`, types/api, operational route sau gate.

- [ ] R-07.1 Mirror from/to/department, generationId/reconciledAt/snapshotOnly và days.metrics;
  hai endpoint daily/surgery có mục đích riêng, không import feature Surgery.
- [ ] R-07.2 Map metric codes được contract hỗ trợ thành nhãn/đơn vị; unknown metric không bị gán nghĩa.
- [ ] R-07.3 Không dựng occupancy/LOS/capacity hiện tại từ admission ban đầu hoặc series thiếu coverage.
- [ ] R-07.4 Role ADMIN/MANAGER/DOCTOR theo controller; route này không bị chặn nhầm bởi legacy
  page RoleGate ADMIN/MANAGER. Test role, unavailable, 0, thiếu metric và stale response.

**Đạt khi:** chỉ KPI có publication được dùng; không biến Snapshot thành bảng vận hành realtime.

### R-08 — Operator rebuild/publication/rollback

**LOCAL + GATE GW-03/BE-03.** File: `operator/RebuildWorkspace`, `PublicationWorkspace`, types/api, admin routes sau gate.

- [ ] R-08.1 Tách financial rebuild (`POST` body from/to, batch `days` query) với operational
  (`POST` không body, batch DTO); GET operational trả publication candidate, không ép về progress DTO financial.
- [ ] R-08.2 Progress/resume bounded batch theo generation server; không polling liên tục vô hạn,
  không chạy rebuild khi mount; ADMIN only, thao tác có xác nhận.
- [ ] R-08.3 Publication/rollback dùng expected pointer và DTO từng controller; không auto-publish khi VERIFIED;
  409 refetch pointer, giữ intent, yêu cầu xác nhận lại nếu state đổi.
- [ ] R-08.4 Test ADMIN/denied roles, body shape, duplicate click, uncertain outcome, resume và pointer conflict;
  live acceptance chỉ sau reviewed Gateway ingress, published reads và rollback proof.

**Đạt khi:** operator không tự thay dataset đang public sau một lần refresh trang.
Binary archive/restore và CSV/PDF export chưa thuộc task này; nếu bổ sung phải có task/contract riêng.

## 9. Surgery — đọc trước, command sau, release theo capability

### S-01 — Feature contract và read facade

**READY cho source chưa mount; route chờ BE-02/GW-02.** File: surgery types/api/permissions/presentation, owner pointer, tests.

- [ ] S-01.1 Mirror `SurgeryCaseBoardItem`, `SurgeryCaseDetails` và enums; xác nhận nullability từ
  query mapper/test. `checklistSnapshotId`, `checklistItemId`, `readinessSnapshotId` giữ đúng wire names.
- [ ] S-01.2 List query department/status/requestedFrom/Until/scheduledFrom/Until/page/size;
  Instant phải có offset, UUID validation; GET qua shared api.
- [ ] S-01.3 Permission presentation theo role và trạng thái; unknown trạng thái fail closed cho command,
  vẫn hiển thị thông tin đọc có thể hiểu được.
- [ ] S-01.4 Contract test query/response/role, default-disabled behavior; fixture test tách khỏi production.

**Đạt khi:** không tạo route/nav để quảng bá Surgery chỉ vì API facade/test đã pass.

### S-02 — Danh sách ca

**READY local sau S-01; mount sau BE-02.** File: `case/SurgeryCaseList`, list route sau gate.

- [ ] S-02.1 Board/table procedureCode, status, revision, requestedAt, plannedStart/End;
  backend board chưa có tên bệnh nhân/phòng, không tự bổ sung cột bằng inferred IDs.
- [ ] S-02.2 Filter URL + pagination, empty/loading/error/403; date range/local input sang Instant đúng offset.
- [ ] S-02.3 Giữ latest-request guard, deep link caseId; không lấy mọi detail để giả lập theatre occupancy.
- [ ] S-02.4 Test filter serialization, timezone, page correction, terminal status, denied role và error recovery.

**Đạt khi:** danh sách khớp DTO board, không nói phòng rảnh chỉ vì không có ca trong page hiện tại.

### S-03 — Chi tiết ca theo mẫu đã chốt

**READY local sau S-01; mount sau BE-02.** File: `case/SurgeryCaseDetail`, `schedule/ReadinessPanel`, detail route sau gate.

- [ ] S-03.1 Metadata từ `caseDetails`, lịch từ `plannedSchedule`, timeline từ status/history,
  result panel khi có `actualResult`; không hardcode case/demo state.
- [ ] S-03.2 Readiness dùng evaluatedAt/validUntil/snapshotValidNow/blockingReasons;
  hết hạn hiển thị cũ, không tự làm mới authority hay xác nhận quyền bắt đầu.
- [ ] S-03.3 Checklist/consents chỉ phản ánh facts có thật; financial/team authority thiếu thì chưa xác minh;
  xử lý null schedule/result/readiness và empty histories rõ ràng.
- [ ] S-03.4 UUID hiển thị rút gọn có cách xem đầy đủ; date/Instant đúng timezone;
  test REQUESTED, PREOP, READY, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED và stale evidence.

**Đạt khi:** cùng bố cục mẫu, mọi nhãn đạt/khóa có nguồn DTO/capability; không tự tạo quyền lâm sàng.

### S-04 — Command intent, idempotency và conflict

**LOCAL + GATE SH-01; sau S-01.** File: `commandState.ts`, api mutation methods sau shared header support, tests.

- [ ] S-04.1 Model idle/confirming/submitting/succeeded/failed/unknown-outcome theo từng command;
  key gắn intent + target + body/revisions, không tái dùng key cho payload thay đổi.
- [ ] S-04.2 Retry cùng intent giữ key; creation key bằng surgeryRequestId; không gửi key bằng body/query,
  không dùng raw fetch hoặc private wrapper hack.
- [ ] S-04.3 Chặn submit kép; conflict refresh detail rồi so sánh revision, không auto-reapply thay đổi;
  timeout phải kiểm tra trạng thái/explicit same-key retry, không tự sinh command mới.
- [ ] S-04.4 Test same-key identical intent, changed payload/new intent, 409/403/401, timeout,
  response tới sau khi đổi ca và success rồi refetch lỗi.

**Đạt khi:** không có mutation mất key, không gửi actor/readiness/clearance do browser tự xác nhận.
Persistence qua reload chỉ thêm khi có yêu cầu rõ; không lưu dữ liệu consent/y khoa vào localStorage.

### S-05 — Intake / tạo ca

**LOCAL + GATE SH-01/GW-02/BE-02; sau S-04.** File: `case/CreateSurgeryForm`, validation/api/types; route nếu intake UX cần.

- [ ] S-05.1 Mirror CreateSurgeryRequest: request/episode/admission/record/patient/department/requester,
  procedure/indication/priority/requestedAt/templateRevision/plannedItems, không đoán field từ Core response.
- [ ] S-05.2 Validate ADMISSION identity equality, outpatient admission null, planned items unique và bounded,
  quantity/priceCode đúng contract; không tự định giá Billing hoặc tự chọn template mới nhất.
- [ ] S-05.3 Source referral phải exact, có provider authority; chỉ hiện form/entrypoint đã được chọn cho intake.
- [ ] S-05.4 Test stable request key, exact replay, changed intent rejection, signed staff/role,
  nguồn không khả dụng và success chỉ dùng caseId backend trả.

**Đạt khi:** creation không biến thành cách bỏ qua referral, prescriber hay patient/episode relationship.

### S-06 — Bắt đầu pre-op và checklist

**LOCAL + GATE SH-01/GW-02/BE-02; sau S-03/04.** File: `preop/ChecklistEditor`, detail action slot, validation/api.

- [ ] S-06.1 Begin pre-op dùng expectedCaseRevision và key; refetch pinned checklist sau success.
- [ ] S-06.2 Update item gửi expected case/snapshot/item revision; status và evidence pair đúng DTO.
- [ ] S-06.3 SATISFIED yêu cầu evidenceReferenceId/evidenceRevision; PENDING không kèm evidence;
  public DTO hiện từ chối NOT_APPLICABLE, không suy từ internal safety policy kernel rằng UI được gửi N/A.
- [ ] S-06.4 Test missing/stale evidence, revision conflict, nurse capability, terminal denial;
  không đánh dấu checklist hoàn thành do tổng số checkbox FE.

**Đạt khi:** backend là nguồn mandatoryComplete; dữ liệu chưa xác minh không được tô thành success.

### S-07 — Ghi nhận consent

**LOCAL + GATE SH-01/GW-02/BE-02; sau S-04.** File: `preop/ConsentRecorder`, validation/types/api, tests.

- [ ] S-07.1 Form expectedCaseRevision, consentType, signerId, signerType, evidenceDocumentId;
  recorder/authority lấy từ backend/JWT, không cho tự tick “đã đủ quyền giám hộ”.
- [ ] S-07.2 Nêu rõ bằng chứng phải đã tồn tại; không invent upload/signature endpoint;
  read view chỉ render redacted consent fields được trả.
- [ ] S-07.3 Handle created/replayed outcome theo contract; same intent giữ key, conflict refetch.
- [ ] S-07.4 Test source unavailable, wrong relationship, stale document, signed actor denied,
  role NURSE và terminal state; không render revoke command khi chưa có public controller.

**Đạt khi:** UI không đóng vai trò xác nhận pháp lý hoặc suy tuổi/quan hệ từ mã bệnh nhân.

### S-08 — Lịch dự kiến và đội mổ

**LOCAL + GATE SH-01/BE-02; sau S-03/04.** File: `schedule/ScheduleEditor`, validation/api/types.

- [ ] S-08.1 Input room UUID, start/end, team staffId/role; expectedCaseRevision và scheduleRevision
  theo DTO, duplicate staff không được gửi, bounds team theo backend.
- [ ] S-08.2 Serialize Instant có timezone; validate end>start; hiển thị timezone đang nhập/đọc.
- [ ] S-08.3 Lưu schedule là draft, không tuyên bố phòng/đội đã giữ chỗ; không chọn phòng rảnh từ dữ liệu page.
- [ ] S-08.4 Test resource conflict, actor scope, revoked capability, stale schedule,
  thời gian qua ngày và sửa lịch khi lifecycle không cho phép.

**Đạt khi:** draft/status đọc lại từ response; approval/finalization là thao tác riêng.

### S-09 — Readiness, finalize, start

**LOCAL + GATE SH-01/GW-02/BE-02; sau S-06/07/08.** File: `ReadinessPanel`, `execution/StartSurgeryDialog`, api/tests.

- [ ] S-09.1 Ba command riêng: evaluate, finalize, start; request expectedCaseRevision>=0 và
  expectedScheduleRevision>0 theo SurgeryLifecycleRequest, không nhận extra readiness fields.
- [ ] S-09.2 UX role matrix: MANAGER có finalize, không evaluate/start; snapshot chỉ cung cấp trạng thái tham khảo.
- [ ] S-09.3 Hiển thị blocking reasons/freshness; luôn gọi backend khi người dùng xác nhận,
  không skip validate vì FE đang thấy READY hoặc có schedule.
- [ ] S-09.4 Test expired/revoked evidence, conflict room/team, mất clearance, exact retry,
  pending command và state chuyển terminal từ lần refetch khác.

**Đạt khi:** chỉ response thành công mới cập nhật trạng thái hiển thị; không có “force start”.

### S-10 — Hoàn tất và hủy ca

**LOCAL + GATE SH-01/GW-02/BE-02; sau S-09.** File: `CompleteSurgeryForm`, `CancelSurgeryDialog`, detail result/history.

- [ ] S-10.1 Complete request dùng `outcomeCode`, response result dùng `treatmentOutcomeCode`;
  procedure/method/complication, actualStart/End, performed item IDs/itemCode/priceCode/quantity theo DTO.
- [ ] S-10.2 Chỉ validate format/invariants contract; không tự tạo danh mục kết quả y khoa, không tính phí tại FE.
- [ ] S-10.3 Cancel pre-start có reason/expectedCaseRevision/key; giữ terminal read-only;
  không mở post-start abort khi không có public business branch.
- [ ] S-10.4 Test duplicate item, time bounds, conflict, already-complete/cancelled, retry và lỗi refresh;
  success thông báo Surgery đã nhận, không claim Billing/Report/Notification đã đồng bộ tức thì.

**Đạt khi:** terminal state nhất quán sau refetch, danh sách/history cập nhật theo backend; không phát event từ FE.

## 10. Nghiệm thu và thứ tự thực thi

### Q-01 — Kiểm tra source và regression trong scope Huy

**READY theo từng batch; đóng sau toàn bộ source của release.** File: tests cạnh feature và evidence trong plan.

- [ ] Q-01.1 Chạy focused tests của task; ưu tiên business/contract/race/role/error,
  không dùng snapshot class hoặc test lặp chính implementation để tăng số lượng.
- [ ] Q-01.2 `pnpm typecheck`, `pnpm lint`, `pnpm test`, `pnpm build` đạt trên candidate;
  ghi rõ test nào mock API, test nào thật, exit code và lỗi nếu có.
- [ ] Q-01.3 Kiểm tra diff: không sibling import/raw fetch/service-port URL, không sample records trong
  production, không sửa shared/theme/Gateway ngoài authority, route pages vẫn mỏng.
- [ ] Q-01.4 Review regression workflow cũ; cập nhật service guides/task evidence khi thực sự đổi trạng thái.

**Đạt khi:** required checks xanh và scope đúng; test mock không được tính là Gateway/backend E2E.

### Q-02 — Browser, role smoke và release

**VERIFY; làm theo capability đã triển khai, không đợi tất cả V2 mới nghiệm thu legacy.**

- [ ] Q-02.1 Kiểm tra browser desktop/tablet/320–360px, Light/Dark/System, keyboard/focus,
  loading/error/empty, long UUID/name, số lớn; chụp evidence đối chiếu mẫu.
- [ ] Q-02.2 Có thể mock ở browser để kiểm layout/request behavior; gắn nhãn evidence đó là mock.
  Chạy riêng Gateway thực với signed tokens và các role allowed/denied, không seed fake session vào production.
- [ ] Q-02.3 Với Surgery/V2, xác nhận từng flag/authority/provider/ingress; chỉ sau đó thêm route và
  phối hợp SH-02 cho sidebar. Giữ disabled capability không gọi API, deep link cũng có gate rõ ràng.
- [ ] Q-02.4 Ghi acceptance/release/rollback; đóng handoff chỉ khi cả producer/consumer đạt;
  commit/push/PR khi người dùng yêu cầu, không ghi “đã release” chỉ vì build xanh.

**Đạt khi:** report rõ phần đã chạy thật và phần còn gate; không có nút có vẻ dùng được nhưng không có contract.

### Thứ tự code đề xuất

| Batch | Task | Kết quả có thể xem/kiểm |
|---|---|---|
| 1 | HFE-01/02 phần legacy → P-01 → P-04 phần compact lookup | Pharmacy catalog theo mẫu, theme đúng, route/filter giữ hoạt động |
| 2 | R-01 → R-02 → R-03 | Reports tabs + monthly chart/table thật, retry/stale state đúng |
| 3 | P-02/03/05/06/07, R-04/05 | Các workflow cũ đồng nhất và có regression tests |
| 4 | S-01 → S-02/03, song song theo thứ tự độc lập R-06/07 local | Surgery read presenters và V2 read presenters, chưa public khi gate mở |
| 5 | S-04 → S-05/06/07/08 → S-09 → S-10 | Command slices; dispatch sau SH-01, activate sau GW/BE gates |
| 6 | P-08, R-08 theo gate; Q-01/02 xuyên suốt | Admission Rx/V2 operator khi hợp đồng đủ; evidence release theo capability |

Thứ tự này không cấp quyền tạo subagent hoặc sửa file owner khác. Không chia việc đồng thời trên cùng
`api.ts/types.ts`, Rx detail, Report workspace hay Surgery detail khi chưa giao ownership file rõ ràng.

### Liên hệ với plan Pharmacy trước

| Checklist cũ | Task tiếp tục ở plan này |
|---|---|
| PH-FE-00/01/02 | HFE-01/02 + P-01; tái sử dụng facade/permissions/URL state đã có |
| PH-FE-03/04/05 | P-02/03; cải thiện UI và test, không tạo lại API |
| PH-FE-06/07 | P-04/05; V1 chỉ ở P-08 |
| PH-FE-08/09 | P-06 + GW-01 real-role smoke |
| PH-FE-10 | P-07 confirmation/accessibility |
| PH-FE-11/12 | Test theo task + Q-01/02; harness đã tồn tại |

## 11. Cách cập nhật tiến độ

Mỗi lần code ghi một hàng, tick subtask đã có bằng chứng. Khi chỉ hoàn thành local presenter của
task LOCAL + GATE, giữ activation subtask và task chính mở; không đếm lại task backend.

| Ngày / commit | Task/subtask | File đã đổi | Kiểm tra và kết quả | Gate còn mở |
|---|---|---|---|---|
| 2026-10-12 / baseline `c110671b` | Lập plan + lưu mẫu đã duyệt | Tài liệu; chưa sửa `frontend/src` | Đọc source/rules/controller/Gateway; kiểm link/diff tài liệu | SH-01/02, GW-01/02/03, BE-01/02/03 |

Không chạy lại toàn bộ backend chỉ vì chỉnh tài liệu/UI. Chạy đúng kiểm tra của code được thay đổi;
chỉ ghi “PASS” sau khi có kết quả thực của lượt đó.

## 12. Checkpoint triển khai batch đầu — 2026-10-12

### Đã đóng và phần đã làm nhưng chưa đóng toàn task

| Task | Trạng thái | Bằng chứng / phần còn lại |
|---|---|---|
| P-01 | DONE — FE local | Catalog 6 cột, semantic theme, native role nav, URL search/size/page, latest request identity; role/filter/stale/page tests và browser mock |
| P-04 | DONE — FE local | Compact/standalone lookup dùng IDs riêng; PENDING không ám chỉ chưa thanh toán; recordId nullable; payment/terminal/missing-dispense/stale-detail tests |
| R-03 | DONE — FE local | Responsive SVG chart, server totals, full calendar table; leap-year, zero/large numbers, failures và browser layout |
| HFE-01 | IN PROGRESS | Pharmacy/Report dùng shared theme/primitives; Surgery chưa có production UI nên chưa đóng task toàn context |
| HFE-02 | IN PROGRESS | Pharma/Report legacy facade tests + đối chiếu roles/DTO; chưa chứng minh V2/Surgery activation |
| R-01 | IN PROGRESS | View URL/nav/active view và monthly filters/Back/Forward đạt; URL persistence cho daily/top-medicines còn mở ở R-01.3 |
| R-02 | IN PROGRESS | Monthly validation/request identity/retry/401/403/correlation/unmount guard đạt; Daily/Top vẫn cần dùng semantics tương ứng |
| Q-01/02 | IN PROGRESS | Candidate checks + isolated browser đã chạy; signed-token Gateway smoke và full release còn mở |

### Thay đổi cụ thể

- Pharmacy pages compose qua context-local `PharmacyPageShell`; nav nằm dưới title và cùng bề rộng
  PageShell. Dashboard/shared shell không đổi. Form/detail cũ giữ route, role và nghiệp vụ hiện có.
- Catalog data gắn đúng request key, không hiện count/rows cũ sau khi URL đổi hoặc lần đọc mới lỗi.
- Rx response `recordId` chấp nhận null đúng hợp đồng V1; không tạo input V1, không suy ra admission,
  và không bỏ payment proof chỉ vì thiếu medical record.
- Monthly filter draft tách result scope; retry giữ query đã gửi, request cũ/unmount không commit result.
- Report query-only navigation dùng Native History API để cập nhật `useSearchParams` và giữ Back/Forward,
  phù hợp [Next.js documented integration](https://nextjs.org/docs/app/getting-started/linking-and-navigating#native-history-api).
  Browser acceptance đã kiểm đúng hành vi này; không thay shared routing/config hay mở V2.

### Kiểm tra thực tế

- `pnpm typecheck`: PASS, exit 0.
- `pnpm lint`: PASS, exit 0, không error/warning trên lượt cuối.
- `pnpm test`: **81/81 PASS, 16 test files**; baseline trước batch là 22 tests / 5 files,
  batch thêm 59 tests / 11 files. Đây là unit/component/contract tests với mocked feature HTTP boundary.
- `pnpm build`: PASS, static/dynamic routes được build; không có Surgery/V2 route mới.
- Production-build browser smoke: **PASS với API fixture trong context riêng**, ADMIN/DOCTOR/PHARMACIST/MANAGER,
  20 tổ hợp theme/layout ở 1366/1024/768/360/320px; system theme theo media change, keyboard focus,
  search/UUID validation, monthly submit/Back/Forward, full calendar và inactive-view behavior.
- Không có page runtime error, không overflow ngang toàn trang; table có vùng cuộn riêng. Screenshot
  mobile được kiểm sau khi off-canvas sidebar transition kết thúc; không sửa shared sidebar.
- Role/session trong browser smoke là fixture riêng và mọi `/api/*` được intercept; **không phải signed-token
  Gateway/backend nghiệm thu**. Không ghi fixtures/session demo vào production source hoặc database.
- Diff source chỉ thuộc Pharmacy/Report features/routes. Không đổi package/lock/config, shared lib/components,
  Gateway hoặc backend. Test server tạm được dừng sau smoke. Chưa commit/push.

Lượt thử đầu có lỗi truy vấn test tìm button theo label trong lúc loading và lỗi type ở option test;
đã sửa test selector/type đúng behavior, không giảm assertion. Browser đã phát hiện và xử lý luồng
monthly URL submit trước khi ghi PASS. Không ghi các lượt lỗi đó là thành công.

### Batch tiếp theo

Tiếp tục R-01.3 và R-02 cho Daily/Top, rồi R-04/R-05; sau đó P-02/P-03/P-05/P-06/P-07.
Surgery/V2 đi theo gate đã ghi, có thể làm local DTO/presenter nhưng chưa tự mở route hoặc sửa shared/Gateway.

