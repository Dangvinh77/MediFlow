# 11 — surgery-service — Care & Finance V2 target candidate

**Owner:** Huy (`LQHuy0210`)

**Module:** `backend/surgery-service` — platform and initial pure-Java domain exist; business persistence/API/messaging are not implemented yet.

**Package / port / database:** `com.mediflow.surgery` / `8091` / `mediflow_surgery`

**Base path:** `/api/v1/surgery`

**Status (2026-09-28):** cross-service implementation candidate. Huy-delegated local V1 decisions permit internal code; unresolved wire/clinical/identity inputs still require owner acceptance. The executable backlog is [Huy plan §6](../../../superpowers/plans/2026-09-25-huy-surgery-pharmacy-report.md#surgery-backlog), with decisions in the [active handoff](../../../handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md). Do not scaffold the module again or treat all §14 rows as blockers for local persistence/tests.

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
local Flyway/reliability/contracts/tests; root module, DB/Compose and Gateway wiring are assigned
separately through the bootstrap handoff, not silently included in Huy's production scope.

## 12. Rollout

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
