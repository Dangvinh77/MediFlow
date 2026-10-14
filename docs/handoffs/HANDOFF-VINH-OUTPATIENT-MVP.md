# Vinh Clinical/Lab outpatient MVP source lock

**Status:** Partial implementation evidence; not full MVP acceptance. This file records the owned-service boundary and open work from M6-02/M6-09/M6-11/M6-14. The global plan remains open.

**Scope:** Production edits are limited to `backend/clinical-service` and `backend/lab-service`. The implementation profile is opt-in as `outpatient-mvp`; the default remains `compatibility-v0` so rollback and existing V0 regression behavior remain available.

## Locked source fixtures

The following Clinical-owned fixtures are the checked-in golden inputs for this slice. Their SHA-256 values make source changes reviewable; refresh them only from a producer acceptance test and update the owning consumer test in the same change.

| Fixture | SHA-256 | Locked facts |
|---|---|---|
| `backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json` | `34BE41CD12BD6FE38E18C89F0828097350405D077F05FA853E828F50578715E7` | Billing EXAM trigger: appointment ID is the episode and source; `recordId` is null; transition is `PENDING` to `AWAITING_PAYMENT`. |
| `backend/clinical-service/src/test/resources/contracts/financial.clearance.granted.v1.json` | `3876A219BEB33B00A0BAC57AE41688E80B969BA08E5E2B880C26F723AE754713` | Accepted live EXAM clearance targets exactly one appointment; `recordId` is null and episode ID equals appointment ID. |
| `backend/clinical-service/src/test/resources/contracts/prescription-context-v1/appointment.json` | `CB9385BC7F582A87C7576944A64CF99CFE379E5F384358C42EF215070B7D28C7` | Existing exact appointment-backed stored record relationship response; no symptoms or diagnosis details. |
| `backend/clinical-service/src/test/resources/contracts/prescription-context-v1/walk-in.json` | `AB97DB118BC8ECF7BC6360B541A6469FB77F1099479B9C8E32DA76E693438D5C` | Existing exact walk-in record relationship response; episode ID is the record ID. |

The generic Billing fixture `backend/billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json` currently contains both `appointmentId` and `recordId`. Do not relax Clinical's exactly-one-target check to accept it. Billing's live `ExamChargeRepositoryAdapter` writes an appointment target only, and `ExamChargeRabbitIntegrationTest` asserts the emitted clearance has that appointment target and `recordId=null`. Lộc owns refreshing or relabeling the generic held fixture. This fixture discrepancy does not mean the live EXAM producer is blocked.

## Runtime switches and current guard behavior

| Mode | Clinical | Lab |
|---|---|---|
| Default | `compatibility-v0`; `mediflow.features.outpatient-mvp.enabled=false`; V2 retains the pre-existing opt-in flag behavior. | `compatibility-v0`; `mediflow.features.outpatient-mvp.enabled=false`; legacy behavior remains available. |
| `outpatient-mvp` profile | V0 medical-record create is rejected with `CLINICAL_V0_WRITE_DISABLED`; V0 appointment status changes are rejected with `CLINICAL_V0_STATUS_WRITE_DISABLED` except cancellation. Active V1 diagnosis edits require the authenticated assigned doctor and matching authoritative department. Start/complete require the same authority. The admission-referral route and service gate are disabled by the effective outpatient-mode condition; admission/transfer completion returns `CLINICAL_ADMISSION_DEFERRED`. | V0 create/lifecycle mutations are rejected with `LAB_V0_MUTATION_DISABLED`; historical V1 Admission rows are also barred from outpatient mutation. `payment.completed` is rejected without requeue so the configured durable `lab.dlq` retains evidence. Admission clearances are rejected with `LAB_ADMISSION_DEFERRED`. Cancellation requires signed staff identity matching `cancelledBy`; spoof/missing identity returns `LAB_ACTOR_MISMATCH`. |

The effective outpatient mode is `mediflow.features.outpatient-mvp.enabled=true` **or** the active `outpatient-mvp` Spring profile; a false property cannot turn off a selected profile. `MEDIFLOW_OUTPATIENT_MVP_ENABLED` selects the property mode. Both V1 care-finance gates default off in the MVP profile until the complete consumer/producer acceptance runs. A caller-supplied Lab `sourceOrderId` is not treated as proof of a Clinical order. Outpatient MVP Lab creation fails closed with `LAB_SOURCE_ORDER_UNVERIFIED` whenever V1 create is configured, until Clinical owns and persists an exact order identity and the request can be checked against it. No `recordId`, appointment ID, or caller field is substituted for that missing authority. No Lab-specific Clinical context endpoint was added because it could only confirm record relationships and could not verify the required source order.

