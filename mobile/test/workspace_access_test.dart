import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/dependencies.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

class TestSession extends SessionController {
  @override
  Future<Session?> build() async => null;

  void replace(Session? session) => state = AsyncData(session);
  void loading() => state = const AsyncLoading();
}

void main() {
  test(
    'composition removes access during login/logout and changes identity',
    () async {
      final container = ProviderContainer(
        overrides: [
          ...appOverrides,
          sessionProvider.overrideWith(TestSession.new),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);
      expect(container.read(workspaceAccessProvider), isNull);
      final controller =
          container.read(sessionProvider.notifier) as TestSession;
      controller.replace(
        const Session(
          accessToken: 'session-one',
          refreshToken: 'r',
          role: UserRole.admin,
        ),
      );
      final first = container.read(workspaceAccessProvider)!;
      expect(first.role, 'ADMIN');
      expect(first.sessionIdentity, isNot(isA<String>()));
      expect(
        container.read(workspaceAccessProvider)!.sessionIdentity,
        same(first.sessionIdentity),
      );
      controller.loading();
      expect(container.read(workspaceAccessProvider), isNull);
      controller.replace(
        const Session(
          accessToken: 'session-two',
          refreshToken: 'r',
          role: UserRole.nurse,
        ),
      );
      final second = container.read(workspaceAccessProvider)!;
      expect(second.role, 'NURSE');
      expect(second.sessionIdentity, isNot(first.sessionIdentity));
      controller.replace(null);
      expect(container.read(workspaceAccessProvider), isNull);
    },
  );
}
