# Frontend Service: Notification

**Owner:** Lộc (`locgit-89`)  
**Backend:** `backend/notification-service/**`  
**Contract context:** [`docs/ai/services/notification.md`](../../../docs/ai/services/notification.md)

## Writable frontend scope

- `frontend/src/features/notification/**`
- `frontend/src/app/(dashboard)/notifications/**`

## Current UI baseline

`/notifications` performs an explicit patient UUID lookup and renders a paged notification history
through `GET /v1/notifications/patient/{patientId}`. It shows channel, delivery status, failure
reason, and timestamps without exposing recipient addresses. UI roles are `ADMIN`, `NURSE`, and
`PATIENT`.

## Owner queue

- `FE-NOTIFICATION-01` — `VERIFY-CONTRACT`: add notification detail if a live secured endpoint exists.
- `FE-NOTIFICATION-02` — `VERIFY-CONTRACT`: add one send action supported by the live controller,
  including channel validation and failure behavior.
- `FE-NOTIFICATION-03` — `VERIFY-CONTRACT`: add retry or read-state controls only when those commands
  exist in the owner contract.
- `FE-NOTIFICATION-04` — `IMPLEMENT`: test pagination, channel/status display, errors, and privacy
  boundaries after the shared harness exists.

## Handoffs and blockers

- “My notifications” is blocked on a documented patient identity mapping. Producers: Gateway and
  Patient / Hoàng Anh. Acceptance: stable authenticated patient ID with backend ownership checks.
- Delivery/provider diagnostics remain backend-owned. Do not expose recipient addresses, secrets,
  provider payloads, or internal retry metadata unless the response contract explicitly allows it.
- Events from other contexts trigger backend delivery; the frontend must not reproduce that event
  orchestration.

## Contract and verification gate

Confirm live Notification controller DTOs, roles, privacy fields, enum values, pagination, and error
codes. UI role hiding does not replace backend authorization for a patient UUID.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Notification tests when available.
Smoke test invalid UUID, empty/error/retry, privacy-safe rendering, and cross-patient denial.
