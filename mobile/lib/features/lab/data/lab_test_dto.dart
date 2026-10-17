import '../../../core/network/json_reader.dart';
import '../domain/lab_test.dart';

abstract final class LabTestDto {
  static LabTest fromJson(Map<String, dynamic> json) {
    if (json['paid'] is! bool) {
      throw const FormatException('Dữ liệu paid không hợp lệ.');
    }
    return LabTest(
      id: JsonReader.string(json, 'testId'),
      patientId: JsonReader.string(json, 'patientId'),
      recordId: JsonReader.string(json, 'recordId'),
      type: JsonReader.string(json, 'labType'),
      requestedDate: JsonReader.string(json, 'requestedDate'),
      status: JsonReader.string(json, 'status'),
      paid: json['paid'] as bool,
    );
  }
}
