import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/notification.dart';
import 'notification_dto.dart';

class GatewayNotificationRepository implements NotificationRepository {
  const GatewayNotificationRepository(this.client);
  final ApiClient client;

  @override
  Future<PageResult<PatientNotification>> byPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  }) async {
    _requireUuid(patientId);
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang thông báo không hợp lệ.');
    }
    final response = await client.get(
      '/v1/notifications/patient/$patientId',
      query: {'page': page, 'size': size},
    );
    final json = _object(response);
    if (json['size'] is! int || json['size'] != size) {
      throw const FormatException('Kích thước trang thông báo không khớp.');
    }
    return decodePage(json, NotificationDto.fromJson);
  }

  @override
  Future<PatientNotification> byId(String notificationId) async {
    _requireUuid(notificationId);
    final notification = NotificationDto.fromJson(
      _object(await client.get('/v1/notifications/$notificationId')),
    );
    if (notification.id.toLowerCase() != notificationId.toLowerCase()) {
      throw const FormatException('Mã thông báo trả về không khớp.');
    }
    return notification;
  }

  @override
  Future<PatientNotification> send(SendNotificationCommand command) async {
    _requireUuid(command.patientId);
    final notification = NotificationDto.fromJson(
      _object(
        await client.post('/v1/notifications/send', {
          'patientId': command.patientId,
          'title': command.title,
          'content': command.content,
          'channel': command.channel,
          'recipientAddress': command.recipientAddress,
        }),
      ),
    );
    if (notification.patientId.toLowerCase() !=
        command.patientId.toLowerCase()) {
      throw const FormatException(
        'Thông báo trả về không khớp bệnh nhân đã chọn.',
      );
    }
    return notification;
  }

  static Map<String, dynamic> _object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi thông báo không hợp lệ.');
    }
    return value;
  }

  static void _requireUuid(String value) {
    if (!isNotificationUuid(value)) {
      throw ArgumentError('Mã phải là UUID hợp lệ.');
    }
  }
}
