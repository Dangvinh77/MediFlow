# 08 — report-service — Care & Finance V2 target

**Owner:** Huy (`LQHuy0210`)

**Module:** `backend/report-service`

**Base path:** `/api/v1/reports`

**Status:** implementation-ready projection target; enable per producer fixture

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [CURRENT Report spec](../08-report.md)

Report is a rebuildable event projection. It owns no financial transaction, admission, surgery or
clinical decision and never calls REST to fill missing event data.

## 2. Migration boundary

The current five-event reports remain compatibility projections. V2 writes separate contribution
and aggregate tables so deposit semantics cannot corrupt legacy monthly revenue. After replay and
parallel-run totals are reconciled, clients move to V2 endpoints in a separate release.

## 3. Target DDL — `V6__care_finance_projections.sql`

The Pharmacy/Billing/Report branch keeps its approved English persistence naming convention.

```sql
CREATE TABLE FINANCIAL_CONTRIBUTION (
    contribution_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    account_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    department_id UUID,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    contribution_type VARCHAR(32) NOT NULL,
    cash_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    liability_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    revenue_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    refund_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    receivable_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    original_contribution_id UUID REFERENCES FINANCIAL_CONTRIBUTION(contribution_id),
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_financial_contribution_event_source
        UNIQUE(event_id, source_type, source_id),
    CONSTRAINT ck_financial_contribution_type CHECK (contribution_type IN
        ('PAYMENT', 'DEPOSIT', 'REVENUE_RECOGNITION', 'REFUND', 'RECEIVABLE', 'REVERSAL'))
);
CREATE INDEX idx_financial_contribution_period
    ON FINANCIAL_CONTRIBUTION(business_date, department_id);

CREATE TABLE DAILY_FINANCIAL_REPORT (
    report_date DATE NOT NULL,
    department_id UUID,
    cash_received DECIMAL(19,2) NOT NULL DEFAULT 0,
    deposit_liability DECIMAL(19,2) NOT NULL DEFAULT 0,
    earned_revenue DECIMAL(19,2) NOT NULL DEFAULT 0,
    refunds DECIMAL(19,2) NOT NULL DEFAULT 0,
    outstanding_receivable DECIMAL(19,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_daily_financial_report_scope
    ON DAILY_FINANCIAL_REPORT(report_date, COALESCE(department_id,
        '00000000-0000-0000-0000-000000000000'::UUID));

CREATE TABLE OPERATIONAL_CONTRIBUTION (
    contribution_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    department_id UUID,
    care_episode_id UUID,
    metric_date DATE NOT NULL,
    numeric_value DECIMAL(19,3) NOT NULL DEFAULT 1,
    category VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_operational_event_metric UNIQUE(event_id, metric_type, source_id)
);

CREATE TABLE DAILY_OPERATIONAL_REPORT (
    report_date DATE NOT NULL,
    department_id UUID,
    completed_visits BIGINT NOT NULL DEFAULT 0,
    admissions BIGINT NOT NULL DEFAULT 0,
    discharges BIGINT NOT NULL DEFAULT 0,
    inpatient_days BIGINT NOT NULL DEFAULT 0,
    surgeries_completed BIGINT NOT NULL DEFAULT 0,
    surgeries_cancelled BIGINT NOT NULL DEFAULT 0,
    surgery_duration_minutes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_daily_operational_report_scope
    ON DAILY_OPERATIONAL_REPORT(report_date, COALESCE(department_id,
        '00000000-0000-0000-0000-000000000000'::UUID));
```

`PROCESSED_EVENT` remains the inbox. Each contribution and aggregate update commits atomically with
the event claim.

## 4. Projection equations

```text
payment SERVICE_PAYMENT     → cash += amount; revenue += allocated earned amount
payment ADMISSION_DEPOSIT   → cash += amount; liability += unallocated deposit amount
settlement recognition      → liability -= applied deposit; revenue += recognized charges
refund                      → cash -= amount; refund += amount; reverse linked liability/revenue
settlement outstanding debt → receivable = persisted Billing balance
```

Report never infers classification from amount, patient or episode type. It uses Billing's explicit
transaction classification and persisted settlement totals.

## 5. Ports and commands

