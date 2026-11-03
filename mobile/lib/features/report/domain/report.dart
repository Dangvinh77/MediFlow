enum ReportKind {
  daily,
  monthly,
  topMedicines,
  outpatientDaily,
  outpatientMonthly,
}

class ReportQuery {
  const ReportQuery({
    required this.kind,
    required this.from,
    required this.to,
    this.departmentId,
    this.limit = 10,
  });
  final ReportKind kind;
  final String from, to;
  final String? departmentId;
  final int limit;
  void validate() {
    bool date(String s) {
      final d = DateTime.tryParse(s);
      return d != null && d.toIso8601String().substring(0, 10) == s;
    }

    final rangeBased =
        kind == ReportKind.topMedicines || kind == ReportKind.outpatientDaily;
    if (!date(from) ||
        (rangeBased && (!date(to) || from.compareTo(to) > 0)) ||
        (kind == ReportKind.topMedicines && (limit < 1 || limit > 50))) {
      throw ArgumentError('Khoảng ngày hoặc số lượng không hợp lệ.');
    }
  }
}

class ReportRow {
  const ReportRow(this.title, this.metrics);
  final String title;
  final Map<String, num> metrics;
}

class ReportData {
  const ReportData(
    this.title,
    this.rows, {
    this.note,
    this.departmentId,
    this.currency,
  });
  final String title;
  final List<ReportRow> rows;
  final String? note, departmentId, currency;
}

class AvailableReportPeriod {
  const AvailableReportPeriod({
    required this.coverageId,
    required this.from,
    required this.to,
    required this.currency,
    required this.zoneId,
    required this.sourceObservedAt,
    required this.reconciledAt,
    required this.syntheticData,
    this.departmentId,
  });
  final String coverageId,
      from,
      to,
      currency,
      zoneId,
      sourceObservedAt,
      reconciledAt;
  final String? departmentId;
  final bool syntheticData;
  bool get isFullYear =>
      from.endsWith('-01-01') && to == '${from.substring(0, 4)}-12-31';
}

abstract interface class ReportRepository {
  Future<List<AvailableReportPeriod>> availablePeriods({String? departmentId});
  Future<ReportData> read(ReportQuery query);
}
