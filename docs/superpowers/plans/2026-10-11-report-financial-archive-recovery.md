# Report V2 — local financial archive recovery and historical discovery

Owner: Huy. This continuation changes Report and its documents only. Existing Pharmacy/Billing/
Surgery and other owners' changes are preserved. No Gateway/Common/root build/scripts/CI/frontend
edits, feature activation, held release, Git synchronization, commit or push.

## Source discovery

The user requires pre-V2 history and asked the agent to discover the source and decide the path.
Read-only discovery checked repository files including ignored/hidden files (excluding Git/build/
dependency trees), relevant Git history filenames, project sibling directories, matching top-level
Documents/Downloads files/directories and Docker inventory/mounts. No production dump/event export
was found there. This is scoped discovery, not a claim to have searched the entire computer/cloud.

The named `mediflow_mediflow-pgdata` volume contains a PostgreSQL 16 cluster dated July–August.
It had no attached running container. Its files were mounted READ-ONLY and copied into
`mediflow-history-audit-20261011`. The isolated PostgreSQL clone used `--network none`, no TCP
listener and default read-only transactions. Only metadata/counts/time horizons were queried;
no patient records, secrets or event payloads were printed.

- `mediflow_billing`: no public tables.
- `mediflow_report`: no public tables.
- `mediflow_pharmacy`: prescription, dispense_slip, drug and processed_event each **0 rows**;
  no minimum/maximum business timestamp exists in those empty tables.
- The observed Rabbit volume is 568 KiB; its four visible persistent/transient `.rdq` segments
  each have length 0. No broker was started or modified to obtain this observation.
- The exited randomized PostgreSQL/Rabbit containers are labeled Testcontainers. They are test
  infrastructure, not accepted pre-V2 business history. The unrelated Banking DB is out of scope.

Original MediFlow volumes are unchanged. The inspection clone container is stopped; its separate
volume remains recoverable. Nothing was deleted. An empty old local installation does not prove
that no real historical transactions existed on another machine, deployment or deleted archive.
Do not use it as a historical no-events approval.

## Implemented recovery path

Sealed V23 local sources now have portable schema-version-1 export/restore. The archive preserves
original business/source/delivery IDs, exact typed snapshot bytes, business-time nanoseconds,
source/envelope/snapshot hashes and APPLIED/PENDING/REJECTED state. Metadata and ordered source
inventory have validated SHA-256 fingerprints; those are integrity checks, **not signatures or
producer completeness evidence**. Empty archives are not approved zero-history coverage.

Limits remain 10,000 inputs, 1 MiB/snapshot, 16 MiB total snapshots, 32 MiB wire body and 366
completed days. Strict JSON/schema/hash checks reject damage, changed metadata, duplicates, missing/
unknown keys, trailing JSON and unsupported versions. Database observation precision is enforced
rather than silently truncated; original business-time nanos survive inside the snapshots.

Restore creates only a new isolated BUILDING V23 generation. V24 records immutable original/new
generation, archive digest, authenticated operator and restore timestamp. Database fencing makes
retries/replicas restore the same archive once. Applied sources must decode and satisfy the existing
five-metric projector before commit. Pending/rejected remains unresolved even if newer live sources
are present. Source/seal/audit writes are atomic; failures leave no partial generation. Existing
bounded advance/reconciliation still verifies amounts. No producer database/REST, live source,
payment/outbox, command publication or financial read pointer is touched.

Dual-gated ADMIN-only routes default OFF:

- GET `/api/v1/reports/financial/source-archives/{generationId}` downloads no-store/nosniff evidence.
- POST `/api/v1/reports/financial/source-archives` accepts bounded octet-stream data and takes the
  operator UUID from the verified access token. No request filename/URL or client-supplied actor.

`MEDIFLOW_REPORT_FINANCIAL_ARCHIVE_ENABLED` is independently false. MANAGER cannot download the
patient-reference archive, unlike aggregate report reads. Missing generation is unavailable, not
an empty successful backup. Use encrypted operator-controlled storage and do not commit/log backup
payloads. No automatic upload/encryption, producer historical import or Gateway route change is added.

## Verification

Verification checkout: `D:/HK4/EProject/.mediflow-v2-verify-20261010`, Java 21, actual PostgreSQL 16
and Docker/Testcontainers API 1.44.

- Affected selection **83 tests / 5 suites**, 0 failure/error/skip, Maven exit 0:
  `report-history-archive-target-corrected.log`. Includes 6 new actual PostgreSQL recovery cases,
  strict codec/schema, default-off gates, role/signed-token checks and architecture. The initial
  selection failed test compilation on an ambiguous generic AssertJ overload; the test now stores
  the typed result before asserting, without changing production behavior or weakening assertions.
- Actual recovery restores exported unchanged Billing-fixture sources into a second empty migrated
  Report database. It rebuilds exactly the original cash/refund/earned/liability/receivable and
  retains all source IDs/bytes/hash proofs, with zero live sources and zero V20 publication rows.
- Concurrent operators restore once; PENDING stays pending and fails isolated replay; audit storage
  failure rolls back all sources/metadata; invalid applied facts roll back; immutable audit rejects
  update/delete. Database restart or missing upstream history is not inferred from these fixtures.
- Clean full Report snapshot: **989 tests / 100 suites**, 0 failure/error/skip, Maven exit 0,
  `report-history-archive-full.log` (9 min 13 sec). This precedes the final enum/numeric-state,
  sub-microsecond-observation and unknown-generation hardening.
- Latest affected selection: **93 tests / 6 suites**, 0 failure/error/skip, Maven exit 0,
  `report-history-archive-latest.log` (52.725 sec): codec 23 + application 4 + configuration 1 +
  PostgreSQL rebuild/recovery 40 + web/security 19 + architecture 6. Overlapping runs are not summed;
  no new full 995-test run is inferred from mixed XML reports.
- **54 distinct new cases** pass. PostgreSQL/RabbitMQ are real containers; no new test is skipped.
- Final source-tree/POM SHA-256 audit includes non-runtime source placeholders as well: Report
  **339**, Pharmacy **347**, Billing **400** files, **zero differences** from the verification copy.
  This audit's broader file set differs from earlier runtime-only counts. `git diff --check` passes.

## Original plan accounting and boundary

**54/70 (77.14%), 16 original tasks open.** This advances original R-01.4.2 but does not close its
accepted producer historical export/coverage/retention requirement. No new checklist IDs/denominator
or fixture-based history approval is introduced. **6 true original closures** are still needed for
60/70; local archive recovery alone cannot supply them.

The path chosen is explicit: preserve existing evidence, support audited local backup/restore, keep
legacy compatibility reports and unavailable historical V2 coverage separate, and require actual
producer archives/exports or a supported isolated legacy conversion for history. Post-final financial
corrections, Pharmacy medication/order/placement authority, staffed-bed/census facts and controlled
publication remain independent open work; this batch does not silently waive them.
