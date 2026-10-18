# HANDOFF: Shared FE → Surgery — Idempotency-Key qua API wrapper

- **Status:** ACTIVE — producer code implemented 2026-10-18; behavioral/consumer acceptance OPEN.
- **Producer owner:** Vinh (`Dangvinh77` / `Harori`), shared frontend.
- **Consumer owner:** Huy (`LQHuy0210`), Surgery frontend.
- **Affected contexts:** shared HTTP boundary, planned Surgery command UI.
- **Affected paths:** producer `frontend/src/lib/api.ts` và test của HTTP boundary trong scope shared;
  consumer `frontend/src/features/surgery/api.ts`, command state và tests thuộc Surgery.
- **Verified baseline:** `c110671b`, 2026-10-12.
- **Resume owner:** Huy, task S-04 trong [plan FE](../huy-frontend-implementation-plan.md).

## Producer checkpoint — 2026-10-18

Vinh implemented `api.post/put/postRaw(path, data?, { idempotencyKey })` in the shared wrapper.
Only Idempotency-Key is accepted; bearer/method/body stay controlled by the wrapper. Blank,
more-than-160-character and CR/LF keys are rejected; bodyless requests remain bodyless.
Existing two-argument consumers remain compatible. Verification in this session is build only
at the user's request; header propagation/error regression tests and Huy's consumer acceptance
have not run. Keep this handoff ACTIVE until the acceptance below is met. Surgery stays deferred;
the same shared facility is available to the active outpatient Pharmacy V1 work.

## Historical source finding

Public `api.post(path, data?)` và `api.put(path, data?)` chưa nhận options/header.
Hàm private `requestJson` có xử lý `RequestInit.headers`, nhưng feature không thể truyền header qua
public API. Các Surgery mutation controller yêu cầu `Idempotency-Key` (nonblank, tối đa 160 ký tự).
Creation còn yêu cầu key trùng `surgeryRequestId`. Request body và actor/revisions vẫn theo DTO.

## Việc cần làm

1. Bổ sung cách truyền request header được kiểm soát cho public POST/PUT, giữ chữ ký gọi hiện hữu
   tương thích ngược. Vinh quyết định public options type ở shared boundary.
2. Giữ bearer/correlation/error/envelope/401 semantics hiện tại; không cho options vô hiệu hóa
   signed-session authorization hoặc ghi đè method/body ngoài API được công bố.
3. Giữ bodyless mutation: không tự tạo `{}` hoặc Content-Type khi request thực sự không có body.
4. Cung cấp test chứng minh request đi qua `/api/v1/...`, có key và bearer đúng, API cũ vẫn chạy.

## Acceptance

- Surgery gọi shared wrapper với key ổn định, không raw fetch, không gọi cổng service.
- Caller cũ chỉ truyền path hoặc path/body tiếp tục biên dịch và giữ behavior.
- Test header propagation, bodyless request, backend 401/403/409 và error correlation pass.
- Không log token, key gắn thông tin bệnh nhân hoặc payload y khoa ra console.
- Consumer test của Huy chứng minh same intent giữ key; changed intent không tái dùng key.

Handoff này không cấp quyền sửa shared cho Huy và không bật Surgery/Gateway. Không đưa binary
Report archive vào deliverable này. Đóng/xóa handoff theo registry sau khi producer và consumer
đều đạt; giữ public options contract trong tài liệu shared phù hợp.
