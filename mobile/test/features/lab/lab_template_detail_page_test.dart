import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_catalog.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_detail_page.dart';

import '../../pharmacy_fixtures.dart';
import '../../reference_picker_test_support.dart';

class TemplateWorkflow implements LabWorkflow, LabCatalogReader {
  bool failNextDetail = false;
  bool hasTemplate = true;

  @override
  Future<LabDetail> detail(String id) async {
    if (failNextDetail) {
      failNextDetail = false;
      throw const ApiFailure('Không thể tải trạng thái xét nghiệm.');
    }
    return LabDetail(
      id: id,
      patientId: otherDrugId,
      recordId: otherDrugId,
      departmentId: otherDrugId,
      type: 'Công thức máu',
      requestedDate: '2026-10-22',
      status: 'IN_PROGRESS',
      paid: true,
      version: 2,
    );
  }

  @override
  Future<LabCatalog> catalog({String? group}) async =>
      const LabCatalog(enabled: true);

  @override
  Future<LabTestTemplate> template(String testId) async => LabTestTemplate(
    testId: testId,
    serviceCode: hasTemplate ? 'CBC' : null,
    template: hasTemplate
        ? const LabResultTemplate(
            templateCode: 'CBC_V1',
            version: 1,
            name: 'Công thức máu',
            fields: [
              LabTemplateField(
                fieldCode: 'WBC',
                label: 'Bạch cầu',
                position: 1,
                required: true,
                valueType: 'QUANTITATIVE',
                unit: 'G/L',
                allowedOptions: null,
                allowsOperator: false,
                ranges: [],
              ),
            ],
          )
        : null,
  );

  @override
  Future<LabDetail> start(String id) => throw UnimplementedError();

  @override
  Future<LabDetail> results(
    String id,
    List<LabResultDraft> results,
    String? conclusion,
    String date,
  ) => throw UnimplementedError();

  @override
  Future<LabDetail> cancel(String id, String reason) =>
      throw UnimplementedError();
}

void main() {
  final references = ReferencePickerFake({
    for (final kind in ReferenceKind.values)
      kind: [const ReferenceOption(id: otherDrugId, name: 'Nguyễn An')],
  });

  Widget host(TemplateWorkflow workflow) => ProviderScope(
    overrides: [
      workspaceAccessProvider.overrideWithValue(
        WorkspaceAccess(
          sessionIdentity: 'session',
          role: 'ADMIN',
          staffId: otherDrugId,
        ),
      ),
      referenceReaderProvider.overrideWithValue(references),
      labWorkflowProvider.overrideWithValue(workflow),
    ],
    child: const MaterialApp(home: LabDetailPage(id: drugId)),
  );

  testWidgets('null template keeps the legacy free-row result form', (
    tester,
  ) async {
    final workflow = TemplateWorkflow();
    workflow.hasTemplate = false;
    await tester.pumpWidget(host(workflow));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Thêm chỉ số'),
      300,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.text('Thêm chỉ số'), findsOneWidget);
    expect(find.byKey(const ValueKey('value-WBC')), findsNothing);
  });

  testWidgets('failed detail refresh preserves a structured result draft', (
    tester,
  ) async {
    final workflow = TemplateWorkflow();
    await tester.pumpWidget(host(workflow));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('value-WBC')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.enterText(find.byKey(const ValueKey('value-WBC')), '7');

    workflow.failNextDetail = true;
    await tester.scrollUntilVisible(
      find.text('Tải lại trạng thái'),
      -300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Tải lại trạng thái'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('value-WBC')),
      300,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.byKey(const ValueKey('value-WBC')), findsOneWidget);
    expect(
      tester
          .widget<TextField>(find.byKey(const ValueKey('value-WBC')))
          .controller!
          .text,
      '7',
    );
    await tester.scrollUntilVisible(
      find.text('Không thể tải trạng thái xét nghiệm.'),
      -300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Không thể tải trạng thái xét nghiệm.'), findsOneWidget);
  });
}
