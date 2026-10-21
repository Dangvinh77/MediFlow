import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/organization/data/gateway_organization_repository.dart';
import 'package:mediflow_mobile/features/organization/data/organization_dto.dart';
import 'package:mediflow_mobile/features/organization/domain/organization.dart';
import 'package:mediflow_mobile/features/organization/presentation/department_staff_pages.dart';
import 'package:mediflow_mobile/features/organization/presentation/organization_page.dart';
import 'package:mediflow_mobile/features/organization/presentation/providers.dart';

const departmentId = '22222222-2222-4222-8222-222222222222';
const departmentId2 = '33333333-3333-4333-8333-333333333333';
const staffId = '44444444-4444-4444-8444-444444444444';
const accountId = '55555555-5555-4555-8555-555555555555';

Map<String, dynamic> departmentJson({
  String id = departmentId,
  String name = 'Ngoại trú',
}) => {
  'departmentId': id,
  'departmentName': name,
  'abbreviation': 'NT',
  'departmentType': 'CLINICAL',
  'departmentHeadId': null,
  'location': 'Tầng 1',
  'active': true,
  'createdAt': '2026-01-01T10:00:00Z',
  'updatedAt': '2026-01-02T10:00:00Z',
};

Map<String, dynamic> staffJson({String department = departmentId}) => {
  'staffId': staffId,
  'fullName': 'Tran Thi Binh',
  'departmentId': department,
  'jobTitle': 'NURSE',
  'specialization': null,
  'licenseNumber': null,
  'phoneNumber': null,
  'email': null,
  'active': true,
  'createdAt': '2026-01-01T10:00:00Z',
  'updatedAt': '2026-01-02T10:00:00Z',
};

Map<String, dynamic> accountJson() => {
  'accountId': accountId,
  'username': 'nurse.01',
  'staffId': staffId,
  'patientId': null,
  'role': 'NURSE',
  'isActive': true,
  'lastLoginAt': null,
  'createdAt': '2026-01-01T10:00:00Z',
  'updatedAt': '2026-01-02T10:00:00Z',
};

class OrganizationAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  int status = 200;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final path = options.uri.path;
    Object? data;
    if (path.endsWith('/org/departments') && options.method == 'GET') {
      data = [
        departmentJson(),
        departmentJson(id: departmentId2, name: 'Nội tổng quát'),
      ];
    } else if (path.endsWith('/org/staff') && options.method == 'GET') {
      data = {
        'content': [staffJson()],
        'number': 0,
        'totalPages': 1,
        'totalElements': 1,
      };
    } else if (path.contains('/org/accounts')) {
      data = accountJson();
    } else if (path.contains('/org/departments')) {
      data = departmentJson();
    } else {
      data = staffJson();
    }
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': data}),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class OrganizationStub implements OrganizationRepository {
  final departmentReads = <String>[];
  final staffReads = <String>[];
  int departmentCreates = 0, staffCreates = 0, staffUpdates = 0, transfers = 0;
  int accountCreates = 0;
  bool transferFails = false;
  bool staffReadForbidden = false;
  Future<Staff> Function(String id, StaffDraft draft)? staffUpdateAction;
  Future<Account> Function(AccountDraft draft)? accountCreateAction;
  @override
  Future<List<Department>> departments({bool activeOnly = false}) async => [
    OrganizationDto.department(departmentJson()),
    OrganizationDto.department(
      departmentJson(id: departmentId2, name: 'Nội tổng quát'),
    ),
  ];
  @override
  Future<Department> department(String id) async {
    departmentReads.add(id);
    return OrganizationDto.department(departmentJson());
  }

  @override
  Future<Department> createDepartment(DepartmentDraft draft) async {
    departmentCreates++;
    return OrganizationDto.department(departmentJson());
  }

  @override
  Future<Department> updateDepartment(String id, DepartmentDraft draft) async =>
      OrganizationDto.department(departmentJson());
  @override
  Future<PageResult<Staff>> staff({
    String? departmentId,
    String? jobTitle,
    int page = 0,
    int size = 20,
  }) async => PageResult(
    items: [OrganizationDto.staff(staffJson())],
    number: page,
    totalPages: 2,
    totalElements: 21,
  );
  @override
  Future<Staff> staffMember(String id) async {
    staffReads.add(id);
    if (staffReadForbidden) throw const ApiFailure('Forbidden', status: 403);
    return OrganizationDto.staff(staffJson());
  }

  @override
  Future<Staff> createStaff(StaffDraft draft) async {
    staffCreates++;
    return OrganizationDto.staff(staffJson());
  }

  @override
  Future<Staff> updateStaff(String id, StaffDraft draft) async {
    staffUpdates++;
    return staffUpdateAction?.call(id, draft) ??
        OrganizationDto.staff(staffJson());
  }

  @override
  Future<Staff> transferStaff(String id, String newDepartmentId) async {
    transfers++;
    if (transferFails) {
      throw const ApiFailure(
        'Current department head cannot be transferred.',
        status: 422,
        code: 'DEPARTMENT_HEAD_INVALID',
      );
    }
    return OrganizationDto.staff(staffJson(department: newDepartmentId));
  }

  @override
  Future<Account> createAccount(AccountDraft draft) {
    accountCreates++;
    return accountCreateAction?.call(draft) ??
        Future.value(OrganizationDto.account(accountJson()));
  }

  @override
  Future<Account> updateAccountStatus(String id, bool active) async =>
      OrganizationDto.account(accountJson());
}

