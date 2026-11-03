import '../../../core/network/api_client.dart';
import '../domain/account_recovery.dart';

class GatewayAccountRecovery implements AccountRecoveryRepository {
  const GatewayAccountRecovery(this.client);
  final ApiClient client;

  @override
  Future<void> forgot(String email) async {
    final data = await client.post('/v1/auth/forgot-password', {
      'email': email,
    }, public: true);
    if (data is! Map || data['accepted'] != true) {
      throw const FormatException('Chưa xác nhận được yêu cầu khôi phục.');
    }
  }

  @override
  Future<void> reset(String token, String newPassword) async {
    final data = await client.post('/v1/auth/reset-password', {
      'token': token,
      'newPassword': newPassword,
    }, public: true);
    if (data is! Map || data['success'] != true) {
      throw const FormatException(
        'Chưa xác nhận được kết quả đặt lại mật khẩu.',
      );
    }
  }
}
