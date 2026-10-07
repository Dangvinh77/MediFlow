# Surgery V1 producer fixtures

These fourteen JSON files are verified by the actual producer mapper/serializer assertions in
`SurgeryCareEventFactoryTest`. Consumers read these files directly; do not copy/rename wire fields.
Both ADMISSION and OUTPATIENT_VISIT are supplied for case.created, ready, readiness.invalidated,
completed and cancelled. The latter two are alternative terminal scenarios, not two valid
outcomes for the same case. Fixtures are test data, not clinical catalogue/price/policy approval.

## Billing: fixed identity and reconciliation fixtures

Exchange `mediflow.events`, producer `surgery-service`, nested envelope integer `version=1`.
Routing key equals `eventType`, without a `.v1` suffix:

- `surgery.case.created`: after case creation, planned charge source `SURGERY/surgeryCaseId`,
  `sourceRevision=1`, explicit `plannedItems`. This is not the upstream `surgery.requested` referral.
- `surgery.completed`: after actual surgery, immutable `resultId/sourceRevision=1`, exact case/episode
  and `performedItems`; `occurredAt=recordedAt`, actual care ends at `completedAt`.
- `surgery.cancelled`: immutable `cancellationId/sourceRevision=1`, pre-start stage and exact case/episode.

For either context, pair `surgery.case.created.<context>.v1.json` with ONE completion:
normal `.v1.json`, `.planned-difference.v1.json` (ITEM quantity 2 + EXTRA_ITEM quantity 0.5),
or `.unknown-price.v1.json` (UNRECOGNIZED_PRICE). Different variants are alternative isolated
scenarios using the same source IDs, NOT correction revisions; consuming them together must conflict.
Consumers must also test identical repeated delivery and identical source under a new event ID.
Billing chooses the test prices and must reject/quarantine the deliberately absent price code;
Surgery never substitutes zero or adds amount fields. No production catalogue is seeded here.

`SurgeryCareEventFactoryTest` verifies actual serializer output. `RabbitSurgeryEventPublisherAdapterTest`
sends the eight creation/completion Billing fixtures through real RabbitMQ, checks exact routing,
version headers, IDs/correlation and repeated bytes. This is transport acceptance only, not Billing
charge effects. Existing PostgreSQL tests cover atomic capture/retry/conflict/rollback. V7 rows remain
HELD, with no live create/referral caller or V1 dispatcher released by these tests.

## Consumer acceptance sequences

| Sequence | Expected acceptance |
|---|---|
| case.created → ready → completed → duplicate completed | Register exact case reference; one terminal effect, no repeated care/charge/report |
| completed → case.created → ready | Durably retain early terminal outcome; apply after reference registration; late READY never reopens it |
| case.created → ready → cancelled → duplicate cancelled | One pre-start cancellation; Billing owns adjustment, not an automatic bank refund |
| ready → readiness.invalidated → duplicate invalidation | Suppress the exact snapshot/schedule reminder once; not a new booked schedule |
| invalidation → stale ready for the same snapshot | No renewed reminder for the invalidated snapshot |
| valid outpatient outcome at Inpatient | Not applicable, without inventing an admission or treating it as malformed admission |
| same business operation/new eventId, identical payload | Semantic duplicate, no second effect |
| same business operation/new eventId, changed payload | Contract conflict, no partial effect |
| missing/mismatched patient/department/episode/case/source | Reject/quarantine; no reference or time fallback |

The table is a consumer test specification, NOT a claim all consumers already pass it. Inpatient's
current adapter tests cover admission decoding only. Billing charge/reconciliation and Notification
reminder/invalidation readers still need these contracts. All new producer rows remain HELD.

Case-created capture is internal and requires the caller's case-creation transaction. No live
referral consumer/creation route is introduced by these fixtures. READY/COMPLETE/CANCEL and readiness
invalidation capture join their implemented local mutation transaction. Private V6 intent bytes
must never be published as these V1 envelopes.
