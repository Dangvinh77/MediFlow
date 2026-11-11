import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/patient_timeline.dart';

class GatewayPatientAllergyRepository implements PatientAllergyRepository {
  const GatewayPatientAllergyRepository(this.client);
  final ApiClient client;

  @override
  Future<PatientAllergies> list(String patientId) async {
    _requireUuid(patientId, 'patientId');
    final value = await client.get(
      '/v2/patients/${Uri.encodeComponent(patientId)}/allergies',
    );
    if (value is! Map<String, dynamic> ||
        value['active'] is! List ||
        value['inactive'] is! List) {
      throw const FormatException('Phản hồi sổ dị ứng không hợp lệ.');
    }
    return PatientAllergies(
      active: _allergies(value['active'] as List),
      inactive: _allergies(value['inactive'] as List),
    );
  }

  @override
  Future<PatientAllergy> add(
    String patientId, {
    required String substance,
    String? reaction,
    required String severity,
    String? sourceRecordId,
    required String idempotencyKey,
  }) async {
    _requireUuid(patientId, 'patientId');
    if (sourceRecordId != null) _requireUuid(sourceRecordId, 'sourceRecordId');
    final normalized = substance.trim();
    if (normalized.isEmpty || normalized.length > 200) {
      throw ArgumentError('Tên dị nguyên phải có từ 1 đến 200 ký tự.');
    }
    if ((reaction?.trim().length ?? 0) > 500) {
      throw ArgumentError('Phản ứng không được dài quá 500 ký tự.');
    }
    if (!const {'MILD', 'MODERATE', 'SEVERE', 'UNKNOWN'}.contains(severity)) {
      throw ArgumentError('Mức độ dị ứng không hợp lệ.');
    }
    final value = await client.post(
      '/v2/patients/${Uri.encodeComponent(patientId)}/allergies',
      {
        'substance': normalized,
        'reaction': _optionalText(reaction),
        'severity': severity,
        'sourceRecordId': sourceRecordId,
      },
      idempotencyKey: idempotencyKey,
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi dị ứng không hợp lệ.');
    }
    return PatientAllergyDto.fromJson(value);
  }

  @override
  Future<PatientAllergy> changeStatus(
    String patientId,
    String allergyId, {
    required String status,
    required String reason,
    required String idempotencyKey,
  }) async {
    _requireUuid(patientId, 'patientId');
    _requireUuid(allergyId, 'allergyId');
    final normalizedReason = reason.trim();
    if (normalizedReason.isEmpty || normalizedReason.length > 500) {
      throw ArgumentError('Lý do thay đổi trạng thái phải từ 1 đến 500 ký tự.');
    }
    if (!const {'RESOLVED', 'ENTERED_IN_ERROR'}.contains(status)) {
      throw ArgumentError('Trạng thái dị ứng không hợp lệ.');
    }
    final value = await client.putIdempotent(
      '/v2/patients/${Uri.encodeComponent(patientId)}/allergies/'
      '${Uri.encodeComponent(allergyId)}/status',
      {'status': status, 'reason': normalizedReason},
      idempotencyKey: idempotencyKey,
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi dị ứng không hợp lệ.');
    }
    return PatientAllergyDto.fromJson(value);
  }

  static List<PatientAllergy> _allergies(List<dynamic> values) => values
      .map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Dữ liệu dị ứng không hợp lệ.');
        }
        return PatientAllergyDto.fromJson(item);
      })
      .toList(growable: false);

  static void _requireUuid(String value, String name) {
    if (!RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    ).hasMatch(value)) {
      throw ArgumentError('$name không hợp lệ.');
    }
  }

  static String? _optionalText(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }
}

abstract final class PatientAllergiesDto {
  static PatientAllergies fromJson(Map<String, dynamic> json) {
    if (json['active'] is! List || json['inactive'] is! List) {
      throw const FormatException('Dữ liệu sổ dị ứng không hợp lệ.');
    }
    return PatientAllergies(
      active: GatewayPatientAllergyRepository._allergies(
        json['active'] as List,
      ),
      inactive: GatewayPatientAllergyRepository._allergies(
        json['inactive'] as List,
      ),
    );
  }
}

abstract final class PatientAllergyDto {
  static PatientAllergy fromJson(Map<String, dynamic> json) => PatientAllergy(
    allergyId: JsonReader.string(json, 'allergyId'),
    substance: JsonReader.string(json, 'substance'),
    reaction: JsonReader.optionalString(json, 'reaction'),
    severity: JsonReader.string(json, 'severity'),
    status: JsonReader.string(json, 'status'),
    recordedByStaffId: JsonReader.string(json, 'recordedByStaffId'),
    recordedAt: JsonReader.string(json, 'recordedAt'),
    sourceRecordId: JsonReader.optionalString(json, 'sourceRecordId'),
    statusReason: JsonReader.optionalString(json, 'statusReason'),
    statusChangedByStaffId: JsonReader.optionalString(
      json,
      'statusChangedByStaffId',
    ),
    statusChangedAt: JsonReader.optionalString(json, 'statusChangedAt'),
  );
}
