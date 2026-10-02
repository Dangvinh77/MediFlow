# Pharmacy V1 proposal fixtures — NOT LIVE / NOT OWNER-ACCEPTED

These are five independent alternative lifecycle scenarios, not a single legal transition chain.
The files round-trip through `PrescriptionCareEventCodec`; the existing V0 publisher/outbox bytes,
consumer queues and V1 create fence are unchanged. All fields/columns/wire names are English.

`prescription.filled` includes the exact local `dispenseId`. Every payload carries the explicit
care episode and priced snapshot. Cancellation, expiry and stock failure are charge-adjustment
facts for Billing to decide, not a Pharmacy refund command; there is no invented invoice ID.

Billing/Clinical/Inpatient/Notification owners must use these same bytes in their consumer tests,
agree V0/V1 routing/cutover and accept the terminal adjustment rules before enabling the V1 writer.
See `docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md` for the cross-owner gate.
