# Shared mobile composition and infrastructure

Owner/coordinator: Vinh. Root/mobile AGENTS and docs/ai/14-flutter.md apply.
Shared writes in app/, core/, auth, package manifests and platform hosts require coordination with Vinh.
Concrete data adapter and SecureStorage construction belongs in app/dependencies.dart; presentation consumes domain interfaces.
Keep every request on core/network/api_client.dart through Gateway. No fake data or demo runtime.
Verify with mobile/tool/check.ps1 and real-backend smoke when accounts/services are available.
