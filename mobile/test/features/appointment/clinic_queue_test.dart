import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/reference.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/appointment/data/gateway_clinic_queue_repository.dart';
import 'package:mediflow_mobile/features/appointment/domain/clinic_queue.dart';
import 'package:mediflow_mobile/features/appointment/domain/outpatient_schedule.dart';
import 'package:mediflow_mobile/features/appointment/domain/reception.dart';
import 'package:mediflow_mobile/features/appointment/presentation/clinic_queue_page.dart';
import 'package:mediflow_mobile/features/appointment/presentation/outpatient_schedule_pages.dart';
import 'package:mediflow_mobile/features/appointment/presentation/reception_page.dart';
import 'package:mediflow_mobile/features/appointment/presentation/reschedule_sheet.dart';
import 'package:mediflow_mobile/features/appointment/presentation/vital_sign_sheet.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';

const patientId = '11111111-1111-4111-8111-111111111111';
const departmentId = '22222222-2222-4222-8222-222222222222';
const doctorId = '33333333-3333-4333-8333-333333333333';
const sessionId = '44444444-4444-4444-8444-444444444444';
const roomA = '55555555-5555-4555-8555-555555555555';
const roomB = '55555555-5555-4555-8555-555555555556';
const registrationId = '66666666-6666-4666-8666-666666666666';
const ticketId = '77777777-7777-4777-8777-777777777777';
const targetSessionId = '88888888-8888-4888-8888-888888888888';

class QueueTransport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  final failures = <Object>[];
  Object? data;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (failures.isNotEmpty) throw failures.removeAt(0);
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

ClinicQueueEntry entry({
  String registration = registrationId,
  String room = roomA,
  String stage = 'WAITING_CALL',
  int? queueNumber = 7,
  int? roomQueueNumber = 1,
  bool vitals = false,
  String? ticket = ticketId,
}) => ClinicQueueEntry(
  registrationId: registration,
  patientId: patientId,
  sessionId: sessionId,
  doctorId: doctorId,
  departmentId: departmentId,
  roomId: room,
  queueDate: '2026-11-10',
  queueNumber: queueNumber,
  ticketId: ticket,
  ticketRevision: ticket == null ? null : 2,
  roomQueueNumber: roomQueueNumber,
  stage: stage,
  priority: false,
  checkedInAt: '2026-11-10T01:00:00Z',
  calledAt: null,
  vitalSignsRecorded: vitals,
  readyForExam: true,
  continuationRequired: false,
);

const ticketResult = ClinicQueueTicket(
  ticketId: ticketId,
  revision: 3,
  registrationId: registrationId,
  roomId: roomA,
  queueDate: '2026-11-10',
  roomQueueNumber: 1,
  status: 'CALLED',
  priority: false,
  issuedAt: '2026-11-10T01:00:00Z',
  calledAt: '2026-11-10T01:10:00Z',
);

class FakeClinic extends Fake implements ClinicQueueRepository {
  FakeClinic(this.entries);
  final List<ClinicQueueEntry> entries;
  final vitals = <VitalSignInput>[];
  final reschedules = <RescheduleInput>[];
  final keys = <String>[];

  @override
  Future<List<ClinicQueueEntry>> board({
    required String date,
    String? roomId,
  }) async => entries;

  @override
  Future<ClinicQueueTicket> call(
    String id,
    int expectedRevision, {
    required String idempotencyKey,
  }) async {
    keys.add(idempotencyKey);
    return ticketResult;
  }

  @override
  Future<VitalSignRecord> recordVitals(
    String id,
    VitalSignInput input, {
    required String idempotencyKey,
  }) async {
    vitals.add(input);
    return VitalSignRecord(
      observationId: ticketId,
      registrationId: id,
      observedAt: '2026-11-10T01:00:00Z',
      input: input,
    );
  }

  @override
  Future<List<VitalSignRecord>> listVitals(String id) async => const [];

