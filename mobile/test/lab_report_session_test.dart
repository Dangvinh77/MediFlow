import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_detail_page.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';
import 'package:mediflow_mobile/features/report/presentation/report_page.dart';

import 'pharmacy_fixtures.dart';
import 'reference_picker_test_support.dart';

LabDetail labDetail(String status) => LabDetail(
  id: drugId,
  patientId: otherDrugId,
  recordId: otherDrugId,
  departmentId: otherDrugId,
  type: 'Công thức máu',
  requestedDate: '2026-10-22',
  status: status,
  paid: false,
  version: 1,
);

class Workflow implements LabWorkflow {
  Future<LabDetail> Function()? read;
  bool committed = false;
  int starts = 0;
  @override
  Future<LabDetail> detail(String id) =>
      read?.call() ??
      Future.value(labDetail(committed ? 'COMPLETED' : 'READY'));
  @override
  Future<LabDetail> start(String id) async {
    starts++;
    committed = true;
    throw const ApiFailure('Mất phản hồi');
  }

  @override
  Future<LabDetail> results(
    String id,
    List<LabResultDraft> results,
    String? conclusion,
    String date,
  ) async => throw UnimplementedError();
  @override
  Future<LabDetail> cancel(String id, String reason, String actorId) async =>
      throw UnimplementedError();
}

class Reports implements ReportRepository {
  final pending = Completer<ReportData>();
  @override
  Future<ReportData> read(ReportQuery query) => pending.future;
}

void main() {
  final refs = ReferencePickerFake({
    for (final kind in ReferenceKind.values)
      kind: [const ReferenceOption(id: otherDrugId, name: 'Nguyễn An')],
  });
  Widget host(
    Object identity,
    Widget child, {
    Workflow? workflow,
    Reports? reports,
    String role = 'ADMIN',
  }) => ProviderScope(
    key: ValueKey(identity),
    overrides: [
      workspaceAccessProvider.overrideWithValue(
        WorkspaceAccess(
          sessionIdentity: identity,
          role: role,
          staffId: otherDrugId,
        ),
      ),
      referenceReaderProvider.overrideWithValue(refs),
      if (workflow != null) labWorkflowProvider.overrideWithValue(workflow),
      if (reports != null) reportRepositoryProvider.overrideWithValue(reports),
    ],
    child: MaterialApp(home: Scaffold(body: child)),
  );

  testWidgets(
    'lost Lab command response locks actions until detail reconciliation',
    (tester) async {
      final workflow = Workflow();
      await tester.pumpWidget(
        host('a', const LabDetailPage(id: drugId), workflow: workflow),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Bắt đầu thực hiện'));
      await tester.tap(find.text('Bắt đầu thực hiện'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xác nhận'));
      await tester.pumpAndSettle();
      expect(workflow.starts, 1);
      expect(find.textContaining('Chưa xác định kết quả'), findsOneWidget);
      await tester.ensureVisible(find.text('Tải lại trạng thái'));
      await tester.tap(find.text('Tải lại trạng thái'));
      await tester.pumpAndSettle();
      expect(find.text('COMPLETED'), findsOneWidget);
      expect(find.text('Bắt đầu thực hiện'), findsNothing);
    },
  );
  testWidgets(
    'deferred Lab refresh locks mutations until the fresh read completes',
    (tester) async {
      final workflow = Workflow();
      await tester.pumpWidget(
        host('a', const LabDetailPage(id: drugId), workflow: workflow),
      );
      await tester.pumpAndSettle();

      final pending = Completer<LabDetail>();
      workflow.read = () => pending.future;
      await tester.ensureVisible(find.text('Tải lại trạng thái'));
      await tester.tap(find.text('Tải lại trạng thái'));
      await tester.pump();

      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Bắt đầu thực hiện'),
            )
            .onPressed,
        isNull,
      );
      expect(
        tester
            .widget<OutlinedButton>(
              find.widgetWithText(OutlinedButton, 'Tải lại trạng thái'),
            )
            .onPressed,
        isNull,
      );
      expect(workflow.starts, 0);

      pending.complete(labDetail('READY'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Bắt đầu thực hiện'),
            )
            .onPressed,
        isNotNull,
      );
    },
  );
  testWidgets(
    'pending Lab private response cannot appear after session replacement',
    (tester) async {
      final pending = Completer<LabDetail>();
      final old = Workflow()..read = () => pending.future;
      await tester.pumpWidget(
        host('old', const LabDetailPage(id: drugId), workflow: old),
      );
      await tester.pump();
      await tester.pumpWidget(host('new', const Text('Phiên mới')));
      await tester.pumpAndSettle();
      pending.complete(labDetail('READY'));
      await tester.pumpAndSettle();
      expect(find.text('Công thức máu'), findsNothing);
      expect(find.text('Phiên mới'), findsOneWidget);
    },
  );
  testWidgets(
    'pending report response cannot appear after session replacement',
    (tester) async {
      final reports = Reports();
      await tester.pumpWidget(
        host('old', const ReportPage(), reports: reports),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xem báo cáo'));
      await tester.pump();
      await tester.pumpWidget(host('new', const Text('Phiên mới')));
      await tester.pumpAndSettle();
      reports.pending.complete(const ReportData('Dữ liệu phiên cũ', []));
      await tester.pumpAndSettle();
      expect(find.text('Dữ liệu phiên cũ'), findsNothing);
    },
  );
  testWidgets('empty unauthorized reference picker explains denied lookup', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(
        'patient',
        ReferencePicker(
          kind: ReferenceKind.patient,
          label: 'Bệnh nhân',
          value: null,
          onChanged: (_) {},
        ),
        role: 'PATIENT',
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Không có quyền tra cứu tên'), findsOneWidget);
    expect(find.text('Chọn theo tên'), findsNothing);
    expect(
      tester
          .widget<IconButton>(
            find.byWidgetPredicate(
              (w) => w is IconButton && w.tooltip == 'Chọn Bệnh nhân',
            ),
          )
          .onPressed,
      isNull,
    );
  });
}
