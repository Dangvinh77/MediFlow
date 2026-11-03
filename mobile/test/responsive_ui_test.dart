import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/core/widgets/request_state.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';

import 'reference_picker_test_support.dart';
import 'session_controller_test.dart' show MemoryAuth;

void main() {
  testWidgets(
    'error state remains scrollable on a short screen with large text',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 240));
      addTearDown(() {
        tester.view.reset();
        return tester.binding.setSurfaceSize(null);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(2)),
              child: RequestError(
                const ApiFailure(
                  'Không kết nối được Gateway. Kiểm tra mạng và địa chỉ máy chủ.',
                  correlationId: 'support-reference',
                ),
                onRetry: () {},
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Thử lại'));
      expect(find.text('Thử lại').hitTestable(), findsOneWidget);
    },
  );

  testWidgets(
    'reference picker fits a narrow screen with keyboard and large text',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 568));
      addTearDown(() {
        tester.view.reset();
        return tester.binding.setSurfaceSize(null);
      });
      final refs = ReferencePickerFake({
        ReferenceKind.patient: [
          const ReferenceOption(id: 'id', name: 'Nguyễn An'),
        ],
      });
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            referenceReaderProvider.overrideWithValue(refs),
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(sessionIdentity: Object(), role: 'ADMIN'),
            ),
          ],
          child: MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: const TextScaler.linear(1.5),
                viewInsets: const EdgeInsets.only(bottom: 220),
              ),
              child: child!,
            ),
            home: Scaffold(
              body: ReferencePicker(
                kind: ReferenceKind.patient,
                label: 'Bệnh nhân',
                value: null,
                onChanged: (_) {},
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.byTooltip('Chọn Bệnh nhân'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Nguyễn An'));
      await tester.tap(find.text('Nguyễn An'));
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
    },
  );

  testWidgets('login remains usable in landscape with the keyboard visible', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(568, 320));
    addTearDown(() {
      tester.view.reset();
      return tester.binding.setSurfaceSize(null);
    });
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authRepositoryProvider.overrideWithValue(MemoryAuth())],
        child: const MediFlowApp(),
      ),
    );
    await tester.pumpAndSettle();
    tester.view.viewInsets = const FakeViewPadding(bottom: 160);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Quên mật khẩu?'));
    expect(tester.takeException(), isNull);
    expect(find.text('Quên mật khẩu?').hitTestable(), findsOneWidget);
  });
}
