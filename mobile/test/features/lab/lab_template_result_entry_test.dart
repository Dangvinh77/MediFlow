import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_catalog.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_template_result_entry.dart';

LabResultTemplate testTemplate({
  bool allowsOperator = false,
  String valueType = 'QUANTITATIVE',
  List<String>? allowedOptions,
  bool required = true,
}) => LabResultTemplate(
  templateCode: 'CBC_V1',
  version: 1,
  name: 'Công thức máu',
  fields: [
    LabTemplateField(
      fieldCode: 'WBC',
      label: 'Bạch cầu',
      position: 1,
      required: required,
      valueType: valueType,
      unit: 'G/L',
      allowedOptions: allowedOptions,
      allowsOperator: allowsOperator,
      ranges: const [LabReferenceRange(low: '4', high: '10', reviewed: false)],
    ),
  ],
);

void main() {
  testWidgets('operator selector appears only when field allows it', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: LabTemplateResultEntry(template: testTemplate())),
      ),
    );
    expect(find.byKey(const ValueKey('operator-WBC')), findsNothing);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(
            template: testTemplate(allowsOperator: true),
          ),
        ),
      ),
    );
    expect(find.byKey(const ValueKey('operator-WBC')), findsOneWidget);
  });

  testWidgets('operator is only prefixed after a selection', (tester) async {
    final key = GlobalKey<LabTemplateResultEntryState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(
            template: testTemplate(allowsOperator: true),
            key: key,
          ),
        ),
      ),
    );
    await tester.enterText(find.byKey(const ValueKey('value-WBC')), '7');
    expect(key.currentState!.buildResults()!.single.value, '7');

    await tester.tap(find.byKey(const ValueKey('operator-WBC')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('<=').last);
    await tester.pumpAndSettle();

    expect(key.currentState!.buildResults()!.single.value, '<=7');
  });

  testWidgets('combined operator and result obey the legacy value limit', (
    tester,
  ) async {
    final key = GlobalKey<LabTemplateResultEntryState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(
            template: testTemplate(allowsOperator: true),
            key: key,
          ),
        ),
      ),
    );
    await tester.enterText(find.byKey(const ValueKey('value-WBC')), '1' * 50);
    await tester.tap(find.byKey(const ValueKey('operator-WBC')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('<=').last);
    await tester.pumpAndSettle();

    expect(key.currentState!.buildResults(), isNull);
    await tester.pump();
    expect(find.text('Giá trị hoặc lý do vượt quá 50 ký tự.'), findsOneWidget);
  });

  testWidgets('qualitative field with options renders a selection control', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(
            template: testTemplate(
              valueType: 'QUALITATIVE',
              allowedOptions: const ['Âm tính', 'Dương tính'],
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const ValueKey('value-WBC')), findsOneWidget);
    expect(find.byType(DropdownButtonFormField<String>), findsOneWidget);
  });

  testWidgets('qualitative value can be cleared before entering a reason', (
    tester,
  ) async {
    final key = GlobalKey<LabTemplateResultEntryState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(
            template: testTemplate(
              valueType: 'QUALITATIVE',
              allowedOptions: const ['Âm tính', 'Dương tính'],
            ),
            key: key,
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('value-WBC')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Âm tính').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('value-WBC')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chọn kết quả').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('reason-WBC')), 'Mẫu vỡ');

    expect(
      key.currentState!.buildResults()!.single.value,
      'Không có kết quả: Mẫu vỡ',
    );
  });

  testWidgets('reason maps to the legacy no-result value', (tester) async {
    final key = GlobalKey<LabTemplateResultEntryState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(template: testTemplate(), key: key),
        ),
      ),
    );

    await tester.enterText(find.byKey(const ValueKey('reason-WBC')), 'Mẫu vỡ');
    final results = key.currentState!.buildResults();

    expect(results, isNotNull);
    expect(results!.single.indicator, 'Bạch cầu');
    expect(results.single.value, startsWith('Không có kết quả: Mẫu vỡ'));
    expect(results.single.unit, 'G/L');
    expect(results.single.referenceRange, '4–10');
  });

  testWidgets('missing required result shows validation and blocks mapping', (
    tester,
  ) async {
    final key = GlobalKey<LabTemplateResultEntryState>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LabTemplateResultEntry(template: testTemplate(), key: key),
        ),
      ),
    );

    expect(key.currentState!.buildResults(), isNull);
    await tester.pump();
    expect(
      find.text('Nhập giá trị hoặc lý do không có kết quả.'),
      findsOneWidget,
    );
  });

  testWidgets('unreviewed reference range shows demo warning', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: LabTemplateResultEntry(template: testTemplate())),
      ),
    );

    expect(
      find.text('Khoảng tham chiếu demo, chưa được duyệt lâm sàng'),
      findsOneWidget,
    );
  });
}
