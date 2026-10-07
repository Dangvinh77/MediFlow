# HANDOFF — Inpatient deposit and settlement

**Status:** ACTIVE — held deposit fixtures exist; request issuance, top-up and settlement producers remain open.
**Owner:** Lộc (`locgit-89`), Billing.
**Unblocks:** Vinh's admission activation and administrative close.

## Producer

Billing produces purpose-scoped `financial.clearance.granted`, `deposit.topup.required` and `settlement.completed` version-1 facts. Contract fields and episode rules are canonical in [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md).

## Consumer

Inpatient consumes deposit clearance for the exact admission, projects explicit top-up requests, and accepts settlement only for the same admission before administrative close. Its consumers and producers remain disabled pending shared acceptance.

## Owner actions

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
