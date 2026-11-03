import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/report/data/gateway_report_repository.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';

import 'pharmacy_fixtures.dart';

void main() {
  test(
    'discovery uses gateway scope and rejects malformed acceptance metadata',
    () async {
      final item = <String, dynamic>{
        'coverageId': drugId,
        'fromDate': '2025-01-01',
        'toDate': '2025-12-31',
        'departmentId': otherDrugId,
        'currency': 'VND',
        'zoneId': 'Asia/Bangkok',
        'sourceObservedAt': '2026-01-01T00:00:00Z',
        'reconciledAt': '2026-01-01T01:00:00Z',
        'syntheticData': true,
      };
      final adapter = PharmacyHttpAdapter()..data = [item];
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReportRepository(client);
      final result = await reader.availablePeriods(departmentId: otherDrugId);
      expect(
        adapter.requests.single.path,
        'v1/reports/outpatient/available-periods',
      );
      expect(adapter.requests.single.queryParameters, {
        'departmentId': otherDrugId,
      });
      expect(result.single.isFullYear, isTrue);
      expect(result.single.syntheticData, isTrue);
      adapter.data = [
        {...item, 'coverageId': 'invalid'},
      ];
      await expectLater(reader.availablePeriods(), throwsFormatException);
      adapter.data = [
        {...item, 'toDate': '2025-02-30'},
      ];
      await expectLater(reader.availablePeriods(), throwsArgumentError);
    },
  );

  test(
    'outpatient provenance preserves zone currency and both observation times',
    () async {
      final adapter = PharmacyHttpAdapter()
        ..data = <String, dynamic>{
          'covered': true,
          'coverageId': drugId,
          'sourceObservedAt': '2025-01-02T00:00:00Z',
          'reconciledAt': '2025-01-02T01:00:00Z',
          'currency': 'VND',
          'zoneId': 'Asia/Bangkok',
          'syntheticData': true,
          'periods': <dynamic>[],
        };
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final result = await GatewayReportRepository(client).read(
        const ReportQuery(
          kind: ReportKind.outpatientDaily,
          from: '2025-01-01',
          to: '2025-01-01',
        ),
      );
      expect(result.note, contains('Asia/Bangkok'));
      expect(result.note, contains('VND'));
      expect(result.note, contains('2025-01-02T00:00:00Z'));
      expect(result.note, contains('Dữ liệu mô phỏng'));
    },
  );

  test(
    'all remaining report readers serialize their exact parameters',
    () async {
      final adapter = PharmacyHttpAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReportRepository(client);
      for (final (kind, path, query, data) in [
        (
          ReportKind.monthly,
          'monthly',
          {'month': 10, 'year': 2026},
          <String, dynamic>{
            'totalRevenue': 100,
            'invoiceCount': 2,
            'dailyDetails': [],
          },
        ),
        (
          ReportKind.topMedicines,
          'top-medicines',
          {'fromDate': '2026-10-01', 'toDate': '2026-10-22', 'limit': 10},
          <dynamic>[
            {'drugName': 'Paracetamol', 'totalQuantity': 12},
          ],
        ),
        (
          ReportKind.outpatientDaily,
          'outpatient/daily',
          {'fromDate': '2026-10-01', 'toDate': '2026-10-22'},
          <String, dynamic>{
            'covered': true,
            'coverageId': drugId,
            'sourceObservedAt': '2026-10-23T00:00:00Z',
            'reconciledAt': '2026-10-23T01:00:00Z',
            'currency': 'VND',
            'zoneId': 'Asia/Ho_Chi_Minh',
            'periods': [],
          },
        ),
        (
          ReportKind.outpatientMonthly,
          'outpatient/monthly',
          {'year': 2026},
          <String, dynamic>{
            'covered': true,
            'coverageId': drugId,
            'sourceObservedAt': '2026-10-23T00:00:00Z',
            'reconciledAt': '2026-10-23T01:00:00Z',
            'currency': 'VND',
            'zoneId': 'Asia/Ho_Chi_Minh',
            'periods': [],
          },
        ),
      ]) {
        adapter.data = data;
        final result = await reader.read(
          ReportQuery(
            kind: kind,
            from: '2026-10-01',
            to: '2026-10-22',
            departmentId: drugId,
          ),
        );
        expect(result.title, isNotEmpty);
        expect(adapter.requests.last.uri.path, '/api/v1/reports/$path');
        expect(adapter.requests.last.queryParameters, {
          ...query,
          'departmentId': drugId,
        });
      }
    },
  );

  test('single-period reports ignore an old hidden range end; top limit matches server', () {
    for (final kind in [
      ReportKind.daily,
      ReportKind.monthly,
      ReportKind.outpatientMonthly,
    ]) {
      expect(
        () => ReportQuery(
          kind: kind,
          from: '2026-11-01',
          to: '2026-10-01',
        ).validate(),
        returnsNormally,
      );
    }
    expect(
      () => const ReportQuery(
        kind: ReportKind.topMedicines,
        from: '2026-10-01',
        to: '2026-10-22',
        limit: 51,
      ).validate(),
      throwsArgumentError,
    );
  });
  test(
    'daily report uses exact query and rejects unknown numeric values',
    () async {
      final adapter = PharmacyHttpAdapter()
        ..data = {
          'reportDate': '2026-10-22',
          'departmentId': null,
          'visitCount': 4,
          'labCount': 2,
          'prescriptionCount': 1,
          'revenue': 125000,
        };
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final reader = GatewayReportRepository(client);
      final report = await reader.read(
        const ReportQuery(
          kind: ReportKind.daily,
          from: '2026-10-22',
          to: '2026-10-22',
        ),
      );
      expect(report.rows.single.metrics['Lượt khám'], 4);
      expect(adapter.requests.last.uri.path, '/api/v1/reports/daily');
      expect(adapter.requests.last.queryParameters, {'date': '2026-10-22'});
      adapter.data = {
        'reportDate': '2026-10-22',
        'visitCount': 'bad',
        'labCount': 2,
        'prescriptionCount': 1,
        'revenue': 125000,
      };
      await expectLater(
        reader.read(
          const ReportQuery(
            kind: ReportKind.daily,
            from: '2026-10-22',
            to: '2026-10-22',
          ),
        ),
        throwsFormatException,
      );
    },
  );
  test('report date ranges reject impossible dates before transport', () {
    expect(
      () => const ReportQuery(
        kind: ReportKind.outpatientDaily,
        from: '2026-02-30',
        to: '2026-03-01',
      ).validate(),
      throwsArgumentError,
    );
    expect(
      () => const ReportQuery(
        kind: ReportKind.topMedicines,
        from: '2026-10-23',
        to: '2026-10-22',
      ).validate(),
      throwsArgumentError,
    );
  });

  test('outpatient report refuses unaccepted or incomplete coverage', () async {
    final adapter = PharmacyHttpAdapter();
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    addTearDown(client.close);
    final reader = GatewayReportRepository(client);
    const query = ReportQuery(
      kind: ReportKind.outpatientDaily,
      from: '2026-10-01',
      to: '2026-10-22',
    );
    for (final coverage in [
      <String, dynamic>{'covered': false},
      <String, dynamic>{
        'covered': true,
        'currency': 'VND',
        'zoneId': 'Asia/Ho_Chi_Minh',
        'periods': [],
      },
    ]) {
      adapter.data = coverage;
      await expectLater(reader.read(query), throwsFormatException);
    }
  });
}
