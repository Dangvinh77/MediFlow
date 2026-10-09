# 07 — notification-service — Care & Finance V2 target

**Owner:** Lộc (`locgit-89`)

**Module:** `backend/notification-service`

**Base path:** `/api/v1/notifications`

**Status:** implementation-ready consumer target; enable per producer fixture

**Implemented slice (2026-10-05):** V2 migration adds durable receipt source/template/privacy/hash
metadata and held delivery outbox. Existing queue reader multiplexes V0 flat payloads and strictly
gated V1 classified Billing receipts. IN_APP delivery commits inbox + private readable history + held
sent fact atomically, with no external side effect. Every installment is a receipt, not a fully paid
invoice; admission-deposit receipts have their own wording. External sender workers and other V2
events remain unfinished. `MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED` defaults to false.

## 1. Sources and boundary

**Completed-refund follow-up 2026-10-08:** strict separately gated V1 intake, semantic
dedupe, private PAYMENT_REFUNDED history and held sent fact now pass actual Billing fixture
PG/Rabbit rollback/DLQ/replay acceptance. Flags remain false; no external delivery, final
settlement or clinical permission. Other templates/reviewed release remain open.
[Canonical wire and verification](../../../superpowers/plans/2026-10-08-billing-refund-closure.md).

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [CURRENT Notification spec](../07-notification.md)

Notification owns delivery intent/history and retry state. It is never a workflow authority; a
failed email/SMS cannot roll back a payment, admission or surgery transition.

## 2. Target DDL — `V3__care_finance_templates.sql`

```sql
ALTER TABLE NOTIFICATION
    ADD COLUMN template_key VARCHAR(80),
    ADD COLUMN source_event_id UUID,
    ADD COLUMN source_event_type VARCHAR(100),
    ADD COLUMN source_id UUID,
    ADD COLUMN correlation_id VARCHAR(100),
    ADD COLUMN sensitivity VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

CREATE UNIQUE INDEX uq_notification_event_template_channel
    ON NOTIFICATION(source_event_id, template_key, channel)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE NOTIFICATION
    ADD CONSTRAINT ck_notification_sensitivity
        CHECK (sensitivity IN ('STANDARD', 'PRIVATE_IN_APP_ONLY'));

CREATE TABLE NOTIFICATION_OUTBOX_EVENT (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_outbox_unpublished
    ON NOTIFICATION_OUTBOX_EVENT(occurred_at) WHERE published_at IS NULL;
```

Existing `PROCESSED_EVENT` remains the inbox. If one event produces multiple channels, the event is
claimed after all intended notification rows are persisted in the same transaction.

## 3. Template keys

```java
public enum CareFinanceTemplateKey {
    PAYMENT_REQUESTED, PAYMENT_RECEIPT, PAYMENT_REFUNDED,
    ADMISSION_DEPOSIT_REQUESTED, DEPOSIT_TOPUP_REQUIRED,
    ADMISSION_STARTED, SURGERY_READY, SURGERY_CANCELLED,
    SETTLEMENT_COMPLETED, ADMISSION_CLOSED
}
```

Deposit request, deposit receipt, service payment, refund and final settlement use different text.
A deposit receipt must state that it is an advance and does not mean treatment is fully settled.

## 4. Ports and commands

```java
public interface ReactToCareEventUseCase {
    void createNotificationIntents(CareNotificationCommand command);
}
public interface RecipientResolverPort {
    RecipientSnapshot resolve(UUID patientId);
}
public record CareNotificationCommand(
    UUID eventId, int version, Instant occurredAt, String correlationId,
    String eventType, UUID sourceId, UUID patientId,
    Map<String, Object> templateFields
) {}
```

Recipient data comes from explicit permitted event fields or Patient service lookup. The adapter
must distinguish missing patient/contact from Patient outage; it never reads Patient DB.

## 5. Consumer algorithm

1. Validate envelope version and the required source/patient IDs for the routing key.
2. Atomically claim event and choose exactly one template key.
3. Resolve allowed recipient channels. Missing address creates a FAILED/SKIPPED history row with
   reason; it does not fabricate an address.
4. Render from an allow-list of fields; reject unknown/missing mandatory fields.
5. Persist PENDING intents and inbox claim in one transaction.
6. Sender workers deliver independently and move each row to SENT or FAILED with bounded retries.
7. Append `notification.sent` to the outbox after a successful delivery; producer workflow is not
   coupled to it.

