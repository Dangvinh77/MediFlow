class LabResultDraft {
  const LabResultDraft({
    required this.indicator,
    required this.value,
    this.unit,
    this.referenceRange,
  });
  final String indicator, value;
  final String? unit, referenceRange;
  void validate(String date) {
    final parsed = DateTime.tryParse(date);
    if (indicator.trim().isEmpty ||
        value.trim().isEmpty ||
        parsed == null ||
        parsed.toIso8601String().substring(0, 10) != date) {
      throw ArgumentError('Nhập chỉ số, giá trị và ngày thực hiện hợp lệ.');
    }
  }
}

class LabDetail {
  const LabDetail({
    required this.id,
    required this.patientId,
    required this.recordId,
    required this.departmentId,
    required this.type,
    required this.requestedDate,
    required this.status,
    required this.paid,
    required this.version,
    this.clearanceId,
    this.performedDate,
    this.conclusion,
    this.results = const [],
  });
  final String id,
      patientId,
      recordId,
      departmentId,
      type,
      requestedDate,
      status;
  final int version;
  final bool paid;
  final String? clearanceId, performedDate, conclusion;
  final List<LabResultDraft> results;
}

abstract interface class LabWorkflow {
  Future<LabDetail> detail(String id);
  Future<LabDetail> start(String id);
  Future<LabDetail> results(
    String id,
    List<LabResultDraft> results,
    String? conclusion,
    String date,
  );
  Future<LabDetail> cancel(String id, String reason, String actorId);
}
