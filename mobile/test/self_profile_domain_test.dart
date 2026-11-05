import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/self_profile/domain/self_profile.dart';

const staffId = '11111111-1111-4111-8111-111111111111';
const otherStaffId = '22222222-2222-4222-8222-222222222222';
const patientId = '33333333-3333-4333-8333-333333333333';

Session sessionFor(
  UserRole role, {
  String? staffIdClaim,
  String? patientIdClaim,
}) {
  final payload = base64Url
      .encode(
        utf8.encode(
          jsonEncode({
            'type': 'access',
            'role': role.wire,
            'sub': '99999999-9999-4999-8999-999999999999',
            'staffId': staffIdClaim,
            'patientId': patientIdClaim,
          }),
        ),
      )
      .replaceAll('=', '');
  return Session(
    accessToken: 'header.$payload.signature',
    refreshToken: 'refresh',
    role: role,
  );
}

StaffSelfProfile staffProfile({String id = staffId}) => StaffSelfProfile(
  id: id,
  fullName: 'Nguyen Van An',
  identityNumber: '012345678901',
  dateOfBirth: '1988-02-03',
  gender: 'MALE',
  address: 'Hanoi',
  hireDate: '2020-01-02',
  qualification: 'BSc',
  departmentId: '44444444-4444-4444-8444-444444444444',
  jobTitle: 'DOCTOR',
  specialization: 'Internal medicine',
  licenseNumber: 'LIC-1',
  phoneNumber: '0912345678',
  email: 'an@example.org',
  active: true,
);

PatientSelfProfile patientProfile({String id = patientId}) =>
    PatientSelfProfile(
      id: id,
      fullName: 'Tran Thi Binh',
      dateOfBirth: '1990-07-08',
      gender: 'F',
      identityNumber: '123456789012',
      insuranceNumber: '12-12345678-1',
      phoneNumber: '0987654321',
      email: 'binh@example.org',
      address: 'Hue',
    );

class SelfProfileStub implements SelfProfileRepository {
  final staffReads = <String>[];
  final patientReads = <String>[];
  final departmentReads = <String>[];
  final contactUpdates = <(String, StaffContactDraft)>[];
  StaffSelfProfile staffValue = staffProfile();
  PatientSelfProfile patientValue = patientProfile();
  StaffSelfProfile updatedStaffValue = staffProfile();

  @override
  Future<StaffSelfProfile> readStaffProfile(String id) async {
    staffReads.add(id);
    return staffValue;
  }

  @override
  Future<PatientSelfProfile> readPatientProfile(String id) async {
    patientReads.add(id);
    return patientValue;
  }

  @override
  Future<String> readStaffDepartment(String id) async {
    departmentReads.add(id);
    return 'Khoa Nội';
  }

  @override
  Future<StaffSelfProfile> updateOwnStaffContact(
    String id,
    StaffContactDraft draft,
  ) async {
    contactUpdates.add((id, draft));
    return updatedStaffValue;
  }
}

void main() {
  test('reads staff profile only by the signed staff claim', () async {
    final repository = SelfProfileStub();
    final profile = await ReadOwnProfile(repository)(
      sessionFor(UserRole.doctor, staffIdClaim: staffId),
    );

    expect(profile, isA<StaffSelfProfile>());
    expect(repository.staffReads, [staffId]);
    expect(repository.patientReads, isEmpty);
  });

  test('reads patient profile only by the signed patient claim', () async {
    final repository = SelfProfileStub();
    final profile = await ReadOwnProfile(repository)(
      sessionFor(UserRole.patient, patientIdClaim: patientId),
    );

    expect(profile, isA<PatientSelfProfile>());
    expect(repository.patientReads, [patientId]);
    expect(repository.staffReads, isEmpty);
  });

  test('rejects a role and profile-claim mismatch before reading', () async {
    final repository = SelfProfileStub();

    await expectLater(
      ReadOwnProfile(repository)(
        sessionFor(UserRole.patient, staffIdClaim: staffId),
      ),
      throwsArgumentError,
    );
    expect(repository.staffReads, isEmpty);
    expect(repository.patientReads, isEmpty);
  });

  test(
    'rejects a response whose owner differs from the signed owner',
    () async {
      final repository = SelfProfileStub()
        ..staffValue = staffProfile(id: otherStaffId);

      await expectLater(
        ReadOwnProfile(repository)(
          sessionFor(UserRole.doctor, staffIdClaim: staffId),
        ),
        throwsFormatException,
      );
      expect(repository.staffReads, [staffId]);
    },
  );

  test('updates only phone and address for the signed staff owner', () async {
    final repository = SelfProfileStub();
    final updated = await UpdateOwnStaffContact(repository)(
      sessionFor(UserRole.nurse, staffIdClaim: staffId),
      const StaffContactDraft(phoneNumber: ' 0987654321 ', address: ' Hue '),
    );

    expect(updated, isA<StaffSelfProfile>());
    expect(repository.contactUpdates, [
      (
        staffId,
        const StaffContactDraft(phoneNumber: '0987654321', address: 'Hue'),
      ),
    ]);
  });

  test('patient self profiles cannot update staff contact fields', () async {
    final repository = SelfProfileStub();

    await expectLater(
      UpdateOwnStaffContact(repository)(
        sessionFor(UserRole.patient, patientIdClaim: patientId),
        const StaffContactDraft(phoneNumber: null, address: null),
      ),
      throwsArgumentError,
    );
    expect(repository.contactUpdates, isEmpty);
  });

  test('invalid phone or address is rejected before the write', () async {
    final repository = SelfProfileStub();
    final update = UpdateOwnStaffContact(repository);
    final session = sessionFor(UserRole.doctor, staffIdClaim: staffId);

    await expectLater(
      update(
        session,
        const StaffContactDraft(phoneNumber: '1234', address: 'Hanoi'),
      ),
      throwsArgumentError,
    );
    await expectLater(
      update(session, StaffContactDraft(phoneNumber: null, address: 'x' * 256)),
      throwsArgumentError,
    );
    expect(repository.contactUpdates, isEmpty);
  });

  test('staff department reads also use the signed staff owner', () async {
    final repository = SelfProfileStub();
    final department = await ReadOwnStaffDepartment(repository)(
      sessionFor(UserRole.manager, staffIdClaim: staffId),
    );

    expect(department, 'Khoa Nội');
    expect(repository.departmentReads, [staffId]);
  });
}
