# 09 — Testing

## Test pyramid (per service)

| Layer | Tool | Scope | Speed |
|-------|------|-------|-------|
| **Domain unit** | JUnit 5 + AssertJ | invariants inside the domain model — **no Spring, no mocks, no DB** | fastest; write these freely |
| **Application unit** | JUnit 5 + Mockito + AssertJ | use cases — mock the **out-ports** (`XxxRepositoryPort`, `XxxEventPublisherPort`) | fast, most tests here |
| Web slice | `@WebMvcTest` | controller ↔ DTO, validation, status codes, security roles (mock the in-port) | fast |
| Persistence slice | `@DataJpaTest` + Testcontainers | JPA entity mapping, queries, the persistence adapter | medium |
| Integration | `@SpringBootTest` + Testcontainers (DB + RabbitMQ) | request → DB → event published/consumed | slow, few but critical |

The top two rows are what clean architecture buys you: because `domain` and `application` import no
framework, their tests start in milliseconds and need no Spring context. If you find yourself needing
`@SpringBootTest` to test a business rule, the rule is in the wrong layer.

Mock **ports, not adapters.** A use-case test that mocks `PatientJpaRepository` is testing the wrong
seam — it should mock `PatientRepositoryPort` and never know JPA exists.

## Rules

1. **Every business rule from a design doc has a test.** Example (appointment): "một bệnh nhân không có >1 lịch hẹn CHUA_DEN cùng ngày" → a test that asserts the second create fails.
2. **Test naming:** `methodName_condition_expectedResult`, e.g. `createPatient_duplicateCmnd_throwsDuplicateResource`.
3. **Arrange–Act–Assert**, one behavior per test. AssertJ for fluent assertions.
4. **No shared mutable state between tests.** Each integration test gets a clean container / rolled-back transaction.
5. **Events:** integration tests assert the right event is published (and consumers are idempotent — feed the same event twice, assert single effect).
6. **Security:** slice tests assert role enforcement (`@WithMockUser(roles=...)`) — an unauthorized role gets 403.
7. **Do not mock what you don't own** in integration tests — use Testcontainers for real Postgres/MySQL and RabbitMQ.

## Coverage bar

- `domain/` and `application/` (the business logic) are the priority — aim for high coverage there.
- Controllers, mappers, adapters: covered by slice tests.
- Do not chase 100%; chase **every business rule and every failure path** being exercised.

## Running

```bash
# all modules
mvn test
# one service
mvn -pl patient-service test
# integration only
mvn -pl patient-service verify
```

## TDD

For non-trivial business rules, write the failing test first (red → green → refactor). This project ships with a `test-engineer` agent (`.claude/agents/`) — use it for rule-heavy services (billing saga, pharmacy stock, appointment constraints).
