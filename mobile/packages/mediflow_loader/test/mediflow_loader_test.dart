import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

void main() {
  testWidgets('renders the loader and optional label', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: MediFlowLoader(
            size: 72,
            label: 'Loading patient data...',
          ),
        ),
      ),
    );

    expect(find.byType(CustomPaint), findsOneWidget);
    expect(find.text('Loading patient data...'), findsOneWidget);
  });
}
