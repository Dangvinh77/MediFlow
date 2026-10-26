import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';

void main() {
  test('workspace reference IDs use explicit claims only, never sub', () {
    String token(Map<String, Object> claims) =>
        'header.${base64Url.encode(utf8.encode(jsonEncode(claims)))}.signature';
    final session = Session(
      accessToken: token({
        'sub': 'account-name',
        'patientId': '10000000-0000-4000-8000-000000000001',
        'staffId': '10000000-0000-4000-8000-000000000002',
      }),
      refreshToken: 'r',
      role: UserRole.patient,
    );
    expect(session.patientId, '10000000-0000-4000-8000-000000000001');
    expect(session.staffId, '10000000-0000-4000-8000-000000000002');
    final legacy = Session(
      accessToken: token({'sub': '10000000-0000-4000-8000-000000000001'}),
      refreshToken: 'r',
      role: UserRole.patient,
    );
    expect(legacy.patientId, isNull);
    expect(legacy.staffId, isNull);
  });
}
