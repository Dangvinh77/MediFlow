# Huy V2 — financial daily/monthly read boundary, 2026-10-10

## Outcome and scope

LOCAL R-03.6 query implementation and R-02.3.2 financial API/security boundary are complete.
No new task IDs or denominator changes. Pharmacy 25/32 + Report 27/38 = **52/70 = 74.29%**,
**18 open**, **11 original leaves** still required for 90%. This is not 90% or a production-ready
five-metric financial dashboard. Missing financial source/rebuild/publication acceptance remains
tracked in R-01/R-03.3/.4 and the active handoffs.

Only Report production/tests and related docs change in this batch. Existing dirty Billing,
Notification, Pharmacy and other Report work is preserved. Gateway production/tests are read-only.
No fetch, merge, commit, push, production SQL, default-on flag or held outbox release.

## Implemented reader

- Pure FinancialAmounts and FinancialReportPublication model; inward-only ports/DTO/use-case.
- Daily 1..366 inclusive days, monthly full calendar year/12 stable periods; 2000..2100.
- Gross cash, earned revenue and refunds sum; liability/receivable use month-end closing stock.
- One exact configured currency/zone, explicit source/reconcile/publish instants and snapshotOnly.
- No patient data, diagnosis, raw journal or internal acceptance reference in API responses.
- Require explicit complete amounts for every accepted day and exact NULL hospital/department scope.
- Missing day/source/publication, unknown scope/currency/zone, incomplete/unresolved/failed or
  unsupported-version generation -> 404 REPORT_NOT_FOUND, never invented zero stock.
- Invalid/malformed query -> 400 REPORT_VALIDATION_ERROR; over-capacity sums unavailable, not rounded.
- Read-only REPEATABLE READ/8-second transaction timeout; indexed 3-second JDBC statements,
  days+1 row bound. Not an end-to-end SLA including pool acquisition/transaction startup.
- Separate care-finance-v2 AND financial-read.enabled gates, both false.
- ADMIN/MANAGER only; all seven other roles, anonymous, invalid signature/expiry/type/subject denied.
- Existing Gateway reports GET rule is compatible, without a Gateway file edit.
- Additive V20 EMPTY generation/day/publication boundary; verified metadata/amounts immutable in DB.

Canonical details: [CARE-PROJECTIONS](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md#financial-snapshot-read-boundary--2026-10-10).

## Important limits

There is NO production five-metric generation/rebuild or approval/publication writer. V20 must not
be populated by manual SQL or by promoting cash-only V13/V17/non-final V19. Synthetic read seeds
test reader behavior, NOT actual Billing recognition, refund reversal, source export, owner
approval, historical completeness, live catch-up or production rollback. Real endpoints remain
unavailable until a reviewed complete source/rebuild/publication workflow exists.
Reader/API tasks are local code criteria; financial correctness of the missing writer is separate.

## Verification

Initial targeted new tests: **78 PASS**, zero failure/error/skip, before the final future-as-of
guard, nine JDBC-boundary regressions and optional clock wiring regression. Final distinct new cases
are **89 PASS**, zero failure/error/skip:
7 amount + 7 coverage + 21 application + 7 gates + 28 HTTP/security + 10 real PostgreSQL/Spring
query integration + 9 bounded-JDBC/resource-error tests. Actual PostgreSQL 16 and RabbitMQ 3.13 support the full context, but this read
slice introduces no broker contract and claims no new event acceptance. Architecture checks pass.

Read-only Gateway verification: existing GatewayWebTest **19** and CareRouteAuthorizationTest
**127** pass, zero failure/error/skip, with unmodified source/tests. A separate temporary diagnostic
executes the compiled unmodified Gateway filter against financial GET/POST × all nine roles and
anonymous: **38 checks PASS**. These are edge-policy diagnostic checks, not new repository tests
or signed-token end-to-end Gateway-to-Report acceptance.

Final Report **786/786 PASS**, **86 XML suites**, zero failure/error/skip, completed
2026-10-10 09:58:46 Asia/Bangkok. Source was refreshed then clean-compiled for the 89-case selection;
the full suite ran immediately afterward on exactly those unchanged compiled sources. PostgreSQL
16/RabbitMQ 3.13 exercised the existing event regressions and the new HTTP-to-DB read boundary.
Earlier duplicate verification runs were stopped; their overlapping/partial results are not added
to the final total. The independent final source tree contains all changes.
The first full 785 run exposed ten context errors from duplicate default/test Clock beans, no
arithmetic failure. Regular configuration no longer registers a global Clock; the infrastructure
factory resolves the optional provided Clock or UTC once. The original integration assertions
remain intact and all 89 new cases pass after the fix; no test weakening or future-data bypass.
Report **277** Java/SQL/JSON/YAML/XML/HTTP files match the verified copy, zero differences; root
POM hashes match. No prior full-suite totals are presented as new results.

Pharmacy/Report/Billing plus Common packaging succeeded after full verification, with tests
explicitly skipped ONLY for packaging. Pharmacy/Billing code is unchanged this batch; their prior
full suites are not claimed as new runs. `git diff --check` passes, out-of-scope production/shared
diff remains empty, earlier dirty work is preserved, and no commit/push or activation was performed.
