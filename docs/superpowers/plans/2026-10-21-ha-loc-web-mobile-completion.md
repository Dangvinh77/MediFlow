# Hoàng Anh and Lộc client completion

Date: 2026-10-21. User assigns Vinh the remaining Organization, Patient, Billing
and Notification work on frontend and mobile and requests a pull request.

## Contract and scope

Use current Core, Billing and Notification controllers/DTOs as the wire authority.
Organization covers departments, staff and admin account create/status commands;
there is no account-list API. Patient covers paginated search/detail and authorized
create/update/delete. Billing covers supported invoice, ledger, refund and admin
queries/commands. Notification covers patient history, authorized detail and manual
send; resend/read-status and patient identity inference are outside live contracts.
No backend production changes or changes to other owners' features are needed.

## Implementation

1. Close web Organization/Patient load-failure and partial-update paths and add
   meaningful component/form/role regression coverage. Existing web Billing and
   Notification workflows remain the contract reference and receive regression checks.
2. Add a shared mobile session-access boundary, Gateway mutation methods and 204
   handling; bind feature adapters only at the app composition root.
3. Implement mobile Organization/Patient models, exact DTOs, repositories, use cases,
   role-gated list/detail/forms and admin commands, with loading/error/retry states.
4. Implement mobile Billing/Notification supported workflows with explicit IDs,
   strict money inputs, idempotent ledger retries and uncertain-command protection.
5. Verify private state clears when session changes, including nested routes, and
   stale asynchronous responses cannot restore previous-session data.
6. Update dated client status documentation to distinguish implemented client
   capabilities, unsupported APIs and integration acceptance still requiring live services.

## Verification and delivery

Use feature DTO/repository/use-case/widget tests for mobile and component/API tests
for web. Run frontend typecheck/lint and relevant suites; run mobile formatting,
analysis, full tests and web build. Inspect final changes against project standards,
resolve findings, commit with the existing allowed human identity, push and create
a PR against master. Attach the PR to this chat. Do not claim native-device or
external payment/message-provider acceptance based on a client build.
