# Department Head Workspaces Implementation Plan

> **For agentic workers:** Use subagent-driven-development with root integration/review. Follow TDD and verify commands before claiming completion.

**Goal:** One DOCTOR identity with an effective clinical department-head appointment can switch professional/management dashboards, assign doctor work shifts, and explicitly create consultation sessions from shifts.

**Architecture:** Core owns appointments/current authority and projection; Clinical owns doctor shifts plus consultation links in one transaction. Gateway routes/roles expose only human public contracts. Frontend uses session-scoped capabilities without spoofing role or expanding clinical/finance authority.

**Tech Stack:** Existing Spring Boot Java/PostgreSQL/Rabbit outbox, Gateway, Next.js/React/TypeScript/Tailwind/Vitest. No new service/dependency.

Approved spec: ../specs/2026-10-31-manager-workforce-proposal.md. User reinstated doctor shift assignment in MVP and authorized implementation, including admin Organization UI. Keep working in existing checkout/new branch; user previously requested redundant worktrees cleaned. No new worktree or production deploy.

## Ownership and sequence

- Core worker owns backend/core-service appointments/authority/controllers/migration/tests/http.
- Clinical worker owns backend/clinical-service shifts/authority/controllers/migration/tests/http.
- Frontend worker owns frontend changes for admin appointments, shared capabilities/workspace shell and Clinical shift UI.
- Root owns Gateway, contract docs, integration verification/demo seed/evidence and review. Workers are not alone; no revert/commits of others. Root stages and commits integrated result.

## Locked Core API

GET /api/v1/org/me/capabilities returns accountId, staffId, role, canPracticeAsDoctor, managementDepartments[{departmentId,departmentName,appointmentId nullable,revision}], observedAt.

ADMIN GET/PUT /api/v2/org/departments/{id}/head-appointments. PUT body expectedRevision, nullable staffId (null revokes), reason; Idempotency-Key and X-Correlation-Id. Response departmentId,revision,appointmentId/staffId/accountId nullable,updatedAt/updatedByAccountId nullable,history[{appointmentId,staffId,accountId,startedAt,endedAt nullable}]. Empty revision0/history[]. HEAD_REVISION_CONFLICT/IDEMPOTENCY_CONFLICT409; HEAD_INELIGIBLE422. Live existing scheduling-authority wire remains compatible, actual currentRole DOCTOR and canManageSchedules depends on effective head appointment.

## Execution checklist

- [x] Core TDD: ineligible/ordinary doctor denied; uniqueness one head/dept and one dept/staff; replace/revoke atomic/history/idempotency; projection old write cannot bypass; authority uses active signed identity. PG transaction/race verification and public role contracts.
- [x] Clinical TDD: DOCTOR head scope accepted and ordinary doctor denied; shift conflict/edit/cancel/link guards; PG concurrency; explicit session creation from shift, unchanged legacy session/payment/registration behavior. Publish exact shift contracts before consumers.
- [x] Gateway TDD: route configuration gated; exact method/role matrix includes admin appointment and own capabilities; DOCTOR has transport access to management routes but downstream current authority mandatory. Deny internal/future/deep paths. Reflect exact roles in route-role-contracts.csv.
- [x] Frontend TDD: capabilities state/session/focus/denial; role remains DOCTOR; unsaved form switching; management scope menu; admin head appointment commands; shift day/week and create-session actions. Correct loading/error/empty/uncertain outcome states.
- [x] Root spec review: every invariant maps to implementation/tests; code quality/security review for stale permissions, default deny, actor identity, concurrent writes, unchanged source contracts and feature boundaries.
- [x] Full module tests, FE typecheck/lint/build after dev stopped, real PG migration/races; seed/demo/public Gateway/UI screenshots where feasible. Document actual limits rather than label whole journey accepted.
- [x] Update canonical business/contracts and implementation evidence, commit/push focused branch, create PR under standing authorization. Stop owned processes and run clean-docker.bat after demo.

## Verification commands

Run each module reactor separately to avoid concurrent common output writes:

    mvn -q -pl backend/core-service -am test
    mvn -q -pl backend/clinical-service -am test
    mvn -q -pl backend/gateway -am test
    pnpm --dir frontend test
    pnpm --dir frontend typecheck
    pnpm --dir frontend lint
    pnpm --dir frontend build

Tests must fail for intended reason before implementation (new test class missing symbol compilation can establish red but security assertions must also execute green). No DB migration repair/renumbering; no global Docker prune. Do not broaden Pharmacy/Lab/Billing/Report authority for new head capability.

## Integration verification

Implementation and automated checks are complete. Production frontend build passed on 2026-11-01; demo processes stopped and clean-docker.bat exited 0. Actual UI coverage and remaining manual checks are recorded in [acceptance report](../../acceptance/department-head-2026-11-01/README.md). Creating the review PR is the final integration action; this checklist does not certify the untested UI write steps.
