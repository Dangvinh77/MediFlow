# 06 — billing-service — Care & Finance V2 target

**Owner:** Lộc (`locgit-89`)

**Module:** `backend/billing-service`

**Base path:** `/api/v1/billing`

**Status:** implementation-ready ledger target; P1 prerequisite for operational gates

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-CARE-BILLING-01`](../../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
- [CURRENT Billing spec](../06-billing.md)

Billing is the only owner of accounts, charges, payment requests, payment/refund transactions,
allocations, insurance adjustments, clearance and settlement. Domain services own the care work and
send exact source IDs; Billing never discovers work by querying their databases.

## 2. Migration boundary

1. Existing `FEE`/`INVOICE` and outpatient pharmacy saga remain compatibility version `0`.
2. Ledger tables are additive and use episode-scoped version-1 accounts.
3. Completed history is never rewritten. Reconciliation creates append-only adjustments.
4. A legacy invoice is promoted only through an audited mapping of its selected fees to one episode.
5. `payment.completed` remains a financial fact; operational authorization uses
   `financial.clearance.granted`.

## 3. Target DDL — `V8__care_finance_ledger.sql`

```sql
CREATE TABLE BILLING_ACCOUNT (
    account_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    version BIGINT NOT NULL DEFAULT 0,
    opened_at TIMESTAMPTZ NOT NULL,
    charge_closed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_account_episode UNIQUE(care_episode_type, care_episode_id),
    CONSTRAINT ck_billing_account_episode CHECK
        (care_episode_type IN ('OUTPATIENT_VISIT', 'ADMISSION')),
    CONSTRAINT ck_billing_account_status CHECK
        (status IN ('OPEN', 'CHARGE_CLOSED', 'SETTLEMENT_PENDING', 'SETTLED', 'CLOSED'))
);

