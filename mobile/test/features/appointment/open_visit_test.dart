import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_open_visit_repository.dart';
import 'package:mediflow_mobile/features/appointment/domain/open_visit.dart';
import 'package:mediflow_mobile/features/appointment/domain/reception.dart';
import 'package:mediflow_mobile/features/appointment/presentation/appointment_page.dart';
import 'package:mediflow_mobile/features/appointment/presentation/open_visit_notice.dart';
import 'package:mediflow_mobile/features/appointment/presentation/reception_page.dart';

import '../../clinical_completion_workflow_test.dart' as clinical;
import '../../reception_transport_test.dart' as fixtures;
import '../../reception_workflow_test.dart' show ReceptionFake;
import '../../reference_picker_test_support.dart';

class OpenVisitFake implements OpenVisitRepository {
  Future<List<OpenVisit>> Function()? onOpen, onMine;
  Future<List<ReturnSource>> Function()? onSources;
  @override
  Future<List<OpenVisit>> openVisits(String patientId) =>
      onOpen?.call() ?? Future.value(const <OpenVisit>[]);
  @override
  Future<List<OpenVisit>> myOpenVisits() =>
      onMine?.call() ?? Future.value(const <OpenVisit>[]);
  @override
  Future<List<ReturnSource>> returnSources(String patientId) =>
      onSources?.call() ?? Future.value(const <ReturnSource>[]);
}

class ConflictOnceReception extends ReceptionFake {
  var conflicted = false;
  @override
  Future<ReceptionReceipt> register(ReceptionIntent intent) {
    if (conflicted) return super.register(intent);
    conflicted = true;
    intents.add(intent);
    throw const ApiFailure(
      'Còn lượt mở',
      status: 409,
      code: 'OPEN_VISIT_EXISTS',
    );
  }
}

OpenVisit visit({String departmentId = fixtures.departmentId}) => OpenVisit(
  registrationId: fixtures.registrationId,
  recordId: 'record-a',
  doctorId: fixtures.doctorId,
  departmentId: departmentId,
  sessionDate: '2026-11-03',
);

