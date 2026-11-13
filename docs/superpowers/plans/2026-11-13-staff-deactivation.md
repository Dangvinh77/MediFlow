# Staff deactivation implementation plan

**Goal:** Add the approved ADMIN-only “Ngừng làm việc” action to the staff list and detail.

**Architecture:** Organization owns the command, staff state, linked accounts and durable outbox. Web uses the existing same-origin Gateway API wrapper. Keep staff UUIDs, medical history and existing schedules; operators must arrange handover of existing work separately. No automatic cancellation, deletion or reactivation is included.

**Source lock (M6-02):** Baseline `dcef4b34`, clean worktree before this task. StaffController exposes create/update/read/transfer, but no lifecycle command. StaffResponse/StaffDTO already contain `active`; Staff.deactivate maps to existing `INACTIVE` persistence status. AccountRepository exposes owner advisory locks, active-owner row locks and credential-version revocation. OrganizationEventOutboxAdapter persists event bytes within the Organization transaction. Gateway RouteAuthorizationFilter explicitly allowlists methods/roles. Existing fixtures: frontend Organization contract.fixture.ts, StaffControllerContractTest, OrganizationEventOutboxAdapterTest and Gateway route-role-contracts.csv. No new schema or inferred cross-context identifiers are needed.

**Contract:** PUT `/api/v1/org/staff/{id}/deactivate`, empty JSON body, ADMIN only; response is existing `ApiResponse<StaffResponse>`, no-store. Use the signed account principal as actor. Reject self-deactivation and a current department head with explicit conflict codes; missing staff is 404. Atomically deactivate staff and active linked accounts, increment credential versions, and append `staff.deactivated` (eventId, occurredAt, correlationId, staffId, actorAccountId). Repeated commands are no-ops once inactive. Serialize against account issuance and status changes with the existing owner lock; prevent account activation for inactive staff.

**UI:** Reuse a feature-local action component in the staff row and detail. Visible only to ADMIN for active staff. Inline alertdialog with name, retained-history/account-lock explanation, handover reminder, confirm/cancel, Escape and focus restoration. Lock duplicate writes; discard stale session responses. A lost response requires explicit read-back and never automatically repeats the write. Use returned DTO to refresh the list/detail, including linked-account panel. Support Vietnamese and English.

- [x] Write failing service/web/outbox/Gateway and UI/API tests.
- [x] Implement Organization use case/service/controller/outbox event and account issuance/status guards.
- [x] Allowlist the exact Gateway command and extend the route contract fixture.
- [x] Implement shared Organization action and integrate list/detail plus translations.
- [x] Run focused tests, Core/Gateway required tests, frontend typecheck/lint/build; inspect final diff and record any runtime verification limits.

**Scope/actor:** IMPLEMENT as Vinh under the explicit whole-project assignment. Paths: backend/core-service, backend/gateway, frontend/src/features/organization, frontend/src/lib/l10n/en.json, docs/plans/03-contracts.md and frontend/docs/services/organization.md. Patient behavior is outside this change.

**Review:** Read-only review identified a stale profile GET race after deactivation; fixed by invalidating outstanding reads when applying the command response. A delayed-read regression test now verifies inactive state remains authoritative. Follow-up review has no remaining actionable findings. Department-head commands share their existing command lock before owner/account/staff locks to prevent lock inversion and concurrent assignment of an inactive head.

**Verification:** Organization UI/API suite: 28 files, 200 tests passed. Frontend typecheck, lint and production build passed; lint retains six existing warnings outside this change. Gateway suite: 2,256 tests passed. Full Core suite: 572 tests passed with no failures/errors/skips, including real PostgreSQL/RabbitMQ, rollback, repeated-command and concurrent account-issuance/head-appointment cases. Both Maven commands exited 0. Final scoped diff whitespace check passed. Browser verification against a running Gateway/Core was not performed: those applications were not listening locally. No live staff data was changed. Concurrent service-demo artifact edits are outside this task and were left untouched.
