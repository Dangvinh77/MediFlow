# 11 — surgery-service — Care & Finance V2 target candidate

**Owner:** Huy (`LQHuy0210`)

**Module:** `backend/surgery-service` — domain/persistence/reliability, scoped read/preop/cancel/draft APIs and gated grant intake exist. Internal readiness/finalize/START/COMPLETE orchestration now exists without production wiring; the full referral/authority/wire/API workflow remains open.

**Local implementation update — 2026-10-06:** explicit versioned checklist evidence, consent
authority and team-cardinality policies; seven-guard readiness engine; internal lifecycle commands
with receipt/revision fencing, sorted resource locks, local dependency re-read, committed denial
invalidation and immutable completion/result/release. These are not public APIs or approved
clinical defaults. The authority port has no production adapter. Remote preflight is before mutation
locks but currently inside the transaction; live wiring must separate preflight from the write
transaction and provide source-revision reconciliation. Additive V6 holds private READY/COMPLETED
intents with HELD-only database status and no broker dispatcher, plus exact ISO readiness timestamps
for new evidence. It does not approve outbound wire or backfill/rewrite historic data.

**Package / port / database:** `com.mediflow.surgery` / `8091` / `mediflow_surgery`

**Current financial authority implementation — 2026-10-06:** Billing's gated service-only current
clearance producer and Surgery's strict Feign adapter now exist. Internal READY/finalize/START
requires a fresh exact-context read before mutation locks, rechecks observation age after resource
waits and denies revoked/refunded/expired authority despite a historical grant. Observation freshness
does not create a 30-second schedule expiry; persisted business validity uses actual grant expiry.
The combined clinical/policy authority adapter, preflight transaction separation and distributed
race fence remain implementation tasks. No new approval from another code owner is required for
those engineering dependencies under the current user override; clinical/legal catalogue data is
still not invented. Canonical contract and verification: [SURGERY-BILLING](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md),
[execution ledger](../../../superpowers/plans/2026-10-05-huy-50-task-execution.md#financial-authority-both-sides).

**Base path:** `/api/v1/surgery`

**Status (2026-10-05):** partially implemented cross-service candidate, not a live clinical workflow. Huy-delegated local V1 decisions permit internal code; unresolved wire/clinical/identity inputs still require owner acceptance. The executable backlog is [Huy plan §6](../../../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md#surgery-backlog), with decisions in the [active handoff](../../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). Do not scaffold the module again or treat all §14 rows as blockers for local persistence/tests.

**Example maturity:** SQL/DTO snippets below remain candidate illustrations, not copy-ready migrations or approved wire contracts. They omit revision/receipt/resource-lock details added to the current plan, use naming requiring correction, and retain future override shapes. Local V1 has **no override implementation/table/endpoint**. S-02/S-03/S-04 must finalize each affected schema/API/contract before implementing that slice; no shared contract is approved by this plan update.

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-INPATIENT-SURGERY-01`](../../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [Historical working draft / scenario inventory](../10-surgery.md)

Surgery owns case, checklist, consent, schedule, team, readiness snapshot, result and status history.
It stores bare UUID references to admission/record/patient/staff/department and never owns their
aggregates or Billing amounts.

## 2. Episode and request identity

- `surgeryRequestId` is the producer idempotency key and creates at most one case.
- Inpatient case: `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`, `recordId` optional.
- Outpatient case: `careEpisodeType=OUTPATIENT_VISIT`, exact selected `appointmentId` when appointment-backed, otherwise `recordId`; `admissionId` null. A distinct recordId remains clinical context and does not replace an already selected episode. This is the Huy-local choice following CARE-BILLING; producer mapping/shared fixtures remain open.
- Patient and department must match the request forever; no lookup by latest admission/record.

## 3. Database — `V1__surgery_core.sql`

**Implemented grant-only slice (2026-10-05):** live V1 remains unchanged. Additive
`V2__surgery_financial_authority.sql` stores the actual Billing V1 grant fields in
`surgery_financial_clearance`, with case FK, exact episode/patient and immutable grant fingerprint.
Grant/expiry ISO instants preserve nanoseconds. Envelope event dedupe uses the existing Surgery inbox;
immutable clearance identity is separately checked. Early grants remain durable pending; wrong target
quarantines. This is grant evidence only: no override, revocation/supersession assumption or automatic
READY/START. Full activation still requires the next authority/readiness/lifecycle slices.

Surgery-owned database identifiers follow English snake_case; Java/HTTP DTO and Surgery-owned event payload fields follow English camelCase under the Huy-scoped exception in `docs/ai/08`. Class names/URLs remain English. Cross-service keys produced by another owner follow their accepted canonical version and must not be silently translated. This is a V2 target candidate, not the exact already-implemented V1 schema: compare each proposed field/table against the live Surgery migration and contract gate before implementation. S-02.2 has completed the V1 mapping; S-04.6 must preserve these English wire names when business endpoints are added.

```sql
CREATE TABLE surgery_case (
    surgery_case_id UUID PRIMARY KEY,
    surgery_request_id UUID NOT NULL UNIQUE,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    admission_id UUID,
    record_id UUID,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    procedure_code VARCHAR(64) NOT NULL,
    indication TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(32) NOT NULL,
    financial_clearance_id UUID,
    emergency_override_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    requested_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_episode CHECK (
        (care_episode_type = 'ADMISSION' AND admission_id IS NOT NULL
         AND care_episode_id = admission_id)
        OR (care_episode_type = 'OUTPATIENT_VISIT' AND record_id IS NOT NULL
            AND admission_id IS NULL AND care_episode_id = record_id)
    ),
    CONSTRAINT ck_surgery_priority CHECK (priority IN ('ROUTINE', 'URGENT', 'EMERGENCY')),
    CONSTRAINT ck_surgery_status CHECK (status IN
        ('REQUESTED', 'PREOP_IN_PROGRESS', 'READY', 'SCHEDULED',
         'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX idx_surgery_schedule_board
    ON surgery_case(department_id, status, requested_at);

CREATE TABLE preop_checklist_item (
    checklist_item_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    item_code VARCHAR(64) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    evidence_reference VARCHAR(255),
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_preop_item UNIQUE(surgery_case_id, item_code),
    CONSTRAINT ck_preop_status CHECK (status IN ('PENDING', 'SATISFIED', 'NOT_APPLICABLE', 'FAILED'))
);

CREATE TABLE surgery_consent (
    consent_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    consent_type VARCHAR(40) NOT NULL,
    signer_reference UUID NOT NULL,
    witness_staff_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    CONSTRAINT ck_consent_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_consent_revoke CHECK
        (status = 'ACTIVE' OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL))
);
CREATE UNIQUE INDEX uq_active_surgery_consent
    ON surgery_consent(surgery_case_id, consent_type) WHERE status = 'ACTIVE';

CREATE TABLE surgery_schedule (
    schedule_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES surgery_case(surgery_case_id),
    room_reference VARCHAR(64) NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    scheduled_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_schedule_time CHECK (planned_end > planned_start),
    CONSTRAINT ck_surgery_schedule_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED'))
);
CREATE INDEX idx_surgery_room_time ON surgery_schedule(room_reference, planned_start, planned_end);

CREATE TABLE surgery_team_assignment (
    team_member_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    staff_id UUID NOT NULL,
    team_role VARCHAR(32) NOT NULL,
    active_from TIMESTAMPTZ NOT NULL,
    active_to TIMESTAMPTZ,
    CONSTRAINT uq_surgery_team_member UNIQUE(surgery_case_id, staff_id, team_role),
    CONSTRAINT ck_team_interval CHECK (active_to IS NULL OR active_to > active_from)
);

CREATE TABLE surgery_result (
    result_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES surgery_case(surgery_case_id),
    performed_method TEXT NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    complications_summary TEXT,
    performed_items JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    CONSTRAINT ck_surgery_result_time CHECK (completed_at > started_at)
);

CREATE TABLE surgery_readiness_snapshot (
    readiness_snapshot_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    indication_valid BOOLEAN NOT NULL,
    checklist_complete BOOLEAN NOT NULL,
    consent_active BOOLEAN NOT NULL,
    team_assigned BOOLEAN NOT NULL,
    schedule_confirmed BOOLEAN NOT NULL,
    financial_guard_satisfied BOOLEAN NOT NULL,
    emergency_override_used BOOLEAN NOT NULL DEFAULT FALSE,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE surgery_financial_clearance (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    admission_id UUID,
    patient_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE surgery_override_approval (
    override_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    override_type VARCHAR(32) NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_override_type CHECK
        (override_type = 'FINANCIAL_EMERGENCY')
);

CREATE TABLE surgery_status_history (
    history_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES surgery_case(surgery_case_id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id UUID,
    reason TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE surgery_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE surgery_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_surgery_outbox_unpublished
    ON surgery_outbox(occurred_at) WHERE published_at IS NULL;

ALTER TABLE surgery_case
    ADD CONSTRAINT fk_surgery_clearance
        FOREIGN KEY (financial_clearance_id)
        REFERENCES surgery_financial_clearance(clearance_id),
    ADD CONSTRAINT fk_surgery_override
        FOREIGN KEY (emergency_override_id)
        REFERENCES surgery_override_approval(override_id);
```

## 4. State machine and readiness

```text
REQUESTED → PREOP_IN_PROGRESS → READY → SCHEDULED → IN_PROGRESS → COMPLETED
REQUESTED | PREOP_IN_PROGRESS | READY | SCHEDULED → CANCELLED
```

READY and START both require:

```text
valid indication
AND every mandatory checklist item satisfied
AND active SURGERY consent AND active ANESTHESIA consent
AND eligible team assigned
AND confirmed room/time
AND matching, valid SURGERY clearance
```

Emergency override is disabled in local V1, including for ADMIN. A future approved version may
replace only the financial guard, never consent/checklist/team/schedule. Candidate override snippets
below are future-design material, not authorization to implement a V1 bypass.

The schedule row may be prepared and confirmed while the case is `PREOP_IN_PROGRESS`; that does not
change the case status. Readiness evaluation then moves the case to `READY`. The explicit scheduling
transition locks the already-confirmed slot and moves `READY → SCHEDULED`, avoiding a circular guard.

```java
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
public enum SurgeryPriority { ROUTINE, URGENT, EMERGENCY }
public enum SurgeryStatus {
    REQUESTED, PREOP_IN_PROGRESS, READY, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
}
public enum ChecklistItemStatus { PENDING, SATISFIED, NOT_APPLICABLE, FAILED }
public enum ConsentStatus { ACTIVE, REVOKED }
public enum CancellationStage { BEFORE_PREOP, AFTER_PREOP, BEFORE_START, IN_PROGRESS_ABORTED }
```

## 5. Ports and DTOs

Implemented local lifecycle boundary (2026-10-07): `SurgeryUnitOfWorkPort` separates read/preflight
and atomic write phases without application imports of adapter transaction machinery. Remote readiness
observation, live Billing and result-code verification execute with any calling transaction suspended.
Committed receipts are probed before external I/O without inserting PENDING; claim/conflict/replay is
checked again inside the write. Owned dependencies and Clock are rechecked after case/sorted resource
locks. Three fresh transactions at most retry only real PostgreSQL `55P03`/`40P01`, each with a one-second
lock timeout and five-second transaction timeout. Exhaustion is `SURGERY_COMMAND_BUSY` (409).
Receipt V2 adds structured reasons, preserving V1 decoding and original timestamps/identities.

POST `/api/v1/surgery/cases/{id}/readiness/evaluate`, `/schedule/finalize`, `/start`, `/complete` have
strict English DTOs, expected case/schedule revisions, mandatory idempotency and JWT-derived actor.
ADMIN/DOCTOR are allowed clinical commands; MANAGER additionally may finalize. Unknown authority,
override or amount fields are rejected. No V1 financial bypass is introduced. HTTP registration needs
both business and `mediflow.surgery.lifecycle.api.enabled` flags, both false, plus explicit real use-case
beans. The implementation does NOT install positive policy doubles or activate missing clinical,
legal, source-fence or downstream contracts. HELD V7 bytes remain unreleased. Verification and local
close criteria are in [the ten-ID ledger](../../../superpowers/plans/2026-10-07-surgery-closeable-batch.md).

Organization authority invalidation slice (2026-10-06): `ReceiveSurgeryAuthorityChangeUseCase`,
`QuerySurgeryAuthorityInvalidationsUseCase`, `ApplySurgeryAuthorityInvalidationUseCase` and
`RecordSurgeryAuthorityInvalidationRetryUseCase` consume the existing canonical V1 event through
`SurgeryAuthorityChangeWirePort`. `SurgeryAuthorityInvalidationPort` captures immutable hints and
jobs in the inbox transaction. Additive V5 pins event/case/readinessSnapshot/schedule/revision;
semantic replay creates no new work, same-revision changed content conflicts. Case lock precedes
job lock; a worker re-reads pins and only pre-start states may use shared invalidation/exact release.
Case/audit/release/job completion are atomic; retry is separate and cannot reopen a completed job.
Batch 20 (1..100), 5-second transaction timeout, durable 5..300-second backoff, three independently
disabled business/global-consumer/Organization-consumer gates. No REST under resource locks,
eligibility grant, inferred clinical policy, auto-READY/START or invented notification wire.
Future READY/finalize/START still must reconcile current source revisions/fresh authority after
waits; this job protocol alone is not a distributed authorization fence. Verification and exact
open acceptance are in the [execution ledger](../../../superpowers/plans/2026-10-05-huy-50-task-execution.md).

Implemented internal expiry slice (2026-10-05): `QueryExpiredSurgeryReadinessUseCase`,
`ExpireSurgeryReadinessUseCase`, `RecordSurgeryReadinessExpiryRetryUseCase`, and the driven
`SurgeryReadinessExpiryPort` use an exact `(surgeryCaseId, readinessSnapshotId)` candidate.
Query is read-only, bounded 1..100; each mutation/retry is independently committed with a 5-second
transaction timeout. Worker registration requires both business and explicit expiry gates;
defaults are false. Only persisted, non-null `validUntil` is considered, with `now >= validUntil`.
After case lock, re-read snapshot identity/state/Clock: READY/SCHEDULED becomes PREOP with immutable
history and exact pinned booking release in the same transaction. Started, terminal and replaced
winners remain unchanged. Additive V4 stores retry count, next attempt, safe failure code and time;
5..300-second backoff is operational recovery, not clinical validity policy. Stale retries cannot
delay a new snapshot. No expiry HTTP state setter or guessed notification event is added. Full
invalidation outbound acceptance and real READY/finalize/START orchestration remain open.

```java
public interface ManageSurgeryCaseUseCase {
    SurgeryCaseDTO create(CreateSurgeryCaseRequest request);
    SurgeryCaseDTO confirmChecklist(UUID caseId, ConfirmChecklistRequest request);
    SurgeryCaseDTO recordConsent(UUID caseId, RecordConsentRequest request);
    SurgeryCaseDTO schedule(UUID caseId, ScheduleSurgeryRequest request);
    SurgeryCaseDTO evaluateReadiness(UUID caseId);
    SurgeryCaseDTO start(UUID caseId, StartSurgeryRequest request);
    SurgeryCaseDTO complete(UUID caseId, CompleteSurgeryRequest request);
    SurgeryCaseDTO cancel(UUID caseId, CancelSurgeryRequest request);
}
public interface OrganizationIdentityPort {
    StaffIdentity requireEligible(UUID staffId, String teamRole);
    DepartmentIdentity requireActiveDepartment(UUID departmentId);
}
public interface SurgeryOutboxPort { void append(DomainEventEnvelope<?> event); }

public record CreateSurgeryCaseRequest(
    @NotNull UUID surgeryRequestId,
    @NotNull CareEpisodeType careEpisodeType,
    @NotNull UUID careEpisodeId,
    UUID admissionId, UUID recordId,
    @NotNull UUID patientId, @NotNull UUID departmentId,
    @NotNull UUID requestedBy,
    @NotBlank @Size(max = 64) String procedureCode,
    @NotBlank @Size(max = 4000) String indication,
    @NotNull SurgeryPriority priority,
    @NotNull Instant requestedAt
) {}

public record FinancialClearanceCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    UUID admissionId, UUID surgeryCaseId,
    BigDecimal amount, String currency, String paymentMethod,
    Instant expiresAt, boolean emergencyOverride
) {}
```

Schedule/team/result DTOs carry exact UUIDs, validated time intervals and non-empty performed item
codes. Actor IDs come from verified claims, not request bodies where the acting user is implicit.

## 6. Application algorithms

### Create from request

**Internal LOCAL creation core — 2026-10-07:** channel-neutral `CreateSurgeryCaseUseCase` now has
mandatory creation authority authorization/observation ports, without a production provider or
referral driving adapter. A follow-up default-off HTTP adapter is now implemented (see below).
V8 globally fences request UUID + deterministic clinical-intent
fingerprint; no actor/channel/delivery identity splits one business request into multiple cases.
Replay reauthorizes and returns original IDs/nanosecond time without repeating lookups or charge
capture. External preflight suspends caller transactions; bounded fresh writes serialize the request,
check proof validity after waiting, re-read exact approved template and atomically create case,
PENDING checklist snapshot/items, initial histories, canonical V7 HELD charge bytes and receipt.
Unknown historical cases are not automatically adopted/charged. No catalogue/legal policy/default
permission is fabricated; actual referral/requester/source-fence/API/consumer acceptance stays open.
See [ten execution checks and evidence](../../../superpowers/plans/2026-10-07-surgery-creation-batch.md).

**Creation HTTP follow-up — 2026-10-07:** explicit wiring requires business and
`mediflow.surgery.creation.api.enabled` flags plus all mandatory ports; missing real authority
fails startup. Both flags stay false. POST `/api/v1/surgery/cases` uses the source fields illustrated
in §5 plus positive `templateRevision` and 1..100 distinct `plannedItems[{itemCode,priceCode,quantity}]`.
It rejects unknown top-level/nested fields, wrong admission episode and quantities outside 15 integer/
4 fractional digits. ADMIN/DOCTOR require a verified account and signed staff recorder, independent
of the untrusted source `requestedBy`; actual delegation and relationship policy remain the authority's
responsibility. No body value supplies approval. Header `Idempotency-Key` must equal canonical lowercase
`surgeryRequestId`; first response is 201 with Location, matching authorized replay 200 with the same
Location and original `requestId/case/checklist/createdAt/replayed` receipt. There is no current-state
or clinical narrative in the receipt. Error codes/correlation remain stable; 404/422/500/503 messages
are redacted. Direct HTTP/PG validation and internal SYSTEM contention are not shared referral wire or
Gateway approval. [Ten-check verification](../../../superpowers/plans/2026-10-07-surgery-creation-api-batch.md).

1. Claim `surgery.requested` event or command idempotency key.
2. Validate one exact episode mapping, patient, department and requester eligibility.
3. Create one case plus configured mandatory checklist rows.
4. Append status history and case/request charge fact in the same transaction.

### Evaluate readiness

1. Lock case, checklist, consent and schedule.
2. Check team eligibility snapshots and overlapping room/team schedules.
3. Match valid, non-expired/non-revoked clearance by exact case, patient and episode; no V1 override.
4. Persist immutable readiness snapshot.
5. Move to READY and append `surgery.ready` only when every guard is true.

### Start/complete/cancel

- START re-evaluates all guards and moves only `SCHEDULED → IN_PROGRESS`.
- COMPLETE inserts one result, records actual item/price codes and appends `surgery.completed`.
- CANCEL is pre-start only, derives stage from persisted state and requires a reason; it preserves charges/payments and appends `surgery.cancelled` so
  Billing applies adjustment policy.

## 7. REST endpoints and roles

Use the endpoint table in `docs/ai/services/surgery.md`. GET is available to
`ADMIN, MANAGER, DOCTOR, NURSE`; clinical commands use `ADMIN, DOCTOR`, checklist permits NURSE,
and scheduling additionally permits MANAGER. Every method has explicit `@PreAuthorize`.

## 8. Events

**Current producer contract — 2026-10-07:** post-case planned charge uses `surgery.case.created`;
actual performed-charge reconciliation uses `surgery.completed`. Both have nested envelope
`version=1`, producer `surgery-service`, exchange `mediflow.events`, routing key equal to eventType
(no `.v1` suffix). This supersedes the candidate's unresolved post-case naming, not live rollout.
The canonical [SURGERY-BILLING contract](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
and actual producer fixtures, including planned/performed differences and unknown price codes,
govern exact fields. Private V6 intents are not wire events; V7 capture remains HELD.

Publish `surgery.ready`, `surgery.completed`, `surgery.cancelled` with the exact fields from the
care-finance contracts. Subscribe to `surgery.requested` and `financial.clearance.granted` with
`purpose=SURGERY`. A general Lab result cannot satisfy a checklist item unless it carries explicit
case/order correlation accepted by a later versioned contract.

```java
public record SurgeryReadyPayload(
    UUID surgeryCaseId, UUID admissionId, UUID recordId, UUID patientId,
    UUID scheduleId, UUID readinessSnapshotId, boolean emergencyOverrideUsed,
    Instant readyAt
) {}
public record SurgeryCompletedPayload(
    UUID surgeryCaseId, UUID admissionId, UUID recordId, UUID patientId,
    UUID departmentId, UUID resultId, List<PerformedItem> performedItems,
    Instant startedAt, Instant completedAt, String complicationsCategory
) {}
public record SurgeryCancelledPayload(
    UUID surgeryCaseId, UUID admissionId, UUID recordId, UUID patientId,
    String cancellationStage, String reason, UUID cancelledBy, Instant cancelledAt
) {}
public record PerformedItem(
    @NotBlank @Size(max = 64) String itemCode,
    @NotBlank @Size(max = 64) String priceCode,
    @Positive BigDecimal quantity
) {}
```

`PerformedItem` contains non-empty `itemCode`, `priceCode` and positive quantity; Billing resolves
the authoritative amount. Unknown versions, missing canonical IDs and mismatched episode targets use
bounded retry then DLQ without changing local state.

## 9. Concurrency and error codes

- Optimistic version protects case transitions; start/complete use row lock.
- Schedule checks reject room or active team-member interval overlap.
- Unique request/result/consent constraints enforce command idempotency.
- Inbox claim and side effect are one transaction; aggregate and outbox are one transaction.

Key codes: `SURGERY_CASE_NOT_FOUND` (404), `SURGERY_DUPLICATE_REQUEST` (409),
`SURGERY_INVALID_TRANSITION` (422), `SURGERY_NOT_READY` (422),
`SURGERY_CLEARANCE_MISMATCH` (422), `SURGERY_SCHEDULE_CONFLICT` (409),
`SURGERY_STAFF_INELIGIBLE` (422), `SURGERY_UPSTREAM_UNAVAILABLE` (503).

## 10. Required tests

| Rule | Required test |
|---|---|
| duplicate request creates one case | `create_duplicateRequest_singleCase` |
| one missing guard blocks ready | `evaluateReadiness_eachMissingGuard_rejects` |
| V1 rejects every financial override/bypass | `evaluateReadiness_overrideDisabled_rejects` and `start_missingClearance_evenAdmin_rejects` |
| wrong case/admission clearance rejected | `onClearance_wrongTarget_rejects` |
| room/team conflicts rejected | `schedule_overlappingResource_conflicts` |
| start rechecks readiness | `start_guardChangedAfterReady_rejects` |
| completion publishes actual items once | `complete_duplicateCommand_singleResultAndEvent` |
| cancellation is audit preserving | `cancel_paidCase_keepsLedgerReferences` |
| Organization outage is not ineligible | `schedule_staffLookupOutage_returns503` |
| event fixtures round-trip | `surgeryFixtures_roundTripAllConsumers` |

## 11. Scaffold map

The module, nested `AGENTS.md`, security/correlation and configuration already exist. Follow the
standard blueprint for remaining models/application/driving adapters/driven adapters. Huy implements
local Flyway/reliability/contracts/tests. Root module and DB/Compose bootstrap were integrated by
their shared owner. Gateway dependency implementation was separately user-assigned; packaged runtime
acceptance passed 2026-10-05 and the bootstrap handoff was retired. This does not expand shared scope.

## 12. Rollout

Current additive slice (2026-10-05): GET detail/list and PUT draft schedule are implemented with
exact Gateway roles, authoritative departmental read scope, redacted immutable views, UTC bounded
pagination and transactional reschedule invalidation/old-booking release. GET snapshot validity is
cached evidence, not a new READY/START decision. Mutation DTOs reject unknown actor/ready/amount
fields. Grant intake now has a dual-gated listener, durable pending worker and dedicated DLQ using
Billing's actual fixtures. No referral/create, approved Surgery outcome delivery, clinical policy,
required team composition or finalization/START/COMPLETE is silently enabled. README documents the
module Dockerfile and explicit packaged-app runtime-acceptance profile. Exact current evidence is
in the fixed 50-item execution ledger; historical local test counts do not imply whole-flow acceptance.

Keep existing `mediflow.features.surgery.enabled`, `mediflow.surgery.messaging.producer.enabled`
and `mediflow.surgery.messaging.consumers.enabled` false by default. Implement core persistence and
reliability before real create/read workflows. Enable each integration only after its contract/fixture
and local tests pass; flags need runtime enforcement, not only property binding. Gateway activation
requires health/role tests and assigned-owner changes.

## 13. Definition of Done

The P4 Docker path request → preop → checklist/two consents → clearance → draft schedule → READY →
finalize → start → complete, plus pre-start cancellation, must pass with duplicate delivery, resource
conflicts/overrun, expired/revoked clearance and rejected override attempts. Local module tests do
not substitute for same-version downstream fixtures and actual cross-service/Gateway verification.

## 14. Owner decision gate

Huy-local choices are already recorded by delegation: exact selected episode, immutable checklist
templates, two typed consents, external room authority, named team roles, no V1 override,
pre-start-only cancellation and code/quantity-only items. These permit internal implementation;
they do not approve clinical mandatory sets/signers, Organization eligibility/room lookups, Billing
catalogue/clearance, referral/reference registration or event consumer semantics owned elsewhere.

Close the corresponding handoff rows and update canonical specs with shared producer/consumer
fixtures before enabling each integration. The file remains a cross-service `candidate` until its
required owner inputs and example-schema gaps are resolved. Do not reopen Huy-local choices as
approval requests or block unrelated local tasks while one contract remains open.
