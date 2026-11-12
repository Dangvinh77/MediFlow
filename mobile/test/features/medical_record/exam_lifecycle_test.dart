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
import 'package:mediflow_mobile/features/medical_record/data/gateway_exam_lifecycle_repository.dart';
import 'package:mediflow_mobile/features/medical_record/data/medical_record_dto.dart';
import 'package:mediflow_mobile/features/medical_record/domain/exam_lifecycle.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_page.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/record_lab_orders_section.dart';

import '../../clinical_completion_workflow_test.dart' as fixtures;

class LifecycleFake implements ExamLifecycleRepository {
  Future<List<RecordLabOrder>> Function()? onOrders;
  Future<MedicalRecord> Function()? onClose;
  final closes = <({String reason, String note, String key})>[];
  final withdrawals = <({String order, String reason, String key})>[];
  int orderReads = 0;

  @override
  Future<void> withdrawLabOrder(
    String recordId,
    String sourceOrderId, {
    required String reason,
    required String idempotencyKey,
  }) async {
    withdrawals.add((
      order: sourceOrderId,
      reason: reason,
      key: idempotencyKey,
    ));
  }

  @override
  Future<List<RecordLabOrder>> labOrders(String recordId) {
    orderReads++;
    return onOrders?.call() ?? Future.value(const <RecordLabOrder>[]);
  }

  @override
  Future<MedicalRecord> closeWithoutConclusion(
    String recordId, {
    required String reason,
    required String note,
    required String idempotencyKey,
  }) {
    closes.add((reason: reason, note: note, key: idempotencyKey));
    return onClose?.call() ?? Future.value(closed());
  }
}

MedicalRecord closed({String? previous}) => MedicalRecord(
  id: fixtures.recordId,
  patientId: fixtures.patientId,
  doctorId: fixtures.doctorId,
  departmentId: fixtures.departmentId,
  examinationDate: '2026-10-22',
  appointmentId: fixtures.appointmentId,
  diagnoses: const [],
  createdAt: '2026-10-22T08:00:00Z',
  status: 'CLOSED_WITHOUT_CONCLUSION',
  closureReason: 'PATIENT_LEFT',
  closureNote: 'Tự ý về, đã dặn tái khám',
  closedAt: '2026-10-22T09:00:00Z',
  previousRecordId: previous,
);

MedicalRecord open({List<Diagnosis> diagnoses = const []}) => MedicalRecord(
  id: fixtures.recordId,
  patientId: fixtures.patientId,
  doctorId: fixtures.doctorId,
  departmentId: fixtures.departmentId,
  examinationDate: '2026-10-22',
  appointmentId: fixtures.appointmentId,
  diagnoses: diagnoses,
  createdAt: '2026-10-22T08:00:00Z',
  status: 'OPEN',
);

const order = RecordLabOrder(
  sourceOrderId: 'order-1',
  testId: 'test-1',
  labType: 'Công thức máu',
  status: 'IN_PROGRESS',
  orderedAt: '2026-10-22T08:10:00Z',
);

Widget lifecycleHost({
  required LifecycleFake lifecycle,
  required fixtures.MedicalRecordFake records,
  String role = 'DOCTOR',
  Future<void> Function(String testId, String reason)? cancel,
  Widget child = const MedicalRecordDetailPage(recordId: fixtures.recordId),
}) => ProviderScope(
  overrides: [
    workspaceAccessProvider.overrideWithValue(
      WorkspaceAccess(role: role, sessionIdentity: 'session-one'),
    ),
    referenceReaderProvider.overrideWithValue(fixtures.ReferenceFake()),
    medicalRecordRepositoryProvider.overrideWithValue(records),
    medicalRecordOutpatientUiEnabledProvider.overrideWithValue(true),
    examLifecycleRepositoryProvider.overrideWithValue(lifecycle),
    if (cancel != null) recordLabCancelProvider.overrideWithValue(cancel),
  ],
  child: MaterialApp(home: Scaffold(body: child)),
);

