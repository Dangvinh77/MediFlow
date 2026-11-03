# Cashier UI cleanup implementation plan

Approved: user “ok tiến hành”, 2026-11-04, following the Cashier UI audit. Execute inline in the current shared workspace. Preserve unrelated untracked scripts/demo_video. No commit/push requested.

**Goal:** reduce duplicate cashier actions and technical clutter on web and mobile while preserving payment receipts and uncertainty safeguards.

**Architecture:** Billing-only presentation changes with existing gateway contracts. Confirmed scheduled receipts survive independent read-only refreshes. Legacy invoices and refunds get separate navigation entries. Mobile retains its existing payment flows and mirrors clearer status/receipt presentation.

**Tech stack:** Next.js/React/TypeScript/Tailwind, Flutter/Dart, Vitest and Flutter widget tests.

- [x] Add failing scheduled-payment regressions: confirmed receipt survives failed/stale refresh, read-only retry does not submit money again, next-payment action clears selection only after completion. Retain uncertain/zero/partial-payment tests.
- [x] Replace ambiguous balance refresh with automatic bounded refresh and error-only retry; preserve confirmed receipt, hide stale dues, expose next-payment action. Fold quote details and redundant support UI.
- [x] Reduce duplicate instructions/actions in Lab/Rx lookup; show read/quote retry only when needed, retain uncertainty status checks and technical reconciliation in details.
- [x] Separate web invoice history and refund routes in BillingNavigation; remove saga column from history, retain its information in invoice detail. Validate navigation and existing invoice/refund tests.
- [x] Mobile: retain receipts outside open-payment form, translate status labels, move account/time/IDs to reconciliation details, show read retry only after failure and a return-to-queue action after a completed payment. Test stale/failed/paid refresh and uncertain retry.
- [x] Run Billing web suites, TypeScript, lint and production build; Flutter focused tests, analyze and web build. Review diff against approved scope and document exact results/limits.

## Decisions

- User approval covers the preceding Cashier cleanup proposal. Report analytics remains a separate requested follow-up; do not mix Report contract/gate changes into this cashier batch.
- Use existing workspace so the user's review sees the changes directly. No dependency/library or backend wire changes are needed for this UI cleanup.
- UI-only text/layout edits use existing regression suites; add tests for receipt retention and navigation behavior, not snapshots of every label.

## Execution and verification evidence

- Initial scheduled-payment regression tests failed for missing automatic read/retained receipt/next action, then passed after implementation. Mobile paid-refresh regression reproduced the receipt disappearing with the closed payment form, then passed after separating receipt presentation.
- Fresh final review identified two important stale-read bypasses. Both were reproduced before the fix: manual same-registration lookup erased completion on web; an intermediate mobile snapshot with an insufficient paid increment reopened collection.
- Final: fixed manual lookup bypass — `keeps completion evidence when manually looking up the same stale registration again` RED→GREEN; full web Billing suite 17 files, 124/124 tests passed.
- Final: fixed intermediate mobile stale snapshot — `intermediate stale balance cannot unlock a completed payment` RED→GREEN; Billing widget/repository suite 20/20 tests passed. Existing partial-payment fixtures now advance paidAmount consistently with remainingAmount and the receipt amount.
- Refund layout smoke check removed the duplicated page/section title and separator after route separation; focused RefundDueLookup suite 9/9 passed.
- TypeScript and full frontend lint passed. Next production build passed. Flutter analyze reports no issues; Flutter web build passed (existing CupertinoIcons font-family warning remains).
- Browser smoke: local Next dev on port 3104 with a temporary, synthetic Gateway response on port 18084. Logged in with a fixture-only CASHIER identity; verified Phí khám queue/support collapse, navigation to Lịch sử thu, and distinct Hoàn tiền route/active tab. No real payment, patient record or backend acceptance was exercised.
- Backend and Report are unchanged in this approved UI cleanup. Native Android/iOS runtime and live Gateway acceptance were not performed.
- No commit or push. Unrelated scripts/demo_video remains untouched.
