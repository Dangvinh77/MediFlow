# Backend audit verification and owned fixes — 2026-10-20

> For agentic workers: use test-driven development and review each implementation before completion.

**Goal:** Recheck all backend modules with Docker, correct Vinh-owned confirmed defects, and hand off foreign production defects with reproducible evidence.

**Architecture:** Keep all service boundaries, wire payloads, feature activation gates and released migrations. Billing/Notification require external JWT keys. Lab retains failed outbox events with bounded retries/backoff and explicit quarantine so poison rows cannot starve healthy events. Core production is read-only; Vinh may add verification tests and hand off failures.

**Tech Stack:** Java 21, Maven, Spring Boot, PostgreSQL 16, RabbitMQ, JUnit/Testcontainers.

## Verification baseline

- [ ] Run `mvn -B -ntp -fae verify` on untouched original checkout with Docker and inspect all reports; do not treat conditional skips as pass.
- [ ] Check existing open audit handoffs against live source; reproduce the department/staff concurrency candidate using isolated PostgreSQL.
- [ ] Run selected packaged process-recovery and integrated outpatient acceptance on freshly built artifacts after fixes; record exact prerequisites and omissions.

## JWT configuration regression and fix (Vinh)

Files: `backend/billing-service/src/main/resources/application.yml`, `backend/notification-service/src/main/resources/application.yml`, and module-local `src/test/java/.../infrastructure/security` configuration regression tests.

- [ ] Add an application-configuration test that loads each real YAML through Spring config and creates the actual JWT properties/filter; absent environment secret must fail startup, an externally supplied valid secret must succeed. Run it before changing production and retain failure output.
- [ ] Change only `${MEDIFLOW_JWT_SECRET:dev-only-change-me-please-32bytes-minimum-secret-key}` to `${MEDIFLOW_JWT_SECRET}` in each module.
- [ ] Rerun the regression plus actual filter/security suites, including Support co-host security with externally provided credentials.

## Lab outbox progress and bounded retry (Vinh)

Files: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/messaging/LabOutboxDispatcher.java`, `.../persistence/jpaEntity/LabOutboxEventJpaEntity.java`, `.../persistence/repository/LabOutboxEventJpaRepository.java`, new append-only migration `V4__outbox_retry_quarantine.sql`, `application.yml`, and corresponding unit/real PostgreSQL/Rabbit tests.

- [ ] First add a real PostgreSQL/Rabbit regression: insert 100 older unroutable outbox events plus one later routable event, use the real dispatcher, and require later progress with original IDs/payloads retained. Run and record the failure.
- [ ] Add nullable `next_attempt_at`, `quarantined_at`, bounded non-PII `last_failure_code`; add pending selection index and filter quarantined/not-yet-due rows. Preserve old rows and migration bytes.
- [ ] Keep current row-lock transaction and publisher confirm/return checks. On failure increment retries, schedule exponential bounded backoff, and quarantine at configurable maximum attempts (default five); never mark failed rows published. Retain original ID/body/time and expose the recovery procedure in Lab README.
- [ ] Add unit assertions for retry scheduling, timeout/NACK/return classification, terminal quarantine and successful retry; add PG upgrade preservation and restart/progress checks. Recovery must require an operator action after cause repair and must never reset published rows.
- [ ] Run full Lab tests, process-recovery and packaged paid-Lab slice with fresh Billing artifact.

## Foreign defects and review

- [ ] Register Core invalid department-head title and non-durable Patient events for Hoàng Anh in `docs/handoffs/README.md`; include exact paths, trigger, reproduction, expected state, owner, contracts and acceptance tests.
- [ ] If department/staff race is reproduced, promote existing HA-AUDIT-03 to a confirmed owner task with the red test and evidence. Keep Core production untouched.
- [ ] Hand off any newly confirmed Pharmacy/Report/Support production issue to Huy. Fix verification harness defects under Vinh's explicit test authority only.
- [ ] Review implementation for spec compliance and code quality; run final full backend verify after all changes. Save fresh commands/counts/images/source and limitations in a durable acceptance bundle. No production deployment, automatic feature activation, queue purge or data deletion.
