import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';
import 'package:mediflow_mobile/features/report/presentation/report_page.dart';

import 'pharmacy_fixtures.dart';
import 'reference_picker_test_support.dart';

const _day = OperationsFigures(
  label: '2026-11-05',
  completedVisits: 3,
  examRevenue: 100000,
  labRevenue: 36000,
  prescriptionRevenue: 30000,
  unclassifiedRevenue: 0,
  refunds: 10000,
);

class _Reports implements ReportRepository {
  int readCalls = 0;
  final operations = const OperationsStatistics(
    from: '2026-11-01',
    to: '2026-11-05',
    currency: 'VND',
    totals: _day,
    days: [_day],
    departments: [],
  );
  final reconciled = const ReportData(
    'Báo cáo ngoại trú',
    [
      ReportRow('2026-11-01 – 2026-11-02', {
        'Lượt khám hoàn tất': 3,
        'Xét nghiệm': 2,
        'Đơn đã cấp': 1,
        'Đơn vị thuốc đã cấp': 4,
        'Tiền thu': 100,
        'Hoàn tiền': 150,
        'Thu ròng': -50,
      }),
    ],
    currency: 'VND',
    departmentId: otherDrugId,
    note:
        'Dữ liệu mô phỏng\nĐã đối soát · VND · Asia/Bangkok\n'
        'Nguồn cập nhật: 2026-11-03T00:00:00Z\n'
        'Đối soát: 2026-11-03T01:00:00Z',
  );

  @override
  Future<List<AvailableReportPeriod>> availablePeriods({
    String? departmentId,
  }) async => [];

  @override
  Future<ReportData> read(ReportQuery query) async {
    readCalls++;
    return reconciled;
  }

  @override
  Future<OperationsStatistics> statistics(ReportQuery query) async =>
      operations;
}

Widget _host(_Reports reports, {double textScale = 1}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(
        sessionIdentity: 'report-test',
        role: 'ADMIN',
        staffId: otherDrugId,
      ),
    ),
    reportRepositoryProvider.overrideWithValue(reports),
    referenceReaderProvider.overrideWithValue(
      ReferencePickerFake({
        for (final kind in ReferenceKind.values)
          kind: [const ReferenceOption(id: otherDrugId, name: 'Khoa Nội')],
      }),
    ),
  ],
  child: MaterialApp(
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context)
          .copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: const Scaffold(body: ReportPage(outpatientEnabled: true)),
  ),
);

void main() {
  testWidgets('report selector fits a narrow screen with large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(_host(_Reports(), textScale: 1.45));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.byType(DropdownButtonFormField<ReportKind>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ngoại trú theo tháng').last);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Bộ lọc đối soát'), findsOneWidget);
  });

  testWidgets(
    'keeps provisional charts prominent on the initial report screen',
    (tester) async {
      await tester.pumpWidget(_host(_Reports()));
      await tester.pumpAndSettle();

      expect(
        find.text('Doanh thu theo ngày và loại dịch vụ (VND)'),
        findsOneWidget,
      );
      expect(find.text('Số lượt khám hoàn tất theo ngày'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'compacts reconciled filters, evidence and period details until requested',
    (tester) async {
      final reports = _Reports();
      await tester.pumpWidget(_host(reports));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(DropdownButtonFormField<ReportKind>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ngoại trú theo ngày').last);
      await tester.pumpAndSettle();

      expect(find.text('Bộ lọc đối soát'), findsOneWidget);
      expect(find.textContaining('Toàn viện'), findsOneWidget);
      expect(find.text('Từ ngày'), findsNothing);
      expect(find.text('Xem báo cáo'), findsOneWidget);

      await tester.ensureVisible(find.text('Xem báo cáo'));
      await tester.tap(find.text('Xem báo cáo'));
      await tester.pumpAndSettle();

      expect(reports.readCalls, 1);
      expect(find.text('Đã đối soát'), findsOneWidget);
      expect(find.text('Dữ liệu mô phỏng'), findsOneWidget);
      expect(find.textContaining('Nguồn cập nhật:'), findsNothing);
      expect(find.text('Dòng tiền ngoại trú (VND)'), findsNothing);
      expect(find.text('Xu hướng đã đối soát'), findsOneWidget);
      expect(
        find.text('3 lượt · 2 xét nghiệm · 1 đơn · Thu ròng: -50 VND'),
        findsOneWidget,
      );
      expect(find.text('Đơn vị thuốc đã cấp'), findsNothing);

      await tester.scrollUntilVisible(
        find.text('2026-11-01 – 2026-11-02'),
        300,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('2026-11-01 – 2026-11-02'));
      await tester.pumpAndSettle();
      expect(find.text('Đơn vị thuốc đã cấp'), findsOneWidget);
      expect(find.text('Tiền thu'), findsOneWidget);
      expect(find.text('Hoàn tiền'), findsOneWidget);
      expect(find.text('Thu ròng'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Xu hướng đã đối soát'), 300);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xu hướng đã đối soát'));
      await tester.pumpAndSettle();
      expect(find.text('Dòng tiền ngoại trú (VND)'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Nguồn và đối soát'), 300);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Nguồn và đối soát'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Nguồn cập nhật:'), findsOneWidget);
      expect(find.textContaining('Đối soát:'), findsOneWidget);
    },
  );
}
