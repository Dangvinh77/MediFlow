# HANDOFF H-13 — Màn ADMIN cấp quyền phát hành kết quả xét nghiệm (11/11/2026)

**Trạng thái:** ACTIVE CONTRACT GAP
**Producer:** Vinh (Core/Organization backend, Lab review đợt 6)
**Người nhận:** Hoàng Anh (frontend Organization, theo [ma trận frontend](../ai/15-frontend-ownership.md))

## Bối cảnh

Từ đợt 6, Lab chỉ cho phát hành báo cáo khi người phát hành có năng lực `LAB_RESULT_RELEASE` do
Organization cấp, và người phát hành phải khác người nhập/gửi duyệt. Nếu thiếu năng lực, Lab trả
403 `LAB_RELEASE_DENIED` (fail-closed). Trước khi bật `mediflow.v2.lab-review` phải có ít nhất hai
LAB_TECH được cấp (xem [runbook](../plans/27-v2-cutover-runbook.md)). Hiện chưa có màn web để ADMIN
cấp/thu hồi; Vinh không sửa `features/organization` vì không thuộc phạm vi sở hữu.

## Hợp đồng backend đã có (Core, qua Gateway)

- `POST /api/v2/org/staff/{staffId}/capability-grants` (ADMIN) body
  `{capability:"LAB_RESULT_RELEASE", reason}` (`reason` bắt buộc, ≤500) → 201 `StaffCapabilityGrantDTO`
  (`grantId, staffId, capability, grantedBy, ...`).
- `PUT /api/v2/org/capability-grants/{grantId}/revocation` (ADMIN) body `{reason}` → 200 DTO.
- `GET /api/v1/org/me/capabilities` trả năng lực của chính người dùng (đã dùng ở `lib/capabilities`).

## Khoảng trống còn lại

1. **Frontend (Hoàng Anh):** màn ADMIN trong Organization để chọn nhân viên LAB_TECH, cấp và thu hồi
   `LAB_RESULT_RELEASE`, kèm lý do; hiển thị lỗi 4xx inline.
2. **Backend (Vinh):** chưa có endpoint liệt kê grant hiện hành theo nhân viên, nên UI chưa lấy được
   `grantId` để thu hồi. Cần thêm `GET /api/v2/org/staff/{staffId}/capability-grants` (ADMIN) trước
   khi làm phần thu hồi; phần cấp có thể làm ngay.

## Tiêu chí nghiệm thu

- ADMIN cấp quyền cho LAB_TECH A; A phát hành được báo cáo do B nhập (200), B tự phát hành bị 403 `LAB_SELF_RELEASE`.
- Thu hồi quyền của A; lần phát hành kế tiếp của A bị 403 `LAB_RELEASE_DENIED`.
- Vai trò khác ADMIN gọi hai endpoint cấp/thu hồi bị 403.
Sau khi cả hai bên đạt, xóa handoff này và chuyển quy tắc lâu dài vào `03-contracts.md`.
