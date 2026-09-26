# Huy — 15 independent slices, 2026-09-26

**Scope:** Huy-owned Surgery design, Pharmacy and Report; integration preparation only for other owners. **State:** `DONE_LOCAL` means the explicitly independent preparation is done, not that the parent business task, G1 contract, or G3 workflow is complete. No new cross-service ID, price, event schema, admission authorization or finance classification is treated as approved here. The task source is the [Huy implementation plan](2026-09-25-huy-surgery-pharmacy-report.md).

## Surgery (2)

### S-06c — competing room/time reservation strategies

| Strategy | Strength | Risk / required decision | Test before selection |
|---|---|---|---|
| PostgreSQL exclusion constraint over `(room_id, time_range)` for active reservations | Database rejects overlapping first insert across service replicas. | Needs a Surgery-owned/approved room identity, range convention, active-state predicate and migration support; D04/D05. | Two concurrent cases request the same interval; exactly one wins. Adjacent intervals, boundary equality, cancel/rebook and inactive room are separate fixtures. |
| Lock an authoritative room row, then check/insert | Simple serial ordering if Surgery owns the row. | A row must exist for each room; ownership, inactive state, lock ordering and timeout are D04. A case lock alone is insufficient. | Reverse-order two-room booking has bounded timeout/no deadlock; no double booking. |
| Reservation aggregate with lease/TTL | Can separate provisional hold from final `SCHEDULED`. | Expiry, renewal, recovery after crash, readiness invalidation and event version are D04/D05. | Two replicas renew/expire at the boundary; stale lease never confirms a schedule. |

Comparison deliberately does **not** select a room master or booking policy. Candidate interval semantics `[start,end)` let adjacent cases touch without overlap, but require team approval alongside preparation/cleanup buffer rules. Required negative cases: same room overlap, different room same time, same team member overlap if staff is exclusive, reschedule race, cancellation releasing only the correct reservation, expired hold, inactive resource, and repeated command. A case-level optimistic version cannot substitute for a cross-case resource constraint. No Surgery table or executable test exists yet.

### S-07e — transition/duplicate/race scenario ledger

| Case | Competing input | Required assertion after D05–D07/D12 approval |
|---|---|---|
| `SUR-RACE-01` | START versus consent revocation/clearance expiry. | Start re-evaluates all guards under approved revision/lock; at most one order succeeds, no START on stale evidence. |
| `SUR-RACE-02` | Two START commands, same and different operation keys. | One state transition/history/outbox intent; identical retry is stable, conflicting retry is rejected. |
| `SUR-RACE-03` | COMPLETE versus CANCEL after operation began. | Exactly one terminal outcome according to D07; actual performed items and financial adjustment are never lost or double-emitted. |
| `SUR-RACE-04` | COMPLETE redelivery with a new `eventId` but same business operation. | One result/revision and one consumer contribution, not just event-ID dedupe. |
| `SUR-RACE-05` | CANCEL before start versus room reschedule/lease expiry. | Released resource belongs to the correct case/version; no second case can observe a half-committed schedule. |
| `SUR-RACE-06` | Crash before/after local commit and before broker confirm/ACK. | Pre-commit has no terminal fact; post-commit replay keeps stable business/event identities and no duplicate charge/report. |
| `SUR-RACE-07` | Emergency request without approved approver or with self-approval. | No bypass unless D06 explicitly authorizes it; no fabricated consent/team/clearance. |

This ledger is a test plan, not evidence. Operation key, `expectedVersion`, cancellation stages, correction policy and actual-item payload require D05–D07; no Surgery code was added.

## Pharmacy (2)

### P-02e — current publisher/outbox inventory and missing care context