## 6. Subscription manifest

| Event | Template | Required source fields |
|---|---|---|
| payment request / `invoice.created` | `PAYMENT_REQUESTED` | request/invoice ID, patient, amount, due/expiry |
| `payment.completed` | `PAYMENT_RECEIPT` | transaction ID, classification, amount, completedAt |
| `payment.refunded` | `PAYMENT_REFUNDED` | refund/original transaction IDs, amount, completedAt |
| `admission.deposit.requested` | `ADMISSION_DEPOSIT_REQUESTED` | admission ID, suggested amount, reason |
| `deposit.topup.required` | `DEPOSIT_TOPUP_REQUIRED` | admission/account IDs, requested amount, reason |
| `admission.started` | `ADMISSION_STARTED` | admission ID, ward/bed display snapshot, admittedAt |
| `surgery.ready` | `SURGERY_READY` | case/schedule IDs, planned time display |
| `surgery.cancelled` | `SURGERY_CANCELLED` | case ID, cancellation stage, reason category |
| `settlement.completed` | `SETTLEMENT_COMPLETED` | settlement/admission IDs, outcome and totals |
| `admission.closed` | `ADMISSION_CLOSED` | admission ID, closedAt, safe follow-up text |

Current subscriptions remain until each target producer fixture is green.

## 7. Privacy and authorization

- SMS/email never includes diagnosis, detailed Lab results, medication list or surgery complication
  text. Those details are `PRIVATE_IN_APP_ONLY` and require authenticated in-app access.
- `PATIENT` reads only rows whose `patientId` equals the explicit token claim; `sub` is account ID.
- ADMIN may inspect delivery metadata; secrets and complete payloads are never logged.
- Manual send remains `ADMIN, SYSTEM` and cannot impersonate a care-finance event ID.

## 8. Error contract

| Code | Behavior |
|---|---|
| `NOTIFICATION_UNSUPPORTED_EVENT_VERSION` | bounded retry then DLQ |
| `NOTIFICATION_TEMPLATE_FIELDS_INVALID` | persist failure metadata; DLQ contract error |
| `NOTIFICATION_RECIPIENT_UNAVAILABLE` | transient retry without duplicate intent |
| `NOTIFICATION_RECIPIENT_MISSING` | terminal failed/skipped history |
| `NOTIFICATION_ACCESS_DENIED` | HTTP 403 for ownership failure |

## 9. Required tests

| Rule | Required test |
|---|---|
| duplicate event creates one intent/channel | `consume_duplicateEvent_singleIntentPerChannel` |
| deposit text is not final receipt | `render_depositReceipt_usesAdvanceTemplate` |
| refund links original transaction | `consume_refund_rendersLinkedReference` |
| insecure channel hides medical details | `render_smsMedicalEvent_excludesSensitiveFields` |
| missing contact recorded | `consume_missingContact_persistsFailedHistory` |
| Patient outage retried | `consume_patientLookupOutage_doesNotMarkProcessed` |
| PATIENT ownership enforced | `getNotification_otherPatient_forbidden` |
| event fixtures deserialize | `careFinanceFixtures_roundTripAllTemplates` |

## 10. Rollout and Definition of Done

Add schema/template registry first. Bind each target routing key only after its producer fixture
passes. Done means redelivery is harmless, privacy tests pass, delivery failure is isolated from
producer workflows, outbox publication is crash-safe and all notification history remains auditable.

## Implementation follow-up — 2026-10-08

Actual current migrations are V3 Surgery reminder evidence and V4 semantic notification sources;
the DDL above remains target design, not permission to rewrite V1/V2. Four gated Surgery subscriptions
use actual producer bytes, exact pinned snapshot suppression and absorbing cancellation/completion.
READY is provisional IN_APP only; completion has no clinical-result template. A separate gated
Billing `invoice.created` V1 Surgery request notice is explicitly not a receipt or booked surgery.
Source re-delivery under a new event ID does not send again. Claim/source/history/held sent outbox
are transactional with bounded retry/DLQ recovery. Full clean **145/145**, 19 fresh reports, no
fail/error/skip, real PG/Rabbit (2026-10-08). Admission/refund/top-up/settlement and reviewed live
release remain open. Precise schema/semantics are in CONTRACT-CARE-PROJECTIONS-01, not duplicated here.
