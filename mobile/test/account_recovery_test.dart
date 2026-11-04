import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/auth/data/gateway_account_recovery.dart';
import 'package:mediflow_mobile/features/auth/domain/account_recovery.dart';
import 'package:mediflow_mobile/features/auth/domain/auth_repository.dart';
import 'package:mediflow_mobile/features/auth/presentation/account_recovery_page.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'api_client_test.dart' show RecordingAdapter;
import 'session_controller_test.dart' show MemoryAuth;

class RecoveryFake implements AccountRecoveryRepository {
  int requests = 0;
  String? receivedToken;
  Completer<void>? pending;
  @override
  Future<void> forgot(String email) async {
    requests++;
    await pending?.future;
  }

  @override
  Future<void> reset(String token, String newPassword) async {
    requests++;
    receivedToken = token;
  }
}

void main() {
  test(
    'sign-in accepts email longer than the retired 50-character username limit',
    () async {
      final auth = MemoryAuth();
      await SignIn(auth)('${'a' * 60}@example.org', 'password123');
      expect(auth.logins, 1);
      expect(
        () => SignIn(auth)('${'a' * 255}@example.org', 'password123'),
        throwsFormatException,
      );
    },
  );

  test(
    'recovery uses exact public Gateway payloads without bearer credentials',
    () async {
      final adapter = RecordingAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'existing-session',
      );
      addTearDown(client.close);
      final useCase = RecoverAccount(GatewayAccountRecovery(client));
      adapter.body = {
        'success': true,
        'data': {'accepted': true},
      };
      await useCase.forgot(' patient@example.org ');
      expect(adapter.requests.single.uri.path, '/api/v1/auth/forgot-password');
      expect(adapter.requests.single.data, {'email': 'patient@example.org'});
      adapter.body = {
        'success': true,
        'data': {'success': true},
      };
      await useCase.reset(
        'https://clinic.example/reset-password?token=recovery-token',
        'new-password',
      );
      expect(adapter.requests.last.uri.path, '/api/v1/auth/reset-password');
      expect(adapter.requests.last.data, {
        'token': 'recovery-token',
        'newPassword': 'new-password',
      });
      expect(
        adapter.requests.every(
          (request) => !request.headers.containsKey('Authorization'),
        ),
        isTrue,
      );
      adapter.body = {'success': true, 'data': null};
      await expectLater(
        useCase.forgot('patient@example.org'),
        throwsFormatException,
      );
      await expectLater(
        useCase.reset('token', 'new-password'),
        throwsFormatException,
      );
    },
  );

  test('invalid recovery input never reaches repository', () {
    final fake = RecoveryFake();
    final useCase = RecoverAccount(fake);
    expect(() => useCase.forgot('invalid'), throwsFormatException);
    expect(
      () => useCase.reset('https://example.org/no-token', 'new-password'),
      throwsFormatException,
    );
    expect(() => useCase.reset('token', 'short'), throwsFormatException);
    expect(fake.requests, 0);
  });

  testWidgets(
    'signed-out recovery routes work and duplicate requests stay disabled',
    (tester) async {
      final fake = RecoveryFake()..pending = Completer<void>();
      final container = ProviderContainer(
        overrides: [
          authRepositoryProvider.overrideWithValue(MemoryAuth()),
          accountRecoveryProvider.overrideWithValue(fake),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Quên mật khẩu?'));
      await tester.tap(find.text('Quên mật khẩu?'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'patient@example.org');
      await tester.tap(find.text('Gửi hướng dẫn'));
      await tester.pump();
      expect(fake.requests, 1);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      fake.pending!.complete();
      await tester.pumpAndSettle();
      expect(find.textContaining('Nếu email đủ điều kiện'), findsOneWidget);
      container.read(routerProvider).go('/reset-password?token=email-token');
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(find.byType(TextField).first).controller!.text,
        'email-token',
      );
      await tester.enterText(find.byType(TextField).at(1), 'new-password');
      await tester.enterText(
        find.byType(TextField).at(2),
        'different-password',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Đặt lại mật khẩu'));
      await tester.pumpAndSettle();
      expect(find.text('Mật khẩu xác nhận chưa khớp.'), findsOneWidget);
      expect(fake.requests, 1);
      await tester.enterText(find.byType(TextField).at(2), 'new-password');
      await tester.ensureVisible(
        find.widgetWithText(FilledButton, 'Đặt lại mật khẩu'),
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Đặt lại mật khẩu'));
      await tester.pumpAndSettle();
      expect(fake.receivedToken, 'email-token');
      expect(find.textContaining('Đã đặt lại mật khẩu.'), findsOneWidget);
      expect(find.byType(TextField), findsNothing);
    },
  );
}
