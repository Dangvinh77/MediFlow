import '../../../core/network/json_reader.dart';
import '../domain/notification.dart';

abstract final class NotificationDto {
  static PatientNotification fromJson(Map<String, dynamic> json) {
    final id = _uuid(json, 'notificationId');
    final patientId = _uuid(json, 'patientId');
    final title = JsonReader.string(json, 'title');
    final content = JsonReader.string(json, 'content');
    final channel = JsonReader.string(json, 'channel');
    final status = JsonReader.string(json, 'status');
    if (!notificationChannels.contains(channel) ||
        !notificationStatuses.contains(status)) {
      throw const FormatException(
        'Kênh hoặc trạng thái thông báo không hợp lệ.',
      );
    }
    final createdAt = JsonReader.string(json, 'createdAt');
    final sentAt = JsonReader.optionalString(json, 'sentAt');
    final failureReason = JsonReader.optionalString(json, 'failureReason');
    if (DateTime.tryParse(createdAt) == null ||
        (sentAt != null && DateTime.tryParse(sentAt) == null)) {
      throw const FormatException('Thời điểm thông báo không hợp lệ.');
    }
    return PatientNotification(
      id: id,
      patientId: patientId,
      title: title,
      content: content,
      channel: channel,
      status: status,
      failureReason: failureReason,
      createdAt: createdAt,
      sentAt: sentAt,
    );
  }

  static String _uuid(Map<String, dynamic> json, String key) {
    final value = JsonReader.string(json, key);
    if (!isNotificationUuid(value)) {
      throw FormatException('Dữ liệu $key không hợp lệ.');
    }
    return value;
  }
}
