import '../../../core/network/json_reader.dart';
import '../domain/profile_account.dart';
import 'organization_dto.dart';

abstract final class ProfileAccountDto {
  static ProfileAccountSnapshot profile(Object? value) {
    final json = _object(value, 'tài khoản hồ sơ');
    final rawAccount = json['account'];
    final account = rawAccount == null
        ? null
        : OrganizationDto.account(rawAccount);
    final syncPending = json['emailSyncPending'];
    if (syncPending is! bool) {
      throw const FormatException('Trạng thái đồng bộ email không hợp lệ.');
    }
    return ProfileAccountSnapshot(
      account: account,
      profileEmail: JsonReader.optionalString(json, 'profileEmail'),
      emailSyncPending: syncPending,
    );
  }

  static ProfileMailDelivery mailDelivery(Object? value) {
    final json = _object(value, 'trạng thái gửi email');
    final rawStatus = JsonReader.string(json, 'status');
    final status = switch (rawStatus) {
      'PENDING' => ProfileMailStatus.pending,
      'SENT' => ProfileMailStatus.sent,
      'FAILED' => ProfileMailStatus.failed,
      _ => throw const FormatException('Trạng thái gửi email không hợp lệ.'),
    };
    return ProfileMailDelivery(
      messageId: JsonReader.string(json, 'messageId'),
      status: status,
    );
  }

  static Map<String, dynamic> _object(Object? value, String label) {
    if (value is! Map<String, dynamic>) {
      throw FormatException('Phản hồi $label không hợp lệ.');
    }
    return value;
  }
}
