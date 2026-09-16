# Pharmacy Frontend Implementation Plan

> Kế hoạch triển khai frontend cho bounded context `pharmacy`.
>
> **Nguồn chuẩn là code backend hiện tại**, không phải thiết kế dự kiến. Kế hoạch này được đối chiếu
> tại repository HEAD `808ffe6900f7eb1a07c9bfd8a2b1cfade7779b44`; commit gần nhất chạm
> `backend/pharmacy-service` là `1bd7fabb18b825fb04204f3ba832364fae48621d`.

## 1. Mục tiêu và giới hạn

Mục tiêu là xây một frontend pharmacy có thể được AI triển khai theo từng task nhỏ, với mỗi task có:

- contract backend cụ thể;
- file được phép tạo/sửa;
- dependency rõ ràng;
- hành vi UI và trạng thái lỗi;
- acceptance criteria;
- kiểm tra bắt buộc.

Phạm vi gồm:

1. Danh mục thuốc và tìm kiếm phân trang.
2. Chi tiết thuốc, tạo thuốc, điều chỉnh tồn kho.
3. Tạo và xem chi tiết đơn thuốc.
4. Hủy đơn thuốc.
5. Xuất thuốc sau khi có bằng chứng thanh toán.
6. Replay một outbox event đã biết id cho ADMIN.

Không thuộc phạm vi:

- tạo API backend mới;
- gọi trực tiếp cổng `8085`;
- truy vấn patient/clinical/organization từ DB hoặc service port;
- tự invent API danh sách đơn thuốc, danh sách outbox hoặc autocomplete nhân viên;
- thay thế xác thực backend bằng việc ẩn/hiện nút trên UI;
- thêm component library hoặc data-fetching library.

## 2. Thứ tự nguồn sự thật

Khi tài liệu và code khác nhau, AI phải dùng thứ tự sau:

1. Controller thật:
   - `backend/pharmacy-service/.../web/DrugController.java`
   - `backend/pharmacy-service/.../web/PrescriptionController.java`
   - `backend/pharmacy-service/.../web/PharmacyOutboxAdminController.java`
2. Request/response record thật trong `application/dto/request` và `application/dto/response`.
3. Enum thật trong `domain/model/enums`.
4. Rule thật trong `application/service` và `domain/model`.
5. Web-slice test thật trong `src/test/.../web`.
6. `pharmacy-service.http` và `pharmacy.http` để lấy request mẫu.
7. `docs/ai/services/pharmacy.md` và backend spec chỉ dùng để giải thích bối cảnh.

Nếu controller chưa có endpoint thì frontend không được gọi endpoint đó, dù port hoặc spec có nhắc tới.

## 3. Hiện trạng frontend đã quét

### 3.1 Stack và quy ước đang dùng

- Next.js `16.2.11`, React `19.2.4`, App Router.
- TypeScript strict, alias `@/*`.
- Tailwind CSS v4 qua `@tailwindcss/postcss` và `@import "tailwindcss"`.
- pnpm workspace.
- `/api/*` được Next rewrite sang gateway qua `GATEWAY_URL`.
- JWT hiện nằm trong `localStorage`.
- Mọi HTTP phải đi qua `src/lib/api.ts`.
- Feature không import feature khác; composition chỉ xảy ra trong `app/`.
- Không dùng TanStack Query/SWR, không dùng component library.
- Shared UI hiện có `PageShell`, `DashboardHeader`, `Pagination` và route loading chung.
- `src/features/pharmacy/` mới chỉ là placeholder.

### 3.2 Hạn chế kiến trúc hiện tại

JWT nằm trong `localStorage`, vì vậy Server Component không đọc được token để gọi pharmacy API.
Trong phase hiện tại:

- `page.tsx` và layout giữ là Server Component, chỉ làm route/composition/metadata;
- component thực hiện API, form, filter và mutation là Client Component;
- không thêm Server Action hoặc Route Handler chỉ để vòng qua `src/lib/api.ts`;
- không thêm middleware/proxy auth dựa trên token hiện tại vì server không nhìn thấy `localStorage`.

Migration sang httpOnly cookie là một kiến trúc riêng, không được trộn vào feature pharmacy. Khi migration
đó được duyệt, pharmacy có thể chuyển read flow sang Server Components và dùng Route Handler/Server
Action có kiểm tra quyền ở server.

### 3.3 Thay đổi nền tảng cần thiết trước pharmacy

`src/lib/api.ts` cần hỗ trợ mutation không có body vì hai endpoint thật không nhận body:

- `PUT /prescriptions/{id}/dispense`
- `POST /admin/outbox/{eventId}/replay`

Thiết kế API wrapper mục tiêu:

```ts
get<T>(path: string): Promise<T>
post<T>(path: string, data?: unknown): Promise<T>
put<T>(path: string, data?: unknown): Promise<T>
del<T>(path: string): Promise<T>
```

Chỉ set `Content-Type: application/json` và chỉ tạo `body` khi `data !== undefined`.

Nên thêm `import "client-only"` vào module session/auth/API client để Next.js báo lỗi build nếu một
Server Component vô tình import client data layer.

## 4. Contract backend đang chạy

Base path qua gateway: `/api/v1/pharmacy`.

### 4.1 Endpoint và role

