# HANDOFF — Inpatient deposit and settlement

**Status:** ACTIVE — opt-in initial deposit request issuance exists (default off); top-up blocked on an
undecided business policy; settlement unblocked but unbuilt; live publication/Docker proof pending.
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

### Initial deposit request issuance (2026-10-10)

Opt-in `admission.deposit.requested` issuer added: `AdmissionDepositRequestService` opens/reuses the
exact ADMISSION episode account and creates one `PAYMENT_REQUEST` (purpose `ADMISSION_DEPOSIT`,
amount = Inpatient's own `suggestedAmount`, target `admissionId`). No `CHARGE` row is created — a
deposit is cash/liability, never an earned charge, matching the existing `BILLING_DEPOSIT_CANNOT_ALLOCATE_CHARGES`
rule already enforced by the generic ledger payment command. Gated by `mediflow.billing.ledger.enabled`
+ `mediflow.billing.admission-deposit-consumer.enabled` (both false by default); disabled leaves
`admission.deposit.requested` unbound on `billing.q`, unchanged from before this slice. Exact replay
of the same `admissionId` returns the recorded request; a conflicting replay (different bytes for the
same admission) rejects. Live ADMISSION_DEPOSIT clearance granting needed no new code: the existing
generic `LedgerPaymentService` already grants it once the request is paid in full (same as item 2
above — already done, not separately listed as remaining work).
Verified against the real inpatient-service producer fixture, copied byte-for-byte into
`backend/billing-service/src/test/resources/contracts/admission-deposit-v1/`.

**Still open, in order:** top-up calculation/issuance (item 3) is blocked on a business-policy
decision no one has made yet — the contract does not define the trigger threshold or what
`currentBalance` means operationally (see `CONTRACT-CARE-BILLING-01` §"Deposit, top-up and
settlement"); this needs a team decision, not a guessed implementation. Settlement (item 4) is
unblocked by any missing decision — the ledger equations and algorithm are fully specified in
`backend-spec/care-finance-v2/06-billing.md` §4/§6 — but is simply not built yet (no `SETTLEMENT`
table, no `SettleAdmissionUseCase`, no REST endpoint). Docker E2E proof for referral → deposit
request → payment → grant → admission is not run on this machine (same unresolved Testcontainers/
Docker-Desktop-proxy environment issue as the Clinical/Lab handoff, confirmed again on 2026-10-10
with Docker Desktop actually running).

## Acceptance criteria

- Billing and Inpatient share exact fixtures for deposit request, grant, top-up and settlement.
- Duplicate and out-of-order events are idempotent; wrong admission/episode targets are rejected.
- Docker proves referral → deposit request → payment → grant → admission.
- Docker proves medical discharge → final ledger reconciliation → settlement → close.
- Emergency admission leaves an auditable receivable and does not fabricate payment.
- Only after these checks may owners enable the related flags and delete this handoff.
