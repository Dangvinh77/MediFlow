# Checkpoint và phân công lịch sử

Đây là nguồn evidence theo ngày, không là backlog hiện hành. Trạng thái OPEN/ACTIVE và owner
trong phần nguyên bản là snapshot. Phần việc còn cần xác minh được giữ ở [registry](../README.md).
Không coi di chuyển tài liệu là đóng acceptance hoặc xóa lỗi.

| Hồ sơ | Nội dung giữ lại |
|---|---|
| [Huy checkpoints](2026-10-22-huy-mvp-checkpoints.md) | Pharmacy/Report/Support, runtime và các giới hạn qua nhiều lượt |
| [Billing/Notification checkpoints](2026-10-18-billing-notification-checkpoints.md) | Discovery/publication và nguồn evidence; owner backend hiện tại là Vinh |
| [Clinical/Lab checkpoints](2026-10-18-clinical-lab-checkpoints.md) | Owned acceptance và distributed recovery |
| [Phân công 18/10](2026-10-18-backend-assignments.md) | Bảng giao việc tại thời điểm trước fix |
| [Test harness 19/10](2026-10-19-test-harness-findings.md) | Clock/publisher findings và targeted proof; không kết luận bug hiện tại |
| [Billing clearance 16/10](2026-10-16-billing-clearance-checkpoints.md) | Tiến trình L1–L4; không yêu cầu viết lại từ checklist cũ |
| [Registry trước gom](2026-10-26-registry-before-consolidation.md) | Giữ nguyên nội dung các checkpoint registry trước chuẩn hóa |

Không chuyển các hồ sơ này thành task implementation mới. Tra commit, fixture, test commands và
scope trước khi sử dụng số liệu; fresh acceptance dùng source/artifact hiện tại.
