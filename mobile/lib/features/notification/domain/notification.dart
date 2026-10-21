import '../../../core/domain/page_result.dart';

const notificationChannels = {'EMAIL', 'SMS', 'IN_APP'};
const notificationStatuses = {'PENDING', 'SENT', 'FAILED'};

bool isNotificationUuid(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

class PatientNotification {
  const PatientNotification({
    required this.id,
    required this.patientId,
    required this.title,
    required this.content,
    required this.channel,
    required this.status,
    required this.failureReason,
    required this.createdAt,
    required this.sentAt,
  });
  final String id, patientId, title, content, channel, status, createdAt;
  final String? failureReason, sentAt;
}

class SendNotificationCommand {
  const SendNotificationCommand({
    required this.patientId,
    required this.title,
    required this.content,
    required this.channel,
    required this.recipientAddress,
  });
  final String patientId, title, content, channel;
  final String? recipientAddress;
}

abstract interface class NotificationRepository {
  Future<PageResult<PatientNotification>> byPatient(
    String patientId, {
    int page = 0,
    int size = 20,
  });
  Future<PatientNotification> byId(String notificationId);
  Future<PatientNotification> send(SendNotificationCommand command);
}

class ReadPatientNotifications {
  const ReadPatientNotifications(this.repository);
  final NotificationRepository repository;
  Future<PageResult<PatientNotification>> call(
    String patientId, {
    int page = 0,
    int size = 20,
  }) {
    if (!isNotificationUuid(patientId) || page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Mã bệnh nhân hoặc trang không hợp lệ.');
    }
    return repository.byPatient(patientId, page: page, size: size);
  }
}

class ReadNotification {
  const ReadNotification(this.repository);
  final NotificationRepository repository;
  Future<PatientNotification> call(String id) {
    if (!isNotificationUuid(id)) {
      throw ArgumentError('Mã thông báo phải là UUID hợp lệ.');
    }
    return repository.byId(id);
  }
}

class SendNotification {
  const SendNotification(this.repository);
  final NotificationRepository repository;
  Future<PatientNotification> call(SendNotificationCommand command) {
    final title = command.title.trim();
    final content = command.content.trim();
    if (!isNotificationUuid(command.patientId) ||
        title.isEmpty ||
        title.length > 255 ||
        content.isEmpty ||
        !notificationChannels.contains(command.channel)) {
      throw ArgumentError('Thông tin thông báo chưa hợp lệ.');
    }
    final address = command.recipientAddress?.trim();
    if (command.channel == 'EMAIL' &&
        (address == null ||
            !RegExp(r'^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$').hasMatch(address))) {
      throw ArgumentError('Nhập địa chỉ email hợp lệ.');
    }
    if (command.channel == 'SMS' &&
        (address == null || !RegExp(r'^0\d{9,10}$').hasMatch(address))) {
      throw ArgumentError('Số điện thoại phải có dạng 0 và 9–10 chữ số.');
    }
    if (address != null && address.length > 150) {
      throw ArgumentError('Địa chỉ nhận tối đa 150 ký tự.');
    }
    if (command.channel == 'IN_APP' && address != null && address.isNotEmpty) {
      throw ArgumentError('Thông báo trong ứng dụng không nhận địa chỉ gửi.');
    }
    return repository.send(
      SendNotificationCommand(
        patientId: command.patientId,
        title: title,
        content: content,
        channel: command.channel,
        recipientAddress: command.channel == 'IN_APP' ? null : address,
      ),
    );
  }
}
