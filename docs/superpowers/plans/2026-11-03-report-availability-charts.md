# Report availability and statistics — continuation

User authorization: continue remaining Cashier/Report work after PR #570 merged; create a new PR. Existing design: docs/reviews/2026-11-04-cashier-report-audit.md. Acting Vinh: IMPLEMENT Report backend and mobile; DELEGATED Report web/route under the user's explicit Report request. Preserve unrelated scripts/demo_video.

Goal: users can discover accepted historical scopes from Report itself and see cash/activity trends on web and mobile, without confusing missing coverage with zero activity.

## Source lock (M6-02)

- OutpatientReportController daily/monthly and OutpatientReportDTO/Period currently define the public wire. Both use ADMIN/MANAGER plus ReportScopeGuard and existing care-finance/outpatient-read gates.
- OutpatientReportReadPersistenceAdapter.load chooses the latest acceptance_order covering the exact scope/zone/currency; service validates coverage, time, pending evidence and current fingerprint. Keep these checks unchanged.
- V27/V28/V29 define coverage/acceptance ordering; no migrations or acceptance writes needed.
- Historical seed acceptance reference starts SYNTHETIC-DEMO-, all five source references share the same explicit prefix. Return a syntheticData flag only for this explicit provenance (no hard-coded department/year).
- Gateway forwards /api/v1/reports/** to Support, but each new endpoint also requires its exact role allowlist entry and route-role fixture.
- Mobile GatewayReportRepository is the parser authority; retain domain/data/presentation boundaries.

## Additive read contract

GET /api/v1/reports/outpatient/available-periods?departmentId=<optional UUID>, same gates/roles/scope guard as daily/monthly. ADMIN omitting department discovers recent accepted scopes; MANAGER must supply a currently authorized department. Up to 20 latest candidate ranges in the configured timezone/VND; every returned range must pass the existing daily reader now. DTO list: coverageId, fromDate, toDate, departmentId, currency, zoneId, sourceObservedAt, reconciledAt, syntheticData. No private source references or accepting actor exposed. Invalid/stale candidates omitted; a later report read always revalidates. Empty discovery is not zero activity or complete inventory. Daily/monthly also add syntheticData boolean, using the same explicit provenance rule. No new default gate values.

## Tasks

- [x] Backend: failing route regression; bounded candidates in read port/adapter; validate candidates through existing reader; DTO/controller/use case; unit/security and real PostgreSQL coverage tests; .http and canonical contract notes.
- [x] Web: accessible financial (received/refund/net) and activity (visits/labs/prescriptions) charts with zero/negative values; keep detail values and scope/timezone/currency; separate dispensed units. Discovery is explicit read-only action, shows labelled scopes and synthetic marker; choosing applies dates/scope visibly, never silently substitutes data; stale requests/session changes ignored.
- [x] Mobile: parse discovery via Gateway, typed accepted-period metadata, matching charts and readable values; correct missing coverage messages, preserve currency/zone/timestamps and session invalidation. Tests for parsing, stale/negative/zero, discovery/filter changes.
- [x] Verify Maven Report suite, focused web/mobile suites, typecheck/lint/build, Flutter analyze/web build and browser smoke with synthetic fixtures. Fresh final review and fixes. Publish the completed branch as a PR into master.

## Decisions

- No shift/cash-float management or official receipt numbering: those are future contract work, not defects remaining after #570.
- Discovery reads only accepted Report coverage; no auto-accept, cross-context DB, seed restoration or gate activation. A compatible accepted seed becomes discoverable when the existing runtime gates are explicitly enabled.
- Existing workspace and new codex branch: preserve user visibility and unrelated files. No deployment/merge is requested.

Ruling: final review found the exact Gateway allowlist also needs the new discovery route — forwarding prefix alone is insufficient — add ADMIN/MANAGER GET only with allow/deny regressions. Gateway belongs to Vinh under the whole-backend assignment.

## Verification evidence — 2026-11-04

- Watched route tests fail before implementation; watched mobile provenance test fail without zone/source metadata; chart regression failed before chart implementation.
- Full Report Maven suite: exit 0, 1,462 tests, no failures/errors/skips (real PostgreSQL and RabbitMQ integration fixtures).
- Full Gateway Maven suite: exit 0, 1,818 tests, no failures/errors/skips. The missing allowlist was reproduced RED before the fix; exact allow/deny tests then passed.
- Web Report: 148 tests passed; typecheck, scoped ESLint and production build passed. One pre-existing focus assertion needed waitFor for its passive effect, matching neighbouring tests; the suite then passed.
- Mobile: 396 tests passed; flutter analyze has no issues; flutter build web passed. Build reports an existing Cupertino icon font warning. This does not establish native Android/iOS acceptance.
- Browser fixture smoke: explicit discovery, visible 2025 filter, user submits before loading; two separate trends, negative net cash and synthetic provenance visible. 390px viewport has no page overflow; chart scrolls internally for readable axis labels. Temporary tab/server closed.
- Fresh reviewer found and rechecked the Gateway allowlist fix; no remaining important findings. No merge or runtime gate activation requested.
