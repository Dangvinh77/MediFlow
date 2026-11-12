class Diagnosis {
  const Diagnosis({
    required this.id,
    required this.name,
    this.description,
    this.icdCode,
  });
  final String id, name;
  final String? description, icdCode;
}

class EffectiveRecordCorrection {
  const EffectiveRecordCorrection({
    required this.section,
    required this.originalText,
    required this.correctedText,
    required this.reason,
    required this.appliedAt,
  });

  final String section, originalText, correctedText, reason, appliedAt;
}

class MedicalRecord {
  const MedicalRecord({
    required this.id,
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.examinationDate,
    required this.diagnoses,
    required this.createdAt,
    required this.status,
    this.symptoms,
    this.chiefComplaint,
    this.presentIllness,
    this.pastHistory,
    this.allergyNote,
    this.physicalExam,
    this.advice,
    this.responsibleDoctorId,
    this.appointmentId,
    this.updatedAt,
    this.disposition,
    this.dispositionNote,
    this.completedAt,
    this.closureReason,
    this.closureNote,
    this.closedAt,
    this.previousRecordId,
    this.effectiveCorrections = const [],
  });

  final String id, patientId, doctorId, departmentId, examinationDate;
  final List<Diagnosis> diagnoses;
  final String createdAt, status;
  final String? symptoms, appointmentId, updatedAt;
  final String? chiefComplaint, presentIllness, pastHistory, allergyNote;
  final String? physicalExam, advice, responsibleDoctorId;
  final String? disposition, dispositionNote, completedAt;
  final String? closureReason, closureNote, closedAt, previousRecordId;
  final List<EffectiveRecordCorrection> effectiveCorrections;
}

class DiagnosisDraft {
  const DiagnosisDraft({
    required this.diagnosisName,
    this.description,
    this.icdCode,
  });
  final String diagnosisName;
  final String? description, icdCode;
}

class CreateMedicalRecord {
  const CreateMedicalRecord({
    required this.patientId,
    required this.doctorId,
    required this.departmentId,
    required this.examinationDate,
    required this.diagnoses,
    this.symptoms,
    this.appointmentId,
  });
  final String patientId, doctorId, departmentId, examinationDate;
  final String? symptoms, appointmentId;
  final List<DiagnosisDraft> diagnoses;
}

class ClinicalLabOrderDraft {
  const ClinicalLabOrderDraft({
    required this.labType,
    required this.requestedDate,
    required this.priceCode,
    this.serviceCode,
  });
  final String labType, requestedDate, priceCode;
  final String? serviceCode;
}

class ClinicalLabOrder {
  const ClinicalLabOrder({
    required this.sourceOrderId,
    required this.recordId,
    required this.patientId,
    required this.requestingDoctorId,
    required this.requestingDepartmentId,
    required this.careEpisodeType,
    required this.careEpisodeId,
    required this.labType,
    required this.requestedDate,
    required this.priceCode,
    required this.orderedAt,
    this.serviceCode,
  });
  final String sourceOrderId, recordId, patientId, requestingDoctorId;
  final String requestingDepartmentId, careEpisodeType, careEpisodeId;
  final String labType, requestedDate, priceCode, orderedAt;
  final String? serviceCode;
}

class ClinicalLabCatalogItem {
  const ClinicalLabCatalogItem({
    required this.serviceCode,
    required this.name,
    required this.displayGroup,
    required this.specimenType,
    required this.priceCode,
    required this.templateCode,
    required this.panel,
    this.preparation,
    this.turnaroundHours,
  });

  final String serviceCode, name, displayGroup, specimenType;
  final String priceCode, templateCode;
  final String? preparation;
  final int? turnaroundHours;
  final bool panel;
}

/// Optional catalogue capability. A null response means the server explicitly
/// disabled the catalogue; all other request failures remain visible to callers.
abstract interface class ClinicalLabCatalogRepository {
  Future<List<ClinicalLabCatalogItem>?> labCatalog({String? group});
}

class LabTestReceipt {
  const LabTestReceipt({required this.testId, required this.status});
  final String testId, status;
}

class RecordCapabilities {
  const RecordCapabilities({
    required this.recordId,
    required this.canRead,
    required this.canWriteContent,
    required this.canComplete,
    this.patientId,
    this.writeBlockedReason,
    this.canHandover,
    this.canAcceptHandover,
    this.canAddConsultationNote,
    this.canAmend,
    this.canApplyAmendment,
    this.canDecideAmendments,
  });

  /// Anything the client cannot interpret grants nothing.
  const RecordCapabilities.readOnly(this.recordId)
    : canRead = false,
      canWriteContent = false,
      canComplete = false,
      patientId = null,
      writeBlockedReason = null,
      canHandover = null,
      canAcceptHandover = null,
      canAddConsultationNote = null,
      canAmend = null,
      canApplyAmendment = null,
      canDecideAmendments = null;

  final String recordId;
  final String? patientId, writeBlockedReason;
  final bool canRead, canWriteContent, canComplete;

  /// Collaboration flags are null (absent) while the server feature is off.
  final bool? canHandover, canAcceptHandover, canAddConsultationNote;
  final bool? canAmend, canApplyAmendment, canDecideAmendments;

