import '../../../core/network/api_client.dart';
import '../../../core/domain/reference.dart';
import '../domain/report.dart';

class GatewayReportRepository implements ReportRepository {
  const GatewayReportRepository(this.client);
  final ApiClient client;
  Map<String, dynamic> _map(Object? raw) {
    if (raw is! Map<String, dynamic>) {
      throw const FormatException('Báo cáo không hợp lệ.');
    }
    return raw;
  }

  num _number(Map<String, dynamic> raw, String key) {
    final value = raw[key];
    if (value is! num || !value.isFinite) {
      throw FormatException('Số liệu $key không hợp lệ.');
    }
    return value;
  }

  List<dynamic> _list(Map<String, dynamic> raw, String key) {
    if (raw[key] is! List) {
      throw const FormatException('Chi tiết báo cáo không hợp lệ.');
    }
    return raw[key] as List;
  }

  Map<String, num> _metrics(
    Map<String, dynamic> raw,
    Map<String, String> fields,
  ) => {for (final e in fields.entries) e.value: _number(raw, e.key)};
  ReportRow _daily(Object? row) {
    final m = _map(row);
    return ReportRow(
      m['reportDate'] as String? ?? 'Trong ngày',
      _metrics(m, {
        'visitCount': 'Lượt khám',
        'labCount': 'Xét nghiệm',
        'prescriptionCount': 'Đơn thuốc',
        'revenue': 'Doanh thu (VND)',
      }),
    );
  }

  @override
  Future<ReportData> read(ReportQuery q) async {
    q.validate();
    if (q.departmentId != null && !isReferenceId(q.departmentId!)) {
      throw ArgumentError('Khoa phòng không hợp lệ.');
    }
    final from = DateTime.parse(q.from);
    final path = switch (q.kind) {
      ReportKind.daily => 'daily',
      ReportKind.monthly => 'monthly',
      ReportKind.topMedicines => 'top-medicines',
      ReportKind.outpatientDaily => 'outpatient/daily',
      ReportKind.outpatientMonthly => 'outpatient/monthly',
    };
    final params = <String, dynamic>{
      if (q.departmentId != null) 'departmentId': q.departmentId,
      ...switch (q.kind) {
        ReportKind.daily => {'date': q.from},
        ReportKind.monthly => {'month': from.month, 'year': from.year},
        ReportKind.topMedicines => {
          'fromDate': q.from,
          'toDate': q.to,
          'limit': q.limit,
        },
        ReportKind.outpatientDaily => {'fromDate': q.from, 'toDate': q.to},
        ReportKind.outpatientMonthly => {'year': from.year},
      },
    };
    final raw = await client.get('/v1/reports/$path', query: params);
    if (q.kind == ReportKind.topMedicines) {
      if (raw is! List) {
        throw const FormatException('Danh sách thuốc không hợp lệ.');
      }
      return ReportData('Thuốc sử dụng nhiều nhất', [
        for (final item in raw)
          ReportRow(
            _map(item)['drugName'] as String,
            _metrics(_map(item), {'totalQuantity': 'Số lượng'}),
          ),
      ], departmentId: q.departmentId);
    }
    final m = _map(raw);
    if (q.kind == ReportKind.daily) {
      return ReportData('Báo cáo ngày', [
        _daily(m),
      ], departmentId: m['departmentId'] as String?);
    }
    if (q.kind == ReportKind.monthly) {
      return ReportData('Báo cáo tháng ${from.month}/${from.year}', [
        ReportRow(
          'Tổng tháng',
          _metrics(m, {
            'totalRevenue': 'Doanh thu (VND)',
            'invoiceCount': 'Hóa đơn',
          }),
        ),
        ..._list(m, 'dailyDetails').map(_daily),
      ], departmentId: m['departmentId'] as String?);
    }
    if (m['covered'] is! bool ||
        m['currency'] is! String ||
        m['zoneId'] is! String) {
      throw const FormatException('Độ phủ báo cáo không hợp lệ.');
    }
    return ReportData(
      'Báo cáo ngoại trú',
      [
        for (final item in _list(m, 'periods'))
          ReportRow(
            '${_map(item)['fromDate']} – ${_map(item)['toDate']}',
            _metrics(_map(item), {
              'completedVisits': 'Lượt khám hoàn tất',
              'labTests': 'Xét nghiệm',
              'dispensedPrescriptions': 'Đơn đã cấp',
              'dispensedUnits': 'Đơn vị thuốc đã cấp',
              'cashReceived': 'Tiền thu',
              'refunds': 'Hoàn tiền',
              'netCash': 'Thu ròng',
            }),
          ),
      ],
      note:
          '${m['covered'] == true ? 'Đã đối soát' : 'Chưa đủ độ phủ đối soát'} · ${m['currency']} · ${m['zoneId']}\nĐối soát lúc: ${m['reconciledAt'] ?? 'Chưa có'}',
      departmentId: m['departmentId'] as String?,
    );
  }
}
