import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/auth/presentation/login_page.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'session_controller_test.dart' show MemoryAuth;

const _statusBarHeight = 24.0;

Future<void> _pumpLogin(
  WidgetTester tester, {
  required Brightness brightness,
  required double height,
}) async {
  tester.view
    ..devicePixelRatio = 1
    ..physicalSize = Size(400, height)
    ..padding = const FakeViewPadding(top: _statusBarHeight);
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [authRepositoryProvider.overrideWithValue(MemoryAuth())],
      child: MaterialApp(
        theme: ThemeData(brightness: brightness),
        home: const LoginPage(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Reads the style Flutter sends to the OS: the annotation under the status bar.
SystemUiOverlayStyle? _statusBarStyle(WidgetTester tester) => tester
    .binding
    .renderViews
    .single
    .debugLayer!
    .find<SystemUiOverlayStyle>(const Offset(200, _statusBarHeight / 2));

Future<void> _scrollHeaderAway(WidgetTester tester) async {
  await tester.drag(find.byType(SingleChildScrollView), const Offset(0, -1000));
  await tester.pumpAndSettle();
}

void main() {
  for (final brightness in Brightness.values) {
    testWidgets('$brightness: icons are light over the dark login header', (
      tester,
    ) async {
      await _pumpLogin(tester, brightness: brightness, height: 800);

      final style = _statusBarStyle(tester);
      expect(style?.statusBarIconBrightness, Brightness.light);
      expect(style?.statusBarBrightness, Brightness.dark);
    });
  }

  testWidgets('light theme: icons turn dark once the header scrolls away', (
    tester,
  ) async {
    await _pumpLogin(tester, brightness: Brightness.light, height: 300);
    await _scrollHeaderAway(tester);

    final style = _statusBarStyle(tester);
    expect(style?.statusBarIconBrightness, Brightness.dark);
    expect(style?.statusBarBrightness, Brightness.light);
  });

  testWidgets('dark theme: icons stay light once the header scrolls away', (
    tester,
  ) async {
    await _pumpLogin(tester, brightness: Brightness.dark, height: 300);
    await _scrollHeaderAway(tester);

    final style = _statusBarStyle(tester);
    expect(style?.statusBarIconBrightness, Brightness.light);
    expect(style?.statusBarBrightness, Brightness.dark);
  });

  testWidgets('leaves the navigation bar colors to the system', (tester) async {
    await _pumpLogin(tester, brightness: Brightness.light, height: 800);

    final style = _statusBarStyle(tester);
    expect(style, isNotNull);
    expect(style?.systemNavigationBarColor, isNull);
    expect(style?.systemNavigationBarIconBrightness, isNull);
  });
}
