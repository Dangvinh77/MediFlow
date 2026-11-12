# Frontend ownership

Canonical scope không đổi theo việc gom deployments. Đọc root/frontend/src/nearest AGENTS,
[frontend blueprint](12-frontend.md), workboard và live backend contracts trước source edits.

| Owner | Features | Routes |
|---|---|---|
| Vinh | appointment, medical-record, lab, billing, notification; inpatient deferred | /appointments, /records, /lab, /billing, /notifications; /inpatient deferred |
| Huy | pharmacy, report; surgery deferred | /pharmacy, /reports; /surgery deferred |
| Hoàng Anh | organization, patient; Gateway-facing identity liaison | /organization, /patients |

Phân công trực tiếp ngày 27/10/2026: Vinh phụ trách toàn bộ backend và quản lý phần frontend
Billing/Notification trước đây của Lộc. Huy và Hoàng Anh tiếp tục frontend theo service trong
bảng; việc backend đổi owner không chuyển các feature frontend của hai người cho Vinh.

**Cập nhật 12/11/2026 (theo phân công trực tiếp của người dùng):** Vinh (`Dangvinh77` / `Harori`)
nhận toàn bộ dự án để hoàn thiện buổi trình bày, gồm cả frontend Organization/Patient/Pharmacy/Report.
Bảng trên giữ làm lịch sử phân công; Vinh sửa trực tiếp mọi feature và không cần HANDOFF cho
Huy/Hoàng Anh. Ranh giới kỹ thuật (Gateway only, `lib/api.ts`, DTO đúng wire, không import chéo
feature) vẫn áp dụng.

Matching paths: frontend/src/features/<feature> và frontend/src/app/(dashboard)/<route>.
Source-local rules có thể thu hẹp, không mở rộng canonical scope. Core/Support co-host không
gộp FE feature folder hoặc cho phép import chéo feature.

Vinh coordinates/default write authority cho frontend shared paths: root configuration,
package/lock/Next/Tailwind/TS/lint/test config, app/dashboard shells/loading/styles/login,
src/components/**, src/lib/**, src/test/** và auth/session. Không quyền default với feature owner khác.
Login/session không exclusive Hoàng Anh; owner khác chỉ sửa exact shared path khi human giao.

IMPLEMENT khi own mọi target; DELEGATED khi có exact owner/developer/path/deliverable; VERIFY
CONTRACT read-only khi kiểm live DTO/role; HANDOFF/GAP khi thiếu producer/permission; BLOCKED
khi phải đoán hoặc vượt scope. Branch/coordinator/subagent không authorization.

All HTTP lib/api.ts → Gateway, feature DTO exact wire. Shared extraction đúng scope.
FE-specific blockers ở frontend/docs/handoffs; backend contract gaps theo docs/05 plan,
không duplicate hai registry. Model routing/tool selection theo developer/user settings;
không tự đổi model hoặc spawn agents vì cần nhiều source paths.
