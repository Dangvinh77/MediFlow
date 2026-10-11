# Pharmacy–Report V2 — versioned receivable stock

Date: 2026-10-10. Owner: Huy. Scope: Report plus the previously authorized Billing integration
override; Gateway and shared infrastructure remain untouched. This batch addresses existing
**R-03.5**, not a newly invented completion percentage.

## Implemented engineering boundary

Billing owns the transaction/insurance/allocation ledger; Report owns only derived projections.
New `settlement.reconciled` V1 is a non-final account arithmetic fact, never a command to discharge,
a paid receipt, earned revenue or final `settlement.completed`. Full canonical fields/rules are in
[CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#non-final-versioned-reconciliation--2026-10-10)
and [CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#versioned-non-final-receivable-stock--2026-10-10).

Billing POST requires existing CHARGE_CLOSED/SETTLEMENT_PENDING ADMISSION account, signed human
ADMIN/CASHIER actor and bounded idempotency key. No caller money or approval input exists. One
bounded REPEATABLE READ transaction fences operation key/account and persists immutable revision
plus HELD outbox. A repeat of the same key returns the original result; a different actor/account
cannot steal the key. Contention rolls back and is safe 503; retry same key. It does not change
payment/allocation/insurance/charge amounts or final account state. Final SETTLEMENT namespace is
not reused; V14 adds `admission_reconciliation`.

Report receives only this isolated default-off key. V19 persists minimal accepted inputs/proofs,
delivery claims, latest-account pointer and currency/zone/department stock. Latest full revision
wins in any arrival order; lower revisions are provenance only. Known predecessor/successor links,
immutable identity/context and exact business chronology reject contradictory source evidence.
Known future-predecessor cycles and two snapshots claiming the same missing predecessor reject
immediately, rather than waiting for that predecessor. A database unique successor fence also
protects the namespace across concurrently processed accounts/replicas.
Both hospital and department deltas commit with claims; database fences, not JVM mutexes, protect
replicas. Restoring a retained input verifies its typed-input fingerprint before deriving a delta.

`outstandingReceivable=max(balance,0)`, replacing the prior account stock rather than summing
daily balances. Completed payments/refunds/reversals in a reconciliation cannot mutate cash again.
Account department is not an earned-charge allocation; multi-department recognition remains open.

## Batch acceptance map

| Work | Implemented verification |
|---|---|
| Non-final wire and revision namespace | Actual Billing use-case serializer vs four checked-in producer files; Report reads same files, not copies |
| Bounded ledger arithmetic issuer | Pure existing AdmissionLedgerSnapshot reconciliation, explicit ready account, no caller-supplied financial evidence |
| Actor/operation idempotency | Signed access JWT/role matrix, key/account/actor conflict, repeat after later account closure |
| Atomic producer persistence | Real PostgreSQL immutable revisions, prior UUID, HELD outbox, outbox-failure rollback |
| Replica fence and retry | Real account/key race; transient concurrency retry produces one immutable result/outbox |
| Strict Report financial mapper | Exact fields/UUID/version/amount/time/equations; reject missing, floating, oversized, final and unsupported data |
| Latest stock replacement | All three-revision permutations, fourth insurance/refund-due permutations and stale/non-adjacent chronology |
| Immutable source/delivery proof | Same business/new event, same revision/different source, changed envelope/context/predecessor, retained-input corruption |
| Two-scope atomicity | Department/hospital reconciliation, currency isolation, concurrent replicas, second-scope rollback/retry |
| Guarded actual broker intake | Independent default-off gates, dedicated durable queue/DLQ, committed ACK, permanent conflict, transient retries and retained original-byte replay |

## Actual producer fixture results

| Reconciliation | Gross | Insurance | Net allocated | Balance | Receivable stock |
|---|---:|---:|---:|---:|---:|
| 1: first 60 installment | 100 | 0 | 60 | 40 | 40 |
| 2: remaining 40 installment | 100 | 0 | 100 | 0 | 0 |
| 3: original-linked refund 15 | 100 | 0 | 85 | 15 | 15 |
| 4: persisted insurance 20 | 100 | 20 | 85 | -5 | 0 |

Envelope version stays 1; business revision advances 1..4. All have finalSettlement=false.
Unit fixture insurance evidence is an existing persisted-ledger input, not implementation or
approval of an insurance command. Real writer tests separately exercise actual local ledger rows.

## Verification and checklist accounting

Fresh clean Billing **641/641 PASS** (72 XML suites); final clean Report **697/697 PASS** (79 XML
suites), actual PostgreSQL 16.14 and RabbitMQ 3.13, zero failure/error/skip. The earlier full Report
695 run preceded the two cycle/fork regression cases; the final 697 run includes those cases and
the new successor constraint. It is not added to the prior run. Architecture checks pass in both.

New distinct tests: Billing **48** (12 application/serializer, 9 gate, 6 PostgreSQL, 21 HTTP/security);
Report **77** (14 domain, 29 strict mapper, 24 PostgreSQL, 6 gate/topology, 4 real broker).
Combined new cases **125**, combined fresh full suites **1338**, not a root-reactor/Pharmacy rerun.
Pharmacy was unchanged this batch; its previous Oct-09 595 result is not presented as a new run.

Final Billing/Pharmacy/Report plus Common package completed successfully with tests explicitly
skipped for that packaging step (not another test result). Source verification compares **2127**
backend Java/SQL/JSON/YAML/XML/HTTP files against the isolated verification tree: **zero differences**;
root POM hashes match. `git diff --check` passes. Gateway/Common/root/CI/scripts, Clinical/Inpatient,
Patient/Organization and frontend have no changes from this batch. All earlier dirty work is
preserved; no fetch, commit, push, publication or production database write was performed.

Existing starting checklist was Pharmacy 25/32, Report 24/38, total **49/70 = 70%**. Current
source and full verification close the local R-03.5 stock/revision criterion, yielding Pharmacy
25/32 + Report 25/38 = **50/70 = 71.43%**, **20 open**. The 90% threshold remains **63/70**,
requiring **13 more original leaves**, not more tests or additional checkbox IDs. No other task is
closed by non-final stock: finalization/recognition/liability release, original earned reversals,
full five-metric financial read/replay, historical/retention/publication/cutover and admission
authority remain separate work. No default-on flag, manual publication approval or push is added.