| Method | Path | Response data | Role từ controller |
|---|---|---|---|
| GET | `/drugs?keyword&page&size` | `PageResult<DrugDTO>` | ADMIN, DOCTOR, PHARMACIST |
| GET | `/drugs/{id}` | `DrugDTO` | ADMIN, DOCTOR, PHARMACIST |
| POST | `/drugs` | `DrugDTO`, HTTP 201 | ADMIN, PHARMACIST |
| PUT | `/drugs/{id}/stock` | `DrugDTO` | ADMIN, PHARMACIST |
| POST | `/prescriptions` | `PrescriptionDTO`, HTTP 201 | ADMIN, DOCTOR |
| GET | `/prescriptions/{id}` | `PrescriptionDTO` | ADMIN, DOCTOR, PHARMACIST |
| PUT | `/prescriptions/{id}/cancel` | `CancelPrescriptionResult` | ADMIN, DOCTOR |
| PUT | `/prescriptions/{id}/dispense` | `DispenseDTO` | ADMIN, PHARMACIST |
| POST | `/admin/outbox/{eventId}/replay` | `OutboxReplayResult` | ADMIN |

Không có endpoint hiện hành cho:

- list/search prescription;
- prescription by patient;
- list dispense slips;
- list/quarantine outbox events;
- dashboard thống kê pharmacy;
- cập nhật metadata thuốc;
- tìm patient/record/doctor/department theo tên.

### 4.2 TypeScript wire types bắt buộc

`BigDecimal` được serialize thành JSON number. Frontend chỉ format để hiển thị; mọi phép tính giá,
`lineTotal` và `totalAmount` phải coi backend là nguồn chuẩn.

```ts
export type PrescriptionStatus =
  | "ACTIVE"
  | "FULFILLED"
  | "CANCELLED"
  | "EXPIRED"
  | "DISPENSE_FAILED";

export type DispenseStatus =
  | "PENDING"
  | "DISPENSED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED";

export interface DrugDTO {
  drugId: string;
  drugName: string;
  activeIngredient: string | null;
  unit: string;
  price: number;
  stockQuantity: number;
  expiryDate: string;
  manufacturer: string | null;
  lowStockThreshold: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDrugRequest {
  drugName: string;
  activeIngredient?: string | null;
  unit: string;
  price: number;
  stockQuantity: number;
  expiryDate: string;
  manufacturer?: string | null;
  lowStockThreshold?: number | null;
}

export interface AdjustStockRequest {
  quantity: number;
  reason?: string | null;
}

export interface PrescriptionLineRequest {
  drugId: string;
  quantity: number;
  dosage?: string | null;
}

export interface CreatePrescriptionRequest {
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  prescribedDate: string;
  lines: PrescriptionLineRequest[];
}

export interface PrescriptionLineDTO {
  lineId: string;
  drugId: string;
  drugName: string | null;
  quantity: number;
  unitPrice: number;
  dosage: string | null;
  lineTotal: number;
}

export interface PrescriptionDTO {
  prescriptionId: string;
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  prescribedDate: string;
  totalAmount: number;
  lines: PrescriptionLineDTO[];
  status: PrescriptionStatus;
  dispenseStatus: DispenseStatus;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CancelPrescriptionRequest {
  reason: string;
}

export interface CancelPrescriptionResult {
  prescriptionId: string;
  status: PrescriptionStatus;
  releasedReservations: number;
  cancelledAt: string;
}

export interface DispenseDTO {
  dispenseId: string;
  prescriptionId: string;
  status: DispenseStatus;
  dispensedAt: string | null;
  dispensedBy: string | null;
  failureReason: string | null;
}

export interface OutboxReplayResult {
  eventId: string;
  replayed: boolean;
}
```

### 4.3 Validation lấy từ request record

#### Tạo thuốc

- `drugName`: bắt buộc, tối đa 150.
- `activeIngredient`: tùy chọn, tối đa 150.
- `unit`: bắt buộc, tối đa 20.
- `price`: bắt buộc, không âm, tối đa 13 chữ số nguyên và 2 chữ số thập phân.
- `stockQuantity`: bắt buộc, `>= 0`.
- `expiryDate`: bắt buộc, hôm nay hoặc tương lai tại DTO boundary.
- `manufacturer`: tùy chọn, tối đa 150.
- `lowStockThreshold`: tùy chọn, `>= 0`; backend dùng mặc định `10` khi null.

#### Điều chỉnh tồn kho

- `quantity`: bắt buộc và domain từ chối `0`.
- số dương là nhập thêm;
- số âm là điều chỉnh giảm;
- khi giảm, `reason` bắt buộc theo application service;
- không được làm tồn vật lý âm;
- không được giảm tồn xuống thấp hơn tổng lượng đang reservation.

#### Tạo đơn thuốc

- bốn id `recordId`, `patientId`, `doctorId`, `departmentId`: UUID bắt buộc;
- `prescribedDate`: bắt buộc, hôm nay hoặc quá khứ;
- `lines`: ít nhất một dòng;
- `drugId`: UUID bắt buộc;
- `quantity`: số nguyên `>= 1`;
- `dosage`: tùy chọn, tối đa 255;
- cùng một `drugId` không được xuất hiện hai lần;
- request tuyệt đối không có `price`, `unitPrice`, `lineTotal` hoặc `totalAmount`.

