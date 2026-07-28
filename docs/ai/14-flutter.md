# 14 — Flutter Mobile Blueprint (MANDATORY)

> The mobile counterpart of `04` (backend) and `12` (frontend). Where those govern Java and
> Next.js, this governs [`mobile/`](../../mobile/). The layout below is **not negotiable** —
> it mirrors the backend's bounded contexts exactly, making it trivial to map an API call to
> the code that handles it.

## Stack

| | |
|---|---|
| Framework | **Flutter** (stable channel) |
| Language | **Dart** 3.x |
| State management | **Riverpod** (recommended) or **Bloc** |
| HTTP client | **Dio** — wrapped once in `core/network/api_client.dart` |
| Routing | **GoRouter** |
| Local storage | `shared_preferences` + `flutter_secure_storage` |
| JSON serialization | `json_serializable` + `freezed` |
| Architecture | **Clean Architecture** — `data → domain ← presentation` |

## Architecture rule

```
presentation  ───►  domain  ◄───  data
```

Dependencies point **inward**:

- **`domain/`** — pure Dart. No Flutter, no Dio, no `BuildContext`, no `Widget`.
  Entities + abstract repository interfaces + use cases.
- **`data/`** — implements domain repositories. Dio calls the gateway,
  JSON models deserialize responses. Depends on `domain/` only.
- **`presentation/`** — Flutter widgets + Riverpod providers. Depends on `domain/`.

## The mandatory tree

```
mobile/
├── pubspec.yaml
├── analysis_options.yaml
└── lib/
    ├── main.dart                     # runApp + ProviderScope
    ├── app/
    │   ├── app.dart                  # MaterialApp.router (GoRouter)
    │   ├── router.dart               # route definitions
    │   └── di.dart                   # Provider definitions (Riverpod)
    ├── core/
    │   ├── constants/
    │   │   └── api_constants.dart    # base URL, endpoints
    │   ├── theme/
    │   │   ├── app_theme.dart        # light/dark theme data
    │   │   └── app_colors.dart       # semantic color tokens
    │   ├── network/
    │   │   ├── api_client.dart       # Dio singleton + interceptors
    │   │   ├── api_endpoints.dart    # endpoint string constants
    │   │   └── interceptors/
    │   │       ├── auth_interceptor.dart    # attach JWT
    │   │       └── error_interceptor.dart   # 401 → logout, 403 → toast
    │   ├── storage/
    │   │   └── secure_storage.dart   # token persistence
    │   └── utils/
    │       └── validators.dart       # input validation helpers
    ├── features/
    │   ├── auth/
    │   │   ├── data/
    │   │   │   ├── datasources/
    │   │   │   │   └── auth_remote_source.dart
    │   │   │   ├── models/
    │   │   │   │   └── login_response.dart
    │   │   │   └── repositories/
    │   │   │       └── auth_repository_impl.dart
    │   │   ├── domain/
    │   │   │   ├── entities/
    │   │   │   │   └── user.dart
    │   │   │   ├── repositories/
    │   │   │   │   └── auth_repository.dart     # abstract
    │   │   │   └── usecases/
    │   │   │       └── login_usecase.dart
    │   │   └── presentation/
    │   │       ├── providers/
    │   │       │   └── auth_provider.dart
    │   │       ├── pages/
    │   │       │   └── login_page.dart
    │   │       └── widgets/
    │   │           └── login_form.dart
    │   ├── patient/
    │   │   ├── data/
    │   │   │   ├── datasources/
    │   │   │   ├── models/
    │   │   │   └── repositories/
    │   │   ├── domain/
    │   │   │   ├── entities/
    │   │   │   ├── repositories/
    │   │   │   └── usecases/
    │   │   └── presentation/
    │   │       ├── providers/
    │   │       ├── pages/
    │   │       └── widgets/
    │   ├── appointment/
    │   ├── medical_record/
    │   ├── lab/
    │   ├── pharmacy/
    │   ├── billing/
    │   ├── notification/
    │   ├── report/
    │   └── organization/            # departments, staff lookup
    └── l10n/                        # ARB localization files
        ├── app_en.arb
        └── app_vi.arb
```

> **Note:** Feature folders mirror the backend bounded contexts 1:1. If the backend has
> `patient-service`, mobile has `features/patient/`. Only `auth` is mobile-only (it owns
> the login screen and JWT storage — the gateway handles auth on the backend side).

## Layer responsibilities

