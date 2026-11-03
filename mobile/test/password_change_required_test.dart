import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'api_client_test.dart' show RecordingAdapter;
import 'session_controller_test.dart' show MemoryAuth;

void main() {
  group('Gateway AUTH_PASSWORD_CHANGE_REQUIRED', () {
    late RecordingAdapter adapter;
    late ApiClient client;
    final required = <String>[];
    final unauthorized = <String>[];
    setUp(() {
      required.clear();
      unauthorized.clear();
      adapter = RecordingAdapter()..status = 403;
      client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'signed-token',
        onUnauthorized: unauthorized.add,
        onPasswordChangeRequired: required.add,
      );
    });
    tearDown(() => client.close());

    test('reports the exact request token and does not replay', () async {
      adapter.body = {
        'error': {
          'code': 'AUTH_PASSWORD_CHANGE_REQUIRED',
          'message': 'Bạn phải đổi mật khẩu ban đầu.',
        },
      };
      await expectLater(
        client.get('/v1/appointments'),
        throwsA(
          isA<ApiFailure>()
              .having((e) => e.status, 'status', 403)
              .having((e) => e.code, 'code', 'AUTH_PASSWORD_CHANGE_REQUIRED'),
        ),
      );
      expect(required, ['signed-token']);
      expect(unauthorized, isEmpty);
      expect(adapter.requests, hasLength(1));
    });

    test('an ordinary 403 is not a password requirement', () async {
      adapter.body = {
        'error': {'code': 'ACCESS_DENIED', 'message': 'Denied'},
      };
      await expectLater(
        client.get('/v1/appointments'),
        throwsA(isA<ApiFailure>()),
      );
      expect(required, isEmpty);
    });
  });

  group('SessionController.requirePasswordChange', () {
    late MemoryAuth auth;
    late ProviderContainer container;
    setUp(() {
      auth = MemoryAuth();
      container = ProviderContainer(
        overrides: [authRepositoryProvider.overrideWithValue(auth)],
      );
    });
    tearDown(() => container.dispose());

    test('flags only the session that owns the rejected token', () async {
      await container.read(sessionProvider.future);
      await container.read(sessionProvider.notifier).signIn('doctor', 'secret');
      final notifier = container.read(sessionProvider.notifier);

      notifier.requirePasswordChange('old');
      expect(
        container.read(sessionProvider).requireValue!.mustChangePassword,
        isFalse,
      );

      notifier.requirePasswordChange('new');
      final session = container.read(sessionProvider).requireValue!;
      expect(session.accessToken, 'new');
      expect(session.role, UserRole.doctor);
      expect(session.mustChangePassword, isTrue);
      // Session is kept: the change endpoint accepts this token.
      expect(auth.saved?.accessToken, 'new');
    });

    test('signing out clears the requirement', () async {
      await container.read(sessionProvider.future);
      await container.read(sessionProvider.notifier).signIn('doctor', 'secret');
      container.read(sessionProvider.notifier).requirePasswordChange('new');
      await container.read(sessionProvider.notifier).signOut();
      expect(container.read(sessionProvider).requireValue, isNull);
    });
  });
}
