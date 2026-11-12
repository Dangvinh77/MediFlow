import '../../../core/domain/reference.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/network/json_reader.dart';
import '../domain/lab_catalog.dart';
import '../domain/lab_review.dart';
import '../domain/lab_workflow.dart';
import 'lab_catalog_dto.dart';
import 'lab_review_dto.dart';

class GatewayLabWorkflow
    implements LabWorkflow, LabCatalogReader, LabReviewRepository {
  const GatewayLabWorkflow(this.client);
  final ApiClient client;

  @override
  Future<LabCatalog> catalog({String? group}) async {
    try {
      return LabCatalogDto.catalog(
        await client.get('/v2/lab/catalog', query: {'group': ?group}),
      );
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'LAB_CATALOG_DISABLED') {
        return const LabCatalog(enabled: false);
      }
      rethrow;
    }
  }

  @override
  Future<LabTestTemplate> template(String testId) async {
    _id(testId);
    try {
      return LabCatalogDto.testTemplate(
        await client.get('/v2/lab/$testId/template'),
        testId,
      );
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'LAB_REVIEW_DISABLED') {
        throw const LabReviewUnavailable();
      }
      if (error.status == 404) {
        return LabTestTemplate(
          testId: testId,
          serviceCode: null,
          template: null,
        );
      }
      rethrow;
    }
  }

  @override
  Future<List<LabCriticalNotice>> criticalNotices({
    String status = 'OPEN',
  }) async {
    if (status != 'OPEN') {
      throw ArgumentError.value(status, 'status');
    }
    try {
      return _decodeList(
        await client.get('/v2/lab/critical-notices', query: {'status': status}),
        LabCriticalNoticeDto.fromJson,
      );
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'LAB_REVIEW_DISABLED') {
        throw const LabReviewUnavailable();
      }
      rethrow;
    }
  }

  @override
  Future<LabCriticalNotice> acknowledge(String testId, String noticeId) async {
    _id(testId);
    _id(noticeId);
    try {
      final raw = await client.post(
        '/v2/lab/$testId/critical-notices/$noticeId/acknowledgement',
        const <String, Object?>{},
      );
      if (raw is! Map<String, dynamic>) {
        throw const FormatException('Phản hồi xác nhận cảnh báo không hợp lệ.');
      }
      return LabCriticalNoticeDto.fromJson(raw);
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'LAB_REVIEW_DISABLED') {
        throw const LabReviewUnavailable();
      }
      rethrow;
    }
  }

  @override
  Future<List<LabWorklistItem>> worklistItems() async {
    try {
      return _decodeList(
        await client.get('/v2/lab/worklist'),
        LabWorklistItemDto.fromJson,
      );
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'LAB_REVIEW_DISABLED') {
        throw const LabReviewUnavailable();
      }
      rethrow;
    }
  }

  List<T> _decodeList<T>(Object? raw, T Function(Map<String, dynamic>) decode) {
    if (raw is! List) {
      throw const FormatException('Danh sách xét nghiệm không hợp lệ.');
    }
    return List.unmodifiable(
      raw.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Dữ liệu xét nghiệm không hợp lệ.');
        }
        return decode(item);
      }),
    );
  }

  LabDetail _decode(Object? raw, String id) {
    if (raw is! Map<String, dynamic> ||
        raw['testId'] != id ||
        raw['paid'] is! bool ||
        raw['careContractVersion'] is! int) {
      throw const FormatException('Phản hồi xét nghiệm không khớp yêu cầu.');
    }
    final rows = raw['results'];
    if (rows != null && rows is! List) {
      throw const FormatException('Kết quả xét nghiệm không hợp lệ.');
    }
    return LabDetail(
      id: id,
      patientId: JsonReader.string(raw, 'patientId'),
      recordId: JsonReader.string(raw, 'recordId'),
      departmentId: JsonReader.string(raw, 'requestingDepartmentId'),
      type: JsonReader.string(raw, 'labType'),
      requestedDate: JsonReader.string(raw, 'requestedDate'),
      status: JsonReader.string(raw, 'status'),
      paid: raw['paid'] as bool,
      version: raw['careContractVersion'] as int,
      clearanceId: JsonReader.optionalString(raw, 'clearanceId'),
      performedDate: JsonReader.optionalString(raw, 'performedDate'),
      conclusion: JsonReader.optionalString(raw, 'conclusion'),
      results: [
        if (rows is List)
          for (final row in rows)
            if (row is Map<String, dynamic>)
              LabResultDraft(
                indicator: JsonReader.string(row, 'indicator'),
                value: JsonReader.string(row, 'value'),
                unit: JsonReader.optionalString(row, 'unit'),
                referenceRange: JsonReader.optionalString(
                  row,
                  'referenceRange',
                ),
              )
            else
              throw const FormatException('Chỉ số không hợp lệ.'),
      ],
    );
  }

  void _id(String id) {
    if (!isReferenceId(id)) {
      throw ArgumentError('Định danh xét nghiệm không hợp lệ.');
    }
  }

  @override
  Future<LabDetail> detail(String id) async {
    _id(id);
    return _decode(await client.get('/v1/lab/$id'), id);
  }

  @override
  Future<LabDetail> start(String id) async {
    _id(id);
    return _decode(
      await client.put('/v1/lab/$id/start', {'emergencyOverride': null}),
      id,
    );
  }

  @override
  Future<LabDetail> results(
    String id,
    List<LabResultDraft> results,
    String? conclusion,
    String date,
  ) async {
    _id(id);
    if (results.isEmpty) throw ArgumentError('Cần ít nhất một chỉ số.');
    for (final result in results) {
      result.validate(date);
    }
    return _decode(
      await client.put('/v1/lab/$id/results', {
        'results': [
          for (final result in results)
            {
              'indicator': result.indicator.trim(),
              'value': result.value.trim(),
              'unit': result.unit,
              'referenceRange': result.referenceRange,
            },
        ],
        'conclusion': conclusion?.trim(),
        'performedDate': date,
      }),
      id,
    );
  }

  @override
  Future<LabDetail> cancel(String id, String reason) async {
    _id(id);
    if (reason.trim().isEmpty || reason.trim().length > 1000) {
      throw ArgumentError('Lý do hủy phải có từ 1 đến 1000 ký tự.');
    }
    return _decode(
      await client.put('/v1/lab/$id/cancel', {'reason': reason.trim()}),
      id,
    );
  }
}
