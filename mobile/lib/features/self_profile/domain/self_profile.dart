import 'dart:convert';

import '../../auth/domain/session.dart';

sealed class SelfProfile {
  const SelfProfile({required this.id, required this.fullName});

  final String id;
  final String fullName;
}

final class StaffSelfProfile extends SelfProfile {
  const StaffSelfProfile({
    required super.id,
    required super.fullName,
    required this.identityNumber,
    required this.dateOfBirth,
    required this.gender,
    required this.address,
    required this.hireDate,
    required this.qualification,
    required this.departmentId,
    required this.jobTitle,
    required this.specialization,
    required this.licenseNumber,
    required this.phoneNumber,
    required this.email,
    required this.active,
  });

  final String? identityNumber;
  final String? dateOfBirth;
  final String? gender;
  final String? address;
  final String? hireDate;
  final String? qualification;
  final String departmentId;
  final String jobTitle;
  final String? specialization;
  final String? licenseNumber;
  final String? phoneNumber;
  final String? email;
  final bool active;
}

final class PatientSelfProfile extends SelfProfile {
  const PatientSelfProfile({
    required super.id,
    required super.fullName,
    required this.dateOfBirth,
    required this.gender,
    required this.identityNumber,
    required this.insuranceNumber,
    required this.phoneNumber,
    required this.email,
    required this.address,
  });

  final String dateOfBirth;
  final String gender;
  final String identityNumber;
  final String? insuranceNumber;
  final String? phoneNumber;
  final String? email;
  final String? address;
}

class StaffContactDraft {
  const StaffContactDraft({required this.phoneNumber, required this.address});

  final String? phoneNumber;
  final String? address;

  @override
  bool operator ==(Object other) =>
      other is StaffContactDraft &&
      other.phoneNumber == phoneNumber &&
      other.address == address;

  @override
  int get hashCode => Object.hash(phoneNumber, address);
}

abstract interface class SelfProfileRepository {
  Future<StaffSelfProfile> readStaffProfile(String staffId);
  Future<PatientSelfProfile> readPatientProfile(String patientId);
  Future<String> readStaffDepartment(String staffId);
  Future<StaffSelfProfile> updateOwnStaffContact(
    String staffId,
    StaffContactDraft draft,
  );
}

class _SignedOwner {
  const _SignedOwner.patient(this.id) : isPatient = true;
  const _SignedOwner.staff(this.id) : isPatient = false;

  final String id;
  final bool isPatient;
}

_SignedOwner _signedOwner(Session session) {
  try {
    final parts = session.accessToken.split('.');
    if (parts.length != 3) throw const FormatException();
    final payload = jsonDecode(
      utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
    );
    if (payload is! Map<String, dynamic> ||
        payload['type'] != 'access' ||
        payload['role'] != session.role.wire ||
        !_isSelfProfileId(payload['sub'])) {
      throw const FormatException();
    }

    final patientId = payload['patientId'];
    final staffId = payload['staffId'];
    if (session.role == UserRole.patient) {
      if (staffId != null || !_isSelfProfileId(patientId)) {
        throw const FormatException();
      }
      return _SignedOwner.patient(patientId as String);
    }
    if (patientId != null || !_isSelfProfileId(staffId)) {
      throw const FormatException();
    }
    return _SignedOwner.staff(staffId as String);
  } on Object {
    throw ArgumentError('Không đọc được định danh hồ sơ từ phiên hiện tại.');
  }
}

bool _isSelfProfileId(Object? value) =>
    value is String &&
    RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    ).hasMatch(value);

class ReadOwnProfile {
  const ReadOwnProfile(this.repository);
  final SelfProfileRepository repository;

  Future<SelfProfile> call(Session session) async {
    final owner = _signedOwner(session);
    if (owner.isPatient) {
      final profile = await repository.readPatientProfile(owner.id);
      _verifyOwner(owner.id, profile.id);
      return profile;
    }
    final profile = await repository.readStaffProfile(owner.id);
    _verifyOwner(owner.id, profile.id);
    return profile;
  }
}

class ReadOwnStaffDepartment {
  const ReadOwnStaffDepartment(this.repository);
  final SelfProfileRepository repository;

  Future<String> call(Session session) {
    final owner = _signedOwner(session);
    if (owner.isPatient) {
      throw ArgumentError('Hồ sơ bệnh nhân không có khoa công tác.');
    }
    return repository.readStaffDepartment(owner.id);
  }
}

class UpdateOwnStaffContact {
  const UpdateOwnStaffContact(this.repository);
  final SelfProfileRepository repository;

  Future<StaffSelfProfile> call(
    Session session,
    StaffContactDraft draft,
  ) async {
    final owner = _signedOwner(session);
    if (owner.isPatient) {
      throw ArgumentError('Bệnh nhân không thể sửa thông tin nhân viên.');
    }
    final normalized = _normalize(draft);
    final updated = await repository.updateOwnStaffContact(
      owner.id,
      normalized,
    );
    _verifyOwner(owner.id, updated.id);
    return updated;
  }

  StaffContactDraft _normalize(StaffContactDraft draft) {
    final phone = draft.phoneNumber?.trim();
    if (phone != null &&
        phone.isNotEmpty &&
        !RegExp(r'^\d{10,15}$').hasMatch(phone)) {
      throw ArgumentError('Số điện thoại phải gồm 10 đến 15 chữ số.');
    }
    final address = draft.address?.trim();
    if ((address?.length ?? 0) > 255) {
      throw ArgumentError('Địa chỉ tối đa 255 ký tự.');
    }
    return StaffContactDraft(
      phoneNumber: phone == null || phone.isEmpty ? null : phone,
      address: address == null || address.isEmpty ? null : address,
    );
  }
}

void _verifyOwner(String requestedId, String receivedId) {
  if (requestedId.toLowerCase() != receivedId.toLowerCase()) {
    throw const FormatException('Hồ sơ trả về không thuộc phiên hiện tại.');
  }
}
