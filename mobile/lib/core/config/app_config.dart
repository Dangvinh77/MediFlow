class AppConfig {
  AppConfig(String gatewayUrl, {bool allowInsecureHttp = false}) {
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
  );

  late final String baseUrl;
}