| Current fact | Huy publisher path | Existing stable content | Target gap (not yet written to wire) |
|---|---|---|---|
| `prescription.created` | `PrescriptionApplicationService.publishCreated` → `PharmacyEventPublisherAdapter` → outbox | `prescriptionId`, `patientId`, `recordId`, `departmentId`, price/item snapshot and correlation. | D08/D11/D12: exact `careContext`, conditional `admissionId`/record reference, episode/charge source and compatible version. |
| `prescription.filled` | `DispenseTransactionService.publishFilled` → same outbox | Prescription/record/patient/department IDs, total and actual dispensed item quantities. | Admission/dispense-operation identity and event classification depend on D08/D11. |
| `prescription.dispense.failed` | `RecordDispenseFailureService` and `LatePaymentCompensationService` → same outbox | Prescription/invoice/patient, reason and failed item snapshot. | Admission compensation must not reuse outpatient refund semantics; D08/D11. |
| `prescription.cancelled` | `CancelPrescriptionService` → same outbox | Prescription/patient, actor and reason. | Episode/granularity for admission cancellation; D08/D11. |
| `prescription.expired` | `ExpirePrescriptionTransaction` → same outbox | Prescription/patient and count of expired reservations; scheduled correlation may be absent. | Admission expiry/charge outcome and replay policy; D08/D11. |

`stock.low`/stock-adjustment facts are inventory facts, not a source for admission authorization. The current outbox stores serialized payload; already committed rows must be dispatched/retried **unchanged**. Any new care fields require an additive contract or new version with producer/consumer fixture tests, not rewriting old outbox JSON. Known consumers include Billing for created/failure/cancel/expiry and Report for filled; Notification/other consumers must be enumerated from the canonical event catalog before cutover.

### P-02f — legacy compatibility fixtures and decoder gate

Current Pharmacy test fixtures cover `prescription.created`, `prescription.filled`, `prescription.dispense.failed`, `payment.completed`; this slice adds local copies of Billing's existing `prescription.cancelled` and `prescription.expired` JSON fixtures and tests that deserialize to Pharmacy's event records. The copies are **byte-for-byte SHA-256 identical** to Billing's files at the current checkout: cancelled `FABF563DC0D6EE16793F6DB507FBA26CCA115715E5CFF797B4CE8047F9B4D73B`, expired `97850D58D8C9B77D6489DCD6E4B3A13D1BF714FAE5BEE5EFF85F236C1CDDE7ED`. `PharmacyEventContractFixtureTest`: **6 tests, 0 failures/errors/skips** on 2026-09-26.

Compatibility gate for a future context payload: preserve legacy outpatient decode and existing outbox rows; define `OUTPATIENT`/`ADMISSION` nullability and episode refs with D08/D11/D12; mark a semantic change as a new version; obtain Billing/Report/Notification fixture tests on the *same producer bytes* before enabling new writers. A future decoder must distinguish absent optional legacy context from malformed admission context; it must not manufacture `admissionId` from `patientId` or `recordId`. No additive care-context DTO, migration or consumer has been implemented by this slice.

## Report (8)

### R-01a — current/target subscription matrix

| Fact | Current binding/projector and source | Target need / blocker |
|---|---|---|
| `medicalrecord.created` | Bound to `report.q`; `recordId`, `examinationDate`, `departmentId` → daily visit count. | A separate completed/disposition metric needs Clinical's new fact/revision (D11); do not count created twice. |
| `lab.result.created` | Bound; `labId`, `performedDate`, `departmentId` → daily lab count. | New classified dimensions/late corrections need producer fixture (D11). |
| `prescription.filled` | Bound; `prescriptionId`, actual items, `occurredAt` converted in configured report timezone → daily prescription/drug quantities. | Admission context and multiple dispense operations depend on D08/D11. |
| `payment.completed` | Bound; `invoiceId`, `totalAmount`, `occurredAt` and optional department → legacy contribution/revenue. | Must not call a deposit earned revenue; transaction/episode/classification fact needed (D09/D11). |
| `payment.failed` | Bound; `invoiceId` reverses the stored legacy contribution (not event amount). | Not a completed ledger refund; refund transaction/source reference needs D09/D11. |
| Admission, Surgery and classified Billing facts | **Not bound today.** | Exact owner, event type/version, source operation/revision, required department/episode, effective timestamp and projector must be supplied by D07–D11. |

The five current bindings are verified against `ReportEventConsumer`/`RabbitConfig`; the sixth legacy `staff.department.changed` mentioned in the older spec is intentionally not bound by current code. Retain all five and their fixtures until D11 cutover is approved. No new binding is added from this matrix.

### R-01b — journal/replay source design

