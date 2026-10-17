import '../domain/session.dart';

abstract final class SessionDto {
  static Session fromJson(Map<String, dynamic> json) {
    final access = json['accessToken'];
    final refresh = json['refreshToken'];
    final roles = UserRole.values.where((role) => role.wire == json['role']);
    if (access is! String ||
        access.trim().isEmpty ||
        refresh is! String ||
        refresh.trim().isEmpty ||
        roles.isEmpty) {
      throw const FormatException('Phản hồi đăng nhập không hợp lệ.');
    }
    return Session(
      accessToken: access,
      refreshToken: refresh,
      role: roles.single,
    );
  }

  static Map<String, dynamic> toJson(Session session) => {
    'accessToken': session.accessToken,
    'refreshToken': session.refreshToken,
    'role': session.role.wire,
  };
}
