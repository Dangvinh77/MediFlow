import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_loader/mediflow_loader.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';
import 'package:mediflow_mobile/features/self_profile/domain/self_profile.dart';
import 'package:mediflow_mobile/features/self_profile/presentation/providers.dart';

import 'session_controller_test.dart' show MemoryAuth;

const staffId = '11111111-1111-4111-8111-111111111111';
const otherStaffId = '22222222-2222-4222-8222-222222222222';
const patientId = '33333333-3333-4333-8333-333333333333';

Session profileSession(
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

StaffSelfProfile staffProfile({
  String id = staffId,
  String fullName = 'Nguyen Van An',
}) => StaffSelfProfile(
  id: id,
  fullName: fullName,
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

PatientSelfProfile patientProfile() => const PatientSelfProfile(
  id: patientId,
  fullName: 'Tran Thi Binh',
  dateOfBirth: '1990-07-08',
  gender: 'F',
  identityNumber: '123456789012',
  insuranceNumber: '12-12345678-1',
  phoneNumber: '0987654321',
  email: 'binh@example.org',
  address: 'Hue',
);

class SelfProfileFake implements SelfProfileRepository {
  final staffReads = <String>[];
  final patientReads = <String>[];
  final departmentReads = <String>[];
  final contactUpdates = <(String, StaffContactDraft)>[];
  Future<StaffSelfProfile> Function(String id)? staffRead;
  StaffSelfProfile staffValue = staffProfile();
  PatientSelfProfile patientValue = patientProfile();
  String departmentName = 'Khoa Nội';

  @override
  Future<StaffSelfProfile> readStaffProfile(String id) {
    staffReads.add(id);
    return staffRead?.call(id) ?? Future.value(staffValue);
  }

  @override
  Future<PatientSelfProfile> readPatientProfile(String id) async {
    patientReads.add(id);
    return patientValue;
  }

  @override
  Future<String> readStaffDepartment(String id) async {
    departmentReads.add(id);
    return departmentName;
  }

  @override
  Future<StaffSelfProfile> updateOwnStaffContact(
    String id,
    StaffContactDraft draft,
  ) async {
    contactUpdates.add((id, draft));
    staffValue = StaffSelfProfile(
      id: staffValue.id,
      fullName: staffValue.fullName,
      identityNumber: staffValue.identityNumber,
      dateOfBirth: staffValue.dateOfBirth,
      gender: staffValue.gender,
      address: draft.address,
      hireDate: staffValue.hireDate,
      qualification: staffValue.qualification,
      departmentId: staffValue.departmentId,
      jobTitle: staffValue.jobTitle,
      specialization: staffValue.specialization,
      licenseNumber: staffValue.licenseNumber,
      phoneNumber: draft.phoneNumber,
      email: staffValue.email,
      active: staffValue.active,
    );
    return staffValue;
  }
}

void main() {
  for (final role in UserRole.values) {
    testWidgets('shows a self-profile destination for ${role.wire}', (
      tester,
    ) async {
      final auth = MemoryAuth()
        ..saved = Session(
          accessToken: 'access-${role.wire}',
          refreshToken: 'refresh',
          role: role,
        );
      final container = ProviderContainer(
        overrides: [authRepositoryProvider.overrideWithValue(auth)],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byTooltip('Hồ sơ cá nhân'), findsOneWidget);
    });
  }

  testWidgets(
    'staff can save only their phone and address, with email read-only',
    (tester) async {
      final auth = MemoryAuth()
        ..saved = profileSession(UserRole.nurse, staffIdClaim: staffId);
      final profile = SelfProfileFake();
      final container = ProviderContainer(
        overrides: [
          authRepositoryProvider.overrideWithValue(auth),
          selfProfileRepositoryProvider.overrideWithValue(profile),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Hồ sơ cá nhân'));
      await tester.pumpAndSettle();

      expect(find.text('Nguyen Van An'), findsOneWidget);
      expect(find.text('Khoa Nội'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('self-profile-phone')),
        320,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.enterText(
        find.byKey(const ValueKey('self-profile-phone')),
        '0987654321',
      );
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('self-profile-email')),
        240,
        scrollable: find.byType(Scrollable).last,
      );
      final email = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const ValueKey('self-profile-email')),
          matching: find.byType(EditableText),
        ),
      );
      expect(email.readOnly, isTrue);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('self-profile-address')),
        240,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.enterText(
        find.byKey(const ValueKey('self-profile-address')),
        'Hue',
      );
      await tester.scrollUntilVisible(
        find.text('Lưu thông tin liên hệ'),
        320,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.tap(find.text('Lưu thông tin liên hệ'));
      await tester.pumpAndSettle();

      expect(profile.contactUpdates, [
        (
          staffId,
          const StaffContactDraft(phoneNumber: '0987654321', address: 'Hue'),
        ),
      ]);
      expect(find.text('Đã cập nhật thông tin liên hệ.'), findsOneWidget);
      expect(profile.patientReads, isEmpty);
    },
  );

  testWidgets('patient self profile stays read-only and uses patient claim', (
    tester,
  ) async {
    final auth = MemoryAuth()
      ..saved = profileSession(UserRole.patient, patientIdClaim: patientId);
    final profile = SelfProfileFake();
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(auth),
        selfProfileRepositoryProvider.overrideWithValue(profile),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MediFlowApp(),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Hồ sơ cá nhân'));
    await tester.pumpAndSettle();

    expect(find.text('Tran Thi Binh'), findsOneWidget);
    expect(find.text('Hồ sơ bệnh nhân của bạn'), findsOneWidget);
    expect(find.text('0987654321'), findsOneWidget);
    expect(find.byType(TextFormField), findsNothing);
    expect(find.text('Lưu thông tin liên hệ'), findsNothing);
    expect(profile.patientReads, [patientId]);
    expect(profile.staffReads, isEmpty);
    expect(profile.contactUpdates, isEmpty);
  });

  testWidgets(
    'does not render another staff member when the owner mismatches',
    (tester) async {
      final auth = MemoryAuth()
        ..saved = profileSession(UserRole.doctor, staffIdClaim: staffId);
      final profile = SelfProfileFake()
        ..staffValue = staffProfile(id: otherStaffId);
      final container = ProviderContainer(
        overrides: [
          authRepositoryProvider.overrideWithValue(auth),
          selfProfileRepositoryProvider.overrideWithValue(profile),
        ],
      );
      addTearDown(container.dispose);
      await container.read(sessionProvider.future);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MediFlowApp(),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Hồ sơ cá nhân'));
      await tester.pumpAndSettle();

      expect(
        find.text('Hồ sơ trả về không thuộc phiên hiện tại.'),
        findsOneWidget,
      );
      expect(find.text('Nguyen Van An'), findsNothing);
      expect(find.text('Lưu thông tin liên hệ'), findsNothing);
      expect(profile.staffReads, [staffId]);
    },
  );

  testWidgets('late profile response is ignored after the session ends', (
    tester,
  ) async {
    final auth = MemoryAuth()
      ..saved = profileSession(UserRole.doctor, staffIdClaim: staffId);
    final pendingRead = Completer<StaffSelfProfile>();
    final profile = SelfProfileFake()..staffRead = (_) => pendingRead.future;
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(auth),
        selfProfileRepositoryProvider.overrideWithValue(profile),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MediFlowApp(),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Hồ sơ cá nhân'));
    await tester.pump();
    expect(find.byType(MediFlowLoader), findsOneWidget);

    await container.read(sessionProvider.notifier).signOut();
    await tester.pumpAndSettle();
    pendingRead.complete(staffProfile());
    await tester.pumpAndSettle();

    expect(find.text('Nguyen Van An'), findsNothing);
    expect(find.text('Hồ sơ cá nhân'), findsNothing);
  });

  testWidgets('self profile fits a 320 pixel viewport', (tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final auth = MemoryAuth()
      ..saved = profileSession(UserRole.doctor, staffIdClaim: staffId);
    final profile = SelfProfileFake();
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(auth),
        selfProfileRepositoryProvider.overrideWithValue(profile),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MediFlowApp(),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Hồ sơ cá nhân'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('Lưu thông tin liên hệ'),
      320,
      scrollable: find.byType(Scrollable).last,
    );
    expect(tester.takeException(), isNull);
    expect(find.text('Lưu thông tin liên hệ'), findsOneWidget);
  });
}
