# Xác minh phản hồi của Lộc — 19/10/2026

## Nguồn và phát hiện

Remote đã fetch: `origin/master` = `4c19dd4a8ede06c07545c9290ff77b687b8dd49c`.
Checkout gốc ban đầu có thay đổi chưa commit nên Git chặn pull để bảo vệ dữ liệu.
Sau khi lưu tracked changes vào stash dự phòng, đã fast-forward checkout gốc lên cùng SHA
và apply lại thành công, không có conflict; untracked files giữ nguyên.
Lượt kiểm tra chạy trên managed worktree riêng từ đúng remote SHA, branch `codex/backend-loc-verification`.
Không đưa thay đổi chưa commit của checkout gốc vào kết quả baseline remote.

Nhận định của Lộc **đúng đối với nguồn chung**: hai file test Report/Pharmacy chưa đổi
trên master sau Gateway fix; các sửa đã thực hiện ở phiên trước chỉ còn trong local diff.
`git diff v0.9.0-backend-feature-complete origin/master -- backend/report-service backend/pharmacy-service backend/gateway`
không có thay đổi; tag trỏ `8d7e6c690d7816f7a502acc4db145031c62e999e`, không chứa hai fix.
Không suy từ tên tag rằng mọi acceptance đã đóng.

## Kết quả mới

- Gateway refund acceptance: **11/11 pass**, 0 failure/error/skip, exit 0.
- Gateway default suite: **1.449/1.449 pass**, 0 failure/error/skip, exit 0.
- Report baseline targeted: **8/8 pass** lần này; lỗi clock vẫn tồn tại trong source.
  Một lần pass không loại trừ flaky giữa clock PostgreSQL và JVM.
- Report full suite sau sửa clock: **1.331/1.331 pass**, 0 failure/error/skip, exit 0.
- Hai targeted reruns sau sửa: **8/8 mỗi lượt**, exit 0, không failure/error/skip.
- Pharmacy opt-in trước sửa: **1 test, 0 failure, 1 error, 0 skip**, exit 1; timeout 35s
  ở `invoice.created`, đúng lỗi PR #429. Sau sửa: **1/1 pass**, exit 0, không skips.
- Pharmacy outpatient mặc định sau sửa: **3 cases, 2 pass, 1 skip**, exit 0; ca issuer-chain
  opt-in được skip có chủ đích. Không tính ca skip là pass; ca đó đã chạy thật ở lượt trên.
- Package fresh Pharmacy/Billing/Report và common: exit 0, không chạy tests.

Exact commands, thời điểm kết thúc, counts, exit và private-log hashes ở [results.json](results.json).
[Source lock](source-lock.json) ghi base commit và SHA-256 hai test files;
[patch hai test](test-fixes.patch) cho phép tái lập diff chưa commit. Raw logs/XML không
publish vì có environment properties; [manifest](manifest.json) kiểm bytes bundle.

## Sửa test, không đổi production contract

Report: chặn `reader.capture()` bằng `clock_timestamp()` trên cùng DB trước/sau;
giữ các assertions backlog, coverage và tính hợp lệ timestamp. Không sleep/nới timeout
hoặc sửa timestamp production. Pharmacy: đã cấu hình isolated Billing publisher đúng
MVP mode và publication purpose/reference khi explicit opt-in; default vẫn OFF. Bổ sung
restart thứ hai sau khi grant đã persist, rồi replay original grant/payment bytes để xác
nhận không tự dispense hoặc đổi stock. Sau explicit dispense, retry vẫn giữ dispense ID.

## Contract cho màn hình Billing cuối

| Thao tác | Method/path | Quyền |
|---|---|---|
| Xem nghĩa vụ hoàn tiền của điều chỉnh đơn | `GET /api/v1/billing/prescription-adjustments/{id}/refunds-due` | ADMIN, CASHIER |
| Ghi nhận tiền đã hoàn của giao dịch gốc | `POST /api/v1/billing/transactions/{id}/refunds` | ADMIN, CASHIER |

Refund request dùng JSON `idempotencyKey`, `amount`, `reason`, `paymentMethod` theo
`RefundLedgerPaymentRequest`; không đổi thành header-only key. Actor lấy từ signed
principal; command thành công trả HTTP 201 với `ApiResponse<LedgerRefundDTO>`.
Giữ các gates ledger/prescription-charge-consumer/refunds như controller hiện hành.
`refunds-due` là nghĩa vụ cần hoàn; POST ghi nhận refund đã thực hiện, không gọi ngân hàng.

Gateway fix `2805b07c` đã có trong nguồn kiểm tra. Phiên này không cần sửa production
Gateway, Billing, common, migrations, root build hoặc flags triển khai để hỗ trợ hai route.
11 acceptance cases kiểm policy exact path/method/role; full Gateway giữ regression JWT,
routing và forwarding. Không gộp chúng thành bằng chứng mới của actual paid refund E2E.

Static review hai test fixes không có actionable finding. Không chạy lại full reactor
6.854 tests hoặc actual six-service refund trong lượt 19/10; evidence local 18/10 là
lịch sử riêng theo source lock của nó. Tại thời điểm chốt evidence, chưa có remote CI/commit/PR mới từ lượt recheck.
`committed: false` trong source lock mô tả thời điểm kiểm thử; PR phát hành bộ evidence
và hai bản sửa này sau đó, không thay đổi nguồn đã kiểm thử.

Lộc có thể triển khai theo contract trên. Không thể bảo đảm sẽ không bao giờ có backend
fix mới; thay đổi breaking contract sau mốc này phải có handoff producer/consumer và
acceptance tương ứng. Không gọi trạng thái local chưa chia sẻ là master đã sửa xong.