  bool get collaborationAvailable =>
      canHandover != null ||
      canAcceptHandover != null ||
      canAddConsultationNote != null ||
      canAmend != null ||
      canApplyAmendment != null ||
      canDecideAmendments != null;
}

abstract interface class MedicalRecordRepository {
  Future<MedicalRecord> getById(String recordId);
  Future<RecordCapabilities> capabilities(String recordId);
  Future<void> declareAccess({
    required String patientId,
    required String purpose,
    String? reason,
  });
  Future<List<MedicalRecord>> byPatient(String patientId);
  Future<MedicalRecord> create(CreateMedicalRecord draft);
  Future<MedicalRecord> update(String recordId, {required String? symptoms});
  Future<Diagnosis> addDiagnosis(String recordId, DiagnosisDraft diagnosis);
  Future<MedicalRecord> complete(
    String recordId, {
    required String disposition,
    required String? note,
  });
  Future<ClinicalLabOrder> createClinicalLabOrder(
    String recordId,
    ClinicalLabOrderDraft draft,
  );
  Future<LabTestReceipt> createLabRequestFromSourceOrder(
    ClinicalLabOrder sourceOrder,
  );
}

bool canEditMedicalRecord(MedicalRecord record, {required String? role}) =>
    const {'ADMIN', 'DOCTOR'}.contains(role) && record.status == 'OPEN';

bool canCompleteMedicalRecord(
  MedicalRecord record, {
  required String? role,
  required bool outpatientEnabled,
}) =>
    outpatientEnabled &&
    const {'ADMIN', 'DOCTOR'}.contains(role) &&
    record.status == 'OPEN' &&
    record.appointmentId?.trim().isNotEmpty == true &&
    record.diagnoses.isNotEmpty;

bool canOrderClinicalLab(
  MedicalRecord record, {
  required String? role,
  required bool outpatientEnabled,
}) =>
    outpatientEnabled &&
    const {'ADMIN', 'DOCTOR'}.contains(role) &&
    record.status == 'OPEN' &&
    record.appointmentId?.trim().isNotEmpty == true;

String? validateDiagnosis(DiagnosisDraft draft) {
  if (draft.diagnosisName.trim().isEmpty) return 'Nhập tên chẩn đoán.';
  if (draft.diagnosisName.trim().length > 255) {
    return 'Tên chẩn đoán không được dài quá 255 ký tự.';
  }
  if ((draft.description?.trim().length ?? 0) > 2000) {
    return 'Mô tả không được dài quá 2000 ký tự.';
  }
  final code = draft.icdCode?.trim();
  if (code != null &&
      code.isNotEmpty &&
      !RegExp(r'^[A-Z]\d{2}(\.\d{1,2})?$').hasMatch(code)) {
    return 'Mã ICD không đúng định dạng.';
  }
  return null;
}

String? validateRecordDraft(CreateMedicalRecord draft) {
  if (![draft.patientId, draft.doctorId, draft.departmentId].every(_isUuid)) {
    return 'Chọn bệnh nhân, bác sĩ và khoa từ danh sách.';
  }
  if (draft.appointmentId != null && !_isUuid(draft.appointmentId!)) {
    return 'Chọn lịch hẹn từ danh sách.';
  }
  if (!_validDate(draft.examinationDate) ||
      _isAfterToday(draft.examinationDate)) {
    return 'Ngày khám phải hợp lệ và không ở tương lai.';
  }
  if ((draft.symptoms?.trim().length ?? 0) > 4000) {
    return 'Triệu chứng không được dài quá 4000 ký tự.';
  }
  if (draft.diagnoses.isEmpty ||
      draft.diagnoses.any((d) => validateDiagnosis(d) != null)) {
    return 'Thêm ít nhất một chẩn đoán hợp lệ.';
  }
  return null;
}

String? validateClinicalLabOrder(ClinicalLabOrderDraft draft) {
  final serviceCode = draft.serviceCode?.trim();
  if (serviceCode != null && (serviceCode.isEmpty || serviceCode.length > 32)) {
    return 'Mã dịch vụ không được dài quá 32 ký tự.';
  }
  if (draft.labType.trim().isEmpty || draft.labType.trim().length > 50) {
    return 'Nhập loại xét nghiệm (tối đa 50 ký tự).';
  }
  if (draft.priceCode.trim().isEmpty || draft.priceCode.trim().length > 64) {
    return 'Nhập mã giá đã được cơ sở phê duyệt (tối đa 64 ký tự).';
  }
  if (!_validDate(draft.requestedDate) || _isAfterToday(draft.requestedDate)) {
    return 'Ngày yêu cầu phải hợp lệ và không ở tương lai.';
  }
  return null;
}

bool isMedicalRecordUuid(String value) => _isUuid(value);

bool _isUuid(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

bool _validDate(String value) {
  final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(value);
  if (match == null) return false;
  final year = int.parse(match[1]!);
  final month = int.parse(match[2]!);
  final day = int.parse(match[3]!);
  final parsed = DateTime.tryParse(value);
  return parsed != null &&
      parsed.year == year &&
      parsed.month == month &&
      parsed.day == day;
}

bool _isAfterToday(String value) {
  final date = DateTime.parse(value);
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  return date.isAfter(today);
}
