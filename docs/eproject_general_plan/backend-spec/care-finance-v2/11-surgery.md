# 11 — surgery-service — Care & Finance V2 target candidate

**Owner:** Huy (`LQHuy0210`)

**Planned module:** `backend/surgery-service`

**Package / port / database:** `com.mediflow.surgery` / `8091` / `mediflow_surgery`

**Base path:** `/api/v1/surgery`

**Status:** detailed implementation candidate; scaffold is blocked until the decision gate in §14 is accepted

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-INPATIENT-SURGERY-01`](../../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [CURRENT working draft](../10-surgery.md)

Surgery owns case, checklist, consent, schedule, team, readiness snapshot, result and status history.
It stores bare UUID references to admission/record/patient/staff/department and never owns their
aggregates or Billing amounts.

## 2. Episode and request identity

- `surgeryRequestId` is the producer idempotency key and creates at most one case.
- Inpatient case: `careEpisodeType=ADMISSION`, `careEpisodeId=admissionId`, `recordId` optional.
- Outpatient case: `careEpisodeType=OUTPATIENT_VISIT`, `careEpisodeId=recordId`, `admissionId` null.
- Patient and department must match the request forever; no lookup by latest admission/record.

## 3. Database — `V1__surgery_core.sql`

Database identifiers follow the project Vietnamese snake_case rule; Java/JSON remains English.

```sql
CREATE TABLE ca_phau_thuat (
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
    ON ca_phau_thuat(department_id, status, requested_at);

CREATE TABLE muc_kiem_tra_tien_phau (
    checklist_item_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
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

CREATE TABLE dong_y_phau_thuat (
    consent_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
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
    ON dong_y_phau_thuat(surgery_case_id, consent_type) WHERE status = 'ACTIVE';

CREATE TABLE lich_phau_thuat (
    schedule_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES ca_phau_thuat(surgery_case_id),
    room_reference VARCHAR(64) NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    scheduled_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_schedule_time CHECK (planned_end > planned_start),
    CONSTRAINT ck_surgery_schedule_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED'))
);
CREATE INDEX idx_surgery_room_time ON lich_phau_thuat(room_reference, planned_start, planned_end);

CREATE TABLE thanh_vien_ekip_phau_thuat (
    team_member_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
    staff_id UUID NOT NULL,
    team_role VARCHAR(32) NOT NULL,
    active_from TIMESTAMPTZ NOT NULL,
    active_to TIMESTAMPTZ,
    CONSTRAINT uq_surgery_team_member UNIQUE(surgery_case_id, staff_id, team_role),
    CONSTRAINT ck_team_interval CHECK (active_to IS NULL OR active_to > active_from)
);

CREATE TABLE ket_qua_phau_thuat (
    result_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL UNIQUE REFERENCES ca_phau_thuat(surgery_case_id),
    performed_method TEXT NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    complications_summary TEXT,
    performed_items JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    CONSTRAINT ck_surgery_result_time CHECK (completed_at > started_at)
);

CREATE TABLE anh_chup_san_sang_phau_thuat (
    readiness_snapshot_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
    indication_valid BOOLEAN NOT NULL,
    checklist_complete BOOLEAN NOT NULL,
    consent_active BOOLEAN NOT NULL,
    team_assigned BOOLEAN NOT NULL,
    schedule_confirmed BOOLEAN NOT NULL,
    financial_guard_satisfied BOOLEAN NOT NULL,
    emergency_override_used BOOLEAN NOT NULL DEFAULT FALSE,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE xac_nhan_tai_chinh_phau_thuat (
    clearance_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
    admission_id UUID,
    patient_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE phe_duyet_ngoai_le_phau_thuat (
    override_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
    override_type VARCHAR(32) NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_surgery_override_type CHECK
        (override_type = 'FINANCIAL_EMERGENCY')
);

CREATE TABLE lich_su_trang_thai_phau_thuat (
    history_id UUID PRIMARY KEY,
    surgery_case_id UUID NOT NULL REFERENCES ca_phau_thuat(surgery_case_id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id UUID,
    reason TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE su_kien_da_xu_ly_phau_thuat (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE su_kien_outbox_phau_thuat (
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
    ON su_kien_outbox_phau_thuat(occurred_at) WHERE published_at IS NULL;

ALTER TABLE ca_phau_thuat
    ADD CONSTRAINT fk_surgery_clearance
        FOREIGN KEY (financial_clearance_id)
        REFERENCES xac_nhan_tai_chinh_phau_thuat(clearance_id),
    ADD CONSTRAINT fk_surgery_override
        FOREIGN KEY (emergency_override_id)
        REFERENCES phe_duyet_ngoai_le_phau_thuat(override_id);
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
AND active consent
AND eligible team assigned
AND confirmed room/time
AND matching SURGERY clearance or audited financial emergency override
```

Emergency override may replace only the financial guard. It never fabricates consent, checklist,
team or schedule readiness.

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
3. Match non-expired clearance by case, patient and episode, or validate financial override.
4. Persist immutable readiness snapshot.
5. Move to READY and append `surgery.ready` only when every guard is true.

### Start/complete/cancel

- START re-evaluates all guards and moves only `SCHEDULED → IN_PROGRESS`.
- COMPLETE inserts one result, records actual item/price codes and appends `surgery.completed`.
- CANCEL requires stage and reason; it preserves charges/payments and appends `surgery.cancelled` so
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
| override bypasses finance only | `evaluateReadiness_overrideCannotBypassConsent` |
| wrong case/admission clearance rejected | `onClearance_wrongTarget_rejects` |
| room/team conflicts rejected | `schedule_overlappingResource_conflicts` |
| start rechecks readiness | `start_guardChangedAfterReady_rejects` |
| completion publishes actual items once | `complete_duplicateCommand_singleResultAndEvent` |
| cancellation is audit preserving | `cancel_paidCase_keepsLedgerReferences` |
| Organization outage is not ineligible | `schedule_staffLookupOutage_returns503` |
| event fixtures round-trip | `surgeryFixtures_roundTripAllConsumers` |

## 11. Scaffold map

Create the module only after §14 acceptance. Follow the standard clean architecture package map,
add root Maven module, DB/Compose/Eureka configuration, Gateway feature route, nested `AGENTS.md`,
security/correlation, Flyway, Rabbit topology, contract fixtures and Testcontainers persistence tests.

## 12. Rollout

Scaffold technical foundation behind `mediflow.features.surgery=false`; implement request and local
read model first; enable Billing and Inpatient integrations only when fixtures pass. Gateway route
stays disabled until health and role tests are green.

## 13. Definition of Done

The P4 Docker path request → checklist/consent → clearance → schedule → start → complete/cancel must
pass with duplicate delivery, resource conflicts, expired clearance and emergency override covered.

## 14. Owner decision gate

Before production coding, Huy must accept this candidate's explicit choices: episode mapping,
mandatory checklist catalogue source, consent types/signers, room-reference authority, team-role
eligibility map, financial-only emergency override, cancellation stage policy, and planned/performed
item catalogue. Acceptance converts this file from `candidate` to `implementation-ready`; changing
one choice requires updating the affected producer/consumer fixtures in the same PR.
