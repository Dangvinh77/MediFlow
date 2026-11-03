import '../../../core/network/api_client.dart';
import '../domain/change_password.dart';

class GatewayPasswordRepository implements PasswordRepository {
  const GatewayPasswordRepository(this.client);
  final ApiClient client;
  @override
  Future<void> change(String currentPassword, String newPassword) async {
    final result = await client.put('/v1/org/accounts/me/password', {
      'currentPassword': currentPassword,
      'newPassword': newPassword,
    });
    if (result != true) {
      throw const FormatException(
        'Chưa xác nhận được kết quả đổi mật khẩu. Hãy đăng nhập lại để kiểm tra.',
      );
    }
  }
}
