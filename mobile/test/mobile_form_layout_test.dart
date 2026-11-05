import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/organization/domain/organization.dart';
import 'package:mediflow_mobile/features/organization/presentation/department_staff_pages.dart';
import 'package:mediflow_mobile/features/organization/presentation/providers.dart';
import 'package:mediflow_mobile/features/patient/domain/patient.dart';
import 'package:mediflow_mobile/features/patient/presentation/patient_page.dart';
import 'package:mediflow_mobile/features/patient/presentation/providers.dart';

const _departmentId = '22222222-2222-4222-8222-222222222222';
const _patientId = '33333333-3333-4333-8333-333333333333';

class _PatientRepository extends Fake implements PatientRepository {
  @override
  Future<Patient> getById(String id) async => const Patient(
    id: _patientId,
    name: 'An Nguyen',
    birthDate: '1988-02-03',
    gender: 'M',
    identityNumber: '012345678901',
    address: null,
    phoneNumber: null,
    email: null,
    insuranceNumber: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    archived: false,
  );
}

class _OrganizationRepository extends Fake implements OrganizationRepository {
  _OrganizationRepository({this.departmentName = 'Ngoại trú'});
  final String departmentName;

  @override
  Future<List<Department>> departments({bool activeOnly = false}) async => [
    Department(
      id: _departmentId,
      name: departmentName,
      abbreviation: 'NT',
      type: 'CLINICAL',
      headId: null,
      location: null,
      active: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    ),
  ];
}

Widget _narrowHost(Widget child) => MaterialApp(
  builder: (context, appChild) => MediaQuery(
    data: MediaQuery.of(context)
        .copyWith(textScaler: const TextScaler.linear(1.45)),
    child: appChild!,
  ),
  home: Scaffold(body: child),
);

void _useNarrowViewport(WidgetTester tester) {
  tester.view.physicalSize = const Size(320, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void _expectVerticalGap(WidgetTester tester, Finder first, Finder second) {
  final firstRect = tester.getRect(first);
  final secondRect = tester.getRect(second);
  expect(secondRect.top - firstRect.bottom, greaterThanOrEqualTo(11));
}

void main() {
  testWidgets(
    'patient form fields stay separated and save remains reachable on a narrow screen',
    (tester) async {
      _useNarrowViewport(tester);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            patientRepositoryProvider.overrideWithValue(_PatientRepository()),
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(sessionIdentity: Object(), role: 'ADMIN'),
            ),
          ],
          child: _narrowHost(const PatientEditorPage(onSaved: _ignoreSaved)),
        ),
      );
      await tester.pumpAndSettle();

      final fields = find.byType(TextFormField);
      _expectVerticalGap(tester, fields.at(0), fields.at(1));
      expect(tester.takeException(), isNull);
      final patientEmail = find.descendant(
        of: fields.at(4),
        matching: find.byType(TextField),
      );
      final emailTextField = tester.widget<TextField>(patientEmail);
      expect(emailTextField.maxLength, 254);
      final longEmail = '${'a' * 64}@${'b' * 63}.${'c' * 63}.${'d' * 61}';
      expect(
        tester.widget<TextFormField>(fields.at(4)).validator!(longEmail),
        isNull,
      );
      expect(
        tester.widget<TextFormField>(fields.at(4)).validator!('${longEmail}e'),
        'Email không hợp lệ.',
      );

      final save = find.text('Tạo bệnh nhân');
      await tester.scrollUntilVisible(
        save,
        180,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(save);
      await tester.pumpAndSettle();
      expect(find.text('Vui lòng nhập họ tên.'), findsOneWidget);
      await tester.scrollUntilVisible(
        save,
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.getRect(save).bottom, lessThanOrEqualTo(640));
      expect(tester.takeException(), isNull);

      tester.view.viewInsets = const FakeViewPadding(bottom: 240);
      final lastField = fields.last;
      await tester.ensureVisible(lastField);
      await tester.tap(lastField);
      await tester.showKeyboard(lastField);
      await tester.pumpAndSettle();
      expect(tester.getRect(lastField).bottom, lessThanOrEqualTo(400));
      expect(tester.takeException(), isNull);

      tester.view.viewInsets = const FakeViewPadding();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            patientRepositoryProvider.overrideWithValue(_PatientRepository()),
            workspaceAccessProvider.overrideWithValue(
              WorkspaceAccess(sessionIdentity: Object(), role: 'ADMIN'),
            ),
          ],
          child: _narrowHost(
            const PatientEditorPage(
              patientId: _patientId,
              onSaved: _ignoreSaved,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Email của tài khoản đã cấp'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'department form spacing and staff department labels fit a narrow viewport',
    (tester) async {
      _useNarrowViewport(tester);
      final longDepartmentName = List.filled(
        5,
        'Khoa chăm sóc chuyên sâu và điều trị ngoại trú',
      ).join(' ');
      final repository = _OrganizationRepository(
        departmentName: longDepartmentName,
      );
      final overrides = [
        organizationRepositoryProvider.overrideWithValue(repository),
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: Object(), role: 'ADMIN'),
        ),
      ];

      await tester.pumpWidget(
        ProviderScope(
          overrides: overrides,
          child: _narrowHost(DepartmentEditorPage(onSaved: _ignoreSaved)),
        ),
      );
      await tester.pumpAndSettle();
      final departmentFields = find.byType(TextFormField);
      _expectVerticalGap(
        tester,
        departmentFields.at(0),
        departmentFields.at(1),
      );
      expect(tester.takeException(), isNull);

      await tester.pumpWidget(
        ProviderScope(
          overrides: overrides,
          child: _narrowHost(StaffEditorPage(onSaved: _ignoreSaved)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Khoa chăm sóc chuyên sâu'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}

void _ignoreSaved() {}
