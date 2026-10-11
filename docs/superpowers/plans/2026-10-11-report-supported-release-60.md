# Huy V2 — supported release, available history and controlled financial publication

Owner: Huy (LQHuy0210). Decision date: 2026-10-11.

## User decision and accounting

The user now authorizes a reasonable redesign if historical sources cannot be found, and asks
for 60/70 completed tasks. This supersedes the earlier same-day requirement that unavailable
pre-V2 history must prevent every release. It does NOT authorize synthetic transactions, invented
source IDs, missing-money-as-zero, or a claim that all future capabilities are implemented.

The denominator remains the frozen 70 original leaves. The release acceptance below is explicitly
revised to the contracts the current producers actually support: immutable initial operations,
explicit non-final settlement predecessor history, and final-only earned recognition. Unsupported
operational corrections/imports and post-final earned reversals remain outside this supported
release and are visible in the capability API and full-target rollout work, not silently applied.
This is a supported-release milestone, not completion of the broader architecture target.

Counting follows the existing 2026-10-09 rule: Pharmacy P-02/P-03 and Report R-01..R-04
checkbox leaves; exclude legacy P-01 and R-02.1/.2 and parents with children. The already-existing
P-03.2.3a and R-01.5.3.CASH leaves remain included, not newly added for credit. Before this batch
that gives Pharmacy 25/32 and Report 29/38, total 54/70. Six Report leaves below are the only
closures in this batch. Open parent text still records broader target work beyond that leaf metric.

## Historical-data policy

`AVAILABLE_EVIDENCE_ONLY` means a report is a finite snapshot of its accepted, retained source set.
It is not a claim that all upstream business transactions have arrived or that missing legacy
periods had no transactions. The discovered local legacy volume has no relevant rows; no fixture
or invoice aggregate is converted into production transaction/allocation identity.

Known source snapshots keep exact IDs, original delivery proof, amounts and business timestamps.
Archive export/restore retains these bytes and immutable operator provenance. A restored manifest
can rebuild independently; it cannot publish as caught up with local intake unless its fingerprint
matches the currently accepted local source set. Missing history remains explicit metadata and
financial reads outside published coverage remain 404, never a successful zero balance.

Publication cannot start before the earliest retained source business day, and a completely empty
manifest cannot establish historical no-events coverage. Internal replay may inspect an earlier
empty period, but the publisher refuses to turn it into an accepted zero-valued historical report.

Keep Report's minimal source stores and journals without automatic purge. Billing/Pharmacy
published-outbox purge stays default OFF. Archive download/import is ADMIN-only; backups must be
kept in encrypted, access-controlled operator storage, and restoration must be verified before any
future retention deletion. No backup upload, at-rest encryption provider or unavailable historical
reconstruction is claimed by the endpoint.

## Implemented workflow

1. ADMIN starts a bounded completed-day rebuild (configured reporting zone; VND; 1..366 days).
2. Each bounded 1..31-day batch atomically commits its complete rows and durable checkpoint.
3. The shared pure projector reconciles exact frozen provenance and all five amounts, both
   department and hospital scope. Pending/rejected inputs, broken predecessors or corrupt sources
   fail the generation; verification alone never creates a read pointer.
4. ADMIN publishes the VERIFIED generation with command UUID, exact manifest fingerprint,
   non-PII acceptance reference, explicit history policy and expected current generation.
5. An exclusive PostgreSQL publication fence waits for existing source writers, then READ COMMITTED
   re-reads their committed state. Any new source/state change requires a NEW freeze and rebuild;
   a moving manifest is not appended to a supposedly verified generation.
6. Exact five-metric rows and metadata copy to V20, reconcile again, then seal. Acceptance, read
   pointer and immutable audit commit together. Failure leaves the previous publication intact.
7. Exact command retries return the original receipt without switching back after later commands.
   Changed request/actor under the same command UUID is rejected. Expected-pointer conflicts fail.
8. Explicit rollback can select only a previously accepted/published generation. It preserves that
   snapshot's original as-of time; it is not represented as new/live data.

All four financial source writers/recovery kernels use a shared publication fence BEFORE account
and sorted original-transaction fences. Shared writers still run concurrently. The rare publication
uses the exclusive counterpart; this protocol fences local Report intake, not upstream databases
or undelivered Rabbit messages. A broker ACK is not upstream completeness evidence.

## Six original leaves: revised acceptance map