void tallScreen(WidgetTester tester) {
  tester.view.physicalSize = const Size(900, 3200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> openSheet(WidgetTester tester) async {
  await tester.tap(find.text('Kết thúc không kết luận'));
  await tester.pumpAndSettle();
}

Future<void> fillAndConfirm(WidgetTester tester, String note) async {
  await tester.enterText(find.byKey(const Key('close-record-note')), note);
  await tester.tap(find.byKey(const Key('close-record-confirm')));
  await tester.pump();
}

FilledButton closeButton(WidgetTester tester) =>
    tester.widget<FilledButton>(find.byKey(const Key('close-record-submit')));

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
  group('closing a visit without a conclusion', () {
    testWidgets('closes with reason and note', (tester) async {
      tallScreen(tester);
      final lifecycle = LifecycleFake();
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: lifecycle, records: records),
      );
      await tester.pumpAndSettle();
      await openSheet(tester);
      await fillAndConfirm(tester, '  Tự ý về, đã dặn quay lại  ');
      await tester.tap(find.byKey(const Key('close-record-submit')));
      await tester.pumpAndSettle();
      expect(lifecycle.closes.single.reason, 'PATIENT_LEFT');
      expect(lifecycle.closes.single.note, 'Tự ý về, đã dặn quay lại');
      expect(lifecycle.closes.single.key, isNotEmpty);
      expect(find.text('Tự ý về, đã dặn tái khám'), findsOneWidget);
      expect(find.byKey(const Key('close-record-submit')), findsNothing);
    });

    testWidgets('close needs a note and offers no diagnosis field', (
      tester,
    ) async {
      tallScreen(tester);
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: LifecycleFake(), records: records),
      );
      await tester.pumpAndSettle();
      await openSheet(tester);
      await fillAndConfirm(tester, '   ');
      expect(closeButton(tester).onPressed, isNull);
      final sheet = find.byKey(const Key('close-record-sheet'));
      expect(
        find.descendant(of: sheet, matching: find.textContaining('chẩn đoán')),
        findsNothing,
      );
    });

    testWidgets('lost response resends the same key', (tester) async {
      tallScreen(tester);
      var attempts = 0;
      final lifecycle = LifecycleFake()
        ..onClose = () async {
          if (attempts++ == 0) throw const ApiFailure('Mất kết nối');
          return closed();
        };
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: lifecycle, records: records),
      );
      await tester.pumpAndSettle();
      await openSheet(tester);
      await fillAndConfirm(tester, 'Tự ý về');
      await tester.tap(find.byKey(const Key('close-record-submit')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Kết quả chưa rõ'), findsOneWidget);
      await tester.tap(find.byKey(const Key('close-record-submit')));
      await tester.pumpAndSettle();
      expect(lifecycle.closes, hasLength(2));
      expect(lifecycle.closes[0].key, lifecycle.closes[1].key);
    });

    testWidgets('pending lab order points to the orders section', (
      tester,
    ) async {
      tallScreen(tester);
      final lifecycle = LifecycleFake()
        ..onClose = () async => throw const ApiFailure(
          'Còn chỉ định',
          status: 422,
          code: 'CLINICAL_LAB_ORDER_PENDING',
        );
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: lifecycle, records: records),
      );
      await tester.pumpAndSettle();
      await openSheet(tester);
      await fillAndConfirm(tester, 'Tự ý về');
      await tester.tap(find.byKey(const Key('close-record-submit')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Hủy hoặc hoãn'), findsOneWidget);
    });

    testWidgets('order never sent to Lab points to withdrawal', (tester) async {
      tallScreen(tester);
      final lifecycle = LifecycleFake()
        ..onClose = () async => throw const ApiFailure(
          'Chưa tới Lab',
          status: 422,
          code: 'CLINICAL_LAB_ORDER_NOT_FOUND',
        );
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: lifecycle, records: records),
      );
      await tester.pumpAndSettle();
      await openSheet(tester);
      await fillAndConfirm(tester, 'Tự ý về');
      await tester.tap(find.byKey(const Key('close-record-submit')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Rút chỉ định'), findsOneWidget);
    });

    testWidgets('closed record shows reason and no write actions', (
      tester,
    ) async {
      tallScreen(tester);
      final records = fixtures.MedicalRecordFake()
        ..onGet = (_) async => closed();
      await tester.pumpWidget(
        lifecycleHost(lifecycle: LifecycleFake(), records: records),
      );
      await tester.pumpAndSettle();
      expect(find.text('Kết thúc không kết luận'), findsOneWidget);
      expect(find.text('Lý do: Bệnh nhân tự ý về'), findsOneWidget);
      expect(find.text('Thêm chẩn đoán'), findsNothing);
      expect(find.text('Đã hoàn tất khám'), findsNothing);
    });

    testWidgets('shows link to previous record', (tester) async {
      tallScreen(tester);
      final records = fixtures.MedicalRecordFake()
        ..onGet = (_) async => closed(previous: 'record-old');
      await tester.pumpWidget(
        lifecycleHost(lifecycle: LifecycleFake(), records: records),
      );
      await tester.pumpAndSettle();
      expect(find.text('Lượt trước — chỉ tham khảo'), findsOneWidget);
    });

    testWidgets('a nurse is not offered closing', (tester) async {
      tallScreen(tester);
      final records = fixtures.MedicalRecordFake()..onGet = (_) async => open();
      await tester.pumpWidget(
        lifecycleHost(
          lifecycle: LifecycleFake(),
          records: records,
          role: 'NURSE',
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Kết thúc không kết luận'), findsNothing);
    });
  });

  group('lab orders of a record', () {
    Widget section(
      LifecycleFake lifecycle, {
      Future<void> Function(String, String)? cancel,
      bool canChange = true,
    }) => lifecycleHost(
      lifecycle: lifecycle,
      records: fixtures.MedicalRecordFake(),
      cancel: cancel,
      child: SingleChildScrollView(
        child: RecordLabOrdersSection(
          recordId: 'record-a',
          canChange: canChange,
        ),
      ),
    );

    const unreceived = RecordLabOrder(
      sourceOrderId: 'order-2',
      labType: 'Nước tiểu',
      status: 'NOT_RECEIVED',
      orderedAt: '2026-10-22T08:10:00Z',
    );

    testWidgets('withdraws an order Lab never received', (tester) async {
      final lifecycle = LifecycleFake()
        ..onOrders = () async => const [unreceived];
      await tester.pumpWidget(section(lifecycle));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Rút chỉ định'));
      await tester.pumpAndSettle();
      final confirm = find.byKey(const Key('lab-order-action-confirm'));
      expect(tester.widget<FilledButton>(confirm).onPressed, isNull);
      expect(find.text('Lý do rút chỉ định'), findsOneWidget);
      await tester.enterText(
        find.byKey(const Key('lab-order-action-reason')),
        ' Không lấy mẫu ',
      );
      await tester.pump();
      await tester.tap(confirm);
      await tester.pumpAndSettle();
      final sent = lifecycle.withdrawals.single;
      expect(sent.order, 'order-2');
      expect(sent.reason, 'Không lấy mẫu');
      expect(sent.key, isNotEmpty);
      expect(lifecycle.orderReads, 2);
    });

    testWidgets('withdrawal is offered only while the visit can change', (
      tester,
    ) async {
      final lifecycle = LifecycleFake()
        ..onOrders = () async => const [unreceived, order];
      await tester.pumpWidget(section(lifecycle, canChange: false));
      await tester.pumpAndSettle();
      expect(find.text('Rút chỉ định'), findsNothing);
      await tester.pumpWidget(
        section(
          LifecycleFake()..onOrders = () async => const [unreceived, order],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Rút chỉ định'), findsOneWidget);
    });

    testWidgets('shows each order status from source', (tester) async {
      final lifecycle = LifecycleFake()
        ..onOrders = () async => const [
          order,
          RecordLabOrder(
            sourceOrderId: 'order-2',
            labType: 'Nước tiểu',
            status: 'NOT_RECEIVED',
            orderedAt: '2026-10-22T08:10:00Z',
          ),
        ];
      await tester.pumpWidget(section(lifecycle));
      await tester.pumpAndSettle();
      expect(find.text('Đang thực hiện'), findsOneWidget);
      expect(find.text('Lab chưa tiếp nhận'), findsOneWidget);
    });

    testWidgets('outage shows unavailable not empty', (tester) async {
      final lifecycle = LifecycleFake()
        ..onOrders = () async => throw const ApiFailure('Lỗi', status: 503);
      await tester.pumpWidget(section(lifecycle));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Không kiểm tra được trạng thái xét nghiệm'),
        findsOneWidget,
      );
      expect(find.text('Chưa có chỉ định xét nghiệm.'), findsNothing);
    });

    testWidgets('offers actions only on orders still in progress', (
      tester,
    ) async {
      final lifecycle = LifecycleFake()
        ..onOrders = () async => const [
          RecordLabOrder(
            sourceOrderId: 'order-1',
            testId: 'test-1',
            labType: 'Công thức máu',
            status: 'COMPLETED',
            orderedAt: '2026-10-22T08:10:00Z',
          ),
        ];
      await tester.pumpWidget(section(lifecycle, cancel: (_, _) async {}));
      await tester.pumpAndSettle();
      expect(find.text('Hủy chỉ định'), findsNothing);
    });

    testWidgets('cancel needs a reason', (tester) async {
      final calls = <(String, String)>[];
      final lifecycle = LifecycleFake()..onOrders = () async => const [order];
      await tester.pumpWidget(
        section(
          lifecycle,
          cancel: (id, reason) async => calls.add((id, reason)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hủy chỉ định'));
      await tester.pumpAndSettle();
      final confirm = find.byKey(const Key('lab-order-action-confirm'));
      expect(tester.widget<FilledButton>(confirm).onPressed, isNull);
      await tester.enterText(
        find.byKey(const Key('lab-order-action-reason')),
        'Chỉ định nhầm',
      );
      await tester.pump();
      await tester.tap(confirm);
      await tester.pumpAndSettle();
      expect(calls.single, ('test-1', 'Chỉ định nhầm'));
      expect(lifecycle.orderReads, 2);
    });

    testWidgets('postpone prefixes the reason', (tester) async {
      final calls = <(String, String)>[];
      final lifecycle = LifecycleFake()..onOrders = () async => const [order];
      await tester.pumpWidget(
        section(
          lifecycle,
          cancel: (id, reason) async => calls.add((id, reason)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hoãn sang lần sau'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('lab-order-action-reason')),
        'Chưa nhịn ăn',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('lab-order-action-confirm')));
      await tester.pumpAndSettle();
      expect(calls.single, ('test-1', 'Hoãn sang lần khám sau: Chưa nhịn ăn'));
    });
  });

  group('gateway', () {
    late Transport adapter;
    late ApiClient client;
    setUp(() {
      adapter = Transport();
      client = ApiClient(
        AppConfig('http://localhost:28080/api'),
        dio: Dio()..httpClientAdapter = adapter,
        token: () => 'doctor-token',
      );
    });
    tearDown(() => client.close());

    const recordJson = {
      'recordId': fixtures.recordId,
      'patientId': fixtures.patientId,
      'doctorId': fixtures.doctorId,
      'departmentId': fixtures.departmentId,
      'examinationDate': '2026-10-22',
      'diagnoses': <Object>[],
      'createdAt': '2026-10-22T08:00:00Z',
      'status': 'CLOSED_WITHOUT_CONCLUSION',
      'closureReason': 'OTHER',
      'closureNote': 'Ghi chú',
      'closedAt': '2026-10-22T09:00:00Z',
      'previousRecordId': 'record-old',
    };

    test('close sends the reason under an idempotency key', () async {
      adapter.data = recordJson;
      final result = await GatewayExamLifecycleRepository(client)
          .closeWithoutConclusion(
            fixtures.recordId,
            reason: 'OTHER',
            note: 'Ghi chú',
            idempotencyKey: 'key-1',
          );
      final request = adapter.requests.single;
      expect(request.method, 'PUT');
      expect(
        request.path,
        endsWith('v2/records/${fixtures.recordId}/close-without-conclusion'),
      );
      expect(request.headers['Idempotency-Key'], 'key-1');
      expect(request.data, {'reason': 'OTHER', 'note': 'Ghi chú'});
      expect(result.status, 'CLOSED_WITHOUT_CONCLUSION');
    });

    test(
      'withdrawal sends the trimmed reason under an idempotency key',
      () async {
        adapter.data = {
          'sourceOrderId': 'order-1',
          'labType': 'Máu',
          'status': 'WITHDRAWN',
          'orderedAt': '2026-10-22T08:10:00Z',
        };
        await GatewayExamLifecycleRepository(client).withdrawLabOrder(
          fixtures.recordId,
          'order-1',
          reason: ' Không lấy mẫu ',
          idempotencyKey: 'key-w',
        );
        final request = adapter.requests.single;
        expect(request.method, 'PUT');
        expect(
          request.path,
          endsWith(
            'v2/records/${fixtures.recordId}/lab-orders/order-1/withdrawal',
          ),
        );
        expect(request.headers['Idempotency-Key'], 'key-w');
        expect(request.data, {'reason': 'Không lấy mẫu'});
      },
    );

    test('a null test id means the lab has not received the order', () async {
      adapter.data = [
        {
          'sourceOrderId': 'order-1',
          'labType': 'Máu',
          'status': 'NOT_RECEIVED',
          'orderedAt': '2026-10-22T08:10:00Z',
        },
      ];
      final orders = await GatewayExamLifecycleRepository(client)
          .labOrders(fixtures.recordId);
      expect(orders.single.testId, isNull);
      expect(orders.single.inProgress, isFalse);
    });

    test('dto keeps closure and previous visit fields', () {
      final record = MedicalRecordDto.fromJson(recordJson);
      expect(record.closureReason, 'OTHER');
      expect(record.closureNote, 'Ghi chú');
      expect(record.closedAt, '2026-10-22T09:00:00Z');
      expect(record.previousRecordId, 'record-old');
    });

    test('blank close note is refused before sending', () async {
      await expectLater(
        GatewayExamLifecycleRepository(client).closeWithoutConclusion(
          fixtures.recordId,
          reason: 'OTHER',
          note: '  ',
          idempotencyKey: 'key-1',
        ),
        throwsArgumentError,
      );
      expect(adapter.requests, isEmpty);
    });
  });
}
