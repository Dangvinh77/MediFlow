import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/exam_lifecycle.dart';
import '../domain/medical_record.dart';
import 'medical_record_dto.dart';

class GatewayExamLifecycleRepository implements ExamLifecycleRepository {
  const GatewayExamLifecycleRepository(this.client);
  final ApiClient client;

  @override
  Future<List<RecordLabOrder>> labOrders(String recordId) async {
    final value = await client.get(
      '/v2/records/${Uri.encodeComponent(recordId)}/lab-orders',
    );
    if (value is! List) {
      throw const FormatException('Phản hồi chỉ định xét nghiệm không hợp lệ.');
    }
    return value
        .map((item) {
          if (item is! Map<String, dynamic>) {
            throw const FormatException('Dữ liệu chỉ định không hợp lệ.');
          }
          return RecordLabOrder(
            sourceOrderId: JsonReader.string(item, 'sourceOrderId'),
            testId: JsonReader.optionalString(item, 'testId'),
            labType: JsonReader.string(item, 'labType'),
            status: JsonReader.string(item, 'status'),
            orderedAt: JsonReader.string(item, 'orderedAt'),
          );
        })
        .toList(growable: false);
  }

  @override
  Future<void> withdrawLabOrder(
    String recordId,
    String sourceOrderId, {
    required String reason,
    required String idempotencyKey,
  }) async {
    final text = reason.trim();
    if (text.isEmpty || text.length > 500) {
      throw ArgumentError('Lý do rút chỉ định phải có từ 1 đến 500 ký tự.');
    }
    await client.putIdempotent(
      '/v2/records/${Uri.encodeComponent(recordId)}/lab-orders/'
      '${Uri.encodeComponent(sourceOrderId)}/withdrawal',
      {'reason': text},
      idempotencyKey: idempotencyKey,
    );
  }

  @override
  Future<MedicalRecord> closeWithoutConclusion(
    String recordId, {
    required String reason,
    required String note,
    required String idempotencyKey,
  }) async {
    final text = note.trim();
    if (!recordClosureReasonLabels.containsKey(reason)) {
      throw ArgumentError.value(reason, 'reason');
    }
    if (text.isEmpty || text.length > recordClosureNoteMax) {
      throw ArgumentError('Ghi chú phải có từ 1 đến 1000 ký tự.');
    }
    final value = await client.putIdempotent(
      '/v2/records/${Uri.encodeComponent(recordId)}/close-without-conclusion',
      {'reason': reason, 'note': text},
      idempotencyKey: idempotencyKey,
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi hồ sơ không hợp lệ.');
    }
    final record = MedicalRecordDto.fromJson(value);
    if (record.id != recordId) {
      throw const FormatException('Hồ sơ trả về không khớp hồ sơ đã chọn.');
    }
    return record;
  }
}
