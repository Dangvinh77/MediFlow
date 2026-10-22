import '../../../core/network/json_reader.dart';
import '../domain/medical_record.dart';

abstract final class MedicalRecordDto {
  static MedicalRecord fromJson(Map<String, dynamic> json) {
    final rawDiagnoses = json['diagnoses'];
    if (rawDiagnoses is! List) {
      throw const FormatException('Dữ liệu diagnoses không hợp lệ.');
    }
    return MedicalRecord(
      id: JsonReader.string(json, 'recordId'),
      patientId: JsonReader.string(json, 'patientId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      examinationDate: JsonReader.string(json, 'examinationDate'),
      symptoms: JsonReader.optionalString(json, 'symptoms'),
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
    );
  }
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
    if (episodeType != 'OUTPATIENT_VISIT' && episodeType != 'ADMISSION') {
      throw const FormatException('Dữ liệu careEpisodeType không hợp lệ.');
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
    );
  }
}

abstract final class LabTestReceiptDto {
  static LabTestReceipt fromJson(Map<String, dynamic> json) => LabTestReceipt(
    testId: JsonReader.string(json, 'testId'),
    status: JsonReader.string(json, 'status'),
  );
}
