import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_loader/mediflow_loader.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_patient_timeline_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/domain/patient_timeline.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/patient_timeline_section.dart';

const _patient = '10000000-0000-4000-8000-000000000001';
const _record = '10000000-0000-4000-8000-000000000002';
const _doctor = '10000000-0000-4000-8000-000000000003';
const _department = '10000000-0000-4000-8000-000000000004';

TimelineEntry _entry({
  String id = _record,
  String kind = 'NEW',
  String status = 'OPEN',
  String? previousRecordId,
  String? responsibleDoctorId,
}) => TimelineEntry(
  recordId: id,
  examinationDate: '2026-11-10',
  departmentId: _department,
  doctorId: _doctor,
  responsibleDoctorId: responsibleDoctorId,
  visitKind: kind,
  status: status,
  disposition: null,
  previousRecordId: previousRecordId,
  treatmentCourseIds: const [],
);

class TimelineFake implements PatientTimelineRepository {
  Future<TimelinePage> Function({
    required int page,
    String? departmentId,
    String? status,
  })? onTimeline;
  final queries = <({int page, String? departmentId, String? status})>[];
  @override
  Future<TimelinePage> timeline(
    String patientId, {
    int page = 0,
    int size = 20,
    String? departmentId,
    String? status,
  }) {
    queries.add((page: page, departmentId: departmentId, status: status));
    return onTimeline?.call(
          page: page,
          departmentId: departmentId,
          status: status,
        ) ??
        Future.value(
          TimelinePage(
            entries: [_entry()],
            page: page,
            size: size,
            totalPages: 2,
            totalElements: 21,
          ),
        );
  }

  @override
  Future<List<RecordDocument>> documents(String recordId) async => const [];

  @override
  Future<MedicalRecord> updateContent(
    String recordId, {
    required Map<String, String?> changes,
    required String idempotencyKey,
  }) => throw UnimplementedError();
}

