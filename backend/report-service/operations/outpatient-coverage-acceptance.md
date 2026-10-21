# Outpatient coverage approval — Report operator workflow

This is an ADMIN approval of reviewed source evidence, not automatic proof that event intake is
complete. Never approve a window merely because queues are quiet or the local preview is empty.
No source DB is read by Report. External exports must come from each source owner through their
approved workflow; this change does not invent producer export APIs or source signatures.

## Deployment and access

Use `outpatient-mvp` (alias `mvp-outpatient`). Existing care-finance and outpatient-read gates
must already be accepted for the environment. The additional approval switch is
`MEDIFLOW_REPORT_OUTPATIENT_COVERAGE_ACCEPTANCE_ENABLED`, default **false**. No gate is enabled
by this change. Importing the Report module in Support picks up the controller/configuration
and namespaced V28 migration; the Report store/transaction manager remain independent.
The canonical Spring property is `mediflow.report.outpatient-coverage-acceptance.enabled`.
The environment alias above is declared in Report's MVP profile resource; the host owner must
verify the resolved property/profile in the packaged Support deployment before an approved rollout.
Module wiring alone is not evidence of that host configuration or authorization acceptance.

Two additive routes are ADMIN-only, human access JWT with valid exp:

| Method and path | Contract |
|---|---|
| GET `/api/v1/reports/outpatient/coverage/preview?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD&departmentId=UUID` | Optional department: omission means whole scope, not an inferred department. Returns `fingerprint`, `pendingOrInvalid`, and `sources` mapping each of the five source names to `rowCount`/`fingerprint`. No coverage is created. |
| POST `/api/v1/reports/outpatient/coverage/acceptances` | Body below; 200 approval receipt, including on exact idempotent retry. ADMIN actor is the canonical UUID from the signed subject, acceptedAt is server time. No body actor/time override. |

Both use ApiResponse and correlation, with `Cache-Control: no-store` on successful responses.
MANAGER may read accepted reports, but may not inspect source witnesses or approve coverage.
Invalid body/scope/times/proof yields 400 REPORT_VALIDATION_ERROR. Changed inputs, pending/invalid
facts, mismatched source witnesses or changed reuse of a requestId yields 409 REPORT_COVERAGE_CONFLICT.
No automatic fallback or zero-fill follows an error. Source locks have a 2s acquisition timeout
and 10s statement budget; failure rolls the entire transaction back. Review/HTTP/file retrieval
never runs while locks are held. Choose a quiet bounded operational window for approval.

**Gateway source connected on 2026-10-21 by explicit user assignment:** the allowlist exposes
only the two exact methods/paths above for ADMIN, using the existing Report route to Support.
Gateway regression covers signed authentication, denied roles/methods/nested paths and HTTP
pass-through to a synthetic Support peer. Packaged Gateway→Support ADMIN preview returned 200
in the isolated 2026-10-21 acceptance runtime; this does not approve real source evidence.
See `frontend/docs/evidence/huy-six-service/2026-10-21-close/README.md` for the current evidence
and remaining source reconciliation. FE uses Gateway only.

## Review and approve

1. Choose an inclusive 1–366-day period, years 2000–2100, configured Report zone and `VND`.
   The complete final local business day must have ended. Choose the exact department scope;
   all five external source proofs must cover that same scope/window/zone/currency.
2. Obtain retained immutable exports/cursors and a completeness sign-off from **CLINICAL, LAB,
   PHARMACY, BILLING_CASH, BILLING_REFUND**, including explicitly empty sources. Verify original
   operation/payment/refund identities, revisions, dates, monetary values and deduplication.
   `sourceObservedAt` must cover the end of the final day; `reconciledAt` must be no earlier.
   Both must be at or before the server's approval time. Do not fabricate timestamps or IDs.
3. Preview the local Report window. `pendingOrInvalid=true` must be resolved by the source/intake
   owner before approval. Reconcile the external exports with the Report contributions; a local
   hash alone is not this reconciliation. Record each export's real SHA-256 and stable evidence
   reference plus the reviewed local source witness. `rowCount` counts projected evidence rows,
   not visits/payments; a dispense may contribute both prescription and units metrics.
4. Submit a new requestId (an idempotency UUID, not a clinical authority ID), acceptanceReference
   pointing to the retained review, all five proofs and the reviewed overall fingerprint.
   The full body is preserved in Report's audit table with signed actor and approval receipt.
   Export hashes are retained attestations; Report validates their format and local reconciliation,
   not inaccessible external files or cryptographic producer signatures. ADMIN is accountable for
   authentic source-owner evidence. Do not copy local preview values into unreviewed source claims.
