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
    this.passwordChangeRequired = false,
  });
  final String accessToken;
  final String refreshToken;
  final UserRole role;

  /// Set when Gateway rejects this session with AUTH_PASSWORD_CHANGE_REQUIRED
  /// after login (e.g. an admin reset). Not persisted: a restored session hits
  /// the same Gateway rejection again.
  final bool passwordChangeRequired;

  Session requirePasswordChange() => Session(
    accessToken: accessToken,
    refreshToken: refreshToken,
    role: role,
    passwordChangeRequired: true,
  );

  // Routing hint only; Gateway enforces the current account requirement.
  bool get mustChangePassword {
    if (passwordChangeRequired) return true;
    try {
      final claims = jsonDecode(
        utf8.decode(
          base64Url.decode(base64Url.normalize(accessToken.split('.')[1])),
        ),
      );
      return claims is Map && claims['mustChangePassword'] == true;
    } catch (_) {
      return false;
    }
  }

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
