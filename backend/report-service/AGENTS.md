# Report Service Agent Rules

Root `AGENTS.md` rules continue to apply.

Current scope is the [Huy MVP adjustment plan](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md).
Full V2/Surgery/Admission metrics are deferred; legacy zero-fill is not MVP coverage evidence.
Canonical profile is outpatient-mvp; mvp-outpatient remains an alias. Co-host import must use
ReportModuleConfiguration with report-module + outpatient-mvp, never broad-scan standalone boot.
Support composition does not grant write access to Notification, root build, Compose or Gateway.

- **Owner:** Vinh (`Dangvinh77` / `Harori`), entire-backend assignment on 2026-10-27
- **Writable scope:** `backend/report-service/**`
- **Task types:** `IMPLEMENT` inside this scope; `HANDOFF` under `docs/` when another owner must change a contract.
- Cross-module backend work follows Vinh's current entire-backend assignment in root AGENTS;
  shared repository paths and other developers' frontend scopes still require explicit delegation.
- Do not infer cross-service identifiers, query another service's database, or edit its producer.
- Subagents inherit this boundary and cannot bypass it.
- **JWT contract:** human endpoints require `type=access`; preserve the cases in
  `src/test/java/com/mediflow/report/infrastructure/security/JwtAuthFilterTest.java`.
- **Integration gate:** before changing event bindings, revenue/payment semantics or projection
  fixtures, read [MVP contracts](../../docs/plans/03-contracts.md),
  [reliability](../../docs/plans/04-data-reliability.md) and the
  [historical Huy checkpoints](../../docs/handoffs/history/2026-10-22-huy-mvp-checkpoints.md) and
  [current registry](../../docs/handoffs/README.md).
  New outpatient reads require accepted coverage; absence is not zero. Gates default OFF.
  Support must use the `lib` classifier and exclude the standalone Report application from scans;
  the library artifact alone does not establish multi-store/security composition readiness.
  Use qualified reportTransactionManager/reportJdbcTemplate/reportZoneId and explicit Report DS
  credentials. Report Flyway location is module-specific; never scan a foreign default migration
  directory. Report jobs bind reportTaskScheduler; no generic transaction manager fallback.
- Deposits are cash/liability, not earned revenue. New producer events require matching Report
  fixtures/replay/idempotency tests before the contract is marked implemented.
- **Verify:** `mvn -q -pl backend/report-service -am test`
