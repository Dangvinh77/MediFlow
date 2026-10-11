# HANDOFF — Inpatient deposit and settlement

**Status:** ACTIVE — local initial final settlement/recognition writer, medical charge-close and deposit allocation exist; request issuance, top-up, corrections and live end-to-end close acceptance remain open.
**Owner:** Lộc (`locgit-89`), Billing.
**Unblocks:** Vinh's admission activation and administrative close.

## Producer

Billing produces purpose-scoped `financial.clearance.granted`, `deposit.topup.required` and `settlement.completed` version-1 facts. Contract fields and episode rules are canonical in [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md).

## Consumer

Inpatient consumes deposit clearance for the exact admission, projects explicit top-up requests, and accepts settlement only for the same admission before administrative close. Its consumers and producers remain disabled pending shared acceptance.

## Owner actions

Latest continuation 2026-10-10: initial finalization now persists SETTLED and atomic HELD
`settlement.completed` V1 plus an explicit final recognition companion. Initial-only policy rejects
insurance attribution, reversal, debt/waiver, historical final settlement and reopening. No event
publication or Inpatient close permission is enabled by implementation alone. Report's isolated
recognition intake and matching original cash evidence do not supply deposit request/top-up or
the full referral -> admission -> medical discharge -> close broker workflow.
[Canonical initial-final writer](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#initial-final-settlement-and-explicit-earned-operation--2026-10-10).

Continuation 2026-10-10: exact Inpatient medical proof now closes charges through the gated single
Billing dispatcher, with durable PENDING_ACCOUNT when financial context is absent. Financial issuers
cannot replace Inpatient patient authority. The independently gated allocation command funds exact
charges from one original deposit; the actual refund command can return its unused remainder without
undoing funded charges. Both remain non-final/HELD. That earlier tranche added no final settlement, initial deposit issuer,
automatic pending account creation or permission to close Inpatient has been added.
[Canonical rules](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#local-admission-deposit-allocation-and-unused-cash-refund--2026-10-10).

Continuation 2026-10-10: the user-scoped implementation override now adds a non-final snapshot
writer and Report receivable stock consumer. `settlement.reconciled` MUST NOT be interpreted as
the final `settlement.completed` required below: finalSettlement is always false and all outbound
rows remain HELD. Canonical [namespace/API/stock rules](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#non-final-versioned-reconciliation--2026-10-10).

Continuation 2026-10-09: the Billing reconciliation domain/read-only preview prerequisite is now
implemented under Huy's user-scoped override, not deferred to Lộc. It verifies persisted totals in
one bounded database snapshot, but creates no approval, allocation, settlement or event. The
remaining owner actions and acceptance criteria below stay open; the GET must never be used by
Report for REST enrichment or by Inpatient as financial clearance.
[Canonical boundary](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#read-only-admission-ledger-reconciliation--2026-10-09).

1. Create the initial deposit request from `admission.deposit.requested` with exact admission and patient references.
2. Publish live ADMISSION_DEPOSIT clearance after an authoritative payment allocation.
3. Define and implement top-up calculation, request issuance and `deposit.topup.required` outbox publication without reading Inpatient tables.
4. Reconcile the admission ledger and publish `settlement.completed` once per closeable account.
5. Preserve reversals and late-event handling without inferring an admission from patient ID.

## Acceptance criteria

- Billing and Inpatient share exact fixtures for deposit request, grant, top-up and settlement.
- Duplicate and out-of-order events are idempotent; wrong admission/episode targets are rejected.
- Docker proves referral → deposit request → payment → grant → admission.
- Docker proves medical discharge → final ledger reconciliation → settlement → close.
- Emergency admission leaves an auditable receivable and does not fabricate payment.
- Only after these checks may owners enable the related flags and delete this handoff.
