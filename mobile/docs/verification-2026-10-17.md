# Mobile foundation verification — 2026-10-17

Branch: codex/vinh-frontend-mobile-foundation. Verification recorded before publication; subsequent user request authorizes push and PR. No deployment. Backend and existing Review1 artifacts were not modified by this mobile task.

## Approved scope

Real backend only. Shared Riverpod/GoRouter/Dio/SecureStorage foundation; separate context ownership for four developers. Appointment and Lab are real read-only API slices. Other business workspaces are explicitly unfinished; no runtime demo fixtures. Native hosts are generated, not certified native releases.

## Fresh evidence

- Dart format: 46 files, zero further changes, exit 0.
- flutter analyze: No issues found, exit 0 after composition fix.
- flutter test test packages/mediflow_loader/test: 28 passed, exit 0.
- flutter build web --dart-define=GATEWAY_URL=http://localhost:4300/api: built build/web, exit 0 (128.7s). Build emitted a missing optional Cupertino font-family warning; current visible icons use MaterialIcons. Wasm dry run succeeded; Wasm deployment not tested.
- Local static/proxy server served real compiled web at localhost:4300. Browser showed login and empty-field validation without layout clipping at 448x560.
- Request through proxy to /api/actuator/health returned 503 GATEWAY_UNAVAILABLE because upstream Gateway was unavailable. No authenticated API smoke or mutation acceptance claimed.
- Independent review: composition-direction finding fixed by moving concrete adapters/SecureStorage to app/dependencies.dart; reviewer rechecked and reported no remaining actionable mobile finding.

## Native environment limits

Flutter 3.47.0 stable / Dart 3.13.0. Web browser available. Android SDK present, licenses incomplete. Windows lacks required Visual Studio C++ workload/MSVC/CMake. iOS requires macOS/Xcode. Native Android/iOS/Windows build/run not verified. iOS ATS requires HTTPS; Dart insecure-HTTP opt-in does not override platform policy. Android release signing remains development scaffold.

## Ready for owners

Follow README.md and docs/contributing.md. Run tool/run_web.ps1 to use the real Gateway without changing backend CORS; run tool/check.ps1 before handoff. Shared app/core/auth and platform changes coordinate with Vinh. Each owner implements its context from exact backend contracts; missing contracts become registered handoffs.
