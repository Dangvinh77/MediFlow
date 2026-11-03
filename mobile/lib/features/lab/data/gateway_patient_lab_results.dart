import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/patient_lab_result.dart';

abstract final class PatientLabResultDto {
  static PatientLabResult fromJson(Map<String, dynamic> json) {
    final results = json['results'];
    if (results is! List) {
      throw const FormatException('Danh sách chỉ số xét nghiệm không hợp lệ.');
    }
    return PatientLabResult(
      id: JsonReader.string(json, 'testId'),
      recordId: JsonReader.string(json, 'recordId'),
      labType: JsonReader.string(json, 'labType'),
      requestedDate: JsonReader.string(json, 'requestedDate'),
      performedDate: JsonReader.string(json, 'performedDate'),
      conclusion: JsonReader.optionalString(json, 'conclusion'),
      results: List.unmodifiable(
        results.map((row) {
          if (row is! Map<String, dynamic>) {
            throw const FormatException('Chỉ số xét nghiệm không hợp lệ.');
          }
          return PatientLabValue(
            JsonReader.string(row, 'resultId'),
            JsonReader.string(row, 'indicator'),
            JsonReader.string(row, 'value'),
            JsonReader.optionalString(row, 'unit'),
            JsonReader.optionalString(row, 'referenceRange'),
          );
        }),
      ),
    );
  }
}

class GatewayPatientLabResults implements PatientLabResultsRepository {
  const GatewayPatientLabResults(this.client);
  final ApiClient client;
  @override
  Future<PageResult<PatientLabResult>> mine({int page = 0}) async {
    if (page < 0) throw ArgumentError.value(page, 'page');
    return decodePage(
      await client.get('/v1/lab/mine', query: {'page': page, 'size': 20}),
      PatientLabResultDto.fromJson,
    );
  }
}
