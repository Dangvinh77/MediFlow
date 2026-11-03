import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/app/app.dart';
import 'package:mediflow_mobile/app/dependencies.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/app/router.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';
import 'package:mediflow_mobile/features/auth/presentation/session_controller.dart';
import 'package:mediflow_mobile/features/lab/data/gateway_patient_lab_results.dart';
import 'package:mediflow_mobile/features/lab/domain/patient_lab_result.dart';
import 'package:mediflow_mobile/features/lab/presentation/my_lab_results_page.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_patient_exam_results.dart';
import 'package:mediflow_mobile/features/medical_record/domain/patient_exam_result.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/my_exam_results_page.dart';

import 'api_client_test.dart' show RecordingAdapter;
import 'workspace_access_test.dart' show TestSession;

const _id = '10000000-0000-4000-8000-000000000001';
Map<String, dynamic> examJson() => {
  'recordId': _id,
  'doctorId': _id,
  'departmentId': _id,
  'examinationDate': '2026-11-03',
  'completedAt': '2026-11-03T02:00:00Z',
  'symptoms': 'Đau đầu',
  'disposition': 'OUTPATIENT_FOLLOW_UP',
  'dispositionNote': 'Theo dõi tại nhà',
  'diagnoses': [
    {
      'diagnosisId': _id,
      'diagnosisName': 'Đau nửa đầu',
      'description': null,
      'icdCode': 'G43',
    },
  ],
};
Map<String, dynamic> labJson() => {
  'testId': _id,
  'recordId': _id,
  'labType': 'Công thức máu',
  'requestedDate': '2026-11-02',
  'performedDate': '2026-11-03',
  'conclusion': 'Đã có kết quả',
  'results': [
    <String, dynamic>{
      'resultId': _id,
      'indicator': 'WBC',
      'value': '< 5.0',
      'unit': 'G/L',
      'referenceRange': '4–10',
    },
  ],
};
Map<String, dynamic> _envelope(Map<String, dynamic> row) => {
  'success': true,
  'data': {
    'content': [row],
    'number': 0,
    'totalPages': 2,
    'totalElements': 21,
  },
};

class _Exams implements PatientExamResultsRepository {
  Future<PageResult<PatientExamResult>> Function(int)? read;
  int calls = 0;
  @override
  Future<PageResult<PatientExamResult>> mine({int page = 0}) {
    calls++;
    return read?.call(page) ??
        Future.value(
          PageResult(
            items: [PatientExamResultDto.fromJson(examJson())],
            number: page,
            totalPages: 2,
            totalElements: 21,
          ),
        );
  }
}

class _Labs implements PatientLabResultsRepository {
  @override
  Future<PageResult<PatientLabResult>> mine({int page = 0}) async => PageResult(
    items: [PatientLabResultDto.fromJson(labJson())],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  );
}

