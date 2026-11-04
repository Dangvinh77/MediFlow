import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/report/data/gateway_report_repository.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';
import 'package:mediflow_mobile/features/report/presentation/operations_statistics.dart';

import 'pharmacy_fixtures.dart';

Map<String, dynamic> figures({
  int visits = 0,
  num exam = 0,
  num lab = 0,
  num rx = 0,
}) => {
  'completedVisits': visits,
  'examRevenue': exam,
  'labRevenue': lab,
  'prescriptionRevenue': rx,
  'unclassifiedRevenue': 0,
  'refunds': 0,
};

void main() {
  test('revenue labels preserve fractional cash amounts', () {
    expect(money(5000.5), '5.000,5');
    expect(money(1234567.25), '1.234.567,25');
    expect(money(36000), '36.000');
  });

  test(
    'statistics read the provisional gateway contract by service line',
    () async {
      final adapter = PharmacyHttpAdapter()
        ..data = <String, dynamic>{
          'fromDate': '2026-11-01',
          'toDate': '2026-11-02',
          'departmentId': null,
          'currency': 'VND',
          'zoneId': 'Asia/Bangkok',
          'reconciled': false,
          'totals': figures(visits: 2, exam: 100000, lab: 36000, rx: 30000),
          'days': [
            {...figures(), 'date': '2026-11-01'},
            {
              ...figures(visits: 2, exam: 100000, lab: 36000, rx: 30000),
              'date': '2026-11-02',
            },
          ],
          'departments': [
            {
              ...figures(visits: 2, exam: 100000, lab: 36000, rx: 30000),
              'departmentId': otherDrugId,
            },
          ],
        };
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReportRepository(client);
      final result = await reader.statistics(
        const ReportQuery(
          kind: ReportKind.operations,
          from: '2026-11-01',
          to: '2026-11-02',
        ),
      );
      expect(adapter.requests.single.path, 'v1/reports/outpatient/statistics');
      expect(adapter.requests.single.queryParameters, {
        'fromDate': '2026-11-01',
        'toDate': '2026-11-02',
      });
      expect(result.totals.completedVisits, 2);
      expect(result.totals.prescriptionRevenue, 30000);
      expect(result.days, hasLength(2));
      expect(result.departments.single.label, otherDrugId);
      expect(result.departments.single.revenue, 166000);

      adapter.data = {
        ...adapter.data as Map<String, dynamic>,
        'reconciled': true,
      };
      await expectLater(
        reader.statistics(
          const ReportQuery(
            kind: ReportKind.operations,
            from: '2026-11-01',
            to: '2026-11-02',
          ),
        ),
        throwsFormatException,
      );
      await expectLater(
        reader.statistics(
          const ReportQuery(
            kind: ReportKind.operations,
            from: '2026-11-03',
            to: '2026-11-02',
          ),
        ),
        throwsArgumentError,
      );
    },
  );

  testWidgets('charts totals by service line without an unclassified series', (
    tester,
  ) async {
    const day = OperationsFigures(
      label: '2026-11-02',
      completedVisits: 2,
      examRevenue: 100000,
      labRevenue: 36000,
      prescriptionRevenue: 30000,
      unclassifiedRevenue: 0,
      refunds: 0,
    );
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: OperationsStatisticsView(
              OperationsStatistics(
                from: '2026-11-01',
                to: '2026-11-02',
                currency: 'VND',
                totals: day,
                days: [day],
                departments: [],
              ),
            ),
          ),
        ),
      ),
    );
    expect(
      find.text('Doanh thu theo ngày và loại dịch vụ (VND)'),
      findsOneWidget,
    );
    expect(find.text('Số lượt khám hoàn tất theo ngày'), findsOneWidget);
    expect(find.text('100.000 VND'), findsOneWidget);
    expect(find.text('30.000 VND'), findsOneWidget);
    expect(find.text('Chưa phân loại'), findsNothing);
    expect(find.textContaining('chưa đối soát'), findsOneWidget);
  });
}
