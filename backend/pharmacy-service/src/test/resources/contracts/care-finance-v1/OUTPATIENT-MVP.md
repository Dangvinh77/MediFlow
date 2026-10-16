# Outpatient MVP lifecycle fixture set — 2026-10-14

Supplement to the unchanged historical fixture README. Canonical decisions are now in
[MVP contracts](../../../../../../../docs/plans/03-contracts.md) and the
[Huy activation handoff](../../../../../../../docs/handoffs/HANDOFF-HUY-MVP.md).

The existing `prescription.created.outpatient.v1.json` plus four new
`prescription.{filled,cancelled,expired,dispense.failed}.outpatient.v1.json` files are five
ALTERNATIVE scenarios, not a single state-transition sequence or production history.
They use OUTPATIENT/OUTPATIENT_VISIT, distinct record/visit identity, no admissionId, source-priced
snapshots and exact dispense proof where applicable. Event IDs are distinct across the scenarios.
All five round-trip through the actual Pharmacy codec without semantic JSON changes.

Report tests consume the filled outpatient bytes directly. Cross-owner Billing/Clinical/Notification
acceptance and live producer rollout remain open. Pharmacy local PG/broker tests now cover selective
new-cohort delivery, restart, quarantine/replay and immutable historical HELD; these examples do not
activate a deployment. Existing admission fixtures and all baseline migration bytes are untouched.
Terminal facts ask Billing to evaluate original-linked adjustments, not claim refunded cash.
