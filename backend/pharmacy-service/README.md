# pharmacy-service

2026-10-15 Core/MVP alignment: use canonical profile `outpatient-mvp`; `mvp-outpatient` remains
a compatibility alias. Both select Core for Patient/Organization discovery and exclude legacy
payment auto-dispense/admission authority/consumers. Explicit V1 gates remain OFF until acceptance.
Core fixtures are read from the new source location; logical caller stays pharmacy-service.

Cutover preparation: [owned runbook](../../docs/plans/huy-mvp-cutover-runbook.md).
`src/main/resources/operations/mvp-cutover-inventory.sql` is an operator-only, bounded,
read-only snapshot for a verified Pharmacy clone, not an app migration or HELD activation tool.

Drugs, prescriptions, dispensing and stock. **Saga participant** in prescribe → pay → dispense.

Reference: [MVP design](../../docs/plans/01-system-design.md) ·
[Huy adjustment plan](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md) ·
[source manifest and verification](../../docs/plans/huy-backend-mvp-baseline.md).

- **Port:** 8085 · **Base path:** `/api/v1/pharmacy` · **DB:** `mediflow_pharmacy` (PostgreSQL)
- **Owns tables:** `DRUG`, `PRESCRIPTION`, `PRESCRIPTION_LINE`, `DISPENSE_SLIP`, `PROCESSED_EVENT`, `STOCK_RESERVATION`, `PAYMENT_RECEIPT`, `PHARMACY_EVENT_OUTBOX`, `STOCK_ADJUSTMENT`, `PHARMACY_SCHEDULER_LEASE`
- **Architecture:** clean architecture (hexagonal) per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `application → domain`; driving adapters `web`/`messaging` call `application`, `infrastructure` implements its out-ports. Dependencies inward only.

## MVP adjustment — 2026-10-14

`outpatient-mvp` (alias `mvp-outpatient`) is a module profile, not the completed six-application runtime.
Patient/Organization discovery points to Core; logical caller identity and existing lookup paths remain.
Admission configuration and the legacy payment listener are excluded in this profile; durable queues,
data and HELD outbox evidence are not deleted. Compatibility without this profile keeps V0 behavior.

Public V1 POST/lifecycle are opt-in: profile plus global V2, identity, clinical-context, clearance-consumer, clearance-authority and
`mediflow.pharmacy.outpatient-api.enabled` (default false) are required. V1 creation uses a canonical
UUID `Idempotency-Key` and the proof-checked writer; V0 POST is rejected in the MVP profile.
Dispense/cancel now route to a gated V1 facade; expiry reuses the existing lease/REQUIRES_NEW job boundary.
Only persisted V1 outpatient rows enter that pipeline; V0 compatibility and deferred Admission stay separate.
Stock failures return a proven FAILED outcome after rollback/re-evaluation; retries read the exact stored proof.
Before stock effects, the locked V1 transaction reads Billing's current clearance lookup with a signed
`pharmacy-service` credential. Exact invoice/account/patient/purpose/episode/grant times must match the
local prescription-bound proof; expired, revoked/ineligible, stale or unavailable observations deny effects.
This is a point-in-time observation, not a distributed lease that serializes a later Billing revocation.
The profile disables Open EntityManager in View so preliminary reads cannot leak stale entities into writers.
Keep the API gate OFF outside isolated tests until H6-05/06/12 pass the real provider/DB/broker chain.

