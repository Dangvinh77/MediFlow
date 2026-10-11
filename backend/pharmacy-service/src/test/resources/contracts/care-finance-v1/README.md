# Pharmacy V1 proposal fixtures — NOT LIVE / NOT OWNER-ACCEPTED

These are five independent alternative lifecycle scenarios, not a single legal transition chain.
The additive `prescription.created.outpatient.v1.json` is a separate outpatient creation scenario,
round-tripped by the actual Pharmacy codec and read directly by Billing's opt-in issuer tests. It
keeps recordId distinct from the exact visit episode; it does not enable public V1 or held delivery.
The files round-trip through `PrescriptionCareEventCodec`; the existing V0 publisher/outbox bytes,
consumer queues and V1 create fence are unchanged. All fields/columns/wire names are English.

`prescription.filled` includes the exact local `dispenseId`. Every payload carries the explicit
care episode and priced snapshot. Cancellation, expiry and stock failure are charge-adjustment
facts for Billing to decide, not a Pharmacy refund command; there is no invented invoice ID.

2026-10-09 local follow-up: Billing's opt-in terminal reader checks these exact alternative bytes
against original creation proof, with early recovery/local adjustment and original-only refund
obligations. It never fabricates refunded cash. Distributed revocation/admission late adjustment
and reviewed V1 activation remain open; local consumer tests do not release the held writer.

Billing/Clinical/Inpatient/Notification owners must use these same bytes in their consumer tests,
agree V0/V1 routing/cutover and accept the terminal adjustment rules before enabling the V1 writer.
See `docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md` for the cross-owner gate.
