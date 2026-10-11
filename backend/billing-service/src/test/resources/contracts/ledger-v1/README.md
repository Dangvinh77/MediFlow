# Billing ledger V1 producer fixtures

These are local opt-in/HELD contracts, not released runtime events or public V1 acceptance.
Consumers read the producer-owned files directly; do not copy or rewrite their identifiers.

`settlement-recognition-completed.json` is actual `FinalAdmissionSettlementService` serialization,
with deterministic generated UUIDs. It is an initial final-only operation: 120 actual deposit,
20 actual unused cash return, 100 earned split 60/40 by generating department, and 100 release at
the original receipt department. It is not a cumulative gross delta or another cash receipt.
`payment-final-recognition-deposit.json` and `refund-final-recognition-deposit.json` are pinned
against the actual payment/refund commands in `FinalRecognitionCashProducerFixtureTest`; only
generated delivery/transaction IDs normalize. Business IDs, amounts, time and original references
are unchanged. Report reads all three originals at mapper/PostgreSQL/Rabbit boundaries.
No post-final correction, debt/waiver, public acceptance or HELD release is implied.

`deposit-allocated.json` is exact `AdmissionDepositAllocationService` serialization, verified with
deterministic generated IDs only. Original deposit 100 funds charge lines 48 + 32 = 80. The fact is
always non-final, is HELD and does not itself release deposit liability or earn revenue. PostgreSQL
independently exercises actual allocation and unused-cash refund services, including original budget,
audit/outbox rollback and stale-snapshot fencing. Diagnostic ledger seeds are not an initial deposit
request issuer or live producer approval. Report/Notification do not yet bind/accept this fact.

`settlement-reconciled-{1,2,3,4}.json` are exact actual `AdmissionReconciliationService` serialization
fixtures verified in its application tests, with deterministic generated UUIDs only. Persisted-ledger
arithmetic variants are installments, original-linked refund and approved insurance; balances
40 / 0 / 15 / -5 remain non-final. PostgreSQL tests independently run the writer from real local
ledger rows and check atomic HELD outbox/replay/concurrency. Report consumes the same files at
mapper/PG/Rabbit boundaries. These are not final `settlement.completed` fixtures or reviewed release.

Prescription finance files are verified against actual Billing issuer/payment/refund services in
`PrescriptionFinanceProducerFixtureTest`. Generated event and payment/refund transaction IDs alone
normalize to the fixture's declared values; business amounts, original links, targets and times do not.

- `invoice-prescription.json`: unpaid selected outpatient Rx request, not a payment or dispense.
- `payment-prescription-first.json` / `payment-prescription-last.json`: 750 + 1250 on one request,
  separate completed SERVICE_PAYMENT transactions. Only the last installment yields the exact
  `clearance-prescription-issued.json` grant; no earned recognition is inferred.
- `refund-prescription-first.json` / `refund-prescription-remainder.json`: 250 + 500 linked only
  to the original first 750 transaction.
- `refund-prescription-last.json`: 1250 linked only to the original last 1250 transaction.

Actual completed refunds are cash-out facts. Prescription cancellation/expiry/dispense failure
creates a local adjustment/refund obligation, not these refund events. A private refund notice is
not a settlement notice. Report must keep gross inflows 2000 unchanged and separately reconcile
cash refunds 2000, including early delivery and repeated source/delivery evidence.

Pharmacy checks the exact purpose/target grant; Report checks receipts/refunds/replay; Notification
checks the private unpaid-request and actual-refund notices. Canonical semantics and release limits
are in `docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md` and
`docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md`.
