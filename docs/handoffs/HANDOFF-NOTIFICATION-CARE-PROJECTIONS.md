# HANDOFF — Notification care-finance projections

**Status:** ACTIVE — gated Surgery V1 intake/suppression, admission (deposit/started/closed) and
settlement notices are implemented and locally verified; top-up and reviewed live publication
remain open.
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

**IMPLEMENTED LOCAL Rx invoice purpose — 2026-10-09:** Billing's opt-in Pharmacy issuer captures
PRESCRIPTION `invoice.created` V1. Its producer-service fixture is
`backend/billing-service/src/test/resources/contracts/ledger-v1/invoice-prescription.json`;
[canonical fields/meaning](care-finance/CONTRACT-CARE-BILLING-01.md#opt-in-prescription-charge-issuance--2026-10-09).
The existing guarded Surgery request reader now accepts only exact SURGERY/PRESCRIPTION targets
and chooses the distinct PRESCRIPTION_PAYMENT_REQUEST private template. No competing listener
or flat legacy reader was added. Same-byte contract and PG/Rabbit tests cover source/delivery
dedupe/conflicts, rollback and privacy; a request is neither paid nor dispensed. Historical
queue/flag names stay unchanged. Actual original-linked Rx refund fixtures additionally use the
existing PAYMENT_REFUNDED private reader, not settlement. Gates and held rows remain OFF.
[Current verification and remaining limits](../superpowers/plans/2026-10-09-pharmacy-report-rx-adjustment.md).
This handoff remains active for admission/top-up/settlement and reviewed live publication.

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

**Admission and settlement notices (2026-10-12):** `CareProjectionWireHandler`/`CareProjectionService`
now also consume `admission.deposit.requested`, `admission.started`, `admission.closed` (all three
already existed) plus the newly-added `settlement.completed` (billing-service producer), creating one
private IN_APP intent each — amount/outcome only, no clinical detail, same `mediflow.notification.
care-v1.enabled` gate as the others. `settlement.completed` was blocked until Billing's own
`discharge.medically.approved`/settlement work (2026-10-10) produced a real producer fixture
(`backend/billing-service/src/test/resources/contracts/ledger-v1/settlement-completed.json`, same
bytes as Inpatient's copy); that fixture now exists, so this was unblocked and implemented, same
spirit as EXAM issuance being unblocked in the Clinical/Lab handoff by Vinh's fixture commit.
Producer bytes are consumed directly from `../billing-service/...` in tests — no fixture copy into
notification-service, matching the existing admission/surgery pattern.

**Docker E2E (2026-10-12):** `CareProjectionPostgresIntegrationTest` proves `admission.deposit.requested`
and `settlement.completed` each deliver exactly one real `NOTIFICATION` row through the real wire
handler against a real Postgres container (duplicate delivery still commits only one). This closes
the acceptance criterion "Docker proves each enabled producer event reaches one Notification intent"
for these two events; `surgery.ready`/`surgery.cancelled` already had their own Docker coverage.
`admission.started`/`admission.closed` still only have mock-level coverage. The pre-existing Docker
Desktop/testcontainers-version incompatibility that blocked any Docker test on this machine is now
fixed — see the Billing-side handoff and `CONTRACT-CARE-BILLING-01`'s Docker compatibility note.

The remaining `deposit.topup.required` and cutover criteria below stay active:
`deposit.topup.required` is still blocked because Billing itself has not built the top-up issuer yet
(undecided trigger-threshold policy — see HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT), so no real producer
fixture exists to decode against.

1. Add guarded version-1 bindings and exact decoders for `admission.deposit.requested`,
   `admission.started`, `surgery.ready`, `surgery.cancelled`, `admission.closed` (all done) and
   `settlement.completed` (done 2026-10-12). `deposit.topup.required` stays open: Billing has not
   built the top-up issuer yet, so there is no real producer fixture to decode against. Add
   `surgery.readiness.invalidated` only after its reminder-suppression intent is registered in the
   canonical projection contract; do not infer the behavior from producer bytes alone.
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