#### Hủy đơn

- `reason`: bắt buộc, sau trim không rỗng, tối đa 500.

### 4.4 Nghiệp vụ phải phản ánh trên UI

#### Danh mục và tồn kho

- Tồn kho vật lý là `stockQuantity`.
- `stockQuantity <= lowStockThreshold` là trạng thái tồn thấp.
- Thuốc có `expiryDate` trước ngày hiện tại không thể xuất; create không cho ngày quá khứ.
- Điều chỉnh âm là hợp lệ, nhưng có thể bị từ chối bởi reservation hoặc làm tồn âm.
- UI không được dùng “điều chỉnh tồn” để mô phỏng xuất thuốc.

#### Kê đơn và reservation

- Backend khóa thuốc, kiểm tra lượng khả dụng sau reservation, chụp tên/giá và tự tính tiền.
- Tạo đơn thành công đồng thời tạo reservation và dispense slip `PENDING`.
- Đơn mới có `status=ACTIVE`.
- UI chỉ hiển thị estimate từ thuốc hiện tại nếu cần; kết quả trả về từ backend mới là số tiền chính thức.
- Lỗi `INSUFFICIENT_AVAILABLE_STOCK` có thể xảy ra dù `stockQuantity` nhìn thấy vẫn đủ, vì một phần
  hàng đang được giữ cho đơn khác.

#### Hủy đơn

- Chỉ đơn `ACTIVE` và dispense slip `PENDING` mới hủy được.
- ADMIN được override ownership.
- DOCTOR chỉ hủy được đơn do chính `staffId` của mình kê.
- Gọi lại đơn đã `CANCELLED` là idempotent: kết quả có `releasedReservations=0`.
- Thành công sẽ release reservation và chuyển cả prescription/dispense lifecycle sang `CANCELLED`.

#### Xuất thuốc

- Không nhận `paid=true`, `invoiceId` hay bất kỳ payment flag nào từ frontend.
- Backend chỉ xuất khi đã có durable `PAYMENT_RECEIPT` từ event `payment.completed`.
- Nếu chưa có payment proof: `PAYMENT_PROOF_REQUIRED`.
- Nếu đã `DISPENSED`, gọi lại trả kết quả hiện tại thay vì trừ kho lần hai.
- Nếu terminal theo nhánh khác, backend từ chối.
- Xuất thành công: prescription `FULFILLED`, dispense `DISPENSED`, stock giảm.
- Xuất thất bại do business rule: stock transaction rollback, dispense/prescription failure được ghi
  ở transaction bù và billing nhận event compensation.
- UI không optimistic update stock hoặc status cho thao tác này.

#### Outbox replay

- Frontend chỉ có thể replay khi operator đã biết `eventId`.
- Không có API list quarantine, nên không xây bảng outbox giả.
- `OUTBOX_EVENT_NOT_FOUND` phải hiển thị rõ event đã biến mất hoặc không tồn tại.

### 4.5 Error mapping cần có

| HTTP/code | Hành vi UI |
|---|---|
| 400 `VALIDATION_ERROR` | map `error.details[]` vào field; giữ dữ liệu form |
| 400 `INVALID_REQUEST` | banner “Dữ liệu không đúng định dạng” |
| 401 `UNAUTHORIZED` | clear session nếu phù hợp và chuyển `/login` |
| 403 `FORBIDDEN` | trang/nút không có quyền; không giả là 404 |
| 403 `PRESCRIPTION_CREATION_FORBIDDEN` | báo thiếu/sai staff identity hoặc doctor ownership |
| 403 `PRESCRIPTION_CANCELLATION_FORBIDDEN` | báo chỉ bác sĩ kê đơn hoặc ADMIN được hủy |
| 404 `DRUG_NOT_FOUND` | empty not-found state ở drug detail |
| 404 `PRESCRIPTION_NOT_FOUND` | empty not-found state ở prescription detail |
| 404 `DISPENSE_NOT_FOUND` | báo dữ liệu lifecycle không hoàn chỉnh |
| 404 `OUTBOX_EVENT_NOT_FOUND` | giữ event id trong form và cho nhập lại |
| 422 `DRUG_OUT_OF_STOCK` | hiển thị stock conflict; refetch drug |
| 422 `STOCK_BELOW_RESERVED` | giải thích lượng đang giữ khiến không thể giảm |
| 422 `INSUFFICIENT_AVAILABLE_STOCK` | giữ form prescription; đánh dấu dòng liên quan nếu suy ra được |
| 422 `PRESCRIPTION_DUPLICATE_DRUG` | đánh dấu các dòng trùng `drugId` |
| 422 `PAYMENT_PROOF_REQUIRED` | không cho retry liên tục; yêu cầu chờ xác nhận thanh toán |
| 422 lifecycle/reservation code khác | banner dùng message backend, sau đó refetch detail |
| 500 `INTERNAL_ERROR` | thông báo chung và correlation id nếu envelope có |

## 5. Blocker liên service đã xác nhận từ code

### 5.1 JWT chưa có `staffId`

Code pharmacy đọc claim tùy chọn `staffId`, nhưng gateway hiện chỉ phát `sub`, `role`, `cid`.
Các thao tác staff-owned fail closed:

