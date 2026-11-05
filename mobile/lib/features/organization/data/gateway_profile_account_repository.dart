import '../../../core/network/api_client.dart';
import '../../../core/network/api_failure.dart';
import '../domain/organization.dart';
import '../domain/profile_account.dart';
import 'organization_dto.dart';
import 'profile_account_dto.dart';

class GatewayProfileAccountRepository implements ProfileAccountRepository {
  const GatewayProfileAccountRepository(this.client);

  final ApiClient client;

  @override
  Future<ProfileAccountSnapshot> staffProfile(String staffId) async {
    _requireId(staffId, 'nhân viên');
    final profile = ProfileAccountDto.profile(
      await client.get('/v1/org/profile-accounts/staff/$staffId'),
    );
    final account = profile.account;
    if (account != null &&
        (account.staffId?.toLowerCase() != staffId.toLowerCase() ||
            account.patientId != null ||
            !accountRoles.contains(account.role) ||
            account.role == 'PATIENT')) {
      throw const FormatException(
        'Tài khoản trả về không thuộc hồ sơ nhân viên được yêu cầu.',
      );
    }
    return profile;
  }

  @override
  Future<ProfileAccountSnapshot> patientProfile(String patientId) async {
    _requireId(patientId, 'bệnh nhân');
    final profile = ProfileAccountDto.profile(
      await client.get('/v1/org/profile-accounts/patient/$patientId'),
    );
    final account = profile.account;
    if (account != null &&
        (account.patientId?.toLowerCase() != patientId.toLowerCase() ||
            account.staffId != null ||
            account.role != 'PATIENT')) {
      throw const FormatException(
        'Tài khoản trả về không thuộc hồ sơ bệnh nhân được yêu cầu.',
      );
    }
    return profile;
  }

  @override
  Future<void> issueStaffAccount({
    required String staffId,
    required String username,
    required String role,
    required String idempotencyKey,
  }) async {
    _requireId(staffId, 'nhân viên');
    await client.post('/v1/org/accounts', {
      'username': username.trim().toLowerCase(),
      'staffId': staffId,
      'patientId': null,
      'role': role,
    }, idempotencyKey: idempotencyKey);
  }

  @override
  Future<void> issuePatientAccount({
    required String patientId,
    required String idempotencyKey,
  }) async {
    _requireId(patientId, 'bệnh nhân');
    await client.post('/v1/org/patient-accounts', {
      'patientId': patientId,
    }, idempotencyKey: idempotencyKey);
  }

  @override
  Future<ProfileMailDelivery> resetStaffPassword(
    String accountId,
    String idempotencyKey,
  ) async {
    _requireId(accountId, 'tài khoản');
    return ProfileAccountDto.mailDelivery(
      await client.putIdempotent(
        '/v1/org/accounts/$accountId/password',
        const <String, Object?>{},
        idempotencyKey: idempotencyKey,
      ),
    );
  }

  @override
  Future<ProfileMailDelivery> resetPatientPassword(
    String patientId,
    String idempotencyKey,
  ) async {
    _requireId(patientId, 'bệnh nhân');
    return ProfileAccountDto.mailDelivery(
      await client.putIdempotent(
        '/v1/org/patient-accounts/$patientId/password',
        const <String, Object?>{},
        idempotencyKey: idempotencyKey,
      ),
    );
  }

  @override
  Future<ProfileMailDelivery?> latestStaffMailDelivery(String accountId) async {
    _requireId(accountId, 'tài khoản');
    try {
      return ProfileAccountDto.mailDelivery(
        await client.get('/v1/org/accounts/$accountId/mail-delivery'),
      );
    } on ApiFailure catch (error) {
      if (error.status == 404) return null;
      rethrow;
    }
  }

  @override
  Future<ProfileMailDelivery?> latestPatientMailDelivery(
    String patientId,
  ) async {
    _requireId(patientId, 'bệnh nhân');
    try {
      return ProfileAccountDto.mailDelivery(
        await client.get('/v1/org/patient-accounts/$patientId/mail-delivery'),
      );
    } on ApiFailure catch (error) {
      if (error.status == 404) return null;
      rethrow;
    }
  }

  @override
  Future<ProfileMailDelivery> retryStaffMailDelivery(
    String accountId,
    String messageId,
  ) async {
    _requireId(accountId, 'tài khoản');
    _requireMessageId(messageId);
    return ProfileAccountDto.mailDelivery(
      await client.post(
        '/v1/org/accounts/$accountId/mail-delivery/${Uri.encodeComponent(messageId)}/retry',
        const <String, Object?>{},
      ),
    );
  }

  @override
  Future<ProfileMailDelivery> retryPatientMailDelivery(
    String patientId,
    String messageId,
  ) async {
    _requireId(patientId, 'bệnh nhân');
    _requireMessageId(messageId);
    return ProfileAccountDto.mailDelivery(
      await client.post(
        '/v1/org/patient-accounts/$patientId/mail-delivery/${Uri.encodeComponent(messageId)}/retry',
        const <String, Object?>{},
      ),
    );
  }

  @override
  Future<void> updateStaffEmail({
    required String accountId,
    required String email,
    required String reason,
  }) async {
    _requireId(accountId, 'tài khoản');
    final result = OrganizationDto.account(
      await client.put('/v1/org/accounts/$accountId/email', {
        'email': email.trim().toLowerCase(),
        'reason': reason.trim(),
      }),
    );
    if (result.id.toLowerCase() != accountId.toLowerCase()) {
      throw const FormatException('Mã tài khoản trả về không khớp.');
    }
  }

  static void _requireId(String value, String label) {
    if (!isOrganizationId(value)) {
      throw ArgumentError('Mã $label không hợp lệ.');
    }
  }

  @override
  Future<void> updatePatientEmail({
    required String patientId,
    required String email,
    required String reason,
  }) async {
    _requireId(patientId, 'bệnh nhân');
    final result = OrganizationDto.account(
      await client.put('/v1/org/patient-accounts/$patientId/email', {
        'email': email.trim().toLowerCase(),
        'reason': reason.trim(),
      }),
    );
    if (result.patientId?.toLowerCase() != patientId.toLowerCase() ||
        result.staffId != null ||
        result.role != 'PATIENT' ||
        result.username.toLowerCase() != email.trim().toLowerCase()) {
      throw const FormatException(
        'Tài khoản trả về không khớp hồ sơ bệnh nhân.',
      );
    }
  }

  static void _requireMessageId(String value) {
    if (value.isEmpty || value.contains(RegExp(r'[\r\n]'))) {
      throw ArgumentError('Mã email không hợp lệ.');
    }
  }
}
