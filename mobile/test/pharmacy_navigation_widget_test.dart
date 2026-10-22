import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/pharmacy_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_widget_test.dart' show tap;

Widget host(PharmacyStub repository, Brightness brightness) => ProviderScope(
  overrides: [
    pharmacyRepositoryProvider.overrideWithValue(repository),
    pharmacyAccessProvider.overrideWithValue(
      const PharmacyAccess(role: 'ADMIN', sessionIdentity: 'test-session'),
    ),
    pharmacyModeProvider.overrideWithValue(PharmacyMode.outpatient),
  ],
  child: MaterialApp(
    theme: ThemeData(useMaterial3: true, brightness: brightness),
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context)
          .copyWith(textScaler: const TextScaler.linear(2)),
      child: child!,
    ),
    home: const Scaffold(body: PharmacyPage()),
  ),
);

void main() {
  for (final brightness in Brightness.values) {
    testWidgets(
      'catalog rotation and keyboard retain reachable actions $brightness',
      (tester) async {
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = const Size(320, 720);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetViewInsets);
        final repository = PharmacyStub();
        await tester.pumpWidget(host(repository, brightness));
        await tester.pumpAndSettle();
        tester.view.physicalSize = const Size(720, 320);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.enterText(
          find.widgetWithText(TextField, 'Tên thuốc'),
          'Paracetamol',
        );
        tester.view.viewInsets = const FakeViewPadding(bottom: 150);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.testTextInput.receiveAction(TextInputAction.search);
        await tester.pumpAndSettle();
        expect(repository.searches.last.keyword, 'Paracetamol');
        tester.view.resetViewInsets();
        tester.view.physicalSize = const Size(320, 720);
        await tester.pumpAndSettle();
        expect(find.text('Kết quả cho: Paracetamol'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
    testWidgets(
      'back from create discards unsubmitted draft without mutation $brightness',
      (tester) async {
        final repository = PharmacyStub();
        await tester.pumpWidget(host(repository, brightness));
        await tester.pumpAndSettle();
        await tap(tester, 'Thêm thuốc');
        await tester.enterText(
          find.widgetWithText(TextField, 'Tên thuốc'),
          'Draft discarded',
        );
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(find.text('Danh mục thuốc'), findsOneWidget);
        expect(repository.creations, isEmpty);
        await tap(tester, 'Thêm thuốc');
        final field = tester.widget<TextField>(
          find.widgetWithText(TextField, 'Tên thuốc'),
        );
        expect(field.controller!.text, isEmpty);
        expect(repository.creations, isEmpty);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
