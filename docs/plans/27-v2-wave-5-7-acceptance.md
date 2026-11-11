# Nghiệm thu Đợt 5–7 (Lab danh mục, duyệt, mẫu, đính chính, nguy cấp) — 12/11/2026

Kết quả chạy trên nhánh `develop/mediflow-v2.0` tại commit `26201b02`. Đây là bằng chứng test tự động;
**chưa** chạy nghiệm thu đóng gói qua Gateway với cờ bật (xem mục cuối).

## Test tự động

| Phạm vi | Lệnh | Kết quả |
|---|---|---|
| Backend toàn bộ | `mvn -o -fae test` (trước lượt sửa) | Gateway 2223, Core 527, Clinical 1120, Lab 423, Pharmacy 898, Notification 323, Report 1467 đạt; Billing 1 lỗi và Support 1 lỗi, đã sửa ở `26201b02` |
| Lab sau lượt sửa | `mvn -o test` trong `backend/lab-service` | 423/423 đạt |
| Billing/Support sau lượt sửa | `LabTestChargeConfigurationTest`, `SupportStoreIsolationTest` | 5/5 và 5/5 đạt |
| Web | `pnpm test --run`, `tsc --noEmit`, `pnpm lint` | 1616/1617 (1 test focus của appointment chập chờn, chạy riêng đạt); tsc 0 lỗi; lint 0 lỗi |
| Mobile | `flutter analyze --no-pub`, `flutter test --no-pub` | 3 info có sẵn; 778/778 đạt |

## Review và lượt sửa

Review toàn nhánh (code-reviewer) tìm 1 Critical, 4 Important; tất cả đã sửa trong `4f6a72e3`, mỗi lỗi có test đỏ→xanh:
tên dịch vụ Organization (core-service), độ rộng `lab_result.value` (V11), chặn v1 cho xét nghiệm đã gắn template,
ghi nhận mọi người lưu nháp, thứ tự khóa khi phát hành, và bác sĩ/điều dưỡng chỉ đọc bản đã phát hành.
Còn lại (nhỏ): `source_at` khi bản đính chính đến trước bản gốc ở Clinical; trùng `fieldCode` trong một nháp trả 500;
thu mẫu không kiểm trạng thái xét nghiệm.

## Chưa làm

- Nghiệm thu đóng gói qua Gateway theo thứ tự bật cờ của [runbook](27-v2-cutover-runbook.md).
- Màn ADMIN cấp quyền phát hành ([H-13](../handoffs/HANDOFF-HA-LAB-RELEASE-CAPABILITY-UI-2026-11-11.md)).