Candidate Report journal fields: immutable event identity/type/version, producer, occurredAt/correlation, minimal payload or approved encrypted reference, source business identity/revision, ingestion time, schema/projector version, processing status (`RECEIVED/APPLIED/PENDING/REJECTED`), reason and checkpoint. It must not be a second clinical record or a searchable dump of consent/diagnosis text. Restrict access, redact logs and separate retention from live projection tables.

Replay source options: (A) producer outbox/history while retention permits; (B) a durable event archive operated by the team; (C) Report's own minimal journal from its activation date. Rabbit ACK is **not** an archive. None currently proves full historical rebuild. D11 must choose source, retention, late/correction window and lawful access before migration. Test plan: payload fingerprint conflict, restart after journal-before-apply, unknown version quarantine, expired-history limit and deterministic rebuild from a finite legacy fixture set. No journal table is deployed.

### R-01c — event dedupe versus semantic contribution key

| Layer | Candidate identity | What it prevents | What it cannot prevent |
|---|---|---|---|
| Inbox | Unique `eventId` plus payload fingerprint; conflict if same ID/different bytes. | Duplicate delivery of one fact. | New event ID for the same business operation. |
| Legacy payment contribution | Current `invoiceId` plus terminal status/evidence. | Duplicate legacy invoice completion/failure. | Several legitimate partial payment/refund transactions on one account. |
| Target finance contribution | Billing `transactionId` or approved allocation/adjustment operation ID plus business revision. | Semantic duplicate without collapsing partial transactions. | Cannot be implemented until D09 supplies IDs, delta/snapshot and reversal references. |
| Target surgery/dispense contribution | Case/result operation or dispense operation ID plus revision. | Replayed/corrected outcome double count. | Cannot be guessed from `eventId` or patient; D07/D08 required. |

`R-KEY-01` same event twice → one effect; `R-KEY-02` same operation with two event IDs → one semantic contribution; `R-KEY-03` two distinct partial transactions for one invoice → two legal contributions; `R-KEY-04` same event ID with changed payload → conflict/no mutation. Existing Report regressions cover legacy variants only. No target key/index is approved.

### R-01e — atomic projection and pending prerequisite

Proposed local transaction: validate contract → claim inbox identity → persist minimal journal status → resolve exact original/business key → apply/reverse contribution and daily/monthly aggregates → mark journal applied. If an otherwise valid reversal arrives before its original, persist `PENDING` with original reference, reason, retry checkpoint and alert; do **not** fabricate date/department/amount from current event or today's clock. Resume only when the exact original arrives, atomically and once. Invalid schema/version goes to bounded retry/DLQ, not infinite pending. `R-ATOMIC-01` rollback after contribution write leaves no processed inbox; `R-ATOMIC-02` reverse-before-original survives restart; `R-ATOMIC-03` two workers resume once; `R-ATOMIC-04` wrong original never changes totals. Target code/migration awaits D09/D11 and producer fields; admission pending semantics also need D10.

### R-01g — generation-based replay proposal

Keep live projection generation `G0` readable while replay builds `G1` with a separate `(generationId,eventId/projectorVersion)` processing ledger. Pin journal/source checkpoint, projector version and report timezone; replay a finite ordered set, then catch up events after the checkpoint, reconcile counts/totals and atomically switch the read pointer only when approved. Do not delete live inbox or publish commands/notifications during replay. Tests must show same source+version+timezone produces same totals, duplicate/out-of-order facts are stable, crash resumes from checkpoint, and failed reconciliation leaves `G0` serving. Live cutover/watermark and pre-journal history depend on D11; this section does not claim a production replay source exists.

### R-03a — metric-to-Billing-fact requirements

| Metric | Minimum authoritative Billing fact | Required identity/dimensions; open D09/D11 choice |
|---|---|---|
| Gross cash received | Completed receipt/payment transaction, not invoice creation. | `transactionId`, signed amount/currency, account/episode, effective time, department allocation; delta vs snapshot. |
| Deposit liability | Deposit receipt and explicit allocation/release/settlement. | Deposit transaction, unearned balance transition, original reference and account. |
| Earned revenue | Recognized charge/allocation/settlement fact. | Charge/source ID, recognized amount, department, effective period and correction revision. |
| Completed refund | Refund transaction confirmed by Billing. | `refundTransactionId`, original payment/allocation reference, amount/currency and completed time. |
| Receivable/debt | Approved settlement/adjustment outcome. | Account/episode, outcome version, balance type, delta or replacement snapshot and original contribution. |