- DOCTOR tạo đơn;
- DOCTOR hủy đơn;
- PHARMACIST điều chỉnh tồn;
- PHARMACIST xuất thuốc.

ADMIN vẫn hoạt động vì backend cho phép dùng account id làm audit actor cho admin.

Handoff hiện có:

`docs/eproject_general_plan/backend-spec/pharmacy-identity-contract-handoff.md`

Quy tắc cho AI:

- không dùng `sub` làm `staffId`;
- không cho người dùng tự nhập `staffId` để giả ownership;
- không tự sửa gateway/organization trong task frontend;
- có thể implement UI cho đúng role, nhưng E2E staff workflow phải ghi **blocked by signed staffId claim**;
- E2E mutation trước khi blocker được giải quyết chỉ xác nhận bằng tài khoản ADMIN.

### 5.2 Không có prescription list/search API

Frontend phase hiện tại chỉ hỗ trợ:

- tạo đơn rồi điều hướng tới detail từ id trả về;
- mở detail bằng URL/id đã biết;
- deep link từ nơi khác khi nơi đó đã có `prescriptionId`.

Không xây trang “danh sách tất cả đơn thuốc” cho tới khi controller thật có endpoint. Nếu product yêu
cầu, tạo HANDOFF riêng cho owner pharmacy; frontend task không được tự gọi repository hoặc endpoint dự đoán.

### 5.3 Không có cross-service display lookup

Pharmacy response chỉ trả UUID patient, record, doctor, department. Không gọi trực tiếp patient,
clinical hoặc organization service. Trong phase này hiển thị UUID có label rõ. Việc bổ sung tên cần
contract gateway/composition được phê duyệt riêng.

## 6. Kỹ thuật Next.js được chọn

### 6.1 Server shell, client island

- Mọi `page.tsx` giữ là Server Component.
- Chỉ form/list/detail có JWT fetch mới dùng `"use client"`.
- Không đặt `"use client"` trên toàn bộ pharmacy layout.
- Route page đọc `params` kiểu Promise theo Next.js 16 và truyền string xuống feature component.

Lý do: giảm client bundle và giữ route/layout tĩnh, trong khi vẫn tương thích `localStorage` hiện tại.

### 6.2 URL là state của tìm kiếm và phân trang

Drug catalog dùng query:

```text
/pharmacy/drugs?keyword=para&page=0&size=20
```

- đọc bằng `useSearchParams` trong Client Component;
- cập nhật bằng `router.replace` để filter không làm phình browser history;
- reset `page=0` khi keyword/size đổi;
- debounce keyword 250–400 ms hoặc submit form rõ ràng; không gọi API mỗi ký tự nếu chưa debounce;
- bọc component dùng `useSearchParams` trong `<Suspense>` tại page để production build không lỗi.

### 6.3 Dynamic segments cho resource detail

- `/pharmacy/drugs/[drugId]`
- `/pharmacy/prescriptions/[prescriptionId]`

Dùng `<Link>` cho navigation. Với bảng có nhiều dynamic link, đặt `prefetch={false}` nếu page size lớn
để tránh prefetch hàng loạt; route vẫn có client-side navigation khi click.

### 6.4 Loading và error đúng tầng

- `loading.tsx` tại segment pharmacy cho route transition/partial prefetch.
- Skeleton/`MediFlowLoader` bên trong Client Component cho browser API fetch.
- `error.tsx` chỉ bắt lỗi render/unexpected; phải có `reset()`.
- Expected API errors được xử lý thành state gần form/table, không ném lên error boundary.
- 404 từ client fetch dùng feature not-found state; không gọi `notFound()` từ Client Component.

### 6.5 Không optimistic update cho dữ liệu an toàn cao

Không dùng optimistic UI cho:

- điều chỉnh tồn;
- hủy đơn;
- xuất thuốc;
- replay outbox.

Mỗi mutation phải:

1. disable nút và chống double submit;
2. hiển thị confirmation chứa resource id và hậu quả;
3. chờ server trả thành công;
4. refetch snapshot từ backend;
5. render status/stock chính thức.

### 6.6 Auth và authorization

- Dashboard client guard hiện tại tiếp tục bảo vệ UX.
- `permissions.ts` chỉ quyết định route/action visibility.
- Backend vẫn là authority cuối cùng.
- Không thêm Next middleware auth trong phase localStorage.
- Roadmap riêng: đổi sang httpOnly cookie, server-side DAL và server auth checks theo hướng dẫn Next.js.

### 6.7 Metadata và accessibility

- Mỗi page export metadata tĩnh phù hợp: “Kho thuốc”, “Tạo thuốc”, “Kê đơn”, “Chi tiết đơn”.
- Form có `label`, `id`, `aria-describedby`, field error và focus vào lỗi đầu tiên.
- Mutation result dùng `aria-live="polite"`; lỗi nguy hiểm dùng `role="alert"`.
- Status không chỉ dựa vào màu; luôn có text/icon.
- Tiền hiển thị bằng `Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" })`.
- Ngày hiển thị locale Việt Nam nhưng request vẫn gửi ISO `YYYY-MM-DD`.

## 7. Cây thư mục mục tiêu

