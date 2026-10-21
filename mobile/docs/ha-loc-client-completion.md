# Organization, Patient, Billing, Notification client completion

2026-10-21 — Vinh implements the frontend/mobile scope delegated by the user from
Hoàng Anh and Lộc. The backend controllers remain authoritative; this change does
not grant another context's data authority to a client.

## Supported API boundaries

- Organization: department list/detail/create/update; staff list/detail/create/update
  and department transfer; admin account create/status commands. No account-list
  endpoint exists. Department-head transfer can fail after profile fields were saved;
  the UI must preserve and explain that partial outcome.
- Patient: search, zero-based pagination, detail, create/update, admin deletion.
  `soCmnd` is create-only and cannot be edited. Roles mirror Core controllers.
- Billing: patient invoice history, invoice lookup/create/payment; explicit payment
  request discovery, ledger payment and prescription refund discovery/recording;
  authorized department revenue and failed-outbox replay. Ledger/refund flows use
  server-owned IDs, amounts, currency and statuses. An uncertain ledger/refund
  command keeps its original payload and idempotency key across tab switches.
  A completed client command is not evidence of bank/provider settlement.
- Notification: explicit patient-ID history, authorized detail and admin manual
  send. No inferred patient ID from account claims, resend or mark-read endpoint.
  Recipient addresses are send inputs and never reconstructed from private history.

## Shared behavior

All traffic uses the shared Gateway client. The composition root binds adapters
and derives workspace access from the current authenticated session. Session changes
must reset private form/result state, including nested routes; a late request may not
restore an earlier session's data. Role gates support UX and do not replace backend
authorization. DELETE supports the documented empty HTTP 204 response.

## Verification

Local verification on 2026-10-21:

- Frontend: all 701 tests in 75 files pass, including the 103 Billing/Notification
  tests; typecheck, lint and production build pass. With Node 26, tests use
  `NODE_OPTIONS=--no-experimental-webstorage` and `--maxWorkers=2`.
- Mobile: all 138 application and loader-package tests pass. The delegated
  Billing/Notification subset passes 27 tests; Patient/Organization passes 19.
- Mobile dependency resolution, formatting (89 files unchanged), full Flutter
  analysis and web build with the local Gateway proxy URL pass.
- Independent reviews covered shared session/composition boundaries, web
  load/partial-save behavior, Patient/Organization and Billing/Notification.
  Identified session-privacy and uncertain-payment race issues were fixed and
  covered by regressions before delivery.

Tests use fixtures only under `test/`; production screens do not contain
fallback/sample records. Signed Gateway workflows were not run because the
required services were not available locally. Android/iOS/Windows native builds
were not run. Those acceptance checks remain distinct from Flutter web evidence.
