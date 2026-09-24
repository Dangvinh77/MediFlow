# Pharmacy Frontend Implementation Plan

> Kế hoạch triển khai frontend cho bounded context `pharmacy`.
>
> **Nguồn chuẩn là code backend hiện tại**, không phải thiết kế dự kiến. Kế hoạch này được kiểm tra
> lại ngày 2026-09-21 trên repository HEAD `dc79f8928cccb8d5b763415cbed2194446f23fd4`
> cùng các thay đổi chưa commit trong workspace;
> commit gần nhất chạm `backend/pharmacy-service` là
> `8cea770c70e7bc9b078a8e7c6ef5d43e0a0d170b`, còn commit gần nhất triển khai frontend Pharmacy là
> `8958fc169fde136d9d72f4feb52b7814a523e06b`.

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
- Shared UI hiện có `PageShell`, `DashboardHeader`, `Pagination`, `RoleGate`, `AsyncState`,
  `lib/format.ts`, `lib/validation.ts` và route loading chung từ master service-base foundation.
- `PH-FE-00` đã có contract/client boundary và `PH-FE-01` đã có route shell/navigation.
- `PH-FE-02` đến `PH-FE-07` đã có read flow, drug form và prescription create/lookup/detail; mutation
  prescription terminal actions vẫn để ở `PH-FE-08`/`PH-FE-09`.
- Các Pharmacy page hiện compose shared `RoleGate` ở route boundary; feature component vẫn giữ
  capability/error checks riêng để xử lý backend 401/403 và race state.
- Shared auth hiện phát sự kiện khi session đổi trong cùng tab, lắng nghe thay đổi giữa các tab,
  chặn `/login` hiển thị lại khi phiên còn hợp lệ và xóa session khi API trả `401`.

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

### 3.3 Nền tảng dùng chung đã triển khai cho pharmacy

`src/lib/api.ts` đã hỗ trợ mutation không có body vì hai endpoint thật không nhận body:

- `PUT /prescriptions/{id}/dispense`
- `POST /admin/outbox/{eventId}/replay`

Thiết kế API wrapper mục tiêu:

```ts
get<T>(path: string): Promise<T>
post<T>(path: string, data?: unknown): Promise<T>
put<T>(path: string, data?: unknown): Promise<T>
del<T>(path: string): Promise<T>
```

Wrapper hiện chỉ set `Content-Type: application/json` và chỉ tạo `body` khi `data !== undefined`.
Khi nhận `401`, wrapper xóa session để dashboard/login guard không tiếp tục dùng token đã hết hạn.

`src/lib/api.ts` đã có `import "client-only"` để Next.js báo lỗi build nếu một Server Component vô
tình import HTTP client. Việc migration token sang httpOnly cookie vẫn là roadmap riêng.

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

### 5.1 JWT `staffId` đã có contract chính thức