```text
frontend/src/
├── app/(dashboard)/pharmacy/
│   ├── layout.tsx                         # pharmacy subnav, Server Component
│   ├── loading.tsx                        # route transition fallback
│   ├── error.tsx                          # unexpected route error boundary
│   ├── page.tsx                           # redirect /pharmacy/drugs
│   ├── drugs/
│   │   ├── page.tsx                       # Server shell + Suspense
│   │   ├── new/page.tsx                   # create drug shell
│   │   └── [drugId]/page.tsx              # detail + stock action
│   ├── prescriptions/
│   │   ├── page.tsx                       # lookup by known id; no fake list
│   │   ├── new/page.tsx                   # create prescription shell
│   │   └── [prescriptionId]/page.tsx      # detail/cancel/dispense
│   └── admin/outbox/page.tsx              # ADMIN replay by known event id
│
├── features/pharmacy/
│   ├── api.ts                              # all /v1/pharmacy calls
│   ├── types.ts                            # exact DTO/request unions
│   ├── permissions.ts                      # role → visible capability
│   ├── presentation.ts                     # status labels/colors only
│   ├── utils.ts                            # money/date/UUID helpers
│   └── components/
│       ├── PharmacyNav.tsx
│       ├── drug/
│       │   ├── DrugCatalog.tsx
│       │   ├── DrugTable.tsx
│       │   ├── DrugDetail.tsx
│       │   ├── CreateDrugForm.tsx
│       │   └── AdjustStockForm.tsx
│       ├── prescription/
│       │   ├── PrescriptionLookup.tsx
│       │   ├── PrescriptionDetail.tsx
│       │   ├── CreatePrescriptionForm.tsx
│       │   ├── PrescriptionLinesEditor.tsx
│       │   ├── CancelPrescriptionDialog.tsx
│       │   └── DispensePrescriptionDialog.tsx
│       └── outbox/
│           └── OutboxReplayForm.tsx
│
└── components/ui/
    ├── Button.tsx                          # chỉ tạo nếu task cần reuse
    ├── FieldError.tsx
    ├── StatusBadge.tsx                     # generic; mapping domain ở feature
    └── ConfirmDialog.tsx
```

Không tạo `domain/application/infrastructure` trong frontend.

## 8. API facade mục tiêu

```ts
export const pharmacyApi = {
  searchDrugs(params: DrugSearchParams): Promise<PageResult<DrugDTO>>,
  getDrug(drugId: string): Promise<DrugDTO>,
  createDrug(body: CreateDrugRequest): Promise<DrugDTO>,
  adjustStock(drugId: string, body: AdjustStockRequest): Promise<DrugDTO>,

  createPrescription(body: CreatePrescriptionRequest): Promise<PrescriptionDTO>,
  getPrescription(prescriptionId: string): Promise<PrescriptionDTO>,
  cancelPrescription(
    prescriptionId: string,
    body: CancelPrescriptionRequest,
  ): Promise<CancelPrescriptionResult>,
  dispensePrescription(prescriptionId: string): Promise<DispenseDTO>,

  replayOutbox(eventId: string): Promise<OutboxReplayResult>,
};
```

Path phải là:

```ts
const BASE = "/v1/pharmacy";
```

Không viết `/api` trong feature API vì `lib/api.ts` đã prepend `/api`.

## 9. Permission model cho UX

```ts
canReadDrugs: ADMIN | DOCTOR | PHARMACIST
canCreateDrug: ADMIN | PHARMACIST
canAdjustStock: ADMIN | PHARMACIST
canCreatePrescription: ADMIN | DOCTOR
canReadPrescription: ADMIN | DOCTOR | PHARMACIST
canCancelPrescription: ADMIN | DOCTOR
canDispensePrescription: ADMIN | PHARMACIST
canReplayOutbox: ADMIN
```

Action còn phải thỏa state:

- cancel: `prescription.status === "ACTIVE" && dispenseStatus === "PENDING"`;
- dispense: `prescription.status === "ACTIVE" && dispenseStatus === "PENDING"`;
- adjust stock: drug tồn tại;
- replay: event id là UUID hợp lệ.

Đây chỉ là UX gate. API vẫn phải xử lý 403/422 vì trạng thái có thể đổi giữa lúc render và submit.

## 10. Task breakdown cho AI implementation

### PH-FE-00 — Khóa contract và client boundary

**Dependency:** không.

**Files:**

- `src/features/pharmacy/types.ts`
- `src/features/pharmacy/api.ts`
- `src/features/pharmacy/permissions.ts`
- `src/features/pharmacy/presentation.ts`
- `src/features/pharmacy/utils.ts`
- `src/lib/api.ts`
- `src/lib/session.ts`/`src/lib/auth.ts` chỉ nếu thêm `client-only`.

**Implement:**

1. Copy type đúng mục 4.2; không dùng optional cho field response có thể null—dùng `| null`.
2. Implement đủ 9 endpoint thật.
3. Sửa HTTP wrapper để mutation body optional.
4. Query builder chỉ gửi `keyword` khi có giá trị, page 0-based, size mặc định 20.
5. Thêm permission predicate và mapping label/status exhaustively bằng `satisfies Record<...>`.
6. Thêm formatter VND/date và UUID validator thuần.

**Acceptance:**

