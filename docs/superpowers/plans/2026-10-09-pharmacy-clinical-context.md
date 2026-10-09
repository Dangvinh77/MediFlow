# Pharmacy ↔ Clinical exact context — 2026-10-09

Scope: Pharmacy plus the existing user-scoped Clinical dependency override. Gateway/shared/root/
frontend are untouched; all earlier dirty work is preserved. Advances existing P-02.5.4/.5 and
H-01.2/X-01 contract verification, without a new numeric task inventory or full-V2 claim.

## Delivered

- Clinical gated service-only exact context read with bounded pharmacy-service SYSTEM JWT.
- One owned MVCC read selects appointment-backed/walk-in episode, checks relationship integrity,
  echoes exact record on absence and never exports clinical narrative or fabricates a revision.
- Same-byte original Clinical fixtures consumed through real Pharmacy Feign HTTP; strict schema,
  canonical IDs, bounded correlation/size/freshness, unavailable fallback/circuit breaker/timeouts.
- Context-checked internal creation preflights outside transactions (NEVER propagation), then joins
  the existing atomic Rx/receipt/reservation/slip/HELD writer. Exact proof is rechecked before claim,
  after receipt locks including replay, after stock locks and before save; no unverified fallback.
- Existing V0 and trusted low-level kernel remain supported. No public V1 activation or hold release.

## Verification

Final complete suites and packaged jars: Clinical **235/235 PASS**, Pharmacy **536/536 PASS**,
zero failure/error/skip, BUILD SUCCESS (2026-10-09). PostgreSQL 16/RabbitMQ ran on Docker 29.6.2.
Both extra preflight transaction tests passed: actual context caller reads outside a transaction,
stock writer commits Rx/reservation/slip/held fact atomically, and ambient transactions reject
before remote/stock effects. Receipt-wait expiry, generic SYSTEM-principal denial and noncanonical
path checks are included. Earlier selected 21/75 and full 234/534 counts overlap and are not added.
The initial PostgreSQL attempt failed before container startup while Docker engine was stopped;
it was rerun successfully after the user enabled Docker, not skipped. Local runtime uses
`-Dapi.version=1.44`, without shared dependency or CI changes. Final log:
`backend/pharmacy-service/target/huy-context-full-regression.log` (ignored build evidence).
Source fixtures live under Clinical
`src/test/resources/contracts/prescription-context-v1/` and are read directly by Pharmacy tests.
Separate owned-PG + fixture-over-HTTP tests are not a multi-JVM/Gateway medication E2E claim.
`git diff --check` passes, no unmerged index entries, and Gateway/Common/root/Compose/CI/scripts/
frontend have no changes. No commit, push, stash deletion or branch recreation was performed.

Original producer fixture SHA-256 (consumer reads these files directly):

| Fixture | SHA-256 |
|---|---|
| appointment.json | CB9385BC7F582A87C7576944A64CF99CFE379E5F384358C42EF215070B7D28C7 |
| missing.json | 463FE6DD7CE64781579F998FAD817953B3566D83FAF644A057FF59B9F0B0155E |
| walk-in.json | AB97DB118BC8ECF7BC6360B541A6469FB77F1099479B9C8E32DA76E693438D5C |

## Remaining gate

The lookup supplies necessary relationship facts only: OPEN/PRESCRIPTION disposition is not an
order/permission. Current Organization/Patient eligibility, authoritative medication-order policy,
Billing prescription request issuance/terminal adjustment, inpatient placement/discharge races,
financial revocation and reviewed held/public cutover remain open. Defaults stay false; no positive
policy fallback, invented identifiers, external DB query or completed-money fact is introduced.

Canonical wire and gate:
[CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md#additive-clinical-prescription-context-lookup--2026-10-09).

Reproduce:

```powershell
mvn -pl "backend/clinical-service,backend/pharmacy-service" -am "-Dapi.version=1.44" package
```
