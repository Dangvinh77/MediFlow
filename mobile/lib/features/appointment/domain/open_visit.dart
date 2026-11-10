/// An examination that has started and has not ended; carries no clinical content.
class OpenVisit {
  const OpenVisit({
    required this.registrationId,
    required this.recordId,
    required this.doctorId,
    required this.departmentId,
    required this.sessionDate,
    this.startedAt,
  });
  final String registrationId, recordId, doctorId, departmentId, sessionDate;
  final String? startedAt;
}

/// A finished record a return visit can point back to.
class ReturnSource {
  const ReturnSource({
    required this.recordId,
    required this.status,
    required this.examinationDate,
  });
  final String recordId, status, examinationDate;
}

const returnSourceLabels = <String, String>{
  'COMPLETED': 'Đã hoàn tất khám',
  'CLOSED_WITHOUT_CONCLUSION': 'Kết thúc không kết luận',
};

abstract interface class OpenVisitRepository {
  Future<List<OpenVisit>> openVisits(String patientId);
  Future<List<OpenVisit>> myOpenVisits();
  Future<List<ReturnSource>> returnSources(String patientId);
}
