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
import 'package:mediflow_mobile/features/lab/data/gateway_lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/data/lab_review_dto.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_catalog.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_review.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_test.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_detail_page.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_review_page.dart';
import 'package:mediflow_mobile/features/lab/presentation/lab_page.dart';

const _id = '10000000-0000-4000-8000-000000000001';
const _noticeId = '10000000-0000-4000-8000-000000000002';

Map<String, dynamic> _notice({String status = 'OPEN'}) => {
  'noticeId': _noticeId,
  'testId': _id,
  'resultVersion': 1,
  'fieldCode': 'K',
  'label': 'Kali',
  'value': '6.1',
  'unit': 'mmol/L',
  'flag': 'CRITICAL_HIGH',
  'patientId': _id,
  'departmentId': _id,
  'status': status,
  'acknowledgedBy': status == 'OPEN' ? null : _id,
  'acknowledgedAt': status == 'OPEN' ? null : '2026-11-11T02:00:00Z',
  'createdAt': '2026-11-11T01:00:00Z',
};

Map<String, dynamic> _worklist({
  String testId = _id,
  String displayGroup = 'HEMATOLOGY',
  bool overdue = true,
  String serviceName = 'Công thức máu',
}) => {
  'testId': testId,
  'patientId': _id,
  'labType': 'Công thức máu',
  'testStatus': 'IN_PROGRESS',
  'serviceCode': 'CBC',
  'serviceName': serviceName,
  'displayGroup': displayGroup,
  'specimenStatus': 'ACCEPTED',
  'reportStatus': 'DRAFT',
  'reportVersion': 1,
  'dueAt': '2026-11-10T10:00:00Z',
  'overdue': overdue,
};

