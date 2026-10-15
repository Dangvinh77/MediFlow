# Vinh Clinical/Lab outpatient MVP source lock

**Status:** Partial implementation evidence; not full MVP acceptance. This file records the owned-service boundary and open work from M6-02/M6-09/M6-11/M6-14. The global plan remains open.

**Scope:** Production edits are limited to `backend/clinical-service` and `backend/lab-service`. The implementation profile is opt-in as `outpatient-mvp`; the default remains `compatibility-v0` so rollback and existing V0 regression behavior remain available.

## Follow-up source lock (2026-10-15)

The follow-up branch `fix/vinh-lab-authority-inbox` supersedes the earlier missing-source-order and event-ID-only implementation notes below. Final verification evidence for this branch is recorded separately; the 2026-10-14 test counts are historical, not evidence for these changes.

Clinical now persists an immutable, server-generated Lab order through `POST /api/v1/records/{id}/lab-orders` (ADMIN/DOCTOR). Creation requires both effective outpatient MVP mode and the Clinical care-finance gate, an OPEN record, its matching V1 appointment in IN_EXAM, and the signed assigned eligible doctor. Patient, department and outpatient episode come from Clinical's stored examination. `labType`, `priceCode` and `requestedDate` are explicit order fields; a future scheduled requested date is permitted by the existing Lab contract.

`GET /api/v1/lab-orders/{sourceOrderId}/context` is an internal Lab-only lookup. It accepts only the exact GET path, a short-lived signed `lab-service` SYSTEM service token, and signed `staffId`/`originalActorRole` delegation matching the stored requesting doctor. It does not broaden Pharmacy's separate prescription-context authority. Confirmed absence is a successful `exists=false` response; forbidden actors are denied, and upstream errors or malformed responses are unavailable rather than absence.

Lab's V1 create verifies every order/request identity, signed actor, episode, test type, price code and requested date before starting its local write transaction. The local test and request outbox remain atomic. The lookup requires `mediflow.lab.clinical-context.enabled=true` (`MEDIFLOW_LAB_CLINICAL_CONTEXT_ENABLED`), as well as Lab's existing care-finance gate; both remain opt-in. Missing authority has no compatibility fallback. The Clinical/Lab `contracts/clinical-lab-order-context-v1/valid.json` fixtures have identical SHA-256 `B1ECB5A8DC167BA934716728119BB324649DD7EFD1B0EB80A630E9AE2C659B50`.

Append-only Clinical V6 stores orders; Clinical V7 and Lab V3 add delivery/operation receipts. Receipts retain original body bytes and SHA-256, plus canonical business bytes and SHA-256. Event identity is unique across handlers within each module. An identical event is a no-op; the same operation under a new event ID is a no-op only when business evidence agrees. Reused IDs or operation keys with changed evidence are conflicts. Claim, effect and any resulting outbox writes share one transaction; failures roll them back together. Legacy effects without verifiable evidence fail closed rather than acquiring invented fingerprints. These database guarantees still require real PostgreSQL acceptance.

Clinical V1 Lab results require `resultVersion=1` and the exact record/patient/department/outpatient episode before attachment. Amendments are outside this MVP. Effective outpatient MVP mode quarantines flat V0 Lab/Rx facts before receipt or effect, while their compatibility paths remain available outside MVP. Permanent conflicts/deferred evidence go to the existing durable DLQs; transient database/broker failures have bounded retry.

### Dependencies still open

- **Hoang Anh / Gateway:** add exactly `POST /api/v1/records/{id}/lab-orders` for ADMIN/DOCTOR to the Gateway allowlist, with wrong-role, wrong-method and extra/nested-path denial tests. Keep the internal `/api/v1/lab-orders/{id}/context` lookup unavailable to human/public Gateway callers. Clinical's downstream signed assigned-doctor checks remain mandatory. The current allowlist does not yet expose the new driving API.
- **Huy / Pharmacy and Vinh / Clinical:** the locked V1 `prescription.filled` contract uses `dispenseId` and does not provide Clinical's required `recordId`. Agree a producer-owned exact relationship contract and same-PR fixtures/tests before enabling V1 attachment. Clinical's flat V0 attachment path must not guess a record or treat a prescription ID as a V1 dispense operation.
- **Vinh with Billing/Gateway owners:** run real PostgreSQL/RabbitMQ migration, race, rollback, restart, DLQ and Gateway flow acceptance. Core consolidation is now present on remote master; its presence does not prove the cross-service slice has passed. No Core/Gateway production files are changed by this branch.

Rollback to a pre-V7 Clinical or pre-V3 Lab binary must keep its old consumer listeners disabled after the new receipts have accepted writes. Those binaries do not understand operation/raw-body evidence. Preserve orders, receipts, outbox and DLQ contents for forward recovery; do not reset markers or delete evidence to make replay succeed.

### Follow-up verification evidence

Fresh verification on 2026-10-15 used Java 21.0.11, Maven 3.9.16 and the existing offline dependency cache. The branch is based on `334df99d`; remote master is nine commits ahead with Core/Gateway and frontend changes, but no Clinical/Lab source differences. Verification here does not claim a merged six-service runtime test.

| Latest run | Tests | Passed | Failed/errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Clinical selected suite | 301 | 284 | 0 / 0 | 17 | Clinical reactor step SUCCESS; excludes only `PrescriptionContextPostgresTest`. |
| Lab full suite | 209 | 193 | 0 / 0 | 16 | Exit 0, BUILD SUCCESS; no Lab test exclusions. |
| Total selected tests | 510 | 477 | 0 / 0 | 33 | Skipped container tests are not passing evidence. |

The initial unrestricted owned-module run stopped in Clinical with a Docker initialization error, one stale command assertion and three controller-test context errors; Lab was not reached. The test-harness errors were corrected and the latest selected Clinical and full Lab runs above passed. `PrescriptionContextPostgresTest` still requires a running Docker engine and is excluded only from the selected Clinical run, not counted as passed or skipped there. The new five-test PostgreSQL inbox class in each module is included in the 33 skips.

Commands (add `-o -Dmaven.repo.local=<existing-cache>` only when using the offline cache):

```text
mvn -pl backend/clinical-service,backend/lab-service -am -Dtest=*,!PrescriptionContextPostgresTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl backend/lab-service -am test
mvn -pl backend/clinical-service,backend/lab-service -am -DskipTests package
```

The first command reached Clinical SUCCESS before a now-fixed Lab test-compile issue. Clinical production code did not change afterward. The second command is the fresh successful Lab evidence. Packaging compiles both modules and their final test sources; it does not execute container acceptance. Sol 5.6 high completed static review; its last `labTestIds` canonical-order finding is fixed and covered by the passing Clinical factory test. No full MVP or PostgreSQL/RabbitMQ/Gateway acceptance is claimed.

## Initial PR #366 baseline (historical)

The remaining sections describe the first implementation at `334df99d`. The follow-up source lock above replaces its missing-authority and event-ID-only notes; its original test evidence is retained for traceability.

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
