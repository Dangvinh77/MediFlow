# HANDOFF — Purpose-scoped financial clearance for Clinical and Lab

> **Status (2026-09-26): OPEN.** Billing currently publishes the compatibility
> `payment.completed` event but does not publish `financial.clearance.granted`. Clinical therefore
> cannot unlock an examination from an EXAM payment, and Lab cannot migrate its operational gate
> from the compatibility `paid` projection to an exact LAB_TEST clearance.

- **Producer / owner:** Billing — Lộc (`locgit-89`)
- **Consumers:** Clinical and Lab — Vinh (`Dangvinh77` / `Harori`)
- **Canonical contract:**
  [`CONTRACT-CARE-BILLING-01`](../../docs/handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- **Blocked work:** Clinical payment-aware appointment states; Lab clearance gate, care-episode
  migration and removal of `payment.completed` as an operational authorization

## Required producer behavior

Publish version 1 `financial.clearance.granted` only after the matching payment is committed. The
event must use the common envelope and the canonical payload from `CONTRACT-CARE-BILLING-01`,
including `clearanceId`, `invoiceId`, `accountId`, `patientId`, `careEpisodeType`, `careEpisodeId`,
`purpose`, amount/payment metadata and the purpose-specific target.

- `purpose=EXAM` carries exactly one authoritative `appointmentId`, or `recordId` for an agreed
  walk-in episode.
- `purpose=LAB_TEST` carries a non-empty, deduplicated `labTestIds` list sourced from paid LAB
  charges.
- Optional targets for unrelated purposes remain null/empty and are never filled by inference.
- The event is written through Billing's transactional outbox so a committed payment cannot lose
  its clearance event.
- Existing `payment.completed` publishing remains unchanged during the compatibility window.

## Why Clinical and Lab need it

Clinical must distinguish check-in from permission to begin examination. Lab must authorize each
test by its exact `testId`; an invoice, patient, record or latest test is not a valid substitute.
Neither consumer owns money, so neither may infer financial clearance from local state.

## Acceptance criteria

- Billing has producer fixtures for EXAM and LAB_TEST clearances using the canonical version 1
  shape and rejects missing/mismatched target IDs.
- Duplicate payment commands produce one clearance fact per purpose/target set.
- Outbox replay republishes the same `eventId` and immutable payload.
- Clinical and Lab deserialize the shared fixtures and atomically deduplicate by `eventId` before
  either contract status is promoted from `DESIGN_READY`.
- Integration tests prove an EXAM clearance cannot unlock Lab and a LAB_TEST clearance cannot
  unlock an appointment or unrelated test.
- Once both consumers pass the shared fixtures, move the lasting rules into service docs, update
  the contract status and remove this handoff plus its active-registry entry.
