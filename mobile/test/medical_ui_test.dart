import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/core/theme/app_theme.dart';
import 'package:mediflow_mobile/core/theme/medical_colors.dart';
import 'package:mediflow_mobile/core/widgets/medical_ui.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'session_controller_test.dart' show MemoryAuth;

double _contrast(Color a, Color b) {
  final hi = a.computeLuminance() > b.computeLuminance() ? a : b;
  final lo = identical(hi, a) ? b : a;
  return (hi.computeLuminance() + 0.05) / (lo.computeLuminance() + 0.05);
}

Future<void> _pumpShell(WidgetTester tester, Size size) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final auth = MemoryAuth()
    ..saved = const Session(
      accessToken: 'a',
      refreshToken: 'r',
      role: UserRole.admin,
    );
  final container = ProviderContainer(
    overrides: [authRepositoryProvider.overrideWithValue(auth)],
  );
  addTearDown(container.dispose);
  await container.read(sessionProvider.future);
  await tester.pumpWidget(
    UncontrolledProviderScope(container: container, child: const MediFlowApp()),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('statusToneOf', () {
    test('reads the triage tone from backend status codes', () {
      expect(statusToneOf('CANCELLED'), StatusTone.danger);
      expect(statusToneOf('DISPENSE_FAILED'), StatusTone.danger);
      expect(statusToneOf('COMPLETED'), StatusTone.success);
      expect(statusToneOf('AWAITING_PAYMENT'), StatusTone.warning);
      expect(statusToneOf('READY'), StatusTone.info);
      expect(statusToneOf('IN_EXAM'), StatusTone.info);
    });

    test('UNPAID is never read as paid', () {
      expect(statusToneOf('UNPAID'), StatusTone.warning);
      expect(statusToneOf('PAID'), StatusTone.success);
    });

    test('an unknown code stays neutral', () {
      expect(statusToneOf('SOMETHING_NEW'), StatusTone.neutral);
    });
  });

  group('bottomBarModules', () {
    for (final role in UserRole.values) {
      test('${role.wire} gets at most three allowed modules', () {
        final visible = visibleMainModules(role, schedulingEnabled: true);
        final tabs = bottomBarModules(visible);
        expect(tabs.length, lessThanOrEqualTo(3));
        expect(tabs.every(visible.contains), isTrue);
        expect(tabs, isNotEmpty);
      });
    }

    test('puts the clinical queue before administration', () {
      final tabs = bottomBarModules(visibleMainModules(UserRole.nurse));
      expect(tabs.first.path, '/appointments');
      expect(tabs.map((tab) => tab.path), isNot(contains('/organization')));
    });
  });

  group('colour contrast', () {
    for (final brightness in Brightness.values) {
      test('$brightness status and wayfinding colours stay readable', () {
        final theme = buildAppTheme(brightness);
        final medical = theme.extension<MedicalColors>()!;
        final surface = theme.colorScheme.surface;
        for (final color in [
          medical.success,
          medical.warning,
          medical.danger,
          medical.info,
          medical.mutedText,
        ]) {
          expect(_contrast(color, surface), greaterThanOrEqualTo(4.5));
        }
        for (final module in modules) {
          expect(
            _contrast(medical.accent(module.accent), surface),
            greaterThanOrEqualTo(3),
            reason: module.path,
          );
        }
      });
    }
  });

  testWidgets('a widget under a plain theme still resolves status colours', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: StatusPill('Đã hủy', tone: StatusTone.danger)),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('Đã hủy'), findsOneWidget);
  });

  testWidgets('pulse line holds still when reduced motion is requested', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MediaQuery(
        data: MediaQueryData(disableAnimations: true),
        child: Directionality(
          textDirection: TextDirection.ltr,
          child: PulseLine(),
        ),
      ),
    );
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('bottom bar menu opens the full module list', (tester) async {
    await _pumpShell(tester, const Size(390, 844));
    expect(find.byType(NavigationBar), findsOneWidget);
    await tester.tap(find.text('Thêm'));
    await tester.pumpAndSettle();
    expect(find.byType(Drawer), findsOneWidget);
    expect(find.text('Đổi mật khẩu'), findsOneWidget);
  });

  testWidgets('bottom bar gives way on a landscape phone', (tester) async {
    await _pumpShell(tester, const Size(800, 390));
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('bottom bar text size stays fixed on a 320px phone', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearAllTestValues);
    await _pumpShell(tester, const Size(320, 640));
    expect(tester.takeException(), isNull);
    final label = tester.element(find.text('Tổng quan').last);
    expect(MediaQuery.textScalerOf(label).scale(10), 10);
  });
}
