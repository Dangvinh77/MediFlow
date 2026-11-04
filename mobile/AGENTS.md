# MediFlow Mobile Instructions

Read [`docs/ai/14-flutter.md`](../docs/ai/14-flutter.md) before editing mobile code. It is the authoritative Flutter blueprint. Mirror gateway contracts from [`docs/ai/05-api-conventions.md`](../docs/ai/05-api-conventions.md).

## Mobile assignment — 2026-11-03

Per the user's explicit leader assignment, Vinh owns completion of **all mobile features**,
including Pharmacy/Report and Organization/Patient/Billing/Notification, plus shared app,
auth, network and platform hosts. Earlier mobile owner labels are historical. This assignment
does not change web feature ownership or enable deferred Inpatient/Surgery runtime.
Track implementation and separate runtime/native acceptance in
[`docs/mobile-completion-workboard.md`](docs/mobile-completion-workboard.md).

- Follow Clean Architecture: `presentation → domain ← data`; domain remains pure Dart.
- Send all HTTP through the gateway using `core/network/api_client.dart`.
- Mirror each backend JSON contract exactly. Patient currently uses Vietnamese camelCase;
  Clinical and Lab use English camelCase.
- Use Riverpod for state and SecureStorage for JWTs.
- Do not apply Java/JPA/Flyway rules or the Next.js frontend structure inside `mobile/`.
