# Surgery Service

**Owner:** Huy (`LQHuy0210`)
**Coordinates:** port `8091`, database `mediflow_surgery`, base path `/api/v1/surgery`
**Design:** [service boundary](../../docs/ai/services/surgery.md) · [Care–Finance V2 candidate](../../docs/eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md)

This module contains the platform foundation plus an initial pure-Java domain slice for exact episode
identity, the surgery-case lifecycle, readiness snapshots, guard re-evaluation and V1 pre-start-only
cancellation. It deliberately has no business API, migration, event binding, or placeholder-success
endpoint. The feature flag remains disabled.

Cross-service episode/referral, billing, clearance, room/team and outcome contracts remain tracked in
the [Surgery decision handoff](../../docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md).
Cross-service producers/consumers and clinical/Organization contracts remain gated by owner fixtures.
Root module registration, DB/Compose wiring and the Gateway route remain with their owners in the
[bootstrap handoff](../../docs/handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md).

Run local unit/context tests with `mvn -f backend/surgery-service/pom.xml test`. The root Maven
reactor will include this service after the shared owner registers it in `pom.xml`.
