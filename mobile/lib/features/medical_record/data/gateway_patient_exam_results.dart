import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/patient_exam_result.dart';

abstract final class PatientExamResultDto {
  static PatientExamResult fromJson(Map<String, dynamic> json) {
    final diagnoses = json['diagnoses'];
    if (diagnoses is! List) {
      throw const FormatException('Danh sách chẩn đoán không hợp lệ.');
    }
    final corrections = json['effectiveCorrections'];
    if (corrections != null && corrections is! List) {
      throw const FormatException('Danh sách đính chính có hiệu lực không hợp lệ.');
    }
    return PatientExamResult(
      id: JsonReader.string(json, 'recordId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      examinationDate: JsonReader.string(json, 'examinationDate'),
      completedAt: JsonReader.string(json, 'completedAt'),
      symptoms: JsonReader.optionalString(json, 'symptoms'),
      disposition: JsonReader.optionalString(json, 'disposition'),
      dispositionNote: JsonReader.optionalString(json, 'dispositionNote'),
      effectiveCorrections: (corrections as List? ?? const [])
          .map((row) {
            if (row is! Map<String, dynamic>) {
              throw const FormatException('Đính chính có hiệu lực không hợp lệ.');
            }
            return PatientEffectiveCorrection(
              section: JsonReader.string(row, 'section'),
              originalText: JsonReader.string(row, 'originalText'),
              correctedText: JsonReader.string(row, 'correctedText'),
              reason: JsonReader.string(row, 'reason'),
              appliedAt: JsonReader.string(row, 'appliedAt'),
            );
          })
          .toList(growable: false),
      diagnoses: List.unmodifiable(
        diagnoses.map((row) {
          if (row is! Map<String, dynamic>) {
            throw const FormatException('Chẩn đoán không hợp lệ.');
          }
          return PatientDiagnosis(
            JsonReader.string(row, 'diagnosisId'),
            JsonReader.string(row, 'diagnosisName'),
            JsonReader.optionalString(row, 'description'),
            JsonReader.optionalString(row, 'icdCode'),
          );
        }),
      ),
    );
  }
}

class GatewayPatientExamResults implements PatientExamResultsRepository {
  const GatewayPatientExamResults(this.client);
  final ApiClient client;
  @override
  Future<PageResult<PatientExamResult>> mine({int page = 0}) async {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return decodePage(
      await client.get('/v1/records/mine', query: {'page': page, 'size': 20}),
      PatientExamResultDto.fromJson,
    );
  }
}
