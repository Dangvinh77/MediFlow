enum ReportKind {
  daily,
  monthly,
  topMedicines,
  outpatientDaily,
  outpatientMonthly,
  operations,
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
        kind == ReportKind.topMedicines ||
        kind == ReportKind.outpatientDaily ||
        kind == ReportKind.operations;
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

/// One day or one department of provisional outpatient operations (not reconciled).
class OperationsFigures {
  const OperationsFigures({
    required this.label,
    required this.completedVisits,
    required this.examRevenue,
    required this.labRevenue,
    required this.prescriptionRevenue,
    required this.unclassifiedRevenue,
    required this.refunds,
  });
  final String label;
  final int completedVisits;
  final double examRevenue,
      labRevenue,
      prescriptionRevenue,
      unclassifiedRevenue,
      refunds;
  double get revenue =>
      examRevenue + labRevenue + prescriptionRevenue + unclassifiedRevenue;
}

/// Report `GET /v1/reports/outpatient/statistics`: daily and per-department figures.
/// Department rows carry the department id in [OperationsFigures.label].
class OperationsStatistics {
  const OperationsStatistics({
    required this.from,
    required this.to,
    required this.currency,
    required this.totals,
    required this.days,
    required this.departments,
    this.departmentId,
  });
  final String from, to, currency;
  final String? departmentId;
  final OperationsFigures totals;
  final List<OperationsFigures> days, departments;
}

abstract interface class ReportRepository {
  Future<List<AvailableReportPeriod>> availablePeriods({String? departmentId});
  Future<ReportData> read(ReportQuery query);
  Future<OperationsStatistics> statistics(ReportQuery query);
}
