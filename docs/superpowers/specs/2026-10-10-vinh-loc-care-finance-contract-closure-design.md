# Vinh–Lộc Care-Finance Contract Closure Design

**Date:** 2026-10-10
**Scope owner:** Vinh (`Dangvinh77` / `Harori`)
**Related producer owner:** Lộc (`locgit-89`)
**Status:** Approved for implementation planning

## Goal

Close the producer/consumer fixture gaps that are actionable inside Vinh's backend ownership after
Billing commit `e76501c` added LAB_TEST request issuance, admission-deposit issuance and admission
settlement support.

This slice gives Billing a canonical Clinical EXAM charge-trigger fixture and gives Inpatient
same-byte evidence for Billing's `settlement.completed` output. It does not activate runtime flags or
claim that the larger Clinical/Lab/Inpatient handoffs are complete.

## Scope

### Clinical producer fixtures

Add a canonical version-1 fixture for `appointment.status.changed` representing the normal
check-in transition from `PENDING` to `AWAITING_PAYMENT`. The payload must preserve the exact
outpatient episode and charge-source relationship already emitted by
`ClinicalCareFinanceApplicationService`:

- `careEpisodeType=OUTPATIENT_VISIT`;
- `careEpisodeId=appointmentId`;
- `sourceType=EXAM`;
- `sourceId=appointmentId`;
- `priceCode=OUTPATIENT_EXAM`;
- `recordId=null` before examination starts;
- no emergency override.

Add a second canonical version-1 fixture for `medicalrecord.created`. This fixture locks the existing
wire shape for downstream consumers and migration checks. It is not the normal initial EXAM charge
trigger because the normal record is created only after clearance permits examination to start.

Fixture tests must serialize the real `DomainEventEnvelope` and real payload records and compare the
result as a JSON tree against the committed fixture. They must not recreate a parallel test-only DTO.

### Inpatient settlement consumer fixture

Copy Billing's actual `settlement.completed` producer fixture byte-for-byte into Inpatient's
contract resources. Add a consumer contract test that sends those exact bytes through the real
`InpatientEventConsumer` and verifies the resulting `SettlementCompletedCommand` fields, including:

- event identity, schema version, time and correlation ID;
- settlement, admission and account IDs;
- gross, insurance, patient-liability, completed-payment, completed-refund and balance amounts;
- settlement outcome and completion time.

The test must verify no unrelated Inpatient use case is invoked. Existing application-level
idempotency and target-mismatch tests remain authoritative; this slice does not duplicate them.

### Lab assessment

No Lab production or test change is required in this slice. Billing already copied Lab's canonical
`lab.request.created.v1.json` byte-for-byte and tests its decoder against that fixture. Lab runtime
activation still depends on the broker-backed request → charge → payment → clearance → execution
proof.

## Data flow

### Clinical EXAM request

1. Clinical checks in the exact appointment.
2. Clinical persists `AWAITING_PAYMENT` and appends `appointment.status.changed` atomically.
3. The canonical Clinical fixture defines the version-1 bytes that Billing may consume.
4. Billing may subsequently create the EXAM charge/payment request in Lộc's owned module.
5. A paid request produces `financial.clearance.granted`; Clinical's existing consumer applies it to
   the exact appointment before examination starts.

### Inpatient settlement

1. Inpatient publishes `discharge.medically.approved` for the exact admission.
2. Billing freezes charge intake and computes settlement using its owned ledger.
3. Billing publishes `settlement.completed`.
4. Inpatient decodes the shared fixture into its settlement command and stores the projection.
5. Administrative close remains subject to Inpatient's existing settlement/override rules.

## Validation and failure behavior

- Fixture mismatches fail tests instead of accepting renamed, missing or defaulted fields.
- Clinical fixture values use deterministic UUIDs and timestamps so downstream copies remain stable.
- Inpatient uses Billing's exact fixture bytes; it does not infer admission, account or patient
  relationships from another identifier.
- Malformed envelopes, wrong producers, missing fields and unsupported enums continue through the
  existing consumer rejection path.
- No feature flag is enabled, no queue binding is released and no handoff is deleted in this slice.
- Top-up thresholds, insurance allocation and deposit recognition remain explicitly outside scope;
  their policies are not defined well enough to implement safely.

## Ownership boundaries

Writable production/test scope is limited to:

- `backend/clinical-service/**`;
- `backend/inpatient-service/**`.

`backend/billing-service/**`, Lab production code and other owners' modules remain read-only. Shared
handoff status is not changed because Docker acceptance and Billing policy work remain open.

## Test strategy

Implementation follows red-green-refactor:

1. Add each fixture test first and run it while the fixture is absent to observe the expected failure.
2. Add the minimal fixture resource needed to make the test pass.
3. Run focused contract tests for Clinical and Inpatient.
4. Run `mvn -q -pl backend/clinical-service,backend/inpatient-service -am test`.
5. Attempt only the existing relevant Docker/Testcontainers acceptance tests if the local Docker
   connection is usable; report environmental failure separately and do not treat it as passing.

## Completion criteria

- Clinical owns committed, deterministic, real-record fixtures for both EXAM-related version-1
  producer events.
- The check-in fixture unambiguously identifies `appointment.status.changed` as the normal initial
  EXAM charge trigger.
- Inpatient consumes Billing's exact `settlement.completed` producer fixture through the real
  decoder and maps every financial field correctly.
- All focused and module-level tests pass with fresh output.
- No runtime flags, foreign modules or unresolved financial policies are changed.
