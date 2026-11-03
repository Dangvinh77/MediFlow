abstract interface class PasswordRepository {
  Future<void> change(String currentPassword, String newPassword);
}

class ChangeMyPassword {
  const ChangeMyPassword(this.repository);
  final PasswordRepository repository;
  Future<void> call(String currentPassword, String newPassword) {
    if (currentPassword.trim().isEmpty ||
        currentPassword.length > 72 ||
        newPassword.trim().isEmpty ||
        newPassword.length < 8 ||
        newPassword.length > 72) {
      throw const FormatException(
        'Mật khẩu hiện tại tối đa 72 ký tự; mật khẩu mới từ 8 đến 72 ký tự.',
      );
    }
    return repository.change(currentPassword, newPassword);
  }
}