  @override
  Future<void> reschedule(
    String id,
    RescheduleInput input, {
    required String idempotencyKey,
  }) async => reschedules.add(input);
}

class FakeSchedule extends Fake implements OutpatientScheduleRepository {
  @override
  Future<PageResult<RegistrationRow>> sessionRegistrations(
    String id, {
    int page = 0,
  }) async => const PageResult(
    items: [
      RegistrationRow(
        id: registrationId,
        patientId: patientId,
        visitType: 'NEW',
        status: 'CONFIRMED',
        readyForExam: true,
        checkedInAt: '2026-11-10T01:00:00Z',
        queueNumber: 7,
        roomQueueNumber: 3,
      ),
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  );

  @override
  Future<OutpatientRegistration> registration(String id) async =>
      registrationDetail;

  @override
  Future<PageResult<OutpatientRegistration>> myRegistrations({
    int page = 0,
  }) async => const PageResult(
    items: [registrationDetail],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  );
}

const registrationDetail = OutpatientRegistration(
  id: registrationId,
  revision: 4,
  doctorId: doctorId,
  departmentId: departmentId,
  roomId: roomA,
  sessionDate: '2026-11-10',
  startTime: '08:00',
  endTime: '11:00',
  receptionMode: 'SCHEDULED',
  visitType: 'NEW',
  status: 'CONFIRMED',
  reservationExpiresAt: '2026-11-10T01:00:00Z',
  queueNumber: 7,
  roomQueueNumber: 3,
);

const openTarget = ReceptionSession(
  id: targetSessionId,
  revision: 1,
  departmentId: departmentId,
  doctorId: doctorId,
  roomId: roomA,
  date: '2026-11-12',
  startTime: '13:00',
  endTime: '16:00',
  capacity: 20,
  availableCount: 3,
  status: 'OPEN',
);

class FakeReception extends Fake implements ReceptionRepository {
  @override
  Future<PageResult<ReceptionSession>> sessions({
    required String departmentId,
    required String fromDate,
    required String toDate,
    int page = 0,
  }) async => const PageResult(
    items: [openTarget],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  );
}

class References extends Fake implements ReferenceReader {
  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async =>
      ReferenceOption(
        id: id,
        name: kind != ReferenceKind.room
            ? 'Người liên quan'
            : id == roomB
            ? 'Phòng 2'
            : 'Phòng 1',
      );
}

Widget host(
  Widget child, {
  String role = 'NURSE',
  bool clinicIntake = true,
  FakeClinic? clinic,
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(sessionIdentity: 'session', role: role),
    ),
    referenceReaderProvider.overrideWithValue(References()),
    outpatientScheduleRepositoryProvider.overrideWithValue(FakeSchedule()),
    receptionRepositoryProvider.overrideWithValue(FakeReception()),
    clinicQueueRepositoryProvider.overrideWithValue(clinic ?? FakeClinic([])),
    appConfigProvider.overrideWithValue(
      AppConfig(
        'http://localhost:8080/api',
        scheduledPaymentsEnabled: true,
        clinicIntakeEnabled: clinicIntake,
      ),
    ),
  ],
  child: MaterialApp(home: Scaffold(body: child)),
);

