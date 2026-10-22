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
import 'package:mediflow_mobile/features/patient/data/gateway_patient_repository.dart';
import 'package:mediflow_mobile/features/patient/data/patient_dto.dart';
import 'package:mediflow_mobile/features/patient/domain/patient.dart';
import 'package:mediflow_mobile/features/patient/presentation/patient_page.dart';
import 'package:mediflow_mobile/features/patient/presentation/providers.dart';

const patientId = '11111111-1111-4111-8111-111111111111';

Map<String, dynamic> patientJson({
  String id = patientId,
  String name = 'Nguyen Van An',
  String gender = 'M',
}) => {
  'maBenhNhan': id,
  'hoTen': name,
  'ngaySinh': '1988-02-03',
  'gioiTinh': gender,
  'soCmnd': '012345678901',
  'diaChi': 'District 1',
  'soDienThoai': '0912345678',
  'email': 'an@example.test',
  'bhytSo': '12-12345678-1',
  'createdAt': '2026-01-01T10:00:00Z',
  'updatedAt': '2026-01-02T10:00:00Z',
};

Patient samplePatient() => PatientDto.fromJson(patientJson());

class PatientAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  int status = 200;
  Object? body;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final response = options.method == 'DELETE'
        ? null
        : options.method == 'GET' && options.uri.path.endsWith('/patients')
        ? {
            'content': [patientJson()],
            'number': 0,
            'totalPages': 1,
            'totalElements': 1,
          }
        : patientJson();
    return ResponseBody.fromString(
      status == 204
          ? ''
          : jsonEncode({'success': true, 'data': body ?? response}),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class PatientStub implements PatientRepository {
  final searches = <({String? keyword, int page, int size})>[];
  Future<PageResult<Patient>> Function(String? keyword, int page, int size)?
  searchAction;
  Future<Patient> Function(String id)? readAction;
  Future<Patient> Function(PatientDraft draft)? createAction;
  Future<Patient> Function(String id, PatientDraft draft)? updateAction;
  Future<void> Function(String id)? deleteAction;
  int creates = 0, updates = 0, deletes = 0;

  @override
  Future<PageResult<Patient>> search({
    String? keyword,
    int page = 0,
    int size = 20,
  }) {
    searches.add((keyword: keyword, page: page, size: size));
    return searchAction?.call(keyword, page, size) ??
        Future.value(
          PageResult(
            items: [samplePatient()],
            number: page,
            totalPages: 2,
            totalElements: 21,
          ),
        );
  }

  @override
  Future<Patient> getById(String id) =>
      readAction?.call(id) ?? Future.value(samplePatient());
  @override
  Future<Patient> create(PatientDraft draft) {
    creates++;
    return createAction?.call(draft) ?? Future.value(samplePatient());
  }

  @override
  Future<Patient> update(String id, PatientDraft draft) {
    updates++;
    return updateAction?.call(id, draft) ?? Future.value(samplePatient());
  }

  @override
  Future<void> delete(String id) {
    deletes++;
    return deleteAction?.call(id) ?? Future.value();
  }
}

Widget host(
  PatientStub repository, {
  String role = 'ADMIN',
  Object? identity,
}) => ProviderScope(
  overrides: [
    patientRepositoryProvider.overrideWithValue(repository),
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: identity ?? Object(), role: role),
    ),
  ],
  child: const MaterialApp(home: Scaffold(body: PatientPage())),
);

Future<void> enterValidPatientDraft(WidgetTester tester) async {
  await tester.enterText(find.byType(TextFormField).at(0), 'Nguyen Van An');
  await tester.tap(find.byType(TextFormField).at(1));
  await tester.pumpAndSettle();
  expect(find.byType(DatePickerDialog), findsOneWidget);
  await tester.tap(find.text('1').last);
  await tester.pumpAndSettle();
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();
  await tester.enterText(find.byType(TextFormField).at(2), '012345678901');
}

