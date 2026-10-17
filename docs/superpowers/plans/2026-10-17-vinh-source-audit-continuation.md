# Vinh source audit and outpatient continuation

**Scope:** Vinh: Clinical/Lab backend, appointment/medical-record/Lab frontend. Existing MVP design and ownership remain authoritative. No deferred features or foreign production edits.

**Source baseline:** `d80d23b2`; pre-existing Word/PDF edits are excluded.

## Findings and execution

- [x] Read current design, owner rules, workboard, handoffs and live contracts; use the indexed graph for structural discovery.
- [x] Confirm Gateway now contains exact GET payment-request discovery. Earlier Vinh handoff describes an obsolete source gap. Billing's missing error correlation was fixed in #417; runtime acceptance still requires a fresh run.
- [x] Reproduce stale appointment mutation, leftover confirmation and cross-record diagnosis attachment with component tests (3 failures, 12 passes before fix).
- [x] Isolate detail workspace by appointment/record identity and ignore mutation completion after unmount. Keep existing frontend theme and release gates.
- [x] Reproduce and fix stale Lab queue errors and 401 after unmount; preserve submitted filters on retry. Add 21 component regressions across the three features.
- [x] Add/run actual paid packaged Clinical EXAM acceptance: source check-in, public exact-source discovery, signed cashier payment/replay, actual Rabbit delivery, one Clinical clearance and one exam record. Do not query Billing business tables or inject a grant.
- [x] Add/run actual paid EXAM + LAB_TEST through Gateway and normal outpatient completion in the Lab packaged slice (1/1, no skips).
- [x] Run Clinical/Lab regression with Docker (339 + 216, no failures/errors/skips), frontend tests (327/327, 40 files)/typecheck/lint/build and review changed scope.
- [x] Update owner evidence and queue; distinguish actual paid EXAM/LAB_TEST slices from outstanding paid Rx/integrated recovery/full-runtime acceptance. Do not mark global MVP complete.

## Verification

Frontend focused regression: `pnpm exec vitest run src/features/appointment/components/AppointmentDetail.test.tsx src/features/medical-record/components/MedicalRecordDetail.test.tsx --maxWorkers=2`.

Backend regression: `mvn -q -pl backend/clinical-service,backend/lab-service -am test`.

Packaged EXAM: existing `packaged-acceptance` profile; package Core/Gateway/Eureka/Billing/Clinical from the same source and pass exact jar paths. Billing catalog and selective EXAM publication are configured only inside disposable test processes. Keep deployment defaults unchanged.

Packaged Lab: existing `packaged-slice` profile now requires Billing jar too. Use actual EXAM and LAB_TEST payments, replay unchanged received clearance bytes, start/result through Gateway, Clinical attachment and normal outpatient completion. No emergency exam override or synthetic clearance.

Runtime findings: reserve future child ports until launch to prevent ephemeral outgoing sockets occupying them. Billing first-payment timestamp has nanoseconds while replay reads PostgreSQL microseconds; compare every other response field exactly and require a timestamp delta below one microsecond. Record the precision difference for Billing owner rather than editing foreign production code.

Actual Billing also publishes two `payment.completed` receipts (EXAM and LAB_TEST). Lab MVP deliberately quarantines that routing key; the old synthetic-grant slice incorrectly expected an empty DLQ. Require exactly two quarantined receipts, verify their transaction/request/account/amount/patient/episode against public payment responses, preserve their original bytes and requeue them after inspection. Any other DLQ event or additional receipt fails the slice. Do not purge or acknowledge financial evidence to make acceptance green.
