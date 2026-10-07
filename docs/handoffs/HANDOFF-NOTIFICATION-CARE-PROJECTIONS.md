# HANDOFF — Notification care-finance projections

**Status:** ACTIVE — Notification has no live version-1 bindings for the admission, top-up,
settlement or Surgery lifecycle.
**Owner:** Lộc (`locgit-89`), Notification.
**Unblocks:** privacy-safe patient communication for the Care-Finance workflow.

## Producer

Billing produces invoice/payment/refund/top-up/settlement facts, Inpatient produces deposit,
admission and close facts, and Surgery produces readiness/cancellation facts. The allowed event-to-
intent mapping is canonical in
[`CONTRACT-CARE-PROJECTIONS-01`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Consumer

Notification consumes only the approved projection events through version-aware RabbitMQ bindings.
It creates delivery intents and retry history; it never authorizes a care or payment transition.
`medicalrecord.completed` currently belongs to Report operational projection only and must not gain
a Notification template unless the canonical contract is amended by its owners.

## Owner actions

1. Add guarded version-1 bindings and exact decoders for `admission.deposit.requested`,
   `deposit.topup.required`, `admission.started`, `surgery.ready`, `surgery.cancelled`,
   `settlement.completed` and `admission.closed`.
2. Reuse existing Billing notification paths only where the canonical intent is identical; keep
   deposit, top-up, receipt, refund and settlement templates semantically distinct.
3. Persist one notification intent per event ID and retain bounded retry/failure state.
4. Resolve contact only from an explicit snapshot or permitted Patient lookup. Never query Patient
   storage or fabricate an address.
5. Keep templates free of diagnosis, test-result and other sensitive clinical detail on insecure
   channels.

## Acceptance criteria

- Notification deserializes the same producer fixture bytes for every approved event above.
- Duplicate delivery creates one intent; malformed/unknown versions follow retry/DLQ policy.
- Missing contact creates a recorded skipped/failed intent with a reason.
- Template tests prove financial intents remain distinct and clinical detail is not disclosed.
- Docker proves each enabled producer event reaches one Notification intent through RabbitMQ.
- Bindings remain disabled until their producer fixture and failure-path tests pass together.
