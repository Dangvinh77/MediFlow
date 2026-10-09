# Pharmacy and Report V2 — priority implementation, 2026-10-09

Owner: Huy (`LQHuy0210`). Request: prioritize complete Pharmacy/Report V2; do not expand Surgery.
This records concrete progress in existing P-02.5.4 and R-04.2.3, not new task IDs or full V2 DONE.

## Baseline and scope

Local Huy `193bfe25` contains fetched master `5367817c` (HEAD/master ahead/behind `5/0`).
The master merge retained all pre-existing uncommitted work; the safety stash is retained.
No push or remote Huy recreation is performed by this batch. Production edits are limited to
Pharmacy and Report. Gateway, Patient, Organization, Common/root/Compose/CI/scripts and previous
Clinical/Inpatient/Billing/Notification/Surgery work are unchanged by this implementation batch.
No released migration, binding, public endpoint or held-delivery fence is changed.

## Implemented slices

### Pharmacy: current identity and checked creation

- Real service-auth Patient existence and Organization generic staff/department Feign reads,
  strict bounded JSON/canonical IDs/correlation, timeouts/circuit breaker and safe unavailable
  fallbacks. Defaults remain OFF behind care-finance-v2 plus pharmacy.identity.enabled.
- Necessary exact existing patient, active DOCTOR job and active matching department facts; never
  derive prescribing authority from team-role strings or manufacture license/order/finance proof.
  The separate Organization doctor-eligibility endpoint is not redefined by this generic consumer.
- The internal Clinical-context-checked caller requires the identity port, performs all remote
  reads before transactions, checks actor before lookup/replay, and calls the mandatory two-proof
  writer overload. No permissive fallback. Local preflight start time bounds the whole lookup.
- Context and identity are rechecked after receipt/stock waits and reservation reads, including
  replay. Drug expiry uses the final business date before effects, including crossing midnight;
  TTL uses that final clock or later persisted creation time. Existing V0/trusted internal kernel
  behavior, server pricing and atomic reservations/slip/held-event/receipt remain intact.
- Patient and generic staff HTTP tests read existing producer fixture files. Department responses
  in these tests are explicitly local contract examples, not producer/clinical-policy sign-off.

Canonical boundary: [identity lookup](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md#pharmacy-necessary-identity-preflight--2026-10-09).

### Report: actual admission-start count, not inferred discharge

- Pure actual-producer mapper: source ADMISSION/admissionId, immutable-operation revision 1,
  exact start department/episode, value 1, admittedAt in the configured reporting zone.
- One receiver transaction joins existing admission evidence validation and operational journal/
  source/delivery/contribution plus department/hospital scopes. Early close must match exact
  patient/chronology before the late start counts; it never reopens the admission.
- Same source/new delivery counts once; changed bed/emergency/time remains a source conflict even
  if the KPI scalar is unchanged. Unsupported correction/revision markers reject before effects.
- Every admission payload UUID, including optional override references, must be canonical; the
  envelope decoder's source-ID check alone does not validate patient/department/bed references.
  Additional pure-mapper cases reject abbreviated UUIDs before any admission evidence or count.
- Hospital-scope failure rolls back all evidence and metrics. Minimal accepted journal supports
  the existing finite isolated replay, without patient/bed/medical payloads or republishing.
- Existing evidence-only history is not automatically backfilled; a newly received real start must
  pass source revalidation. Administrative close alone does not contribute any metric.

Canonical boundary: [operational source identity](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#operational-source-identity-and-revision).

## Verification

Docker engine 29.6.2 is reachable. Tests use actual PostgreSQL 16/RabbitMQ containers and local
HTTP through real Feign, not cross-service SQL or a synthetic permission provider in production.

Fresh packaged CURRENT V0 distributed acceptance: **3/3 PASS**, zero failures/errors/skips, using
four rebuilt Pharmacy/Billing/Report/Notification applications, four private PostgreSQL databases
and RabbitMQ. Authenticated HTTP creates/payments, duplicate deliveries, Report JVM outage/catch-up
and broker outage/producer restart pass; no service DB is seeded/queried by that runtime test.
This is compatibility regression, not V1 identity/clearance/admission activation or full finance.
The runtime packaging precedes the final V2-only UUID hardening; its V0 routes are unchanged.

Final full suite/package results: **Pharmacy 592/592 PASS**, **Report 581/581 PASS**, zero
failures/errors/skips. These include the clock-boundary/midnight regressions, new eight admission
PostgreSQL tests and operational Rabbit/DLQ/replay coverage. The earlier combined run passed
580 Pharmacy + 576 Report and the clock selection passed 11/11; those overlapping runs are not
added to the final total of **1,173** owned-module tests. The final Report run includes five
additional canonical-payload-UUID regressions; its 72 discovered suites and Pharmacy's 89 suites
match the respective Surefire totals. Three packaged V0 runtime tests are separate evidence.

Initial test-only failures were corrected
without weakening assertions: the new Pharmacy denial check used a nonexistent table name, and
new Report admission tests did not explicitly truncate the FK-independent delivery ledger between
methods. Source schemas and production transaction semantics were not changed to hide failures.

Commands from the repository root:

```powershell
mvn -pl backend/pharmacy-service,backend/report-service -am "-Dapi.version=1.44" package
mvn -pl backend/pharmacy-service -am "-Dapi.version=1.44" package
mvn -pl backend/report-service -am "-Dapi.version=1.44" package
mvn -pl backend/pharmacy-service -am "-Dtest=PrescriptionIdentityAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl backend/pharmacy-service,backend/billing-service,backend/report-service,backend/notification-service -am -DskipTests package
mvn -f backend/pharmacy-service/pom.xml -Pdistributed-acceptance "-DskipUnitTests=true" "-Dapi.version=1.44" verify
git diff --check
```

The second full Pharmacy run includes the subsequently added clock-boundary and midnight-expiry
regressions; totals must not sum overlapping full/targeted runs. Module tests are local integration
evidence, not packaged full V1 distributed workflow, producer release or production rollout.

## Still required for complete V2

1. Pharmacy: authoritative prescribing/order policy, Billing exact Rx charge/payment-request
   issuance, revoke/supersede and cancelled/expired/failed adjustment; public adapters and reviewed
   held delivery only after those real contracts pass together.
2. Pharmacy: admission create/dispense authorizer with exact current placement/order, local terminal
   fences and medical-discharge/close versus stock races. An initial bed or outpatient clearance
   does not authorize admission medication.
3. Report: authoritative earned recognition, deposit-liability release/reversal and settlement/
   receivable facts, exact producer IDs/revisions, atomic projection and financial read contracts.
   Gross receipt/refund kernels do not constitute all five financial metrics.
4. Report: medical discharge/LOS and current occupancy/capacity source semantics. Administrative
   closedAt cannot substitute for approvedAt, medical duration or bed release.
5. Both: source/history coverage, live catch-up, controlled publication/cutover/rollback and actual
   packaged V1 workflows with duplicate/outage/restart proof. Finite VERIFIED is not live coverage.

These are remaining implementation/contract acceptance, not a claim that Huy must simply wait for
other developers. Existing task-scoped dependency overrides remain available, except Gateway and
unassigned shared paths. Public flags remain false while authority or financial facts are missing.
