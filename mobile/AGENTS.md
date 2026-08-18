# MediFlow Mobile Instructions

Read [`docs/ai/14-flutter.md`](../docs/ai/14-flutter.md) before editing mobile code. It is the authoritative Flutter blueprint. Mirror gateway contracts from [`docs/ai/05-api-conventions.md`](../docs/ai/05-api-conventions.md).

- Follow Clean Architecture: `presentation → domain ← data`; domain remains pure Dart.
- Send all HTTP through the gateway using `core/network/api_client.dart`.
- Mirror backend JSON with Vietnamese camelCase DTO fields.
- Use Riverpod for state and SecureStorage for JWTs.
- Do not apply Java/JPA/Flyway rules or the Next.js frontend structure inside `mobile/`.
