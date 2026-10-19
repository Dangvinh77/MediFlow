# Audit API và nghiệp vụ backend — 19/10/2026

## Source và kết luận phát hành

Follow-up Notification V1 sau PR #441: implementation raw identity/backfill và trạng thái
verification mới nằm trong [bundle riêng](../notification-v1-byte-identity-2026-10-19/README.md).
Inventory và red reproductions trong tài liệu audit này là lịch sử của revision bên dưới.

Base chung sau merge PR #439: `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Branch audit: `codex/backend-api-business-audit`, checkout riêng. Không đưa thay đổi chưa commit
của checkout làm việc chính vào audit. Theo phân công người dùng, Vinh tiếp quản Billing và
Notification từ Lộc, kiểm thử toàn backend; production Gateway/Core/Pharmacy/Report giữ owner.

**Chưa được chốt backend sạch.** Có regression mới tái hiện ngoài suite hiện có; suite mặc định
xanh không phủ định các lỗi đó. Các mục còn mở đăng ký ở [handoff registry](../../handoffs/README.md).
Không cam kết không bao giờ phát sinh backend fix; contract breaking phải có producer/consumer
handoff và acceptance trước thay đổi.

## Phát hiện và xử lý

| Mục | Kết luận có căn cứ | Xử lý |
|---|---|---|
| Billing credential boundary | Route allowlist kiểm access type/expiry/UUID bỏ sót invoice và refunds-due. 10 negative cases thực tế trả 200 trước sửa | Thay bằng boundary chung cho human routes; giữ service-token exact lookup. 14 boundary cases cùng filter/controller/security regression |
| Lab result authority | Bắt đầu khi clearance còn hạn rồi ghi kết quả lúc đã hết hạn vẫn thành công | Kiểm exact bound clearance sau aggregate lock, cùng action-time cho validation/event; giữ emergency/V0/terminal semantics |
| Notification JWT expiry | Cả signed human/service token thiếu exp vẫn tạo authentication | Thêm mandatory exp trước hai nhánh; hai regression đỏ trước sửa, filter/security/controller xanh sau sửa, chạy full module |
| Gateway/Core token expiry | Signed access token không có exp vượt Gateway authentication và tạo ROLE_ADMIN ở Core Organization | [Hoàng Anh sửa](../../handoffs/HANDOFF-HA-JWT-EXPIRATION-AND-CORE-AUDIT.md), hai regression đỏ lưu patch; Patient cùng thiếu sót từ source, cần regression riêng |
| Core verifier caller | Token Pharmacy service đúng TTL gọi được accounts/verify; regression expected 403, actual 200 và credential use case được gọi | Hoàng Anh sửa scope riêng route; regression patch trong cùng handoff HA |
| Core department/staff concurrency | Có interleaving check-then-write không lock/version | Candidate từ source, chưa PG concurrency proof; không tính như lỗi đã tái hiện |
| Notification V1 event identity | Whitespace khác với cùng eventId vẫn ACK duplicate vì normalized JSON hash | [Vinh xử lý cutover](../../handoffs/HANDOFF-VINH-NOTIFICATION-V1-BYTE-IDENTITY.md); không ship thay raw hash làm hỏng replay historical receipts |
| Pharmacy V1 UI | UI dùng paymentConfirmed legacy để chặn command V1 dù exact grant đã có | [Huy sửa FE](../../handoffs/HANDOFF-HUY-PHARMACY-V1-DISPENSE-UI.md); không đổi boolean backend để che mismatch |
| Pharmacy/Report expiry | Pharmacy ADMIN/PHARMACIST và Report outpatient ADMIN access token thiếu exp vẫn được authenticate; 3 negative cases đỏ, source test đã restore và baseline xanh | [Huy sửa JWT boundary](../../handoffs/HANDOFF-HUY-PHARMACY-REPORT-JWT-EXPIRATION.md), giữ API/wire |

## Phạm vi đã đọc và giới hạn

- Gateway/Core: exact method/path/role policies, refund routes, credential classes/headers,
  account verify, department/staff invariants. Hai route refund không có thay đổi contract.
- Clinical/Lab: assigned doctor/authority, lifecycle, check-in/start/result, clearance binding,
  expiry, emergency audit, transaction/outbox. Lab source-order uniqueness đã có partial unique
  index; không báo race duplicate từ application precheck khi DB đã có constraint.
- Billing: payment discovery/exact source, idempotency, allocation/refund budget, lock order,
  signed actor, terminal prescription adjustment, revocation/outbox. Refund event HELD không
  tự được xem là đã publish/projection đã xử lý.
