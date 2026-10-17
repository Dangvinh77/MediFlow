class ApiFailure implements Exception {
  const ApiFailure(
    this.message, {
    this.status,
    this.code = 'NETWORK_ERROR',
    this.correlationId,
    this.fields = const {},
  });

  factory ApiFailure.fromResponse(
    int? status,
    Object? body, {
    String? correlationId,
  }) {
    final json = body is Map ? body : const {};
    final error = json['error'] is Map ? json['error'] as Map : const {};
    final details = error['details'];
    return ApiFailure(
      error['message'] is String
          ? error['message'] as String
          : status == 401
          ? 'Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.'
          : status == 403
          ? 'Bạn không có quyền thực hiện thao tác này.'
          : 'Không thể kết nối dịch vụ. Vui lòng thử lại.',
      status: status,
      code: error['code'] is String
          ? error['code'] as String
          : 'HTTP_${status ?? 0}',
      correlationId: json['correlationId'] is String
          ? json['correlationId'] as String
          : correlationId,
      fields: {
        if (details is List)
          for (final item in details)
            if (item is Map &&
                item['field'] is String &&
                item['message'] is String)
              item['field'] as String: item['message'] as String,
      },
    );
  }

  final String message;
  final int? status;
  final String code;
  final String? correlationId;
  final Map<String, String> fields;

  @override
  String toString() => message;
}