- chỉ `src/lib/api.ts` có `fetch(`;
- không có endpoint ngoài bảng 4.1;
- TypeScript buộc xử lý mọi enum status;
- `pnpm typecheck`, `pnpm lint`, `pnpm build` xanh.

### PH-FE-01 — Pharmacy route shell và navigation

**Dependency:** PH-FE-00.

**Files:** `app/(dashboard)/pharmacy/**/page.tsx`, pharmacy `layout/loading/error`, `PharmacyNav` và
`DashboardHeader` để thêm link Pharmacy.

**Implement:**

1. `/pharmacy` redirect server-side sang `/pharmacy/drugs`.
2. Pharmacy layout render subnav theo role: Kho thuốc, Kê đơn, Tra đơn, Outbox (ADMIN).
3. Page là Server Component, chỉ compose feature components.
4. `error.tsx` là Client Component có nút `reset()`.
5. `loading.tsx` dùng loader/skeleton đã có.

**Acceptance:** URL route đúng, shared dashboard layout giữ nguyên, không fetch trong route file.

### PH-FE-02 — Drug catalog read flow

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `DrugCatalog.tsx`, `DrugTable.tsx`, `drugs/page.tsx`.

**Implement:**

1. Search `keyword`, `page`, `size` từ URL.
2. Bọc Client Component bằng Suspense.
3. Hiển thị loading/error/empty/data rõ ràng.
4. Cột: tên, hoạt chất, đơn vị, giá, tồn, ngưỡng, hạn dùng, nhà sản xuất.
5. Badge “Tồn thấp” khi `stockQuantity <= lowStockThreshold`.
6. Badge “Hết hạn/Sắp hết hạn” dựa trên ngày; chỉ mang tính hiển thị, backend vẫn quyết định.
7. Link detail với `prefetch={false}` khi render bảng nhiều dòng.
8. Pagination giữ keyword và size trong URL.

**Acceptance:** reload/back/forward giữ filter; request đúng page 0-based; empty không render table rỗng.

### PH-FE-03 — Drug detail

**Dependency:** PH-FE-02.

**Files:** `DrugDetail.tsx`, `drugs/[drugId]/page.tsx`.

**Implement:**

1. Server page lấy `drugId` từ `await params`, validate UUID trước khi render client detail.
2. Client detail gọi `getDrug`.
3. 404 `DRUG_NOT_FOUND` render resource-not-found state.
4. Render timestamps, price, stock, threshold, expiry và nullable fields.
5. Hiện action create/adjust theo permissions.

**Acceptance:** invalid UUID không gọi API; direct deep-link hoạt động; không dùng `notFound()` trong client.

### PH-FE-04 — Create drug

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `CreateDrugForm.tsx`, `drugs/new/page.tsx`, shared field primitives nếu cần.

**Implement:**

1. Client validation mirror mục 4.3 nhưng không thay backend validation.
2. Giữ price input ở dạng string trong form; chuyển number ngay trước submit.
3. `expiryDate` min là ngày local hiện tại cho UX.
4. Nếu bỏ threshold, không tự gửi `0`; để backend dùng default 10.
5. Map `VALIDATION_ERROR.details` vào field.
6. Thành công điều hướng `/pharmacy/drugs/{drugId}`.

**Acceptance:** không gửi server-owned fields; double submit bị chặn; role không phù hợp không thấy form action.

### PH-FE-05 — Stock adjustment

**Dependency:** PH-FE-03.

**Files:** `AdjustStockForm.tsx`, có thể `ConfirmDialog.tsx`.

**Implement:**

1. Form nằm ở drug detail hoặc dialog.
2. Quantity cho phép âm/dương, cấm 0.
3. Reason bắt buộc khi âm; với số dương vẫn cho phép nhập.
4. Confirmation hiển thị stock hiện tại, delta và phép tính preview; preview không phải source of truth.
5. Sau success dùng `DrugDTO` response để thay snapshot/refetch.
6. `DRUG_OUT_OF_STOCK`/`STOCK_BELOW_RESERVED` giữ form và giải thích conflict.

**Acceptance:** không optimistic commit; PHARMACIST E2E ghi blocked nếu JWT thiếu staffId; ADMIN E2E phải qua.

### PH-FE-06 — Create prescription và line editor

**Dependency:** PH-FE-00, PH-FE-02, identity blocker được ghi nhận.

**Files:** `CreatePrescriptionForm.tsx`, `PrescriptionLinesEditor.tsx`, `prescriptions/new/page.tsx`.

**Implement:**

1. Form nhập 4 UUID và prescribedDate.
2. Line editor thêm/xóa dòng, ít nhất một dòng.
3. Chọn thuốc từ catalog API; lưu `drugId`, không copy giá vào request.
4. Ngăn duplicate `drugId` trước submit.
5. Mỗi dòng chỉ gửi `drugId`, `quantity`, `dosage`.
6. Có thể hiển thị estimate từ current drug price, nhưng label rõ “tạm tính”; server response là chính thức.
7. Xử lý `INSUFFICIENT_AVAILABLE_STOCK` và `PRESCRIPTION_DUPLICATE_DRUG` tại line editor.
8. Thành công điều hướng detail id trả về.

