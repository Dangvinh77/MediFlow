import '../../../core/network/json_reader.dart';
import '../domain/medical_record.dart';

abstract final class MedicalRecordDto {
  static MedicalRecord fromJson(Map<String, dynamic> json) {
    final rawDiagnoses = json['diagnoses'];
    if (rawDiagnoses is! List) {
      throw const FormatException('Dữ liệu diagnoses không hợp lệ.');
    }
    final rawCorrections = json['effectiveCorrections'];
    if (rawCorrections != null && rawCorrections is! List) {
      throw const FormatException('Dữ liệu effectiveCorrections không hợp lệ.');
    }
    return MedicalRecord(
      id: JsonReader.string(json, 'recordId'),
      patientId: JsonReader.string(json, 'patientId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      examinationDate: JsonReader.string(json, 'examinationDate'),
      symptoms: JsonReader.optionalString(json, 'symptoms'),
      chiefComplaint: JsonReader.optionalString(json, 'chiefComplaint'),
      presentIllness: JsonReader.optionalString(json, 'presentIllness'),
      pastHistory: JsonReader.optionalString(json, 'pastHistory'),
      allergyNote: JsonReader.optionalString(json, 'allergyNote'),
      physicalExam: JsonReader.optionalString(json, 'physicalExam'),
      advice: JsonReader.optionalString(json, 'advice'),
      responsibleDoctorId: JsonReader.optionalString(
        json,
        'responsibleDoctorId',
      ),
      appointmentId: JsonReader.optionalString(json, 'appointmentId'),
      diagnoses: rawDiagnoses
          .map((value) {
            if (value is! Map<String, dynamic>) {
              throw const FormatException('Dữ liệu diagnosis không hợp lệ.');
            }
            return DiagnosisDto.fromJson(value);
          })
          .toList(growable: false),
      createdAt: JsonReader.string(json, 'createdAt'),
      updatedAt: JsonReader.optionalString(json, 'updatedAt'),
      status: JsonReader.string(json, 'status'),
      disposition: JsonReader.optionalString(json, 'disposition'),
      dispositionNote: JsonReader.optionalString(json, 'dispositionNote'),
      completedAt: JsonReader.optionalString(json, 'completedAt'),
      closureReason: JsonReader.optionalString(json, 'closureReason'),
      closureNote: JsonReader.optionalString(json, 'closureNote'),
      closedAt: JsonReader.optionalString(json, 'closedAt'),
      previousRecordId: JsonReader.optionalString(json, 'previousRecordId'),
      effectiveCorrections: (rawCorrections as List? ?? const [])
          .map((value) {
            if (value is! Map<String, dynamic>) {
              throw const FormatException('Dữ liệu đính chính có hiệu lực không hợp lệ.');
            }
            return EffectiveRecordCorrectionDto.fromJson(value);
          })
          .toList(growable: false),
    );
  }
}

abstract final class EffectiveRecordCorrectionDto {
  static EffectiveRecordCorrection fromJson(Map<String, dynamic> json) =>
      EffectiveRecordCorrection(
        section: JsonReader.string(json, 'section'),
        originalText: JsonReader.string(json, 'originalText'),
        correctedText: JsonReader.string(json, 'correctedText'),
        reason: JsonReader.string(json, 'reason'),
        appliedAt: JsonReader.string(json, 'appliedAt'),
      );
}

abstract final class DiagnosisDto {
  static Diagnosis fromJson(Map<String, dynamic> json) => Diagnosis(
    id: JsonReader.string(json, 'diagnosisId'),
    name: JsonReader.string(json, 'diagnosisName'),
    description: JsonReader.optionalString(json, 'description'),
    icdCode: JsonReader.optionalString(json, 'icdCode'),
  );
}

abstract final class ClinicalLabOrderDto {
  static ClinicalLabOrder fromJson(Map<String, dynamic> json) {
    final episodeType = JsonReader.string(json, 'careEpisodeType');
    final serviceCode = JsonReader.optionalString(json, 'serviceCode');
    if (episodeType != 'OUTPATIENT_VISIT' && episodeType != 'ADMISSION') {
      throw const FormatException('Dữ liệu careEpisodeType không hợp lệ.');
    }
    if (serviceCode != null &&
        (serviceCode.trim().isEmpty || serviceCode.length > 32)) {
      throw const FormatException('Dữ liệu serviceCode không hợp lệ.');
    }
    return ClinicalLabOrder(
      sourceOrderId: JsonReader.string(json, 'sourceOrderId'),
      recordId: JsonReader.string(json, 'recordId'),
      patientId: JsonReader.string(json, 'patientId'),
      requestingDoctorId: JsonReader.string(json, 'requestingDoctorId'),
      requestingDepartmentId: JsonReader.string(json, 'requestingDepartmentId'),
      careEpisodeType: episodeType,
      careEpisodeId: JsonReader.string(json, 'careEpisodeId'),
      labType: JsonReader.string(json, 'labType'),
      requestedDate: JsonReader.string(json, 'requestedDate'),
      priceCode: JsonReader.string(json, 'priceCode'),
      orderedAt: JsonReader.string(json, 'orderedAt'),
      serviceCode: serviceCode,
    );
  }
}

abstract final class ClinicalLabCatalogDto {
  static ClinicalLabCatalogItem fromJson(Map<String, dynamic> json) {
    final turnaroundHours = json['turnaroundHours'];
    final serviceCode = JsonReader.string(json, 'serviceCode');
    if (turnaroundHours != null && turnaroundHours is! int) {
      throw const FormatException('Dữ liệu turnaroundHours không hợp lệ.');
    }
    final panel = json['panel'];
    if (panel is! bool) {
      throw const FormatException('Dữ liệu panel không hợp lệ.');
    }
    if (serviceCode.length > 32) {
      throw const FormatException('Dữ liệu serviceCode không hợp lệ.');
    }
    return ClinicalLabCatalogItem(
      serviceCode: serviceCode,
      name: JsonReader.string(json, 'name'),
      displayGroup: JsonReader.string(json, 'displayGroup'),
      specimenType: JsonReader.string(json, 'specimenType'),
      preparation: JsonReader.optionalString(json, 'preparation'),
      turnaroundHours: turnaroundHours as int?,
      priceCode: JsonReader.string(json, 'priceCode'),
      templateCode: JsonReader.string(json, 'templateCode'),
      panel: panel,
    );
  }
}

abstract final class LabTestReceiptDto {
  static LabTestReceipt fromJson(Map<String, dynamic> json) => LabTestReceipt(
    testId: JsonReader.string(json, 'testId'),
    status: JsonReader.string(json, 'status'),
  );
}
