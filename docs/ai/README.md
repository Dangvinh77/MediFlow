# Quy tắc coding MediFlow

Định vị sản phẩm: **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Phạm vi triển khai đồ án là ngoại trú cho phòng khám đa khoa hoặc khoa khám bệnh của bệnh viện; nội trú/phẫu thuật hoãn. [Thiết kế hệ thống](../plans/01-system-design.md) phân biệt source hiện tại, sáu business deployments đích và tiêu chí nghiệm thu.

Bộ quy tắc viết lại ngày 2026-10-14 cho [thiết kế tối giản](../README.md).
Chỉ giữ quy tắc cần cho correctness, bảo mật, phối hợp owner và nghiệm thu phân tán.
Các entry files/skill cũ có thể trỏ qua tài liệu ngắn trong thư mục này; không dùng spec cũ
để scaffold thêm nghiệp vụ ngoài MVP.

## Ownership và scope

Theo phân công trực tiếp ngày 27/10/2026, Vinh phụ trách toàn bộ backend: Core, Gateway,
Clinical, Lab, Pharmacy, Billing, Report, Notification và Support; Inpatient/Surgery vẫn deferred.
Các owner backend Huy/Hoàng Anh trong checkpoint cũ là lịch sử, không chặn Vinh sửa production.
Frontend chia theo service ở [15](15-frontend-ownership.md); Billing/Notification của Lộc do Vinh quản lý.
Chung owner không gộp data authority, transaction, persistence hoặc quyền của các context.
Common/Eureka thuộc backend Vinh; thay đổi phải kiểm ảnh hưởng consumer. Root build/CI/scripts/
Compose là shared, chỉ sửa khi được giao rõ.
Branch name/reviewer/coordinator không tự cấp quyền sửa module khác. Subagents cũng vậy.

Frontend ownership và shared scope ở [15](15-frontend-ownership.md); source-local AGENTS phải đọc.
Production changes không vượt scope. Foreign modules được inspect read-only để xác nhận contracts.
Gap MVP ghi owner/producer/consumer/fixture/test trong [plan](../plans/05-implementation-plan.md).
Hoãn Surgery/Inpatient không cho phép xóa code/data, relax guards hoặc fabricate authority.

## Backend blueprint

Infrastructure/web/messaging → application → domain; dependencies inward.
Domain pure Java, không Spring/JPA/AMQP/Jackson; application không infrastructure/Spring Data/JPA/HTTP.
Cho phép @Service/@Transactional, MapStruct annotations và DTO validation theo blueprint.
In-ports/out-ports ở application; framework adapters ngoài domain. Constructor injection.
DTO records cross boundary, entity không làm response. common chỉ generic, không business domain.

Mỗi context giữ data authority. Không cross-context SQL/JPA/repository/domain entity coupling.
Core/Support là ngoại lệ deployment được thiết kế rõ, không ngoại lệ quyền DB hay business rules.
Mỗi module có đúng datasource/Flyway/EMF/transaction manager. Không bean overriding che collision.
Host security/config/advice explicit scoped. Không schema consolidation/refactor domain hàng loạt.

Money BigDecimal; UUID IDs; Instant/LocalDate cho thời gian. Transaction critical ghi aggregate/
receipt/outbox cùng nhau; consumer claim và effect atomic. Giữ REQUIRES_NEW và unit-of-work
semantics của code hiện có. Không distributed @Transactional/XA.

## Naming và API

Class/routes English. Wire mirror live source, không đổi field names trong cùng refactor.
Pharmacy/Report/Surgery Java/JSON English camelCase và SQL English snake_case; convention context
khác giữ source hiện tại. Released migrations append-only, không edit/renumber/repair checksum.
DTO request validation; ApiResponse/error.code/PageResult giữ contract và correlation.
Mỗi endpoint shipped có role declarations và matching .http request. Breaking wire change
version riêng, producer + consumer fixtures/tests cùng update.

## Messaging

State change có domain fact nếu contexts khác cần. Synchronous REST chỉ cho câu trả lời cần
ngay; timeout/bounded retries/circuit breaker, confirmed absence khác unavailable.
RabbitMQ durable exchange/queue/DLQ. Critical writes dùng durable outbox; ACK/confirm không
là exactly-once. Dedupe event + logical operation, same key/different bytes reject.
Original IDs/bytes giữ khi retry/restart. Poison bounded retry rồi DLQ, không nuốt evidence.
HELD/quarantined không release vì host đổi tên. Không fake target IDs/price/history/authority.

## Security

JWT verify ở Gateway và downstream. Endpoint @PreAuthorize default deny theo exact role/
method/path, không OR all roles của co-host. PATIENT resource ownership kiểm trong use case.
SYSTEM chỉ service token; human access/service/refresh types không interchangeable.
Actor/role/staff/account từ signed credential, không body untrusted.
Secrets external; không log passwords/tokens/full PII. Service calls giữ subject/scope/correlation.
Clinical authority khác login role; absence/provider failure fail-closed.

## Frontend

Next.js App Router/TypeScript/Tailwind hiện tại, pnpm; versions lấy package/lockfile.
Feature-based folders theo logical context, không theo developer/deployment. Thin app routes.
Gateway same-origin /api/*, mọi HTTP qua src/lib/api.ts; không raw fetch trong component.
features/<ctx>/types.ts mirror live wire; shared envelopes ở lib/types.ts.
Không feature imports từ feature khác; shared extraction phải đúng scope.
Tailwind utilities/tokens/themes có sẵn; không component library/CSS-in-JS/new fetch library.
Loading/error/empty/retry/permission states rõ; stale request không overwrite current state.
Giữ UI Huy đã chốt; source FE đang sửa không phải scope xóa docs.

## Mobile

Flutter presentation → domain ← data, gateway-only qua api_client, repositories/use cases.
Domain pure Dart; DTO mirror exact wire, không áp một quy tắc Vietnamese lên mọi context.
JWT SecureStorage, không plain SharedPreferences. Dùng stack/dependencies hiện có.
Không mobile expansion bắt buộc để nghiệm thu backend; regression nếu đổi contract.

## Tests và release

Business invariants cần unit/use-case tests; mock ports, không mock persistence adapters để
chứng minh domain. Web slices cho validation/RBAC; PostgreSQL/RabbitMQ thật cho race/atomicity/
retry/DLQ/restart. Testcontainers Docker engine phải running; skipped không là passed.
Schema fresh/upgrade validate; giữ compatibility regression, test opt-in tách gates OFF.
Không weakened tests/permissive fallback để green CI. Evidence ghi commit/flags/test counts.

Conventional Commits, focused owner changes. Không tự push/merge từ task đọc/plan/docs.
Mỗi commit đúng một human author theo scripts/allowed-contributors.json; không đổi identity
để impersonate, không Co-Authored-By/AI/session trailers. Giữ hooks/required checks.
Trước code xem `git status --short`, `git log -5 --oneline`, rồi đọc [docs index](../README.md),
contracts, plan, nearest AGENTS và source/test liên quan trực tiếp.
