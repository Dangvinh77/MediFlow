# HANDOFF — Notification care-finance projections

**Status:** ACTIVE — gated Surgery V1 intake and suppression are implemented and locally verified;
admission, top-up, settlement and reviewed live publication remain open.
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

Refund follow-up 2026-10-08: completed-refund template/strict two-gated reader now
consume actual Billing fixtures directly. Private history, source/delivery claims and held
sent fact are atomic; PG/Rabbit duplicate/conflict/rollback/DLQ/replay and full **171/171**
pass. Refund notice no longer waits for implementation. Remaining admission/top-up/
settlement, external delivery and released multi-service acceptance keep this handoff active.
[Evidence](../superpowers/plans/2026-10-08-billing-refund-closure.md).

Surgery engineering intake no longer waits for Lộc: the 2026-10-08 user-scoped override implemented
four exact subscriptions/V3 suppression and the separate Billing Surgery request notice/V4 semantic
source receipt. Final full clean Notification suite passed 145/145 (19 reports) with PostgreSQL/
RabbitMQ, zero skipped. Producer bytes are consumed directly; no fixture copies.
This is local broker acceptance, not released Surgery/Billing outboxes or production activation.
The remaining admission/top-up/settlement work and cutover criteria below stay active.

1. Add guarded version-1 bindings and exact decoders for `admission.deposit.requested`,
   `deposit.topup.required`, `admission.started`, `surgery.ready`, `surgery.cancelled`,
   `settlement.completed` and `admission.closed`. Add `surgery.readiness.invalidated` only after its
   reminder-suppression intent is registered in the canonical projection contract; do not infer the
   behavior from producer bytes alone.
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