**Acceptance:** payload không chứa giá; ADMIN E2E qua; DOCTOR E2E blocked cho tới khi signed `staffId` có thật.

### PH-FE-07 — Prescription lookup và detail

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `PrescriptionLookup.tsx`, `PrescriptionDetail.tsx`, prescription page và dynamic page.

**Implement:**

1. `/pharmacy/prescriptions` chỉ có form nhập known prescription UUID, không giả list.
2. Submit điều hướng tới dynamic detail route.
3. Detail hiển thị các UUID context, prescribedDate, totalAmount backend, line snapshots và audit cancellation.
4. Hiển thị cả `status` và `dispenseStatus`, vì chúng là hai state machine khác nhau.
5. 404 và missing dispense có trạng thái riêng.
6. Action cancel/dispense dựa trên role + state, nhưng vẫn xử lý race từ backend.

**Acceptance:** deep-link hoạt động; không có request list prescription; money chỉ format, không tính lại làm truth.

### PH-FE-08 — Cancel prescription

**Dependency:** PH-FE-07, identity blocker được ghi nhận.

**Files:** `CancelPrescriptionDialog.tsx`, cập nhật `PrescriptionDetail.tsx`.

**Implement:**

1. Chỉ show cho ADMIN/DOCTOR khi ACTIVE + PENDING.
2. Require reason 1–500 chars.
3. Confirmation cảnh báo reservation sẽ được release.
4. Sau success refetch detail; hiển thị `releasedReservations`.
5. Nếu response idempotent có `releasedReservations=0`, hiển thị “đơn đã được hủy trước đó”.
6. Lifecycle conflict refetch trước khi cho retry.

**Acceptance:** không optimistic status; ADMIN E2E qua; DOCTOR E2E blocked đến signed `staffId`.

### PH-FE-09 — Dispense prescription

**Dependency:** PH-FE-07, identity blocker được ghi nhận.

**Files:** `DispensePrescriptionDialog.tsx`, cập nhật `PrescriptionDetail.tsx`.

**Implement:**

1. Chỉ show ADMIN/PHARMACIST khi ACTIVE + PENDING.
2. Không render field payment flag/invoice id.
3. Confirmation ghi rõ chỉ xuất khi billing đã xác nhận thanh toán.
4. PUT không body.
5. `PAYMENT_PROOF_REQUIRED`: giữ detail PENDING, disable retry ngắn hạn hoặc yêu cầu operator kiểm tra billing.
6. Success refetch prescription và linked drug snapshots khi có trong UI.
7. Failure terminal hiển thị `failureReason` từ detail/response khi backend cung cấp.

**Acceptance:** double click chỉ gửi một request; ADMIN E2E qua khi payment proof fixture tồn tại; PHARMACIST
E2E blocked đến signed `staffId`.

### PH-FE-10 — Admin outbox replay

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `OutboxReplayForm.tsx`, `admin/outbox/page.tsx`.

**Implement:**

1. ADMIN-only UX.
2. Chỉ input UUID event id và confirmation.
3. POST không body.
4. Thành công hiển thị eventId + replayed.
5. 404 giữ form để sửa id.
6. Không tạo bảng/danh sách/quarantine filters.

**Acceptance:** non-admin không thấy nav/action; backend 403 vẫn được xử lý nếu truy cập URL trực tiếp.

### PH-FE-11 — Test infrastructure và contract tests

**Dependency:** PH-FE-00 trước unit tests; các feature task trước component/E2E tương ứng.

**Files dự kiến:** `vitest.config.ts`, `vitest.setup.ts`, `tests/` hoặc test colocated; Playwright chỉ thêm
khi team đồng ý chạy browser trong CI.

**Unit/component bắt buộc:**

- query builder giữ page/size/keyword;
- status mapping exhaustiveness;
- money/date formatter;
- duplicate drug validation;
- create prescription serializer không chứa price;
- stock negative requires reason;
- role capability matrix;
- API error details → field errors;
- cancel/dispense button state matrix.

**E2E ưu tiên:**

1. ADMIN search → open drug.
2. ADMIN create drug → detail.
3. ADMIN adjust stock → official stock response.
4. ADMIN create prescription → detail PENDING.
5. ADMIN cancel ACTIVE prescription.
6. ADMIN dispense paid prescription fixture.
7. Unauthorized/forbidden route behavior.

Async Server Component chủ yếu được kiểm tra E2E; Client Component và pure helper kiểm tra bằng Vitest.

### PH-FE-12 — Quality gate và tài liệu vận hành

**Dependency:** tất cả task được chọn cho release.

**Implement/verify:**

