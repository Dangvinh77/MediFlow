# 08 — report-service — Care & Finance V2 target

**Owner:** Huy (`LQHuy0210`)

**Module:** `backend/report-service`

**Base path:** `/api/v1/reports`

**Status:** implementation-ready local projection target; source-operation mapping, finance totals
and durable replay remain gated by the canonical producer contracts

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
    source_revision INTEGER NOT NULL,
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
    CONSTRAINT ck_financial_contribution_source_type CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_financial_contribution_revision CHECK (source_revision > 0),
    CONSTRAINT ck_financial_contribution_episode_type CHECK (
        care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')
    ),
    CONSTRAINT ck_financial_contribution_type CHECK (contribution_type IN
        ('PAYMENT', 'DEPOSIT', 'REVENUE_RECOGNITION', 'REFUND', 'RECEIVABLE', 'REVERSAL'))
);
CREATE UNIQUE INDEX uq_financial_contribution_business_operation
    ON FINANCIAL_CONTRIBUTION
        (source_type, source_id, source_revision, contribution_type, department_id)
        NULLS NOT DISTINCT;
CREATE INDEX idx_financial_contribution_event_id ON FINANCIAL_CONTRIBUTION(event_id);
CREATE INDEX idx_financial_contribution_period
    ON FINANCIAL_CONTRIBUTION(business_date, department_id);
CREATE INDEX idx_financial_contribution_episode
    ON FINANCIAL_CONTRIBUTION(care_episode_type, care_episode_id);

