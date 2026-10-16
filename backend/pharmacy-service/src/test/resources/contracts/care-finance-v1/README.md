# Pharmacy V1 proposal fixtures — NOT LIVE / NOT OWNER-ACCEPTED

These are five independent alternative lifecycle scenarios, not a single legal transition chain.
The additive `prescription.created.outpatient.v1.json` is a separate outpatient creation scenario,
round-tripped by the actual Pharmacy codec and read directly by Billing's opt-in issuer tests. It
keeps recordId distinct from the exact visit episode; it does not enable public V1 or held delivery.
The files round-trip through `PrescriptionCareEventCodec`; V0 publisher/outbox bytes and consumer
queues remain compatible. Public outpatient V1 now has a separate proof-checked, default-off API
and selective new-outpatient delivery cohort. All fields/columns/wire names are English.

`prescription.filled` includes the exact local `dispenseId`. Every payload carries the explicit
care episode and priced snapshot. Cancellation, expiry and stock failure are charge-adjustment
facts for Billing to decide, not a Pharmacy refund command; there is no invented invoice ID.

2026-10-09 local follow-up: Billing's opt-in terminal reader checks these exact alternative bytes
against original creation proof, with early recovery/local adjustment and original-only refund
obligations. It never fabricates refunded cash. Distributed revocation/admission late adjustment
and reviewed V1 activation remain open; local consumer tests do not release the held writer.

For the outpatient MVP, Billing/Clinical/Report/Notification owners must accept these same bytes,
agree V0/V1 routing/cutover and terminal adjustment rules before enabling delivery. Inpatient is
deferred and does not block outpatient acceptance. The active gate is
[`HANDOFF-HUY-MVP.md`](../../../../../../../docs/handoffs/HANDOFF-HUY-MVP.md).

## Outpatient relationship supplied to Clinical

`recordId` is copied from the persisted prescription's proof-checked Clinical record. It is not
inferred from `patientId`, `careEpisodeId` or `dispenseId`. `careEpisodeId` is the authoritative
outpatient visit and may differ from the record. The `prescription.filled` operation identity is
the persisted `dispenseId`; delivery identity is envelope `eventId`. Patient, department, source
prescription and server-priced item snapshot remain exact. Admission ID is absent.

`OutpatientApiPostgresTest.filledProducerRelationshipComesFromPersistedPrescriptionAndSlipAndReplayKeepsExactBytes`
compares the produced held event against the actual prescription/slip rows after a signed HTTP
create/dispense, then verifies retry retains the exact stored bytes and one stock effect. Upstream
proof ports in that suite are isolated test doubles; Clinical consumer and live Billing acceptance
are separate owner gates. The five outpatient fixture files are alternative terminal scenarios;
their existence never permits an illegal terminal-to-terminal transition or historical HELD release.
