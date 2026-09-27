# 04 — lab-service — Care & Finance V2 target

**Owner:** Vinh (`Dangvinh77` / `Harori`)

**Module:** `backend/lab-service`

**Base path:** `/api/v1/lab`

**Status:** implementation-ready target; compatible migration from explicit `payment.completed.labTestIds`

## 1. Sources and boundary

This specification implements the Lab part of:

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html);
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md);
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md);
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

The existing [Lab spec](../04-lab.md) is the `CURRENT` compatibility contract. This file is the
`TARGET` contract. Lab owns test requests, execution status and verified results. Billing owns
charges/payments/clearance. Clinical or Inpatient owns the care episode and supplies its exact ID.

## 2. Migration rules

1. `lab.request.created` becomes the charge trigger; `lab.result.created` never creates the first charge.
2. Existing `payment.completed.labTestIds` continues to set the compatibility `is_paid` projection.
3. New operational gates consume `financial.clearance.granted(purpose=LAB_TEST)`.
4. Existing rows are `care_contract_version=0`. New requests are version `1` and require episode,
   price and source identifiers.
5. Legacy rows are not backfilled by guessing an appointment/admission from `recordId` or patient.
6. `medicalrecord.created` is not a lab order. It cannot create a test without explicit test codes.

## 3. Target DDL — `V2__care_finance_v2.sql`

```sql
ALTER TABLE lab_test
    ADD COLUMN care_contract_version SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN care_episode_type VARCHAR(32),
    ADD COLUMN care_episode_id UUID,
    ADD COLUMN source_order_id UUID,
    ADD COLUMN price_code VARCHAR(64),
    ADD COLUMN clearance_id UUID,
    ADD COLUMN clearance_granted_at TIMESTAMPTZ,
    ADD COLUMN emergency_override_id UUID,
    ADD COLUMN result_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE lab_test
    ADD CONSTRAINT ck_lab_contract_version CHECK (care_contract_version IN (0, 1)),
    ADD CONSTRAINT ck_lab_v1_episode CHECK (
        care_contract_version = 0 OR (
            care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
            AND care_episode_id IS NOT NULL
            AND price_code IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_lab_execution_gate CHECK (
        care_contract_version = 0 OR status NOT IN ('IN_PROGRESS', 'COMPLETED')
        OR clearance_id IS NOT NULL OR emergency_override_id IS NOT NULL
    );

CREATE UNIQUE INDEX uq_lab_source_order
    ON lab_test(source_order_id) WHERE source_order_id IS NOT NULL;
CREATE INDEX idx_lab_episode ON lab_test(care_episode_type, care_episode_id);

CREATE TABLE lab_financial_clearance (
    clearance_target_id UUID PRIMARY KEY,
    clearance_id UUID NOT NULL,
    event_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    account_id UUID NOT NULL,
    test_id UUID NOT NULL REFERENCES lab_test(test_id),
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_clearance_target UNIQUE(clearance_id, test_id),
    CONSTRAINT uq_lab_clearance_event_target UNIQUE(event_id, test_id)
);

CREATE TABLE lab_emergency_override (
    override_id UUID PRIMARY KEY,
    test_id UUID NOT NULL REFERENCES lab_test(test_id),
    patient_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    approved_by UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lab_outbox_event (
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
CREATE INDEX idx_lab_outbox_unpublished
    ON lab_outbox_event(occurred_at) WHERE published_at IS NULL;
```

`processed_event` remains the inbox/dedupe ledger. Claiming its event ID and applying the side effect
must be one database transaction.

## 4. Enums and state machine

```java
public enum LabTestStatus { PENDING, AWAITING_PAYMENT, READY, IN_PROGRESS, COMPLETED, CANCELLED }
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
```

```text
PENDING → AWAITING_PAYMENT → READY → IN_PROGRESS → COMPLETED
   └──────────────────────────────────────────────→ CANCELLED
```

- Creating a V2 request immediately records the charge fact and sets `AWAITING_PAYMENT`.
- Matching clearance sets `READY`.
- Emergency override allows `AWAITING_PAYMENT → IN_PROGRESS` while retaining an unpaid receivable.
- `COMPLETED` and `CANCELLED` are terminal.
- A compatibility version-0 test retains the current `PENDING|IN_PROGRESS|COMPLETED|CANCELLED` flow.

## 5. Error codes