| HTTP operation | Role gate | Outpatient-MVP contract |
|---|---|---|
| Clinical `PUT /api/v1/appointments/{id}/check-in` | `ADMIN`, `NURSE` | Available only with the Clinical V1 gate. |
| Clinical `PUT /api/v1/appointments/{id}/start-exam` | `ADMIN`, `DOCTOR` | Requires signed actor staff ID equal to the assigned doctor; authority errors are `CLINICAL_STAFF_IDENTITY_REQUIRED`, `CLINICAL_DOCTOR_NOT_ASSIGNED`, `CLINICAL_DOCTOR_NOT_ELIGIBLE`, or `CLINICAL_DOCTOR_WRONG_DEPARTMENT`. |
| Clinical `PUT /api/v1/records/{id}/complete` | `ADMIN`, `DOCTOR` | Same assigned-doctor check; admission/transfer returns `CLINICAL_ADMISSION_DEFERRED`. |
| Clinical `POST /api/v1/records/{id}/admission-referrals` | `ADMIN`, `DOCTOR` | Route is absent in outpatient mode even if both feature properties are overridden true. |
| Lab `POST /api/v1/lab` | `ADMIN`, `DOCTOR` | V0 is blocked; V1 remains gated and then returns `LAB_SOURCE_ORDER_UNVERIFIED` until an exact order authority exists. |
| Lab `PUT /api/v1/lab/{id}/start` and `/results` | `ADMIN`, `LAB_TECH` | V0 mutations are blocked; V1 also needs its care-finance gate and outpatient episode. |
| Lab `PUT /api/v1/lab/{id}/cancel` | `ADMIN`, `DOCTOR`, `LAB_TECH` | V1 audit identity must match signed staff; mismatch is `LAB_ACTOR_MISMATCH`. |
| Lab `PUT /api/v1/lab/{id}/status` | `ADMIN`, `LAB_TECH` | V0 mutation is blocked; V1 requires its care-finance gate. |

The primary profile keys are `mediflow.features.care-finance-v2.enabled=false` and `mediflow.features.admission-referral.enabled=false` for Clinical, and `mediflow.features.care-finance-v2=false` for Lab. The compatibility configuration preserves the legacy admission-referral default derived from the old Care-Finance gate.

The Clinical Core clients use profile-configured target names `core-service` while retaining the logical JWT identities and compatibility defaults `organization-service`/`patient-service` outside the profile.

## Explicit open owner tasks

- **Vinh — Lab order authority (M6-11):** define and persist the exact Clinical Lab-order identity and producer contract; then add a bounded Lab-to-Clinical relationship lookup and reject every mismatched record/patient/doctor/department/episode/order combination. Keep Lab V1 create disabled until its producer and consumer fixtures agree. Do not derive order identity from a record or appointment.
- **Vinh - durable Clinical acceptance (M6-09/M6-14):** the four existing flat Clinical facts now enqueue original converter bytes in the outbox when durable publication or effective outpatient MVP mode is enabled. Real PostgreSQL/RabbitMQ migration, atomicity and restart acceptance remains open; local serializer/dispatcher tests do not close this task.
- **Vinh — operation plus fingerprint idempotency (M6-09/M6-11):** current inbox claims are event-ID based. Add scoped operation/fingerprint conflict handling for Clinical and Lab consumers only with append-only migrations and tests for duplicate-identical versus duplicate-conflicting bytes.
- **Lộc — fixture ownership:** reconcile the generic `clearance-exam.json` fixture with the already-verified live appointment-only EXAM producer behavior; keep Clinical's XOR acceptance invariant unchanged.
- **Cross-owner acceptance:** run real PostgreSQL/RabbitMQ proof for check-in to payment to exam start/completion and Lab request to result, including DLQ/restart. Unit fixtures do not satisfy M6-09, M6-11, M6-14, or M6-16.

## Durable publication and rollback fence

`appointment.created`, `appointment.status.changed`, `medicalrecord.created` and `diagnosis.added` retain their existing flat body shape, event IDs, correlation IDs and converter date/time representation. Effective MVP mode forces durable publication and activates its single scheduler even if the independent care-finance or durable-event flags are false. Outside MVP, durable publication is opt-in and the compatibility default retains its original after-commit publisher.

Append-only migration V5 adds original payload bytes and HELD/quarantine evidence without rewriting V4 payloads. The dispatcher preserves original bytes across retries, bounds failed publication attempts and holds deferred admission/transfer facts before selecting an active page. HELD and quarantined rows remain excluded when switching profiles; a profile switch cannot release them.

Rollback to the current artifact's compatibility mode preserves these guards. A pre-V5 binary does not understand the new HELD/quarantine columns: stop and drain the current host, disable the older binary's care-finance/outbox scheduler, and preserve all V5 rows for forward recovery. Never run an old enabled dispatcher against HELD/quarantined rows, reset their markers, or drop the new columns to force rollback.

## Test evidence

Fresh post-edit verification on 2026-10-14 (Java 21, Maven offline using the existing dependency cache):

| Run | Clinical | Lab | Result |
|---|---|---|---|
| Full owned-module `test` | 266 tests, 0 failures, 1 environment error, 13 skipped | Not reached because the reactor stopped | Docker is unavailable; `PrescriptionContextPostgresTest` cannot initialize `postgres:16-alpine`. |
| `test` excluding only `PrescriptionContextPostgresTest` | 265 tests, 0 failures/errors, 13 skipped | 180 tests, 0 failures/errors, 11 skipped | Exit 0; 421 passed and 24 skipped across both modules. |
| Owned-module `-DskipTests package` | Packaged | Packaged | Exit 0; compilation and packaging only, not runtime acceptance. |

Commands target `-pl backend/clinical-service,backend/lab-service -am`. The filtered run uses `-Dtest=*,!PrescriptionContextPostgresTest -Dsurefire.failIfNoSpecifiedTests=false`; it does not change test annotations or disable application guards. Other Docker-dependent tests skip themselves without an available engine. Skipped or excluded tests are not passing evidence. V5 PostgreSQL execution, RabbitMQ confirmation/restart/DLQ proof, Core/Gateway integration and the Lab order-authority happy path remain unverified; the global M6 tasks remain open.
