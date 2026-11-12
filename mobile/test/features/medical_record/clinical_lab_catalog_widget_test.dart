import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';

import '../../clinical_completion_workflow_test.dart'
    as workflow
    show
        MedicalRecordFake,
        appointmentId,
        departmentId,
        doctorId,
        host,
        patientId,
        recordId,
        sourceOrderId;

class _CatalogRecordFake extends workflow.MedicalRecordFake
    implements ClinicalLabCatalogRepository {
  _CatalogRecordFake(this.items, {this.onCatalog});

  final List<ClinicalLabCatalogItem>? items;
  final Future<List<ClinicalLabCatalogItem>?> Function(String? group)?
  onCatalog;

  @override
  Future<List<ClinicalLabCatalogItem>?> labCatalog({String? group}) async {
    return onCatalog?.call(group) ?? items;
  }

  @override
  Future<ClinicalLabOrder> createClinicalLabOrder(
    String id,
    ClinicalLabOrderDraft draft,
  ) async {
    sources.add((id, draft));
    return ClinicalLabOrder(
      sourceOrderId: workflow.sourceOrderId,
      recordId: id,
      patientId: workflow.patientId,
      requestingDoctorId: workflow.doctorId,
      requestingDepartmentId: workflow.departmentId,
      careEpisodeType: 'OUTPATIENT_VISIT',
      careEpisodeId: workflow.appointmentId,
      labType: draft.labType,
      requestedDate: draft.requestedDate,
      priceCode: draft.priceCode,
      orderedAt: '2026-10-22T09:00:00Z',
      serviceCode: draft.serviceCode,
    );
  }
}

const _completeBloodCount = ClinicalLabCatalogItem(
  serviceCode: 'CBC001',
  name: 'Công thức máu toàn bộ',
  displayGroup: 'HEMATOLOGY',
  specimenType: 'Máu toàn phần',
  preparation: 'Nhịn ăn 8 giờ',
  turnaroundHours: 4,
  priceCode: 'CBC-APPROVED',
  templateCode: 'CBC_V1',
  panel: true,
);

const _glucose = ClinicalLabCatalogItem(
  serviceCode: 'GLU001',
  name: 'Đường huyết',
  displayGroup: 'BIOCHEMISTRY',
  specimenType: 'Huyết thanh',
  preparation: null,
  turnaroundHours: null,
  priceCode: 'GLU-APPROVED',
  templateCode: 'GLU_V1',
  panel: false,
);

void main() {
  testWidgets('catalogue choice creates a locked order with its serviceCode', (
    tester,
  ) async {
    final catalogResponse = Completer<List<ClinicalLabCatalogItem>?>();
    final records = _CatalogRecordFake(
      null,
      onCatalog: (_) => catalogResponse.future,
    );
    await tester.pumpWidget(
      workflow.host(
        child: const MedicalRecordDetailPage(recordId: workflow.recordId),
        records: records,
        outpatientEnabled: true,
      ),
    );
    await tester.pump();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-submit')),
      400,
      scrollable: find.byType(Scrollable).first,
    );

    expect(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('clinical-lab-submit')), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('clinical-lab-submit')),
          )
          .onPressed,
      isNull,
    );

    catalogResponse.complete([_completeBloodCount]);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-catalog-service-HEMATOLOGY')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(
      find.byKey(const ValueKey('clinical-lab-catalog-service-HEMATOLOGY')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text(_completeBloodCount.name).last);
    await tester.pumpAndSettle();

    expect(find.textContaining('Máu toàn phần'), findsOneWidget);
    expect(
      find.text('Dịch vụ bảng được gửi trong một đơn xét nghiệm.'),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-submit')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('clinical-lab-submit')));
    await tester.pumpAndSettle();

    expect(records.sources, hasLength(1));
    expect(records.sources.single.$2.labType, _completeBloodCount.name);
    expect(records.sources.single.$2.priceCode, _completeBloodCount.priceCode);
    expect(
      records.sources.single.$2.serviceCode,
      _completeBloodCount.serviceCode,
    );
    expect(records.submissions, hasLength(1));
    expect(
      records.submissions.single.serviceCode,
      _completeBloodCount.serviceCode,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('changing catalogue group clears the previous service choice', (
    tester,
  ) async {
    final records = _CatalogRecordFake([_completeBloodCount, _glucose]);
    await tester.pumpWidget(
      workflow.host(
        child: const MedicalRecordDetailPage(recordId: workflow.recordId),
        records: records,
        outpatientEnabled: true,
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-catalog-group')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await Scrollable.ensureVisible(
      tester.element(find.byKey(const ValueKey('clinical-lab-catalog-group'))),
      alignment: 0.5,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('clinical-lab-catalog-group')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Huyết học').last);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-catalog-service-HEMATOLOGY')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(
      find.byKey(const ValueKey('clinical-lab-catalog-service-HEMATOLOGY')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text(_completeBloodCount.name).last);
    await tester.pumpAndSettle();
    expect(find.text(_completeBloodCount.name), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-catalog-group')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await Scrollable.ensureVisible(
      tester.element(find.byKey(const ValueKey('clinical-lab-catalog-group'))),
      alignment: 0.5,
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('clinical-lab-catalog-group')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sinh hóa').last);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('clinical-lab-catalog-service-BIOCHEMISTRY')),
      findsOneWidget,
    );
    expect(find.text(_completeBloodCount.name), findsNothing);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-catalog-service-BIOCHEMISTRY')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(
      find.byKey(const ValueKey('clinical-lab-catalog-service-BIOCHEMISTRY')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text(_glucose.name).last);
    await tester.pumpAndSettle();
    expect(find.text('Chuẩn bị: Chưa có thông tin'), findsOneWidget);
    expect(
      find.text('Thời gian trả kết quả: Chưa có thông tin'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty catalogue prevents manual ordering', (tester) async {
    final records = _CatalogRecordFake([]);
    await tester.pumpWidget(
      workflow.host(
        child: const MedicalRecordDetailPage(recordId: workflow.recordId),
        records: records,
        outpatientEnabled: true,
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-submit')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Danh mục chưa có dịch vụ xét nghiệm để chọn.'),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      findsNothing,
    );
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('clinical-lab-submit')),
          )
          .onPressed,
      isNull,
    );
  });

  testWidgets('disabled catalogue keeps the legacy order form', (tester) async {
    final records = _CatalogRecordFake(null);
    await tester.pumpWidget(
      workflow.host(
        child: const MedicalRecordDetailPage(recordId: workflow.recordId),
        records: records,
        outpatientEnabled: true,
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-manual-price')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-catalog-group')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-catalog-service-HEMATOLOGY')),
      findsNothing,
    );
  });

  testWidgets('catalogue failure is shown and leaves manual entry usable', (
    tester,
  ) async {
    final records = _CatalogRecordFake(
      null,
      onCatalog: (_) async =>
          throw const ApiFailure('Catalogue unavailable', status: 503),
    );
    await tester.pumpWidget(
      workflow.host(
        child: const MedicalRecordDetailPage(recordId: workflow.recordId),
        records: records,
        outpatientEnabled: true,
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Catalogue unavailable'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('clinical-lab-manual-type')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('clinical-lab-manual-price')),
      findsOneWidget,
    );
  });
}
