import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/dependencies.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'workspace_access_test.dart' show TestSession;

class _GatewayAdapter implements HttpClientAdapter {
  final paths = <String>[];
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    paths.add(options.uri.path);
    return ResponseBody.fromString(
      jsonEncode({
        'success': true,
        'data': options.uri.path.endsWith('/departments')
            ? <Object>[]
            : {
                'content': <Object>[],
                'number': 0,
                'totalPages': 0,
                'totalElements': 0,
              },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  testWidgets(
    'real app composition exposes delegated clients and clears role state',
    (tester) async {
      final adapter = _GatewayAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      final container = ProviderContainer(
        overrides: [
          ...appOverrides,
          sessionProvider.overrideWith(TestSession.new),
          apiClientProvider.overrideWithValue(client),
        ],
      );
      addTearDown(() {
        container.dispose();
        client.close();
      });
      await container.read(sessionProvider.future);
      final session = container.read(sessionProvider.notifier) as TestSession;
      session.replace(
        const Session(
          accessToken: 'admin',
          refreshToken: 'r',
          role: UserRole.admin,
        ),
      );
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();

      container.read(routerProvider).go('/organization');
      await tester.pumpAndSettle();
      expect(find.text('Chưa có khoa phòng phù hợp.'), findsOneWidget);
      expect(adapter.paths, contains('/api/v1/org/departments'));
      container.read(routerProvider).go('/patients');
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Tìm kiếm'));
      await tester.pumpAndSettle();
      expect(adapter.paths, contains('/api/v1/patients'));

      container.read(routerProvider).go('/billing');
      await tester.pumpAndSettle();
      expect(find.text('Khoản thu'), findsOneWidget);
      expect(find.text('Hoàn tiền'), findsOneWidget);
      container.read(routerProvider).go('/notifications');
      await tester.pumpAndSettle();
      expect(find.text('Gửi thông báo'), findsOneWidget);

      session.replace(
        const Session(
          accessToken: 'nurse',
          refreshToken: 'r',
          role: UserRole.nurse,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Gửi thông báo'), findsNothing);
      container.read(routerProvider).go('/billing');
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền truy cập chức năng này.'),
        findsOneWidget,
      );
      session.replace(
        const Session(
          accessToken: 'manager',
          refreshToken: 'r',
          role: UserRole.manager,
        ),
      );
      container.read(routerProvider).go('/billing');
      await tester.pumpAndSettle();
      expect(find.text('Báo cáo'), findsWidgets);
      expect(find.text('Khoản thu'), findsNothing);
    },
  );
}
