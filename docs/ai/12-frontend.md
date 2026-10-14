# Blueprint frontend tối giản

Canonical rules ở [coding](README.md) và [ownership](15-frontend-ownership.md).
Đọc frontend/AGENTS, frontend/src/AGENTS và nearest rule trước sửa source.
Frontend/docs/workboard và feature guides còn giữ; source DTO/controller là wire truth.

    src/app/                       # routing/composition
    src/features/<logical-context>/
      api.ts
      types.ts
      components/
    src/components/                # feature-agnostic shared UI
    src/lib/                       # API/auth/envelope/shared utilities

Feature folders không đổi theo Core/Support deployments. Không cross-feature imports;
backend domain/application/infrastructure blueprint không áp cho TypeScript.
Next/Tailwind/TypeScript/pnpm versions lấy manifest/lockfile hiện có.
All HTTP gateway-only /api/* qua lib/api.ts; no raw fetch/service ports.
DTO exact wire, shared envelopes lib/types.ts. UI role hiding không là security.
Tailwind utilities và semantic theme tokens trong globals.css/theme source; không hard-coded
brand palette mới, component library/CSS-in-JS/data-fetch library ngoài rule hiện có.
Giữ mẫu Huy đã chốt ở frontend/docs/designs, không redesign vì giảm backend scope.

Loading/error/empty/retry/validation/permission states; abort/ignore stale requests, không
giả dữ liệu thiếu bằng zero. Test component/API/query + typecheck/lint + browser smoke.
MVP FE tập trung patient/appointments/records/Lab/Pharmacy/Billing/Report/in-app. Surgery/
Inpatient có code/docs nhưng deferred; thay navigation cần shared scope, không xóa feature.
