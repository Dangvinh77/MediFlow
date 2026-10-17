import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/auth/data/session_dto.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';

void main() {
  test('Gateway base normalizes one api prefix', () {
    expect(
      AppConfig('http://localhost:8080/api').baseUrl,
      'http://localhost:8080/api/',
    );
  });
  test('rejects credentials, service ports and non-http URLs', () {
    for (final url in [
      'file:///api',
      'http://u:p@localhost:8080/api',
      'http://localhost:8082/api',
      'https://example.com/clinical',
    ]) {
      expect(() => AppConfig(url), throwsArgumentError);
    }
  });
  test('LAN http requires explicit development opt-in', () {
    expect(() => AppConfig('http://192.168.1.2:8080/api'), throwsArgumentError);
    expect(
      AppConfig('http://192.168.1.2:8080/api', allowInsecureHttp: true).baseUrl,
      'http://192.168.1.2:8080/api/',
    );
  });
  test('login response is raw and preserves both tokens', () {
    final session = SessionDto.fromJson({
      'accessToken': 'access',
      'refreshToken': 'refresh',
      'role': 'DOCTOR',
    });
    expect(session.role, UserRole.doctor);
    expect(session.refreshToken, 'refresh');
    expect(SessionDto.toJson(session)['accessToken'], 'access');
  });
  test('rejects empty tokens and non-human role', () {
    for (final role in ['SYSTEM', 'UNKNOWN', '']) {
      expect(
        () => SessionDto.fromJson({
          'accessToken': 'a',
          'refreshToken': 'r',
          'role': role,
        }),
        throwsFormatException,
      );
    }
    expect(
      () => SessionDto.fromJson({
        'accessToken': ' ',
        'refreshToken': 'r',
        'role': 'ADMIN',
      }),
      throwsFormatException,
    );
  });
  test('structured failure retains field errors and correlation', () {
    final failure = ApiFailure.fromResponse(422, {
      'error': {
        'code': 'RULE',
        'message': 'Blocked',
        'details': [
          {'field': 'patientId', 'message': 'Invalid'},
        ],
      },
      'correlationId': 'trace-1',
    });
    expect(failure.code, 'RULE');
    expect(failure.correlationId, 'trace-1');
    expect(failure.fields['patientId'], 'Invalid');
    expect(failure.toString(), 'Blocked');
  });
  test('non-json unavailable response stays unavailable', () {
    final failure = ApiFailure.fromResponse(503, '<html>unavailable</html>');
    expect(failure.status, 503);
    expect(failure.code, 'HTTP_503');
    expect(failure.message, isNot(contains('<html>')));
  });
}
