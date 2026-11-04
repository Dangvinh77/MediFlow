abstract interface class AccountRecoveryRepository {
  Future<void> forgot(String email);
  Future<void> reset(String token, String newPassword);
}

bool isAccountEmail(String email) =>
    email.length <= 254 &&
    RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(email);

class RecoverAccount {
  const RecoverAccount(this.repository);
  final AccountRecoveryRepository repository;

  Future<void> forgot(String email) {
    if (!isAccountEmail(email.trim())) {
      throw const FormatException('Nhập email hợp lệ, tối đa 254 ký tự.');
    }
    return repository.forgot(email.trim());
  }

  Future<void> reset(String linkOrToken, String password) {
    final input = linkOrToken.trim();
    final uri = Uri.tryParse(input);
    final token = uri != null && uri.hasScheme
        ? uri.queryParameters['token'] ?? ''
        : input;
    if (token.isEmpty || token.length > 256) {
      throw const FormatException('Nhập liên kết hoặc mã khôi phục từ email.');
    }
    if (password.trim().isEmpty ||
        password.length < 8 ||
        password.length > 72) {
      throw const FormatException('Mật khẩu mới phải có từ 8 đến 72 ký tự.');
    }
    return repository.reset(token, password);
  }
}
