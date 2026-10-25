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

  // Display/workflow hints only; Gateway validates the signed token and resource authority.
  String? get patientId => _referenceClaim('patientId');
  String? get staffId => _referenceClaim('staffId');
  String? _referenceClaim(String key) {
    try {
      final claims = jsonDecode(
        utf8.decode(
          base64Url.decode(base64Url.normalize(accessToken.split('.')[1])),
        ),
      );
      final value = claims is Map ? claims[key] : null;
      return value is String &&
              RegExp(
                r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
              ).hasMatch(value)
          ? value
          : null;
    } catch (_) {
      return null;
    }
  }

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