void main() {
  late RecordingAdapter adapter;
  late ApiClient client;
  setUp(() {
    adapter = RecordingAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'patient-token',
    );
  });
  tearDown(() => client.close());

  test(
    'mine APIs send only paging and bearer; patient scope is server owned',
    () async {
      adapter.body = _envelope(examJson());
      final exams = GatewayPatientExamResults(client);
      final page = await ReadMyExamResults(exams)(0);
      expect(page.items.single.diagnoses.single.name, 'Đau nửa đầu');
      expect(page.hasNext, isTrue);
      adapter.body = _envelope(labJson());
      final labs = GatewayPatientLabResults(client);
      expect(
        (await ReadMyLabResults(labs)(0)).items.single.results.single.value,
        '< 5.0',
      );
      expect(adapter.requests.map((r) => r.uri.path), [
        '/api/v1/records/mine',
        '/api/v1/lab/mine',
      ]);
      for (final request in adapter.requests) {
        expect(request.method, 'GET');
        expect(request.queryParameters, {'page': 0, 'size': 20});
        expect(request.headers['Authorization'], 'Bearer patient-token');
        expect(request.data, isNull);
      }
      await expectLater(exams.mine(page: -1), throwsArgumentError);
      await expectLater(labs.mine(page: -1), throwsArgumentError);
      expect(() => ReadMyExamResults(exams)(-1), throwsArgumentError);
      expect(() => ReadMyLabResults(labs)(-1), throwsArgumentError);
      expect(adapter.requests, hasLength(2));
    },
  );

  test(
    'patient wire accepts nullable summaries and rejects malformed children',
    () {
      final exam = examJson()
        ..['symptoms'] = null
        ..['disposition'] = null;
      expect(PatientExamResultDto.fromJson(exam).symptoms, isNull);
      for (final field in ['recordId', 'completedAt', 'diagnoses']) {
        expect(
          () => PatientExamResultDto.fromJson(examJson()..remove(field)),
          throwsFormatException,
        );
      }
      expect(
        () => PatientExamResultDto.fromJson(examJson()..['diagnoses'] = [null]),
        throwsFormatException,
      );
      expect(
        () => PatientLabResultDto.fromJson(labJson()..['results'] = 'invalid'),
        throwsFormatException,
      );
      final invalid = labJson();
      (invalid['results'] as List).single['value'] = 5;
      expect(
        () => PatientLabResultDto.fromJson(invalid),
        throwsFormatException,
      );
      expect(
        () => PatientLabResultDto.fromJson(labJson()..remove('performedDate')),
        throwsFormatException,
      );
    },
  );

  test(
    'denied and unavailable remain errors without automatic retries',
    () async {
      for (final status in [403, 503]) {
        adapter.status = status;
        adapter.body = {
          'success': false,
          'error': {'code': 'DENIED', 'message': 'Không thể truy cập'},
        };
        await expectLater(
          GatewayPatientExamResults(client).mine(),
          throwsA(isA<ApiFailure>()),
        );
        await expectLater(
          GatewayPatientLabResults(client).mine(),
          throwsA(isA<ApiFailure>()),
        );
      }
      expect(adapter.requests, hasLength(4));
    },
  );

  Widget host(
    Widget child, {
    String role = 'PATIENT',
    Object identity = 'a',
    _Exams? exams,
  }) => ProviderScope(
    overrides: [
      workspaceAccessProvider.overrideWithValue(
        WorkspaceAccess(sessionIdentity: identity, role: role),
      ),
      patientExamResultsProvider.overrideWithValue(exams ?? _Exams()),
      patientLabResultsProvider.overrideWithValue(_Labs()),
    ],
    child: MaterialApp(home: Scaffold(body: child)),
  );

  testWidgets('patient sees diagnosis details and server pagination', (
    tester,
  ) async {
    await tester.pumpWidget(host(const MyExamResultsPage()));
    await tester.pumpAndSettle();
    expect(find.text('Đau nửa đầu'), findsOneWidget);
    await tester.tap(find.text('Khám ngày 2026-11-03'));
    await tester.pumpAndSettle();
    expect(find.text('Mã ICD: G43'), findsOneWidget);
    expect(find.text('Hướng xử trí: Hẹn tái khám ngoại trú'), findsOneWidget);
    await tester.tap(find.byTooltip('Trang sau'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Trang 2/2'), findsOneWidget);
    expect(find.text(_id), findsNothing);
  });
  testWidgets('patient sees textual lab value, range and conclusion', (
    tester,
  ) async {
    await tester.pumpWidget(host(const MyLabResultsPage()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Công thức máu'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Kết quả: < 5.0 G/L'), findsOneWidget);
    expect(find.textContaining('Khoảng tham chiếu: 4–10'), findsOneWidget);
    expect(find.text('Kết luận: Đã có kết quả'), findsOneWidget);
  });
  testWidgets('staff cannot load patient summary pages', (tester) async {
    final exams = _Exams();
    await tester.pumpWidget(
      host(const MyExamResultsPage(), role: 'DOCTOR', exams: exams),
    );
    await tester.pumpAndSettle();
    expect(exams.calls, 0);
    expect(
      find.text('Chức năng dành cho bệnh nhân đăng nhập.'),
      findsOneWidget,
    );
    expect(
      modules
          .where((m) => m.path.startsWith('/my-results'))
          .every(
            (m) => m.allows(UserRole.patient) && !m.allows(UserRole.admin),
          ),
      isTrue,
    );
  });
  testWidgets('late private response cannot cross a session switch', (
    tester,
  ) async {
    final pending = Completer<PageResult<PatientExamResult>>();
    final old = _Exams()..read = (_) => pending.future;
    await tester.pumpWidget(host(const MyExamResultsPage(), exams: old));
    await tester.pump();
    await tester.pumpWidget(
      host(
        const MyExamResultsPage(),
        identity: 'b',
        exams: _Exams()
          ..read = (_) async => const PageResult(
            items: [],
            number: 0,
            totalPages: 0,
            totalElements: 0,
          ),
      ),
    );
    await tester.pumpAndSettle();
    pending.complete(
      PageResult(
        items: [PatientExamResultDto.fromJson(examJson())],
        number: 0,
        totalPages: 1,
        totalElements: 1,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Đau nửa đầu'), findsNothing);
    expect(find.text('Chưa có dữ liệu phù hợp.'), findsOneWidget);
  });
  testWidgets('permission denial shows correlation with no retry button', (
    tester,
  ) async {
    final exams = _Exams()
      ..read = (_) async => throw const ApiFailure(
        'Thiếu liên kết bệnh nhân',
        status: 403,
        correlationId: 'trace-patient',
      );
    await tester.pumpWidget(host(const MyExamResultsPage(), exams: exams));
    await tester.pumpAndSettle();
    expect(find.text('Thiếu liên kết bệnh nhân'), findsOneWidget);
    expect(find.text('Mã hỗ trợ: trace-patient'), findsOneWidget);
    expect(find.text('Thử lại'), findsNothing);
  });

  testWidgets('app binds both patient routes and denies staff deep links', (
    tester,
  ) async {
    final container = ProviderContainer(
      overrides: [
        ...appOverrides,
        sessionProvider.overrideWith(TestSession.new),
        apiClientProvider.overrideWithValue(client),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    final session = container.read(sessionProvider.notifier) as TestSession;
    session.replace(
      const Session(
        accessToken: 'patient',
        refreshToken: 'r',
        role: UserRole.patient,
      ),
    );
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MediFlowApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Kết quả khám của tôi'), findsOneWidget);
    expect(find.text('Kết quả xét nghiệm của tôi'), findsOneWidget);
    adapter.body = _envelope(examJson());
    container.read(routerProvider).go('/my-results/exams');
    await tester.pumpAndSettle();
    expect(find.text('Đau nửa đầu'), findsOneWidget);
    adapter.body = _envelope(labJson());
    container.read(routerProvider).go('/my-results/lab');
    await tester.pumpAndSettle();
    expect(find.text('Công thức máu'), findsOneWidget);
    session.replace(
      const Session(
        accessToken: 'staff',
        refreshToken: 'r',
        role: UserRole.doctor,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Công thức máu'), findsNothing);
    expect(
      find.text('Bạn không có quyền truy cập chức năng này.'),
      findsOneWidget,
    );
    expect(adapter.requests, hasLength(2));
  });
}
