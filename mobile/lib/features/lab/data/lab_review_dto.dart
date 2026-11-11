import '../../../core/network/json_reader.dart';
import '../domain/lab_review.dart';

abstract final class LabCriticalNoticeDto {
  static LabCriticalNotice fromJson(Map<String, dynamic> json) {
    final version = json['resultVersion'];
    if (version is! int) {
      throw const FormatException('Phiên bản kết quả xét nghiệm không hợp lệ.');
    }
    return LabCriticalNotice(
      noticeId: JsonReader.string(json, 'noticeId'),
      testId: JsonReader.string(json, 'testId'),
      resultVersion: version,
      fieldCode: JsonReader.string(json, 'fieldCode'),
      label: JsonReader.string(json, 'label'),
      value: JsonReader.string(json, 'value'),
      unit: JsonReader.optionalString(json, 'unit'),
      flag: JsonReader.string(json, 'flag'),
      patientId: JsonReader.string(json, 'patientId'),
      departmentId: JsonReader.string(json, 'departmentId'),
      status: JsonReader.string(json, 'status'),
      acknowledgedBy: JsonReader.optionalString(json, 'acknowledgedBy'),
      acknowledgedAt: JsonReader.optionalString(json, 'acknowledgedAt'),
      createdAt: JsonReader.string(json, 'createdAt'),
    );
  }
}

abstract final class LabWorklistItemDto {
  static LabWorklistItem fromJson(Map<String, dynamic> json) {
    final reportVersion = json['reportVersion'];
    if (reportVersion != null && reportVersion is! int) {
      throw const FormatException('Phiên bản báo cáo không hợp lệ.');
    }
    if (json['overdue'] is! bool) {
      throw const FormatException('Trạng thái quá hạn không hợp lệ.');
    }
    return LabWorklistItem(
      testId: JsonReader.string(json, 'testId'),
      patientId: JsonReader.string(json, 'patientId'),
      labType: JsonReader.string(json, 'labType'),
      testStatus: JsonReader.string(json, 'testStatus'),
      serviceCode: JsonReader.optionalString(json, 'serviceCode'),
      serviceName: JsonReader.optionalString(json, 'serviceName'),
      displayGroup: JsonReader.optionalString(json, 'displayGroup'),
      specimenStatus: JsonReader.optionalString(json, 'specimenStatus'),
      reportStatus: JsonReader.optionalString(json, 'reportStatus'),
      reportVersion: reportVersion as int?,
      dueAt: JsonReader.optionalString(json, 'dueAt'),
      overdue: json['overdue'] as bool,
    );
  }
}
