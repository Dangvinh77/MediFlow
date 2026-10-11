# Pharmacy–Report V2 — Rx terminal adjustment and cash/refund acceptance

Date: 2026-10-09. Baseline: local Huy `c4172761`; earlier uncommitted creation/receipt work retained.
Owner: Huy. Billing/Notification changes use the user's existing task-scoped implementation
override. Gateway, Common, infrastructure/root build, CI and frontend remain unchanged.

## Implemented continuation

- Billing V13 and the existing default-off Rx dispatcher consume the four exact Pharmacy terminal
  keys. Strict producer/version/identity/context/item/time validation never falls back to V0.
  One immutable terminal per Rx is absorbing; changed source or reused creation/terminal delivery
  identity rejects. Original common snapshots must match. Historical NULL creation proof requires
  the original validated creation replay, never reconstruction from a terminal.
- Early terminal delivery persists PENDING. Original creation or validated creation replay applies
  it in the same transaction. Invalid business evidence is quarantined before effects; storage
  failure rolls back creation and adjustment together. No cross-service database access exists.
- OPEN-account cancel/expire/fail void only the original Rx charge, cancel its selected outpatient
  request and revoke its local grant. FILLED records absorbing evidence only, never revenue.
  Account locks serialize actual payments/refunds and adjustment. Closed-account or ambiguous
  admission allocation paths reject rather than silently choosing late compensation policy.
- Immutable original-payment refund obligations are not payment/refund transactions. The guarded
  ADMIN/CASHIER `GET /api/v1/billing/prescription-adjustments/{id}/refunds-due` reports outstanding
  original-only net amounts. Actual partial/full refunds use the existing audited cash command,
  reverse only their original allocations and append the unchanged held `payment.refunded` fact.
  No automatic bank operation, fake refund or Pharmacy money authority was added.
- Notification's existing guarded request reader additionally accepts exact PRESCRIPTION /
  OUTPATIENT_VISIT and selects the private PRESCRIPTION_PAYMENT_REQUEST unpaid template.
  Historical queue/flag names stay unchanged. No competing listener, email/SMS, contact lookup,
  paid claim or auto-dispense exists. History, source/delivery receipts and held sent fact are atomic.
- Producer-owned fixtures are generated/checked against actual Billing services: invoice, two
  installments (750 + 1250), one exact full grant and three completed original-linked refunds
  (250 + 500 + 1250). Only generated event/transaction identities normalize in producer tests.
  Report and Notification read those exact files, not copied or consumer-invented fixtures.
- Report's existing cash intake/replay accepts Rx cash refunds without new finance semantics.
  Refund-before-receipt recovery, duplicate/new-delivery dedupe and finite isolated paired replay
  must reconcile gross inflows 2000 and refunds 2000 while leaving financial publication untouched.

## Verification

Final fresh isolated four-module clean regression: BUILD SUCCESS with actual PostgreSQL 16 /
RabbitMQ 3.13 containers. Counts come from the final Surefire XML reports, not overlapping runs.

| Module | Tests | Reports | Failures / errors / skipped |
| --- | ---: | ---: | --- |
| Pharmacy | 595 | 90 | 0 / 0 / 0 |
| Report | 596 | 72 | 0 / 0 / 0 |
| Billing dependency | 514 | 63 | 0 / 0 / 0 |
| Notification dependency | 219 | 24 | 0 / 0 / 0 |

Four-module total: **1924** non-overlapping tests (1191 Pharmacy/Report, 733 Billing/Notification).
Earlier runs are historical and are not added. This includes the 31-case Billing Rx broker suite,
12 real-security adjustment-controller cases, 29-case Notification broker suite, 36-case Report
cash broker suite and 14-case paired-refund PostgreSQL replay suite. All architecture checks pass.
This is local module acceptance, not packaged public V1 distributed workflow or production release.
Final packaging of the same four modules plus required Common dependency: **BUILD SUCCESS**.
Final source recheck again matched all 2081 backend source/configuration files, zero differences,
and the root POM hash matched. `git diff --check` and trailing-whitespace checks for 42 new text
files pass. Final scope check confirms no diff in Gateway, Common/root/Compose/CI/scripts,
Clinical/Inpatient, Organization/Patient or frontend. Packaging skipped tests only after the fresh
clean regression above; it is not an additional successful test run.

Reproduction in the isolated source root:

```powershell
mvn -pl backend/billing-service,backend/report-service,backend/notification-service,backend/pharmacy-service -am "-Dapi.version=1.44" clean test
mvn -pl backend/billing-service,backend/report-service,backend/notification-service,backend/pharmacy-service -am -DskipTests package
```

Intermediate verification (not additive to final counts): strict terminal/creation/roles tests
passed, including 31 real PostgreSQL/RabbitMQ Rx creation/terminal/race/rollback cases and 12
real-security read authorization cases. Notification request contract/configuration tests passed,
and its initial 26-case broker suite passed before adding the three Rx refund cases.

The first endpoint slice exposed missing method-security configuration; importing real SecurityConfig
fixed the seven role-denial tests without weakening permission expectations. Local stale generated
classes also caused discovery/compile failures; the fresh isolated clean rebuild above resolves
verification without editing those mapper sources. Docker
stopped between runs and was safely reopened; no reset or data deletion was performed.

The editor's running Java language server was observed during repeated invalid unqualified mapper
class/discovery errors in the workspace build. Verification therefore uses an isolated source
snapshot at `C:/Users/hp/AppData/Local/Temp/mediflow-rx-verify-98441fb2d93f4775bbfdb3f2b18603c3`;
2081 backend Java/SQL/JSON/YAML/XML/HTTP files matched the working source hashes, zero differences.
No editor was killed, IDE configuration changed, mapper test weakened or shared build file edited.
Workspace failures are retained as failed evidence, not presented as successful regressions.

## Existing task mapping and honest completion limits

- P-03.2.3a: local creation/fixture dependency completed previously; this extends terminal/private
  notice acceptance, not the public workflow parent.
- P-03.5.5: PARTIAL. Local terminal adjustment/refund obligations are implemented; distributed
  revoke/supersede, live admission terminal/late compensation and released acceptance remain open.
- R-03.1/.2/.7: more actual producer cash/refund/replay acceptance, not five-metric finance closure.
- No task IDs were added, denominator changed or partial parent marked done. The last raw leaf
  checklist count remains 48/70 (~69%) until an entire remaining leaf meets its acceptance criteria;
  this number is not production readiness and cannot be represented as 90%.

Counting method: Pharmacy P-02/P-03 and Report R-01..R-04 checklist leaves only; exclude legacy
P-01 and R-02.1/.2, and exclude parents with children. Include the existing named
R-01.5.3.CASH leaf and P-03.2.3a leaf. Pharmacy 25/32; Report 23/38. Nested local slices are
not upgraded to full parent acceptance and historical totals are not summed.

Remaining V2 engineering: authoritative admission medication/order/current placement and close
fencing; distributed current clearance/revocation; public V1 workflows; financial earned
recognition/deposit liability/settlement/as-of receivables and APIs; medical discharge/LOS/bed
occupancy; approved historical coverage; reviewed held release, packaged V1 cutover and rollback.
No medical/legal policy is invented to close a checkbox. Existing V0 behavior/default gates remain.

No fetch/merge, commit or push was performed. `.changelog/entries.jsonl` and unrelated user changes
were preserved. See the [canonical plan](2026-09-25-huy-surgery-pharmacy-report.md) and active
[CARE-BILLING](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md) /
[CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md) contracts.
