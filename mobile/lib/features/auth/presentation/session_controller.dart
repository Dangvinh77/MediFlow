import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/providers.dart';
import '../domain/auth_repository.dart';
import '../domain/session.dart';
import 'providers.dart';
export 'providers.dart' show authRepositoryProvider;

final sessionProvider = AsyncNotifierProvider<SessionController, Session?>(
  SessionController.new,
);

class SessionController extends AsyncNotifier<Session?> {
  int _generation = 0;

  @override
  Future<Session?> build() {
    final bridge = ref.read(apiSessionProvider);
    bridge.onUnauthorized = invalidateToken;
    listenSelf(
      (_, next) => bridge.accessToken = next.asData?.value?.accessToken,
    );
    ref.onDispose(() {
      bridge.accessToken = null;
      bridge.onUnauthorized = null;
    });
    return ref.watch(authRepositoryProvider).restore();
  }

  Future<void> signIn(String username, String password) async {
    if (state.isLoading) return;
    final generation = ++_generation;
    final repository = ref.read(authRepositoryProvider);
    state = const AsyncLoading();
    try {
      final session = await SignIn(repository)(username, password);
      if (!ref.mounted || generation != _generation) return;
      await repository.save(session);
      if (!ref.mounted || generation != _generation) return;
      state = AsyncData(session);
    } catch (error, stack) {
      if (ref.mounted && generation == _generation) {
        state = AsyncError(error, stack);
      }
    }
  }

  Future<void> signOut() async {
    final generation = ++_generation;
    state = const AsyncData(null);
    try {
      await ref.read(authRepositoryProvider).clear();
    } catch (error, stack) {
      if (ref.mounted && generation == _generation) {
        state = AsyncError(error, stack);
      }
    }
  }

  void invalidateToken(String token) {
    if (state.asData?.value?.accessToken == token) unawaited(signOut());
  }
}
