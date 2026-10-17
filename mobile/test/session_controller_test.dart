import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/auth/domain/auth_repository.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

class MemoryAuth implements AuthRepository {
  Session? saved;
  Completer<Session>? pending;
  int logins = 0;
  @override
  Future<Session?> restore() async => saved;
  @override
  Future<Session> login(String u, String p) async {
    logins++;
    return pending?.future ??
        const Session(
          accessToken: 'new',
          refreshToken: 'r',
          role: UserRole.doctor,
        );
  }

  @override
  Future<void> save(Session s) async {
    saved = s;
  }

  @override
  Future<void> clear() async {
    saved = null;
  }
}

void main() {
  late MemoryAuth auth;
  late ProviderContainer container;
  setUp(() {
    auth = MemoryAuth();
    container = ProviderContainer(
      overrides: [authRepositoryProvider.overrideWithValue(auth)],
    );
  });
  tearDown(() => container.dispose());

  test('restores and signs in through repository', () async {
    expect(await container.read(sessionProvider.future), isNull);
    await container.read(sessionProvider.notifier).signIn(' doctor ', 'secret');
    expect(container.read(sessionProvider).requireValue?.role, UserRole.doctor);
    expect(auth.saved?.accessToken, 'new');
  });
  test('duplicate sign-in is not sent twice', () async {
    await container.read(sessionProvider.future);
    auth.pending = Completer<Session>();
    final first = container
        .read(sessionProvider.notifier)
        .signIn('doctor', 'secret');
    await container.read(sessionProvider.notifier).signIn('doctor', 'secret');
    expect(auth.logins, 1);
    auth.pending!.complete(
      const Session(accessToken: 'a', refreshToken: 'r', role: UserRole.doctor),
    );
    await first;
  });
  test('late login cannot restore a logged-out session', () async {
    await container.read(sessionProvider.future);
    auth.pending = Completer<Session>();
    final login = container
        .read(sessionProvider.notifier)
        .signIn('doctor', 'secret');
    await container.read(sessionProvider.notifier).signOut();
    auth.pending!.complete(
      const Session(
        accessToken: 'old',
        refreshToken: 'r',
        role: UserRole.doctor,
      ),
    );
    await login;
    expect(container.read(sessionProvider).requireValue, isNull);
    expect(auth.saved, isNull);
  });
  test('old 401 does not sign out the current session', () async {
    await container.read(sessionProvider.future);
    await container.read(sessionProvider.notifier).signIn('doctor', 'secret');
    container.read(sessionProvider.notifier).invalidateToken('old');
    expect(container.read(sessionProvider).requireValue?.accessToken, 'new');
    container.read(sessionProvider.notifier).invalidateToken('new');
    expect(container.read(sessionProvider).requireValue, isNull);
  });
  test('validation failure never calls network', () async {
    await container.read(sessionProvider.future);
    await container.read(sessionProvider.notifier).signIn(' ', '');
    expect(auth.logins, 0);
    expect(container.read(sessionProvider).hasError, isTrue);
  });
}
