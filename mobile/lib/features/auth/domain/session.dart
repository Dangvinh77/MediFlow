import 'dart:convert';

enum UserRole {
  admin('ADMIN'),
  doctor('DOCTOR'),
  nurse('NURSE'),
  pharmacist('PHARMACIST'),
  cashier('CASHIER'),
  labTech('LAB_TECH'),
  manager('MANAGER'),
  patient('PATIENT');

  const UserRole(this.wire);
  final String wire;
}

class Session {
  const Session({
    required this.accessToken,
    required this.refreshToken,
    required this.role,
  });
  final String accessToken;
  final String refreshToken;
  final UserRole role;

  // UX expiry check only. Gateway remains the signature/authority verifier.
  bool get isExpired {
    try {
      final parts = accessToken.split('.');
      if (parts.length != 3) return true;
      final claims = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      );
      final exp = claims['exp'];
      return exp is! num || DateTime.now().millisecondsSinceEpoch >= exp * 1000;
    } catch (_) {
      return true;
    }
  }
}