class _Adapter implements HttpClientAdapter {
  Object? body;
  int status = 200;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class _ReviewFake implements LabReviewRepository {
  List<LabCriticalNotice> notices = [LabCriticalNoticeDto.fromJson(_notice())];
  List<LabWorklistItem> worklist = [
    LabWorklistItemDto.fromJson(_worklist()),
    LabWorklistItemDto.fromJson(
      _worklist(
        testId: _noticeId,
        displayGroup: 'BIOCHEMISTRY',
        overdue: false,
        serviceName: 'Glucose',
      ),
    ),
  ];
  Object? noticeFailure;
  Object? acknowledgeFailure;
  final acknowledged = <String>[];

  @override
  Future<List<LabCriticalNotice>> criticalNotices({
    String status = 'OPEN',
  }) async {
    if (noticeFailure case final failure?) throw failure;
    return notices;
  }

  @override
  Future<LabCriticalNotice> acknowledge(String testId, String noticeId) async {
    if (acknowledgeFailure case final failure?) throw failure;
    acknowledged.add(noticeId);
    final result = LabCriticalNoticeDto.fromJson(
      _notice(status: 'ACKNOWLEDGED'),
    );
    notices = [result];
    return result;
  }

  @override
  Future<List<LabWorklistItem>> worklistItems() async => worklist;
}

class _LabFake implements LabRepository {
  @override
  Future<PageResult<LabTest>> search({int page = 0, String? status}) async =>
      const PageResult(items: [], number: 0, totalPages: 0, totalElements: 0);
}

void main() {
  late _Adapter adapter;
  late ApiClient client;
  late GatewayLabWorkflow workflow;

  setUp(() {
    adapter = _Adapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    workflow = GatewayLabWorkflow(client);
  });
  tearDown(() => client.close());

  test(
    'critical notice DTO mirrors wire fields and optional acknowledgement',
    () {
      final parsed = LabCriticalNoticeDto.fromJson(_notice());
      expect(parsed.noticeId, _noticeId);
      expect(parsed.resultVersion, 1);
      expect(parsed.unit, 'mmol/L');
      expect(parsed.acknowledgedAt, isNull);
      expect(
        () => LabCriticalNoticeDto.fromJson(_notice()..['resultVersion'] = '1'),
        throwsFormatException,
      );
    },
  );

  test('worklist DTO parses optional values and overdue flag', () {
    final json = _worklist()..['serviceCode'] = null;
    final item = LabWorklistItemDto.fromJson(json);
    expect(item.displayGroup, 'HEMATOLOGY');
    expect(item.serviceCode, isNull);
    expect(item.overdue, isTrue);
    expect(
      () => LabWorklistItemDto.fromJson(_worklist()..['overdue'] = 'true'),
      throwsFormatException,
    );
  });

  test(
    'gateway reads OPEN notices and posts acknowledgement to the test route',
    () async {
      adapter.body = {
        'success': true,
        'data': [_notice()],
      };
      final notices = await workflow.criticalNotices();
      expect(notices.single.noticeId, _noticeId);
      adapter.body = {'success': true, 'data': _notice(status: 'ACKNOWLEDGED')};
      final updated = await workflow.acknowledge(_id, _noticeId);
      expect(updated.status, 'ACKNOWLEDGED');
      expect(adapter.requests.map((request) => request.uri.path), [
        '/api/v2/lab/critical-notices',
        '/api/v2/lab/$_id/critical-notices/$_noticeId/acknowledgement',
      ]);
      expect(adapter.requests.first.queryParameters, {'status': 'OPEN'});
      expect(adapter.requests.last.method, 'POST');
    },
  );

  test(
    'gateway reads the v2 worklist and maps feature-disabled response',
    () async {
      adapter.body = {
        'success': true,
        'data': [_worklist()],
      };
      expect((await workflow.worklistItems()).single.testId, _id);
      expect(adapter.requests.single.uri.path, '/api/v2/lab/worklist');

      adapter.status = 404;
      adapter.body = {
        'success': false,
        'error': {'code': 'LAB_REVIEW_DISABLED', 'message': 'disabled'},
      };
      await expectLater(
        workflow.worklistItems(),
        throwsA(isA<LabReviewUnavailable>()),
      );
    },
  );

  testWidgets('critical notices acknowledge and show acknowledged status', (
    tester,
  ) async {
    final repo = _ReviewFake();
    await tester.pumpWidget(_host(const CriticalNoticesPage(), repo));
    await tester.pumpAndSettle();
    expect(find.text('6.1 mmol/L'), findsOneWidget);
    await tester.tap(find.text('Xác nhận đã nhận'));
    await tester.pumpAndSettle();
    expect(repo.acknowledged, [_noticeId]);
    expect(find.text('Đã xác nhận'), findsOneWidget);
    expect(find.text('Xác nhận đã nhận'), findsNothing);
  });

  testWidgets('critical notice shows inline responsible doctor 403 message', (
    tester,
  ) async {
    final repo = _ReviewFake()
      ..acknowledgeFailure = const ApiFailure(
        'denied',
        status: 403,
        code: 'LAB_CRITICAL_NOT_RESPONSIBLE',
      );
    await tester.pumpWidget(_host(const CriticalNoticesPage(), repo));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Xác nhận đã nhận'));
    await tester.pumpAndSettle();
    expect(
      find.text('Chỉ bác sĩ của khoa chỉ định được xác nhận'),
      findsOneWidget,
    );
  });

  testWidgets('worklist filter narrows rows and marks overdue item', (
    tester,
  ) async {
    final repo = _ReviewFake();
    await tester.pumpWidget(
      _host(const LabWorklistPage(), repo, role: 'LAB_TECH'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Công thức máu'), findsOneWidget);
    expect(find.text('Quá hạn'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('lab-worklist-group')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('BIOCHEMISTRY').last);
    await tester.pumpAndSettle();
    expect(find.text('Công thức máu'), findsNothing);
    expect(find.text('Quá hạn'), findsNothing);
    expect(find.text('Glucose'), findsOneWidget);
  });

  testWidgets('v2 review disabled is shown without hiding other lab areas', (
    tester,
  ) async {
    final repo = _ReviewFake()..noticeFailure = const LabReviewUnavailable();
    await tester.pumpWidget(_host(const CriticalNoticesPage(), repo));
    await tester.pumpAndSettle();
    expect(find.text('Chức năng chưa được bật'), findsOneWidget);
  });
}

Widget _host(Widget child, _ReviewFake repository, {String role = 'DOCTOR'}) =>
    ProviderScope(
      overrides: [
        workspaceAccessProvider.overrideWithValue(
          WorkspaceAccess(sessionIdentity: 'staff', role: role),
        ),
        labReviewRepositoryProvider.overrideWithValue(repository),
        labRepositoryProvider.overrideWithValue(_LabFake()),
        labWorkflowProvider.overrideWithValue(_LegacyWorkflow()),
      ],
      child: MaterialApp(home: Scaffold(body: child)),
    );

class _LegacyWorkflow implements LabWorkflow {
  @override
  Future<LabDetail> detail(String id) => throw UnimplementedError();
  @override
  Future<LabTestTemplate> template(String testId) => throw UnimplementedError();
  @override
  Future<LabDetail> start(String id) => throw UnimplementedError();
  @override
  Future<LabDetail> results(
    String id,
    List<LabResultDraft> results,
    String? conclusion,
    String date,
  ) => throw UnimplementedError();
  @override
  Future<LabDetail> cancel(String id, String reason) =>
      throw UnimplementedError();
}
