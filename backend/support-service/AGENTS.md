# Support Host Agent Rules

Root `AGENTS.md`, canonical MVP design and existing Report/Notification module rules apply.

- This directory is composition infrastructure, coordinated by Huy; Report and Notification
  retain their original business ownership. Changes to business modules or root
  POM/Compose/Dockerfile/CI/Gateway require explicit task-scoped assignment.
- Import only `ReportModuleConfiguration` and `NotificationModuleConfiguration` from their
  plain `lib` artifacts. Never broad-scan standalone launchers or copy business code into the host.
- Keep separate named datasource/EMF/Flyway/transaction manager/JDBC, credentials and pools.
  Keep original SQL bytes, histories, context queues/DLQs and Report scheduler ownership.
- Module security/advice must retain exact path/role/patient rules. Unmatched host paths deny.
  New gates default OFF; deferred Admission/Surgery/Report V2 behavior stays blocked.
- No cross-store SQL/transaction or dual standalone/host writers. Host restart must preserve
  accepted writes and original delivery IDs/bytes. Consult the [runbook](README.md).
- Verify from root: `mvn -q -pl backend/support-service -am -Psupport-acceptance verify`.
  This requires Docker; skipped/unrun container cases never establish runtime acceptance.
