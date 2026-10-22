# Mobile Pharmacy — MP-HUY-08/10, 2026-10-22

Candidate: working tree trên nhánh Huy, base `d5373058436463d5640a36423c66620103967b4c`.
Không tạo nhánh/commit/push. Source hashes và kết quả máy đọc ở [verification.json](verification.json).
Checkpoint Rx trước đó giữ nguyên làm lịch sử, không dùng hash cũ cho candidate mới.

## Phần thay đổi

- Task08: catalog totalPages0 hiển thị rỗng; lỗi field Java `lines[0].quantity` và dotted paths
  hiển thị đúng dòng. Khi chủ động sửa draft bị từ chối, field lỗi nhận focus sau khi mở khóa.
  Retry network/429 và đóng confirmation giữ cùng frozen intent/key/body; không tạo authority ID.
- Task10: eligibility theo mode tường minh, ADMIN/PHARMACIST và ACTIVE/PENDING; V1 không dựa
  boolean legacy, V0 vẫn yêu cầu paid. Xác nhận trước lệnh và khóa đồng bộ chống gửi đôi/hủy song song.
  Domain capability inject từ composition; không import concrete data adapter trong presentation.
- DispenseDTO giữ trường actor type hiện hành, nullable và enum chính xác; receipt phải khớp
  Rx, trạng thái terminal và dữ liệu cần thiết. FAILED có lý do riêng, không gọi là xuất thành công.
  Phiếu máy chủ vẫn hiển thị khi GET sau lệnh lỗi. Không optimistic stock/payment.
- Unpaid/clearance expired/revoked giữ denial/correlation; known rejection cần fresh GET và
  confirmation mới. Network/timeout/401/403/404/conflict/5xx/ambiguous response giữ khóa đối soát,
  không tự retry, không xem GET ACTIVE là bằng chứng lệnh trước chưa chạy. Session change đóng
  dialog và bỏ late result. Mode/capability identity cũng reset feature subtree.

## Kiểm tra local

Standard `mobile/tool/check.ps1`: **PASS exit0**.

| Kiểm tra | Kết quả |
|---|---|
| Dependencies | PASS, không đổi package/lockfile |
| Format | 103 files,0 changed |
| Analyze | No issues |
| Full app + loader | 284/284 PASS; thêm50 tests so checkpoint Rx234 |
| Web release | PASS; thời gian trong verification.json |
| Wasm dry run | PASS |
| Frame/source | presentation → domain ← data; shared ApiClient, không HTTP client/theme/app riêng |
| Widget layout | 320px,font scale2,Sáng/Tối; receipt/dialog/focus không overflow trong tests |
| Actual Gateway/browser/device/native | NOT RUN |

Warning font CupertinoIcons từ cấu hình hiện có được giữ trong raw log, không thêm shared asset
hoặc package. Raw log local `.git/mobile-pharmacy-dispense-check.log`, hash lưu trong verification.
Đã sửa lint braces, test helper lazy-list lookup và cập nhật kỳ vọng cũ “V1 luôn ẩn nút xuất” thành
“V1 hiện nút nhưng disabled khi thiếu capability”; chạy lại toàn bộ checks sau các sửa này.
Không nới validation backend, không bỏ assertion về quyền/trạng thái hoặc shared dependency.

## Tiến độ và giới hạn

**8/12 DONE local**:01–07,09. **08/10 PARTIAL**: phần feature/domain/widget đã có; chưa có
POST Idempotency-Key hoặc bodyless PUT transport và real writer/dispenser adapters. Hai provider
mặc định null, nút command khóa. Người dùng đã chọn giữ shared network thành dependency;
không gửi header giả bằng JSON/query hoặc `{}` để thay bodyless. Shared root DI/routes/mode/session
ở11 và actual signed Gateway/native acceptance ở12 chưa hoàn tất.

Đối chiếu contract từ PrescriptionController, DispenseDTO/DispensedActorType,
OutpatientPrescriptionLifecycleApplicationService và CareDispenseTransactionService của Pharmacy.
Tests dùng doubles chỉ chứng minh client behavior; không chứng minh clearance thật, quyền actor
thật, stock giảm đúng một lần hoặc phiếu xuất duy nhất. Không gọi build/widget là browser acceptance.
Không sửa app/core/platform/backend/FE/gates. Dependency chỉ cập nhật trong
[handoff chung](../../../../../docs/handoffs/HANDOFF-HUY-MVP.md).

Verdict: **LOCAL FEATURE VERIFICATION PASS; COMMAND TRANSPORT/INTEGRATED ACCEPTANCE PENDING**.
