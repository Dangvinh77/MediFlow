# HANDOFF — Inpatient deposit and settlement

**Status:** ACTIVE — opt-in initial deposit request issuance and discharge-freeze/settlement
(no-insurance, no-deposit-recognition path) exist (default off); top-up blocked on an undecided
business policy; insurance-adjusted settlement and deposit recognition open; live publication/
Docker proof pending.
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

### Discharge freeze and settlement (2026-10-10)

Opt-in `discharge.medically.approved` consumer added: `AdmissionSettlementService` freezes charge
intake on the exact admission account (OPEN → CHARGE_CLOSED), matching patientId defensively.
Opt-in `POST /api/v1/billing/accounts/{id}/settlements` (ADMIN/CASHIER) added: computes
`grossAmount`/`allocatedPayments`/`patientLiability`/`balance` per the §4 equations from persisted
ledger rows, persists a new immutable `SETTLEMENT` version (supersedes-linked), and:
- positive balance → creates a `SETTLEMENT` `PAYMENT_REQUEST` for the exact remaining per-charge
  balance (paid later through the existing generic ledger payment command — no new writer needed);
- negative balance → records `REFUND_DUE` only; the cashier refunds through the existing refund
  command and re-calls settle() later to reach `PAID_IN_FULL`;
- zero balance, or an explicitly approved `DEBT_APPROVED`/`WAIVED` override → publishes
  `settlement.completed` and closes the account to SETTLED.

Both consumer and endpoint are gated by `mediflow.billing.ledger.enabled` +
`mediflow.billing.settlement.enabled` (both false by default); disabled leaves
`discharge.medically.approved` unbound and the endpoint absent, unchanged from before this slice.
No new migration was needed — `SETTLEMENT`/`INSURANCE_ADJUSTMENT` tables and the domain model
already existed from the original V2 ledger schema. Verified against inpatient-service's real
`discharge.medically.approved` fixture and a hand-authored `settlement.completed` producer fixture
(no canonical one existed yet from either side), both proven against the actual service output.

**Deliberately not supported, with a clear rejection rather than a guess:** a settlement payment
request for a *positive* balance while a nonzero insurance adjustment is in effect
(`BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED`) — attaching remaining per-charge
balances to that request would only sum to the pre-insurance balance, and no one has decided how an
aggregate insurance adjustment should be distributed across individual charges. Insurance recording
and the zero/negative-balance outcomes are unaffected by this gap. Deposit recognition (unapplied
deposit cash becoming earned revenue at settlement) also stays open, per CONTRACT-CARE-BILLING-01's
own "final settlement/recognition" still-open item — deposits that were never separately allocated
to a charge are simply absent from `allocatedPayments`, exactly as the §4 formula is written.

**Still open, in order:** top-up calculation/issuance (item 3) is blocked on a business-policy
decision no one has made yet — the contract does not define the trigger threshold or what
`currentBalance` means operationally (see `CONTRACT-CARE-BILLING-01` §"Deposit, top-up and
settlement"); this needs a team decision, not a guessed implementation. Settlement (item 4) now has
its no-insurance, no-deposit-recognition path implemented; the two gaps above remain. Docker E2E
proof for referral → deposit request → payment → grant → admission, and for discharge → settlement
→ close, is not run on this machine (same unresolved Testcontainers/Docker-Desktop-proxy environment
issue as the Clinical/Lab handoff — confirmed again on 2026-10-10 with Docker Desktop actually
running: both the default named-pipe and an explicit TCP/`docker_engine`-pipe connection reproduce
the identical empty `/info` response, so this is not a Docker Desktop setting to flip).

## Acceptance criteria

- Billing and Inpatient share exact fixtures for deposit request, grant, top-up and settlement.
- Duplicate and out-of-order events are idempotent; wrong admission/episode targets are rejected.
- Docker proves referral → deposit request → payment → grant → admission.
- Docker proves medical discharge → final ledger reconciliation → settlement → close.
- Emergency admission leaves an auditable receivable and does not fabricate payment.
- Only after these checks may owners enable the related flags and delete this handoff.