Future<ReceptionFake> openReception(
  WidgetTester tester,
  OpenVisitFake visits, {
  ReceptionFake? reception,
}) async {
  tester.view.physicalSize = const Size(1000, 3200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final repository = (reception ?? ReceptionFake())
    ..failRegistration = false
    ..failRefresh = false;
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: Object(), role: 'NURSE'),
        ),
        outpatientUiEnabledProvider.overrideWithValue(true),
        receptionRepositoryProvider.overrideWithValue(repository),
        openVisitRepositoryProvider.overrideWithValue(visits),
        referenceReaderProvider.overrideWithValue(
          ReferencePickerFake({
            ReferenceKind.patient: const [
              ReferenceOption(id: fixtures.patientId, name: 'Nguyễn An'),
            ],
            ReferenceKind.department: const [
              ReferenceOption(id: fixtures.departmentId, name: 'Nội'),
            ],
            ReferenceKind.doctor: const [
              ReferenceOption(id: fixtures.doctorId, name: 'Bác sĩ A'),
            ],
            ReferenceKind.room: const [
              ReferenceOption(id: fixtures.roomId, name: 'Phòng 1'),
            ],
          }),
        ),
      ],
      child: const MaterialApp(home: Scaffold(body: ReceptionPage())),
    ),
  );
  await tester.pumpAndSettle();
  await pickReference(tester, label: 'Bệnh nhân', name: 'Nguyễn An');
  await pickReference(tester, label: 'Khoa', name: 'Nội');
  await tester.tap(find.text('Tải buổi khám'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Chọn buổi'));
  await tester.pumpAndSettle();
  return repository;
}

Future<void> submit(WidgetTester tester) async {
  final button = find.text('Đăng ký và giữ chỗ');
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

class Transport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? data;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  group('reception', () {
    testWidgets('warns about an open visit', (tester) async {
      final visits = OpenVisitFake()
        ..onOpen = () async => [visit(departmentId: 'other-department')];
      await openReception(tester, visits);
      expect(
        find.textContaining('còn 1 lượt khám chưa kết thúc'),
        findsOneWidget,
      );
      expect(
        find.byKey(const Key('reception-independent-reason')),
        findsNothing,
      );
    });

    testWidgets('same department asks for a reason and sends it', (
      tester,
    ) async {
      final visits = OpenVisitFake()..onOpen = () async => [visit()];
      final reception = await openReception(tester, visits);
      await submit(tester);
      expect(reception.intents, isEmpty);
      expect(find.textContaining('Cần ghi lý do đăng ký thêm'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('reception-independent-reason')),
        '  Khám vấn đề khác  ',
      );
      await submit(tester);
      expect(
        reception.intents.single.command.toJson()['independentVisitReason'],
        'Khám vấn đề khác',
      );
    });

    testWidgets('return visit needs source and reason', (tester) async {
      final visits = OpenVisitFake()
        ..onSources = () async => const [
          ReturnSource(
            recordId: 'closed-record',
            status: 'CLOSED_WITHOUT_CONCLUSION',
            examinationDate: '2026-10-20',
          ),
        ];
      final reception = await openReception(tester, visits);
      final toggle = find.text('Bệnh nhân quay lại sau lượt đã kết thúc');
      await tester.ensureVisible(toggle);
      await tester.tap(toggle);
      await tester.pumpAndSettle();
      await submit(tester);
      expect(reception.intents, isEmpty);
      expect(
        find.textContaining('Chọn lượt đã kết thúc và ghi lý do quay lại'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const Key('reception-return-source')));
      await tester.pumpAndSettle();
      await tester.tap(find.textContaining('2026-10-20').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('reception-return-reason')),
        'Đau tăng sau khi về',
      );
      await submit(tester);
      final json = reception.intents.single.command.toJson();
      expect(json['visitType'], 'NEW_VISIT');
      expect(json['returnSourceRecordId'], 'closed-record');
      expect(json['returnReason'], 'Đau tăng sau khi về');
    });

    testWidgets('open visit outage does not block registration', (
      tester,
    ) async {
      final visits = OpenVisitFake()
        ..onOpen = () async => throw const ApiFailure('Lỗi', status: 503);
      final reception = await openReception(tester, visits);
      expect(
        find.textContaining('Không kiểm tra được lượt khám đang mở'),
        findsOneWidget,
      );
      await submit(tester);
      final json = reception.intents.single.command.toJson();
      expect(json.containsKey('independentVisitReason'), isFalse);
      expect(json.containsKey('returnSourceRecordId'), isFalse);
    });

    testWidgets('server conflict reveals the reason field', (tester) async {
      final visits = OpenVisitFake()
        ..onOpen = () async => throw const ApiFailure('Lỗi', status: 503);
      final reception = await openReception(
        tester,
        visits,
        reception: ConflictOnceReception(),
      );
      await submit(tester);
      await tester.enterText(
        find.byKey(const Key('reception-independent-reason')),
        'Khám vấn đề khác',
      );
      await submit(tester);
      expect(reception.intents, hasLength(2));
      expect(
        reception.intents.last.command.toJson()['independentVisitReason'],
        'Khám vấn đề khác',
      );
    });
  });

  group('doctor', () {
    testWidgets('doctor list opens the same visit', (tester) async {
      final visits = OpenVisitFake()..onMine = () async => [visit()];
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workspaceAccessProvider.overrideWithValue(
              const WorkspaceAccess(role: 'DOCTOR', sessionIdentity: 'one'),
            ),
            referenceReaderProvider.overrideWithValue(clinical.ReferenceFake()),
            appointmentRepositoryProvider.overrideWithValue(
              clinical.AppointmentFake(),
            ),
            outpatientUiEnabledProvider.overrideWithValue(true),
            openVisitRepositoryProvider.overrideWithValue(visits),
          ],
          child: const MaterialApp(home: Scaffold(body: AppointmentPage())),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tiếp tục khám'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<AppointmentDetailPage>(find.byType(AppointmentDetailPage))
            .appointmentId,
        fixtures.registrationId,
      );
    });

    testWidgets('outage shows an error, not an empty list', (tester) async {
      final visits = OpenVisitFake()
        ..onMine = () async => throw const ApiFailure('Lỗi', status: 503);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            referenceReaderProvider.overrideWithValue(clinical.ReferenceFake()),
            openVisitRepositoryProvider.overrideWithValue(visits),
          ],
          child: MaterialApp(
            home: Scaffold(body: DoctorOpenVisitsSection(onOpen: (_) {})),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Không tải được'), findsOneWidget);
    });

    testWidgets('in exam offers continue and never start', (tester) async {
      await tester.pumpWidget(
        clinical.host(
          child: AppointmentDetailPage(
            appointmentId: clinical.appointmentId,
            onOpenRecord: (_, _) {},
          ),
          appointments: clinical.AppointmentFake()
            ..onGet = (_) async =>
                clinical.appointment(status: 'IN_EXAM', careContractVersion: 2),
          outpatientEnabled: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Tiếp tục khám'), findsOneWidget);
      expect(find.text('Bắt đầu khám'), findsNothing);
    });
  });

  group('wire', () {
    late Transport adapter;
    late ApiClient client;
    setUp(() {
      adapter = Transport();
      client = ApiClient(
        AppConfig('http://localhost:28080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'token',
      );
    });
    tearDown(() => client.close());

    test('reads open visits of a patient', () async {
      adapter.data = [
        {
          'registrationId': 'registration-a',
          'recordId': 'record-a',
          'doctorId': 'doctor-a',
          'departmentId': 'department-a',
          'sessionDate': '2026-11-03',
        },
      ];
      final result = await GatewayOpenVisitRepository(client)
          .openVisits(fixtures.patientId);
      expect(
        adapter.requests.single.path,
        endsWith('v2/patients/${fixtures.patientId}/open-visits'),
      );
      expect(result.single.recordId, 'record-a');
      expect(result.single.startedAt, isNull);
    });

    test('reads the doctor list from its own route', () async {
      adapter.data = <Object>[];
      await GatewayOpenVisitRepository(client).myOpenVisits();
      expect(
        adapter.requests.single.path,
        endsWith('v2/doctors/me/open-visits'),
      );
    });

    test('an open record is not a return source', () async {
      adapter.data = [
        {'recordId': 'a', 'status': 'OPEN', 'examinationDate': '2026-11-01'},
        {
          'recordId': 'b',
          'status': 'COMPLETED',
          'examinationDate': '2026-11-02',
        },
      ];
      final result = await GatewayOpenVisitRepository(client)
          .returnSources(fixtures.patientId);
      expect(result.map((item) => item.recordId), ['b']);
    });

    test('a plain registration omits the new fields', () {
      final json = const ReceptionCommand(
        patientId: fixtures.patientId,
        session: fixtures.session,
      ).toJson();
      expect(json.containsKey('independentVisitReason'), isFalse);
      expect(json.containsKey('returnSourceRecordId'), isFalse);
      expect(json.containsKey('returnReason'), isFalse);
    });

    test('a follow up cannot carry a return link', () {
      final json = const ReceptionCommand(
        patientId: fixtures.patientId,
        session: fixtures.session,
        followUp: true,
        returnVisit: true,
        returnSourceRecordId: 'record-a',
        returnReason: 'x',
      ).toJson();
      expect(json.containsKey('returnSourceRecordId'), isFalse);
    });
  });
}