5. Within a single transaction, Report serializes approvals, freezes only its three local evidence
   tables, rereads inputs, compares overall and per-source fingerprints/counts, and inserts coverage
   plus audit receipt together. There is no producer command, event replay, notification or money effect.
6. Read the daily report with ADMIN/MANAGER. Monthly reads require an accepted complete calendar year;
   a one-day approval does not authorize that year. Missing/wrong scope remains 404 REPORT_NOT_FOUND.
   Retain requestId, coverageId, approved evidence references and correlation; never retain bearer tokens.

POST body (all references/digests must come from the actual review):

```json
{
  "requestId": "<new idempotency UUID>",
  "fromDate": "<reviewed YYYY-MM-DD>",
  "toDate": "<reviewed YYYY-MM-DD>",
  "departmentId": null,
  "zoneId": "<configured Report zone>",
  "currency": "VND",
  "sources": {
    "CLINICAL": {"reference":"<retained export reference>","exportSha256":"<64 lowercase hex>","rowCount":0,"localFingerprint":"<reviewed local source hash>"},
    "LAB": {"reference":"<retained export reference>","exportSha256":"<64 lowercase hex>","rowCount":0,"localFingerprint":"<reviewed local source hash>"},
    "PHARMACY": {"reference":"<retained export reference>","exportSha256":"<64 lowercase hex>","rowCount":0,"localFingerprint":"<reviewed local source hash>"},
    "BILLING_CASH": {"reference":"<retained export reference>","exportSha256":"<64 lowercase hex>","rowCount":0,"localFingerprint":"<reviewed local source hash>"},
    "BILLING_REFUND": {"reference":"<retained export reference>","exportSha256":"<64 lowercase hex>","rowCount":0,"localFingerprint":"<reviewed local source hash>"}
  },
  "sourceObservedAt": "<actual observation Instant>",
  "reconciledAt": "<actual reconciliation Instant>",
  "acceptanceReference": "<retained approved review reference>",
  "expectedFingerprint": "<reviewed overall local hash>"
}
```

Zero counts in this shape are placeholders, not proof. Real reviewed values are mandatory.
Request/acceptance references are nonblank and at most 255 characters; exports/digests are lowercase
SHA-256. No source may be omitted or added. No PII or credentials belong in references.

## Retry and late facts

Retry the identical body with the same requestId and signed actor after an ambiguous response.
It returns the original immutable receipt; it does **not** refresh coverage, even if late facts
have arrived. Reusing the ID with another actor/body is a conflict. Current Report reads recompute
the witness across the complete approved window, including when reading a subrange. Late facts,
pending refunds or local drift make reads unavailable. Reconcile again and use a new requestId
and evidence reference to create a new approval. No update/delete/revoke/auto-approve API is added.

## Verification scope

Unit/web/real PG tests exercise signed JWT/RBAC, invalid proofs, all five sources, scope/time bounds,
idempotent concurrency, atomic audit failure rollback, V27→V28 upgrade and late-fact invalidation.
All fixtures are disposable synthetic reviewed evidence, not acceptance of a production dataset.
Existing legacy daily/monthly/top-medicines and current outpatient response contracts are unchanged.

Final verification on 2026-10-20, working tree based on `44ff1ed7`:

```text
mvn -q -pl backend/report-service -am -Preport-packaging-acceptance clean verify
exit 0
Report Surefire: 1440 tests / 142 suites, 0 failures, 0 errors, 0 skipped
Report Failsafe: 5 tests, 0 failures, 0 errors, 0 skipped
```

The five new suites contribute 33 tests: approval service 11, gate configuration 5, PostgreSQL 4,
signed HTTP/PostgreSQL 1 and controller/security 12. The HTTP case uses the real JWT filter,
Spring transaction proxy and PostgreSQL: absent coverage 404, MANAGER approval denied, ADMIN
approval succeeds, exact retry has one effect, ADMIN/MANAGER read successfully and DOCTOR is denied.
The reviewed source proofs in these tests are explicitly synthetic fixtures.

Packaging verifies both standalone and plain-library artifacts, all 25 SQL files byte-for-byte
at classic/namespaced locations, and three forked-JVM module probes from the actual library JAR.
This run does not start the actual Support/Gateway chain, approve real source exports, perform
browser acceptance or enable any rollout switch. See the single repository handoff for those
remaining dependencies.
