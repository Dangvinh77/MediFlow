# Frontend Service: Billing

## Cashier work queue — 2026-11-02

`/billing/payment-requests` now starts with the Billing-owned Lab/Rx queue, filtered by
patient/purpose and optionally the exact episode of a selected row. Amounts, source references,
eligibility and creation time come from `GET /v1/billing/payment-requests/queue`; creation time
is not a clinical appointment date. Selection re-reads `/by-source` and checks all identities
before mounting payment. Manual identifiers remain in collapsed support lookup, not the main task.

Positive Lab payments and zero acknowledgments lock queue/context changes until their outcome
is known. Quote expiry preserves an existing same-intent retry. Captured-session storage and
pre-send guards prevent cross-session dispatch/deletion; aborting a replay retains the older
unknown recovery. Completed receipts survive refresh failure; stale balance cannot reopen the
same payment. Existing command routes, gates and scheduled exam-fee queue remain authoritative.

Scope/design: [MF-03 specification](../../../docs/superpowers/specs/2026-11-02-outpatient-billing-work-queue.md).
The older checkpoints below describe their dated baseline, not new queue acceptance evidence.

> **Nghiệp vụ đích 25/10/2026:** lịch khám theo ca, đăng ký tại quầy, tái khám và hỗ trợ BHYT
> mô phỏng theo [02-business-flows](../../../docs/plans/02-business-flows.md).
> Các trạng thái route, test và evidence bên dưới chỉ chứng minh baseline tương ứng;
> không tự chứng minh nghiệp vụ mới đã có. Triển khai theo
> [WF-01–11](../../../docs/plans/07-outpatient-workflow-implementation.md), giữ ownership frontend
> và khóa DTO thật trước đổi UI. Chính sách BHYT/tỷ lệ do dự án chọn, mặc định thẻ còn hạn.

**Frontend owner từ 27/10/2026:** Vinh (`Dangvinh77` / `Harori`), quản lý phần của Lộc theo
phân công trực tiếp. Vinh cũng phụ trách toàn bộ backend. Các delegation/owner ngày trước
trong evidence giữ làm lịch sử; không tiếp tục giao việc Billing cho Lộc.

**Backend:** `backend/billing-service/**`
**Contract context:** [`docs/ai/services/billing.md`](../../../docs/ai/services/billing.md)

## Current implementation — 2026-10-20

`/billing` supports paginated patient invoice lookup and prescription-adjustment refund lookup.
`/billing/new` creates an invoice from server-owned fees; invoice detail displays fees and accepts
payment. Confirmed payment refreshes authoritative fee rows; a refresh failure cannot reopen payment.
An uncertain V0 payment stays locked because that endpoint has no idempotency contract. Checking
status only reads; an unpaid read after an uncertain response does not authorize another payment.

`/billing/payment-requests` discovers the exact server request from purpose, source, patient and
care episode through `GET /v1/billing/payment-requests/by-source`. EXAM uses the appointment source
identity defined by the contract; LAB_TEST/PRESCRIPTION require the explicit episode reference.
Amount, remaining amount, currency, expiry and account/request status come from Billing. A direct
request-ID route links back to discovery before payment. No free-text currency or guessed balance.
Ledger payment accepts CASH/TRANSFER and at most the server remaining amount. It locks synchronously,
retains the same payload/idempotency key for explicit retry after uncertainty, and only COMPLETED
means success. Lookup remains locked while payment is pending or uncertain, preventing a fresh
read from unmounting the payment intent. Session changes clear private drafts/results.