Widget host(
  OrganizationStub repository, {
  String role = 'ADMIN',
  Object? identity,
}) => ProviderScope(
  overrides: [
    organizationRepositoryProvider.overrideWithValue(repository),
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: identity ?? Object(), role: role),
    ),
  ],
  child: const MaterialApp(home: Scaffold(body: OrganizationPage())),
);

void main() {
  test('organization DTOs and mutation bodies mirror Core wire names', () {
    expect(OrganizationDto.department(departmentJson()).id, departmentId);
    expect(OrganizationDto.staff(staffJson()).departmentId, departmentId);
    expect(OrganizationDto.account(accountJson()).active, isTrue);
    expect(
      OrganizationDto.createDepartmentBody(
        const DepartmentDraft(
          name: ' Khoa ',
          abbreviation: ' KH ',
          type: 'CLINICAL',
          location: '',
          headId: null,
          active: true,
        ),
      ),
      {
        'departmentName': 'Khoa',
        'abbreviation': 'KH',
        'departmentType': 'CLINICAL',
        'location': null,
      },
    );
    expect(
      OrganizationDto.updateDepartmentBody(
        const DepartmentDraft(
          name: 'Khoa',
          abbreviation: 'KH',
          type: 'CLINICAL',
          location: null,
          headId: null,
          active: false,
        ),
      ),
      containsPair('active', false),
    );
    expect(
      OrganizationDto.createStaffBody(
        const StaffDraft(
          name: 'Binh',
          departmentId: departmentId,
          jobTitle: 'NURSE',
          specialization: null,
          licenseNumber: null,
          phoneNumber: null,
          email: null,
        ),
      ),
      containsPair('departmentId', departmentId),
    );
    expect(
      OrganizationDto.updateStaffBody(
        const StaffDraft(
          name: 'Binh',
          departmentId: departmentId2,
          jobTitle: 'NURSE',
          specialization: null,
          licenseNumber: null,
          phoneNumber: null,
          email: null,
        ),
      ).containsKey('departmentId'),
      isFalse,
    );
    expect(
      OrganizationDto.createAccountBody(
        const AccountDraft(
          username: 'patient01',
          password: 'password123',
          ownerId: departmentId,
          role: 'PATIENT',
        ),
      ),
      {
        'username': 'patient01',
        'password': 'password123',
        'staffId': null,
        'patientId': departmentId,
        'role': 'PATIENT',
      },
    );
  });

  test(
    'gateway repository uses exact department, staff and account endpoints',
    () async {
      final adapter = OrganizationAdapter();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'test-token',
      );
      addTearDown(client.close);
      final repository = GatewayOrganizationRepository(client);
      await repository.departments(activeOnly: true);
      await repository.department(departmentId);
      const department = DepartmentDraft(
        name: 'Khoa',
        abbreviation: 'KH',
        type: 'CLINICAL',
        location: null,
        headId: null,
        active: true,
      );
      await repository.createDepartment(department);
      await repository.updateDepartment(departmentId, department);
      await repository.staff(
        departmentId: departmentId,
        jobTitle: 'NURSE',
        page: 2,
      );
      await repository.staffMember(staffId);
      const draft = StaffDraft(
        name: 'Binh',
        departmentId: departmentId,
        jobTitle: 'NURSE',
        specialization: null,
        licenseNumber: null,
        phoneNumber: null,
        email: null,
      );
      await repository.createStaff(draft);
      await repository.updateStaff(staffId, draft);
      await repository.transferStaff(staffId, departmentId2);
      const account = AccountDraft(
        username: 'nurse.01',
        password: 'password123',
        ownerId: staffId,
        role: 'NURSE',
      );
      await repository.createAccount(account);
      await repository.updateAccountStatus(accountId, false);
      expect(adapter.requests.map((request) => request.method), [
        'GET',
        'GET',
        'POST',
        'PUT',
        'GET',
        'GET',
        'POST',
        'PUT',
        'PUT',
        'POST',
        'PUT',
      ]);
      expect(adapter.requests.first.uri.path, '/api/v1/org/departments');
      expect(adapter.requests.first.uri.queryParameters, {
        'activeOnly': 'true',
      });
      expect(adapter.requests[4].uri.queryParameters, {
        'departmentId': departmentId,
        'jobTitle': 'NURSE',
        'page': '2',
        'size': '20',
      });
      expect(
        adapter.requests[8].uri.path,
        '/api/v1/org/staff/$staffId/department',
      );
      expect(adapter.requests[8].data, {'newDepartmentId': departmentId2});
      expect(adapter.requests[9].uri.path, '/api/v1/org/accounts');
      expect(adapter.requests[10].data, {'isActive': false});
    },
  );

  testWidgets(
    'read roles see organization lists; only ADMIN sees account and create controls',
    (tester) async {
      final repository = OrganizationStub();
      await tester.pumpWidget(host(repository, role: 'DOCTOR'));
      await tester.pumpAndSettle();
      expect(find.text('Ngoại trú'), findsOneWidget);
      expect(find.text('Tài khoản'), findsNothing);
      expect(find.text('Thêm khoa phòng'), findsNothing);
      await tester.tap(find.text('Nhân sự'));
      await tester.pumpAndSettle();
      expect(find.text('Tran Thi Binh'), findsOneWidget);
      expect(find.text('Thêm nhân viên'), findsNothing);
      await tester.pumpWidget(host(repository, role: 'ADMIN'));
      await tester.pumpAndSettle();
      expect(find.text('Thêm khoa phòng'), findsOneWidget);
      expect(find.text('Tài khoản'), findsOneWidget);
      await tester.tap(find.text('Tài khoản'));
      await tester.pumpAndSettle();
      expect(find.text('Tạo tài khoản'), findsWidgets);
      expect(
        find.textContaining('API hiện không cung cấp danh sách tài khoản.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'failed staff detail load blocks blank editing form and suppresses 403 retry',
    (tester) async {
      final repository = OrganizationStub()..staffReadForbidden = true;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            organizationRepositoryProvider.overrideWithValue(repository),
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(sessionIdentity: 's1', role: 'ADMIN'),
            ),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: StaffEditorPage(staffId: staffId, onSaved: () {}),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Forbidden'), findsOneWidget);
      expect(find.byType(TextFormField), findsNothing);
      expect(find.text('Thử lại'), findsNothing);
    },
  );

  testWidgets(
    'staff profile save and department transfer report partial completion truthfully',
    (tester) async {
      final repository = OrganizationStub()..transferFails = true;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            organizationRepositoryProvider.overrideWithValue(repository),
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(sessionIdentity: 's1', role: 'ADMIN'),
            ),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: StaffEditorPage(staffId: staffId, onSaved: () {}),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final departmentDropdown = find
          .byType(DropdownButtonFormField<String>)
          .first;
      await tester.tap(departmentDropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Nội tổng quát').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Lưu thay đổi'));
      await tester.pumpAndSettle();
      expect(repository.staffUpdates, 1);
      expect(repository.transfers, 1);
      expect(
        find.text(
          'Thông tin nhân viên đã được lưu; chuyển khoa chưa hoàn tất.',
        ),
        findsOneWidget,
      );
      await tester.drag(find.byType(ListView).last, const Offset(0, -700));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chỉ thử chuyển khoa'));
      await tester.pumpAndSettle();
      expect(repository.staffUpdates, 1);
      expect(repository.transfers, 2);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'staff session switch during profile update prevents old-session department transfer',
    (tester) async {
      final update = Completer<Staff>();
      var saved = 0;
      final repository = OrganizationStub()
        ..staffUpdateAction = (_, _) => update.future;
      final container = ProviderContainer(
        overrides: [
          organizationRepositoryProvider.overrideWithValue(repository),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'staff-session-1',
              role: 'ADMIN',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: Scaffold(
              body: StaffEditorPage(staffId: staffId, onSaved: () => saved++),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byType(DropdownButtonFormField<String>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Nội tổng quát').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Lưu thay đổi'));
      await tester.pump();
      expect(repository.staffUpdates, 1);
      expect(repository.transfers, 0);

      container.updateOverrides([
        organizationRepositoryProvider.overrideWithValue(repository),
        workspaceAccessProvider.overrideWithValue(
          const WorkspaceAccess(
            sessionIdentity: 'staff-session-2',
            role: 'ADMIN',
          ),
        ),
      ]);
      await tester.pumpAndSettle();
      update.complete(OrganizationDto.staff(staffJson()));
      await tester.pumpAndSettle();
      expect(repository.transfers, 0);
      expect(saved, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('unknown account create outcome locks the POST form', (
    tester,
  ) async {
    final repository = OrganizationStub()
      ..accountCreateAction = (_) async => throw const ApiFailure('Timed out');
    await tester.pumpWidget(host(repository));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tài khoản'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).at(0), 'doctor.01');
    await tester.enterText(find.byType(TextFormField).at(1), 'password123');
    await tester.enterText(find.byType(TextFormField).at(2), staffId);
    await tester.tap(find.text('Tạo tài khoản').last);
    await tester.pumpAndSettle();
    expect(repository.accountCreates, 1);
    expect(find.textContaining('Kết quả tạo chưa xác định.'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Tạo tài khoản'),
          )
          .onPressed,
      isNull,
    );
    await tester.tap(find.text('Tạo tài khoản').last, warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(repository.accountCreates, 1);
  });
}
