import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'session_controller_test.dart' show MemoryAuth;

void main() {
  testWidgets(
    'direct role-denied route is blocked and logout removes workspace',
    (tester) async {
      final auth = MemoryAuth()
        ..saved = const Session(
          accessToken: 'a',
          refreshToken: 'r',
          role: UserRole.cashier,
        );
      final container = ProviderContainer(
        overrides: [authRepositoryProvider.overrideWithValue(auth)],
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
      expect(find.text('Viện phí'), findsWidgets);
      expect(find.text('Xét nghiệm'), findsNothing);
      container.read(routerProvider).go('/lab');
      await tester.pumpAndSettle();
      expect(
        find.text('Bạn không có quyền truy cập chức năng này.'),
        findsOneWidget,
      );
      await tester.tap(find.byTooltip('Đăng xuất'));
      await tester.pumpAndSettle();
      expect(find.text('Tên đăng nhập'), findsOneWidget);
      expect(auth.saved, isNull);
    },
  );

  testWidgets(
    'signed-out app shows real login and validates missing credentials',
    (tester) async {
      final auth = MemoryAuth();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [authRepositoryProvider.overrideWithValue(auth)],
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('MediFlow'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
      await tester.pump();
      expect(find.text('Nhập tên đăng nhập.'), findsOneWidget);
      expect(find.text('Nhập mật khẩu.'), findsOneWidget);
      expect(auth.logins, 0);
    },
  );
}
