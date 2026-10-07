# HANDOFF — Surgery intake and held outbound activation

**Status:** ACTIVE — Surgery owns tested version-1 held outbound fixtures/capture; upstream request intake, live case creation and dispatcher activation remain open.
**Owner:** Huy (`LQHuy0210`), Surgery.
**Unblocks:** Clinical/Inpatient request publication plus Billing, Inpatient, Notification and Report runtime acceptance.

## Producer

Clinical or Inpatient will produce `surgery.requested` only after Surgery accepts the same canonical fixture. Surgery already captures held `surgery.case.created`, `surgery.ready`, `surgery.readiness.invalidated`, `surgery.completed` and `surgery.cancelled` version-1 facts with actual serializer fixtures for outpatient and admission contexts. Durable workflow rules live in [`CONTRACT-INPATIENT-SURGERY-01`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), financial rules in [`CONTRACT-SURGERY-BILLING-01`](care-finance/CONTRACT-SURGERY-BILLING-01.md), and producer fixture instructions in [`surgery-outcomes-v1`](../../backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/README.md).

## Consumer

Surgery still needs idempotent request intake with exact patient, episode/admission, source record, procedure and requester references. Downstream services consume held Surgery facts by their producer-owned operation IDs/revisions. No side may select a latest record, admission or case by patient ID.

## Owner actions

1. Approve the exact `surgery.requested` fixture and connect it to one idempotent case-creation transaction keyed by the stable request identity.
2. Capture `surgery.case.created` from that live transaction; keep planned charge and upstream referral as distinct facts.
3. Complete reference-first, event-first, outpatient and late-terminal handling with Inpatient.
4. Complete Billing charge/reconciliation, Notification provisional/invalidation reminder, and Report live-delivery acceptance against the checked-in fixtures.
5. Release held rows through an approved source-fenced dispatcher only after every affected consumer passes the same bytes.

## Acceptance criteria

- Shared fixtures cover request plus all checked-in case-created/readiness/terminal facts.
- Duplicate/out-of-order delivery is safe; mismatched episode/case/source references are quarantined.
- A terminal fact arriving before reference registration is durably recovered; late READY never reopens it.
- Docker proves request → case creation → financial readiness → completion/cancellation through RabbitMQ.
- V7 held rows remain unpublished until cross-service acceptance and rollback evidence pass.