CREATE TABLE DAILY_FINANCIAL_REPORT (
    report_id UUID PRIMARY KEY,
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
    ON DAILY_FINANCIAL_REPORT(report_date, department_id) NULLS NOT DISTINCT;

CREATE TABLE OPERATIONAL_CONTRIBUTION (
    contribution_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    source_revision INTEGER NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    department_id UUID,
    care_episode_type VARCHAR(32),
    care_episode_id UUID,
    metric_date DATE NOT NULL,
    numeric_value DECIMAL(19,3) NOT NULL DEFAULT 1,
    category VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operational_contribution_source_type CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_operational_contribution_metric_type CHECK (btrim(metric_type) <> ''),
    CONSTRAINT ck_operational_contribution_revision CHECK (source_revision > 0),
    CONSTRAINT ck_operational_contribution_episode_pair CHECK (
        (care_episode_type IS NULL AND care_episode_id IS NULL)
        OR (care_episode_type IS NOT NULL
            AND care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION') AND care_episode_id IS NOT NULL)
    ),
    CONSTRAINT ck_operational_contribution_category CHECK (
        category IS NULL OR btrim(category) <> ''
    )
);
CREATE UNIQUE INDEX uq_operational_contribution_business_operation
    ON OPERATIONAL_CONTRIBUTION
        (source_type, source_id, source_revision, metric_type, department_id)
        NULLS NOT DISTINCT;
CREATE INDEX idx_operational_contribution_event_id ON OPERATIONAL_CONTRIBUTION(event_id);
CREATE INDEX idx_operational_contribution_period
    ON OPERATIONAL_CONTRIBUTION(metric_date, department_id);
CREATE INDEX idx_operational_contribution_episode
    ON OPERATIONAL_CONTRIBUTION(care_episode_type, care_episode_id);

CREATE TABLE DAILY_OPERATIONAL_REPORT (
    report_id UUID PRIMARY KEY,
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
    ON DAILY_OPERATIONAL_REPORT(report_date, department_id) NULLS NOT DISTINCT;
```

`event_id` is delivery provenance, not the only semantic key. The Report-local uniqueness proposal is
`source_type + source_id + source_revision + contribution_type/metric_type + department_id`; source
ID meaning, correction/supersedes behavior and which owner fact supplies each field remain subject to
the canonical contracts in `CONTRACT-CARE-PROJECTIONS-01` and `CONTRACT-CARE-BILLING-01`. The V2
listener/writer stays disabled until those fixtures are accepted. The legacy `PROCESSED_EVENT` table
is not a durable replay archive; replay generation/ledger and finite source are separate R-01.4/.5
work. When enabled, a contribution and both department/hospital aggregate updates must commit
atomically with the V2 delivery claim.

Local V7 operational specialization (2026-10-01): a completed source/metric belongs to exactly one
department, so `source_type + source_id + source_revision + metric_type` additionally has global
uniqueness. Changed department/value/category/business time is a conflict, not a second allocation.
Financial multi-department allocation keys are unchanged. `lab_tests`, `dispensed_prescriptions`
and `dispensed_units` extend the operational aggregate. Typed commands supply an explicit revision;
revision >1 is rejected until correction/replacement policy exists, never defaulted from envelope
version. No live mapper, subscriber or V2 query is enabled by this kernel.

Local offline Lab mapping (2026-10-02) supplies source revision from `resultVersion`, source ID from
`labId`, department/episode directly from the payload, and business time from `completedAt`. KPI
date uses the configured Report timezone; it does not use republish time or `performedDate`.
Revision 1 maps to one LAB_TESTS contribution. The real admission fixture's revision 3 is rejected
until the producer clarifies first imported result versus replacement/correction semantics. No
revision is defaulted for Clinical/Pharmacy facts lacking one; their source mapping remains open.
This offline mapper does not enable a listener or close owner/rollout acceptance.

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
    boolean existsBusinessOperation(String sourceType, UUID sourceId, int sourceRevision,
            String contributionType, UUID departmentId);
    void insert(FinancialContribution contribution);
    Optional<FinancialContribution> findOriginal(String sourceType, UUID sourceId, int sourceRevision);
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

1. Validate envelope and canonical source fields; reject an unknown source/revision rather than infer it.
2. In one transaction, claim the event delivery in an isolated V2 processing ledger by `eventId`.
3. Insert/check the immutable contribution by its business-operation key, independent of `eventId`.
4. Lock/find-or-create department and all-hospital aggregate rows.
5. Apply the accepted delta to both scopes and commit with the delivery claim.
6. Exact redelivery and same-operation/new-event delivery both produce no second increment; same key
   with different bytes/payload is a contract conflict, not a silent duplicate.

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

**Local read slice 2026-10-02 (not full target activation):** V10 adds empty accepted-coverage
publication storage for immutable operational replay generations. Two default-off operations GET
controllers implement direct ADMIN/MANAGER/DOCTOR roles, bounded 1..366-day periods and the errors
above. Reader requires explicit accepted period/timezone/metric coverage and VERIFIED/all-applied
generation. No publication/partial source/failed replay means unavailable, never invented zero;
covered empty days may be zero-filled. Response is aggregate-only with generationId/reconciledAt,
`snapshotOnly=true` and stable camelCase daily counters. Values do not read later live facts.
Replay VERIFIED alone is not owner approval or historical completeness and creates no publication.
No production publication writer/admin API exists; do not populate the table manually. Financial
APIs, complete inpatient-day/surgery-category/occupancy metrics, approved coverage/export, live
catch-up/controlled publication/rollback and Gateway role acceptance remain OPEN. This slice does
not change the target financial/operations semantics or the legacy reader.

V2 projections are rebuildable only after R-01.4 selects a finite durable source, retention and
watermark; Rabbit queue acknowledgement alone is not an archive. Replay uses a separate projection
generation and the same pure projector/business-operation keys. A replay run must produce identical
totals regardless of redelivery order for distinct operations, and must not truncate or clear the
live inbox/projection. Until those D11 decisions and replay tests pass, rebuild is not an available
runtime capability.

Huy's local source choice is `operational_event_journal`: event metadata/source references plus
versioned accepted contribution inputs from activation. Full producer diagnosis/results/free text
are not retained; a SHA-256 of the normalized envelope detects conflicts. Projection inputs also
have a fingerprint, and multi-metric snapshots are ordered deterministically. The journal and all
accepted effects commit together. Retention has no automatic purge pending export/cutover approval;
there is no journal API. This source cannot recreate events predating activation.

### Local finite operational rebuild (2026-10-02)

V8 implements a separate generation, immutable copied input manifest, per-generation semantic
contributions and scoped counters. `start()` materializes the currently committed journal rows with
one `INSERT ... SELECT` statement. An older uncommitted transaction is excluded even if it commits
after start; a wall-clock/sequence maximum is not substituted for this MVCC visibility boundary.
`advance(generationId, batchSize)` accepts 1..500 inputs, holds the DB generation lock and commits
fact dedupe, both scopes, applied markers and progress together. A processing failure rolls back
the whole batch, leaving it restartable. No JVM lock, producer command, notification or live inbox
mutation is used. Live projection and replay share the pure scope planner and V7 snapshot codec.

Only projector version 1 and the accepted revision-1 kernel are supported. Reconstructed typed
inputs must match their projection fingerprint; retained envelope hash is provenance, not proof
that the redacted journal can reconstruct/verify raw producer bytes. Semantic duplicates do not
increment counters. Final reconciliation compares both fact sets and department/hospital scopes
against distinct facts in the frozen manifest, not the advancing live projections. Mismatch marks
the generation FAILED. VERIFIED means only finite-manifest equality; it never changes a read pointer.

Local unit/static tests pass; real PostgreSQL snapshot/concurrency/rollback/reconciliation tests
are written but VERIFY OPEN while Docker is unavailable. Pending finance/admission metric mapping,
historical export/retention approval, live catch-up, atomic read switch/rollback and APIs remain open.

### Offline admission pending/pairing (2026-10-02)

V9 stores minimal immutable STARTED/CLOSED facts and normalized envelope fingerprints. The pure
mapper reads the actual Inpatient V1 bytes: exact admission/patient; start department/bed/admittedAt/
emergency audit reference; close closedAt and settlement OR approved override proof. Close is an
administrative fact, not medical discharge. Close-before-start persists as PENDING_START; only the
exact matching start supplies department. Patient/chronology/immutable department/time/proof changes
are conflicts; late start never reopens a closed admission. Local row fence, event claim and evidence
effects join one transaction; ISO business times preserve source nanoseconds across reload. No full
clinical payload is retained. No source revision is invented from envelope version and no metric
contribution/count/LOS/occupancy is emitted until source business revision and correction/discharge
semantics are accepted. Existing listener/API remains unchanged; real PG pending/reload/race/rollback
and upgrade/shape tests remain VERIFY OPEN without Docker.

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
