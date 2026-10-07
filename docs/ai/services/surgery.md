# Service: surgery

**Status:** approved bounded context; runtime foundation and internal lifecycle core exist, production authority/wire/API contracts remain open
**Owner:** Huy (`LQHuy0210`)
**Source of truth:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
**Module:** `backend/surgery-service/` · **Port:** 8091 · **Database:** `mediflow_surgery` · **Base path:** `/api/v1/surgery`

## Bounded context

Owns surgery cases, indications/reference to request, pre-op checklist, consent, schedule, room/time,
team assignments, readiness snapshots, result and state history.

Does not own the admission, patient/staff identities, financial ledger, Pharmacy stock or diagnostic
results. It keeps bare UUID references and auditable snapshots only.

## Logical data model

- `SURGERY_CASE`: surgeryCaseId, surgeryRequestId, exact `careEpisodeType/careEpisodeId`, optional
  admissionId/recordId context, patientId, departmentId, procedure code, priority, status and timestamps.
- `PREOP_CHECK_ITEM`: item code, mandatory flag, status, evidence reference, confirmedBy/At.
- `CONSENT`: type, signer/witness references, signedAt, status and revocation fields.
- `SURGERY_SCHEDULE`: room reference, planned start/end and status.
- `SURGERY_TEAM`: case, staffId, role and active interval.
- `SURGERY_RESULT`: performed method/items, outcome, complications summary and times.
- `SURGERY_STATUS_HISTORY`: old/new state, actor, reason, time and correlation.

The selected episode ID is exact: outpatient uses `appointmentId` when present, otherwise `recordId`;
an admission uses its exact `admissionId`. A distinct outpatient `recordId` remains clinical context.
Physical DDL and naming mappings belong to the implementation-ready spec.

Surgery-owned SQL identifiers are English snake_case, and Java/JSON/event fields are English
camelCase (see `docs/ai/08-persistence-naming.md`). The initial internal schema uses
`surgery_case`, `preop_checklist_*`, `surgery_consent*`, `surgery_schedule*`,
`surgery_resource_*`, `surgery_readiness_*`, `surgery_result`, `surgery_inbox` and
`surgery_outbox`. Its case identity is `surgeryCaseId` / `surgery_case_id`; the exact
episode fields are `episodeType`, `episodeId`, `admissionId`, and `medicalRecordId`.
Patient's lookup response remains the Patient-owned English `exists`/`patientId` contract.
Implemented feature-gated routes are detail/list, begin-preop, pre-start cancellation and draft
schedule replacement. None publishes Surgery outcome events. Future producer/consumer DTOs must
pass the registered contract tests before enabling integrations.

## State machine

```text
REQUESTED → PREOP_IN_PROGRESS → READY → SCHEDULED → IN_PROGRESS → COMPLETED
```

V1 `CANCELLED` is allowed only before `IN_PROGRESS`; stage is derived from persisted status.
`READY` is computed from all guards; it is not a free-form status update.

## Readiness invariant

```text
valid indication
AND mandatory pre-op checklist complete
AND active SURGERY and ANESTHESIA consents
AND team assigned
AND room/time confirmed
AND matching SURGERY financial clearance
```

Emergency override is disabled in V1. A later version may only bypass the financial guard after the
approver and Billing policies are confirmed; it can never invent consent or team assignment.

## Internal expiry recovery (2026-10-05)

The opt-in expiry worker requires both the business flag and
`mediflow.surgery.readiness.expiry.enabled=true`; both default off. It reads at most 100 due
READY/SCHEDULED candidates (default 20), then locks each case and re-checks the active snapshot ID,
explicit `validUntil` and Clock in a separate 5-second transaction. At the exclusive validity
boundary it atomically returns to PREOP, clears only the active readiness pointer, appends SYSTEM
`READINESS_EXPIRED` audit and releases only the snapshot-pinned schedule revision. Snapshot/history
remain immutable. START/terminal/replacement winners are no-ops, never a release of IN_USE.
Flyway V4 retry metadata backs failed candidates off 5..300 seconds and is revalidated under the
case lock. The job invents no clinical TTL or new outbound event key. It is internal, not a public
state setter or completed READY/START/notification workflow; these integration gates remain open.

## Planned endpoints

| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/v1/surgery/cases` | ADMIN, DOCTOR | create from exact surgery request |
| POST | `/api/v1/surgery/cases/{id}/preop` | ADMIN, DOCTOR | begin pre-op with expected revision and idempotency key |
| GET | `/api/v1/surgery/cases/{id}` | ADMIN, MANAGER, DOCTOR, NURSE | read case/readiness |
| GET | `/api/v1/surgery/cases` | ADMIN, MANAGER, DOCTOR, NURSE | filter schedule/status/department |
| PUT | `/api/v1/surgery/cases/{id}/checklist` | ADMIN, DOCTOR, NURSE | confirm pre-op item |
| POST | `/api/v1/surgery/cases/{id}/consents` | ADMIN, DOCTOR, NURSE | record consent |
| POST | `/api/v1/surgery/cases/{id}/consents/{consentId}/revoke` | policy pending; do not expose | revoke exact consent with audit |
| PUT | `/api/v1/surgery/cases/{id}/schedule` | ADMIN, MANAGER, DOCTOR | assign room/time/team |
| POST | `/api/v1/surgery/cases/{id}/readiness/evaluate` | ADMIN, DOCTOR | evaluate all readiness guards; never accept caller-supplied READY |
| POST | `/api/v1/surgery/cases/{id}/schedule/finalize` | ADMIN, MANAGER, DOCTOR | finalize an eligible schedule and reserve resources |
| POST | `/api/v1/surgery/cases/{id}/start` | ADMIN, DOCTOR | start after readiness guard |
| POST | `/api/v1/surgery/cases/{id}/complete` | ADMIN, DOCTOR | record result/performed items |
| POST | `/api/v1/surgery/cases/{id}/cancel` | ADMIN, DOCTOR | cancel with stage/reason |

The implemented transition endpoints are feature-gated and the flag defaults to false. Begin-preop uses
`POST /api/v1/surgery/cases/{id}/preop`, ADMIN/DOCTOR, a body with only `expectedCaseRevision`, and a
required `Idempotency-Key`; its actor comes from verified JWT identity. Cancellation uses
`POST /api/v1/surgery/cases/{id}/cancel`; its request body is
`{"expectedCaseRevision": 3, "reason": "Patient request"}`; it requires an `Idempotency-Key` header and
accepts `X-Correlation-Id` (generated by the service when omitted). The response is the common envelope
with `surgeryCaseId`, `caseRevision`, `status`, optional `scheduleId`, `scheduleRevision`, `cancelledAt`
and `replayed`. Both routes are behind `mediflow.features.surgery.enabled`, which remains false by
default. Neither publishes business events. Billing/Inpatient event identity and acceptance fixtures
remain open; other endpoints still require their own implementation-ready DTO/contract slices.

## Current implementation state — 2026-10-05

Detail/list and draft-schedule PUT have application ports, immutable redacted DTOs, MapStruct,
real persistence queries, direct API role tests and exact Gateway authorization. ADMIN/MANAGER read
all departments; DOCTOR/NURSE require signed staff identity and fresh active Organization authority
for its current department. Foreign detail is opaque 404; foreign department filter is empty.
List has no narrative, signatures, evidence IDs or histories and no per-row REST lookups.
Filters use UTC half-open request intervals or planned-time overlap, page 0/20/max 100, stable
requestedAt descending/caseId ascending. `snapshotValidNow` describes stored evidence only, never
new readiness/start authority. Planned schedule/procedure and actual performed result stay separate.

PUT schedule requires expected case/schedule revision and Idempotency-Key. Exact room/department,
whole-interval staff capability and inpatient medical-window lookups precede locks; freshness is
rechecked after waits. Room and capability departments must both equal the case department;
missing relationships fail 503 and foreign departments fail 422 before any mutation.
Replacement from READY/SCHEDULED invalidates to PREOP, releases only the
old exact revision, saves a new draft/history/receipt atomically, and requires re-READY/finalize.
Actual PostgreSQL covers success, injected-write rollback, replay and competing replacements.
Shared invalidation for checklist, consent, clearance and reschedule verifies the exact case,
schedule ID and schedule revision pinned by readiness before releasing anything; stale or foreign
schedule data returns conflict without clearing the snapshot or releasing a replacement booking.
Optimistic lock failure is typed 409, not generic 500 or lost update. Mutation DTOs reject unknown
authority fields; 400/401/403/404/409/422/503 and redacted 500 envelopes preserve correlation.
README/.http/Dockerfile describe only real routes. The explicit runtime-acceptance Failsafe profile
runs packaged apps, not an HTTP stub. Full workflow/referral/create/readiness/START/COMPLETE is open.

### Foundation verification history (not current endpoint inventory)

The owner-authorized platform foundation and initial pure-Java domain core exist in `backend/surgery-service/`: Maven module POM,
Spring Boot entry point, configuration, JWT authentication/default-deny authorization, correlation
handling, feature flag (disabled by default), package skeleton and test sources. Huy-delegated local
V1 defaults are recorded in the implementation-decision handoff; they guide internal Surgery work
but do **not** approve the V2 candidate's still-open cross-service contracts. The only business endpoints
are feature-gated pre-start cancellation and begin-preop routes; the remaining case/readiness/schedule lifecycle APIs,
event-specific serializer, consumer binding, and Gateway route are not implemented. A generic outbox
dispatcher and Rabbit publisher transport now exist behind both the Surgery and producer flags; both
remain false, and no business command currently creates approved event bytes. The current domain slice
contains exact episode identity and case lifecycle/readiness rules, checklist/consent/schedule/result
models, snapshot rehydration and business-revision audit. Surgery now has a V1 Flyway schema, case JPA
mapping, checklist/consent/result persistence adapters, schedule-history readback, resource/reliability
adapters and an Organization lookup port shape. The 2026-09-29 module-local suite passed 128 tests,
including PostgreSQL 16.14 persistence/race tests and RabbitMQ transport confirm/return tests. After
the local cancellation API slice on 2026-10-01, the module suite passed 141 tests with 32
Docker-dependent tests skipped because no Docker daemon was available. After adding the pre-op API and
business-route feature-gate test on 2026-10-01, the full suite passed 147 tests with 32 Docker-dependent
tests skipped; focused pre-op application/API/security/architecture tests passed 24/24 and feature-gate
test passed 1/1. On 2026-10-02, shared integration registered the module in the root reactor, provisioned
`mediflow_surgery` for fresh environments, and added its Compose runtime. The full 147-test Surgery
suite then passed with PostgreSQL and RabbitMQ available and no skipped tests. Compose verification
confirmed health on `8091`, Flyway V1, Eureka `UP`, and connections only to the owned database. These
are internal foundations, not an activated workflow. The Gateway route and cross-service business
contracts remain open. See the current Huy plan for the exact verification scope.

## Events

### Current financial grant slice — 2026-10-05

`SurgeryClearanceDecoder` accepts exact Billing V1 SURGERY grants and reads the same producer
fixture as Billing's serializer tests. An opt-in Rabbit listener now requires BOTH business and
consumer gates; neither defaults on. Fully validated other purposes are not-applicable, malformed/
conflicting grants use a dedicated durable DLQ and infrastructure failure has bounded retry.
Consumer registration and producer dispatch are independently tested across all eight flag combinations.
V2 migration stores immutable grant identity/episode/patient/case and exact ISO instants alongside
PostgreSQL timestamps. The transactional application consumer uses the existing inbox: early grants
remain PENDING until the exact case appears; a bounded durable worker retries due rows after restart;
mismatches/conflicts quarantine; duplicates do not repeat
an effect. Matching grants only store financial evidence, never auto-READY or START. A new proof
invalidates pre-start readiness/reservations through the existing audited protocol when applicable.
Expiry is exclusive and grant-time validity keeps nanoseconds. Revocation/supersession and full
readiness/START authority require their next slice; no live financial gate is enabled by this change.
New grant evidence invalidates a pre-start READY/SCHEDULED decision, with exact schedule/revision
resource release in the same inbox/proof/case transaction. An unchanged grant under a new event ID
does not invalidate again; late grants do not roll back started/terminal clinical states. Scheduled
invalidation and injected-finalization rollback are verified with PostgreSQL, alongside the 6-case
application branch suite. Full Surgery module rerun: 188 tests, zero failure/error/skip (2026-10-05).
Business/messaging flags remain off. Actual PostgreSQL/RabbitMQ tests cover duplicate intake,
early/pending-worker recovery, wrong purpose and DLQ. This does not release Billing's held V1 rows
or close revocation/supersession/readiness/START acceptance.

### Organization authority invalidation slice — 2026-10-06

The gated consumer accepts Organization's existing V1 `organization.surgery.authority.changed`
wire as an invalidation hint only. Producer-owned event serialization fixtures are shared with the
strict Surgery decoder; no Java event class is imported across modules. Intake atomically stores
raw inbox bytes, immutable `(referenceKind, referenceId, teamRole, revision)` source evidence and
durable jobs pinned to current pre-start snapshots/schedules. Exact replay and semantic new-event
replay are no-effect; contradictory source revisions quarantine/DLQ. A case-locked, bounded worker
rechecks pins and atomically invalidates READY/SCHEDULED with SYSTEM audit/exact release/completion.
Started, terminal and replacement winners remain unchanged; failures use durable 5..300s backoff.

V5 is additive, Surgery-owned only. Queue/listener/worker require business + global consumers +
`mediflow.surgery.messaging.organization-authority.enabled`, all defaults false. Malformed facts
do not retry; transient intake has three attempts before dedicated DLQ. ACK certifies durable
capture, not that all affected cases have already changed. No permission grant or clinical policy
is inferred. Full READY/finalize/START must reconcile current source revisions after waits; this
foundation does not close that distributed race window or approve notification wire/live rollout.
Current verification evidence is recorded in the [execution ledger](../../superpowers/plans/2026-10-05-huy-50-task-execution.md).

### Internal lifecycle slice — 2026-10-06

Evaluate/finalize/START/COMPLETE have in-ports and atomic orchestration. Since 2026-10-07 a
default-off HTTP boundary also exists; it requires BOTH business and lifecycle API gates and explicit
authority-backed use-case beans. No production lifecycle bean or dummy authority provider is installed.
The business flag alone cannot activate them. Seven verified proofs carry exact case/patient/department/episode,
case/schedule revisions and explicit observation/validity. Versioned checklist/consent/team policy
models have no seeded clinical or legal defaults. Actual authority integration remains open.

The 2026-10-06 financial lookup slice now also mandates a live Billing read before READY,
finalize and START mutation locks. Exact grant, patient, case, invoice, account and episode must
match. Revoked/refunded/expired clearance denies even when the local historical grant remains.
Billing failure never falls back to a stored positive grant. The financial observation is capped
by a 30-second freshness check and the true grant expiry, rechecked after resource-lock wait.
Read freshness is not the business expiry of a READY/SCHEDULED case; START obtains a new read.
No financial source revision
or distributed lock is fabricated. Transaction separation is implemented through the Surgery unit-of-work
port; the cross-service race fence remains a separate task, not a request to invent a financial revision.
See [SURGERY-BILLING](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).

Commands recheck owned checklist, typed consents and immutable grant after case/sorted resource
locks, obtain Clock after waiting, and require exact pinned dependencies. Failed START/finalize
commits a denial with PREOP/audit/exact release; it is not a successful transition. START rejects
foreign IN_USE resources and mismatched booking sets. COMPLETE stores result/items/history/receipt,
exact release and a held intent atomically, with original-result replay and no implicit correction.

Additive V6 `surgery_lifecycle_intent` is a PRIVATE HELD-only journal: no event routing, dispatcher
or approved consumer may treat its bytes as a domain event. Production publishing gates do not
release it. V6 readiness ISO precision preserves new snapshot nanoseconds without rewriting older
evidence. Remote observation, live Billing lookup and result-code verification now execute outside
transactions, including when a calling transaction must be suspended. A non-locking committed receipt
probe preserves original replay without making a PENDING receipt before preflight. The atomic write
claims again, locks case/resources and re-reads owned evidence. Each fresh write transaction has a
five-second timeout and one-second PostgreSQL lock timeout; at most three attempts retry genuine
deadlock/lock-timeout SQL states only. Exhaustion returns `SURGERY_COMMAND_BUSY` (409), other failures
are not retried. Receipt V2 persists blocking reasons/nanoseconds and still reads V1 without rewriting it.
Clinical policies, genuine authority aggregation/fences, downstream acceptance and live activation
remain open. See the execution ledger for actual tests; do not promote local mock acceptance to
joint contract approval.

The gated endpoints are POST `/{caseId}/readiness/evaluate`, `/schedule/finalize`, `/start`, `/complete`
under `/api/v1/surgery/cases`. READY/START/COMPLETE require ADMIN/DOCTOR; finalize also permits MANAGER.
JWT supplies the actor; unknown actor/role/READY/clearance/emergency/amount fields are rejected, even
for ADMIN. Expected case/schedule revisions and `Idempotency-Key` are mandatory. A committed guard
denial returns an explicit state plus `blockingReasons`, never a successful clinical transition.
Evaluate returns the immutable readiness snapshot as `subjectId`; finalize/START identify the pinned
schedule and COMPLETE identifies the immutable result. Both feature gates remain false. Enabling both
without real use-case beans fails startup. These local HTTP tests do not certify Gateway integration.

**2026-10-07 local outbound boundary:** typed V1 `surgery.case.created`, `surgery.ready`,
`surgery.readiness.invalidated`, `surgery.completed` and `surgery.cancelled` have actual serializer
fixtures for admission/outpatient. New V7 `surgery_care_event_outbox` stores only HELD wire bytes,
with immutable event/content uniqueness and exact persisted case/context/revision checks. Local
READY/COMPLETE/CANCEL and all seven readiness-invalidation callers capture in their transaction.
Case-created capture requires the caller's creation transaction; a live referral/creation caller is
still missing. This is separate from PRIVATE V6 intents; neither table has a public V1 dispatcher.
Canonical payloads/consumer gates remain in [care contract](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
and [charge contract](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md).
Inpatient verifies actual admission bytes at its adapter; outpatient/reference/late acceptance,
Billing reconciliation, Notification reminder handling, policy sources and live rollout stay OPEN.

**2026-10-07 internal creation core:** a channel-neutral create in-port now atomically persists
REQUESTED case, pinned PENDING checklist/items, initial histories, canonical V7 HELD charge bytes
and V8 immutable creation receipt. A global request UUID/fingerprint fence serializes replicas;
changed clinical intent conflicts, replay preserves original IDs/time and still reauthorizes without
repeating external lookups or charge capture. Preflight suspends caller transactions; Clock/proof
validity and exact approved template are checked after lock waits. The existing durable pending
clearance worker can recover after creation commits, independently of any in-memory callback.
There is no production creation-authority provider, HTTP/referral adapter or permissive fallback.
The internal caller is not live referral/creation acceptance, a source lease or clinical approval.
V1–V7 are unchanged; V8 neither backfills/adopts old cases nor releases held events. Local verification
and the ten selected backlog edges: [creation batch](../../superpowers/plans/2026-10-07-surgery-creation-batch.md).

**Subscribe:** `surgery.requested`, `financial.clearance.granted` with `purpose=SURGERY`, and explicit
pre-op Lab/Pharmacy facts chosen by the future contract. A general Lab result does not automatically
complete a checklist item without exact case/order correlation.

## Business rules

1. One `surgeryRequestId` creates at most one case.
2. Case/admission/patient/department references must match producer facts.
3. READY and START both re-evaluate mandatory guards transactionally.
4. Team/room/time conflicts are rejected; staff eligibility comes from Organization lookup.
5. Completion records performed item codes so Billing reconciles actual charges idempotently.
6. Cancellation is append/audit preserving and never edits completed payment history.
7. Duplicate events/commands do not repeat charge, result or notification side effects.

## Mandatory handoffs

- [`CONTRACT-INPATIENT-SURGERY-01`](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)

## Business implementation and shared bootstrap gates

The initial internal domain slice is not a production workflow by itself. The [active Surgery backlog](../../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md#surgery-backlog)
separates executable local tasks from unresolved integration edges. Huy-delegated V1 defaults allow
internal models, persistence, reliability and application tests with port doubles; they do not require
all H-01.2/H-01.3 rows to close first. Update the slice's local API/schema specification before coding;
unconfirmed producer fields, clinical evidence/consent policies and Organization eligibility remain
fail-closed. Wire adapters and real workflows require the corresponding canonical contract/fixtures,
not just a local mock. V1 has no emergency override, post-start abort or result correction.
The initial-domain implementation passed 30 tests; the current full module-local suite passed 128 tests
with PostgreSQL 16.14 migration/JPA/reliability/race and RabbitMQ publisher confirm/return verification
on 2026-09-29. This Rabbit test covers generic transport only, not an approved Surgery event contract.
Root-reactor, fresh/existing database bootstrap and Compose/Eureka integration were verified on
2026-10-02. On 2026-10-05 actual packaged Eureka/Gateway/Surgery/Organization acceptance passed
three Failsafe scenarios, including both security boundaries, correlation, scoped read/draft and
capability revocation denial. The bootstrap handoff was retired after this evidence. Shared DB init
runs only on a fresh volume; check/create the missing owned database on existing environments,
never remove a user volume to replay initialization. Add same-version producer/consumer fixtures and
contract tests before enabling any integration. Status remains `DESIGN_READY` while any required
producer/consumer is missing.
