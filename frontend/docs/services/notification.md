# Frontend Service: Notification

2026-10-20 detail follow-up: authorized ADMIN/PATIENT readers can explicitly reload the same
notification with GET to observe updated delivery status. Refresh clears the previous private
result while pending; it never resends a notification or polls automatically. Named error feedback
receives focus after read failure; retry/refresh returns focus to the new title only when the user
has not moved elsewhere. HTTP 403 still has no retry control. Session/resource guards discard late
results. This is local FE verification, not signed PATIENT/cross-patient or provider acceptance.

2026-10-19 actual Support intake check: replaying the known PrescriptionFilled event preserved one
observed Notification notice, alongside one Report journal and Clinical attachment. This is backend
intake/deduplication evidence, not signed Notification browser privacy, manual delivery or provider
acceptance. HF6-14 remains open; see [current evidence](../evidence/huy-six-service/2026-10-19/README.md).

2026-10-19 timeout follow-up: HTTP 408 joins network/5xx/unreadable-success outcomes. Manual send
locks for history reconciliation, retains correlation and focuses feedback; timeout details never
become editable field errors. No blind resend or idempotency header is introduced. See
[plan section 13](../huy-six-service-frontend-plan.md#13-đợt-năm-đầu-việc-timeout--2026-10-19).

2026-10-19 keyboard follow-up: send validation focuses the earliest visible field rather than server
detail order. General errors, denied commands and unknown outcome focus the error feedback after
saving settles; locked controls are not targeted. No automatic resend is added. See
[plan section 11](../huy-six-service-frontend-plan.md#11-keyboard-và-focus-sau-đồng-bộ-master--2026-10-19)
for local tests; signed Notification acceptance remains open.

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

2026-10-20 history follow-up: explicit GET refresh retains the submitted patient and current page,
including empty results, and displays the result patient separately from the editable UUID draft.
Loading removes old rows. Retry/pagination/refresh can restore focus to a named result/error when
focus has fallen to the body, preserving another focused control. A forbidden scope has no retry.
Notification local regression: 68/68 tests in 3 files; signed ownership/provider acceptance stays
open in HF6-14 and the existing consolidated Huy handoff.
