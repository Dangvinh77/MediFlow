import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/auth/data/gateway_password_repository.dart';
import 'package:mediflow_mobile/features/auth/domain/auth_repository.dart';
import 'package:mediflow_mobile/features/auth/domain/change_password.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/change_password_page.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'api_client_test.dart' show RecordingAdapter;
import 'workspace_access_test.dart' show TestSession;

Session patient({bool requiredChange = true}) => Session(
  accessToken:
      'header.${base64Url.encode(utf8.encode(jsonEncode({'mustChangePassword': requiredChange})))}.signature',
  refreshToken: 'refresh',
  role: UserRole.patient,
);

class _Passwords implements PasswordRepository {
  int calls = 0;
  Future<void> Function()? action;
  @override
  Future<void> change(String currentPassword, String newPassword) async {
    calls++;
    await action?.call();
  }
}

class _Auth implements AuthRepository {
  int clears = 0;
  @override
  Future<void> clear() async {
    clears++;
  }

  @override
  Future<Session?> restore() async => patient();
  @override
  Future<void> save(Session session) async {}
  @override
  Future<Session> login(String username, String password) async => patient();
}

void main() {
  test(
    'password command uses exact own-account wire and true confirmation',
    () async {
      final adapter = RecordingAdapter()
        ..body = {'success': true, 'data': true};
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'signed',
      );
      addTearDown(client.close);
      final repository = GatewayPasswordRepository(client);
      await ChangeMyPassword(repository)('1', 'password-new');
      final request = adapter.requests.single;
      expect(request.uri.path, '/api/v1/org/accounts/me/password');
      expect(request.method, 'PUT');
      expect(request.headers['Authorization'], 'Bearer signed');
      expect(request.data, {
        'currentPassword': '1',
        'newPassword': 'password-new',
      });
      adapter.body = {'success': true, 'data': false};
      await expectLater(
        repository.change('1', 'password-new'),
        throwsFormatException,
      );
      expect(
        () => ChangeMyPassword(repository)('1', 'short'),
        throwsFormatException,
      );
      expect(adapter.requests, hasLength(2));
    },
  );
  test('password flag is a routing hint from access claims', () {
    expect(patient().mustChangePassword, isTrue);
    expect(patient(requiredChange: false).mustChangePassword, isFalse);
    expect(
      const Session(
        accessToken: 'malformed',
        refreshToken: 'r',
        role: UserRole.patient,
      ).mustChangePassword,
      isFalse,
    );
  });

  Future<ProviderContainer> host(
    WidgetTester tester,
    _Passwords passwords,
    _Auth auth,
  ) async {
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(auth),
        passwordRepositoryProvider.overrideWithValue(passwords),
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
    return container;
  }

  Future<void> fill(
    WidgetTester tester, {
    String confirm = 'password-new',
  }) async {
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), '1');
    await tester.enterText(fields.at(1), 'password-new');
    await tester.enterText(fields.at(2), confirm);
    await tester.ensureVisible(find.text('Lưu mật khẩu mới'));
    await tester.tap(find.text('Lưu mật khẩu mới'));
    await tester.pump();
  }

  testWidgets(
    'required password change intercepts deep link and successful change clears session',
    (tester) async {
      final auth = _Auth();
      final passwords = _Passwords();
      final container = await host(tester, passwords, auth);
      container.read(routerProvider).go('/my-results/exams');
      await tester.pumpAndSettle();
      expect(
        container.read(routerProvider).routeInformationProvider.value.uri.path,
        '/account/password',
      );
      await fill(tester, confirm: 'wrong');
      expect(passwords.calls, 0);
      expect(find.text('Mật khẩu xác nhận không khớp.'), findsOneWidget);
      await fill(tester);
      await tester.pumpAndSettle();
      expect(passwords.calls, 1);
      expect(auth.clears, 1);
      expect(container.read(sessionProvider).asData?.value, isNull);
      expect(
        container.read(routerProvider).routeInformationProvider.value.uri.path,
        '/login',
      );
    },
  );
  testWidgets(
    'pending request blocks double tap; uncertain outcome blocks replay',
    (tester) async {
      final pending = Completer<void>();
      final passwords = _Passwords()..action = () => pending.future;
      await host(tester, passwords, _Auth());
      await fill(tester);
      expect(passwords.calls, 1);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      pending.completeError(const ApiFailure('Mất kết nối'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Chưa xác định kết quả.'), findsOneWidget);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(passwords.calls, 1);
      for (final field in tester.widgetList<TextFormField>(
        find.byType(TextFormField),
      )) {
        expect(field.controller!.text, isEmpty);
      }
    },
  );
  testWidgets(
    'late password response does not sign out a replacement session',
    (tester) async {
      final pending = Completer<void>();
      final passwords = _Passwords()..action = () => pending.future;
      final container = ProviderContainer(
        overrides: [
          sessionProvider.overrideWith(TestSession.new),
          passwordRepositoryProvider.overrideWithValue(passwords),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);
      final controller =
          container.read(sessionProvider.notifier) as TestSession;
      controller.replace(patient());
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(home: Scaffold(body: ChangePasswordPage())),
        ),
      );
      await tester.pumpAndSettle();
      await fill(tester);
      final replacement = patient(requiredChange: false);
      controller.replace(replacement);
      await tester.pump();
      pending.complete();
      await tester.pumpAndSettle();
      expect(container.read(sessionProvider).asData?.value, same(replacement));
    },
  );
}