V20 adds an explicit delivery cohort for **new** V1 outpatient aggregates. Enrollment requires all six
API gates, the MVP profile, `mediflow.pharmacy.outpatient-events.enabled=true` and a bounded
`mediflow.pharmacy.outpatient-events.acceptance-reference`. Defaults are OFF/empty; the reference
records operator approval but does not establish consumer acceptance by itself. Existing HELD rows
cannot be enrolled by replay or flag changes. A database trigger rejects changing a V1 row's delivery
cohort/enabled state. Accepted in-flight cohorts retain their terminal delivery when new enrollment
is disabled. The dispatcher preserves lifecycle order and original bytes/IDs; consumers still deduplicate.
Published V1 proof is excluded from automatic purge until a separate archive/retention policy exists.
See [current implementation evidence](../../docs/plans/huy-backend-mvp-baseline.md#12-current-clearance-and-selective-outpatient-delivery).

Fresh test counts and Docker limitations are recorded in the source manifest above. Historical
counts below are earlier evidence, not results for this MVP batch or approval to enable deferred work.

## Historical implementation notes — compatibility/deferred

2026-10-12 identity continuation: the internal checked-creation preflight now also reads
Organization's existing service-only `/api/v1/org/staff/{id}/exists` doctor-eligibility contract.
Generic active DOCTOR identity and a positive `eligibleDoctor` decision for the exact department
are both necessary. Missing/false eligibility denies; malformed, inconsistent, failed or
wrong-correlation responses are unavailable, never a fallback to job/team labels. All four reads
remain outside mutation transactions and share the original preflight-start freshness bound.
This does not certify a current licence/clinical medication order, authorize admission medication,
open public V1 or release held events. Existing independent identity/V2 gates stay default OFF.
Focused 64 unit/real-Feign-HTTP tests and fresh full Pharmacy 625 tests / 90 suites pass, zero
failures/errors/skips, actual PostgreSQL/RabbitMQ. The compatibility eligibility HTTP examples are LOCAL,
not a new producer-owned fixture or full distributed acceptance.

2026-10-09 identity follow-up: `MEDIFLOW_PHARMACY_IDENTITY_ENABLED` AND care-finance-v2
(both default false) register real Patient and Organization preflight reads. The internal
context-checked caller requires them and rechecks both identity/context after receipt/stock waits.
Confirmed missing/ineligible identities reject; failed/malformed reads are unavailable. This is
necessary descriptive evidence, not an order/license grant. Public V1 and held rows stay closed.
Expiry and proof timestamps are re-evaluated after reservation reads, including crossing midnight.
[Verification and remaining V2 work](../../docs/plans/2026-10-14-huy-backend-mvp-adjustment-plan.md).

2026-10-09: opt-in Clinical context transport and internal context-checked creation now match
exact record/patient/prescriber/department/episode and recheck freshness after receipt/stock lock
waits. Calls are service-authenticated and outside mutation transactions, with absence distinct
from outage. This is necessary relationship evidence only; public V1 remains fail-closed and
HELD rows remain blocked. [Canonical contract](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#additive-clinical-prescription-context-lookup--2026-10-09).

**In progress.** Domain, application ports/services, persistence adapters, stock reservations,
expiry scheduler, HTTP/JWT security and RabbitMQ consumer/topology are implemented. The
transactional outbox is enabled for durable event delivery; cross-service E2E gates remain before release.
Outbox delivery now supports bounded exponential retry, per-row replay, opt-in retention cleanup and
per-aggregate causal ordering. Administrators can replay quarantined rows, and Micrometer exposes
pending/quarantine count and age gauges. Stock adjustments persist an immutable audit row in the same
transaction as the drug mutation and `stock.adjusted` outbox event.

Automatic published-row cleanup defaults OFF via `MEDIFLOW_PHARMACY_OUTBOX_PURGE_PUBLISHED_ENABLED`.
Review archived source completeness and backup/restore before enabling it; 7-day retention is not a
historical replay archive. Dispatch and operator replay remain available with cleanup disabled.

Package layout (already created, each folder holds a `.gitkeep` until you fill it):

```
domain/model          domain/exception
application/port/in   application/port/out   application/dto   application/mapper   application/service
web                   (driving HTTP: controllers + GlobalExceptionHandler)
messaging/consumer    (driving events: @RabbitListener)
infrastructure/persistence   infrastructure/messaging   infrastructure/security   infrastructure/config
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_pharmacy;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/pharmacy-service -am spring-boot:run
```

Swagger UI: http://localhost:8085/swagger-ui.html

## Events

- **Publish:** `prescription.created`, `prescription.cancelled`, `prescription.expired`, `prescription.filled`, `prescription.dispense.failed`, `stock.low`, `stock.adjusted`
- **Subscribe:** `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

Payment events are claimed atomically by `eventId`; duplicate deliveries are ignored. Manual
dispensing never accepts a client-supplied `paid` flag and requires a durable `PAYMENT_RECEIPT`.
Listener
retries are bounded (default 3 attempts with exponential backoff) and poison messages are routed
to `pharmacy.dlq`. Configure with `MEDIFLOW_PHARMACY_RABBIT_RETRY_*` environment variables.

## Care-finance V2 boundary

The prescription request accepts an explicit `careContractVersion: 1` context shape for validation,
but V1 creation remains fail-closed with `PHARMACY_CARE_FINANCE_V2_UNAVAILABLE` until exact Billing
clearance and Inpatient admission projections are implemented and verified. The legacy
`payment.completed` consumer accepts version-0 prescriptions only; it rejects V1 before claiming a
receipt or touching stock. The feature flag remains `false` by default.

V15 adds a local admission context/event ledger. The offline Inpatient V1 decoder reads actual
producer fixtures; CLOSED is absorbing even when received before STARTED, conflicting source facts
roll back their claims, and exact nanosecond source instants survive PostgreSQL reload. No new Rabbit
binding or V1 dispense permission is enabled. Medical discharge, transfers and freshness still need
the Inpatient contract; this context check alone is not an authorization decision.

`PrescriptionCareEventCodec` supplies separate V1 DTOs/round-trip serialization for all five
prescription lifecycle events. Proposed fixtures are in
`src/test/resources/contracts/care-finance-v1/`; they are not live producer/consumer acceptance.
The V0 publisher and committed outbox bytes are untouched. V1 create/dispense remains closed.

V16 adds consumer-local prescription clearance grants, target fences and event claims. Exact
PRESCRIPTION purpose, V1 outpatient target, patient and episode are required. A grant arriving before
its prescription remains `PENDING`; a matching late prescription is rechecked before verification.
The grant only authorizes, never auto-dispenses or creates a `PAYMENT_RECEIPT`. Grants are immutable;
duplicate events/new-event-ID same grant have no repeated effect, changed snapshots are conflicts.
The transaction-only authorization primitive checks the clock after its lock waits, including exact
source nanosecond expiry. An internal held-event outpatient transaction now invokes it; no public
API, legacy workflow or Rabbit listener invokes this transaction.

The legacy dispense executor now rejects V1 even if a V0 receipt exists. Authorization denial is
not recorded as stock failure/compensation. V0 reservation TTL is rechecked after **all** stock locks
are acquired. Billing fixture approval, grant-time/revocation policy and cross-owner acceptance
remain activation gates. PostgreSQL runtime evidence is recorded in the current Huy plan.

V17 adds historical drug-name snapshots and exact terminal business times (`lifecycle_at_iso`),
independent of the JPA audit timestamp. New V0 lines capture the server catalogue name without
changing their wire shape; old names/times are not backfilled. A pure V1 lifecycle factory and
transaction-required capture hook read locked prescription/slip evidence; no public or legacy
workflow invokes this hook. The internal V1 outpatient executor invokes it in the stock transaction.
Its writer stores immutable V1 bytes in the existing outbox with
`delivery_enabled=false`: one creation and one terminal outcome per prescription. A DB constraint,
dispatcher claim filter and lease completion guard prevent accidental delivery; admin replay does
not enable the row. Held critical predecessors still block causal successors. Live activation
requires a reviewed migration/cutover, not a flag toggle. Legacy cancel/failure/compensation reject
V1; the V0 expiry job skips V1 without release or events. Internal V1 commands below are separate.

`DispenseCarePrescriptionUseCase` is internal only. It requires a staff/account command and exact
V1 outpatient context, locks Rx/slip and every stock/reservation in stable drug order, then checks
clearance. All reservation TTLs, drug expiry and grant expiry are rechecked after authorization
lock/write waits, before any stock mutation. Stock, reservation, explicit Rx/slip business time,
held filled bytes and existing `stock.low` outbox commit together. Missing creation/invalid snapshots
fail the whole transaction; no V0 filled event, payment receipt or refund is produced. Retry returns
only matching persisted slip/Rx AND held filled proof, never creates another event or decrements stock.
Admission eligibility/live activation remain open; the internal outpatient creation/terminal paths
now exist and real PostgreSQL rollback/concurrency/nanosecond evidence is in the plan.

V18 adds an internal create-command receipt/fence. `CreateCarePrescriptionUseCase` checks DOCTOR
self/ADMIN delegation before replay, rejects unsupported admission eligibility, locks drugs in UUID
order, checks expiry/available stock with a fresh clock and snapshots server price/name. Rx, complete
reservations, pending slip, held creation and receipt commit together. Same command/intent retries
return the original Rx; changed actor/intent conflicts; failed writes roll back the fence too. This
port is not connected to the public API. Patient/episode authority approval still gates activation.

V19 stores the exact `discharge.medically.approved` singleton separately from administrative
`admission.closed`. The offline Inpatient decoder reads the producer fixture; a discharge arriving
before start is retained, and a late start never restores medication eligibility. Existing V18
admission rows upgrade with no fabricated discharge. This is a local projection only: no new Rabbit
binding or public admission create/dispense path is enabled until transfer/freshness and cross-owner
acceptance are complete.

`CancelCarePrescriptionUseCase` and `ExpireCarePrescriptionUseCase` terminate whole reserved orders
with matching Rx/slip/held proof; cancellation is restricted to the prescribing doctor or ADMIN,
expiry uses time after lock waits. Retry does not release twice or change the original exact time.
`RecordCareStockFailureUseCase` runs in REQUIRES_NEW after dispense rollback and rechecks locked
current business stock/authorization. Missing grant/structure or infrastructure error never becomes
FAILED; healthy stock or a competing terminal winner is not overwritten. Definitive shortage/expiry
releases reservations and holds one failure fact, without stock decrement, PaymentReceipt or refund.
These internal paths do not enable a listener, scheduler, public route or V1 outbox delivery.

Reservation TTL defaults to 24 hours and is configurable with
`MEDIFLOW_PHARMACY_RESERVATION_TTL` (ISO-8601 duration, for example `PT24H`).

## Tests

### Packaged distributed-flow acceptance

The owned `distributed-acceptance` profile contains three isolated packaged suites:

- `PharmacyDistributedRuntimeAcceptanceIT`: current Pharmacy/Billing/Report/Notification,
  four private PostgreSQL stores and RabbitMQ. Twelve cases cover V0 duplicate delivery,
  JVM/broker recovery, exact committed-stock/finalize/ACK/publish-mark crash windows,
  real Billing cancel/expiry/stock-shortage compensation and retained DLQ/redrive.
- `PharmacyAuthorityRuntimeAcceptanceIT`: actual packaged Core/Clinical, their three private
  context stores and signed production Pharmacy adapters. Setup uses producer HTTP APIs only.
  Clinical's supported compatibility seed disables its durable publisher because of the regression
  recorded in the activation handoff; lookup/security remain real. Not MVP exam/publisher acceptance.
- `PharmacyOutpatientRuntimeAcceptanceIT`: packaged MVP negative guards and unchanged Billing
  grant/cash fixtures over RabbitMQ. Grant survives two JVM restarts without auto-dispense.
  The live V1 issuer-chain case is explicitly skipped by default: Billing's new `invoice.created`
  source remains HELD. Run it with `-Dmediflow.acceptance.billing-v1-published=true` only after
  producer-owner acceptance, never by releasing foreign rows from this test.

The test-only `acceptancefixture` bootstrap injects faults/observes **Pharmacy's own** store in
disposable containers. It is outside the production scan and absent from the shipped Boot JAR;
its endpoints are not production APIs. No foreign database is queried or seeded. Success snapshots
in Failsafe XML contain synthetic IDs, states and payload hashes, not tokens or raw payloads.
Notification receives V0's mandatory `invoice.created` predecessor. These tests do not establish
Gateway/Support acceptance, V1 rollout, admission activation or a complete finance rollout.

```powershell
mvn -pl backend/core-service,backend/clinical-service,backend/billing-service,backend/report-service,backend/notification-service -am -DskipTests package
mvn -pl backend/pharmacy-service -am -Pdistributed-acceptance -DskipUnitTests=true verify
```

See [batch 8 results and limitations](../../docs/plans/huy-mvp-batch-8-acceptance.md).

See [scope, priority and evidence](../../docs/plans/04-data-reliability.md).

### Default-off admission integrations (2026-10-08)

With care-finance-v2 also enabled, `MEDIFLOW_PHARMACY_ADMISSION_CONSUMER_ENABLED` opts into the
private durable admission lifecycle queue; `MEDIFLOW_PHARMACY_ADMISSION_AUTHORITY_ENABLED`
independently registers the current-admission Inpatient read adapter. Both default false.
The queue commits immutable exact lifecycle evidence only, never automatic stock/medication effects.
The lookup uses a short-lived Pharmacy service credential, Eureka, bounded timeouts/circuit breaker
and strict identity/correlation/freshness validation; failure is unavailable, not confirmed absence.
No REST call is allowed within a mutation transaction. Neither path opens public V1 admission
commands or held outbox delivery, and current placement/order authority remains an activation gate.
See [current evidence and limitations](../../docs/plans/huy-backend-mvp-baseline.md).

```bash
mvn -pl backend/pharmacy-service test        # unit (domain + application, no Spring)
mvn -pl backend/pharmacy-service verify      # + integration (Testcontainers, needs Docker)
```

For the installed Docker Desktop engine, the verified command is
`mvn -q -f backend/pharmacy-service/pom.xml -Dapi.version=1.44 test`.
