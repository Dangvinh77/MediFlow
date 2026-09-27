# Care-finance V2 implementation-spec set plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish one isolated TARGET implementation specification for every MediFlow bounded context affected by the care-finance redesign while retaining the existing implementation specs as CURRENT compatibility contracts.

**Architecture:** `docs/eproject_general_plan/backend-spec/*.md` continues to describe deployed behavior. `backend-spec/care-finance-v2/*.md` defines additive migrations, exact APIs/events, idempotency, security and tests for the target rollout. Shared identifiers and event semantics come only from the approved care-finance handoffs.

**Tech Stack:** Markdown, PostgreSQL/Flyway DDL, Spring Boot port/DTO signatures, RabbitMQ event contracts, Spring Security role matrices.

---

### Task 1: Establish the V2 index and maturity rules

**Files:**
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/README.md`

- [x] Record CURRENT/TARGET precedence and implementation gates.
- [x] Index all eleven target service specifications and their owners.
- [x] State that an unavailable producer fixture blocks enablement, not additive local scaffolding.

### Task 2: Specify Hoàng Anh contexts

**Files:**
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/01-organization.md`
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/02-patient.md`
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/09-gateway.md`

- [x] Lock service-only identity lookup DTOs, authentication and absence/outage behavior.
- [x] Lock Gateway routes and role matrices for Inpatient and Surgery.
- [x] Preserve explicit JWT identity claims and correlation propagation.

### Task 3: Specify Huy contexts

**Files:**
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/05-pharmacy.md`
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/08-report.md`
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md`

- [x] Separate outpatient dispense clearance from admission medication charging.
- [x] Separate financial cash, liability, revenue, refund and receivable projections.
- [x] Define Surgery storage, readiness guards, event contracts and unresolved implementation gate.

### Task 4: Specify Lộc contexts

**Files:**
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/06-billing.md`
- Create: `docs/eproject_general_plan/backend-spec/care-finance-v2/07-notification.md`

- [x] Define the account/charge/transaction/allocation/settlement ledger and migration boundary.
- [x] Define purpose-specific clearance, deposit, refund and settlement events.
- [x] Define notification templates, privacy rules and idempotent delivery intents.

### Task 5: Cross-contract verification

- [x] Verify every event producer and consumer uses the same target ID and episode identity.
- [x] Verify every money amount uses `BigDecimal`/`NUMERIC(19,2)` and every ID uses UUID.
- [x] Verify no target spec permits cross-service database access or identifier inference.
- [x] Verify no placeholders (`TBD`, `TODO`) or AI/co-author trailers exist.
- [ ] Commit focused documentation changes and open a PR against `master`.
