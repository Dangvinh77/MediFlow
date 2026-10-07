# HANDOFF — Pharmacy and Report care-finance activation

**Status:** ACTIVE — Report operational mappings and producer-byte PostgreSQL evidence exist; Pharmacy admission wiring, Report finance/live publication and placement policy remain open.
**Owner:** Huy (`LQHuy0210`), Pharmacy and Report.
**Unblocks:** safe Inpatient lifecycle activation and complete operational/financial reporting.

## Producer

Inpatient produces approved admission/discharge lifecycle events. Billing produces clearance, allocation/refund/settlement facts. Clinical, Lab, Pharmacy and Surgery publish their canonical operational facts. Exact contracts belong to [`CONTRACT-INPATIENT-SURGERY-01`](care-finance/CONTRACT-INPATIENT-SURGERY-01.md), [`CONTRACT-CARE-BILLING-01`](care-finance/CONTRACT-CARE-BILLING-01.md) and [`CONTRACT-CARE-PROJECTIONS-01`](care-finance/CONTRACT-CARE-PROJECTIONS-01.md).

## Consumer

- Pharmacy uses admission lifecycle only to authorize medication for an exact admission. Its current admission decoder is offline and is not a live Rabbit listener.
- Report now maps actual Clinical, Pharmacy and Surgery producer bytes with source fingerprints and PostgreSQL dedupe/rollback/replay evidence. This does not activate live bindings, finance projection or controlled publication.

## Owner actions

1. Bind Pharmacy to the approved admission lifecycle only after its exact authorization fixtures pass; stop admission medication eligibility on medical discharge.
2. Keep current placement absent until Vinh and Huy approve transfer/release/capacity identity, revision and business-time fixtures. Never reuse the initial start bed as current occupancy.
3. Connect Report's implemented operational mappers to guarded live bindings and controlled publication with source revalidation for legacy rows.
4. Implement Billing allocation/recognition/refund/settlement projection against exact fixtures; do not count deposit cash as earned revenue.
5. Prove live catch-up, finite replay, cutover and rollback without double-counting compatibility and versioned facts.

## Acceptance criteria

- Pharmacy and Report deserialize the same producer fixture bytes.
- Duplicate, out-of-order, conflicting-source and malformed events have bounded tested behavior.
- Disabled flags produce no public delivery or publication side effects.
- Docker proves admission start/medical discharge against Pharmacy authorization.
- Docker proves operational and finance facts populate Report once under replay and live catch-up.
- Placement, capacity and LOS remain absent/unavailable until their separate contracts are approved.
