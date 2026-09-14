# HANDOFF — Pharmacy T16 cross-service contract and E2E

## Scope

Pharmacy has added versioned JSON fixtures and deserialization tests for:

- `payment.completed` (Billing → Pharmacy)
- `prescription.created` (Pharmacy → Billing)
- `prescription.filled` (Pharmacy → Notification/Report)
- `prescription.dispense.failed` (Pharmacy → Billing compensation)

Fixtures live under `backend/pharmacy-service/src/test/resources/contracts/` and are validated by
`PharmacyEventContractFixtureTest`.

## Required owner confirmations

Billing, Notification and Report owners must confirm field names, nullability, event version and
routing-key ownership before a cross-service E2E test is declared passing. Pharmacy must not infer
an invoice/payment identifier or query another service's database.

## Authentication claim blocker

Operational pharmacy actions (prescription ownership, dispense and stock audit) require a signed
`staffId` claim. The current Gateway JWT issuer exports `sub`, `role` and `cid` only, so these
requests fail closed in an end-to-end deployment. Gateway/Organization owners must add the
validated staff identifier to the shared JWT contract (and common claim constant) before enabling
these flows; pharmacy deliberately does not mint or infer the claim locally.

## E2E blocker

The Gateway and the producer/consumer services are not provisioned in the pharmacy module's test
environment. Therefore create-prescription → invoice → payment → dispense, compensation,
redelivery and broker-restart E2E scenarios remain **not run**. Once owners provide a compatible
contract fixture and an isolated environment, add contract tests through the Gateway without
changing the pharmacy bounded-context ports.
