# Cashier, Report and mobile — source audit and proposed scope

Date: 2026-11-04. Requested by user: audit and improve Billing/Cashier and Report on web, then mobile. The explicit request includes Report web scope for this task. Status: source audit completed; design proposed; implementation pending design review. No runtime or test acceptance is claimed.

## Confirmed source findings

1. `ScheduledPaymentWorkspace.lookup()` clears the payment result, intent and selected request before reading the registration payment request. The post-completion button “Cập nhật số dư” invokes this same general lookup. It is a read operation, not a balance adjustment. This can discard the visible receipt; a failed refresh leaves the cashier without the previous visible result. Successful-payment evidence must survive a read failure.
2. The scheduled queue and Lab/Rx payment queue are separate existing workspaces. Preserve their distinct backend contracts; improve the cashier's path through them without inventing a merged authoritative balance.
3. `MonthlyReportView` already renders `MonthlyRevenueChart`, using legacy `/v1/reports/monthly`. `ReportApplicationService` zero-fills missing legacy aggregates. A zero-filled chart does not establish that current outpatient activity is zero.
4. `ReportEventConsumer` is excluded under both outpatient MVP profile names. Re-enabling the legacy consumer indiscriminately would not make versioned outpatient events valid legacy inputs.
5. `OutpatientReportResult` displays seven metrics per period but no chart. Mobile Report similarly renders metric cards without statistical charts.
6. `scripts/demo_25_10/REPORT_READINESS.md` records historical synthetic outpatient coverage for 2025-01-01 through 2025-12-31, department `9a6dd24a-1c1f-4122-9e31-7cfea345ec45`, timezone `Asia/Bangkok`. Its documented daily example is 2025-06-15. This does not cover today's date or whole-hospital requests. Evidence must still match the restored current store and configured zone.
7. Compose defaults outpatient consumer/read gates to false. Web/mobile also have outpatient UI gates. Stored historical data alone does not establish current runtime availability.

## Recommended bounded design

### Cashier

- Replace the ambiguous post-payment action with read-only “Kiểm tra trạng thái khoản thu”, automatically run a bounded authoritative refresh after confirmed completion, and show last read status/time.
- Preserve the completed transaction/operation receipt and amount/method while reading; if reading fails, keep the receipt and offer read-only retry. Never reopen collection because a stale response still says unpaid.
- Clearly separate “đã thu”, “đang xác nhận” and “chưa xác định kết quả”. Unknown outcomes retain the existing original idempotency key/payload and context locks.
- Provide a clear next-patient action after a definitive completed outcome. Retain completed receipt information for review while refreshing the queue.
- Use patient/khoa labels and purpose in primary UI; keep technical reconciliation identifiers expandable. Show zero-liability confirmation separately from cash collection.
- Evaluate printable receipt from confirmed server fields; any official receipt numbering/history requirement needs a Billing-owned contract, not a browser-generated financial identity.

### Report

- Make outpatient reporting and legacy reporting visibly distinct. Show which period/scope was loaded and whether its source is available, covered, empty or failed.
- Add financial trends for cash received, refunds and net cash; add activity trends for completed visits, lab tests and dispensed prescriptions. Keep financial and activity units separate. Retain a readable value table and negative net cash support.
- Label net cash as net cash, never earned revenue. Use server period metrics; do not reconstruct authoritative totals by joining other features.
- Expose discoverable available/accepted reporting periods from Report's own store if a suitable contract is absent. Choose a covered period only with a visible period/scope label; do not silently substitute historical data for today's report.
- Provide a historical demo shortcut only when the matching current API confirms availability; label synthetic demo data explicitly. Do not hard-code the 2025 department as a production default.
- Missing coverage must remain unavailable. Preserve source observation/reconciliation times, currency and timezone. Do not auto-accept coverage or turn on runtime acceptance gates as a cosmetic UI fix.

### Mobile parity

- Apply the same completed-payment receipt retention, status feedback and safe retry semantics.
- Add compact financial/activity charts and readable detail lists using the same Report metrics and available-period contract.
- Preserve Flutter presentation → domain ← data and gateway-only access. Distinguish source errors from no activity.

## Alternatives

1. UI-only cleanup: smallest scope, but does not solve discovering usable seed/accepted periods.
2. Recommended: cashier workflow correction plus Report period availability and charts, followed by mobile parity. Solves the visible issues while preserving existing financial authority.
3. Full cashier shift management: opening float, end-of-shift reconciliation, cashier-specific transaction ledger and official receipts. Useful future scope, but requires separate business decisions and persisted contracts; do not represent a refresh button as this feature.

## Verification required for implementation

- Lock live controllers/DTOs/security/Gateway contracts before adding reads; document any new additive contract and fixtures.
- Regression cases: completed payment plus failed refresh; stale unpaid refresh; partial payment; zero liability; uncertain submission/retry; selecting next patient; session change.
- Report cases: correct historical scope; wrong scope; missing coverage; genuinely zero metrics; negative net cash; narrow layout; stale filter/result handling; role denial.
- Run focused Billing/Report web tests plus typecheck/lint/build; relevant Maven suites for backend edits; Flutter analyze/tests/web build for mobile edits. Perform browser/runtime checks where available and distinguish fixtures from real services.

## Inspection limits

The findings above are from live repository source and checked-in seed/readiness evidence. No restored database, authenticated live API response, browser session or money operation has been inspected or modified. The working tree initially contained untracked `scripts/demo_video/`; it is outside this task and remains untouched.

## Implementation continuation — 2026-11-04

Cashier cleanup and matching mobile payment behavior shipped in PR #570 (merged into master).
The follow-up branch `codex/report-available-periods-charts` implements accepted-period discovery,
explicit synthetic labels and separate cash/activity charts in Report web and mobile. The new
Gateway GET rule mirrors Report ADMIN/MANAGER guards. Scope selection remains explicit;
backend coverage is revalidated on discovery and report reads. The seed department/year is
never substituted as a default. No gates or accepted coverage records are modified.

Verification uses isolated PostgreSQL/RabbitMQ integration fixtures and a temporary local UI
Gateway fixture. The browser check confirms choosing 2025 updates the filter before submission,
negative net cash and separate activity lines render, and a 390px viewport has no page overflow.
This is not a claim of restored seed or packaged live Gateway/Support acceptance. Cashier shift
management and official receipt numbering remain future persisted contract work.
