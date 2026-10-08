# Surgery — ten creation HTTP follow-up work items

Date: 2026-10-07. Baseline: Huy `e0cd227` (master conflict resolution), preserved local changelog.
Scope: Surgery and its docs only. Gateway/shared/other-owner modules are read-only. No commit/push.

This decomposes remaining LOCAL work in existing **S-04.1, S-04.4, S-02.3, S-02.4,
S-07.6 and X-01.7**. The ten execution checks are not ten new global backlog IDs and do not close
joint-contract parents by counting local test cases. Existing global backlog remains **62** until
the full acceptance criteria of a parent pass. Fixed selection remains **18/50** accepted LOCAL.

## Execution checks

| Check | Existing plan | Implemented acceptance | Status |
|---|---|---|---|
| [x] API-01 | S-04.1 | Strict English DTO, required source references and exact selected episode mapping; no latest-record inference | LOCAL PASS |
| [x] API-02 | S-04.1 | Bounded 1..100 distinct planned item codes, positive quantities, nested validation; no prices/amount authority | LOCAL PASS |
| [x] API-03 | S-04.1 | Verified account plus signed staff recorder; requester independent, no ADMIN/missing-staff fallback | LOCAL PASS |
| [x] API-04 | S-02.4 / S-04.4 | Canonical HTTP key equals stable request UUID; HTTP/internal SYSTEM contention shares the V8 global fence | LOCAL PASS |
| [x] API-05 | S-04.4 | POST 201 + Location, replay 200 + same Location and exact original receipt; no clinical/current-state exposure | LOCAL PASS |
| [x] API-06 | S-04.4 | Direct-service ADMIN/DOCTOR allowlist, anonymous/forbidden/refresh/forged identities have no effect | LOCAL PASS |
| [x] API-07 | S-04.4 | Unknown actor/authority/amount fields denied; typed 400/401/403/404/409/422/503/500 envelopes and redacted messages | LOCAL PASS |
| [x] API-08 | X-01.7 | Independent default-off creation gate, all flag combinations, missing mandatory dependencies fail startup | LOCAL PASS |
| [x] API-09 | S-02.3 | Explicit real kernel wiring mandates authority/UoW/atomic capture; no positive provider, fallback or implicit dispatch | LOCAL PASS |
| [x] API-10 | S-07.6 / S-04.4 | Actual HTTP/JWT + owned PostgreSQL: replay reauthorization, rollback/retry, HTTP/system race and create→preop→cancel→replay | LOCAL PASS |

## Contract and activation limits

- Both business and creation API gates default false. Lifecycle and messaging gates are unchanged.
- No production requester/referral/template authority provider is installed. An HTTP body is an
  untrusted request, not proof of a clinical relationship or delegated authorization.
- HTTP rejects actors without signed staff identity, including ADMIN. The real authority must approve
  any requester/department delegation and reauthorize committed replay; no policy is invented here.
- Idempotency header equals the stable canonical lowercase business UUID, not a second channel key.
- Admission selects its exact admission ID; outpatient keeps the originally selected appointment
  or walk-in record ID and optional distinct clinical record. No external UUID is generated/inferred.
- No upstream `surgery.requested` decoder, schema change or producer fixture is fabricated.
  SYSTEM-vs-HTTP concurrency uses an **internal test command**, not accepted referral wire.
- HTTP/PG tests run the real kernel/adapters but provide explicit **test-only** authority and Clock.
  They are LOCAL vertical evidence, not Clinical/Inpatient/Organization/Billing approval or G3.
- Existing V1–V8 migrations and V7 wire fixtures are unchanged; HELD charge/cancel rows are not released.
- Gateway remains Hoàng Anh's scope. No Gateway route, role-policy or whole-root CI success is claimed.

## Verification journal

**FINAL LOCAL PASS:** clean Maven reactor command below exited **0**. Fresh Surgery discovery:
**740 tests / 68 XML reports / 0 failures / 0 errors / 0 skipped**, written 2026-10-07
**16:18:31–16:23:17 Asia/Bangkok**, with actual PostgreSQL 16 and RabbitMQ containers. All ten
execution checks above pass LOCAL; none is a substitute for production referral/authority/Gateway
or downstream approval. Main open backlog remains 62; fixed selection remains 18/50 accepted.

New coverage is **75 tests**, not 75 backlog tasks and not reruns added together:

| Fresh suite | Tests | Failure / error / skip |
|---|---:|---|
| SurgeryCreationConfigurationTest | 14 | 0 / 0 / 0 |
| SurgeryCreationApiTest | 50 | 0 / 0 / 0 |
| SurgeryCreationApiDisabledTest | 1 | 0 / 0 / 0 |
| SurgeryCreationHttpPostgresTest | 10 | 0 / 0 / 0 |

Remaining 665 tests are the existing full-module baseline, rerun on the corrected source.
ArchUnit dependency rules pass unchanged. Mandatory-port startup failures, unknown authority
fields and failure-trigger rollback are tested explicitly, not hidden by skips or permissive mocks.

**Earlier runs:** initial focused run: configuration 14 tests pass; HTTP/PG 7/8 pass with one preop
runtime-type error. Recompilation and the next focused run passed **44/44** (8 HTTP/PG, 35 API,
1 disabled API). Extra authority-injection/item negatives and two HTTP/PG cases were then added.

First clean full discovery: **740 tests**, two failures, zero errors/skips. ArchUnit correctly
rejected the controller's direct domain actor dependency; it now passes application identity to
the DTO mapper, with no domain import in the controller. The other failure was a frozen nanosecond
Clock equal to requestedAt while PostgreSQL rounds to microseconds: the fixture now advances
time before preop/cancel, preserving the production monotonic-time invariant. No tests or
architecture rule were weakened. A subsequent incremental run reported NoClassDefFoundError for an
unresolved simple type; final clean rebuild removes reliance on those artefacts. The identity mapper
uses the actual application record's verifiedStaffId accessor. Actual PostgreSQL/RabbitMQ cases ran,
with no skipped Docker acceptance. Existing broker fixture can log a scheduled query before its
test-only MockBean Clock is initialized; this is not a new creation worker or a failed test.

## Scope and retained contracts

- Git HEAD remains `e0cd227`; batch changes are uncommitted. No fetch/merge/commit/push in this batch.
- Only Surgery source/config/tests/README/.http and linked docs changed. Other owners, Gateway,
  Report/Pharmacy production, Common/root/Compose/CI/scripts are unchanged.
- V1–V8 migrations and producer fixtures remain unchanged. Retained SHA-256:
  V7 `28d36076f35670646c52b27c23914af5632e6c63dd7c83804eaf3c394a5062c8`;
  V8 `541c8c7bf6c334ad6ac12b843489ab80cf1f2fd3481c5e01ebe9713b9e000843`.
- Local links and `git diff --check` pass. Existing local changelog entry is preserved.

```powershell
mvn -q -pl backend/surgery-service -am '-Dtest=SurgeryCreationConfigurationTest,SurgeryCreationApiTest,SurgeryCreationApiDisabledTest,SurgeryCreationHttpPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' test
mvn -q -pl backend/surgery-service -am '-Dapi.version=1.44' '-Dlogging.level.root=ERROR' clean test
```
