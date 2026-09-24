# CONTRACT-PATIENT-NOTIFICATION-01 — Patient-created notification event

- **Status:** `COMPATIBILITY_LOCKED`
- **Producer:** Patient Service — Hoàng Anh
- **Consumer:** Notification Service — Lộc
- **Routing key:** `patient.created`

## Phase 1 decision

The producer keeps the flat payload already consumed by Notification. This is intentionally a
compatibility decision; Patient must not publish a second envelope shape or rename fields while the
consumer still deserializes the current payload.

```json
{
  "eventId": "uuid",
  "occurredAt": "2026-09-24T03:00:00Z",
  "correlationId": "uuid",
  "patientId": "uuid",
  "hoTen": "Nguyen Van A",
  "email": "patient@example.com",
  "sdt": "0901234567"
}
```

The event is published only after the Patient transaction commits. `patient.updated` follows the
existing additive shape documented in the Patient spec and is not changed by this phase.

## Envelope migration gate

Moving this event to the versioned care-finance envelope requires, in one coordinated contract
change:

1. a new event version and canonical fixture;
2. Patient serializer/producer test against that fixture;
3. Notification deserializer/consumer test against the same fixture; and
4. a compatibility or rollout path so existing messages are not silently dropped.

Until all four conditions pass, the flat payload above remains authoritative. No consumer may infer
patient identity from a JWT subject; JWT identity is governed separately by
[`CONTRACT-IDENTITY-LOOKUP-01`](CONTRACT-IDENTITY-LOOKUP-01.md).