Questions for Lộc: which event/version provides each row, are amounts delta or full snapshots, how are multi-department allocations represented, and which timestamp determines Report's period? Existing `payment.completed` is insufficient to classify deposit/revenue; Report must not infer from `paymentMethod`, invoice total or patient.

### R-03h — expected-outcome fixture scenarios

The following are **conditional accounting examples for Billing review**, not approved producer fixtures. In an assumed scenario where a VND 1,000 deposit is received, VND 600 is explicitly recognized/allocated and VND 400 is actually refunded, gross cash receipts = 1,000, completed refunds = 400, net cash movement = 600, and remaining deposit liability = 0. Revenue = 600 **only if** Billing publishes a recognition fact for 600; it is not derived from the deposit or settlement label. Negative scenarios to fixture: two partial receipts for one invoice (both valid), duplicate transaction with new `eventId` (one contribution), refund-before-original (pending), settlement-before-allocation (no invented revenue), split 60/40 across two departments (both scopes reconcile), late correction retaining original period/scope, and cross-midnight effective time under configured timezone. D09/D11 must provide exact source IDs, signs, snapshots/deltas and expected totals before Report tests become approved.

### R-04g — read API/query/index proposal

Current read surface is exactly `GET /api/v1/reports/daily`, `/monthly`, `/top-medicines`, guarded for ADMIN/MANAGER; current daily/monthly no-data is zero-filled and top-medicines has limit 1–50. Preserve these routes/DTOs while new metrics are blocked. Candidate new query dimensions are bounded date interval, `departmentId`, episode/context and metric availability/freshness; the response must distinguish a true zero from **source not available** and include an approved `asOf`/generation when replay is introduced. New indexes follow locked date+department+source keys and measured query plans; no unbounded raw clinical payload in list results. Proposed tests: invalid/reversed/oversized ranges, stable order with equal totals, max page/limit, leap-day zero fill, department/hospital reconciliation, bounded query count and stale-generation marker. D09/D10/D11 determine new DTO meanings; no new endpoint is exposed yet.

## Integration and evidence (3)

### X-01f — distributed fault-injection plan

| Injection | Expected invariant | Executable now / later |
|---|---|---|
| Redeliver the same `eventId` and bytes; then send a different `eventId` for the same business operation. | One local state change for each logical operation; a conflicting same-ID payload is rejected, not silently accepted. | Legacy Pharmacy/Report duplicate tests exist. Cross-service semantic-key tests wait for D07–D09/D11 producer operation IDs. |
| Reverse an original and its correction/refund/close; restart between them. | The later-dependent fact remains durable and pending; no date, department or amount is guessed. | Legacy Report payment-order regression exists; target journal/pending code waits for D09–D11. |
| Crash before DB commit, after DB commit but before publish confirm, and after consumer commit but before ACK. | Pre-commit yields no fact; post-commit retries preserve outbox/business identity; consumer replay adds no second contribution. | Pharmacy outbox and Report legacy retry tests can run now. Surgery and target finance paths wait for G1 contracts and service implementation. |
| Stop broker, let retry/DLQ thresholds elapse, restore broker, replay one poisoned and one valid message. | No lost committed outbox row, unbounded retry loop or double effect; poison stays inspectable with correlation. | Existing Rabbit/Testcontainers behavior is testable; new event routing, replay tooling and alert policy need D11. |
| Timeout an authoritative eligibility/clearance lookup, then restore it; race with evidence expiry. | Fail closed without manufacturing `READY`, dispense authorization or approval; later retry revalidates freshness. | Surgery/admission variants wait for D01/D05/D08/D12 and their producers. |
| Run two replicas against one room/operation/payment source. | One reservation/transition/contribution at the agreed business granularity. | Pharmacy/Report legacy DB concurrency is testable; Surgery room and classified Billing source keys wait for D04/D07/D09. |

For every executed case record producer bytes/version, operation and event IDs, commit/ACK injection point, PostgreSQL/Rabbit versions, observed state/outbox/inbox/aggregate counts, and recovery result. The table is a **plan**, not a claim that the new distributed workflow passed.

