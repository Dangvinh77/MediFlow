/// The collaboration feature (treatment courses, handover, consultation,
/// amendments) is switched off on the server; the UI hides its sections.
class CollaborationUnavailable implements Exception {
  const CollaborationUnavailable();
}

const treatmentCourseClosureLabels = <String, String>{
  'RESOLVED': 'Đã giải quyết',
  'TRANSFERRED': 'Chuyển tuyến',
  'PATIENT_STOPPED': 'Bệnh nhân ngừng điều trị',
  'OTHER': 'Lý do khác',
};

const amendmentSectionLabels = <String, String>{
  'SYMPTOMS': 'Triệu chứng',
  'CHIEF_COMPLAINT': 'Lý do khám',
  'PRESENT_ILLNESS': 'Diễn biến bệnh',
  'PAST_HISTORY': 'Tiền sử',
  'ALLERGY_NOTE': 'Ghi chú dị ứng của lượt',
  'PHYSICAL_EXAM': 'Khám thực thể',
  'ADVICE': 'Dặn dò',
  'DISPOSITION_NOTE': 'Ghi chú hướng xử trí',
  'CLOSURE_NOTE': 'Ghi chú kết thúc',
  'DIAGNOSIS': 'Chẩn đoán',
};

const amendmentDecisionLabels = <String, String>{
  'APPROVED': 'Duyệt',
  'REJECTED': 'Từ chối',
};

class TreatmentCourse {
  const TreatmentCourse({
    required this.courseId,
    required this.patientId,
    required this.departmentId,
    required this.problem,
    required this.openingReason,
    required this.responsibleDoctorId,
    required this.status,
    required this.recordIds,
    required this.openedAt,
    required this.lastVisitDate,
    required this.reviewDue,
    this.closureDecision,
    this.closureNote,
    this.closedAt,
  });

  final String courseId, patientId, departmentId, problem, openingReason;
  final String responsibleDoctorId, status, openedAt, lastVisitDate;
  final List<String> recordIds;
  final bool reviewDue;
  final String? closureDecision, closureNote, closedAt;

  bool get isOpen => status == 'OPEN';
}

class RecordHandover {
  const RecordHandover({
    required this.handoverId,
    required this.recordId,
    required this.fromDoctorId,
    required this.toDoctorId,
    required this.reason,
    required this.pendingItems,
    required this.status,
    required this.requestedAt,
    this.acceptedAt,
  });

  final String handoverId, recordId, fromDoctorId, toDoctorId;
  final String reason, pendingItems, status, requestedAt;
  final String? acceptedAt;
}

class ConsultationNote {
  const ConsultationNote({
    required this.noteId,
    required this.recordId,
    required this.authorStaffId,
    required this.opinion,
    required this.createdAt,
  });

  final String noteId, recordId, authorStaffId, opinion, createdAt;
}

class RecordAmendment {
  const RecordAmendment({
    required this.amendmentId,
    required this.recordId,
    required this.section,
    required this.originalText,
    required this.amendedText,
    required this.reason,
    required this.authorStaffId,
    required this.status,
    required this.requestedAt,
    this.assignedByStaffId,
    this.decidedByStaffId,
    this.decisionNote,
    this.decidedAt,
    this.contentHash,
  });

  final String amendmentId, recordId, section, originalText, amendedText;
  final String reason, authorStaffId, status, requestedAt;
  final String? assignedByStaffId, decidedByStaffId, decisionNote, decidedAt;
  final String? contentHash;
}

abstract interface class RecordCollaborationRepository {
  /// Throws [CollaborationUnavailable] when the feature is off.
  Future<List<TreatmentCourse>> courses(String patientId);
  Future<void> openCourse({
    required String patientId,
    required String problem,
    required String openingReason,
    required String recordId,
    required String idempotencyKey,
  });
  Future<void> attachVisit({
    required String courseId,
    required String recordId,
    required String idempotencyKey,
  });
  Future<void> closeCourse({
    required String courseId,
    required String decision,
    required String note,
    required String idempotencyKey,
  });

  Future<List<RecordHandover>> handovers(String recordId);
  Future<void> requestHandover(
    String recordId, {
    required String toDoctorId,
    required String reason,
    required String pendingItems,
    required String idempotencyKey,
  });
  Future<void> acceptHandover(
    String recordId,
    String handoverId, {
    required String idempotencyKey,
  });

  Future<List<ConsultationNote>> notes(String recordId);
  Future<void> addNote(
    String recordId, {
    required String opinion,
    required String idempotencyKey,
  });

  Future<List<RecordAmendment>> amendments(String recordId);
  Future<void> requestAmendment(
    String recordId, {
    required String section,
    required String amendedText,
    required String reason,
    required String idempotencyKey,
  });
  Future<void> decideAmendment(
    String recordId,
    String amendmentId, {
    required String decision,
    required String note,
    required String idempotencyKey,
  });
  Future<List<RecordAmendment>> pendingAmendments();
}
