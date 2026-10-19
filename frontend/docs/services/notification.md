# Frontend Service: Notification

**Owner:** Lộc (`locgit-89`)  
**Backend:** `backend/notification-service/**`  
**Contract context:** [`docs/ai/services/notification.md`](../../../docs/ai/services/notification.md)

**Task override, 2026-10-18:** user assigned Huy Pharmacy and all Support FE, including Notification
feature/matching routes. Canonical owner remains Lộc. Execution/evidence live in the
[same Huy six-service plan](../huy-six-service-frontend-plan.md), HF6-13/14; dependencies use the
[single Huy handoff](../../../docs/handoffs/HANDOFF-HUY-MVP.md).

## Writable frontend scope

- `frontend/src/features/notification/**`
- `frontend/src/app/(dashboard)/notifications/**`

## Current UI baseline

`/notifications` renders patient history. `ADMIN` and the authorized `PATIENT` can open secured
detail; `ADMIN` can send manually through `/notifications/send`. Responses never display recipient
address or internal retry metadata. `NURSE` remains list-only, matching the controller roles.

The Support host keeps `/api/v1/notifications`; it does not introduce `/support` routes or a
`support` feature folder. Lookup paginates the submitted patient and discards stale requests.
Detail reloads its own request on retry and resets when ID/session changes. Feature-local access
guards prevent disallowed reads/mutations and clear old drafts/results on session changes;
the backend still enforces patient ownership using its signed `patientId` claim.

2026-10-18 follow-up: invalid lookup focuses its UUID field, clears pagination and invalidates old
pending results. A server-denied patient scope is not retried within that session; another patient
can still be searched. Detail 403 shows correlation and a return link without retry, while 404/503
remain explicitly retryable. Manual-send 403 locks fields and the submit handler; returning to
history stays available. Feature facade tests and isolated browser fixtures do not prove shared
transport's late-401 behavior or signed PATIENT ownership; see the single handoff and plan section 10.

Manual send validates nonblank title/content, title ≤255, address ≤150 and the exact live email/SMS
rules (SMS: initial 0 plus 9–10 digits). Channel change clears the previous address; IN_APP sends
null address. It locks submission synchronously and after success. Network/5xx/ambiguous outcomes
require checking history; there is no blind resend or invented idempotency key on this endpoint.
Field validation, 401/403 and correlation IDs are preserved. Recipient data is never displayed by
the history/detail views or newly persisted by the form.

## Owner queue

- `FE-NOTIFICATION-01` — `DONE`: secured detail mirrors live roles.
- `FE-NOTIFICATION-02` — `DONE`: manual send includes channel/address validation.
- `FE-NOTIFICATION-03` — `BLOCKED`: retry/read-state commands do not exist in the live contract.
- `FE-NOTIFICATION-04` — `DONE — FE local`: API, validation and component tests cover pagination,
  roles, channel/status display, errors, stale/session/unmount guards, privacy and manual send.
  Real signed-role/provider/browser acceptance remains HF6-14.

## Handoffs and blockers

- Automatic “My notifications” entry is not added: current frontend auth exposes role/token, not
  a verified patient selector. Backend already reads a distinct signed `patientId` claim and does
  not fall back to accountId. Runtime acceptance needs the actual claim and a confirmed patient
  ID supplied through the current lookup; never infer it from JWT subject.
- Delivery/provider diagnostics remain backend-owned. Do not expose recipient addresses, secrets,
  provider payloads, or internal retry metadata unless the response contract explicitly allows it.
- Events from other contexts trigger backend delivery; the frontend must not reproduce that event
  orchestration.

## Contract and verification gate

Confirm live Notification controller DTOs, roles, privacy fields, enum values, pagination, and error
codes. UI role hiding does not replace backend authorization for a patient UUID.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Notification tests when available.
Smoke test invalid UUID, empty/error/retry, privacy-safe rendering, and cross-patient denial.