| Code | HTTP | Condition |
|---|---:|---|
| `LAB_TEST_NOT_FOUND` | 404 | test absent |
| `LAB_INVALID_STATUS_TRANSITION` | 422 | invalid transition |
| `LAB_CLEARANCE_REQUIRED` | 422 | start/result without clearance or override |
| `LAB_CLEARANCE_TARGET_MISMATCH` | 422 | target/patient/episode mismatch |
| `LAB_CLEARANCE_EXPIRED` | 422 | clearance expired before start |
| `LAB_OVERRIDE_INVALID` | 422 | missing emergency audit field |
| `LAB_EPISODE_REQUIRED` | 422 | V2 request lacks episode identity |
| `LAB_PRICE_CODE_REQUIRED` | 422 | request lacks stable catalog key |
| `LAB_DUPLICATE_SOURCE_ORDER` | 409 | producer order was already materialized |
| `LAB_RESULT_FINALIZED` | 409 | result added after terminal state |

## 6. Ports

```java
public interface ManageLabTestUseCase {
    LabTestDTO create(CreateLabRequest request);
    LabTestDTO start(UUID testId, StartLabTestRequest request);
    LabTestDTO addResults(UUID testId, AddResultRequest request);
    LabTestDTO cancel(UUID testId, CancelLabTestRequest request);
    LabTestDTO getById(UUID testId);
    PageResult<LabTestDTO> search(LabSearchQuery query);
}

public interface ReactToFinancialClearanceUseCase {
    void onFinancialClearance(FinancialClearanceCommand command);
}

public interface LabClearanceRepositoryPort {
    boolean claimAndSave(UUID eventId, LabFinancialClearance clearance);
    Optional<LabFinancialClearance> findValidByTestId(UUID testId, Instant at);
}

public interface LabOutboxPort {
    void append(DomainEventEnvelope<?> event);
}
```

The existing repository port adds:

```java
Optional<LabTest> findByIdForUpdate(UUID testId);
boolean existsBySourceOrderId(UUID sourceOrderId);
```

## 7. DTOs

```java
public record CreateLabRequest(
    @NotNull UUID patientId,
    @NotNull UUID recordId,
    @NotNull UUID requestingDepartmentId,
    UUID sourceOrderId,
    @NotNull CareEpisodeType careEpisodeType,
    @NotNull UUID careEpisodeId,
    @NotBlank @Size(max = 50) String testType,
    @NotBlank @Size(max = 64) String priceCode,
    @NotNull LocalDate requestedDate
) {}

public record EmergencyOverrideRequest(
    @NotNull UUID overrideId,
    @NotNull UUID approvedBy,
    @NotBlank @Size(max = 32) String approverRole,
    @NotBlank @Size(max = 1000) String reason,
    @NotNull @PastOrPresent Instant approvedAt
) {}

public record StartLabTestRequest(EmergencyOverrideRequest emergencyOverride) {}

public record CancelLabTestRequest(
    @NotBlank @Size(max = 1000) String reason,
    @NotNull UUID cancelledBy
) {}

public record LabTestDTO(
    UUID testId, UUID recordId, UUID patientId, UUID requestingDepartmentId,
    UUID sourceOrderId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
    String testType, String priceCode, LocalDate requestedDate, LocalDate performedDate,
    LabTestStatus status, UUID clearanceId, UUID emergencyOverrideId,
    int resultVersion, String conclusion, List<LabResultDTO> results
) {}

public record FinancialClearanceCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    List<UUID> labTestIds, BigDecimal amount, String currency,
    Instant expiresAt, boolean emergencyOverride
) {}
```

`AddResultRequest` keeps the existing item structure and adds `performedDate`; all results in one
command produce one new immutable `resultVersion` snapshot. `verifiedBy` is taken from the
authenticated `staffId` claim and never trusted from the request body.

## 8. Application algorithms

### Create request

1. Validate the episode: outpatient requires the producer-selected visit ID; admission requires
   `careEpisodeId=admissionId`. Do not derive either from `recordId`.
2. If `sourceOrderId` exists, reject a second local test for the same producer order.
3. Create status `AWAITING_PAYMENT`, `careContractVersion=1`.
4. Save aggregate and append `lab.request.created` to the outbox in the same transaction.

### Consume LAB_TEST clearance

1. Validate envelope `version=1`, `purpose=LAB_TEST`, and non-empty `labTestIds`.
2. Claim the event once. Process every listed ID independently but atomically for this event.
3. Lock each local test and require exact patient, episode type and episode ID.
4. Save one clearance-target row per local test; move `AWAITING_PAYMENT → READY`.
5. Unknown/mismatched target is a contract failure and follows bounded retry/DLQ policy.

### Start and complete

