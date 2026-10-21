# Backend business audit fixes B01–B06

User approved implementation after the source/test audit at `77944c39`, including task-scoped writes to Core, Gateway and Report. Existing migrations remain immutable; no deployment gates are activated and no production data is modified.

## Contract and implementation sequence

- [x] B01: serialize Billing care authorization against refunds; Clinical/Lab require exact current financial authority and fail closed on unavailable authority. Keep audited emergency overrides. User selected per-service full/partial approved refunds based on execution progress, with cancellation/adjustment evidence, immutable payment history and no reopening canceled orders.
- [x] B02/B03: Lab persists cancellation actor/reason/time with the aggregate and a `lab.request.cancelled` V1 outbox fact atomically. Billing retains early cancellation evidence, deduplicates deliveries/operations, cancels payable requests, voids charges and records paid refund obligations without fabricating cash movement.
- [x] B04: Core exposes service-only current account identity for Gateway refresh. Require expiration and current active identity; provider failure never mints tokens.
- [x] B05: persist deterministic acceptance ordering under the existing coverage lock, preserving historical coverage and original receipts. Test equal timestamps and concurrent acceptance.
- [x] B06: persist Organization events in its own transactional outbox and persist staff-create idempotency receipts. Verify retries, conflicting keys, broker outages and restart delivery.

## Source lock

Existing `financial.clearance.granted` V1 and `lab.request.created` V1 bytes are unchanged. New cancellation fields are additive to Lab response/audit; cancellation event fields are locked by matching producer/consumer fixtures. Existing human roles, V0 isolation and context-owned stores remain.

The read-only financial lookup is insufficient to serialize start with refund. New SYSTEM-only
execution-start commands use stable target operation IDs and the Billing account lock shared
with refund. A lost response or local rollback after successful Billing admission conservatively
retains the start receipt until audited cancellation/reconciliation; it never implies an automatic
cash refund. Exact new command and cancellation fields must be locked in producer/consumer tests.

Ordinary EXAM/LAB_TEST refunds require accepted, audited exact-source cancellation evidence
and the remaining refund obligation even when no execution-start receipt exists. Missing receipts
cannot prove non-execution for emergency overrides or pre-upgrade starts. Emergency care remains
independent of Billing availability; canceled local orders remain terminal.

User separately authorized the Organization frontend API/StaffForm change for B06, including
focused tests: retain an idempotency key across ambiguous retries of the same create intent.

## Verification

For each change: introduce a regression test, observe the expected failure, implement, then run the relevant suite. PostgreSQL/RabbitMQ tests establish ordering, atomicity and delivery behavior; unit tests establish validation and unavailable/denied behavior. After independent domains complete, review the combined diff and run affected module suites plus host/security/build checks. Record actual counts/skips and remaining limitations, without treating mocked evidence as full distributed acceptance.

### Completed verification, 2026-10-21

| Scope | Command / evidence | Result |
|---|---|---|
| Core | `mvn -q -pl backend/core-service -am test` | 213 Core tests, 0 failures/errors/skips; PostgreSQL/RabbitMQ; excludes the stale report for the removed direct-publisher test |
| Gateway | `mvn -q -pl backend/gateway test` plus `GatewayRefundRoutesAcceptanceIT` | 1,500 tests total, 0 failures/errors/skips |
| Organization frontend | focused API/StaffForm Vitest, `pnpm typecheck`, `pnpm lint` | 11 tests and both checks passed |
| Pharmacy lookup compatibility | `PharmacyBillingClearanceAdapterTest,PharmacyBillingClearanceHttpTest` | 20 tests passed |
| Report | prescribed full module run, followed by final-source migration/acceptance/upgrade/service rerun | Full: 1,443 tests, one stale expected migration-count assertion (25 vs 26), 0 errors/skips. Assertion corrected; final focused 21/21 passed. |
| Clinical | full module suite, then `ConsumerInboxPostgresTest,GlobalExceptionHandlerTest` against final fixtures | 357 tests in full run; two old schema-version assertions and one old overload mock fixed. Final affected classes passed; consolidated reports have 0 failures/errors/skips. |
| Lab | full module suite, then `ConsumerInboxPostgresTest` against final fixtures | 244 tests in full run; three old schema-version assertions fixed. Final affected class passed; consolidated reports have 0 failures/errors/skips. |
| Billing | `mvn -q -pl backend/billing-service -am test`, then final affected-class/method reruns | Full: 1,123 tests, 6 failures/11 errors. Final affected reruns: 86 tests passed, 0 failures/errors/skips, including PostgreSQL/RabbitMQ cancellation, refund and lookup cases. |
| All 13 reactor modules | `mvn -q -DskipTests package` | Passed, exit 0; test execution is covered separately above. |

The complete Clinical/Lab/Report suites were not repeated after their test-only fixture fixes;
all originally failing classes were rerun successfully. Report's final reports contain 1,444
test cases including the additional boundary-date regression. The final-source focused suites
also cover exact cancellation bytes, persistence/outbox rollback, lost-response start retries,
revoked/mismatched/unavailable authority and explicit audited emergency overrides.

Billing's full-run failures were migration-count/legacy-row assertions, isolated configuration
fixtures missing the new port, and one real lookup-response defect: an ineligible multi-target
grant still exposed a stale source ID. The lookup now emits source identity only for current
eligible grants; its affected cases passed after the correction. Errors were old mock fixtures
returning null for the new eligibility port and publication tests lacking audited cancellation
evidence under the new policy. These fixtures were corrected without weakening production
guards. A final publication-evidence helper SQL correction passed all four parameterized cases.
Billing's whole suite was not repeated after these corrections; every failing class/case and the
affected lookup/cancellation/start/refund behavior were rerun successfully.

Independent review reproduced the missing Gateway EXAM refunds-due GET permission (ADMIN and
CASHIER were rejected). Exact route/role/method/path regression now passes as part of the
full Gateway run. Financial review also found durable start retries rejected after 30 seconds
and a lossy legacy timestamp reconstruction; both were corrected with regression coverage.
The final independent financial review found no remaining Blocker/Should-fix issue after the
universal EXAM/LAB cancellation-evidence guard. Final affected tests, exact producer/consumer
fixture hashes, reactor packaging and `git diff --check` passed. Changes are prepared on
`codex/backend-business-audit-fixes` for review against `master`; deployment and gate activation
remain separate from this change.

### Historical and deployment boundaries

- No production deployment, feature-gate activation or historical data mutation has been performed.
  Module/contract/PG/Rabbit tests do not certify six live applications behind the Gateway.
- Report's V29 preserves the old reader's selection for existing equal-time rows. The original
  acceptance order cannot be reconstructed from timestamp ties; new accepts use serialized
  database order and preserve the original receipt on replay. If an existing tied legacy selection
  is stale, validate the current producer evidence and create a new approved acceptance through
  the normal API with a new request identity. Do not rewrite historical UUIDs or timestamps.
- Historical Lab cancellations without actor/reason/time cannot be given invented audit values.
  Reconcile them explicitly using the Lab runbook. Billing's legacy exact source snapshot gaps
  remain pending until an original matching producer fact restores evidence; do not infer Lab type
  or exact producer timestamp from another context's database or a rounded stored timestamp.
- Organization retry protection requires the same actor and idempotency key. StaffForm now keeps
  that key for unchanged retries; unkeyed clients and existing duplicates need separate handling.
- Locking an account blocks new refresh issuance. Access tokens already issued retain their
  existing expiry behavior; this change does not introduce a global token-revocation registry.
