# 10 — surgery-service: implementation-spec working draft

**Status:** `DRAFT / H-01a–d PARTIAL` — glossary, ownership, rule/test inventory, contract manifest and policy-independent technical design are ready for review; episode/referral mapping and business transitions are **not approved**. This is not an implementation-ready business spec. Do not scaffold business entities, DDL, API commands or event consumers from this file yet.

**Owner:** Huy (`LQHuy0210`) · **Planned module:** `backend/surgery-service/` · **Source of truth:** [care-finance redesign](../../architecture/mediflow-care-finance-redesign.html), [Surgery bounded context](../../ai/services/surgery.md), [Inpatient–Surgery contract](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [Surgery–Billing contract](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md), [Care–Billing contract](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md). Open decisions are tracked in the [Surgery handoff](../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) and [Huy plan](../../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md).

## 1. Glossary and identity

| Term | Meaning and owner | Must not be confused with |
|---|---|---|
| `surgeryRequestId` | Stable identifier of one clinical intent/referral, supplied by Clinical or Inpatient. Producer assignment and global stability across paths remain **D02**. | `surgeryCaseId`, `eventId`, patient ID. |
| `surgeryCaseId` | Surgery-owned identity created for a case after accepting an exact referral. Billing's planned surgery charge uses this as `sourceId` under the Surgery–Billing contract. | Referral ID or care episode ID. |
| `recordId` | Clinical-owned medical-record reference. It may be the outpatient Billing episode ID only for a walk-in without an appointment, if that episode was selected when the account opened. | Automatically the outpatient episode ID for every record. |
| `appointmentId` | Clinical-owned appointment reference. For a scheduled outpatient visit, Billing selects it as `careEpisodeId` when opening the account. | `recordId` or surgery case ID. |
| `admissionId` | Inpatient-owned admission identity, carried by `admission.started`; exact `careEpisodeId` for `careEpisodeType=ADMISSION`. | Admission request ID, bed ID, or patient ID. |
| `patientId` | Patient-owned identity used for correlation and reference validation. | A lookup key for a patient's latest admission, surgery, invoice or unpaid charge. |
| `departmentId` | Organization-owned department identity carried by producer facts; Surgery stores a bare reference/snapshot needed for its case. Authority for later department changes is not yet specified. | Cross-service foreign key or inferred department from a clinician's token. |
| `careEpisodeType` + `careEpisodeId` | Billing-owned account/charge grouping: `OUTPATIENT_VISIT` with the preselected appointment/walk-in ID, or `ADMISSION` with `admissionId`. | `surgeryCaseId` or a patient-wide account. |
| `eventId` | Identity of one event delivery/fact for consumer inbox dedupe. | The business identity of a referral, case, charge or command. |

All cross-service references are bare UUIDs. Surgery does not read another service's database or copy its aggregate. A missing exact reference is a contract error or explicit pending dependency under the later H-01c/H-01d policy; it is never filled by searching on `patientId`.

## 2. Ownership matrix

| Information / decision | Authority | Surgery may retain | Surgery must not do |
|---|---|---|---|
| Patient identity and demographic facts | Patient | `patientId`, permitted audit snapshot | Create or amend patient identity. |
| Appointment, medical record, indication and referral origin | Clinical; Inpatient may originate its own surgery referral after **D02** mapping | Exact `recordId`, `surgeryRequestId`, indication/procedure snapshot and producer correlation | Guess the originating producer or alter Clinical records. |
| Admission, bed assignment and discharge | Inpatient | Exact `admissionId` and admission-status evidence required by a later contract | Select an admission by patient or release/transfer a bed. |
| Case lifecycle, readiness, checklist, consent, team, schedule, performed result and history | Surgery | Authoritative Surgery aggregates and auditable snapshots | Represent a planned schedule or payment as a completed operation. |
| Staff/department identity and eligibility | Organization | Bare `staffId`/`departmentId`, eligibility-check evidence | Maintain a competing staff/department master. Room-master ownership remains **D04**. |
| Procedure price, charge, payment, deposit, clearance, refund and settlement | Billing | Exact purpose-scoped clearance evidence and charge-source reference | Calculate price, mark a payment completed or issue a refund. |
| Drugs and stock | Pharmacy | Exact medication/order references when required by a Surgery workflow | Reserve or decrement pharmacy stock. |
| Operational and financial report totals | Report | Publish approved Surgery facts | Mutate Report projections directly or use them as workflow authority. |

## 3. Episode/referral mapping — proposal pending D01/D02

| Incoming context | Known rule | Open decision / required producer evidence |
|---|---|---|
| Exact `admissionId` for an inpatient case | `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`; `surgeryCaseId` remains a separate source ID. | D02 must identify the referral producer and stable `surgeryRequestId`; D12 must define authoritative admission–patient–department validation. |
| `recordId` only | Clinical record is a reference, not proof of an admission. | **D01:** either require admission before Surgery accepts the case, or explicitly support outpatient Surgery. If outpatient is supported, the producer/Billing contract must carry the already-selected `careEpisodeId` (`appointmentId` when scheduled, `recordId` for walk-in). Surgery cannot infer it from `recordId` alone. |
| Both `recordId` and `admissionId` | They remain distinct references; `admissionId` is the inpatient Billing episode ID if this is an admission case. | **D01:** decide whether `recordId` is referral provenance only and define the exact mismatch rejection rule. No automatic precedence or fallback is approved. |
| Same clinical intent crosses Clinical and Inpatient | One intent must not silently produce two cases. | **D02:** define which producer publishes in each path and how the *same* stable `surgeryRequestId` is preserved; provide duplicate/concurrent fixtures. |

### Referral → case → charge-source gap

The current Care–Billing contract names `surgery.requested` as a planned-charge trigger, while the Surgery–Billing contract requires `sourceType=SURGERY` and `sourceId=surgeryCaseId`, which Surgery creates **after** accepting a referral. A referral cannot already contain a Surgery-created case ID. **D02 must resolve the post-case charge-source fact/API and its owner before implementation.** This draft does not rename `surgery.requested`, invent a second event or let Billing guess the case ID.

## 4. Logical model is not physical DDL

The architecture and Surgery service doc use labels such as `SURGERY_CASE`, `PREOP_CHECK_ITEM`, `CONSENT`, `SURGERY_SCHEDULE`, `SURGERY_TEAM`, `SURGERY_RESULT` and `SURGERY_STATUS_HISTORY` to describe concepts and ownership. They do **not** lock table names, columns, keys, cardinalities or migrations. The physical schema belongs to H-01d after the relevant decisions; default project naming is Vietnamese UPPER_SNAKE table names and Vietnamese snake_case columns with explicit mappings. The English-table exception in the existing Pharmacy/Billing/Report branch does not transfer to Surgery.

## 5. Acceptance needed before H-01a can close

1. Vinh, Huy and Lộc choose the supported record-only/admission Surgery contexts and sign off one request + clearance fixture per context (**D01**).
2. Clinical/Inpatient owners identify referral producer per path, stable `surgeryRequestId` and the post-case Billing charge-source handoff; duplicate/concurrent fixtures prove one case per intent (**D02**).
3. Each fixture distinguishes `surgeryRequestId`, `surgeryCaseId`, `recordId`, `admissionId`, `careEpisodeId`, `patientId` and `eventId` without inference or fabricated values.
4. Only then promote this glossary/mapping into the full implementation-ready spec with DTOs, DDL, transitions and test matrix (H-01b–d); keep this file marked `DRAFT` until that work is complete.

## 6. H-01d — technical foundation independent of business policy

This section specifies a **proposed technical contract**, not a completed migration or permission to invent Surgery behavior. The module follows [the mandatory blueprint](../../ai/04-microservice-blueprint.md): `com.mediflow.surgery.domain` has no framework dependency; `application` declares in/out ports and owns transaction orchestration; `web` and `messaging/consumer` drive those ports; `infrastructure` implements persistence, messaging, security and client adapters. No cross-service JPA relation or database access is allowed.

| Concern | Foundation rule | Gate before executable code |
|---|---|
| Runtime | `surgery-service`, port `8091`, dedicated `mediflow_surgery` PostgreSQL DB, Eureka registration, RabbitMQ and actuator health; credentials through environment variables, no committed secrets. `ddl-auto=validate`; Flyway owns schema changes. | The new-module skill requires an implementation-ready V1 schema; root Maven/DB/Compose/Gateway changes need their named owners in the [bootstrap handoff](../../handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md). |
| Startup | Empty business API is not simulated by a success controller. No Surgery business listener is enabled until its event version, routing, producer and replay policy are approved. Health proves process/dependencies, not case workflow readiness. | S-01a–c/e may be separately accepted as a shell only if the module-scaffold requirements and shared registration are resolved. |
| Security | Re-verify JWT downstream; accept access tokens for human APIs, reject refresh and service tokens on those APIs; default deny and `@PreAuthorize` on every eventual endpoint. `SYSTEM` is confined to approved service-to-service operations. Preserve correlation metadata and standard error envelope. | Exact human roles follow [Surgery endpoint matrix](../../ai/services/surgery.md); emergency/self-approval permissions wait for D06. |
| Delivery boundary | One local transaction commits the Surgery state change, its audit/history and an immutable outbox row. A consumer cannot acknowledge success until its inbox/effect transaction commits. No external HTTP call is made while holding a case/resource DB lock. | Actual event names/payloads, consumer effects and command guards wait for H-01b/c and their Dxx decisions. |

### Aggregate and concurrency boundaries

- `surgeryCaseId` identifies the proposed case aggregate root. Checklist, consent, team, result and status history are logically case-owned, but which rows are in one transactional aggregate and which are append-only/versioned remain H-01b/D03–D07 decisions. Do not make their physical tables from the concept names in §4.
- The intended invariant is **at most one case per stable clinical intent**. A database unique key on the approved `surgeryRequestId` (or a producer-qualified key if D02 explicitly requires it) must win a concurrent first-insert race. It cannot be derived from `patientId`, `recordId` or `eventId`. The exact key and replay semantics wait for D02.
- Mutating one case must compare an approved revision/optimistic version or take a deliberate case lock; stale commands must have a deterministic conflict result and cannot append history or outbox. The public `expectedVersion`/operation-key contract and idempotent retry behavior wait for H-01c/D05/D07.
- A case lock does **not** prevent two different cases taking the same room/time or staff interval. Resource reservation needs its own authoritative owner, overlap constraint/lock order and transaction boundary (D04/D05). Until those exist, scheduling is not safe to expose.
- If a transaction needs more than one lock, define a single deterministic order (resource identity, then case identity, then dependent rows) in the final use-case algorithm and test reversed concurrent requests. This is a design requirement, not an approved room schema.

### Durable event ingress and pending dependencies

1. Validate envelope identity/type/version/producer and compute a canonical payload fingerprint before applying a recognized event. Insert/claim an inbox identity with a database uniqueness guarantee. Same `eventId` + same fingerprint is a no-op replay; same `eventId` + different fingerprint is a contract conflict with alerting, never silently overwritten.
2. Inbox state and any accepted business effect commit together. A parsing/contract failure does not become `PROCESSED`; infrastructure failure is retried according to broker policy. A consumer must not acknowledge before commit. `eventId` dedupe is distinct from a business command operation key or Surgery referral uniqueness.
3. A valid fact that arrives before its exact case/admission dependency cannot be applied to a guessed patient-wide case. It remains explicitly `PENDING_DEPENDENCY` (or equivalent durable state), with the original identity/fingerprint/correlation, reason, attempt count, next-attempt and observation metadata. An operator-visible alert is required for unresolved or expired items. Which event types may pend, TTL, expiry outcome, late/correction policy and authoritative relationship lookup are **D11/D12**, so no listener may yet claim this as complete.
4. Retry of pending work must re-check the exact target and guards, atomically transition to applied or terminal failure, and be safe against two workers and duplicate delivery. Retention must cover the agreed producer replay window; deleting inbox evidence earlier would invalidate dedupe. Retention and historical rebuild source remain D11.

### Transactional outbox and crash recovery

1. Persist event identity, approved type/version, aggregate identity/revision, correlation and immutable payload in the same transaction as the state/history change. A rollback leaves neither a business effect nor a publishable row.
2. A dispatcher claims rows with bounded leases (`FOR UPDATE SKIP LOCKED` is a PostgreSQL option), publishes with broker confirmation, and marks delivery only after confirmation. It retries transient failure with bounded backoff and makes expired leases reclaimable after a crash. Retries retain the **same** `eventId` and payload.
3. A crash after broker confirm but before recording success can republish; consumers therefore need idempotency. This design promises atomic local intent plus at-least-once delivery, **not** exactly-once broker delivery. Ordering is either enforced per aggregate revision or explicitly handled by version-aware consumers; choose and test it when H-01c fixes event contracts.
4. Do not publish `surgery.ready/completed/cancelled` or a new charge-source event until H-01b/c defines triggering transition, owner, payload and downstream fixture. The `surgery.requested` producer/charge-source ambiguity in §3 remains D02.

The technical schema must use the project's Vietnamese table/column naming and explicit mappings. Inbox/outbox columns, indexes, unique constraints and retention are to be written as a real migration plus database tests **after** their exact fields and operational policy are reviewed. No empty `V1`, placeholder business table or `ddl-auto=update` is an acceptable shortcut. Fresh DB and in-place upgrade paths are separate acceptance tests. `scripts/init-databases.sql` runs only when a PostgreSQL data directory is first initialized; existing environments need a safe, reviewed create-DB/migration step, not volume deletion.

### Foundation verification matrix

| Test ID | Required proof | Current state |
|---|---|---|
| `SUR-FND-01` | Architecture test forbids framework/JPA/AMQP imports in domain and infrastructure imports in application; context starts with env-backed config and no business listener. | SPECIFIED; code/test not created. |
| `SUR-FND-02` | Security tests: missing/expired/refresh/wrong-type JWT rejected; correct human role accepted only on its endpoint; default-deny and correlation/error-envelope checks. | SPECIFIED; code/test not created. |
| `SUR-FND-03` | Fresh and existing-DB Flyway migrations; ORM mapping round-trip and startup `validate`. | WAITING_SCHEMA and bootstrap owner. |
| `SUR-FND-04` | Concurrent same-event ingress produces one effect; identical replay no-op; same-ID/different-payload conflicts; rollback cannot leave `PROCESSED` inbox. | SPECIFIED; requires technical schema/implementation. |
| `SUR-FND-05` | Outbox rollback, confirm failure, crash after confirm, lease expiry/reclaim and stable event ID/payload across retries against PostgreSQL/RabbitMQ. | SPECIFIED; requires technical schema/implementation. |
| `SUR-FND-06` | Pending item survives restart, exact dependency resumes once, wrong target never applies, expiry alerts; reversed-lock concurrency does not deadlock. | WAITING_D04/D05/D11/D12 and business fixtures. |

**H-01d exit gate:** accept a concrete aggregate/DDL/constraint list and command keys after D02–D07; accept inbox/outbox/pending field and retention policy after D11/D12; run the corresponding DB/broker tests; obtain shared bootstrap integration evidence. This section completes only the policy-independent **design slice** of H-01d, not H-01d itself.

## 7. H-01b — use-case and transition inventory (not yet approved)

The [Surgery service design](../../ai/services/surgery.md) gives the state path `REQUESTED → PREOP_IN_PROGRESS → READY → SCHEDULED → IN_PROGRESS → COMPLETED`. It also requires **team and room/time confirmation before READY**, while `SCHEDULED` follows READY. Whether room/time is reserved before READY and confirmed again on scheduling is **D04/D05**. The rows below expose this ordering gap; they do not silently define the missing transition. `IN_PROGRESS_ABORTED` is a cancellation *stage* in the Billing contract, not an approved case state. Only one transaction may commit a case transition, its history and any approved outbox fact.

| Rule | From → command / input | Guard and ownership | To / durable record | Published fact | Gate |
|---|---|---|---|---|---|
| `SUR-BR-01` | No case → accept exact referral or create command | Producer, stable `surgeryRequestId`, patient/episode match and indication must be valid; one clinical intent yields at most one case. | `REQUESTED` + provenance/history only on first acceptance. | **TBD D02:** post-case planned-charge source; Surgery must not republish producer-owned `surgery.requested`. | D01/D02/D12 |
| `SUR-BR-02` | `REQUESTED` → begin pre-op preparation | Trigger/actor and whether this is explicit or derived are unspecified. | Proposed `PREOP_IN_PROGRESS` + history once; no state change is asserted until D05. | None approved. | D03/D05 |
| `SUR-BR-03` | Pre-op case → confirm or correct checklist evidence | Exact mandatory code, template version, source order/case identity and evidence freshness; external facts cannot be fabricated. | Case/checklist audit; readiness recomputed, not set from one item. | None approved unless all readiness guards and D05 transition hold. | D03/D05 |
| `SUR-BR-04` | Pre-op case → record or revoke consent | Authorized signer/witness, active version, reason and revocation authority; stale/revoked consent cannot satisfy readiness. | Consent audit/version and any derived readiness change, exact state path TBD. | `surgery.ready` invalidation/revision semantics TBD; do not emit an invented event. | D05/D06 |
| `SUR-BR-05` | Case → receive `financial.clearance.granted` | Only `purpose=SURGERY`; exact patient, episode, admission (if applicable), case and freshness must match. Payment for another purpose is not surgery clearance. | Financial guard evidence only; never READY by itself. | None approved. | D01/D12 |
| `SUR-BR-06` | Pre-op case → reserve room/time and assign team | Room master/active state, staff eligibility, non-overlap and reservation/lease policy must be authoritative. | Reservation/team audit; whether this precedes READY and how it maps to `SCHEDULED` TBD. | None approved. | D04/D05 |
| `SUR-BR-07` | Pre-op case → derive READY | Valid indication ∧ mandatory checklist ∧ active consent ∧ team ∧ room/time ∧ matching financial clearance; emergency exception only if explicitly approved. | `READY` + readiness snapshot/history only when D05 defines transition and revision. | Planned `surgery.ready` with exact snapshot/schedule relation TBD. | D03–D06/D12 |
| `SUR-BR-08` | `READY` → schedule/confirm | Room/time/team remain conflict-free and approved; re-check relationship to prior reservation and expiry. | Proposed `SCHEDULED` + schedule/history; no automatic scheduling from READY. | No event specified by the current catalog. | D04/D05 |
| `SUR-BR-09` | `SCHEDULED` → start | Re-evaluate all mandatory guards transactionally at start, including expiry/revocation; exact role and any override audit apply. | `IN_PROGRESS` + start/history only after guard passes. | No `surgery.started` contract is approved. | D05/D06/D12 |
| `SUR-BR-10` | `IN_PROGRESS` → complete | Authoritative result and actual performed item/price codes, time ordering, authorized actor; correction/version policy remains open. | `COMPLETED` + result/history and outbox atomically. | `surgery.completed`, exact v1 fixture pending. | D07/D02 |
| `SUR-BR-11` | Nonterminal case → cancel | Allowed stages/reasons/roles, partial performed items and resource-release policy depend on stage. Completed payment is never edited. | `CANCELLED` or another explicitly approved terminal outcome + audit; `IN_PROGRESS_ABORTED` is not presumed a state. | `surgery.cancelled`, payload/partial-abort semantics pending. | D07 |
| `SUR-BR-12` | Any case → get/list | RBAC and patient/department visibility; reads do not change workflow state. | Unchanged; no history/outbox mutation. | None. | H-01c query filter/visibility contract |

No row authorizes setting READY or START from a payment event, skipping consent/team, looking up an admission by patient, or treating a room reservation as a completed operation. Repeated commands need an approved business operation key and expected revision (H-01c/D05/D07); `eventId` alone is insufficient.

### Negative-test inventory (one minimum per rule)

| Rule | Required negative fixture/assertion |
|---|---|
| `SUR-BR-01` | Same intent delivered concurrently with two event IDs creates at most one case; mismatched patient/episode or conflicting payload is rejected without charge-source output. |
| `SUR-BR-02` | Repeated begin-prep cannot append duplicate state history; an unapproved trigger cannot advance state. |
| `SUR-BR-03` | Missing, stale, wrong-order or wrong-case mandatory evidence cannot complete the checklist or READY guard. |
| `SUR-BR-04` | Revoked, expired, wrong-signer or wrong-case consent cannot enable READY/START; revoke is audited. |
| `SUR-BR-05` | EXAM clearance, wrong case/patient/episode, expired or revoked SURGERY clearance cannot unlock surgery. |
| `SUR-BR-06` | Concurrent overlapping room or ineligible/inactive staff assignment yields one accepted reservation at most; no partial team/schedule write. |
| `SUR-BR-07` | Each of six missing readiness inputs blocks READY independently; an unapproved emergency flag cannot bypass a guard. |
| `SUR-BR-08` | Stale reservation or concurrent slot conflict cannot produce `SCHEDULED`; failing command leaves no ready/schedule fact. |
| `SUR-BR-09` | Consent/clearance revoked after scheduling blocks START; stale revision and unauthorized actor leave case/history unchanged. |
| `SUR-BR-10` | Duplicate completion and unknown item code cannot duplicate result/charge/report output or silently price an item at zero. |
| `SUR-BR-11` | Partial abort cannot be reported as pre-start cancellation; duplicate cancel does not duplicate adjustments/notifications; completed case is not silently rewritten. |
| `SUR-BR-12` | Unauthorized read is denied without leaking case details; cross-department visibility follows an approved scope policy rather than an inferred token claim; pagination/filtering cannot mutate data. |

The fixtures above are **test requirements, not test files or passing evidence**. H-01b closes only after every transition/guard/error and exact fixture is owner-approved and mapped to a runnable test.

## 8. H-01c — API/event contract manifest (draft inventory)

### Wire envelope and API boundary

- Domain events use the [care-finance envelope](../../ai/16-care-finance-integration-contracts.md): `eventId: UUID`, `eventType: string`, `version: integer`, `occurredAt: ISO-8601 instant`, `correlationId: correlation identifier`, `producer: service name`, `payload: object`. The canonical example renders `correlationId` as a UUID; exact transport validation still needs a shared fixture. Runtime routing keys are unversioned (`surgery.ready`, not `.v1`); the version is in the envelope. Architecture `.v1` labels describe schema version, not a second routing key.
- HTTP uses `/api/v1/surgery/**`, shared `ApiResponse`, stable UPPER_SNAKE error codes and correlation. Every shipped endpoint needs its matching `surgery.http` request. Exact Surgery DTO record fields, validation annotations, status/error codes and nullability are **not fixed** by this draft; the service endpoint table provides only route/method/role intent.
- Canonical IDs already have type `UUID`: `surgeryRequestId`, `surgeryCaseId`, `patientId`, `departmentId`, `admissionId`, `recordId`, `careEpisodeId`, `eventId`. `appointmentId` is a distinct UUID and is not a default replacement for `recordId`. `careEpisodeType` is `OUTPATIENT_VISIT | ADMISSION`. D01/D02 decide which reference combinations are required/forbidden on each Surgery command/event; nullable syntax in an architecture diagram does not approve a payload shape.

| Method and route (planned) | Roles from service design | Request/response still requiring H-01c decision | Rule |
|---|---|---|---|
| `POST /cases` | ADMIN, DOCTOR | Exact referral key, episode/reference validation, command idempotency key, `201 + Location` response and error codes. | `SUR-BR-01` |
| `GET /cases/{id}`, `GET /cases` | ADMIN, MANAGER, DOCTOR, NURSE | Visibility, filters, stable sort/page and readiness snapshot shape. | `SUR-BR-12` |
| `PUT /cases/{id}/checklist` | ADMIN, DOCTOR, NURSE | Template/item/version, evidence/source and expected revision. | `SUR-BR-03` |
| `POST /cases/{id}/consents` | ADMIN, DOCTOR, NURSE | Signer/witness proof, consent type/version, revoke path and role not yet defined. | `SUR-BR-04` |
| `PUT /cases/{id}/schedule` | ADMIN, MANAGER, DOCTOR | Room/team/interval/reservation identity, timezone, concurrency error and revision. | `SUR-BR-06/08` |
| `POST /cases/{id}/start` | ADMIN, DOCTOR | Recheck/revision, override authority/audit, response and error taxonomy. | `SUR-BR-09` |
| `POST /cases/{id}/complete` | ADMIN, DOCTOR | Result/actual-item representation, correction/version and repeat-command identity. | `SUR-BR-10` |
| `POST /cases/{id}/cancel` | ADMIN, DOCTOR | Stage/reason/partial work/resource release and financial-adjustment reference. | `SUR-BR-11` |

The role list is **not** approval for self-approval, consent revocation or emergency bypass. Every eventual controller must declare `@PreAuthorize`; patient-facing reads are not in the planned table.

### Producer/consumer manifest and fixture gate

| Fact / lookup | Owner → Surgery or Surgery → consumer | Known identity/purpose | Missing before implementation |
|---|---|---|---|
| `surgery.requested` | Clinical/Inpatient → Surgery; Billing also listed in Care–Billing | `surgeryRequestId`, exact origin ref, patient, department, procedure/indication. | D01/D02 producer per path, stable business key, exact payload/version and post-case `surgeryCaseId` charge-source handoff. No shared fixture yet. |
| `financial.clearance.granted` (`purpose=SURGERY`) | Billing → Surgery | `eventId`, `clearanceId`, patient, episode and exact `surgeryCaseId`; inpatient requires `admissionId`. | D01/D12 outpatient target, expiry/revocation and late-before-case handling; Billing producer and Surgery consumer fixture not shared. |
| Pre-op Lab/Pharmacy evidence | Lab/Pharmacy → Surgery, only if explicitly approved | Exact source order/case identity required; generic result is insufficient. | D03 event/lookup choice, version, freshness and correction fixture. |
| Patient/admission/staff/department/room lookups | Patient/Inpatient/Organization/room owner → Surgery | Patient existence and Organization staff/department lookup paths are in [identity contract](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md); service JWT + correlation required. | Patient producer readiness, admission relationship D12, room owner/eligibility D04; absence vs outage must stay distinct. |
| Planned charge-source fact/API | Surgery → Billing | Billing charge key `(sourceType=SURGERY, sourceId=surgeryCaseId, priceCode)` is already defined. | D02 event/API name, owner payload/version and timing **not defined**; do not relabel `surgery.requested`. |
| `surgery.ready` | Surgery → Inpatient, Notification | `surgeryCaseId`, planned `scheduleId`, `readinessSnapshotId`, `readyAt`. | D05 timing/revision after reserve-vs-schedule decision; D06 override representation; no fixture. |
| `surgery.completed` | Surgery → Inpatient, Billing, Report | `surgeryCaseId`, result/actual items and completion time; Billing reconciles actual item codes. | D01/D07 optional admission semantics, correction/partial-work policy, exact schema/consumer fixtures. |
| `surgery.cancelled` | Surgery → Inpatient, Billing, Notification, Report | Case, stage, reason, actor/time. | D07 partial abort, resource release, adjustment/replay policy and exact fixtures. |

For every row, H-01c needs producer DTO/schema + versioned example fixture, consumer deserialization/negative test, business idempotency key and correlation propagation evidence. Current status is **manifest only / no Surgery fixture or producer-consumer test**. A producer `DESIGN_READY` document is not `PRODUCER_READY`; no event above is claimed live. Unknown/malformed versions and missing target references follow bounded retry/DLQ policy, not silent success; `purpose` for another service on a shared topic must be classified without poisoning Surgery's queue, while malformed `purpose=SURGERY` is a contract error.

## 9. S-02a — aggregate alternatives and invariant placement (draft)

The following is a **model review aid**, not Java classes or physical DDL. Cross-service references remain UUIDs and evidence snapshots; their owners do not become Surgery child entities. A case-level optimistic version protects only one case, whereas room/team availability spans multiple cases.

| Concept | Candidate case-owned record/value | Invariant that must be enforced | Decision still needed |
|---|---|---|---|
| `SurgeryCase` root | `surgeryCaseId`, approved referral identity, exact care reference, procedure snapshot, state, version | One accepted intent creates at most one case; stale mutation cannot append history/outbox. | D01/D02 determine reference combination and unique business key. |
| Care reference | Immutable `patientId`, `departmentId`, selected episode type/ID, `recordId`/`admissionId` provenance | Never infer admission or Billing episode from patient/record presence. | D01/D12 determine which source proves patient–admission–department relation and later correction policy. |
| Checklist/template snapshot | Case-specific item identities, mandatory flag, template version, evidence source/version/time | Mandatory items and evidence must be valid for this case and procedure at evaluation time. | D03 decides template owner/codes, evidence binding, correction and expiry. |
| Consent history | Append-only signed/revoked evidence, signer/witness identity, type and effective time | Revocation must not erase prior signature; only current eligible consent satisfies readiness. | D06 decides accepted proof, roles, expiry and revoke API. |
| Team assignment | Case-local assignment and eligibility evidence | Team can satisfy case readiness only if exact required roles are eligible; shared staff/time conflict may require a separate resource constraint. | D04 decides eligibility and whether staff intervals are globally exclusive. |
| Room/time reservation | Case reference plus room/time interval and reservation state | Two cases cannot hold incompatible active reservations for the same resource; a case lock alone is insufficient. | D04/D05 decide room master, TTL, overlap/exclusion rule and READY↔SCHEDULED timing. |
| Result/performed items | Case-owned result revisions or immutable completion record | No completion without actual performed items/result provenance; corrections cannot silently rewrite previously published facts. | D07 decides partial-abort, correction and financial adjustment semantics. |
| Status/readiness history | Append-only transition, actor/time/reason/correlation and guard snapshot reference | Every committed transition has one durable audit entry; rollback has none. | D05/D07 decide re-ready/invalidation and cancellation stages. |

Two viable **storage boundaries** remain open. Option A puts all case-specific rows under the case transaction/version while a separate globally constrained reservation row protects shared room/time; this keeps case mutation atomic but requires a defined lock order. Option B makes a reservation an independently versioned aggregate with explicit confirmation/expiry messages; that may avoid long transactions but needs a state protocol and compensation not yet specified. Neither option allows a cross-service FK or a second authoritative room master. Choose after D04/D05, then write the actual table/constraint/repository mapping in H-01d and a reversed-concurrency test. Do not generate JPA entities from this table.

## 10. S-03f — fixture provenance and contract-test manifest

This is the **fixture gate ledger**, not a collection of self-authored fixtures. `MISSING` means no approved producer-owned file/hash/commit was found for the target Surgery version. A design JSON example in a Markdown contract is not a serialized producer fixture. A SHA-256 is recorded only after the fixture exists; never assign a hash to a hypothetical payload. A consumer test must deserialize the *same* file/version and assert both accepted and rejected effects before G1 can be called shared-tested.

| Contract / target version | Producer owner | Producer fixture path + SHA-256/commit | Surgery/other consumer test | Live evidence | Gate |
|---|---|---|---|---|---|
| `surgery.requested` / v1 proposed | Clinical/Inpatient (D02) | MISSING; producer path/version unconfirmed | MISSING Surgery decode/one-intent test; Billing charge path disputed | NONE | D01/D02/D11 |
| `financial.clearance.granted` / v1 design | Billing | MISSING Surgery-purpose producer fixture | MISSING Surgery exact-target/expiry/replay test | NONE for Surgery | D01/D12 |
| Pre-op evidence / version TBD | Lab/Pharmacy owner TBD by D03 | MISSING; event vs lookup undecided | MISSING source-order/case/freshness test | NONE | D03 |
| Planned charge-source bridge / version TBD | Surgery → Billing | MISSING; name/transport/fields undecided | MISSING Billing `surgeryCaseId` source-key test | NONE | D02 |
| `surgery.ready` / v1 proposed | Surgery | MISSING Surgery serialization/outbox fixture | MISSING Inpatient/Notification same-fixture tests | NONE | D05/D06 |
| `surgery.completed` / v1 proposed | Surgery | MISSING Surgery serialization/outbox fixture | MISSING Inpatient/Billing/Report same-fixture tests | NONE | D01/D07 |
| `surgery.cancelled` / v1 proposed | Surgery | MISSING Surgery serialization/outbox fixture | MISSING Inpatient/Billing/Notification/Report same-fixture tests | NONE | D07 |
| Patient/staff/department lookup / phase-1 path | Patient/Organization | Paths and shapes are in [identity contract](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md); no Surgery-specific producer fixture/hash recorded | MISSING Surgery contract-mock and absence-vs-outage tests | Producer readiness differs by endpoint; Surgery NONE | Patient handoff, D04 |

Required fixture-harness assertions, once the **producer-owned bytes** exist: `SUR-CON-01` additive optional field ignored without changing meaning; `SUR-CON-02` missing mandatory payload/target rejected without mutation; `SUR-CON-03` unsupported envelope version rejected/quarantined; `SUR-CON-04` wrong `producer`/`eventType`/routing combination rejected; `SUR-CON-05` correlation ID survives inbox → application → outbox; `SUR-CON-06` identical redelivery is no-op, same `eventId` with altered payload is conflict; `SUR-CON-07` shared-topic non-Surgery purpose is classified not-applicable but malformed Surgery-purpose target is a contract error. Each test must record fixture path, SHA-256 or source commit, producer test name, consumer test name and actual pass/skip evidence. None exists yet for Surgery, so **S-03f is prepared only; G1 remains unpassed**.

## 11. S-04c — referral idempotency/concurrency scenario set

There are **two distinct identities**: `eventId` deduplicates one delivery; the D02-approved referral business key deduplicates one clinical intent across deliveries and entry points. A canonical business fingerprint must be based on D02-approved invariant fields (origin, exact episode/procedure/indication/department as decided), excluding delivery metadata such as `eventId`, `occurredAt`, trace/correlation and broker retry count. The **exact field list and whether the key is globally `surgeryRequestId` or producer-qualified are still D02**, so no fingerprint or unique index is approved here.

| Scenario | Inputs / interleaving | Required outcome to test after D02/S-02/S-04a |
|---|---|---|
| `SUR-IDEM-01` | Same event bytes and `eventId` delivered twice. | One inbox effect, case, creation history and approved charge-source intent; retry has no new side effect. |
| `SUR-IDEM-02` | Same intent/key, two distinct `eventId`s, semantically identical business data. | Return/resolve the existing case; one case and at most one initial charge-source intent. Event dedupe alone cannot pass this test. |
| `SUR-IDEM-03` | Same intent/key, changed approved business field (e.g. procedure), whether sequential or concurrent. | Stable conflict; original case/history/outbox unchanged, never silently update or create a second case. |
| `SUR-IDEM-04` | Two different intents for one patient/episode. | Two cases may exist if the approved referral keys differ; patient/episode is not a uniqueness shortcut. |
| `SUR-IDEM-05` | HTTP command and event consumer race with the same approved referral key. | Unique DB constraint/transaction permits one winner; loser resolves identical retry or conflict by business fingerprint; no partial audit/outbox. |
| `SUR-IDEM-06` | Clinical and Inpatient paths refer to the same clinical intent. | Exactly one case **only if D02 supplies a shared stable key/mapping**; otherwise reject/pending contract mismatch instead of guessing by patient. |
| `SUR-IDEM-07` | Crash before commit, then retry; crash after commit before broker ACK, then retry. | Pre-commit leaves no case/inbox-processed/outbox; post-commit retry returns existing case without duplicate effect. |
| `SUR-IDEM-08` | Same `eventId`, altered bytes/fingerprint; or wrong patient/admission/department. | Contract conflict or exact-reference rejection with no case/charge output; never treat as benign replay. |

Executable acceptance requires PostgreSQL concurrent-first-insert tests (two independent transactions/barrier), HTTP/event cross-entry tests, unique-constraint assertions and actual inbox/history/outbox row counts. The returned HTTP status/body for an identical retry and the exact error code for a conflicting command are **H-01c/D02 decisions**, not filled in by this scenario table. These scenarios do not mark S-04c implemented or tested.

## 12. S-04d — create-case transaction and Billing boundary

This is a transaction design for the future accepted referral, not a new `surgery.requested` interpretation. Both the planned `POST /cases` and the inbound referral consumer must call the **same application in-port** after their adapter-specific authentication/envelope validation. The API cannot fabricate a referral origin or bypass the approved D02 business key. Surgery owns case/history/outbox; Billing owns charges, price, clearance and payment.

| Step | Proposed boundary | Failure/retry rule |
|---|---|---|
| Validate exact producer/referral and external references | Parse/validate before mutation. Any required Patient/Inpatient/Organization lookup is a resilient service call **before** holding a Surgery DB lock; confirmed absence differs from timeout/unavailable. | Missing/mismatched refs reject or pend only under D12 policy; no case, history or outbox is written. |
| Claim event identity for broker ingress | Inbox fingerprint/identity and business effect share one local transaction; HTTP entry has a separate D02-approved command key, not a fake `eventId`. | Same event/same payload is no-op; same event/changed payload conflicts; a rolled-back claim cannot block retry. |
| Claim referral business key | Database unique constraint on the approved intent key is authoritative under concurrent first insert. Compare D02-approved semantic fingerprint for a losing transaction. | Identical retry resolves to the existing case; conflicting retry has a stable conflict outcome. No patient-wide search or in-memory-only mutex. |
| Persist accepted case | Insert `REQUESTED` case plus one creation/provenance history record; keep exact episode/referral references and immutable snapshots permitted by contract. | Case and history commit together; a failed command cannot leave a visible partial case. |
| Record outward intent | If D02 approves a post-case charge-source fact, append it to the **same local transaction's outbox** with the new `surgeryCaseId`, approved price/item codes and correlation. Do not call Billing synchronously while holding the transaction. | Outbox insert failure rolls back case/history; broker/Billing outage after commit leaves a retryable outbox intent, not an unpaid case falsely marked paid. Event name/payload remain TBD. |
| Complete ingress | Mark broker inbox processed in the same commit as case/history/outbox. ACK only after commit. HTTP response uses H-01c-approved created/retry semantics. | Crash before commit has no effect; crash after commit before ACK/redelivery returns the same case without a second outward intent. |

`SUR-TX-01` asserts case/history/outbox/inbox row counts after successful first acceptance and an identical retry. `SUR-TX-02` injects failure at case/history/outbox write and proves total rollback. `SUR-TX-03` races HTTP with event ingress on two PostgreSQL connections. `SUR-TX-04` disconnects RabbitMQ/Billing after commit and proves durable retry without duplicate case or fabricated clearance. `SUR-TX-05` exercises confirmed reference absence versus timeout/5xx/malformed lookup response. No test may assert a concrete charge-source event until D02 fixes its producer/schema and Billing shares the same fixture. This is **S-04d design only**; no transaction code or test has run.

## 13. S-04e — read-model and query contract draft

The planned `GET /api/v1/surgery/cases/{id}` and `GET /api/v1/surgery/cases` are read-only and restricted to `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE` by the [Surgery endpoint table](../../ai/services/surgery.md). They return the standard `ApiResponse` envelope; a page uses `PageResult`, `page` is 0-based, `size` defaults to 20 and is at most 100 per [API conventions](../../ai/05-api-conventions.md). Application ports must not expose Spring `Pageable` or JPA entities.

| Concern | Safe baseline / proposed acceptance | Still open |
|---|---|---|
| Detail identity | Return Surgery-owned case ID, approved referral/episode references, case state and a version/readiness snapshot identifier when defined. Do not infer an admission from patient or fetch another service's DB to fill a response. | H-01c exact DTO/nullability; D01 episode combinations; D05 readiness/schedule revision. |
| Detail clinical content | Expose only authorized, necessary procedure/checklist/consent/result **status or snapshot**; raw evidence and sensitive notes need a role-specific policy. | Field-level visibility for DOCTOR/NURSE/MANAGER and approved result/consent DTOs. |
| List filters | Candidate filters are status, department and a bounded date range. `departmentId` is an exact UUID filter, not a name lookup; choose whether the date applies to creation or planned start **before** naming the wire parameter. | D05 schedule semantics, H-01c query names/defaults and authorized department scope. |
| Pagination/order | Stable sort must include a unique `surgeryCaseId` tie-breaker; invalid page/size/sort/range yields a validation error. Indexes follow the locked filters and physical DDL. | Approved primary sort/direction and maximum date-range policy. |
| Unscheduled cases | They must not silently disappear from a general list; how a schedule-date filter treats null planned time must be specified. | D05/query policy. |
| Consistency/availability | Reads use Surgery-local persisted state/snapshots; external Patient/Org/Report calls are not needed to construct every list row. No mutation, history or outbox on GET. | Whether a response advertises `asOf`/freshness is a later wire decision, not a real-time promise. |

`SUR-READ-01` covers 200/404 and unknown case without cross-service lookup. `SUR-READ-02` covers 401/403 and each approved role/department visibility fixture; no blanket cross-department denial is assumed before policy approval. `SUR-READ-03` covers page 0/20, max 100, invalid size/page and stable tie-break ordering for equal primary values. `SUR-READ-04` covers two episodes of the same patient without mixing cases. `SUR-READ-05` covers scheduled/unscheduled date filtering after its meaning is approved. `SUR-READ-06` asserts GET leaves case version, history and outbox untouched. No response DTO/controller or query index is implemented from this draft.

## 14. S-05a — checklist initialization and state-trigger test matrix

The [Surgery design](../../ai/services/surgery.md) requires mandatory pre-op items to be complete before READY, but it does not yet provide an approved procedure→template catalog, template owner/version, item codes or the command that changes `REQUESTED` to `PREOP_IN_PROGRESS`. D03 owns checklist content/evidence and D05 owns the state trigger. A candidate case checklist is an immutable snapshot of the **approved template version at initialization**; this principle must not be converted into columns/commands until those decisions and fixtures exist.

| Scenario | Input variation | Required assertion after D03/D05 approval |
|---|---|---|
| `SUR-CHK-01` | First preparation for a procedure with approved template version. | Create exactly its item set once, including mandatory flags and template provenance; transition to `PREOP_IN_PROGRESS` only through the approved trigger, with one history entry. |
| `SUR-CHK-02` | Retry or two concurrent initialization commands for the same case/template. | No duplicate items/history; result is idempotent or a stable conflict according to the approved operation key/revision. |
| `SUR-CHK-03` | Procedure has no approved template, unknown code or duplicate item code. | Reject/quarantine deterministically; do not create an empty checklist that makes READY vacuously true. |
| `SUR-CHK-04` | Template catalog publishes a newer version after the case was initialized. | Existing case retains its recorded version/items; migration/rebase, if allowed, is an explicit audited operation rather than silent mutation. |
| `SUR-CHK-05` | Procedure changes after some items were confirmed. | Old evidence is not silently reused for a different procedure; approved correction/rebase policy determines rollback/revalidation. |
| `SUR-CHK-06` | Mandatory item missing versus optional item missing. | Mandatory gap blocks readiness; optional gap alone does not, **only** after the approved template flags and evidence rules are applied. |
| `SUR-CHK-07` | Evidence belongs to another patient/case/source order or is stale/corrected. | No confirmation/READY from unrelated or invalid evidence; source-specific fixtures are required. |
| `SUR-CHK-08` | History/item/outbox write fails mid-initialization. | Whole local operation rolls back; retry can safely initialize once. |

Before coding, D03 must record procedure-code owner, template source, version and duplicate/correction policy, mandatory item codes, and exact external evidence source/reference/freshness fixture. D05 must define whether the first checklist action, an explicit begin-preop command or another fact enters `PREOP_IN_PROGRESS`, plus its role and audit. An empty/unknown template is **not** proof of completed preparation. This section is a test inventory only; it does not approve a checklist API or create a template table.
