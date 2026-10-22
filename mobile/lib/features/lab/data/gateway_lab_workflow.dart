import '../../../core/domain/reference.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/lab_workflow.dart';

class GatewayLabWorkflow implements LabWorkflow {
  const GatewayLabWorkflow(this.client);
  final ApiClient client;
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
  Future<LabDetail> cancel(String id, String reason, String actorId) async {
    _id(id);
    if (reason.trim().isEmpty || !isReferenceId(actorId)) {
      throw ArgumentError('Cần lý do hủy và định danh người thực hiện hợp lệ.');
    }
    return _decode(
      await client.put('/v1/lab/$id/cancel', {
        'reason': reason.trim(),
        'cancelledBy': actorId,
      }),
      id,
    );
  }
}
