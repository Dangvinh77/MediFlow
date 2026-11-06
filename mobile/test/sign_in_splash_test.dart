import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_loader/mediflow_loader.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';
import 'package:mediflow_mobile/features/auth/presentation/sign_in_splash.dart';

import 'session_controller_test.dart' show MemoryAuth;

const _splashLabel = 'Đang vào hệ thống…';

Future<void> _pumpApp(WidgetTester tester, MemoryAuth auth) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [authRepositoryProvider.overrideWithValue(auth)],
      child: const MediFlowApp(),
    ),
  );
}

void main() {
  testWidgets('signing in holds a centred loader over the whole screen', (
    tester,
  ) async {
    await _pumpApp(tester, MemoryAuth());
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).first, 'a@b.vn');
    await tester.enterText(find.byType(TextFormField).last, 'secret');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pump();
    await tester.pump();

    expect(find.text(_splashLabel), findsOneWidget);
    final screen = tester.getCenter(find.byType(SignInSplash));
    final loader = tester.getCenter(
      find.widgetWithText(MediFlowLoader, _splashLabel),
    );
    expect(loader, screen);

    await tester.pump(signInSplashDuration - const Duration(milliseconds: 100));
    expect(find.text(_splashLabel), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump();
    expect(find.text(_splashLabel), findsNothing);
  });

  testWidgets('a failed sign-in does not show the loader', (tester) async {
    await _pumpApp(tester, _RejectingAuth());
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).first, 'a@b.vn');
    await tester.enterText(find.byType(TextFormField).last, 'wrong');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pump();
    await tester.pump();

    expect(find.text(_splashLabel), findsNothing);
  });

  testWidgets('a restored session opens without the loader', (tester) async {
    final auth = MemoryAuth()
      ..saved = const Session(
        accessToken: 'saved',
        refreshToken: 'r',
        role: UserRole.doctor,
      );
    await _pumpApp(tester, auth);
    await tester.pump();
    await tester.pump();

    expect(find.text(_splashLabel), findsNothing);
  });
}

class _RejectingAuth extends MemoryAuth {
  @override
  Future<Session> login(String u, String p) async =>
      throw StateError('rejected');
}
