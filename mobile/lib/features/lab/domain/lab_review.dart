class LabCriticalNotice {
  const LabCriticalNotice({
    required this.noticeId,
    required this.testId,
    required this.resultVersion,
    required this.fieldCode,
    required this.label,
    required this.value,
    required this.flag,
    required this.patientId,
    required this.departmentId,
    required this.status,
    required this.createdAt,
    this.unit,
    this.acknowledgedBy,
    this.acknowledgedAt,
  });

  final String noticeId, testId, fieldCode, label, value, flag;
  final String patientId, departmentId, status, createdAt;
  final int resultVersion;
  final String? unit, acknowledgedBy, acknowledgedAt;

  bool get isAcknowledged => status == 'ACKNOWLEDGED';
}

class LabWorklistItem {
  const LabWorklistItem({
    required this.testId,
    required this.patientId,
    required this.labType,
    required this.testStatus,
    required this.overdue,
    this.serviceCode,
    this.serviceName,
    this.displayGroup,
    this.specimenStatus,
    this.reportStatus,
    this.reportVersion,
    this.dueAt,
  });

  final String testId, patientId, labType, testStatus;
  final String? serviceCode, serviceName, displayGroup;
  final String? specimenStatus, reportStatus, dueAt;
  final int? reportVersion;
  final bool overdue;
}

class LabReviewUnavailable implements Exception {
  const LabReviewUnavailable();
}

abstract interface class LabReviewRepository {
  Future<List<LabCriticalNotice>> criticalNotices({String status = 'OPEN'});
  Future<LabCriticalNotice> acknowledge(String testId, String noticeId);
  Future<List<LabWorklistItem>> worklistItems();
}