class TimelineTransport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? responseData;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': responseData}),
      200,
      headers: {Headers.contentTypeHeader: [Headers.jsonContentType]},
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  group('patient visit timeline', () {
    test('DTO reads exact visit metadata and optional fields', () {
      final entry = TimelineEntryDto.fromJson({
        'recordId': _record,
        'examinationDate': '2026-11-10',
        'departmentId': _department,
        'doctorId': _doctor,
        'responsibleDoctorId': '10000000-0000-4000-8000-000000000005',
        'visitKind': 'FOLLOW_UP',
        'status': 'OPEN',
        'disposition': 'PRESCRIPTION',
        'previousRecordId': null,
        'treatmentCourseIds': <String>[],
      });

      expect(entry.recordId, _record);
      expect(entry.departmentId, _department);
      expect(entry.doctorId, _doctor);
      expect(entry.responsibleDoctorId, isNotNull);
      expect(entry.visitKindLabel, 'Tái khám');
      expect(entry.status, 'OPEN');
      expect(entry.previousRecordId, isNull);
    });

    testWidgets('shows department doctor kind and status', (tester) async {
      final repository = TimelineFake()
        ..onTimeline = ({required page, departmentId, status}) async =>
            TimelinePage(
              entries: [_entry(kind: 'RETURN', status: 'COMPLETED')],
              page: page,
              size: 20,
              totalPages: 1,
              totalElements: 1,
            );
      await tester.pumpWidget(_host(repository));
      await tester.pumpAndSettle();

      expect(find.text('Khoa: $_department'), findsOneWidget);
      expect(find.text('Bác sĩ mở hồ sơ: $_doctor'), findsOneWidget);
      expect(find.text('Quay lại'), findsOneWidget);
      expect(find.text('Đã hoàn tất khám'), findsOneWidget);
    });

    testWidgets('filters by department and status and pages forward', (
      tester,
    ) async {
      final repository = TimelineFake();
      await tester.pumpWidget(_host(repository));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('timeline-department-filter')));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_department).last);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('timeline-status-filter')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đang khám').last);
      await tester.pumpAndSettle();
      expect(repository.queries.last.departmentId, _department);
      expect(repository.queries.last.status, 'OPEN');

      await tester.tap(find.byKey(const Key('timeline-next-page')));
      await tester.pumpAndSettle();
      expect(repository.queries.last.page, 1);
    });

    testWidgets('outage shows an error and retry instead of an empty list', (
      tester,
    ) async {
      var attempts = 0;
      final repository = TimelineFake()
        ..onTimeline = ({required page, departmentId, status}) async {
          if (attempts++ == 0) throw const ApiFailure('Mất kết nối', status: 503);
          return TimelinePage(
            entries: const [],
            page: page,
            size: 20,
            totalPages: 0,
            totalElements: 0,
          );
        };
      await tester.pumpWidget(_host(repository));
      await tester.pumpAndSettle();
      expect(find.textContaining('Chưa tải được dòng thời gian'), findsOneWidget);
      expect(find.text('Chưa có lượt khám.'), findsNothing);
      await tester.tap(find.byKey(const Key('timeline-retry')));
      await tester.pumpAndSettle();
      expect(find.text('Chưa có lượt khám.'), findsOneWidget);
    });

    testWidgets('403 is a no-permission state', (tester) async {
      final repository = TimelineFake()
        ..onTimeline = ({required page, departmentId, status}) async =>
            throw const ApiFailure('Denied', status: 403);
      await tester.pumpWidget(_host(repository));
      await tester.pumpAndSettle();
      expect(find.text('Bạn không có quyền xem hồ sơ của bệnh nhân này.'), findsOneWidget);
      expect(find.text('Chưa có lượt khám.'), findsNothing);
    });

    test('gateway requests the exact timeline path and filters', () async {
      final transport = TimelineTransport()
        ..responseData = {
          'content': <Object>[],
          'totalElements': 0,
          'totalPages': 0,
          'number': 0,
          'size': 10,
        };
      final dio = Dio()..httpClientAdapter = transport;
      final repository = GatewayPatientTimelineRepository(
        ApiClient(AppConfig('https://gateway.invalid/api'), dio: dio),
      );
      await repository.timeline(
        _patient,
        page: 0,
        size: 10,
        departmentId: _department,
        status: 'OPEN',
      );

      expect(transport.requests.single.path, 'v2/patients/$_patient/timeline');
      expect(transport.requests.single.queryParameters, {
        'page': 0,
        'size': 10,
        'departmentId': _department,
        'status': 'OPEN',
      });
    });

    testWidgets('shows loading while the timeline request is pending', (
      tester,
    ) async {
      final response = Completer<TimelinePage>();
      final repository = TimelineFake()
        ..onTimeline = ({required page, departmentId, status}) => response.future;
      await tester.pumpWidget(_host(repository));
      await tester.pump();
      expect(find.byType(MediFlowLoader), findsOneWidget);
      response.complete(
        const TimelinePage(
          entries: [],
          page: 0,
          size: 20,
          totalPages: 0,
          totalElements: 0,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Chưa có lượt khám.'), findsOneWidget);
    });

    testWidgets('discards a late result after the patient changes', (
      tester,
    ) async {
      final first = Completer<TimelinePage>();
      var calls = 0;
      final repository = TimelineFake()
        ..onTimeline = ({required page, departmentId, status}) {
          if (calls++ == 0) return first.future;
          return Future.value(
            const TimelinePage(
              entries: [],
              page: 0,
              size: 20,
              totalPages: 0,
              totalElements: 0,
            ),
          );
        };
      await tester.pumpWidget(_host(repository));
      await tester.pump();
      await tester.pumpWidget(_host(repository, patientId: 'patient-next'));
      await tester.pumpAndSettle();
      first.complete(
        TimelinePage(
          entries: [_entry()],
          page: 0,
          size: 20,
          totalPages: 1,
          totalElements: 1,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Chưa có lượt khám.'), findsOneWidget);
      expect(find.text('Khoa: $_department'), findsNothing);
    });
  });
}

Widget _host(TimelineFake repository, {String patientId = _patient}) => ProviderScope(
  overrides: [patientTimelineRepositoryProvider.overrideWithValue(repository)],
  child: MaterialApp(
    home: Scaffold(body: PatientTimelineSection(patientId: patientId)),
  ),
);
