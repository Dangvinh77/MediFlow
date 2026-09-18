# HANDOFF — `payment.completed` lab references for Lab

> **Mandatory for coding agents:** read this file before changing invoice fee aggregation,
> `PaymentCompletedEvent`, or `payment.completed` contract fixtures in `billing-service`.
>
> **Status (2026-09-18): complete on both sides.** `PaymentCompletedEvent` now
> carries `labTestIds: List<UUID>` (`Fee.sourceRefId` for every `LAB` fee on the paid invoice,
> deduplicated). See `BillingApplicationService.publishPaymentCompleted`/`labTestIds`, the wire
> shape in `src/test/resources/contracts/payment.completed*.json`, and coverage for zero/one/many
> lab tests in `BillingApplicationServiceTest`, `BillingEventPublisherAdapterTest` and
> `BillingEventContractFixtureTest`. Lab consumes the explicit list through its single queue
> dispatcher, claims `eventId` transactionally, and marks each referenced aggregate paid.
>
> **Billing/shared-test follow-up:** `scripts/contract-checks/ClinicalLabWireCheck.java` still calls
> the pre-change nine-argument `PaymentCompletedEvent` constructor. Update that fixture to pass an
> explicit `labTestIds` list; until then `check-clinical-lab.ps1` fails at compilation before its
> JSON assertions. This is outside the Clinical/Lab production ownership boundary.

- **Producer / owner:** Billing — locgit-89
- **Consumer:** Lab — Dangvinh77 / Harori
- **Blocked Lab work:** mark the exact paid lab tests as paid

## Historical gap

`PaymentCompletedEvent` identifies an invoice and an optional prescription but carries no lab test
references. An invoice can contain multiple lab fees. Lab must not substitute invoice, prescription,
record, or patient IDs for a test ID.

## Required contract

Add an explicit collection such as `labTestIds: List<UUID>` containing the unique source references
of all LAB fees covered by the paid invoice. An empty list is valid for non-lab invoices. Billing is
the authority because it owns the invoice-to-fee relationship at payment time.

## Acceptance criteria

- The list is deduplicated, deterministic, and built from persisted LAB fee source references.
- Publisher/outbox contract tests cover zero, one, and multiple lab tests.
- Existing Pharmacy and other consumers tolerate the additive field.
- Notify the Lab owner when merged so it can add the idempotent `payment.completed` consumer.
