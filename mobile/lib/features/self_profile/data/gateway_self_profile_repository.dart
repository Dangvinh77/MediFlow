import '../../../core/network/api_client.dart';
import '../domain/self_profile.dart';
import 'self_profile_dto.dart';

class GatewaySelfProfileRepository implements SelfProfileRepository {
  const GatewaySelfProfileRepository(this.client);

  final ApiClient client;

  @override
  Future<StaffSelfProfile> readStaffProfile(String staffId) async {
    _requireId(staffId);
    final profile = SelfProfileDto.staff(
      await client.get('/v1/org/staff/$staffId'),
    );
    _verifyOwner(staffId, profile.id);
    return profile;
  }

  @override
  Future<PatientSelfProfile> readPatientProfile(String patientId) async {
    _requireId(patientId);
    final profile = SelfProfileDto.patient(
      await client.get('/v1/patients/$patientId'),
    );
    _verifyOwner(patientId, profile.id);
    return profile;
  }

  @override
  Future<String> readStaffDepartment(String staffId) async {
    _requireId(staffId);
    final response = await client.get('/v1/org/staff/$staffId/department');
    if (response is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi đơn vị công tác không hợp lệ.');
    }
    final receivedId = response['staffId'];
    if (receivedId != null && receivedId is! String) {
      throw const FormatException('Dữ liệu staffId không hợp lệ.');
    }
    if (receivedId is String) _verifyOwner(staffId, receivedId);
    return SelfProfileDto.staffDepartment(response);
  }

  @override
  Future<StaffSelfProfile> updateOwnStaffContact(
    String staffId,
    StaffContactDraft draft,
  ) async {
    _requireId(staffId);
    final profile = SelfProfileDto.staff(
      await client.put(
        '/v1/org/staff/$staffId/contact',
        SelfProfileDto.ownStaffContactBody(draft),
      ),
    );
    _verifyOwner(staffId, profile.id);
    return profile;
  }

  static void _verifyOwner(String requestedId, String receivedId) {
    if (requestedId.toLowerCase() != receivedId.toLowerCase()) {
      throw const FormatException('Hồ sơ trả về không thuộc yêu cầu hiện tại.');
    }
  }

  static void _requireId(String id) {
    if (!RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    ).hasMatch(id)) {
      throw ArgumentError('Mã hồ sơ không hợp lệ.');
    }
  }
}
