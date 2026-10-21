# Core backend audit — Hoàng Anh — 2026-10-20

Owner: Hoàng Anh (`TranHoangAnh94`). Status: **OPEN**, owner implementation on
`codex/core-audit-reliability`; final shared-revision verification remains required.
Audit source: `26bf2ef7` (backend unchanged from `553ec59e`); Vinh verifies across modules but does not change Core production code.
Canonical requirements: [contracts](../plans/03-contracts.md), [reliability](../plans/04-data-reliability.md).

Source entry points (relative to the repository root):

- `backend/core-service/src/main/java/com/mediflow/patient/application/service/PatientWriteApplicationService.java`
- `backend/core-service/src/main/java/com/mediflow/patient/infrastructure/messaging/PatientEventPublisherAdapter.java`
- `backend/core-service/src/main/java/com/mediflow/organization/domain/model/Department.java`
- `backend/core-service/src/main/java/com/mediflow/organization/application/service/UpdateStaffService.java`
- `backend/core-service/src/main/java/com/mediflow/organization/application/service/UpdateDepartmentService.java`
- `backend/core-service/src/main/java/com/mediflow/organization/application/service/CreateStaffService.java`

## HA-20-01 — Patient events are not durable (P2)

`PatientWriteApplicationService.create/update` saves Patient in its local transaction, then
`PatientEventPublisherAdapter.publish` registers an `afterCommit` callback that invokes
`RabbitTemplate.convertAndSend` directly. A crash between DB commit and send loses the event;
a Rabbit outage can make the callback throw after the Patient write has already committed.
There is no durable pending row to resume. `patient.created` has an active Notification consumer.

Required change: atomically persist Patient state and its event intent in the Patient persistence
boundary; relay with publisher confirms/returns and bounded retry/quarantine. Keep existing
created/updated wire contracts, event identity and correlation. Do not introduce cross-context DB access.

Acceptance: real PostgreSQL/Rabbit tests for create AND update during broker outage, restart/recovery,
crash after publish before mark, preserved event identity/payload and consumer dedupe. Verify API
outcome agrees with the committed write and no welcome event is silently lost.

## HA-20-02 — Department head can be changed to a non-doctor (P2)

`Department.changeHead` requires an active doctor in the same department. However,
`UpdateStaffService.execute` permits changing that head's title from `DOCTOR` to `NURSE`
without checking the reverse reference; the department retains its now-invalid head.
`UpdateDepartmentService` also skips `changeHead` when the supplied head UUID is unchanged.
The audit reproduced the title mutation through the actual Spring application use cases and
PostgreSQL 16: create doctor, assign head, change that staff to NURSE, then read both persisted
rows. The head reference remains invalid. One test, one expected assertion failure, zero errors/skips.
The [test-only regression patch](../acceptance/backend-audit-2026-10-20/HA-20-02-regression.patch)
keeps the failing case outside the default suite. Reproduce on the owner's fix branch:

```powershell
mvn -pl backend/core-service '-Dtest=OrganizationServiceIntegrationTest#changingDepartmentHeadToNonDoctorCannotLeaveInvalidHeadReference' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The existing transfer guard does not cover this path.

Required change: reject an incompatible head-title change, or remove/reassign the head atomically
according to the approved business behavior. Revalidate retained head IDs as needed.

Acceptance: regression through application/API + PostgreSQL showing no department can retain a
non-doctor head after a title change; ordinary non-head title updates still succeed. Include
concurrent assignment/title change so both write paths preserve the invariant.

## HA-AUDIT-03 — Department deactivation races with staff creation (P2, now reproduced)

This updates the previously unproven item in
[the 19 October handoff](HANDOFF-HA-JWT-EXPIRATION-AND-CORE-AUDIT.md); it is one defect, not two tasks.
`UpdateDepartmentService` checks that no active staff exists, while `CreateStaffService` separately
checks that the department is active. Neither serializes these decisions around the department.

A deterministic PostgreSQL 16 test paused deactivation immediately after its active-staff query
returned false, committed a staff creation in another transaction, then released deactivation.
Both writes committed: inactive department with active staff. Test result: **3 tests, 1 expected
assertion failure, 0 errors; the two existing tests passed**.

The [test-only regression patch](../acceptance/backend-audit-2026-10-20/HA-AUDIT-03-regression.patch)
contains the barrier and invariant assertion. It is retained as evidence instead of installing
a deliberately failing test in the default suite. Apply it on the owner's fix branch and run:

```powershell
mvn -pl backend/core-service '-Dtest=OrganizationServiceIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Required change: serialize or reject one competing operation using the same department authority;
do not rely on the staff FK to enforce active state. Cover staff creation and other paths that
can activate/move staff into a department.

Acceptance: the attached concurrent test turns green with real PostgreSQL, the inverse interleaving
is safe, and existing sequential API behavior remains valid. No active staff may remain in an
inactive department after both transactions finish.

## Closeout

Hoàng Anh implements Core fixes; Vinh reruns Core, Gateway, Notification and relevant packaged
workflows on the final shared revision. Close this handoff only after code and its acceptance pass.

## Owner implementation — 21 October

Business clarification from the user: a department head must first be dismissed or replaced
before changing `DOCTOR` to `NURSE`. This is not a blanket prohibition on professional-title
changes for ordinary staff. Appointment/dismissal and professional-title changes are separate
operations. See [Core rules and operations](../../backend/core-service/docs.md).

| Item | Owner change | Local regression |
|---|---|---|
| HA-20-01 | Patient state and outbox commit in the Patient transaction; confirmed mandatory publishing, original payload/identity, bounded backoff and quarantine | `PatientServiceIntegrationTest`: broker outage during create/update, worker reconstruction, post-confirm mark failure/replay, outbox failure rollback, returns/quarantine |
| HA-20-02 | Reject incompatible title while still head; always revalidate a supplied head UUID; serialize appointment and title changes on the department | `OrganizationServiceIntegrationTest`: rejection, dismissal then title change, retained invalid UUID, both concurrent orders; `StaffControllerContractTest`: existing `422 DEPARTMENT_HEAD_INVALID` contract |
| HA-AUDIT-03 | Department row locks cover deactivation, staff creation and incoming transfer; staff mutation locks the staff first and transfer locks departments in UUID order | `OrganizationServiceIntegrationTest`: both orders for deactivation/create and deactivation/transfer |

Local verification on 21 October: `mvn -q -pl backend/core-service -am test` completed with
exit code 0: **205 Core tests / 38 suites, 0 failures, 0 errors, 0 skipped**. Docker Engine
28.5.1, PostgreSQL 16 and RabbitMQ 3.13 Testcontainers. Organization integration **7/7**,
Patient integration **7/7**, Staff controller contract **18/18**. `git diff --check` also passed.

The old race patch waited for creation to commit while deactivation was paused. With the fix,
creation correctly blocks on the department lock. The installed regression instead asserts
that wait, releases the winning transaction, and verifies the competing command rejects the
new committed state. It covers the inverse order as well.

The post-confirm crash test injects failure at the DB mark and reconstructs the worker; it does
not certify a killed JVM/container restart. Vinh still owns the final Core/Gateway/Notification
and packaged workflow gates, including duplicate welcome-event consumption without repeated
effects. No Notification production change is included. Do not close this handoff from Core
test counts alone. At this verification the owner changes were uncommitted; the final artifact
SHA must be recorded after commit and used for Vinh's acceptance run.
