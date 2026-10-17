# Shared authentication

Owner/coordinator: Vinh. Root/mobile AGENTS and docs/ai/14-flutter.md apply.
Keep domain pure Dart; data owns serialization/storage; presentation consumes domain providers.
Bind concrete adapters only in app/dependencies.dart. Never log credentials or tokens.
No automatic replay of mutations. Token-specific 401 must not clear a newer session.
Verify auth/session regressions and mobile/tool/check.ps1.
