# organization-service

Departments, staff and accounts — the organisational backbone the rest of the system references.

Reference: [`docs/ai/services/organization.md`](../docs/ai/services/organization.md) · design doc [`EProject/organization-service.html`](../EProject/organization-service.html).

- **Port:** 8089 · **Base path:** `/api/v1/org` · **DB:** `mediflow_organization` (PostgreSQL)
- **Owns tables:** `KHOA` (departments), `NHAN_VIEN` (staff), `TAI_KHOAN` (accounts)
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`.

## Why it exists

It owns the three things every other service points at:

1. **Departments** — `KHOA`, and the `ma_khoa` carried by every operational table and every domain event. This is the dimension that makes the system *departmental* rather than merely modular.
2. **Staff** — `NHAN_VIEN` is the `ma_bac_si` referenced from `LICH_HEN`, `HO_SO_BA` and `BAN_KE_CP`, three bounded contexts that must not own it themselves.
3. **Accounts** — `TAI_KHOAN` is what the gateway checks before issuing a JWT. The gateway holds no user data of its own.

## Status

**Skeleton only.** Module, dependencies, config and the mandated package layout are in place — no business code yet. Build it against the *Definition of Done* in the blueprint and the rules in the service doc.

## Run locally

1. `CREATE DATABASE mediflow_organization;` (or `docker compose up -d`, which creates it)
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ on localhost:5672.

```bash
mvn -pl organization-service -am spring-boot:run
```

Swagger UI: http://localhost:8089/swagger-ui.html

## Events

- **Publish:** `department.created`, `staff.created`, `staff.department.changed`
- **Subscribe:** none — this is reference data; it drives other contexts rather than reacting to them.

## Two integration points to get right

- **`POST /api/v1/org/accounts/verify`** — the gateway posts credentials here and mints the JWT from the result. The gateway must **never** read `TAI_KHOAN` directly; that would be cross-service DB access.
- **`GET /api/v1/org/staff/{id}/exists`** — `appointment` and `medical-record` call this to validate `ma_bac_si`, resiliently (timeout + circuit breaker + fallback), exactly as they validate patients.

## Tests

```bash
mvn -pl organization-service test        # unit (domain + application, no Spring)
mvn -pl organization-service verify      # + integration (Testcontainers, needs Docker)
```
