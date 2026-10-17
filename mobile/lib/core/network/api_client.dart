import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'api_failure.dart';

class ApiClient {
  ApiClient(
    AppConfig config, {
    Dio? dio,
    String? Function()? token,
    void Function(String token)? onUnauthorized,
  }) : _dio = dio ?? Dio() {
    _dio.options = BaseOptions(
      baseUrl: config.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      sendTimeout: const Duration(seconds: 20),
      headers: {'Accept': 'application/json'},
    );
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final access = options.extra['public'] == true ? null : token?.call();
          if (access != null) {
            options.headers['Authorization'] = 'Bearer $access';
          }
          handler.next(options);
        },
        onError: (error, handler) {
          final auth = error.requestOptions.headers['Authorization'];
          if (error.response?.statusCode == 401 &&
              auth is String &&
              auth.startsWith('Bearer ')) {
            onUnauthorized?.call(auth.substring(7));
          }
          handler.next(error);
        },
      ),
    );
  }

  final Dio _dio;

  Future<Object?> get(String path, {Map<String, dynamic>? query}) =>
      _request(path, 'GET', query: query);

  Future<Object?> post(
    String path,
    Object body, {
    bool raw = false,
    bool public = false,
  }) => _request(path, 'POST', body: body, raw: raw, public: public);

  Future<Object?> _request(
    String path,
    String method, {
    Object? body,
    Map<String, dynamic>? query,
    bool raw = false,
    bool public = false,
  }) async {
    // Prevent absolute URLs, context paths and credentials leaking outside Gateway.
    if (!path.startsWith('/v1/') ||
        path.contains('..') ||
        path.contains('?') ||
        path.contains('#')) {
      throw ArgumentError('API path phải bắt đầu bằng /v1/.');
    }
    try {
      final response = await _dio.request<Object?>(
        path.substring(1),
        data: body,
        queryParameters: query,
        options: Options(method: method, extra: {'public': public}),
      );
      if (raw) return response.data;
      final envelope = response.data;
      if (envelope is! Map ||
          envelope['success'] != true ||
          !envelope.containsKey('data')) {
        throw ApiFailure.fromResponse(
          response.statusCode,
          envelope,
          correlationId: response.headers.value('X-Correlation-Id'),
        );
      }
      return envelope['data'];
    } on DioException catch (error) {
      if (error.response != null) {
        throw ApiFailure.fromResponse(
          error.response!.statusCode,
          error.response!.data,
          correlationId: error.response!.headers.value('X-Correlation-Id'),
        );
      }
      throw const ApiFailure(
        'Không kết nối được Gateway. Kiểm tra mạng và địa chỉ máy chủ.',
      );
    }
  }

  void close() => _dio.close(force: true);
}
