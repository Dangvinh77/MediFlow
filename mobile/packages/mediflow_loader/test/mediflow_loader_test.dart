import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

Widget _host(Widget child, {ThemeData? theme, bool reduceMotion = false}) =>
    MaterialApp(
      theme: theme,
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: reduceMotion),
        child: Scaffold(body: Center(child: child)),
      ),
    );

CustomPainter _painter(WidgetTester tester) => tester
    .widget<CustomPaint>(
      find.descendant(
        of: find.byType(MediFlowLoader),
        matching: find.byType(CustomPaint),
      ),
    )
    .painter!;

void main() {
  testWidgets('renders the loader and optional label', (tester) async {
    await tester.pumpWidget(
      _host(const MediFlowLoader(size: 72, label: 'Loading patient data...')),
    );

    expect(
      find.descendant(
        of: find.byType(MediFlowLoader),
        matching: find.byType(CustomPaint),
      ),
      findsOneWidget,
    );
    expect(find.text('Loading patient data...'), findsOneWidget);
  });

  testWidgets('announces one label, with a Vietnamese default', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_host(const MediFlowLoader()));
    expect(find.bySemanticsLabel('Đang tải dữ liệu'), findsOneWidget);

    await tester.pumpWidget(
      _host(
          const MediFlowLoader(label: 'Hiện', semanticLabel: 'Đang tải hồ sơ')),
    );
    expect(find.bySemanticsLabel('Đang tải hồ sơ'), findsOneWidget);
    expect(find.bySemanticsLabel('Hiện'), findsNothing);
    semantics.dispose();
  });

  testWidgets('keeps animating, also for reduced motion', (tester) async {
    for (final reduceMotion in [false, true]) {
      await tester.pumpWidget(
        _host(const MediFlowLoader(), reduceMotion: reduceMotion),
      );
      final before = _painter(tester);
      await tester.pump(const Duration(milliseconds: 540));
      final after = _painter(tester);
      expect(after.shouldRepaint(before), isTrue, reason: '$reduceMotion');
      await tester.pumpWidget(const SizedBox());
    }
  });

  testWidgets('paints every moment of the beat at several sizes',
      (tester) async {
    for (final theme in [ThemeData.light(), ThemeData.dark()]) {
      await tester.pumpWidget(
        _host(const MediFlowLoader(size: 36), theme: theme),
      );
      for (var i = 0; i < 16; i++) {
        await tester.pump(const Duration(milliseconds: 100));
        expect(tester.takeException(), isNull);
      }
      await tester.pumpWidget(const SizedBox());
    }
  });

  testWidgets('falls back on invalid size and speed', (tester) async {
    await tester.pumpWidget(_host(const MediFlowLoader(size: -1, speed: 0)));
    expect(
      tester.getSize(
        find
            .descendant(
              of: find.byType(MediFlowLoader),
              matching: find.byType(SizedBox),
            )
            .first,
      ),
      const Size.square(64),
    );
  });

  testWidgets('MediFlowMark paints at its size', (tester) async {
    await tester.pumpWidget(_host(const MediFlowMark(size: 38)));
    expect(tester.getSize(find.byType(MediFlowMark)), const Size.square(38));
  });
}
