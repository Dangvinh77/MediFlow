---
description: Path-scoped rule for mobile/ (Flutter). Points to docs/ai/14-flutter.md.
globs: ["mobile/**/*.dart", "mobile/pubspec.yaml", "mobile/analysis_options.yaml"]
---

# MediFlow Flutter (mobile/)

The rules for `mobile/` live in [`docs/ai/14-flutter.md`](../docs/ai/14-flutter.md).
That file is authoritative — read it before editing any `mobile/` code.

## Summary

- **Clean Architecture:** `data → domain ← presentation`. Domain is pure Dart.
- **Gateway only:** all HTTP through `core/network/api_client.dart`.
- **DTOs:** Vietnamese camelCase (`hoTen`, `maBenhNhan`), mirror backend JSON.
- **State:** Riverpod (not Bloc unless the team agrees).
- **Auth:** JWT in SecureStorage, auto-attach via Dio interceptor.
- **No service port calls:** the backend enforces authorization, not the app.

The Java rules (clean architecture with JPA, `@PreAuthorize`, Flyway) do **not**
apply here. Nor do the Next.js frontend rules (`frontend/AGENTS.md`). Flutter has
its own architecture — follow `docs/ai/14-flutter.md`.
