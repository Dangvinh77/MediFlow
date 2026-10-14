# Blueprint backend tối giản

Nguồn quy tắc: [coding](README.md), [architecture](../plans/01-system-design.md),
[contracts](../plans/03-contracts.md). Không scaffold Surgery/Inpatient hay platform mới cho MVP.

    com.mediflow.<context>/
      domain/model, domain/exception
      application/port/in, application/port/out
      application/dto, application/event, application/mapper, application/service
      web/                         # controllers/advice, driving adapter
      messaging/consumer/          # driving adapter
      infrastructure/persistence, infrastructure/client, infrastructure/messaging
      infrastructure/config, infrastructure/security

Dependency inward, domain framework-free; application không infrastructure/JPA/Spring Data.
Published business event records ở application/event; adapter payload ngoài application.
In-/out-ports và immutable DTOs, persistence entity/mapper riêng. Constructor injection.
Core/Support plain module libraries giữ cùng layout; Boot host chỉ explicit composition/config.
Không broad scan cả repo, không BOOT-INF dependency JAR, không duplicate catch-all security/advice.

Dùng dependencies/versions root POM hiện có; common dependency-free. DTO/PageResult/ApiResponse
mirror live wire. UUID/BigDecimal/Instant. Critical aggregate/outbox và inbox/effect atomic.
Flyway append-only từng context; defaults fail-closed, không fictitious migration baseline.

Scaffold mới phải được giao root POM/host path scope và áp dụng skill tương ứng. Done khi
packaged host chạy, correct data/security wiring, endpoint/.http/fixtures và DB/broker tests.
