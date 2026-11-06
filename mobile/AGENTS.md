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

## UI look

- Theme lives in `lib/core/theme/`; shared pieces in `lib/core/widgets/medical_ui.dart`. Prefer stock
  Material widgets so the theme applies, and read colours from the theme, never hard-code them.
- `Wayfinding` colours name an area of the app (`AppModule.accent`). `MedicalColors` statuses
  (`StatusPill`, `NoticeBox`) name a state. Do not use one for the other.
- A page or section waiting on data shows `MediFlowLoader`. Keep `CircularProgressIndicator` for the
  small spinner inside a button and `LinearProgressIndicator` for a thin progress bar.
