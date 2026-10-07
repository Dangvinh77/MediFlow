# HANDOFF — Surgery intake and held outbound activation

**Status:** ACTIVE — Surgery owns tested version-1 held outbound fixtures/capture and a LOCAL internal creation kernel; upstream request intake, live case creation and dispatcher activation remain open.
**Owner:** Huy (`LQHuy0210`), Surgery.
**Unblocks:** Clinical/Inpatient request publication plus Billing, Inpatient, Notification and Report runtime acceptance.

## Producer

Clinical or Inpatient will produce `surgery.requested` only after Surgery accepts the same canonical fixture. Surgery already captures held `surgery.case.created`, `surgery.ready`, `surgery.readiness.invalidated`, `surgery.completed` and `surgery.cancelled` version-1 facts with actual serializer fixtures for outpatient and admission contexts. Durable workflow rules live in [`CONTRACT-INPATIENT-SURGERY-01`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), financial rules in [`CONTRACT-SURGERY-BILLING-01`](care-finance/CONTRACT-SURGERY-BILLING-01.md), and producer fixture instructions in [`surgery-outcomes-v1`](../../backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/README.md).

## Consumer

Surgery still needs production-authorized upstream request intake with exact patient, episode/admission, source record, procedure and requester references. Downstream services consume held Surgery facts by their producer-owned operation IDs/revisions. No side may select a latest record, admission or case by patient ID.

## Huy local implementation — 2026-10-07

The channel-neutral internal creation kernel now atomically persists the case, pinned checklist,
history, canonical V7 HELD `surgery.case.created` fact and V8 global request receipt. Exact replay
reauthorizes and preserves the original outcome; changed intent fails closed. PostgreSQL tests cover
concurrent duplicate requests, rollback at each append boundary and independent durable pending
recovery. No production referral/requester/template authority provider, HTTP/referral adapter or
live dispatcher is installed. Local evidence is not upstream or downstream acceptance.

The lifecycle kernel also implements readiness, explicit finalization, START, completion and
pre-start cancellation with remote preflight outside transactions, bounded SQL lock retries,
source reconciliation and default-off endpoint gates. It does not invent clinical/legal authority.

The charge naming decision is fixed: `surgery.case.created` for planned charges and
`surgery.completed` for actual reconciliation, envelope version 1 on `mediflow.events`, with
matching routing keys (no `.v1` suffix). `surgery.requested` remains the upstream referral, never
a second charge trigger. Billing owns catalog validity and prices. Use the exact English fields
and producer fixtures in the canonical contracts, not an alternate schema in this handoff.

Evidence:
[creation batch](../superpowers/plans/2026-10-07-surgery-creation-batch.md),
[lifecycle batch](../superpowers/plans/2026-10-07-surgery-closeable-batch.md),
[outbound contracts](../superpowers/plans/2026-10-07-huy-outbound-contracts.md).

## Remaining authority and owner inputs

- Vinh / Clinical-Inpatient: stable referral identity and exact patient/department/episode proof,
  reference registration, outpatient not-applicable and durable late-terminal handling; approved
  checklist, signer/guardian/witness and clinical result policy.
- Lộc / Billing-Notification: exact episode/case clearance validity and revocation, catalog/item
  reconciliation, adjustment semantics and provisional READY/invalidation/reschedule acceptance.
- Hoàng Anh / Organization: generic room/staff fixtures do not replace explicit revisioned room
  and interval-scoped capability grants; confirm the canonical authority and rollout evidence.
- Huy / Surgery: wire real authority providers and request adapters, enforce source/financial
  fences, then prove the full multi-service runtime flow. Existing local policy choices and
  schema rules live in [Surgery spec](../eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md)
  and [service rules](../ai/services/surgery.md), not duplicated decision tables here.
- Emergency override and post-start abort are deferred from V1. No permission/clinical bypass is
  inferred from this merge. Gateway remains Hoàng Anh's scope; historical overrides are not current
  authorization to modify it.

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