### X-01g — observability and privacy inventory

Pharmacy currently exposes gauges `mediflow.pharmacy.outbox.pending`, `mediflow.pharmacy.outbox.oldest-age-seconds`, `mediflow.pharmacy.outbox.quarantined`, and `mediflow.pharmacy.outbox.oldest-quarantined-age-seconds`. Report currently has bounded Rabbit retry/DLQ routing (`report.dlq`) and metadata-oriented accepted/recovery logs. These do **not** establish end-to-end projection lag, pending-prerequisite age or replay progress. A dashboard should separate pending outbox from quarantined poison, ingress from applied projection, and historical replay from live processing; owner-approved alert thresholds and on-call policy remain open.

Trace checklist: accept or generate a correlation ID at Gateway, preserve it in Huy command/outbox/event headers where contracts allow, include event type/version/source ID and non-sensitive operation ID in consumer logs, and verify the ID survives retry/DLQ. Do not log bearer tokens, full patient/clinical/consent payloads, financial account details or raw event JSON. Use counts/ages and redacted identifiers for operational metrics; ensure dashboards do not make patient IDs high-cardinality labels. Huy can add metrics inside Pharmacy/Report after metric meanings are specified; Gateway/shared monitoring remains another owner's scope. No new metric or alert is claimed as deployed by this slice.

### X-01h — evidence ledger and current baseline

| Item | Evidence as of 2026-09-26 | Status |
|---|---|---|
| Pharmacy legacy event decoder | `PharmacyEventContractFixtureTest`: 6 run, 0 failures, 0 errors, 0 skipped. `prescription.cancelled` and `prescription.expired` fixtures match the current Billing copies by SHA-256 (see P-02f). Targeted Maven run completed on local JDK/Maven. | `DONE_LOCAL` for P-02f legacy fixture slice only. |
| Pharmacy architecture, web and dispense orchestration | After resolving a web→domain dependency found by the full suite, `ArchitectureTest` 7, `PrescriptionControllerTest` 16 and `DispenseApplicationServiceTest` 13 pass with 0 skips; together with the decoder fixture suite, the focused rerun is 42/42. | `DONE_LOCAL` for these regression suites; not a PostgreSQL/Rabbit proof. |
| Full Huy module suites | `mvn -q -pl backend/pharmacy-service -am test`: 217 discovered, 0 failures/errors, 46 skipped. `mvn -q -pl backend/report-service -am test`: 123 discovered, 0 failures/errors, 24 skipped. The skipped tests require Testcontainers; the local Docker engine was unavailable even after launching Docker Desktop. | Non-container regression green; integrated evidence incomplete. |
| Surgery scenarios | S-06c and S-07e tables above identify races and intended assertions. No `surgery-service` module or executable Surgery tests exist in this checkout. | Preparation only; `BLOCKED` for production by D01–D07/D12 and shared module registration. |
| Report redesign | R-01/R-03/R-04 tables above are proposed designs and conditional examples. Existing Report legacy regressions are recorded in the parent plan; no classified finance, journal, generation or admission producer fixture is approved. | Preparation only; `BLOCKED` for target projection by D07–D11. |
| Integrated workflow | X-01f fault plan and X-01g telemetry checklist; no new-flow distributed run recorded. | `NOT_RUN`, not `DONE_INTEGRATED`. |

Before declaring an integrated task complete, record commit SHA/branch, Docker/Testcontainers images, exact command and suite count, fixture schema versions/hashes, scenario-by-scenario expected/actual rows, skips/failures, owning service, and linked handoff/decision approval. A plan or unit fixture is not a substitute for a live producer/consumer contract test. The parent plan retains open checkboxes for all gated production work.

Working-tree baseline for this evidence: branch `Huy`, pre-change HEAD `d97ec3e`, Java 21.0.11, Maven 3.9.16. The first full Pharmacy run found the architecture violation and had 46 Testcontainers tests skipped; it is **not** recorded as a pass. A focused post-fix rerun passed 42/42; both full Huy suites then finished with zero failures/errors but the container-dependent tests still skipped. Rerun those suites with Docker connected before integrated claims or release.
