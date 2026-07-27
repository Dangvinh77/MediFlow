# 03 — Coding Standards

## Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, English, singular domain | `com.mediflow.patient.domain` |
| Class | PascalCase, English | `PatientService`, `CreatePatientRequest` |
| Method / field | camelCase | `findByCmnd`, `maBenhNhan` |
| Constant | UPPER_SNAKE | `MAX_LOGIN_ATTEMPTS` |
| REST path | kebab/lowercase, plural noun, English | `/api/v1/medical-records` |
| DB table / column | Vietnamese UPPER/snake_case | `BENH_NHAN`, `ma_benh_nhan` |
| JSON / DTO field | Vietnamese camelCase | `maBenhNhan`, `hoTen` |
| Event class | English PascalCase + `Event` | `PatientCreatedEvent` |

Base package: `com.mediflow.<service>` (e.g. `com.mediflow.pharmacy`).

## Clean architecture (inside every service — see `04` for the full tree)

Three layers, dependencies pointing **inward only**:

```
infrastructure  ───►  application  ───►  domain
```

- `domain/` — pure Java. Business model + its invariants. **No Spring, no Jakarta Persistence, no I/O.**
- `application/` — use cases. Declares `port/in` (what it offers) and `port/out` (what it needs). Depends on `domain` only.
- `infrastructure/` — every adapter and every framework annotation: `web/`, `persistence/`, `messaging/`, `client/`, `security/`, `config/`.

**Never point outward.** When the application layer needs the outside world, it declares an interface (port) and infrastructure implements it (adapter). A `import jakarta.persistence` or `import org.springframework.data` under `domain/` or `application/` means the layering is broken — see `04` for the allowed exceptions.

Two models per aggregate, on purpose: a domain model (rules, no annotations) and a JPA entity (annotations, no rules), joined by a persistence mapper. Details and rationale in `04`.

## Rules that are not optional

1. **Constructor injection only.** No `@Autowired` on fields. Use `final` fields + Lombok `@RequiredArgsConstructor`.
2. **Controllers are thin.** Validate input, call one service method, map to response. No business logic, no repository calls.
3. **DTOs cross the boundary, entities never do.** Controllers accept/return DTOs. Entities stay inside the service/repository layer. Map with MapStruct.
4. **No business logic in entities or controllers.** It lives in the service layer.
5. **Fail with typed exceptions**, not `null` returns or generic `RuntimeException`. See error handling below.
6. **Money = `BigDecimal`. IDs = `UUID`. Dates = `LocalDate` / `LocalDateTime` / `Instant`.** Never `double`/`float` for money, never `String` for ids/dates.
7. **Validate at the edge** with Jakarta Bean Validation annotations on request DTOs (`@NotNull`, `@Email`, `@Pattern`, ...). Business-rule validation lives in the service.
8. **No `System.out.println`.** Use SLF4J (`private static final Logger log = ...` or Lombok `@Slf4j`). Log at the right level; never log secrets, tokens, or full patient PII.
9. **Immutability first.** Prefer `record` for DTOs and events. Prefer unmodifiable collections in returns.
10. **No magic values.** Enum for states (`TrangThaiLichHen`), constants for thresholds.

## Error handling

- Domain exceptions extend a shared base (`ResourceNotFoundException`, `BusinessRuleException`, `DuplicateResourceException`, ...).
- One `@RestControllerAdvice` (`GlobalExceptionHandler`) per service maps exceptions → HTTP status + the standard error envelope (`05-api-conventions.md`).
- Map: not-found → 404, validation/business-rule → 400/422, duplicate → 409, auth → 401/403, unexpected → 500 (never leak stack traces to the client).

## Comments & docs

- Comment *why*, not *what*. Match the density of surrounding code.
- Public service methods and non-obvious business rules get a short Javadoc referencing the rule (e.g. `// BR: một bệnh nhân không có >1 lịch hẹn CHUA_DEN cùng ngày`).
- Keep the Vietnamese domain terms in comments where they aid understanding.

## Formatting

- 4-space indent, LF line endings, UTF-8 (enforced by `.editorconfig`).
- Keep methods short; extract private helpers over deep nesting.
