import 'organization.dart';

enum ProfileAccountOwnerType { staff, patient }

class ProfileAccountOwner {
  const ProfileAccountOwner.staff({
    required this.id,
    required this.jobTitle,
    required this.active,
  }) : type = ProfileAccountOwnerType.staff;

  const ProfileAccountOwner.patient({required this.id, this.active = true})
    : type = ProfileAccountOwnerType.patient,
      jobTitle = null;

  final ProfileAccountOwnerType type;
  final String id;
  final String? jobTitle;
  final bool active;

  bool get isPatient => type == ProfileAccountOwnerType.patient;
}

class ProfileAccountSnapshot {
  const ProfileAccountSnapshot({
    required this.account,
    required this.profileEmail,
    required this.emailSyncPending,
  });

  final Account? account;
  final String? profileEmail;
  final bool emailSyncPending;
}

enum ProfileMailStatus { pending, sent, failed }

class ProfileMailDelivery {
  const ProfileMailDelivery({required this.messageId, required this.status});

  final String messageId;
  final ProfileMailStatus status;
}

abstract interface class ProfileAccountRepository {
  Future<ProfileAccountSnapshot> staffProfile(String staffId);
  Future<ProfileAccountSnapshot> patientProfile(String patientId);

  Future<void> issueStaffAccount({
    required String staffId,
    required String username,
    required String role,
    required String idempotencyKey,
  });
  Future<void> issuePatientAccount({
    required String patientId,
    required String idempotencyKey,
  });

  Future<ProfileMailDelivery> resetStaffPassword(
    String accountId,
    String idempotencyKey,
  );
  Future<ProfileMailDelivery> resetPatientPassword(
    String patientId,
    String idempotencyKey,
  );
  Future<ProfileMailDelivery?> latestStaffMailDelivery(String accountId);
  Future<ProfileMailDelivery?> latestPatientMailDelivery(String patientId);
  Future<ProfileMailDelivery> retryStaffMailDelivery(
    String accountId,
    String messageId,
  );
  Future<ProfileMailDelivery> retryPatientMailDelivery(
    String patientId,
    String messageId,
  );
  Future<void> updateStaffEmail({
    required String accountId,
    required String email,
    required String reason,
  });
  Future<void> updatePatientEmail({
    required String patientId,
    required String email,
    required String reason,
  });
}

class ReadProfileAccount {
  const ReadProfileAccount(this.repository);

  final ProfileAccountRepository repository;

  Future<ProfileAccountSnapshot> staff(String staffId) {
    _requireId(staffId, 'nhân viên');
    return repository.staffProfile(staffId);
  }

  Future<ProfileAccountSnapshot> patient(String patientId) {
    _requireId(patientId, 'bệnh nhân');
    return repository.patientProfile(patientId);
  }
}

class IssueStaffProfileAccount {
  const IssueStaffProfileAccount(this.repository);

  final ProfileAccountRepository repository;

  Future<void> call({
    required String staffId,
    required String username,
    required String role,
    required String idempotencyKey,
  }) {
    _requireId(staffId, 'nhân viên');
    _requireEmail(username);
    if (!accountRoles.contains(role) || role == 'PATIENT' || role == 'SYSTEM') {
      throw ArgumentError('Vai trò nhân viên không hợp lệ.');
    }
    _requireIdempotencyKey(idempotencyKey);
    return repository.issueStaffAccount(
      staffId: staffId,
      username: username.trim().toLowerCase(),
      role: role,
      idempotencyKey: idempotencyKey,
    );
  }
}

class IssuePatientProfileAccount {
  const IssuePatientProfileAccount(this.repository);

  final ProfileAccountRepository repository;

  Future<void> call({
    required String patientId,
    required String idempotencyKey,
  }) {
    _requireId(patientId, 'bệnh nhân');
    _requireIdempotencyKey(idempotencyKey);
    return repository.issuePatientAccount(
      patientId: patientId,
      idempotencyKey: idempotencyKey,
    );
  }
}

class ResetProfilePassword {
  const ResetProfilePassword(this.repository);

  final ProfileAccountRepository repository;

  Future<ProfileMailDelivery> call({
    required ProfileAccountOwner owner,
    required String accountId,
    required String idempotencyKey,
  }) {
    _requireIdempotencyKey(idempotencyKey);
    if (owner.isPatient) {
      _requireId(owner.id, 'bệnh nhân');
      return repository.resetPatientPassword(owner.id, idempotencyKey);
    }
    _requireId(accountId, 'tài khoản');
    return repository.resetStaffPassword(accountId, idempotencyKey);
  }
}

class UpdateStaffLoginEmail {
  const UpdateStaffLoginEmail(this.repository);

  final ProfileAccountRepository repository;

  Future<void> call({
    required String accountId,
    required String email,
    required String reason,
  }) {
    _requireId(accountId, 'tài khoản');
    _requireEmail(email);
    if (reason.trim().isEmpty || reason.trim().length > 500) {
      throw ArgumentError('Lý do thay đổi email phải có từ 1 đến 500 ký tự.');
    }
    return repository.updateStaffEmail(
      accountId: accountId,
      email: email.trim().toLowerCase(),
      reason: reason.trim(),
    );
  }
}

class UpdatePatientLoginEmail {
  const UpdatePatientLoginEmail(this.repository);

  final ProfileAccountRepository repository;

  Future<void> call({
    required String patientId,
    required String email,
    required String reason,
  }) {
    _requireId(patientId, 'bệnh nhân');
    _requireEmail(email);
    if (reason.trim().isEmpty || reason.trim().length > 500) {
      throw ArgumentError('Lý do thay đổi email phải có từ 1 đến 500 ký tự.');
    }
    return repository.updatePatientEmail(
      patientId: patientId,
      email: email.trim().toLowerCase(),
      reason: reason.trim(),
    );
  }
}

bool isProfileAccountEmail(String value) =>
    value.trim().length <= 254 &&
    RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(value.trim());

String? profileAccountRoleForJobTitle(String? jobTitle) => switch (jobTitle) {
  'DOCTOR' => 'DOCTOR',
  'NURSE' => 'NURSE',
  'TECHNICIAN' => 'LAB_TECH',
  'PHARMACIST' => 'PHARMACIST',
  'CASHIER' => 'CASHIER',
  'ADMINISTRATIVE' => 'ADMIN',
  _ => null,
};

void _requireId(String value, String label) {
  if (!isOrganizationId(value)) throw ArgumentError('Mã $label không hợp lệ.');
}

void _requireEmail(String value) {
  if (!isProfileAccountEmail(value)) throw ArgumentError('Email không hợp lệ.');
}

void _requireIdempotencyKey(String value) {
  if (value.isEmpty || value.contains(RegExp(r'[\r\n]'))) {
    throw ArgumentError('Idempotency-Key không hợp lệ.');
  }
}
