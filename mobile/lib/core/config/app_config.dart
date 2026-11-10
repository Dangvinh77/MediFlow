class AppConfig {
  AppConfig(
    String gatewayUrl, {
    bool allowInsecureHttp = false,
    this.pharmacyMode = 'unavailable',
    this.scheduledPaymentsEnabled = false,
    this.clinicIntakeEnabled = false,
    this.outpatientQuotesEnabled = false,
    this.zeroPayableEnabled = false,
  }) {
    final uri = Uri.tryParse(gatewayUrl);
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !['/api', '/api/'].contains(uri.path) ||
        (uri.port >= 8081 && uri.port <= 8091) ||
        uri.port == 8761) {
      throw ArgumentError(
        'GATEWAY_URL phải là URL Gateway kết thúc bằng /api.',
      );
    }
    final local = [
      'localhost',
      '127.0.0.1',
      '::1',
      '10.0.2.2',
    ].contains(uri.host);
    if (uri.scheme == 'http' && !local && !allowInsecureHttp) {
      throw ArgumentError(
        'Dùng HTTPS hoặc bật ALLOW_INSECURE_HTTP cho môi trường phát triển.',
      );
    }
    baseUrl = uri.replace(path: '/api/').toString();
  }

  factory AppConfig.environment() => AppConfig(
    const String.fromEnvironment(
      'GATEWAY_URL',
      defaultValue: 'http://localhost:8080/api',
    ),
    allowInsecureHttp: const bool.fromEnvironment('ALLOW_INSECURE_HTTP'),
    pharmacyMode: const String.fromEnvironment(
      'PHARMACY_MODE',
      defaultValue: 'unavailable',
    ),
    clinicIntakeEnabled: const bool.fromEnvironment(
      'MEDIFLOW_CLINIC_INTAKE_UI_ENABLED',
    ),
    scheduledPaymentsEnabled: const bool.fromEnvironment(
      'MEDIFLOW_SCHEDULING_UI_ENABLED',
    ),
    outpatientQuotesEnabled: const bool.fromEnvironment(
      'MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED',
    ),
    zeroPayableEnabled:
        const bool.fromEnvironment('MEDIFLOW_SCHEDULING_UI_ENABLED') &&
        const bool.fromEnvironment('MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED') &&
        const bool.fromEnvironment('MEDIFLOW_ZERO_PAYABLE_UI_ENABLED'),
  );

  late final String baseUrl;
  final String pharmacyMode;
  final bool scheduledPaymentsEnabled;
  final bool clinicIntakeEnabled;
  final bool outpatientQuotesEnabled;
  final bool zeroPayableEnabled;
}
