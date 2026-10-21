import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/patient.dart';
import 'patient_dto.dart';

class GatewayPatientRepository implements PatientRepository {
  const GatewayPatientRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<Patient>> search({
    String? keyword,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang không hợp lệ.');
    }
    final result = await client.get(
      '/v1/patients',
      query: {
        'page': page,
        'size': size,
        if (keyword != null && keyword.trim().isNotEmpty)
          'keyword': keyword.trim(),
      },
    );
    return decodePage(result, PatientDto.fromJson);
  }

  @override
  Future<Patient> getById(String id) async {
    _requireId(id);
    final patient = PatientDto.fromJson(
      _object(await client.get('/v1/patients/$id')),
    );
    _requireResponseId(id, patient.id);
    return patient;
  }

  @override
  Future<Patient> create(PatientDraft draft) async => PatientDto.fromJson(
    _object(await client.post('/v1/patients', PatientDto.createBody(draft))),
  );

  @override
  Future<Patient> update(String id, PatientDraft draft) async {
    _requireId(id);
    final value = await client.put(
      '/v1/patients/$id',
      PatientDto.updateBody(draft),
    );
    final patient = PatientDto.fromJson(_object(value));
    _requireResponseId(id, patient.id);
    return patient;
  }

  @override
  Future<void> delete(String id) async {
    _requireId(id);
    await client.delete('/v1/patients/$id');
  }

  static Map<String, dynamic> _object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi bệnh nhân không hợp lệ.');
    }
    return value;
  }

  static void _requireId(String id) {
    if (!isPatientId(id)) throw ArgumentError('Mã bệnh nhân không hợp lệ.');
  }

  static void _requireResponseId(String requested, String received) {
    if (requested.toLowerCase() != received.toLowerCase()) {
      throw const FormatException('Mã bệnh nhân trả về không khớp.');
    }
  }
}
