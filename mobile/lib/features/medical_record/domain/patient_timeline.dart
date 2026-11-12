import 'medical_record.dart';

class TimelineEntry {
  const TimelineEntry({
    required this.recordId,
    required this.examinationDate,
    required this.departmentId,
    required this.doctorId,
    required this.visitKind,
    required this.status,
    required this.disposition,
    required this.treatmentCourseIds,
    this.responsibleDoctorId,
    this.previousRecordId,
  });

  final String recordId;
  final String examinationDate, departmentId, doctorId, status;
  final String? responsibleDoctorId, visitKind, disposition, previousRecordId;
  final List<String> treatmentCourseIds;

  String get visitKindLabel => switch (visitKind) {
    'NEW' => 'Khám mới',
    'FOLLOW_UP' => 'Tái khám',
    'RETURN' => 'Quay lại',
    _ => 'Không rõ',
  };
}

class TimelinePage {
  const TimelinePage({
    required this.entries,
    required this.page,
    required this.size,
    required this.totalPages,
    required this.totalElements,
  });

  final List<TimelineEntry> entries;
  final int page, size, totalPages, totalElements;
  bool get hasNext => page + 1 < totalPages;
}

class RecordDocument {
  const RecordDocument({
    required this.type,
    required this.referenceId,
    required this.summary,
    required this.attachedAt,
    required this.afterConclusion,
    this.resultVersion,
    this.amendmentReason,
    this.previousRevisions = const [],
  });

  final String type, referenceId, summary, attachedAt;
  final bool afterConclusion;
  final int? resultVersion;
  final String? amendmentReason;
  final List<RecordDocumentRevision> previousRevisions;

  String get typeLabel => switch (type) {
    'LAB' => 'Kết quả xét nghiệm',
    'PRESCRIPTION' => 'Đơn thuốc',
    _ => type,
  };
}

class RecordDocumentRevision {
  const RecordDocumentRevision({
    required this.summary,
    this.resultVersion,
    this.amendmentReason,
    this.sourceAt,
    this.label,
  });

  final int? resultVersion;
  final String summary;
  final String? amendmentReason, sourceAt, label;
}

abstract interface class PatientTimelineRepository {
  Future<TimelinePage> timeline(
    String patientId, {
    int page = 0,
    int size = 20,
    String? departmentId,
    String? status,
  });

  Future<List<RecordDocument>> documents(String recordId);

  Future<MedicalRecord> updateContent(
    String recordId, {
    required Map<String, String?> changes,
    required String idempotencyKey,
  });
}

const recordContentFields = <String>[
  'symptoms',
  'chiefComplaint',
  'presentIllness',
  'pastHistory',
  'allergyNote',
  'physicalExam',
  'advice',
];

/// Returns a partial update map; an empty string intentionally clears a field.
Map<String, String?> changedRecordSections({
  required Map<String, String?> original,
  required Map<String, String?> edited,
}) => Map.unmodifiable({
  for (final entry in edited.entries)
    if (original[entry.key] != entry.value) entry.key: entry.value,
});

class PatientAllergy {
  const PatientAllergy({
    required this.allergyId,
    required this.substance,
    required this.severity,
    required this.status,
    required this.recordedByStaffId,
    required this.recordedAt,
    this.reaction,
    this.sourceRecordId,
    this.statusReason,
    this.statusChangedByStaffId,
    this.statusChangedAt,
  });

  final String allergyId, substance, severity, status;
  final String recordedByStaffId, recordedAt;
  final String? reaction, sourceRecordId, statusReason;
  final String? statusChangedByStaffId, statusChangedAt;
}

class PatientAllergies {
  const PatientAllergies({required this.active, required this.inactive});
  final List<PatientAllergy> active, inactive;
}

abstract interface class PatientAllergyRepository {
  Future<PatientAllergies> list(String patientId);

  Future<PatientAllergy> add(
    String patientId, {
    required String substance,
    String? reaction,
    required String severity,
    String? sourceRecordId,
    required String idempotencyKey,
  });

  Future<PatientAllergy> changeStatus(
    String patientId,
    String allergyId, {
    required String status,
    required String reason,
    required String idempotencyKey,
  });
}