CREATE TABLE CHARGE (
    charge_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    patient_id UUID NOT NULL,
    department_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    price_code VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(12,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_amount DECIMAL(19,2) NOT NULL CHECK (unit_amount >= 0),
    gross_amount DECIMAL(19,2) NOT NULL CHECK (gross_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    void_reason VARCHAR(500),
    incurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_charge_source UNIQUE(source_type, source_id, price_code),
    CONSTRAINT ck_charge_status CHECK (status IN ('POSTED', 'VOIDED'))
);
CREATE INDEX idx_charge_account ON CHARGE(account_id, status, incurred_at);

CREATE TABLE PAYMENT_REQUEST (
    payment_request_id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    requested_amount DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    expires_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_payment_request_purpose CHECK
        (purpose IN ('EXAM', 'LAB_TEST', 'PRESCRIPTION', 'ADMISSION_DEPOSIT', 'SURGERY', 'SETTLEMENT')),
    CONSTRAINT ck_payment_request_status CHECK
        (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'EXPIRED', 'CANCELLED'))
);

CREATE TABLE PAYMENT_REQUEST_CHARGE (
    payment_request_id UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    requested_amount DECIMAL(19,2) NOT NULL CHECK (requested_amount >= 0),
    PRIMARY KEY(payment_request_id, charge_id)
);

CREATE TABLE PAYMENT_TRANSACTION (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    payment_request_id UUID REFERENCES PAYMENT_REQUEST(payment_request_id),
    transaction_type VARCHAR(20) NOT NULL,
    classification VARCHAR(24) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(120),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    original_transaction_id UUID REFERENCES PAYMENT_TRANSACTION(transaction_id),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_transaction_type CHECK
        (transaction_type IN ('PAYMENT', 'REFUND', 'REVERSAL')),
    CONSTRAINT ck_payment_classification CHECK
        (classification IN ('SERVICE_PAYMENT', 'ADMISSION_DEPOSIT', 'SETTLEMENT_PAYMENT')),
    CONSTRAINT ck_payment_transaction_status CHECK
        (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_refund_original CHECK
        (transaction_type = 'PAYMENT' OR original_transaction_id IS NOT NULL)
);

CREATE TABLE PAYMENT_ALLOCATION (
    allocation_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES PAYMENT_TRANSACTION(transaction_id),
    charge_id UUID NOT NULL REFERENCES CHARGE(charge_id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_allocation UNIQUE(transaction_id, charge_id)
);

CREATE TABLE FINANCIAL_CLEARANCE (
    clearance_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    payment_request_id UUID NOT NULL REFERENCES PAYMENT_REQUEST(payment_request_id),
    invoice_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    care_episode_type VARCHAR(32) NOT NULL,
    care_episode_id UUID NOT NULL,
    appointment_id UUID,
    record_id UUID,
    lab_test_ids UUID[] NOT NULL DEFAULT '{}',
    prescription_id UUID,
    admission_id UUID,
    surgery_case_id UUID,
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(20) NOT NULL,
    emergency_override BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_financial_clearance_request UNIQUE(payment_request_id),
    CONSTRAINT ck_clearance_purpose CHECK
        (purpose IN ('EXAM', 'LAB_TEST', 'PRESCRIPTION', 'ADMISSION_DEPOSIT', 'SURGERY')),
    CONSTRAINT ck_clearance_target CHECK (
        (purpose = 'EXAM' AND (appointment_id IS NOT NULL OR record_id IS NOT NULL))
        OR (purpose = 'LAB_TEST' AND cardinality(lab_test_ids) > 0)
        OR (purpose = 'PRESCRIPTION' AND prescription_id IS NOT NULL)
        OR (purpose = 'ADMISSION_DEPOSIT' AND admission_id IS NOT NULL)
        OR (purpose = 'SURGERY' AND surgery_case_id IS NOT NULL)
    )
);

CREATE TABLE INSURANCE_ADJUSTMENT (
    adjustment_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    admission_id UUID NOT NULL,
    decision_reference VARCHAR(120) NOT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    original_adjustment_id UUID REFERENCES INSURANCE_ADJUSTMENT(adjustment_id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount >= 0),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_insurance_decision
        UNIQUE(account_id, decision_reference, adjustment_type),
    CONSTRAINT ck_insurance_adjustment_type
        CHECK (adjustment_type IN ('APPROVAL', 'REVERSAL')),
    CONSTRAINT ck_insurance_reversal_original CHECK
        (adjustment_type = 'APPROVAL' OR original_adjustment_id IS NOT NULL)
);

CREATE TABLE SETTLEMENT (
    settlement_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES BILLING_ACCOUNT(account_id),
    admission_id UUID NOT NULL,
    settlement_version INTEGER NOT NULL,
    supersedes_settlement_id UUID REFERENCES SETTLEMENT(settlement_id),
    gross_amount DECIMAL(19,2) NOT NULL,
    insurance_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    patient_liability DECIMAL(19,2) NOT NULL,
    completed_payments DECIMAL(19,2) NOT NULL,
    completed_refunds DECIMAL(19,2) NOT NULL,
    balance DECIMAL(19,2) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_settlement_account_version UNIQUE(account_id, settlement_version),
    CONSTRAINT ck_settlement_outcome CHECK (outcome IN
        ('PAID_IN_FULL', 'ADDITIONAL_PAYMENT_REQUIRED', 'REFUND_DUE', 'DEBT_APPROVED', 'WAIVED'))
);

CREATE TABLE BILLING_OUTBOX_EVENT (
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
CREATE INDEX idx_billing_outbox_unpublished
    ON BILLING_OUTBOX_EVENT(occurred_at) WHERE published_at IS NULL;
```

Existing `PROCESSED_EVENT` remains the inbox. Claim plus ledger side effect is one transaction.

`PAYMENT_REQUEST.invoice_id` is the stable compatibility billing-document identifier required by
the version-1 clearance and payment fixtures. It is not a license to reuse the CURRENT rule that
collects every unpaid fee for one patient. `FINANCIAL_CLEARANCE.invoice_id` must equal the selected
request's value.

## 4. Enums and equations

```java
public enum CareEpisodeType { OUTPATIENT_VISIT, ADMISSION }
public enum AccountStatus { OPEN, CHARGE_CLOSED, SETTLEMENT_PENDING, SETTLED, CLOSED }
public enum ChargeStatus { POSTED, VOIDED }
public enum PaymentTransactionType { PAYMENT, REFUND, REVERSAL }
public enum PaymentClassification { SERVICE_PAYMENT, ADMISSION_DEPOSIT, SETTLEMENT_PAYMENT }
public enum ClearancePurpose { EXAM, LAB_TEST, PRESCRIPTION, ADMISSION_DEPOSIT, SURGERY }
public enum SettlementOutcome {
    PAID_IN_FULL, ADDITIONAL_PAYMENT_REQUIRED, REFUND_DUE, DEBT_APPROVED, WAIVED
}
```

```text
grossAmount       = sum(POSTED charges)
allocatedPayments = completed PAYMENT allocations - completed REFUND/REVERSAL allocations
patientLiability  = grossAmount - approved insurance adjustments
balance           = patientLiability - allocatedPayments
```

Deposits increase cash and liability. They become earned revenue only through charge allocation and
settlement classification; receiving a deposit never directly increments revenue.

## 5. Ports and DTOs

```java
public interface ManageBillingAccountUseCase {
    BillingAccountDTO getOrOpen(OpenAccountCommand command);
    ChargeDTO postCharge(PostChargeCommand command);
    PaymentRequestDTO createPaymentRequest(CreatePaymentRequest request);
}
public interface ProcessPaymentUseCase {
    PaymentTransactionDTO complete(UUID paymentRequestId, CompletePaymentRequest request);
    PaymentTransactionDTO refund(UUID transactionId, RefundPaymentRequest request);
}
public interface SettleAdmissionUseCase {
    SettlementDTO settle(UUID accountId, SettleAdmissionRequest request);
}
public interface BillingOutboxPort { void append(DomainEventEnvelope<?> event); }
public interface PriceCatalogPort {
    PriceSnapshot requireActive(String priceCode, Instant at);
}

public record PostChargeCommand(
    UUID patientId, UUID departmentId, CareEpisodeType careEpisodeType, UUID careEpisodeId,
    String sourceType, UUID sourceId, String priceCode, String description,
    BigDecimal quantity, Instant incurredAt
) {}
public record CreatePaymentRequest(
    ClearancePurpose purpose, List<UUID> chargeIds, BigDecimal requestedAmount,
    Instant expiresAt, TargetReference target
) {}
public record TargetReference(
    UUID appointmentId, UUID recordId, List<UUID> labTestIds,
    UUID prescriptionId, UUID admissionId, UUID surgeryCaseId
) {}
public record CompletePaymentRequest(
    String idempotencyKey, BigDecimal amount, String currency,
    String paymentMethod, String providerReference
) {}
public record RefundPaymentRequest(
    String idempotencyKey, BigDecimal amount, String reason, String paymentMethod
) {}
public record SettleAdmissionRequest(
    BigDecimal approvedInsuranceAmount, String insuranceDecisionReference,
    SettlementOutcome approvedExceptionalOutcome
) {}
```

## 6. Application algorithms

### Consume charge fact

1. Validate event version, episode, source and price code.
2. Claim event ID and resolve price from Billing catalog/policy.
3. Open/reuse the unique episode account; never search by patient alone.
4. Insert charge by `(sourceType, sourceId, priceCode)`; duplicate source is a no-op.
5. Append payment-request fact when the workflow requires prepayment.

### Complete payment and grant clearance

1. Lock payment request/account and claim idempotency key.
2. Reject expired/cancelled requests and cross-account charge lists.
3. Lock selected charge balances; insert immutable completed transaction and allocations whose sum
   does not exceed the completed transaction or any charge's unpaid balance.
4. Mark request partially paid or paid from persisted totals.
5. Only when the purpose amount is satisfied, create one clearance whose target comes from selected
   charges and append `payment.completed` plus `financial.clearance.granted` atomically.

A charge may appear in later requests only for its persisted unallocated balance. Creating two
concurrent requests locks the account/charge rows so their combined requested amount cannot exceed
that balance.

### Refund

1. Lock original completed payment and account.
2. Require cumulative refunds not to exceed original completed amount.
3. Insert a new REFUND transaction referencing the original; never mutate it.
4. Add reversal allocations and append `payment.refunded`.

### Settle admission

1. Require account episode type ADMISSION and charges closed after medical discharge.
2. Calculate totals from persisted ledger rows and approved insurance input.
3. Insert a new immutable settlement version with persisted totals and the previous settlement link.
4. For positive balance, create settlement payment request and outcome
   `ADDITIONAL_PAYMENT_REQUIRED`; for negative balance create `REFUND_DUE` until refund completes.
5. Append final `settlement.completed` only for `PAID_IN_FULL`, `DEBT_APPROVED` or `WAIVED`, or after
   the required refund completes and the final balance is zero.

An approved insurance decision inserts one `APPROVAL` adjustment by decision reference. Reversal
inserts a linked `REVERSAL` row; Billing never overwrites the original adjustment or external
decision identity.

## 7. REST endpoints and roles

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/billing/accounts/{id}` | ADMIN, CASHIER, MANAGER |
| GET | `/api/v1/billing/accounts?patientId&episodeType&episodeId` | ADMIN, CASHIER, MANAGER |
| POST | `/api/v1/billing/accounts/{id}/payment-requests` | ADMIN, CASHIER |
| POST | `/api/v1/billing/payment-requests/{id}/payments` | ADMIN, CASHIER |
| POST | `/api/v1/billing/transactions/{id}/refunds` | ADMIN, CASHIER |
| POST | `/api/v1/billing/accounts/{id}/settlements` | ADMIN, CASHIER |

Charge creation from domain facts is internal; clients cannot post arbitrary charges. Existing
invoice endpoints remain compatibility-only.

Key errors: `BILLING_ACCOUNT_NOT_FOUND` (404), `BILLING_EPISODE_MISMATCH` (422),
`BILLING_DUPLICATE_CHARGE_SOURCE` (409), `BILLING_PAYMENT_REQUEST_INVALID` (422),
`BILLING_ALLOCATION_EXCEEDS_BALANCE` (422), `BILLING_IDEMPOTENCY_CONFLICT` (409),
`BILLING_REFUND_EXCEEDS_PAYMENT` (422) and `BILLING_SETTLEMENT_NOT_READY` (422).

## 8. Events

Subscribe to `medicalrecord.created`, `lab.request.created`, `prescription.created`,
`admission.deposit.requested`, `admission.started`, `discharge.medically.approved`,
`surgery.requested`, `surgery.completed`, `surgery.cancelled` and compatibility pharmacy events.

Publish:

- `payment.completed`: transaction/account/episode/classification plus compatibility target IDs;
- `financial.clearance.granted`: exact contract fixture including purpose and target reference;
- `deposit.topup.required`: `admissionId`, account, balance, requested amount and reason;
- `payment.refunded`: refund/original transaction IDs, account/episode, amount and reason;
- `settlement.completed`: settlement/admission/account IDs and all persisted totals/outcome.

The producer payloads are fixed as Java records before adapters serialize them:

```java
public record FinancialClearanceGrantedPayload(
    UUID clearanceId, UUID invoiceId, UUID accountId, UUID patientId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId, ClearancePurpose purpose,
    UUID appointmentId, UUID recordId, List<UUID> labTestIds,
    UUID prescriptionId, UUID admissionId, UUID surgeryCaseId,
    BigDecimal amount, String currency, String paymentMethod,
    Instant expiresAt, boolean emergencyOverride
) {}

public record PaymentCompletedV2Payload(
    UUID transactionId, UUID invoiceId, UUID paymentRequestId, UUID accountId,
    UUID patientId, UUID departmentId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId,
    PaymentClassification classification,
    BigDecimal totalAmount, String currency, String paymentMethod, Instant completedAt,
    UUID prescriptionId, List<UUID> labTestIds
) {}

public record DepositTopupRequiredPayload(
    UUID topupRequestId, UUID admissionId, UUID accountId, UUID patientId,
    BigDecimal currentBalance, BigDecimal requestedAmount,
    String currency, String reason, Instant requestedAt
) {}

public record PaymentRefundedPayload(
    UUID refundTransactionId, UUID originalTransactionId, UUID accountId,
    UUID patientId, UUID departmentId,
    CareEpisodeType careEpisodeType, UUID careEpisodeId,
    BigDecimal amount, String currency, String reason, Instant completedAt
) {}

public record SettlementCompletedPayload(
    UUID settlementId, UUID admissionId, UUID accountId, UUID patientId, UUID departmentId,
    BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
    BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal balance,
    SettlementOutcome outcome, Instant completedAt
) {}
```

All records are wrapped in the shared envelope with `version=1`, `eventId`, `occurredAt`,
`correlationId` and `producer=billing-service`. Irrelevant target fields are null/empty and never
serve as fallback identifiers.

## 9. Required tests

| Rule | Required test |
|---|---|
| same patient episodes isolated | `postCharge_samePatientDifferentEpisode_separateAccounts` |
| duplicate fact gives one charge | `postCharge_duplicateSource_singleRow` |
| selected charges belong to account | `createPaymentRequest_crossAccount_rejects` |
| concurrent requests cannot over-request | `createPaymentRequest_concurrent_sameCharge_boundedByBalance` |
| duplicate callback gives one transaction | `completePayment_sameIdempotencyKey_singleEffect` |
| allocation is bounded | `completePayment_allocationExceedsTransactionOrCharge_rejects` |
| partial payment gives no clearance | `completePayment_partial_noClearance` |
| purpose cannot unlock another target | `grantClearance_targetMismatch_rejects` |
| deposit not revenue | `depositPayment_projectsCashAndLiabilityOnly` |
| refund append-only and bounded | `refund_completedPayment_createsLinkedTransaction` |
| settlement extra payment stays open | `settle_positiveBalance_requiresPayment` |
| final settlement fixture exact | `settlementCompleted_fixtureRoundTrip` |
| outbox atomic | `payment_commitPersistsLedgerAndOutbox` |

## 10. Rollout and Definition of Done

1. Add ledger schema and repository tests without changing CURRENT saga.
2. Add charge consumers and APIs behind `mediflow.features.care-finance-v2=false`.
3. Publish shared fixtures and make every operational consumer pass them.
4. Run P2 outpatient and P3 inpatient Docker flows.
5. Retire FEE/INVOICE only in a later destructive-migration release.

Done means ledger equations, idempotency, concurrency, refunds, settlement, DLQ and role matrices
pass, and no service uses a generic payment fact as unrelated operational authorization.
