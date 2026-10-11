# Report V2 — operational verification, 2026-10-09

Owner: Huy. Existing task: **R-04.6**, no new backlog IDs/denominator.

## Scope and implementation

- Owned Report code only. Gateway and all other production services unchanged in this batch.
- Snapshot reads use parameter-bound generation/department/date/metric predicates. Hospital is
  actual SQL NULL, never a sentinel department. Exact department equality permits indexed seeks.
- Each JDBC statement has a 3-second execution timeout. Resource failure/driver cancellation maps
  to existing `REPORT_NOT_FOUND`, not successful zero or sensitive SQL details. Programming errors
  remain errors rather than being swallowed as missing coverage.
- Read row budget is inclusive days × requested metrics + one detection row; 1..366 days enforced
  independently at the adapter. Fetch size is 128. Publication query has a one-row budget.
- V18 adds only the covering scope index. No prior migration edited, no publication, backfill,
  source acceptance, new queue, financial semantics or legacy API changes.

## Existing acceptance → evidence

| R-04.6 criterion | Verification |
|---|---|
| Operations role matrix R-02 | `OperationalSnapshotReportControllerTest`: ADMIN/MANAGER/DOCTOR on both routes; six other roles and anonymous rejected before use case; malformed department rejected. Legacy finance remains ADMIN/MANAGER; V2 financial APIs remain separately open in R-02.3.2/R-03.6. |
| No clinical payload | Aggregate-only HTTP assertions; existing `OperationalContributionPostgresTest` verifies actual Clinical/Pharmacy/Surgery bytes retain no patient/narrative/results in journal. |
| Same operation/new delivery | Real PG operational contribution/replay tests; one effect and both delivery identities, conflicts roll back. |
| Close-before-start | Existing `AdmissionContributionPostgresTest`, history/evidence tests: exact admission/patient pairing and start count once, no fabricated medical discharge/occupancy. |
| Midnight/leap day | Mapper consumer-side mutations of actual fixture, precise business Instant vs later delivery; PG/replay reads 28/29 February and 1 March. Producer fixtures unchanged. |
| Two departments/hospital | Real live → frozen replay → snapshot read: two exact departments sum to hospital for each day; zero in covered empty days only. |
| Availability vs zero | Missing/partial/failed/unaccepted coverage rejected; actual PG exclusive locks cancel within bounded budget then recover to the original nonzero total; unsupported metadata rejected. |
| Query count/index plan | Real JDBC connection instrumentation: two SELECTs for both 1 and 366 days. EXPLAIN ANALYZE on 108,003 synthetic scope rows checks indexed generation/department/date conditions, no sequential scan/post-filter. PostgreSQL may choose either the natural-key or new covering index. |
| Upgrade safety | V17→V18 preserves legacy/snapshot rows, tables and old checksums, publication remains empty; re-run no-op/validation. |

Synthetic publication/volume in tests is **not** producer approval, exported history, live cutover
or an end-to-end care workflow. Financial recognition/settlement, medical LOS/staffed beds,
source corrections/retention/export and controlled publication remain their existing open tasks.

## Verification status

Initial focused run caught PostgreSQL cancellation being translated as a resource failure and a
too-specific index-name expectation. Corrected the production resource-failure handling and tested
the actual bounded index conditions without forcing the optimizer or disabling sequential scans.
Focused re-run: **62 passed, zero failure/error/skip**, PostgreSQL 16.14.

First clean regression: Pharmacy **595**, Report **615**, zero failure/error/skip; reactor exit 0,
finished 14:52:30 Bangkok. Five final new checks were added while that run was using its earlier
source snapshot. The subsequent selective attempt ran **zero tests** because timestamps left stale
compiled test classes; its success is **not accepted as verification**. A new clean Report run is
required and in progress. Prior 615 XML reports preserved outside target before cleaning.

Source copy verification after refresh: **2084** Java/SQL/JSON/YAML/XML/HTTP files match by hash,
zero mismatches; root POM matches. No tests selected but not executed are counted as passed.

Final clean current-source Report run: **620/620**, 74 fresh XML reports, zero failure/error/skip;
Maven exit 0 / BUILD SUCCESS, completed **15:01:03 Bangkok**. Pharmacy **595/595**, 90 fresh XML
reports, zero failure/error/skip in the earlier clean reactor; Pharmacy production/test source
unchanged thereafter. **1215 distinct tests** across the two modules; prior focused/regression
re-runs are not added. PostgreSQL **16.14**, RabbitMQ **3.13**, architecture tests pass.

Final Pharmacy/Report/Common package: **BUILD SUCCESS**, exit 0, completed **15:02:46 Bangkok**;
the package step intentionally skips rerunning tests and is not counted as another test result.
`git diff --check` clean. Verification commands from the matching isolated source root:

```text
mvn -pl backend/pharmacy-service,backend/report-service -am -Dapi.version=1.44 clean test
mvn -pl backend/report-service -am -Dapi.version=1.44 clean test
mvn -pl backend/pharmacy-service,backend/report-service -am -DskipTests package
```

R-04.6 is closed for the criteria mapped above. Existing V2 checklist **49/70 = 70%**: Pharmacy
25/32, Report 24/38. **21 existing leaves remain open**; achieving 90% requires closing **14 more**
to reach 63/70. No task was split/added to increase the percentage. Full V2 is not claimed complete.
Tests run from an isolated matching source copy to avoid editor-generated target interference.
No commit/push or Git history mutation requested/performed.