Future<void> tapText(WidgetTester tester, String text) async {
  final target = find.text(text);
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

Future<void> enter(WidgetTester tester, String label, String value) async {
  final target = find.widgetWithText(TextField, label);
  await tester.ensureVisible(target);
  await tester.enterText(target, value);
  await tester.pump();
}

void main() {
  group('gateway', () {
    late QueueTransport transport;
    late GatewayClinicQueueRepository repository;

    setUp(() {
      transport = QueueTransport();
      repository = GatewayClinicQueueRepository(
        ApiClient(
          AppConfig('http://localhost:8080/api'),
          dio: Dio()..httpClientAdapter = transport,
        ),
      );
    });

    Map<String, dynamic> wire(String stage, int? room) => {
      'registrationId': registrationId,
      'patientId': patientId,
      'sessionId': sessionId,
      'doctorId': doctorId,
      'departmentId': departmentId,
      'roomId': roomA,
      'queueDate': '2026-11-10',
      'queueNumber': 7,
      'ticketId': room == null ? null : ticketId,
      'ticketRevision': room == null ? null : 2,
      'roomQueueNumber': ?room,
      'stage': stage,
      'priority': false,
      'checkedInAt': null,
      'calledAt': null,
      'vitalSignsRecorded': false,
      'readyForExam': false,
      'continuationRequired': false,
    };

    Map<String, dynamic> ticketWire() => {
      'ticketId': ticketId,
      'revision': 3,
      'registrationId': registrationId,
      'roomId': roomA,
      'queueDate': '2026-11-10',
      'roomQueueNumber': 1,
      'status': 'CALLED',
      'priority': false,
      'issuedAt': '2026-11-10T01:00:00Z',
      'calledAt': null,
    };

    test('parses each queue stage and optional room number', () async {
      const stages = [
        'AWAITING_PAYMENT',
        'NOT_ARRIVED',
        'WAITING_ROOM_TICKET',
        'WAITING_CALL',
        'CALLED',
        'IN_EXAM',
        'ABSENT',
      ];
      transport.data = [
        for (final stage in stages)
          wire(stage, stage == 'WAITING_CALL' ? 4 : null),
      ];
      final rows = await repository.board(date: '2026-11-10', roomId: roomA);
      expect(rows.map((row) => row.stage), stages);
      expect(rows[2].roomQueueNumber, isNull);
      expect(rows[3].roomQueueNumber, 4);
      expect(rows[3].ticketRevision, 2);
      expect(transport.requests.single.path, endsWith('v2/clinic-queue'));
      expect(transport.requests.single.queryParameters, {
        'date': '2026-11-10',
        'roomId': roomA,
      });
    });

    test('rejects an unknown queue stage', () async {
      transport.data = [wire('TELEPORTED', null)];
      await expectLater(
        repository.board(date: '2026-11-10'),
        throwsA(isA<Exception>()),
      );
    });

    test('issue and call send Idempotency-Key through api_client', () async {
      transport.data = ticketWire();
      final issueKey = newReceptionKey(), callKey = newReceptionKey();
      await repository.issueTicket(registrationId, idempotencyKey: issueKey);
      await repository.call(ticketId, 2, idempotencyKey: callKey);
      final issue = transport.requests.first, call = transport.requests.last;
      expect(issue.method, 'POST');
      expect(
        issue.path,
        endsWith(
          'v2/outpatient-registrations/$registrationId/room-queue-ticket',
        ),
      );
      expect(issue.headers['Idempotency-Key'], issueKey);
      expect(call.method, 'PUT');
      expect(call.path, endsWith('v2/clinic-queue-tickets/$ticketId/call'));
      expect(call.headers['Idempotency-Key'], callKey);
      expect(call.data, {'expectedRevision': 2});
    });

    test('adjust sends type and reason with the expected revision', () async {
      transport.data = ticketWire();
      await repository.adjust(
        ticketId,
        2,
        'NO_SHOW',
        'Gọi ba lần',
        idempotencyKey: newReceptionKey(),
      );
      expect(transport.requests.single.data, {
        'expectedRevision': 2,
        'type': 'NO_SHOW',
        'reason': 'Gọi ba lần',
      });
    });

    test('reuses the key when retrying after a network failure', () async {
      final keys = ClinicCommandKeys();
      transport.data = ticketWire();
      transport.failures.add(
        DioException.connectionError(
          requestOptions: RequestOptions(path: '/v2/x'),
          reason: 'offline',
        ),
      );
      final scope = 'call:$ticketId';
      final first = keys.keyFor(scope, '2');
      await expectLater(
        repository.call(ticketId, 2, idempotencyKey: first),
        throwsA(isA<ApiFailure>()),
      );
      final second = keys.keyFor(scope, '2');
      expect(second, first);
      await repository.call(ticketId, 2, idempotencyKey: second);
      expect(
        transport.requests.map((request) => request.headers['Idempotency-Key']),
        [first, first],
      );
      keys.settle(scope);
      expect(keys.keyFor(scope, '2'), isNot(first));
    });
  });

  group('domain', () {
    test('a changed request gets a new key', () {
      final keys = ClinicCommandKeys();
      final first = keys.keyFor('scope', 'a');
      expect(keys.keyFor('scope', 'b'), isNot(first));
    });

    test('outcome is unknown only for network, timeout and server errors', () {
      expect(clinicOutcomeUnknown(ApiFailure('x')), isTrue);
      expect(clinicOutcomeUnknown(ApiFailure('x', status: 408)), isTrue);
      expect(clinicOutcomeUnknown(ApiFailure('x', status: 503)), isTrue);
      expect(clinicOutcomeUnknown(ApiFailure('x', status: 409)), isFalse);
      expect(clinicOutcomeUnknown(StateError('x')), isTrue);
    });

    test('distinguishes full, not open and load error', () {
      ReceptionSession session(String status, int available) =>
          ReceptionSession(
            id: sessionId,
            revision: 1,
            departmentId: departmentId,
            doctorId: doctorId,
            roomId: roomA,
            date: '2026-11-12',
            startTime: '08:00',
            endTime: '11:00',
            capacity: 20,
            availableCount: available,
            status: status,
          );
      expect(sessionAvailability(session('OPEN', 2), null), 'AVAILABLE');
      expect(sessionAvailability(session('OPEN', 0), null), 'FULL');
      expect(sessionAvailability(session('CLOSED', 2), null), 'NOT_OPEN');
      expect(sessionAvailability(null, null), 'NOT_OPEN');
      expect(sessionAvailability(session('OPEN', 2), 'boom'), 'LOAD_ERROR');
      expect(
        {
          for (final key in ['AVAILABLE', 'FULL', 'NOT_OPEN', 'LOAD_ERROR'])
            sessionAvailabilityLabels[key],
        }.length,
        4,
      );
    });

    test('vital input rejects a lone blood pressure value', () {
      const input = VitalSignInput(systolicMmhg: 120, notMeasuredReason: 'x');
      expect(input.validate(), 'Cần nhập cả huyết áp tâm thu và tâm trương.');
    });

    test('vital input accepts a complete set without a reason', () {
      const input = VitalSignInput(
        pulseBpm: 80,
        systolicMmhg: 120,
        diastolicMmhg: 70,
        temperatureC: 36.5,
        respiratoryRate: 18,
        spo2Percent: 98,
        weightKg: 60.5,
      );
      expect(input.validate(), isNull);
      expect(input.toJson()['notMeasuredReason'], isNull);
      expect(input.toJson()['temperatureC'], 36.5);
    });

    test('desk entry hidden when flag is off', () {
      bool visible(UserRole role, bool enabled) => visibleMainModules(
        role,
        clinicIntakeEnabled: enabled,
      ).any((module) => module.path == '/appointments/clinic-queue');
      expect(visible(UserRole.nurse, false), isFalse);
      expect(visible(UserRole.doctor, false), isFalse);
      expect(visible(UserRole.nurse, true), isTrue);
      expect(visible(UserRole.doctor, true), isTrue);
      expect(visible(UserRole.patient, true), isFalse);
    });
  });

  group('numbers', () {
    testWidgets('shows Số quầy and Số phòng khám separately when enabled', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(const PatientRegistrationsPage(), role: 'PATIENT'),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Số quầy: 7'), findsOneWidget);
      expect(find.textContaining('Số phòng khám: 3'), findsOneWidget);
      expect(find.textContaining('Số thứ tự'), findsNothing);
    });

    testWidgets('keeps Số thứ tự when disabled', (tester) async {
      await tester.pumpWidget(
        host(
          const PatientRegistrationsPage(),
          role: 'PATIENT',
          clinicIntake: false,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Số thứ tự: 7'), findsOneWidget);
      expect(find.textContaining('Số quầy'), findsNothing);
      expect(find.textContaining('Số phòng khám'), findsNothing);
    });

    testWidgets('session list offers rescheduling only when enabled', (
      tester,
    ) async {
      Widget page(bool enabled) => host(
        const SessionRegistrationsPage(
          sessionId: sessionId,
          title: 'Sáng',
          canMarkArrival: true,
        ),
        clinicIntake: enabled,
      );
      await tester.pumpWidget(page(true));
      await tester.pumpAndSettle();
      expect(find.textContaining('Số quầy 7'), findsOneWidget);
      expect(find.textContaining('Số phòng khám 3'), findsOneWidget);
      expect(find.text('Đổi lịch'), findsOneWidget);
      await tester.pumpWidget(page(false));
      await tester.pumpAndSettle();
      expect(find.text('Đổi lịch'), findsNothing);
    });
  });

  group('nurse desk', () {
    testWidgets('nurse desk lists rooms with independent numbers', (
      tester,
    ) async {
      final clinic = FakeClinic([
        entry(registration: registrationId, room: roomA, roomQueueNumber: 1),
        entry(
          registration: '66666666-6666-4666-8666-666666666667',
          room: roomB,
          roomQueueNumber: 1,
          queueNumber: 8,
        ),
        entry(
          registration: '66666666-6666-4666-8666-666666666668',
          room: roomB,
          stage: 'WAITING_ROOM_TICKET',
          roomQueueNumber: null,
          queueNumber: 9,
          ticket: null,
        ),
      ]);
      await tester.pumpWidget(host(const ClinicQueuePage(), clinic: clinic));
      await tester.pumpAndSettle();
      expect(find.textContaining('Phòng 1'), findsOneWidget);
      expect(find.textContaining('Phòng 2'), findsOneWidget);
      expect(find.textContaining('Số phòng khám 1'), findsNWidgets(2));
      expect(find.textContaining('Số quầy 8'), findsOneWidget);
      expect(find.text('Cấp số phòng'), findsOneWidget);
      expect(find.text('Chờ cấp số phòng'), findsOneWidget);
      expect(find.text('Được vào khám'), findsNWidgets(2));
    });

    testWidgets('page says it is closed when the flag is off', (tester) async {
      await tester.pumpWidget(
        host(const ClinicQueuePage(), clinicIntake: false),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('chưa được mở'), findsOneWidget);
    });

    testWidgets('doctor sees missing vitals as a warning only', (tester) async {
      final clinic = FakeClinic([entry()]);
      await tester.pumpWidget(
        host(const ClinicQueuePage(), role: 'DOCTOR', clinic: clinic),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('Chưa có sinh hiệu'), findsOneWidget);
      expect(find.text('Ghi sinh hiệu'), findsNothing);
      expect(find.text('Cấp số phòng'), findsNothing);
      await tapText(tester, 'Gọi vào khám');
      expect(clinic.keys, hasLength(1));
    });

    testWidgets('vital sheet requires reason for missing measure', (
      tester,
    ) async {
      final clinic = FakeClinic([]);
      await tester.pumpWidget(
        host(
          const VitalSignSheet(registrationId: registrationId),
          clinic: clinic,
        ),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'Mạch (lần/phút)', '80');
      await tapText(tester, 'Lưu sinh hiệu');
      expect(
        find.text('Cần ghi lý do khi chưa đo đủ các chỉ số.'),
        findsOneWidget,
      );
      expect(clinic.vitals, isEmpty);
      await enter(tester, 'Lý do chưa đo đủ', 'Bệnh nhân từ chối');
      await tapText(tester, 'Lưu sinh hiệu');
      expect(clinic.vitals.single.pulseBpm, 80);
      expect(clinic.vitals.single.notMeasuredReason, 'Bệnh nhân từ chối');
    });

    testWidgets('vital sheet asks for a dot instead of a comma', (
      tester,
    ) async {
      final clinic = FakeClinic([]);
      await tester.pumpWidget(
        host(
          const VitalSignSheet(registrationId: registrationId),
          clinic: clinic,
        ),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'Nhiệt độ (°C)', '36,5');
      await tapText(tester, 'Lưu sinh hiệu');
      expect(find.textContaining('dùng dấu chấm'), findsOneWidget);
      expect(clinic.vitals, isEmpty);
    });

    testWidgets('vital sheet shows each scale and flags an out-of-range row', (
      tester,
    ) async {
      final clinic = FakeClinic([]);
      await tester.pumpWidget(
        host(
          const VitalSignSheet(registrationId: registrationId),
          clinic: clinic,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Thang đo: 20 – 250 · Số nguyên'), findsOneWidget);
      await enter(tester, 'Mạch (lần/phút)', '300');
      await enter(tester, 'Huyết áp tâm thu (mmHg)', '120');
      await enter(tester, 'Huyết áp tâm trương (mmHg)', '130');
      await tapText(tester, 'Lưu sinh hiệu');
      expect(
        find.text('Mạch (lần/phút): ngoài thang đo 20 – 250 lần/phút.'),
        findsOneWidget,
      );
      expect(
        find.text('Huyết áp tâm trương phải nhỏ hơn tâm thu.'),
        findsOneWidget,
      );
      expect(
        find.text('Kiểm tra lại các chỉ số được đánh dấu đỏ.'),
        findsOneWidget,
      );
      expect(clinic.vitals, isEmpty);
    });
  });

  group('reschedule', () {
    testWidgets('reschedule requires reason and patient confirmation', (
      tester,
    ) async {
      final clinic = FakeClinic([]);
      await tester.pumpWidget(
        host(
          const RescheduleSheet(
            registrationId: registrationId,
            currentSessionId: sessionId,
          ),
          clinic: clinic,
        ),
      );
      await tester.pumpAndSettle();
      await tapText(tester, 'Xác nhận đổi lịch');
      expect(clinic.reschedules, isEmpty);
      expect(find.textContaining('Chọn buổi'), findsOneWidget);

      await tapText(tester, '2026-11-12 · 13:00–16:00 · Còn 3/20 chỗ');
      await tapText(tester, 'Xác nhận đổi lịch');
      expect(clinic.reschedules, isEmpty);
      expect(find.textContaining('lý do'), findsWidgets);

      await enter(tester, 'Lý do đổi lịch', 'Bệnh nhân bận');
      await tapText(tester, 'Xác nhận đổi lịch');
      expect(clinic.reschedules, isEmpty);
      expect(find.textContaining('đồng ý'), findsWidgets);

      await tapText(tester, 'Bệnh nhân đã đồng ý với lịch mới.');
      await tapText(tester, 'Xác nhận đổi lịch');
      final sent = clinic.reschedules.single;
      expect(sent.expectedRevision, 4);
      expect(sent.targetSessionId, targetSessionId);
      expect(sent.reason, 'Bệnh nhân bận');
      expect(sent.patientConfirmed, isTrue);
    });

    testWidgets('impact sheet lists affected patients', (tester) async {
      await tester.pumpWidget(
        host(
          const ScheduleImpactSheet(
            impact: ScheduleImpact(
              heldCount: 1,
              paymentLockedCount: 2,
              confirmedCount: 3,
              consumedCount: 0,
              registrations: [
                AffectedRegistration(
                  registrationId: registrationId,
                  patientId: patientId,
                  sessionId: sessionId,
                  status: 'CONFIRMED',
                  queueNumber: 7,
                  roomQueueNumber: 2,
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bệnh nhân bị ảnh hưởng'), findsOneWidget);
      expect(find.textContaining('Số quầy 7'), findsOneWidget);
      expect(find.textContaining('Số phòng khám 2'), findsOneWidget);
      expect(find.textContaining('Đã xác nhận: 3'), findsOneWidget);
    });
  });
}