| Original ID | Supported-release acceptance | Concrete evidence |
|---|---|---|
| R-01.2.3 | Explicit actual producer source identity/revision matrix; initial versus supported non-final replacement; unsupported correction denied, not guessed | 12 producer fixture types, capability API, strict mapper and same-source conflict tests |
| R-01.4.2 | Available-history source horizon, retained minimal journal, restricted exact archive recovery and explicit unavailable history | V24 restore proof, source archive PG tests, coverage API; no synthetic pre-V2 reconstruction |
| R-01.6.2 | Actual partial receipts/original refunds, pending recovery, order-independent five-metric replay and correction rejection for supported contracts | Billing fixtures; existing six PG/Rabbit permutations; finite rebuild and supported-source tests |
| R-01.7.3 | Controlled finite financial catch-up, final local fence, audited atomic publication/CAS/rollback | V25 workflow and real PG publication/writer races, failure rollback, immutable audit, reader integration |
| R-03.4 | Actual pre-final cash refunds/original budget; unused-deposit liability reduction; exact original allocation/refund evidence in initial final; post-final earned correction explicitly denied | Original-linked partial-refund tests; all-five projector; final-account refund safeguard |
| R-04.1.3 | Actual immutable Clinical/Lab/Pharmacy/Surgery mappings and eight-key operational intake/replay; unsupported revisions/correction/import rejected | Producer fixtures, strict correction marker checks, existing PG/Rabbit effects/rollback/DLQ replay |

The broader target is preserved: accepted producer-supported corrections/legacy conversion,
post-final earned reversal, operational publication and complete KPI/Gateway cutover remain in
R-01.5.3/R-03.7/R-04.5.2 and the canonical target. Census inpatient-days and staffed-bed occupancy
remain R-04.2.3/R-04.3; no completed-stay metric is relabeled to close them. Pharmacy medication
authority and distributed admission races remain open. No new leaf is added for task credit.

## Operator API and rollout

All new management routes require `care-finance-v2 AND financial-publication.enabled`, default OFF.

| Method / financial path | Direct role | Behavior |
|---|---|---|
| GET `/capabilities` | ADMIN | Supported sources/revisions and explicit unsupported features; no business payload |
| GET `/coverage?from&to` | ADMIN, MANAGER | Snapshot coverage or unavailable publication/history metadata |
| POST `/rebuilds` | ADMIN | Freeze complete bounded local source set, not publish |
| POST `/rebuilds/{id}/batches?days` | ADMIN | Advance/reconcile bounded batch |
| GET `/rebuilds/{id}` | ADMIN | Durable progress; unknown generation 404 |
| POST `/publications` | ADMIN | Identified operator, fingerprint/policy/reference, expected-pointer switch |
| POST `/publications/rollbacks` | ADMIN | Previously accepted target only, immutable audit |

Prefix is `/api/v1/reports/financial`. Financial paths retain signed human access-token type,
expiry and canonical UUID subject checks. Operator identity is taken from authentication, never
the request JSON. Responses contain no patient/clinical payload. `.http` includes every route.
Management JSON rejects duplicate/unknown fields, trailing input, unsupported policy and numeric
enum shortcuts; exact command receipts preserve correlation and use no-store.
Gateway is unchanged; new operator POSTs require a separately reviewed management-plane Gateway
policy before external exposure. No gate, live deployment, HELD release or Git push is performed.

## Verification

Fresh clean full Report verification finished 2026-10-11 at 19:32:47 Asia/Bangkok:

- **1,151 tests / 108 suites; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.**
- Actual PostgreSQL 16 and RabbitMQ 3.13 Testcontainers, including publication concurrency and
  source-writer wait/fresh-read tests, source/archive restoration, all-five reconciliation and
  existing listener/legacy/security/retry regressions.
- All **351 Report source/POM files** are hash-identical to the isolated verification checkout.
  No post-run production/test source change is included under this result.
- Fresh log: `D:/HK4/EProject/.mediflow-v2-verify-20261010/report-supported-release-final-full.log`.
  XML evidence: that checkout's `backend/report-service/target/surefire-reports/TEST-*.xml`.
- Executed: `mvn -pl backend/report-service -Dapi.version=1.44 clean test` against the isolated
  checkout with the already-installed project Common dependency, not a different implementation.

Preliminary runs exposed stricter-rejection message ordering, a negative history test missing its
retained input setup, and a new test incorrectly adding hospital and department refund rows
together. The admission mapper preserves its established correction error while applying the
broader guard; receipt rejection remains earlier/strict with all no-effect assertions; historical
setup now uses retained sources; refund assertions use the hospital scope only. No test was skipped,
deleted or relaxed into accepting an unsafe publication. The final clean run above includes all
corrections; earlier targeted/full runs are not summed or presented as the final result.

The six mapped original leaves are now checked in the main plan: **60/70 = 85.71%, 10 remaining**
(Pharmacy 25/32, Report 35/38). Remaining counted leaves are P-02.2.4, P-02.5.5, P-03.1.3,
P-03.3.2, P-03.4.4, P-03.5.5, P-03.6, R-04.2.3, R-04.3 and R-04.5.2. Full-target open parents
and unsupported capabilities remain visible; this is not production readiness or a 100% claim.
Pharmacy/Billing/Surgery/Gateway suites are not newly claimed as rerun. No Git, live producer
release, operator publication against production data or default gate activation was performed.
