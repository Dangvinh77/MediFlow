# Core Service Agent Rules

Root `AGENTS.md` rules continue to apply.

- **Owner:** Hoàng Anh (`TranHoangAnh94`)
- **Writable scope:** `backend/core-service/**`
- **Contexts:** Organization and Patient remain separate bounded contexts under their existing
  `com.mediflow.organization` and `com.mediflow.patient` packages.
- **Data:** preserve separate datasources, entity-manager factories, transaction managers, and
  Flyway locations. Do not merge schemas or change migration history.
- **API/security:** preserve existing Vietnamese/English wire fields, public paths, JWT claims,
  method roles, and service-token behavior.
- **Verify:** `mvn -q -pl backend/core-service -am test`