Code Pharmacy đọc claim tùy chọn `staffId`; Gateway ký claim này riêng trong typed access/refresh
token cùng `sub=accountId`, `role`, `cid` và `departmentId` theo
[`CONTRACT-IDENTITY-LOOKUP-01`](../../docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
Các thao tác staff-owned vẫn fail closed khi claim thiếu hoặc sai:

- DOCTOR tạo đơn;
- DOCTOR hủy đơn;
- PHARMACIST điều chỉnh tồn;
- PHARMACIST xuất thuốc.

ADMIN vẫn hoạt động vì backend cho phép dùng account id làm audit actor cho admin.

Quy tắc cho AI:

- không dùng `sub` làm `staffId`;
- không cho người dùng tự nhập `staffId` để giả ownership;
- không tự sửa gateway/organization trong task frontend;
- có thể implement UI theo role, nhưng E2E phải dùng token Gateway thật và kiểm tra claim `staffId`;
- không tạo identity handoff mới trừ khi contract hiện hành bị vi phạm thực tế.

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

- Dashboard client guard và `RoleGate` cùng subscribe vào session store dùng chung.
- Login thành công cập nhật auth snapshot ngay trong tab hiện tại; truy cập lại `/login` khi còn phiên
  sẽ chuyển về dashboard thay vì render thêm form đăng nhập.
- API `401` xóa session trước khi các feature chuyển về `/login`, tránh vòng lặp token không hợp lệ.
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

### 10.0 Cách đọc và giao task

Trạng thái tại lần kiểm tra 2026-09-21 (HEAD + workspace hiện tại):

| Task | Tiến độ work package | Trạng thái theo code | Bằng chứng/việc tiếp theo |
|---|---:|---|---|
| PH-FE-00 | 6/6 | Code production hoàn thành | Contract/facade/policy/helper đã có; thêm test contract ở PH-FE-11 |
| PH-FE-01 | 5/5 | Code production hoàn thành | Route shell, navigation, shared `RoleGate` và auth subscription đã có; outbox placeholder thuộc PH-FE-10 |
| PH-FE-02 | 7/7 | Code production hoàn thành | Catalog URL state, stale-response guard, table và pagination đã có; còn test tự động |
| PH-FE-03 | 5/5 | Code production hoàn thành | UUID route guard, detail states và mutation slot đã có; còn test tự động |
| PH-FE-04 | 5/5 | Code production hoàn thành | Create form/validation/error mapping/navigation đã có; còn test tự động |
| PH-FE-05 | 5/5 | Code production hoàn thành, E2E chưa đóng | Stock confirmation/non-optimistic refresh đã có; ADMIN E2E cần chạy, PHARMACIST còn blocker signed `staffId` |
| PH-FE-06 | 8/8 | Code production hoàn thành, E2E chưa đóng | Create prescription/line editor/serializer đã có; ADMIN E2E cần chạy, DOCTOR còn blocker signed `staffId` |
| PH-FE-07 | 7/7 | Read flow hoàn thành | Lookup/detail/refresh boundary đã có; action slot đã được nối với PH-FE-08/09 |
| PH-FE-08 | 5/5 | Code production hoàn thành, test/E2E chưa đóng | `CancelPrescriptionDialog.tsx` đã có reason validation, release count, idempotency và race refresh |
| PH-FE-09 | 6/6 | Code production hoàn thành, test/E2E chưa đóng | `DispensePrescriptionDialog.tsx` đã có bodyless PUT, payment-proof gate, terminal failure và refresh |
| PH-FE-10 | 0/5 | Chưa triển khai | Route outbox vẫn là placeholder; chưa có `OutboxReplayForm.tsx` |
| PH-FE-11 | 0/6 | Chưa triển khai | Chưa có `test` script, Vitest config hoặc test Pharmacy |
| PH-FE-12 | 0/8 đóng | Đang kiểm tra từng phần | `typecheck`, `lint`, `build` xanh; còn `test`, smoke, E2E, accessibility, docs và release evidence |

Tổng quan: 10/13 task đã có code production; 59/78 work package đã có implementation. Con số này
không thay thế Definition of Done vì test/E2E và release evidence vẫn thuộc PH-FE-11/12.

Mỗi task chính được chia thành các **work package** có hậu tố `A`, `B`, `C`... để một agent có thể
nhận phạm vi nhỏ mà không phải tự suy đoán. Một work package chỉ được xem là xong khi:

1. code production và test thuộc đúng file ownership đã nêu;
2. loading/error/empty/success hoặc idle/submitting/success/error đã được xử lý đầy đủ;
3. không còn placeholder/TODO của work package đó;
4. task-specific checks và `pnpm typecheck` chạy xanh;
5. diff không thêm raw `fetch`, cross-feature import hay endpoint ngoài contract;
6. agent ghi lại blocker thật thay vì mock/fake contract để làm E2E xanh.

Quy ước giao việc:

- Một agent chỉ nhận một task chính hoặc một nhóm work package không đụng cùng file với agent khác.
- Nếu hai task cùng sửa một page/detail component, task dependency phải merge trước; agent sau rebase
  hoặc merge theo trạng thái mới, không copy lại phiên bản cũ.
- Shared UI chỉ được tạo khi có ít nhất hai consumer thật trong phạm vi hiện tại; nếu chỉ một feature
  dùng, giữ component trong `features/pharmacy/components/`.
- Mỗi task mutation phải giữ form khi lỗi, chống double-submit và refetch snapshot chính thức sau
  thành công; không tự sửa local state thành trạng thái terminal trước response backend.
- Các lệnh `pnpm lint` và `pnpm build` có thể chạy ở cuối task chính; `PH-FE-12` bắt buộc chạy toàn bộ.

### PH-FE-00 — Khóa contract và client boundary

**Trạng thái:** 6/6 work package đã có code production (khởi đầu ở commit `817a50a`); hiện dùng shared
UUID validator của master. Test contract tự động được theo dõi ở PH-FE-11.

**Dependency:** không.

**Files:**

- `src/features/pharmacy/types.ts`
- `src/features/pharmacy/api.ts`
- `src/features/pharmacy/permissions.ts`
- `src/features/pharmacy/presentation.ts`
- `src/features/pharmacy/utils.ts`
- `src/lib/api.ts`
- `src/lib/validation.ts` (shared dependency; Pharmacy không sở hữu contract này)
- `src/lib/session.ts`/`src/lib/auth.ts` là shared auth dependency; không thêm Pharmacy contract vào đây.

**Implement:**

1. Copy type đúng mục 4.2; không dùng optional cho field response có thể null—dùng `| null`.
2. Implement đủ 9 endpoint thật.
3. Sửa HTTP wrapper để mutation body optional.
4. Query builder chỉ gửi `keyword` khi có giá trị, page 0-based, size mặc định 20.
5. Thêm permission predicate và mapping label/status exhaustively bằng `satisfies Record<...>`.
6. Thêm formatter VND/date; UUID validation của Pharmacy delegate sang `src/lib/validation.ts` dùng chung
   với các bounded context khác.

**Work packages:**

- **PH-FE-00A — Contract snapshot:** lập bảng 9 endpoint gồm method, path, request, response, role và
  error code từ controller/web tests; ghi rõ endpoint nào không có body.
- **PH-FE-00B — Wire types:** mirror enum/DTO/request vào `types.ts`; nullable response dùng
  `T | null`, optional chỉ dùng cho field request thực sự tùy chọn.
- **PH-FE-00C — Shared HTTP boundary:** cập nhật `lib/api.ts` để body optional, không gửi
  `Content-Type` khi không có body, giữ `ApiRequestError` gồm status/code/details/correlationId.
- **PH-FE-00D — Pharmacy facade:** implement đúng 9 hàm trong `api.ts`; query catalog luôn gửi page,
  size và chỉ gửi keyword đã trim khi không rỗng.
- **PH-FE-00E — Pure policy/helpers:** implement capability matrix, state predicate, status mapping,
  UUID/date/VND formatter và field-error mapper mà không import React.
- **PH-FE-00F — Boundary audit:** scan `fetch(`, `/api/v1/pharmacy`, service port `8085` và import
  `@/features/`; kết quả phải chỉ còn các vị trí hợp lệ theo blueprint.

**Test/verification chi tiết:**

- `searchDrugs({})` tạo `page=0&size=20` và không tạo `keyword=`.
- `dispensePrescription` và `replayOutbox` không có body/JSON content type.
- Mọi `PrescriptionStatus` và `DispenseStatus` đều có label/tone tại compile time.
- Permission matrix kiểm tra đủ ADMIN/DOCTOR/PHARMACIST và ít nhất một role không có quyền.
- `pnpm typecheck`; `pnpm lint`; `pnpm build` ở lần merge/release.

**Không làm trong task này:** component, route UI, backend endpoint, auth-cookie migration hoặc
cross-service lookup.

**Acceptance:**

- chỉ `src/lib/api.ts` có `fetch(`;
- không có endpoint ngoài bảng 4.1;
- TypeScript buộc xử lý mọi enum status;
- `pnpm typecheck`, `pnpm lint`, `pnpm build` xanh.

### PH-FE-01 — Pharmacy route shell và navigation

**Trạng thái:** 5/5 work package đã có code production (khởi đầu ở commit `eed19bb`); sau master
service-base foundation, các route Pharmacy compose shared `RoleGate` theo role matrix. Auth guard
hiện phản ứng đúng với login/logout cùng tab. Placeholder còn lại chỉ thuộc PH-FE-10.

**Dependency:** PH-FE-00.

**Files:** `app/(dashboard)/pharmacy/**/page.tsx`, pharmacy `layout/loading/error`, `PharmacyNav`,
shared `RoleGate` và `DashboardHeader` để thêm link Pharmacy.

**Shared foundation đang liên quan:** `app/(dashboard)/layout.tsx`, `app/login/page.tsx`,
`lib/auth.ts`, `lib/session.ts`, `lib/api.ts`. Đây là auth/session dùng chung, không phải API contract
riêng của Pharmacy.

**Implement:**

1. `/pharmacy` redirect server-side sang `/pharmacy/drugs`.
2. Pharmacy layout render subnav theo role: Kho thuốc, Kê đơn, Tra đơn, Outbox (ADMIN).
3. Page là Server Component, chỉ compose feature components.
4. `error.tsx` là Client Component có nút `reset()`.
5. `loading.tsx` dùng loader/skeleton đã có.
6. Các page dùng `RoleGate` ở boundary với đúng role matrix trước khi mount feature component;
   feature-level capability checks vẫn được giữ như defense-in-depth.

**Work packages:**

- **PH-FE-01A — Route tree:** tạo đủ route trong mục 7, metadata tĩnh và redirect server-side từ
  `/pharmacy` tới `/pharmacy/drugs`.
- **PH-FE-01B — Segment states:** tạo `loading.tsx` cho route transition và `error.tsx` chỉ cho
  unexpected render error, có `reset()` và nội dung dễ hiểu.
- **PH-FE-01C — Role-aware navigation:** `PharmacyNav` đọc role ở client, lọc mục bằng
  `permissions.ts`, đánh dấu active route và không xem việc ẩn link là authorization.
- **PH-FE-01D — Dashboard entry:** thêm link Pharmacy vào dashboard header mà không phá các route
  hiện có; kiểm tra keyboard focus và `aria-current`.
- **PH-FE-01E — Thin-page audit:** mọi page chỉ export metadata, đọc params nếu cần và compose
  `RoleGate`/feature component; không import `pharmacyApi` hoặc `lib/api` trực tiếp.

**Test/verification chi tiết:**

- `/pharmacy` redirect đúng một lần; các URL con render được khi refresh trực tiếp.
- ADMIN thấy 4 mục; DOCTOR thấy Kho thuốc/Kê đơn/Tra đơn; PHARMACIST thấy Kho thuốc/Tra đơn.
- Role không thuộc Pharmacy thấy thông báo không có quyền sử dụng phân hệ.
- Truy cập trực tiếp route Pharmacy với role không được phép không mount child component và không tạo
  request tránh được; backend vẫn là authorization cuối cùng.
- `error.tsx` gọi được `reset()`; navigation có accessible name và trạng thái active.

**Không làm trong task này:** fetch dữ liệu, form nghiệp vụ, route protection phía server hoặc API
list prescription/outbox.

**Acceptance:** URL route đúng, shared dashboard layout giữ nguyên, không fetch trong route file.

### PH-FE-02 — Drug catalog read flow

**Trạng thái:** 7/7 work package đã có code production; test parser/component/browser còn thuộc PH-FE-11/12.

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `DrugCatalog.tsx`, `DrugTable.tsx`, `drugs/page.tsx`, `features/pharmacy/utils.ts`.

**Implement:**

1. Search `keyword`, `page`, `size` từ URL.
2. Bọc Client Component bằng Suspense.
3. Hiển thị loading/error/empty/data rõ ràng.
4. Cột: tên, hoạt chất, đơn vị, giá, tồn, ngưỡng, hạn dùng, nhà sản xuất.
5. Badge “Tồn thấp” khi `stockQuantity <= lowStockThreshold`.
6. Badge “Hết hạn/Sắp hết hạn” dựa trên ngày; chỉ mang tính hiển thị, backend vẫn quyết định.
7. Link detail với `prefetch={false}` khi render bảng nhiều dòng.
8. Pagination giữ keyword và size trong URL.

**Work packages:**

- **PH-FE-02A — URL parser:** tạo pure helper đọc `keyword`, `page`, `size`; page âm/NaN về `0`,
  size ngoài tập cho phép về `20`, keyword trim; không ghi state filter trùng trong component.
- **PH-FE-02B — Catalog controller:** `DrugCatalog` đọc `useSearchParams`, quản lý
  loading/error/data, hủy hoặc bỏ qua response cũ khi query đổi nhanh và gọi duy nhất
  `pharmacyApi.searchDrugs`.
- **PH-FE-02C — Search controls:** form search có label, submit hoặc debounce 250–400 ms; đổi keyword
  hoặc size reset page về `0`; dùng `router.replace` và giữ các query pharmacy hợp lệ.
- **PH-FE-02D — Drug table:** `DrugTable` chỉ render `DrugDTO[]`; format VND/date bằng helper; nullable
  field dùng `—`; link detail tắt prefetch; table có caption/screen-reader label.
- **PH-FE-02E — Stock/expiry presentation:** badge tồn thấp theo `stockQuantity <= lowStockThreshold`;
  badge hết hạn theo LocalDate; nếu thêm “sắp hết hạn”, khai báo rõ cửa sổ ngày và test boundary.
- **PH-FE-02F — Pagination:** dùng shared `Pagination`, URL giữ keyword/size, disable khi loading và
  clamp page nếu backend trả page vượt phạm vi sau khi dữ liệu thay đổi.
- **PH-FE-02G — Page composition:** thay placeholder bằng `<Suspense>` + `DrugCatalog`; fallback route
  khác với loading của browser fetch để tránh màn hình trắng.

**State matrix bắt buộc:**

| State | UI |
|---|---|
| Initial/loading | skeleton hoặc `MediFlowLoader`, giữ controls ổn định |
| Success có dữ liệu | table + tổng số bản ghi + pagination |
| Success rỗng | thông báo không tìm thấy; không render header table rỗng |
| Expected API error | banner có retry; giữ URL filter |
| 401 | xử lý theo auth policy chung/chuyển login |
| 403 | thông báo không có quyền, không giả thành empty |

**Test/verification chi tiết:**

- Unit URL parser/query builder: default, keyword rỗng, Unicode, page âm, size sai.
- Component: loading → data, empty, retry sau error, stale response không ghi đè query mới.
- Browser/manual: reload/back/forward giữ đúng keyword/page/size.
- Scan route page: không có API call trực tiếp; `useSearchParams` nằm dưới Suspense.

**Không làm trong task này:** create/adjust stock, detail fetch, autocomplete prescription hoặc
client-side cache library.

**Acceptance:** reload/back/forward giữ filter; request đúng page 0-based; empty không render table rỗng.

### PH-FE-03 — Drug detail

**Trạng thái:** 5/5 work package đã có code production; test detail states/deep-link còn thuộc PH-FE-11/12.

**Dependency:** PH-FE-02.

**Files:** `DrugDetail.tsx`, `drugs/[drugId]/page.tsx`.

**Implement:**

1. Server page lấy `drugId` từ `await params`, validate UUID trước khi render client detail.
2. Client detail gọi `getDrug`.
3. 404 `DRUG_NOT_FOUND` render resource-not-found state.
4. Render timestamps, price, stock, threshold, expiry và nullable fields.
5. Hiện action create/adjust theo permissions.

**Work packages:**

- **PH-FE-03A — Route guard:** server page `await params`, validate UUID bằng helper; invalid UUID
  render trạng thái mã không hợp lệ và tuyệt đối không mount client fetch component.
- **PH-FE-03B — Detail fetch:** `DrugDetail` fetch một lần theo `drugId`, refetch khi id đổi hoặc sau
  mutation callback; tránh setState sau unmount.
- **PH-FE-03C — Detail presentation:** nhóm thông tin nhận diện, giá/tồn/ngưỡng, hạn dùng/nhà sản xuất
  và audit timestamps; nullable field dùng `—`; badge dùng cùng rule catalog.
- **PH-FE-03D — Action slots:** render link tạo thuốc và vùng điều chỉnh tồn theo capability; task này
  chỉ chuẩn bị slot/callback, form điều chỉnh thuộc PH-FE-05.
- **PH-FE-03E — Error states:** phân biệt invalid UUID, `DRUG_NOT_FOUND`, 403 và lỗi retryable; 404
  không đẩy lên route error boundary.

**Test/verification chi tiết:**

- Invalid UUID không gọi `pharmacyApi.getDrug`.
- 404 hiển thị mã thuốc đã yêu cầu và link quay lại catalog.
- Nullable ingredient/manufacturer, ngày hết hạn và tồn thấp render đúng.
- Deep link refresh trực tiếp hoạt động; role không có mutation capability không thấy action.

**Không làm trong task này:** sửa metadata thuốc, invent delete/update endpoint hoặc điều chỉnh stock
trước PH-FE-05.

**Acceptance:** invalid UUID không gọi API; direct deep-link hoạt động; không dùng `notFound()` trong client.

### PH-FE-04 — Create drug

**Trạng thái:** 5/5 work package đã có code production; test form boundary và E2E còn thuộc PH-FE-11/12.

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `CreateDrugForm.tsx`, `drugFormValidation.ts`, `drugs/new/page.tsx`.

**Implement:**

1. Client validation mirror mục 4.3 nhưng không thay backend validation.
2. Giữ price input ở dạng string trong form; chuyển number ngay trước submit.
3. `expiryDate` min là ngày local hiện tại cho UX.
4. Nếu bỏ threshold, không tự gửi `0`; để backend dùng default 10.
5. Map `VALIDATION_ERROR.details` vào field.
6. Thành công điều hướng `/pharmacy/drugs/{drugId}`.

**Work packages:**

- **PH-FE-04A — Form model:** định nghĩa state string cho input text/number/date; hàm normalize tạo
  `CreateDrugRequest`, trim text, đổi optional rỗng thành `undefined`/`null` đúng contract.
- **PH-FE-04B — Client validation:** kiểm tra required/max length, số không âm, precision price,
  expiryDate >= local today và threshold optional; lỗi gắn `aria-describedby` vào input.
- **PH-FE-04C — Submit flow:** clear lỗi cũ, validate, disable submit, gọi `createDrug`, map backend
  field errors, focus field lỗi đầu tiên và không reset form khi request thất bại.
- **PH-FE-04D — Success/navigation:** dùng `drugId` từ response để điều hướng detail; không tự dựng id
  hoặc dùng giá trị form làm snapshot chính thức.
- **PH-FE-04E — Permission UX:** chỉ ADMIN/PHARMACIST thấy entry/action; truy cập URL trực tiếp vẫn
  phải xử lý backend 403 rõ ràng.

**Test/verification chi tiết:**

- Serializer không gửi `createdAt`, `updatedAt`, `drugId`; threshold rỗng không biến thành `0`.
- Price `0` hợp lệ; price âm/quá 2 decimal/quá giới hạn bị chặn.
- Double-submit chỉ tạo một request; backend validation giữ toàn bộ giá trị đã nhập.
- Success điều hướng đúng UUID từ response.

**Không làm trong task này:** upload/import thuốc, bulk create, chỉnh sửa thuốc hiện có hoặc shared
form framework.

**Acceptance:** không gửi server-owned fields; double submit bị chặn; role không phù hợp không thấy form action.

### PH-FE-05 — Stock adjustment

**Trạng thái:** 5/5 work package đã có code production. ADMIN E2E chưa có release evidence;
PHARMACIST E2E vẫn bị block nếu token thiếu signed `staffId`.

**Dependency:** PH-FE-03.

**Files:** `AdjustStockForm.tsx`, `DrugDetail.tsx`, có thể `ConfirmDialog.tsx`.

**Implement:**

1. Form nằm ở drug detail hoặc dialog.
2. Quantity cho phép âm/dương, cấm 0.
3. Reason bắt buộc khi âm; với số dương vẫn cho phép nhập.
4. Confirmation hiển thị stock hiện tại, delta và phép tính preview; preview không phải source of truth.
5. Sau success dùng `DrugDTO` response để thay snapshot/refetch.
6. `DRUG_OUT_OF_STOCK`/`STOCK_BELOW_RESERVED` giữ form và giải thích conflict.

**Work packages:**

- **PH-FE-05A — Form/validation:** quantity là integer khác `0`; reason trim, bắt buộc khi quantity
  âm và giữ optional khi dương; không cho NaN/decimal lọt vào request.
- **PH-FE-05B — Confirmation:** hiển thị drug id/tên, stock hiện tại, delta có dấu và preview kết quả;
  cảnh báo preview có thể stale do reservation/concurrency.
- **PH-FE-05C — Mutation:** chống double-submit, gọi `adjustStock`, không cập nhật optimistic;
  callback đưa `DrugDTO` response vào detail rồi refetch nếu cần xác nhận audit snapshot.
- **PH-FE-05D — Conflict handling:** `DRUG_OUT_OF_STOCK` và `STOCK_BELOW_RESERVED` giữ quantity/reason,
  hiển thị message backend + correlation id, đồng thời refetch drug để cập nhật tồn hiện tại.
- **PH-FE-05E — Permission/identity:** ADMIN path phải test được; PHARMACIST UI đúng capability nhưng
  E2E ghi blocked cho tới khi token có signed `staffId`.

**Test/verification chi tiết:**

- Matrix quantity: âm, dương, `0`, decimal, rỗng; reason âm rỗng/whitespace/hợp lệ.
- Confirmation không gọi API; cancel dialog giữ nguyên snapshot.
- Success hiển thị stock từ response, không dùng phép cộng preview làm truth.
- Conflict refetch và chỉ gửi một request mỗi lần confirm.

**Không làm trong task này:** dispense prescription, lịch sử điều chỉnh tồn hoặc bypass reservation.

**Acceptance:** không optimistic commit; PHARMACIST E2E ghi blocked nếu JWT thiếu staffId; ADMIN E2E phải qua.

### PH-FE-06 — Create prescription và line editor

**Trạng thái:** 8/8 work package đã có code production. ADMIN E2E chưa có release evidence;
DOCTOR E2E còn blocker signed `staffId`.

**Dependency:** PH-FE-00, PH-FE-02, identity blocker được ghi nhận.

**Files:** `CreatePrescriptionForm.tsx`, `PrescriptionLinesEditor.tsx`, `prescriptionFormValidation.ts`,
`prescriptions/new/page.tsx`.

**Implement:**

1. Form nhập 4 UUID và prescribedDate.
2. Line editor thêm/xóa dòng, ít nhất một dòng.
3. Chọn thuốc từ catalog API; lưu `drugId`, không copy giá vào request.
4. Ngăn duplicate `drugId` trước submit.
5. Mỗi dòng chỉ gửi `drugId`, `quantity`, `dosage`.
6. Có thể hiển thị estimate từ current drug price, nhưng label rõ “tạm tính”; server response là chính thức.
7. Xử lý `INSUFFICIENT_AVAILABLE_STOCK` và `PRESCRIPTION_DUPLICATE_DRUG` tại line editor.
8. Thành công điều hướng detail id trả về.

**Work packages:**

- **PH-FE-06A — Prescription form model:** state cho 4 UUID, prescribedDate và danh sách line có
  client-only row key riêng; row key không được đi vào request.
- **PH-FE-06B — Context validation:** validate UUID/rỗng cho record/patient/doctor/department,
  prescribedDate không ở tương lai; label nói rõ hiện chỉ hiển thị/nhập UUID vì chưa có lookup contract.
- **PH-FE-06C — Drug picker:** tái sử dụng `searchDrugs` qua một component nằm trong feature Pharmacy;
  không import catalog component có URL ownership nếu điều đó gây coupling; hỗ trợ loading/error/empty.
- **PH-FE-06D — Line editor:** add/remove, tối thiểu một dòng, quantity integer >= 1, dosage <= 255,
  không cho duplicate drugId; disable thuốc đã chọn ở dòng khác nếu dùng select.
- **PH-FE-06E — Estimate presentation:** lấy tên/giá hiện tại chỉ để hiển thị “Tạm tính”; serializer
  chỉ nhận `drugId`, `quantity`, `dosage` và không chứa bất kỳ money field nào.
- **PH-FE-06F — Submit/error mapping:** xử lý field error cấp form và line; map duplicate/insufficient
  stock về dòng khi backend cung cấp đủ context, nếu không dùng banner và giữ toàn bộ form.
- **PH-FE-06G — Success:** điều hướng theo `PrescriptionDTO.prescriptionId`; detail sau đó fetch snapshot
  server chính thức, không truyền object form qua global state.
- **PH-FE-06H — Identity gate:** ADMIN E2E là đường kiểm chứng chính; DOCTOR UI được phép render nhưng
  không đánh dấu E2E pass nếu JWT chưa có signed `staffId` khớp `doctorId`.

**Test/verification chi tiết:**

- Pure serializer snapshot chứng minh không có `price`, `unitPrice`, `lineTotal`, `totalAmount`, row key.
- Add/remove line giữ tối thiểu một dòng; duplicate và quantity invalid bị focus đúng vị trí.
- Drug search failure không làm mất các line đã nhập.
- `INSUFFICIENT_AVAILABLE_STOCK` giữ form; success dùng id response để điều hướng.

**Không làm trong task này:** tạo patient/record/doctor/department lookup, tự sinh UUID, lưu draft lâu
dài hoặc gọi trực tiếp feature/service khác.

**Acceptance:** payload không chứa giá; ADMIN path dùng đúng response `prescriptionId`; DOCTOR E2E blocked cho tới
khi signed `staffId` có thật.

### PH-FE-07 — Prescription lookup và detail

**Trạng thái:** 7/7 work package của read flow đã có code production; action slot hiện đã được nối với
PH-FE-08/09. Test detail và race recovery vẫn thuộc PH-FE-11/12.

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `PrescriptionLookup.tsx`, `PrescriptionDetail.tsx`, prescription page và dynamic page.

**Implement:**

1. `/pharmacy/prescriptions` chỉ có form nhập known prescription UUID, không giả list.
2. Submit điều hướng tới dynamic detail route.
3. Detail hiển thị các UUID context, prescribedDate, totalAmount backend, line snapshots và audit cancellation.
4. Hiển thị cả `status` và `dispenseStatus`, vì chúng là hai state machine khác nhau.
5. 404 và missing dispense có trạng thái riêng.
6. Action cancel/dispense dựa trên role + state, nhưng vẫn xử lý race từ backend.

**Work packages:**

- **PH-FE-07A — Known-id lookup:** form một UUID, trim/validate, submit bằng router tới dynamic route;
  không gọi API tại trang lookup và không hiển thị list giả.
- **PH-FE-07B — Dynamic route guard:** server page `await params`, invalid UUID không mount detail fetch;
  metadata giữ tĩnh hoặc dùng id đã validate, không fetch ở server do localStorage token.
- **PH-FE-07C — Detail fetch/state:** fetch `getPrescription`, có loading/retry/not-found/forbidden và
  callback `refresh()` dùng chung cho cancel/dispense sau này.
- **PH-FE-07D — Header/lifecycle:** hiển thị prescription id, `status` và `dispenseStatus` bằng hai
  badge riêng; không suy một state từ state còn lại.
- **PH-FE-07E — Context/audit:** render record/patient/doctor/department UUID với label/copy action,
  prescribed/created/updated date và cancellation audit khi nullable fields có giá trị.
- **PH-FE-07F — Line table/amount:** render snapshot drug name/id, quantity, unit price, dosage,
  lineTotal và backend totalAmount; chỉ format, không recompute làm source of truth.
- **PH-FE-07G — Action boundary:** expose cancel/dispense slots theo capability + mutable state;
  dialog implementation thuộc PH-FE-08/09 và phải gọi `refresh()` sau success/conflict.

**Test/verification chi tiết:**

- Lookup invalid UUID không điều hướng; valid UUID tạo đúng URL.
- Detail invalid UUID/404/403/retryable error là bốn state khác nhau.
- Hai lifecycle badge render đúng mọi enum; cancellation audit ẩn khi null.
- Không có request tới `/prescriptions` dạng list; amount hiển thị từ response.

**Không làm trong task này:** list/search prescription, lookup tên cross-service, mutation cancel/dispense.

**Acceptance:** deep-link hoạt động; invalid UUID không mount fetch; không có request list prescription; money chỉ
format, không tính lại làm truth; missing dispense được hiển thị riêng.

### PH-FE-08 — Cancel prescription

**Trạng thái:** 5/5 work package đã có code production trong `CancelPrescriptionDialog.tsx` và
`PrescriptionDetail.tsx`; test matrix/E2E vẫn thuộc PH-FE-11/12.

**Dependency:** PH-FE-07, identity blocker được ghi nhận.

**Files:** `CancelPrescriptionDialog.tsx`, cập nhật `PrescriptionDetail.tsx`.

**Implement:**

1. Chỉ show cho ADMIN/DOCTOR khi ACTIVE + PENDING.
2. Require reason 1–500 chars.
3. Confirmation cảnh báo reservation sẽ được release.
4. Sau success refetch detail; hiển thị `releasedReservations`.
5. Nếu response idempotent có `releasedReservations=0`, hiển thị “đơn đã được hủy trước đó”.
6. Lifecycle conflict refetch trước khi cho retry.

**Work packages:**

- **PH-FE-08A — Visibility/state gate:** chỉ render trigger khi capability cancel và pair state là
  ACTIVE/PENDING; component vẫn xử lý 403/422 vì state có thể đổi sau render.
- **PH-FE-08B — Dialog/form:** reason 1–500 sau trim, hiển thị prescriptionId và cảnh báo release
  reservation; focus trap/escape/return focus nếu dùng modal shared.
- **PH-FE-08C — Submit:** disable confirm, gọi `cancelPrescription`, không optimistic; giữ reason khi
  lỗi validation/forbidden và đọc correlation id từ `ApiRequestError`.
- **PH-FE-08D — Success/idempotency:** hiển thị released count; count `0` dùng thông điệp idempotent;
  đóng hoặc chuyển dialog sang success state rồi gọi detail refresh.
- **PH-FE-08E — Race recovery:** với lifecycle/reservation conflict, refetch trước; nếu action không
  còn hợp lệ thì ẩn trigger và giải thích trạng thái mới.

**Test/verification chi tiết:**

- Matrix role × ACTIVE/PENDING xác định đúng visibility.
- Reason boundary: empty, whitespace, 1, 500, 501 ký tự.
- Double confirm chỉ gửi một PUT; success luôn refetch.
- `releasedReservations=0`, forbidden và lifecycle conflict có message riêng.

**Không làm trong task này:** undo cancel, cancel hàng loạt hoặc giả `staffId` từ account id.

**Acceptance:** không optimistic status; ADMIN E2E qua; DOCTOR E2E blocked đến signed `staffId`.

### PH-FE-09 — Dispense prescription

**Trạng thái:** 6/6 work package đã có code production trong `DispensePrescriptionDialog.tsx` và
`PrescriptionDetail.tsx`; test payment-proof/terminal failure/E2E vẫn thuộc PH-FE-11/12.

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

**Work packages:**

- **PH-FE-09A — Visibility/state gate:** chỉ ADMIN/PHARMACIST với ACTIVE/PENDING thấy trigger; backend
  vẫn quyết định payment proof và terminal race.
- **PH-FE-09B — Confirmation:** hiển thị prescriptionId, tổng tiền, số dòng và cảnh báo “Billing phải
  xác nhận thanh toán”; không có paid checkbox, invoice input hoặc override field.
- **PH-FE-09C — Bodyless mutation:** gọi `dispensePrescription(id)` đúng một lần, PUT không body;
  disable close/confirm phù hợp khi request đang chạy.
- **PH-FE-09D — Payment-proof handling:** `PAYMENT_PROOF_REQUIRED` giữ detail PENDING, giữ dialog/message,
  đặt cooldown UI hoặc yêu cầu kiểm tra Billing; không tự retry loop.
- **PH-FE-09E — Terminal/success refresh:** success hiển thị `DispenseDTO`, refetch prescription; nếu
  detail đang giữ drug snapshot thì refetch chúng bằng API Pharmacy, không tính stock client-side.
- **PH-FE-09F — Failure:** hiển thị `failureReason` khi response/detail có; lifecycle/reservation error
  trigger refresh để UI phản ánh trạng thái terminal mới.

**Test/verification chi tiết:**

- Request không có body/`paid`/`invoiceId`; double click chỉ một PUT.
- Matrix role/state đúng; PHARMACIST E2E vẫn blocked nếu thiếu signed `staffId`.
- `PAYMENT_PROOF_REQUIRED` không đổi local status và không tự retry.
- Success/refusal đều refetch; idempotent DISPENSED response không trừ stock phía UI.

**Không làm trong task này:** tạo payment, sửa invoice, manual payment override hoặc optimistic stock.

**Acceptance:** double click chỉ gửi một request; ADMIN E2E qua khi payment proof fixture tồn tại; PHARMACIST
E2E blocked đến signed `staffId`.

### PH-FE-10 — Admin outbox replay

**Trạng thái:** 0/5 work package; route hiện vẫn là placeholder và chưa gọi API replay.

**Dependency:** PH-FE-00, PH-FE-01.

**Files:** `OutboxReplayForm.tsx`, `admin/outbox/page.tsx`.

**Implement:**

1. ADMIN-only UX.
2. Chỉ input UUID event id và confirmation.
3. POST không body.
4. Thành công hiển thị eventId + replayed.
5. 404 giữ form để sửa id.
6. Không tạo bảng/danh sách/quarantine filters.

**Work packages:**

- **PH-FE-10A — Route capability state:** ADMIN thấy tool; non-admin thấy forbidden UX rõ ràng khi
  vào URL trực tiếp, đồng thời backend 403 vẫn được xử lý.
- **PH-FE-10B — Input/form:** một event UUID duy nhất, trim/validate, giữ nguyên sau lỗi; mô tả rõ
  người vận hành phải lấy id từ nguồn quan sát bên ngoài.
- **PH-FE-10C — Confirmation:** hiển thị chính xác eventId và hậu quả replay; không hứa event sẽ xử lý
  nghiệp vụ thành công, chỉ yêu cầu đưa lại hàng đợi phát.
- **PH-FE-10D — Bodyless mutation:** POST không body, chống double-submit; success hiển thị eventId và
  `replayed` trong vùng `aria-live`.
- **PH-FE-10E — Error handling:** `OUTBOX_EVENT_NOT_FOUND` giữ input; 403, validation, internal error
  và correlation id có presentation riêng.

**Test/verification chi tiết:**

- Invalid UUID không gọi API; valid UUID gửi đúng path và không body.
- ADMIN success render đúng `replayed`; 404 giữ input và focus hợp lý.
- Non-admin không có nav/action; direct route không giả thành 404.
- Scan không có endpoint list/quarantine hoặc bảng event.

**Không làm trong task này:** list/search outbox, replay hàng loạt, sửa payload hoặc xóa outbox row.

**Acceptance:** non-admin không thấy nav/action; backend 403 vẫn được xử lý nếu truy cập URL trực tiếp.

### PH-FE-11 — Test infrastructure và contract tests

**Trạng thái:** 0/6 work package; `package.json` hiện chưa có script `test` và repo chưa có test
Pharmacy tự động.

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

**Work packages:**

- **PH-FE-11A — Tooling decision:** xác nhận team cho phép thêm Vitest, Testing Library, jsdom và
  Playwright (nếu có browser CI). Ghi dependency/script chính xác vào PR; không tự thêm framework
  thứ hai nếu repo đã chuẩn hóa công cụ khác trước khi task bắt đầu.
- **PH-FE-11B — Unit harness:** tạo config/setup tối thiểu, alias `@/*`, cleanup DOM, deterministic
  timezone/clock và helpers tạo `ApiRequestError`/DTO fixture.
- **PH-FE-11C — Pure contract tests:** query builder, UUID/date/money helper, permission matrix,
  status exhaustiveness, create-prescription serializer và stock/cancel validation.
- **PH-FE-11D — Component tests:** mock `pharmacyApi` tại module boundary; kiểm tra state matrix của
  catalog/detail/form/dialog, keyboard/focus và duplicate-submit.
- **PH-FE-11E — Route/browser tests:** ưu tiên ADMIN flows; seed/fixture phải dùng API công khai hoặc
  test setup được duyệt, không chạm DB service khác; staff flows gắn explicit skip reason nếu blocker.
- **PH-FE-11F — Contract drift guard:** fixture JSON tối thiểu cho Drug/Prescription/Dispense/error
  envelope; parse theo TypeScript assumptions và đối chiếu thủ công với Java records/web tests.

**Test organization đề xuất:**

```text
frontend/src/features/pharmacy/
├── __tests__/
│   ├── api.test.ts
│   ├── permissions.test.ts
│   ├── presentation.test.ts
│   └── utils.test.ts
└── components/**/__tests__/*.test.tsx

frontend/tests/e2e/pharmacy/*.spec.ts       # chỉ khi Playwright được duyệt
```

**Coverage/quality rules:**

- Không đặt mục tiêu phần trăm hình thức trước khi có baseline; mọi rule/branch quan trọng trong
  task breakdown phải có một test quan sát được.
- Test không assert Tailwind class dài; assert role/name/text/state/side effect.
- Không mock `fetch` trong component tests; component mock facade, riêng `api.ts` test shared wrapper
  hoặc request contract ở boundary phù hợp.
- Không snapshot toàn trang; dùng assertion tập trung vào contract và trạng thái.

**Definition of Done riêng:** `pnpm test` chạy non-watch và exit code 0; test không phụ thuộc thứ tự,
timezone máy hoặc network thật; skip phải có issue/blocker cụ thể.

### PH-FE-12 — Quality gate và tài liệu vận hành

**Trạng thái:** chưa work package nào đủ điều kiện đóng (0/8). `pnpm typecheck`, `pnpm lint`,
`pnpm build` đã xanh tại lần kiểm tra 2026-09-21 và architecture scan hiện không phát hiện raw fetch
ngoài `lib/api.ts`; chưa có `pnpm test`/contract smoke/E2E/accessibility/release evidence.

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

**Work packages:**

- **PH-FE-12A — Static gate:** chạy typecheck/lint/test/build từ clean install; lưu command và kết quả
  trong PR, không chỉ ghi “đã test”.
- **PH-FE-12B — Architecture scan:** xác nhận raw fetch chỉ ở `lib/api.ts`, không hard-code `8085`,
  không cross-feature import, không invent endpoint, không thêm component/data library ngoài duyệt.
- **PH-FE-12C — Contract smoke:** chạy 9 request contract phù hợp role/fixture; với mutation destructive
  dùng dữ liệu test cô lập, ghi id đã tạo và trạng thái cuối.
- **PH-FE-12D — Role matrix:** kiểm tra ADMIN/DOCTOR/PHARMACIST cho nav/action và backend 403; phân biệt
  UI-hidden với server-enforced; ghi rõ staff path pass hay blocked.
- **PH-FE-12E — Accessibility/responsive:** keyboard-only, focus sau validation/dialog, aria-live,
  contrast/status text, table overflow ở mobile và zoom 200%.
- **PH-FE-12F — Failure/recovery:** tắt gateway/pharmacy hoặc dùng lỗi fixture để kiểm tra retry,
  correlation id, form preservation và route error boundary.
- **PH-FE-12G — Documentation:** cập nhật frontend README/runbook, env `GATEWAY_URL`, route/role matrix,
  known blockers và test data requirements; xóa TODO của các task đã hoàn thành.
- **PH-FE-12H — Release evidence:** ghi commit SHA backend/frontend đã verify, browser/Node/pnpm version,
  command output tóm tắt và danh sách deferred item có owner.

**Definition of Done riêng:** bốn lệnh quality gate xanh; không còn placeholder thuộc milestone;
ADMIN happy path và expected-error path đã kiểm tra; mọi blocker còn lại được mô tả bằng contract/owner,
không bằng câu chung chung “chưa test”.

## 11. Dependency graph và cách chia việc

```text
PH-FE-00
   └── PH-FE-01
       ├── PH-FE-02 ── PH-FE-03 ── PH-FE-05
       ├── PH-FE-04
       ├── PH-FE-07 ── PH-FE-08
       │           └── PH-FE-09
       └── PH-FE-10

PH-FE-02 ── PH-FE-06

PH-FE-11 chạy tăng dần cùng từng nhánh
PH-FE-12 chạy cuối mỗi milestone/release
```

Có thể triển khai song song sau PH-FE-01:

- nhánh Drug read: PH-FE-02 → PH-FE-03 → PH-FE-05;
- nhánh Drug create: PH-FE-04 độc lập sau PH-FE-01;
- nhánh Prescription read: PH-FE-07 → PH-FE-08/09;
- nhánh Prescription create: PH-FE-06 bắt đầu sau khi contract/component tìm thuốc của PH-FE-02 ổn định;
- nhánh Admin: PH-FE-10;
- nhánh Test: PH-FE-11.

### 11.1 File ownership khi triển khai song song

| Nhánh/task | File sở hữu chính | File có nguy cơ conflict | Quy tắc merge |
|---|---|---|---|
| PH-FE-02 | `DrugCatalog.tsx`, `DrugTable.tsx`, `drugs/page.tsx` | `Pagination.tsx` nếu cần sửa shared API | Không sửa detail/create form |
| PH-FE-03 | `DrugDetail.tsx`, `drugs/[drugId]/page.tsx` | `DrugDetail.tsx` sẽ được PH-FE-05 mở rộng | Merge PH-FE-03 trước PH-FE-05 |
| PH-FE-04 | `CreateDrugForm.tsx`, `drugs/new/page.tsx` | field primitives shared | Chỉ extract shared UI khi có consumer thứ hai |
| PH-FE-05 | `AdjustStockForm.tsx`, phần action trong `DrugDetail.tsx` | `DrugDetail.tsx` | Bắt đầu trên phiên bản PH-FE-03 đã merge |
| PH-FE-06 | `CreatePrescriptionForm.tsx`, `PrescriptionLinesEditor.tsx`, `prescriptions/new/page.tsx` | drug-search primitive từ PH-FE-02 | Không import component URL-owned nếu chỉ cần picker |
| PH-FE-07 | `PrescriptionLookup.tsx`, `PrescriptionDetail.tsx`, hai route lookup/detail | `PrescriptionDetail.tsx` | Merge trước PH-FE-08/09 |
| PH-FE-08 | `CancelPrescriptionDialog.tsx`, cancel slot trong `PrescriptionDetail.tsx` | `PrescriptionDetail.tsx` | Nếu song song PH-FE-09, thống nhất slot interface trước |
| PH-FE-09 | `DispensePrescriptionDialog.tsx`, dispense slot trong `PrescriptionDetail.tsx` | `PrescriptionDetail.tsx` | Không ghi đè cancel integration |
| PH-FE-10 | `OutboxReplayForm.tsx`, outbox page | không | Có thể merge độc lập |
| PH-FE-11 | test/config tương ứng feature đã merge | `package.json`, lockfile, test setup | Một owner duy nhất cho tooling/lockfile |

### 11.2 Trình tự PR khuyến nghị

```text
PR-A  PH-FE-02                       Drug catalog
PR-B  PH-FE-03 + PH-FE-05            Drug detail và stock adjustment
PR-C  PH-FE-04                       Create drug
PR-D  PH-FE-07                       Prescription lookup/detail
PR-E  PH-FE-06                       Create prescription
PR-F  PH-FE-08 + PH-FE-09            Prescription terminal actions
PR-G  PH-FE-10                       Admin outbox replay
PR-H  PH-FE-11 + PH-FE-12            Test hardening và release gate
```

PR có thể nhỏ hơn theo hậu tố work package, nhưng không merge một PR để lại route production ở trạng
thái nửa form/nửa placeholder. Nếu nhiều AI cùng làm, mỗi AI phải được giao ownership rõ ràng, không
sửa/revert file của nhánh khác và phải báo file shared trước khi chạm vào.

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
Implement task <PH-FE-ID> or work packages <PH-FE-ID-A...> from
frontend/docs/pharmacy-frontend-implementation-plan.md.

Backend source of truth is the current code under backend/pharmacy-service, especially the
controllers, DTO records, enums, application services, and web tests named by the plan. Do not
invent endpoints or fields from design docs.

Before editing, report:
1. exact live endpoint contract used (method/path/request/response/role/error codes);
2. work packages being implemented and files owned;
3. existing working-tree changes that must be preserved;
4. dependencies/blockers and what evidence will prove completion.

Own only the files listed by the task. Preserve other working-tree changes. Follow
frontend/AGENTS.md: gateway only, all HTTP through src/lib/api.ts, no cross-feature imports, no
data-fetching/component library. Implement every state and test listed by the selected work packages.

After coding, report changed files, completed work packages, state/error matrix covered, deferred
items and exact verification commands/results. Run task acceptance checks plus pnpm typecheck,
pnpm lint, and pnpm build. If `staffId` is required, do not fake it; use a real typed Gateway token
and verify the current identity contract.
```

## 13. Milestone đề xuất

### Milestone A — Read-only pharmacy

PH-FE-00, 01, 02, 03, 07 (lookup/detail only).

**Tiến độ hiện tại:** 5/5 task đã có code production (`PH-FE-00` đến `PH-FE-03`, `PH-FE-07`);
test tự động và browser evidence chưa đóng.

Kết quả mục tiêu: ADMIN/DOCTOR/PHARMACIST đọc catalog và prescription id đã biết.

### Milestone B — Inventory operations

PH-FE-04, 05.

**Tiến độ hiện tại:** 2/2 task đã có code production (`PH-FE-04`, `PH-FE-05`); ADMIN E2E chưa có
release evidence, staff E2E điều chỉnh kho còn phụ thuộc signed `staffId` trong JWT.

Kết quả mục tiêu: ADMIN hoàn chỉnh; PHARMACIST UI có thể implement nhưng E2E phụ thuộc staffId claim.

### Milestone C — Prescription workflow

PH-FE-06, 08, 09.

**Tiến độ hiện tại:** 3/3 task đã có code production (`PH-FE-06`, `PH-FE-08`, `PH-FE-09`); test/E2E
và payment fixture chưa có release evidence.

Kết quả mục tiêu: ADMIN có thể tạo/hủy/xuất theo contract; DOCTOR/PHARMACIST phụ thuộc staffId và payment proof.

### Milestone D — Admin recovery và hardening

PH-FE-10, 11, 12.

**Tiến độ hiện tại:** 0/3 task đóng. PH-FE-10/11 chưa bắt đầu; PH-FE-12 mới có static checks một phần.

Kết quả mục tiêu: outbox replay known-id, coverage, accessibility và production build.

## 14. Definition of Done

- [x] Chỉ dùng 9 endpoint có thật trong controller hiện tại.
- [x] DTO TypeScript mirror đúng record/enum backend hiện tại.
- [x] Không có price trong create prescription request.
- [x] Không có payment flag/invoice id trong manual dispense request.
- [x] Search/pagination nằm trong URL và hỗ trợ back/forward/reload theo implementation.
- [x] Pages là Server Components; client boundary chỉ ở interactive/data components.
- [x] `useSearchParams` nằm dưới Suspense.
- [ ] Expected API errors có UI state cho toàn bộ scope; read/create/stock/cancel/dispense đã có,
  PH-FE-10 chưa triển khai.
- [x] Mutation hiện có không optimistic; cancel/dispense cũng refetch snapshot chính thức sau kết quả.
- [x] Role visibility hiện tại đúng controller và backend vẫn xử lý 403.
- [x] Không invent prescription/outbox list API.
- [x] Không dùng account id thay staff id; staff E2E được ghi blocker thay vì fake claim.
- [ ] Typecheck, lint và production build đã xanh; `pnpm test` chưa tồn tại nên quality gate chưa đủ.
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
