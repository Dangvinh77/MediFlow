import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';
import 'package:mediflow_mobile/features/report/presentation/outpatient_statistics.dart';

const period = AvailableReportPeriod(
  coverageId: 'proof',
  from: '2025-01-01',
  to: '2025-12-31',
  currency: 'VND',
  zoneId: 'Asia/Bangkok',
  sourceObservedAt: '2026-01-01T00:00:00Z',
  reconciledAt: '2026-01-01T01:00:00Z',
  syntheticData: true,
);

class Reports implements ReportRepository {
  int calls = 0;
  final pending = Completer<List<AvailableReportPeriod>>();
  @override
  Future<List<AvailableReportPeriod>> availablePeriods({String? departmentId}) {
    calls++;
    return pending.future;
  }

  @override
  Future<ReportData> read(ReportQuery query) => throw UnimplementedError();

  @override
  Future<OperationsStatistics> statistics(ReportQuery query) =>
      throw UnimplementedError();
}

void main() {
  Widget discovery(
    Reports repo,
    ValueChanged<AvailableReportPeriod> select, {
    bool manager = false,
  }) => MaterialApp(
    home: Scaffold(
      body: AvailableReportPeriods(
        repository: repo,
        departmentId: null,
        monthly: true,
        needsDepartment: manager,
        onSelect: select,
      ),
    ),
  );

  testWidgets(
    'discovery is explicit and selection applies the labelled synthetic range',
    (tester) async {
      final repo = Reports();
      AvailableReportPeriod? selected;
      await tester.pumpWidget(discovery(repo, (value) => selected = value));
      expect(repo.calls, 0);
      await tester.tap(find.text('Tìm kỳ có dữ liệu'));
      await tester.pump();
      repo.pending.complete([period]);
      await tester.pumpAndSettle();
      expect(find.text('Dữ liệu mô phỏng'), findsOneWidget);
      expect(selected, isNull);
      await tester.tap(find.text('Dùng kỳ này'));
      expect(selected, same(period));
    },
  );

  testWidgets(
    'manager must select department and obsolete discovery cannot commit',
    (tester) async {
      final repo = Reports();
      await tester.pumpWidget(
        discovery(repo, (_) => fail('obsolete selection'), manager: true),
      );
      await tester.tap(find.text('Tìm kỳ có dữ liệu'));
      expect(repo.calls, 0);
      await tester.pumpWidget(
        discovery(repo, (_) => fail('obsolete selection')),
      );
      await tester.tap(find.text('Tìm kỳ có dữ liệu'));
      await tester.pump();
      await tester.pumpWidget(const MaterialApp(home: Text('New scope')));
      repo.pending.complete([period]);
      await tester.pumpAndSettle();
      expect(find.text('Dùng kỳ này'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'separate cash and activity charts render negative net and accepted zero',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: OutpatientStatistics(
                ReportData('Ngoại trú', [
                  ReportRow('2025-01-01', {
                    'Tiền thu': 0,
                    'Hoàn tiền': 20,
                    'Thu ròng': -20,
                    'Lượt khám hoàn tất': 0,
                    'Xét nghiệm': 0,
                    'Đơn đã cấp': 0,
                    'Đơn vị thuốc đã cấp': 0,
                  }),
                  ReportRow('2025-01-02', {
                    'Tiền thu': 100,
                    'Hoàn tiền': 0,
                    'Thu ròng': 100,
                    'Lượt khám hoàn tất': 2,
                    'Xét nghiệm': 1,
                    'Đơn đã cấp': 1,
                    'Đơn vị thuốc đã cấp': 4,
                  }),
                ], currency: 'VND'),
              ),
            ),
          ),
        ),
      );
      expect(find.text('Dòng tiền ngoại trú (VND)'), findsOneWidget);
      expect(find.text('Tổng kỳ đã chọn'), findsOneWidget);
      expect(find.text('80 VND'), findsOneWidget);
      expect(find.text('4'), findsOneWidget);
      expect(
        find.text('Hoạt động ngoại trú (lượt / xét nghiệm / đơn)'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );
}