```bash
cd frontend
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

Ngoài ra:

- scan raw `fetch(`: chỉ `src/lib/api.ts`;
- scan cross-feature import: không có import `@/features/*` từ trong `features/pharmacy`;
- kiểm tra keyboard/focus/error announcement;
- kiểm tra mobile table overflow;
- kiểm tra role ADMIN/DOCTOR/PHARMACIST;
- chạy request backend tương ứng trong `pharmacy-service.http` để xác nhận contract trước E2E;
- ghi rõ blocker staffId trong PR, không đánh dấu staff workflow là pass khi chưa có signed claim.

## 11. Dependency graph và cách chia việc

```text
PH-FE-00
   └── PH-FE-01
       ├── PH-FE-02 ── PH-FE-03 ── PH-FE-05
       ├── PH-FE-04
       ├── PH-FE-06 ── PH-FE-07 ── PH-FE-08
       │                         └── PH-FE-09
       └── PH-FE-10

PH-FE-11 chạy tăng dần cùng từng nhánh
PH-FE-12 chạy cuối mỗi milestone/release
```

Có thể triển khai song song sau PH-FE-01:

- nhánh Drug: PH-FE-02/03/04/05;
- nhánh Prescription: PH-FE-06/07/08/09;
- nhánh Admin: PH-FE-10;
- nhánh Test: PH-FE-11.

Nếu nhiều AI cùng làm, mỗi AI phải được giao ownership file rõ ràng và không sửa/revert file của nhánh khác.

## 12. Protocol bắt buộc cho AI implement

Mỗi task AI phải thực hiện theo thứ tự:

1. Đọc `AGENTS.md`, `frontend/AGENTS.md`, `docs/ai/12-frontend.md`, plan này.
2. Chạy changelog theo root instruction.
3. Mở lại controller/DTO/enum backend liên quan ngay trước khi code.
4. Ghi contract sẽ dùng: method, path, request, response, role, error codes.
5. Chỉ sửa các file task sở hữu; giữ nguyên thay đổi hiện có của người khác.
6. Không thêm dependency nếu task không ghi rõ hoặc chưa được duyệt.
7. Không gọi raw `fetch`, service port hoặc feature khác.
8. Implement loading/error/empty/success và quyền UX.
9. Viết/chạy test tương ứng.
10. Chạy `typecheck`, `lint`, `build`; báo rõ bước nào blocked và bằng chứng.

Prompt mẫu để giao cho AI:

```text
Implement task <PH-FE-ID> from
frontend/docs/pharmacy-frontend-implementation-plan.md.

Backend source of truth is the current code under backend/pharmacy-service, especially the
controllers, DTO records, enums, application services, and web tests named by the plan. Do not
invent endpoints or fields from design docs. Own only the files listed by the task. Preserve other
working-tree changes. Follow frontend/AGENTS.md: gateway only, all HTTP through src/lib/api.ts,
no cross-feature imports, no data-fetching/component library.

Before coding, report the exact live endpoint contract used. After coding, run the task acceptance
checks plus pnpm typecheck, pnpm lint, and pnpm build. If staffId is required, do not fake it; mark
the staff E2E path blocked by the existing gateway identity handoff and verify with ADMIN where valid.
```

## 13. Milestone đề xuất

### Milestone A — Read-only pharmacy

PH-FE-00, 01, 02, 03, 07 (lookup/detail only).

Kết quả: ADMIN/DOCTOR/PHARMACIST đọc catalog và prescription id đã biết.

### Milestone B — Inventory operations

PH-FE-04, 05.

Kết quả: ADMIN hoàn chỉnh; PHARMACIST UI có thể implement nhưng E2E phụ thuộc staffId claim.

### Milestone C — Prescription workflow

PH-FE-06, 08, 09.

Kết quả: ADMIN có thể tạo/hủy/xuất theo contract; DOCTOR/PHARMACIST phụ thuộc staffId và payment proof.

### Milestone D — Admin recovery và hardening

PH-FE-10, 11, 12.

Kết quả: outbox replay known-id, coverage, accessibility và production build.

## 14. Definition of Done

- [ ] Chỉ dùng 9 endpoint có thật trong controller hiện tại.
- [ ] DTO TypeScript mirror đúng record/enum backend.
- [ ] Không có price trong create prescription request.
- [ ] Không có payment flag/invoice id trong manual dispense request.
- [ ] Search/pagination nằm trong URL và hoạt động với back/forward/reload.
- [ ] Pages là Server Components; client boundary chỉ ở interactive/data components.
- [ ] `useSearchParams` nằm dưới Suspense.
- [ ] Expected API errors có UI state; unexpected render errors có `error.tsx`.
- [ ] Không optimistic mutation cho stock/prescription/dispense/outbox.
- [ ] Role visibility đúng controller và backend vẫn xử lý 403.
- [ ] Không invent prescription/outbox list API.
- [ ] Không dùng account id thay staff id.
- [ ] Typecheck, lint, tests và production build xanh.
- [ ] ADMIN E2E cho mutation xanh; staff E2E chỉ xanh sau khi signed `staffId` contract được merge.

## 15. Tài liệu Next.js dùng để ra quyết định

- Server/Client Components và client boundary:
  <https://nextjs.org/docs/app/getting-started/server-and-client-components>
- `useSearchParams` và yêu cầu Suspense khi prerender:
  <https://nextjs.org/docs/app/api-reference/functions/use-search-params>
- Dynamic segments, pages và layouts:
  <https://nextjs.org/docs/app/getting-started/layouts-and-pages>
- Link, prefetch, loading UI và client-side transitions:
  <https://nextjs.org/docs/app/getting-started/linking-and-navigating>
- Error handling:
  <https://nextjs.org/docs/app/getting-started/error-handling>
- Authentication/cookie roadmap:
  <https://nextjs.org/docs/app/guides/authentication>
- Testing options:
  <https://nextjs.org/docs/app/guides/testing>