| Layer | File/Dir | What goes here |
|-------|----------|----------------|
| `data/datasources/` | Remote/local | Dio calls to gateway, SharedPrefs reads/writes |
| `data/models/` | JSON DTOs | `fromJson`/`toJson`, Vietnamese camelCase fields |
| `data/repositories/` | Impl | Implements domain repository interface |
| `domain/entities/` | Pure objects | Dart classes with business logic, no `fromJson` |
| `domain/repositories/` | Abstracts | Interfaces the data layer implements |
| `domain/usecases/` | Use cases | One class per operation, injects a repository |
| `presentation/providers/` | Riverpod | StateNotifierProvider / FutureProvider / ... |
| `presentation/pages/` | Screens | Full-page widgets, compose providers + widgets |
| `presentation/widgets/` | Feature UI | Reusable widgets for this feature only |

## Naming conventions

| What | Convention | Example |
|------|-----------|---------|
| Feature directory | `features/<snake_case>/` | `features/patient/` |
| Files | `snake_case.dart` | `login_page.dart` |
| Classes | `PascalCase` | `LoginPage`, `PatientEntity` |
| DTO fields | Vietnamese `camelCase` | `hoTen`, `maBenhNhan` |
| Endpoint constants | `SCREAMING_SNAKE_CASE` | `static const patients = '/api/v1/patients';` |

## Non-negotiables

1. **Gateway only.** Every HTTP request goes to `localhost:8080` (dev) or same-origin
   `/api/v1/*`. Never call a service port (`:8081`…) directly.
2. **All HTTP through `api_client.dart`.** No `http.get` / `dio.get` in feature code.
   Features call the repository; the repository uses `api_client.dart`.
3. **DTOs in Vietnamese camelCase.** Mirror the backend exactly. JSON annotation:
   ```dart
   @JsonKey(name: 'hoTen') final String hoTen;
   ```
4. **Domain is pure Dart.** No `import 'package:flutter/...'` in `domain/`.
5. **Test use cases and providers.** Domain unit tests need no Flutter.
   Provider tests use `ProviderContainer()`.
6. **JWT in SecureStorage.** Never store tokens in plain SharedPrefs.
7. **No service port calls.** The backend enforces authorization — the app just
   sends the JWT. Hiding a button by role is UX, not security.
8. **Always use `const` constructors** where possible (Flutter perf).

## API contract

The mobile app talks to the **gateway** only:

| Method | Endpoint | Feature |
|--------|----------|---------|
| POST | `/api/v1/auth/login` | auth |
| GET | `/api/v1/patients?keyword=&page=&size=` | patient |
| POST | `/api/v1/patients` | patient |
| GET | `/api/v1/appointments/patient/{id}` | appointment |
| POST | `/api/v1/appointments` | appointment |
| GET | `/api/v1/records/patient/{id}` | medical_record |
| POST | `/api/v1/records` | medical_record |
| GET | `/api/v1/lab?patientId=` | lab |
| GET | `/api/v1/pharmacy/drugs` | pharmacy |
| GET | `/api/v1/billing/patient/{id}` | billing |
| GET | `/api/v1/notifications/patient/{id}` | notification |
| GET | `/api/v1/reports` | report |
| GET | `/api/v1/org/departments` | organization |

All responses wrapped in `ApiResponse<T>` envelope:
```json
{ "success": true, "data": {...}, "error": null, "timestamp": "...", "correlationId": "..." }
```

## Dependencies (pubspec.yaml)

```yaml
dependencies:
  flutter:
    sdk: flutter
  flutter_riverpod: ^2.6.0
  riverpod_annotation: ^2.6.0
  go_router: ^14.0.0
  dio: ^5.4.0
  json_annotation: ^4.9.0
  freezed_annotation: ^2.4.0
  shared_preferences: ^2.3.0
  flutter_secure_storage: ^9.2.0
  intl: ^0.19.0
  equatable: ^2.0.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  build_runner: ^2.4.0
  json_serializable: ^6.8.0
  freezed: ^2.5.0
  riverpod_generator: ^2.6.0
  mockito: ^5.4.0
  flutter_lints: ^4.0.0
```

## Build & run

```bash
# Prerequisites: Flutter SDK (stable)
cd mobile
flutter pub get
flutter run                          # default device
flutter run -d chrome                # web (dev only)
flutter test                         # run all tests
flutter build apk                    # release APK
flutter build ios                    # release IPA (macOS only)
```

Start order: `eureka-server (8761) → gateway (8080) → backend services → flutter run`.

## Related docs

- [`docs/ai/05-api-conventions.md`](05-api-conventions.md) — API contracts (shared)
- [`docs/ai/07-security-rbac.md`](07-security-rbac.md) — role constants
- [`docs/ai/12-frontend.md`](12-frontend.md) — frontend conventions (read for reference, do NOT apply Flutter rules there)
- `backend/` service docs — DTO field definitions