void main() {
  test('patient use case rejects impossible dates and missing identity before POST', () async {
    final repository = PatientStub();
    expect(
      () => SavePatient(repository).create(
        const PatientDraft(
          name: 'An',
          birthDate: '2024-02-30',
          gender: 'M',
          identityNumber: '012345678901',
        ),
      ),
      throwsArgumentError,
    );
    expect(
      () => SavePatient(repository).create(
        const PatientDraft(name: 'An', birthDate: '1988-02-03', gender: 'M'),
      ),
      throwsArgumentError,
    );
    expect(repository.creates, 0);
  });

  test('Patient wire model supports optional identity updates', () {
    final patient = PatientDto.fromJson(patientJson());
    expect(patient.id, patientId);
    expect(patient.gender, 'M');
    expect(
      PatientDto.createBody(
        const PatientDraft(
          name: ' Name ',
          birthDate: '1988-02-03',
          gender: 'F',
          identityNumber: ' 123 ',
        ),
      ),
      {
        'hoTen': 'Name',
        'ngaySinh': '1988-02-03',
        'gioiTinh': 'F',
        'soCmnd': '123',
        'diaChi': null,
        'soDienThoai': null,
        'email': null,
        'bhytSo': null,
      },
    );
    final update = PatientDto.updateBody(
      const PatientDraft(
        name: 'Name',
        birthDate: '1988-02-03',
        gender: 'M',
        identityNumber: ' 456 ',
      ),
    );
    expect(update['soCmnd'], '456');
    expect(
      PatientDto.updateBody(
        const PatientDraft(name: 'Name', birthDate: '1988-02-03', gender: 'M'),
      ).containsKey('soCmnd'),
      isFalse,
    );
  });

  test('repository uses exact Gateway paths, query and CRUD verbs', () async {
    final adapter = PatientAdapter();
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'test-token',
    );
    addTearDown(client.close);
    final repository = GatewayPatientRepository(client);
    final list = await repository.search(keyword: ' An ', page: 2);
    expect(list.items.single.id, patientId);
    await repository.getById(patientId);
    await repository.create(
      const PatientDraft(
        name: 'An',
        birthDate: '1988-02-03',
        gender: 'M',
        identityNumber: '123',
      ),
    );
    await repository.update(
      patientId,
      const PatientDraft(name: 'An', birthDate: '1988-02-03', gender: 'M'),
    );
    adapter.status = 204;
    await repository.delete(patientId);
    expect(adapter.requests.map((r) => r.method), [
      'GET',
      'GET',
      'POST',
      'PUT',
      'DELETE',
    ]);
    expect(adapter.requests.first.uri.path, '/api/v1/patients');
    expect(adapter.requests.first.uri.queryParameters, {
      'keyword': 'An',
      'page': '2',
      'size': '20',
    });
    expect((adapter.requests[2].data as Map).keys, contains('soCmnd'));
    expect((adapter.requests[3].data as Map).containsKey('soCmnd'), isFalse);
    expect(adapter.requests.last.uri.path, '/api/v1/patients/$patientId');
  });

  testWidgets(
    'doctor reads patient list but cannot create; nurse can open the create form',
    (tester) async {
      final repo = PatientStub();
      await tester.pumpWidget(host(repo, role: 'DOCTOR'));
      await tester.pumpAndSettle();
      expect(find.text('Nguyen Van An'), findsOneWidget);
      expect(find.textContaining(patientId), findsNothing);
      expect(find.text('Thêm bệnh nhân'), findsNothing);
      expect(repo.searches, hasLength(1));
      await tester.pumpWidget(host(repo, role: 'NURSE'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Thêm bệnh nhân'));
      await tester.pumpAndSettle();
      expect(find.text('CCCD/CMND *'), findsOneWidget);
    },
  );

  testWidgets('forbidden list response does not offer retry', (tester) async {
    final repo = PatientStub()
      ..searchAction = (_, _, _) async =>
          throw const ApiFailure('Denied', status: 403);
    await tester.pumpWidget(host(repo));
    await tester.pumpAndSettle();
    expect(find.text('Denied'), findsOneWidget);
    expect(find.text('Thử lại'), findsNothing);
    expect(repo.searches, hasLength(1));
  });

  testWidgets('nurse can submit a valid new patient', (tester) async {
    final repo = PatientStub();
    var saved = 0;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          patientRepositoryProvider.overrideWithValue(repo),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'create-test',
              role: 'NURSE',
            ),
          ),
        ],
        child: MaterialApp(
          home: Scaffold(body: PatientEditorPage(onSaved: () => saved++)),
        ),
      ),
    );
    await enterValidPatientDraft(tester);
    await tester.tap(find.text('Tạo bệnh nhân'));
    await tester.pumpAndSettle();
    expect(repo.creates, 1);
    expect(saved, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('uncertain patient create outcome prevents duplicate POST', (
    tester,
  ) async {
    final repo = PatientStub()
      ..createAction = (_) async => throw const ApiFailure('Timed out');
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          patientRepositoryProvider.overrideWithValue(repo),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'create-timeout',
              role: 'ADMIN',
            ),
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(body: PatientEditorPage(onSaved: _ignoreSaved)),
        ),
      ),
    );
    await enterValidPatientDraft(tester);
    await tester.tap(find.text('Tạo bệnh nhân'));
    await tester.pumpAndSettle();
    expect(repo.creates, 1);
    await tester.drag(find.byType(ListView).first, const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(find.textContaining('Kết quả tạo chưa xác định.'), findsOneWidget);
    await tester.tap(find.text('Tạo bệnh nhân'), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(repo.creates, 1);
  });

  testWidgets(
    'female patient edit submits changed identity and preserves gender; stale save cannot navigate after session switch',
    (tester) async {
      final update = Completer<Patient>();
      var reads = 0;
      PatientDraft? submitted;
      var saved = 0;
      final repo = PatientStub()
        ..readAction = (_) async {
          reads++;
          return PatientDto.fromJson(
            reads == 1
                ? patientJson(name: 'Old session patient', gender: 'F')
                : patientJson(name: 'New session patient', gender: 'M'),
          );
        }
        ..updateAction = (_, draft) {
          submitted = draft;
          return update.future;
        };
      final container = ProviderContainer(
        overrides: [
          patientRepositoryProvider.overrideWithValue(repo),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(sessionIdentity: 'session-1', role: 'ADMIN'),
          ),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: Scaffold(
              body: PatientEditorPage(
                patientId: patientId,
                onSaved: () => saved++,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Nữ'), findsOneWidget);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'CCCD/CMND *'),
        '987654321',
      );
      await tester.tap(find.text('Lưu thay đổi'));
      await tester.pump();
      expect(submitted?.gender, 'F');
      expect(submitted?.identityNumber, '987654321');
      expect(repo.updates, 1);

      container.updateOverrides([
        patientRepositoryProvider.overrideWithValue(repo),
        workspaceAccessProvider.overrideWithValue(
          const WorkspaceAccess(sessionIdentity: 'session-2', role: 'ADMIN'),
        ),
      ]);
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextFormField>(find.byType(TextFormField).first)
            .controller!
            .text,
        'New session patient',
      );
      update.complete(samplePatient());
      await tester.pumpAndSettle();
      expect(saved, 0);
      expect(
        tester
            .widget<TextFormField>(find.byType(TextFormField).first)
            .controller!
            .text,
        'New session patient',
      );
    },
  );

  testWidgets('late detail response from previous session is discarded', (
    tester,
  ) async {
    final firstRead = Completer<Patient>();
    var reads = 0;
    final repo = PatientStub()
      ..readAction = (_) {
        reads++;
        return reads == 1
            ? firstRead.future
            : Future.value(
                PatientDto.fromJson(patientJson(name: 'New session patient')),
              );
      };
    final container = ProviderContainer(
      overrides: [
        patientRepositoryProvider.overrideWithValue(repo),
        workspaceAccessProvider.overrideWithValue(
          const WorkspaceAccess(sessionIdentity: 'session-1', role: 'ADMIN'),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(
          home: Scaffold(body: PatientDetailPage(patientId: patientId)),
        ),
      ),
    );
    await tester.pump();
    container.updateOverrides([
      patientRepositoryProvider.overrideWithValue(repo),
      workspaceAccessProvider.overrideWithValue(
        const WorkspaceAccess(sessionIdentity: 'session-2', role: 'ADMIN'),
      ),
    ]);
    await tester.pumpAndSettle();
    expect(find.text('New session patient'), findsOneWidget);
    firstRead.complete(
      PatientDto.fromJson(patientJson(name: 'Old private patient')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Old private patient'), findsNothing);
    expect(find.text('New session patient'), findsOneWidget);
  });

  testWidgets('patient ID stays in collapsed technical details', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          patientRepositoryProvider.overrideWithValue(PatientStub()),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'patient-detail',
              role: 'ADMIN',
            ),
          ),
        ],
        child: const MaterialApp(
          home: Scaffold(body: PatientDetailPage(patientId: patientId)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Nguyen Van An'), findsOneWidget);
    expect(find.textContaining(patientId), findsNothing);

    await tester.tap(find.text('Thông tin kỹ thuật'));
    await tester.pumpAndSettle();
    expect(find.textContaining(patientId), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'admin delete requires confirmation and reports a failed delete',
    (tester) async {
      final repo = PatientStub()
        ..deleteAction = (_) async =>
            throw const ApiFailure('Delete failed', status: 500);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            patientRepositoryProvider.overrideWithValue(repo),
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(
                sessionIdentity: 'delete-test',
                role: 'ADMIN',
              ),
            ),
          ],
          child: const MaterialApp(
            home: Scaffold(body: PatientDetailPage(patientId: patientId)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xóa hồ sơ'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hủy'));
      await tester.pumpAndSettle();
      expect(repo.deletes, 0);

      await tester.tap(find.text('Xóa hồ sơ'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xóa hồ sơ').last);
      await tester.pumpAndSettle();
      expect(repo.deletes, 1);
      expect(find.text('Delete failed'), findsOneWidget);
      expect(find.text('Nguyen Van An'), findsOneWidget);
    },
  );

  testWidgets('admin confirms delete and returns to the previous screen', (
    tester,
  ) async {
    final repo = PatientStub();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          patientRepositoryProvider.overrideWithValue(repo),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'delete-success',
              role: 'ADMIN',
            ),
          ),
        ],
        child: MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => const Scaffold(
                      body: PatientDetailPage(patientId: patientId),
                    ),
                  ),
                ),
                child: const Text('Open patient'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open patient'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xóa hồ sơ'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xóa hồ sơ').last);
    await tester.pumpAndSettle();
    expect(repo.deletes, 1);
    expect(find.text('Open patient'), findsOneWidget);
    expect(find.text('Nguyen Van An'), findsNothing);
  });

  testWidgets(
    'delete confirmation drops private patient data and blocks delete after session switch',
    (tester) async {
      var reads = 0;
      final repo = PatientStub()
        ..readAction = (_) async {
          reads++;
          return PatientDto.fromJson(
            patientJson(
              name: reads == 1
                  ? 'Private old-session patient'
                  : 'Current patient',
            ),
          );
        };
      final container = ProviderContainer(
        overrides: [
          patientRepositoryProvider.overrideWithValue(repo),
          workspaceAccessProvider.overrideWithValue(
            const WorkspaceAccess(
              sessionIdentity: 'confirm-session-1',
              role: 'ADMIN',
            ),
          ),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            home: Scaffold(body: PatientDetailPage(patientId: patientId)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xóa hồ sơ'));
      await tester.pumpAndSettle();
      expect(find.text('Private old-session patient'), findsOneWidget);

      container.updateOverrides([
        patientRepositoryProvider.overrideWithValue(repo),
        workspaceAccessProvider.overrideWithValue(
          const WorkspaceAccess(
            sessionIdentity: 'confirm-session-2',
            role: 'ADMIN',
          ),
        ),
      ]);
      await tester.pumpAndSettle();
      expect(find.text('Private old-session patient'), findsNothing);
      expect(find.text('Phiên đăng nhập đã thay đổi'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.text('Xóa hồ sơ'),
        ),
        findsNothing,
      );
      await tester.tap(find.text('Đóng'));
      await tester.pumpAndSettle();
      expect(repo.deletes, 0);
      expect(find.text('Current patient'), findsOneWidget);
    },
  );
}

void _ignoreSaved() {}