```java
public interface ProjectCareFinanceEventUseCase {
    void onPaymentCompleted(PaymentCompletedCommand command);
    void onPaymentRefunded(PaymentRefundedCommand command);
    void onSettlementCompleted(SettlementCompletedCommand command);
    void onOperationalEvent(OperationalProjectionCommand command);
}
public interface ContributionRepositoryPort {
    boolean exists(UUID eventId, String sourceType, UUID sourceId);
    void insert(FinancialContribution contribution);
    Optional<FinancialContribution> findOriginal(UUID sourceId);
}
public interface FinancialReportRepositoryPort {
    void applyDaily(FinancialDelta delta);
}
public interface OperationalReportRepositoryPort {
    void applyDaily(OperationalDelta delta);
}
```

Repository upsert uses a real database unique constraint and atomic increment; JVM-level locking is
not sufficient when multiple consumers run.

## 6. Event mapping

| Event | Projection |
|---|---|
| `payment.completed` | classified cash plus service revenue or deposit liability |
| `payment.refunded` | linked cash/refund/reversal contribution |
| `settlement.completed` | recognized revenue, remaining liability and receivable outcome |
| `medicalrecord.completed` | completed visit and disposition |
| `admission.started` | admission and occupancy start |
| `admission.closed` | discharge and length of stay |
| `surgery.completed` | count, duration and complication category |
| `surgery.cancelled` | cancellation count and stage/reason category |
| `lab.result.created` | completed Lab KPI by requesting department |
| `prescription.filled` | dispensed prescription/item KPI |

Every payload must include the canonical source ID and business timestamp. Unknown versions or
missing sources follow bounded retry/DLQ and do not mutate projections.

## 7. Consumer algorithm

1. Validate envelope and canonical source fields.
2. In one transaction, atomically claim `(eventId, metric/source)`.
3. Build immutable contribution with explicit business date and department scope.
4. Lock/find-or-create department and all-hospital aggregate rows.
5. Apply the same delta to both scopes and commit.
6. Duplicate delivery exits before any increment.

A refund locates the original contribution by transaction reference and reverses its original
business/department scope. It never assigns the reversal to the current date merely because the
event was delivered later.

## 8. REST endpoints and roles

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/reports/financial/daily?from&to&departmentId` | ADMIN, MANAGER |
| GET | `/api/v1/reports/financial/monthly?year&departmentId` | ADMIN, MANAGER |
| GET | `/api/v1/reports/operations/daily?from&to&departmentId` | ADMIN, MANAGER, DOCTOR |
| GET | `/api/v1/reports/operations/surgery?from&to&departmentId` | ADMIN, MANAGER, DOCTOR |

Responses expose aggregate numbers, never patient-level medical payloads.

Key HTTP errors: `REPORT_VALIDATION_ERROR` (400) for invalid periods/page arguments and
`REPORT_NOT_FOUND` (404) for an unavailable requested projection. Consumer contract errors such as
missing source IDs or unknown event versions are retry/DLQ failures and are never exposed as a
successful zero-valued report.

## 9. Replay contract

V2 projections can be truncated and rebuilt from the immutable event stream in event-time order.
Replay uses the same consumer application service and unique contribution keys. A replay run must
produce identical totals regardless of redelivery order for distinct events.

## 10. Required tests

| Rule | Required test |
|---|---|
| deposit not revenue | `projectDeposit_increasesCashAndLiabilityOnly` |
| settlement recognizes revenue | `projectSettlement_movesLiabilityToRevenue` |
| refund reverses original scope | `projectRefund_usesOriginalContributionDateAndDepartment` |
| duplicate event harmless | `project_duplicateEvent_singleContribution` |
| department and hospital updated | `project_singleEvent_updatesBothScopes` |
| concurrent find-or-create safe | `project_concurrentNewScope_singleAggregateRow` |
| missing source reaches DLQ | `project_missingSource_rejectsWithoutMutation` |
| replay deterministic | `rebuild_sameEvents_producesSameTotals` |
| all fixtures deserialize | `careFinanceFixtures_roundTrip` |

## 11. Rollout and Definition of Done

Create V2 projections and run parallel replay without changing CURRENT endpoints. Reconcile totals,
then expose V2 endpoints. Done requires PostgreSQL concurrency tests, duplicate/reversal tests,
producer fixtures and a full replay from an empty projection.
