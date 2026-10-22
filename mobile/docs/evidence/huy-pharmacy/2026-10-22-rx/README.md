# Pharmacy mobile — checkpoint Rx 07/08/09, 2026-10-22

Candidate: working tree nhánh **Huy**, base `d5373058436463d5640a36423c66620103967b4c`.
Không commit/push hoặc tạo nhánh. [Verification](verification.json) ghi source hashes, counts và
frame audit. Evidence04–06 ở thư mục2026-10-22-local giữ nguyên, không là hash candidate mới.

**8/12 local**:01–07 và09 DONE;08 PARTIAL. Task07 đóng phần quy trình nguồn và form nhập đã xác
minh, không tự tạo producer Clinical/Billing. Public episode cho visit chỉ kê thuốc và catalog
selection tổng quát còn dependency; giữ thiếu đầu vào thành lỗi. Workflow public trước đây đã ký
ở [FE acceptance](../../../../../frontend/docs/evidence/huy-six-service/2026-10-19/README.md),
không tính nó là mobile acceptance mới. `prescription-context` là SYSTEM-only; mobile không gọi.

Task08 đã có form nguồn/catalog/lines, exact V1 mapping, immutable intent và same-key retry.
**Chưa có POST adapter V1 thật:** người dùng chọn giữ shared network thành dependency. Writer
provider null khóa gửi; tests inject writer doubles trong test scope. Không fake header bằng JSON,
không suy care mode từ GET, không fallback V0. Không lưu PII/intent qua process restart.

Task09 có Gateway PUT reason, strict cancelled result/ID, role/state/confirmation/pending guards,
unknown không resend, giữ success receipt nếu read lỗi và bỏ response cũ khi đổi ID/session.
Backend vẫn quyết định doctor ownership/authority. Test403 không thay actual foreign-doctor proof.

| Kiểm tra candidate cuối | Kết quả |
|---|---|
| Standard `mobile/tool/check.ps1` | PASS, exit0 |
| Dependencies | PASS, không đổi package/lockfile |
| Format |100 files,0 changed |
| Analyzer | No issues |
| Full app + mediflow_loader |234/234 PASS; thêm48 test trong hai file Rx |
| Web release build | PASS77,5s; Wasm dry run PASS |
| Static frame audit | Inward imports/Gateway-only/theme inheritance; app/core/platform/package không sửa |
| Widget viewport/theme |320px, font scale2, light/dark; no exception/overflow |
| Actual Gateway/browser | NOT RUN; shared app DI/session/mode/routes còn11 |
| Android/iOS/Windows native | NOT RUN |

Raw log local: `.git/mobile-pharmacy-rx-check.log`. Các lượt đầu có lỗi lint và helper transition/
scroll; đã sửa và full check chạy lại đạt. Full log cuối là authority cho counts; focused log trước
đó không được gọi PASS. Build còn warning CupertinoIcons từ cấu hình gốc, không sửa shared assets
ngoài scope. Browser/device/pixel comparison với ảnh FE chưa chạy; widget tests không thay thế.

Khung giữ Flutter/Riverpod/Material3 hiện có, theme app (light/dark seed hiện hành), presentation
→ domain ← data, shared ApiClient duy nhất. Không đổi shell/sidebar/drawer/router hoặc tạo app mới;
navigation nội bộ tương tự task04–06. Route app chưa bind Pharmacy nên build pass không chứng minh
đã mở được workflow trong app chính. Không bật backend gates, mở deferred scope hoặc sửa FE.

Dependency chỉ ở [handoff chung Huy](../../../../../docs/handoffs/HANDOFF-HUY-MVP.md): header POST
và bodyless PUT của shared owner; Huy nối writer adapter sau header; shared composition và actual
acceptance theo plan. Không tạo thêm handoff.