Prescription refund lookup uses `GET /v1/billing/prescription-adjustments/{id}/refunds-due`.
ADMIN/CASHIER can record an already completed refund with
`POST /v1/billing/transactions/{id}/refunds`, explicit amount/reason/method and reconciliation
confirmation. The obligation is not presented as the remaining refundable balance. The backend
validates eligibility and cumulative refunds. Unknown outcomes block blind resend. This command
records a cashier refund; it does not transfer money through a bank.
The 2026-10-21 lookup update adds an in-place “Tra cứu đơn khác” action after a definitive
outcome. Pending, uncertain and non-completed refund results keep that action locked. A completed
refund retains its receipt and reloads the authoritative adjustment; a read failure keeps the
receipt, blocks writes against the stale snapshot, and offers a read-only retry. A successful
refresh clears refund drafts and requires a fresh reconciliation confirmation for another refund.
The returned obligation remains server-owned; the client does not subtract recorded refunds.
The previous Gateway blocker is superseded by implemented rules (PR #431); the existing handoff's
broader acceptance criteria remain owned by its maintainers.

This route (`RefundDueLookup.tsx`, Vinh's 2026-10-20 closeout) is the single implementation for
prescription-adjustment refund lookup. Lộc's own 2026-10-19 parallel implementation
(`PrescriptionAdjustmentRefundDue.tsx` + `PrescriptionAdjustmentLookupForm.tsx` under
`/billing/prescription-adjustments`) duplicated this exact business function and was removed on
2026-10-22 to avoid two components doing the same job; see the frontend workboard for the decision.

Revenue remains ADMIN/MANAGER; outbox replay remains ADMIN only. Replay feedback distinguishes
`replayed: false` and successful requeue from downstream processing. Invoice/refund/payment UI is
ADMIN/CASHIER, with backend authorization authoritative.

## Owner queue

- `FE-BILLING-01/02` — DONE: exact invoice DTO, create/detail/fee flows.
- `FE-BILLING-03` — DONE for implemented MVP payment and prescription refund recording.
- `FE-BILLING-04` — **Lộc, source recheck 2026-10-22:** current tracked source has dedicated
  `RevenueReport.test.tsx`, `InvoiceForm.test.tsx` and `OutboxReplayForm.test.tsx`, alongside
  the existing command, invoice, payment and refund suites. The earlier handoff's missing-test
  description is historical and must not assign those files as new work again. This reference UI
  change reruns the component suites and preserves duplicate/uncertain command safeguards.
  Current verification is recorded in the [reference UI plan](../../../docs/superpowers/plans/2026-10-22-frontend-human-readable-references.md);
  full signed-runtime/coverage acceptance remains a distinct owner checkpoint.
- `FE-BILLING-05/06` — DONE: existing revenue and outbox routes; replay feedback hardened.
- `FE-BILLING-07` — DONE for prescription adjustment/refund (Vinh's `RefundDueLookup`); Surgery
  remains deferred from MVP.
- `FE-BILLING-08` — DONE: source discovery and server-backed selected-charge ledger payment.

## Acceptance and remaining dependencies

See [2026-10-20 evidence](../evidence/vinh-loc/2026-10-20/README.md) for signed browser flows,
verification and limits. Legacy V0 invoice amounts still have no currency field; display them
without inventing VND. V1 currency is the server response currency. Patient/staff display selectors
and cross-context enrichment require their own authorized contracts. No client-side joins.
Production flag rollout and external payment/provider acceptance are separate release work.

- Legacy V0 currency labels are blocked until Billing or a product decision defines currency
  semantics. Acceptance: explicit currency/display rule; never assume VND from locale.
  `FE-BILLING-08` already uses the V1 response currency; do not reopen free-text currency input.
- Patient display enrichment requires Patient's live contract. Producer: Patient / Hoàng Anh.
- Lab/Pharmacy references and saga state must come from Billing DTOs/events; do not query or import
  those frontend features to reconstruct an invoice.

## Contract and verification gate

Verify live controller paths, roles, request validation, monetary serialization, idempotency, and
saga enums. Java `BigDecimal` responses are display-only numbers in the current frontend; do not
perform authoritative financial arithmetic in JavaScript.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Billing tests when available. Smoke test
invalid patient UUID, empty/error/retry, pagination, permission denial, and the changed payment flow.


## Khả dụng thu phí lịch khám — 26/10/2026

Luồng chính dùng ScheduledPaymentQueue: tìm bệnh nhân theo tên/ngày sinh, chọn khoản chờ thu,
rồi tải request/quote hiện hành. Mã đối soát thu gọn khi chọn từ danh sách; số tiền định dạng
chỉ phục vụ hiển thị, command giữ decimal string server. Queue không cấp quyền thu và không
thay reservation lock/idempotency/uncertain recovery. Contract A.13 trong docs/plans/03-contracts.md.

## Dọn giao diện thu ngân — 04/11/2026

- `/billing` là Lịch sử thu; hoàn tiền chuyển sang `/billing/refunds`. Phí khám và Xét nghiệm/thuốc vẫn là hai hàng chờ riêng theo hợp đồng hiện hành.
- Tra cứu bằng mã là mục hỗ trợ thu gọn dưới nội dung chính. Trạng thái saga, tài khoản và thời điểm đọc nằm trong thông tin đối soát; không bỏ dữ liệu nghiệp vụ khỏi chi tiết.
- Sau khi phí khám được xác nhận hoàn tất, tự đọc lại trạng thái bằng GET. Không còn nút Cập nhật số dư. Chỉ hiện Thử kiểm tra lại khi lần đọc lỗi hoặc chưa phản ánh kết quả; giữ biên nhận và không gửi lại lệnh thu.
- Tra cứu lại phiếu đã hoàn tất trong cùng phiên không được mở lại thu tiền khi phản hồi cũ vẫn báo chưa thanh toán. Thu khoản tiếp theo chỉ xóa lựa chọn sau khi kết quả đã rõ và không còn yêu cầu đang xử lý.
- Flutter giữ biên nhận ngoài form thu, thu gọn mã đối soát, dịch trạng thái và chỉ mở lại thu một phần khi số đã thu mới phản ánh ít nhất số tiền của biên nhận vừa nhận.

Bằng chứng: [kế hoạch và kết quả kiểm tra](../../../docs/superpowers/plans/2026-11-04-cashier-ui-cleanup.md). Kiểm tra trình duyệt dùng Gateway mô phỏng cục bộ, không phải nghiệm thu với backend thật hoặc nhà cung cấp thanh toán. Không đổi API/backend hay bật cờ tính năng trong cấu hình triển khai.