- Pharmacy: V1 create/grant/dispense/cancel, stock/reservation, Billing recheck sau locks,
  terminal retry và unavailable fail-closed. Không xác nhận thêm production defect từ phần đã đọc.
- Report/Notification/Support: recipient ownership, report roles/coverage, earned/cash/refund
  distinction, retry/inbox/outbox/recovery, store/security separation. Report legacy normalized
  fingerprints và daily/monthly compatibility là chủ đích trong tài liệu, không tự sửa semantics.

Graph được dùng cho structural lookup, source/fixtures hiện hành đối chiếu lại khi metadata
khác revision hoặc SQL/docs ngoài coverage. Inpatient/Surgery deferred không được kích hoạt.
Không xem bootstrap/startup Lab trong six-service test là paid-Lab workflow acceptance.
Chưa có acceptance riêng cho EXAM/LAB refund-revocation propagation; không suy từ prescription
clearance lookup rằng hai context này có cùng contract revocation.

## Kiểm thử và review

Inventory cuối của suite mặc định: **5.734 tests, 0 failures, 0 errors, 0 skipped**.
Đây là inventory ghép broad run với các lượt module/class chạy lại sau sửa, không phải
5.734 ca chạy trong một invocation. Không cộng lại ca đã rerun hoặc explicit IT cũ.
Metadata từng class, thời điểm và SHA-256 của XML riêng nằm trong
[default-test-inventory.json](default-test-inventory.json); không publish system properties,
system-out hoặc dữ liệu trong raw logs.

| Module | Default tests xanh |
|---|---:|
| Gateway | 1.449 |
| Core | 183 |
| Clinical | 339 |
| Lab | 221 |
| Pharmacy | 838 |
| Billing | 1.074 |
| Notification | 281 |
| Report | 1.331 |
| Support | 18 |

Lệnh broad regression:

```powershell
mvn -q -pl backend/gateway,backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service -am test
```

Broad run exit 0, bắt đầu trước các Lab/Notification edits; hai module được chạy lại full
sau sửa, lần lượt 221 và 281 ca xanh. Billing thay đổi trước khi reactor tới module đó.
Fresh root `mvn -q -DskipTests install` exit 0 là build/install, không tính là test.
Support default chạy lại với classifier libraries mới: 18 ca xanh, exit 0.
Regression cố ý đỏ Gateway/Core/Pharmacy/Report/Notification được lưu dạng patch,
không để trong source suite của PR sửa module khác. Những XML bị targeted run ghi đè không
được cộng như full-run count; ghi stage/source riêng khi tổng hợp.

Independent review Billing/Support phát hiện hai negative JWT fixtures dùng subject không hợp
lệ, có thể làm test pass sai nguyên nhân. Đã đổi sang UUID hợp lệ để expiry/signature là điều
kiện sai duy nhất. Lab static review xác nhận exact bound ID, action time sau lock và giữ
audited override/V0/terminal guards; bổ sung paid-success/missing-projection application tests.

Packaged acceptance bổ sung **đã pass: 1 test, 0 failure/error/skip, 144,153 giây**, exit 0:

```powershell
mvn -q -pl backend/support-service -Psix-service-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

Chạy EXAM paid thật, rollback sau record insert, concurrent start,
Rx paid/dispense/restart và cancelled-paid Rx refund qua actual Gateway. Refund kiểm role/method/
path, hai refund tranh cùng budget, replay/conflicting actor/amount, signed actor/correlation,
remaining obligation bằng 0 và không đổi stock của đơn hủy. Hai refund 20.000 cùng tranh
khoản thu 30.000 trả 201/422; hoàn nốt 10.000 thì tổng hoàn đúng 30.000, obligation rỗng,
stock vẫn 20. Hai start khám trả 200/422 và chỉ một record. Refund outbox còn HELD theo
cấu hình test: không coi đây là evidence consumer đã nhận refund.

[verification-results.json](verification-results.json) khóa commands/exits, số liệu và các
regression còn mở. [source-lock.json](source-lock.json) khóa production commits, source test và
contract hashes; [artifact-manifest.json](artifact-manifest.json) khóa jars và private evidence.
Không commit raw logs/XML hoặc dữ liệu nghiệp vụ test. Chưa kiểm kê database môi trường đích,
chưa chạy migration historical receipt Notification; mock delivery không chứng minh email thật.

Reviewer độc lập: GO cho PR sửa phần owned và lưu handoff, **NO-GO cho tuyên bố toàn backend
đã đóng sạch**. Bốn regression patches đều được kiểm `git apply --check` trên source audit.
Các lỗi tái hiện ngoài owner vẫn mở dù baseline filter suites đã restore và xanh.