1. Lock test.
2. Start requires `READY` and a non-expired clearance, or a complete emergency override.
3. Persist override before changing to `IN_PROGRESS`.
4. Add results only in `IN_PROGRESS`; require `performedDate >= requestedDate`.
5. Insert immutable result rows, increment `resultVersion`, set conclusion/status `COMPLETED`, and
   append `lab.result.created` in the same transaction.

## 9. REST endpoints

| Method | Path | Request | Roles |
|---|---|---|---|
| POST | `/api/v1/lab` | `CreateLabRequest` | ADMIN, DOCTOR |
| PUT | `/api/v1/lab/{id}/start` | `StartLabTestRequest` | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/results` | `AddResultRequest` | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/cancel` | `CancelLabTestRequest` | ADMIN, DOCTOR, LAB_TECH |
| GET | `/api/v1/lab/{id}` | — | ADMIN, DOCTOR, NURSE, LAB_TECH |
| GET | `/api/v1/lab?departmentId&status&episodeType&episodeId&page&size` | — | ADMIN, MANAGER, DOCTOR, NURSE, LAB_TECH |

Update `backend/lab-service/lab.http` in the implementation PR. The generic status endpoint becomes
compatibility-only and cannot bypass target guards. Every controller method declares the listed
roles with `@PreAuthorize`; default deny remains active.

## 10. Events

| Event | Required payload |
|---|---|
| `lab.request.created` | `labId`, `patientId`, `recordId`, `departmentId`, `careEpisodeType`, `careEpisodeId`, `sourceType=LAB_TEST`, `sourceId=labId`, `priceCode`, `labType`, `requestedAt`, `emergencyOverrideId?` |
| `lab.result.created` | `labId`, `patientId`, `recordId`, `departmentId`, `careEpisodeType`, `careEpisodeId`, `labType`, `resultVersion`, `results[]`, `conclusion`, `verifiedBy`, `completedAt` |

Subscribe to:

- `financial.clearance.granted` — target flow;
- `payment.completed` — compatibility only, accepting explicit `labTestIds`;
- a future explicit Clinical/Inpatient lab-order event only after its producer schema and fixture are
  added to the canonical contract. `medicalrecord.created` remains a no-op for test creation.

## 11. Idempotency and concurrency

- Unique `source_order_id` prevents duplicate materialization of a producer order.
- Unique inbox `event_id` prevents duplicate clearance/payment application.
- Result addition locks the test row; concurrent second completion observes terminal status.
- The outbox write shares the aggregate transaction. Publishing directly inside the transaction is
  not the target design.
- Poison payloads retain `eventId` and `correlationId` in logs/DLQ metadata without patient PII.

## 12. Required tests

| Rule | Required test |
|---|---|
| charge occurs at request | `create_v2_appendsLabRequestChargeFact` |
| no payment means no start | `start_awaitingPayment_rejects` |
| clearance targets exact test IDs | `onClearance_explicitIds_unlockOnlyTargets` |
| wrong episode is rejected | `onClearance_wrongAdmission_rejects` |
| duplicate event applies once | `onClearance_duplicateEvent_appliesOnce` |
| emergency override is audited | `start_emergency_persistsOverrideWithoutMarkingPaid` |
| result date is valid | `addResults_beforeRequestedDate_rejects` |
| concurrent completion is single | `addResults_concurrent_singleResultVersion` |
| diagnosis does not auto-order Lab | `onMedicalRecordCreated_withoutOrder_createsNothing` |
| compatibility IDs remain explicit | `onPaymentCompleted_explicitLabIds_marksOnlyThoseTests` |
| role matrix enforced | `labV2Endpoints_roleMatrix` |

Contract fixtures must be shared with Billing, Clinical, Report and Notification. Persistence tests
must prove the version-1 check constraint, source-order uniqueness and atomic event claim.

## 13. Rollout and Definition of Done

1. Merge additive DDL and version-0 compatibility tests.
2. Add target outbox, clearance projection and guarded commands behind
   `mediflow.features.care-finance-v2=false`.
3. Make Billing and Lab pass the same clearance fixture and duplicate-event test.
4. Enable Docker E2E: create request → charge/payment → clearance → start → verified result.
5. Verify emergency start creates receivable/audit without setting `is_paid=true`.
6. Remove the compatibility consumer only after no producer relies on `payment.completed` as an
   operational authorization and the removal has its own migration PR.

Done means every state transition, target mismatch, duplicate, concurrency path, DLQ path and role
is tested, with no cross-service DB access or identifier inference.
