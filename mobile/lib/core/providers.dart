import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'config/app_config.dart';
import 'domain/workspace_access.dart';
import 'network/api_client.dart';

final workspaceAccessProvider = Provider<WorkspaceAccess?>((ref) => null);

// Auth writes this bridge; other contexts depend only on shared Gateway services.
class ApiSession {
  String? accessToken;
  void Function(String)? onUnauthorized;
}

final apiSessionProvider = Provider((ref) => ApiSession());
final appConfigProvider = Provider<AppConfig>((ref) => AppConfig.environment());
final apiClientProvider = Provider<ApiClient>((ref) {
  final session = ref.watch(apiSessionProvider);
  final client = ApiClient(
    ref.watch(appConfigProvider),
    token: () => session.accessToken,
    onUnauthorized: (token) => session.onUnauthorized?.call(token),
  );
  ref.onDispose(client.close);
  return client;
});
