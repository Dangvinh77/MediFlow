import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/medical_record.dart';
import '../domain/patient_timeline.dart';
import 'medical_record_dto.dart';

class GatewayPatientTimelineRepository implements PatientTimelineRepository {
  const GatewayPatientTimelineRepository(this.client);
  final ApiClient client;

  @override
  Future<TimelinePage> timeline(
    String patientId, {
    int page = 0,
    int size = 20,
    String? departmentId,
    String? status,
  }) async {
    _requireId(patientId, 'patientId');
    if (page < 0 || size < 1 || size > 50) {
      throw ArgumentError('Trang phải từ 0 và kích thước từ 1 đến 50.');
    }
    final value = await client.get(
      '/v2/patients/${Uri.encodeComponent(patientId)}/timeline',
      query: {
        'page': page,
        'size': size,
        if (departmentId != null && departmentId.isNotEmpty)
          'departmentId': departmentId,
        if (status != null && status.isNotEmpty) 'status': status,
      },
    );
    if (value is! Map<String, dynamic> || value['content'] is! List) {
      throw const FormatException('Phản hồi dòng thời gian không hợp lệ.');
    }
    final rawContent = value['content'] as List;
    final totalElements = value['totalElements'];
    final totalPages = value['totalPages'];
    final number = value['number'];
    final responseSize = value['size'];
    if (totalElements is! num ||
        totalPages is! int ||
        number is! int ||
        responseSize is! int) {
      throw const FormatException('Thông tin phân trang không hợp lệ.');
    }
    return TimelinePage(
      entries: rawContent.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Dữ liệu lượt khám không hợp lệ.');
        }
        return TimelineEntryDto.fromJson(item);
      }).toList(growable: false),
      page: number,
      size: responseSize,
      totalPages: totalPages,
      totalElements: totalElements.toInt(),
    );
  }

  @override
  Future<List<RecordDocument>> documents(String recordId) async {
    _requireId(recordId, 'recordId');
    final value = await client.get(
      '/v2/records/${Uri.encodeComponent(recordId)}/documents',
    );
    if (value is! List) {
      throw const FormatException('Phản hồi tài liệu hồ sơ không hợp lệ.');
    }
    return value.map((item) {
      if (item is! Map<String, dynamic>) {
        throw const FormatException('Dữ liệu tài liệu không hợp lệ.');
      }
      return RecordDocumentDto.fromJson(item);
    }).toList(growable: false);
  }

  @override
  Future<MedicalRecord> updateContent(
    String recordId, {
    required Map<String, String?> changes,
    required String idempotencyKey,
  }) async {
    _requireId(recordId, 'recordId');
    if (idempotencyKey.isEmpty) {
      throw ArgumentError('Idempotency-Key không được để trống.');
    }
    if (changes.isEmpty ||
        changes.keys.any((field) => !recordContentFields.contains(field))) {
      throw ArgumentError('Nội dung thay đổi không hợp lệ.');
    }
    for (final entry in changes.entries) {
      if ((entry.value?.length ?? 0) > 4000) {
        throw ArgumentError('Mỗi mục nội dung tối đa 4000 ký tự.');
      }
    }
    final value = await client.putIdempotent(
      '/v1/records/${Uri.encodeComponent(recordId)}',
      Map<String, String?>.of(changes),
      idempotencyKey: idempotencyKey,
    );
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi nội dung hồ sơ không hợp lệ.');
    }
    final record = MedicalRecordDto.fromJson(value);
    if (record.id != recordId) {
      throw const FormatException('Hồ sơ trả về không khớp hồ sơ đã chọn.');
    }
    return record;
  }

  static void _requireId(String value, String name) {
    if (!isMedicalRecordUuid(value)) throw ArgumentError('$name không hợp lệ.');
  }
}

abstract final class TimelineEntryDto {
  static TimelineEntry fromJson(Map<String, dynamic> json) {
    final rawCourses = json['treatmentCourseIds'];
    if (rawCourses != null &&
        (rawCourses is! List || rawCourses.any((id) => id is! String))) {
      throw const FormatException('Dữ liệu treatmentCourseIds không hợp lệ.');
    }
    return TimelineEntry(
      recordId: JsonReader.string(json, 'recordId'),
      examinationDate: JsonReader.string(json, 'examinationDate'),
      departmentId: JsonReader.string(json, 'departmentId'),
      doctorId: JsonReader.string(json, 'doctorId'),
      responsibleDoctorId: JsonReader.optionalString(
        json,
        'responsibleDoctorId',
      ),
      visitKind: JsonReader.optionalString(json, 'visitKind'),
      status: JsonReader.string(json, 'status'),
      disposition: JsonReader.optionalString(json, 'disposition'),
      previousRecordId: JsonReader.optionalString(json, 'previousRecordId'),
      treatmentCourseIds: rawCourses == null
          ? const []
          : List<String>.unmodifiable(rawCourses as List),
    );
  }
}

abstract final class RecordDocumentDto {
  static RecordDocument fromJson(Map<String, dynamic> json) {
    final type = JsonReader.string(json, 'type');
    if (type != 'LAB' && type != 'PRESCRIPTION') {
      throw const FormatException('Loại tài liệu hồ sơ không hợp lệ.');
    }
    final afterConclusion = json['afterConclusion'];
    if (afterConclusion is! bool) {
      throw const FormatException('Dữ liệu afterConclusion không hợp lệ.');
    }
    return RecordDocument(
      type: type,
      referenceId: JsonReader.string(json, 'referenceId'),
      summary: JsonReader.string(json, 'summary'),
      attachedAt: JsonReader.string(json, 'attachedAt'),
      afterConclusion: afterConclusion,
    );
  }
}
