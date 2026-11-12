import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/medical_record/data/gateway_patient_timeline_repository.dart';
import 'package:mediflow_mobile/features/medical_record/domain/medical_record.dart';
import 'package:mediflow_mobile/features/medical_record/domain/patient_timeline.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/patient_timeline_section.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/record_documents_section.dart';

const _recordId = '30000000-0000-4000-8000-000000000001';
const _document = <String, Object?>{
  'type': 'LAB',
  'referenceId': '30000000-0000-4000-8000-000000000002',
  'summary': 'Kết quả xét nghiệm hiện tại',
  'attachedAt': '2026-11-10T10:00:00Z',
  'afterConclusion': false,
};

class _DocumentsRepository implements PatientTimelineRepository {
  _DocumentsRepository(this.load);

  final Future<List<RecordDocument>> Function() load;

  @override
  Future<List<RecordDocument>> documents(String recordId) => load();

  @override
  Future<TimelinePage> timeline(
    String patientId, {
    int page = 0,
    int size = 20,
    String? departmentId,
    String? status,
  }) => throw UnimplementedError();

  @override
  Future<MedicalRecord> updateContent(
    String recordId, {
    required Map<String, String?> changes,
    required String idempotencyKey,
  }) => throw UnimplementedError();
}

class _DocumentsTransport implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? responseData;
  int statusCode = 200;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(
        statusCode >= 400
            ? {
                'success': false,
                'error': {'code': 'LAB_REVIEW_DISABLED', 'message': 'Disabled'},
              }
            : {'success': true, 'data': responseData},
      ),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  group('record document revisions', () {
    test('DTO parses current and previous result revisions', () {
      final document = RecordDocumentDto.fromJson({
        ..._document,
        'resultVersion': 3,
        'amendmentReason': 'Sửa sai số nhập liệu',
        'previousRevisions': [
          {
            'resultVersion': 2,
            'summary': 'Kết quả trước',
            'amendmentReason': 'Sửa tên chỉ số',
            'sourceAt': '2026-11-09T10:00:00Z',
            'label': 'Lần 2',
          },
        ],
      });

      expect(document.resultVersion, 3);
      expect(document.amendmentReason, 'Sửa sai số nhập liệu');
      expect(document.previousRevisions, hasLength(1));
      expect(document.previousRevisions.single.resultVersion, 2);
      expect(document.previousRevisions.single.summary, 'Kết quả trước');
      expect(
        document.previousRevisions.single.amendmentReason,
        'Sửa tên chỉ số',
      );
      expect(
        document.previousRevisions.single.sourceAt,
        '2026-11-09T10:00:00Z',
      );
      expect(document.previousRevisions.single.label, 'Lần 2');
    });

    test(
      'DTO accepts documents from older responses without revision fields',
      () {
        final document = RecordDocumentDto.fromJson(_document);

        expect(document.resultVersion, isNull);
        expect(document.amendmentReason, isNull);
        expect(document.previousRevisions, isEmpty);
      },
    );

    test(
      'repository requests documents through the gateway endpoint',
      () async {
        final transport = _DocumentsTransport()..responseData = [_document];
        final api = ApiClient(
          AppConfig('https://gateway.invalid/api'),
          dio: Dio()..httpClientAdapter = transport,
        );
        final repository = GatewayPatientTimelineRepository(api);

        final documents = await repository.documents(_recordId);

        expect(
          transport.requests.single.path,
          'v2/records/$_recordId/documents',
        );
        expect(documents.single.summary, _document['summary']);
      },
    );

    testWidgets('shows the current version and labelled corrected revisions', (
      tester,
    ) async {
      final repository = _DocumentsRepository(
        () async => [
          RecordDocumentDto.fromJson({
            ..._document,
            'resultVersion': 3,
            'amendmentReason': 'Sửa sai số nhập liệu',
            'previousRevisions': [
              {
                'resultVersion': 2,
                'summary': 'Kết quả trước',
                'amendmentReason': 'Sửa tên chỉ số',
                'sourceAt': '2026-11-09T10:00:00Z',
                'label': 'Lần 2',
              },
            ],
          }),
        ],
      );
      await tester.pumpWidget(_host(repository));
      await tester.pumpAndSettle();

      expect(find.text('Phiên bản hiện tại: 3'), findsOneWidget);
      expect(
        find.text('Lý do đính chính: Sửa sai số nhập liệu'),
        findsOneWidget,
      );
      expect(find.text('Đã đính chính'), findsOneWidget);
      expect(find.text('Lần 2 · phiên bản 2'), findsOneWidget);
      expect(find.text('Kết quả trước'), findsOneWidget);
      expect(find.textContaining('Sửa tên chỉ số'), findsOneWidget);
    });

    testWidgets('disabled lab review shows the feature off message', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          _DocumentsRepository(
            () async => throw const ApiFailure(
              'Disabled',
              status: 404,
              code: 'LAB_REVIEW_DISABLED',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Chức năng chưa được bật'), findsOneWidget);
      expect(find.text('Không tải được tài liệu của lượt khám.'), findsNothing);
    });
  });
}

Widget _host(PatientTimelineRepository repository) => ProviderScope(
  overrides: [patientTimelineRepositoryProvider.overrideWithValue(repository)],
  child: const MaterialApp(
    home: Scaffold(body: RecordDocumentsSection(recordId: _recordId)),
  ),
);
