# Mobile pharmacy

Owner: **Huy**. Root/mobile AGENTS and docs/ai/14-flutter.md apply.
Writable business scope: `mobile/lib/features/pharmacy/**` and its tests.
Use presentation → domain ← data. Exact Gateway contracts only; no feature-to-feature imports.
Shared network/session/router/config changes require coordination with Vinh. Do not edit another owner feature.
Verify: `dart format lib test`, `flutter analyze`, `flutter test`, `flutter build web`.
