import 'session.dart';

abstract interface class AuthRepository {
  Future<Session?> restore();
  Future<Session> login(String username, String password);
  Future<void> save(Session session);
  Future<void> clear();
}

class SignIn {
  const SignIn(this.repository);
  final AuthRepository repository;
  Future<Session> call(String username, String password) {
    if (username.trim().isEmpty ||
        password.isEmpty ||
        username.trim().length > 50 ||
        password.length > 72) {
      throw const FormatException('Kiểm tra tên đăng nhập và mật khẩu.');
    }
    return repository.login(username.trim(), password);
  }
}
