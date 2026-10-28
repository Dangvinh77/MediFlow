import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';

class NotificationRequestError extends StatelessWidget {
  const NotificationRequestError(this.error, {super.key, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final failure = error;
    if (failure is! ApiFailure) {
      return RequestError(
        FormatException(
          failure is FormatException &&
                  failure.message == 'Vui lòng chọn lại bệnh nhân.'
              ? 'Vui lòng chọn lại bệnh nhân.'
              : failure is ArgumentError
              ? 'Vui lòng kiểm tra người nhận, tiêu đề, nội dung và cách gửi.'
              : 'Chưa đọc được thông báo. Vui lòng thử lại.',
        ),
        onRetry: onRetry,
      );
    }
    final message = switch (failure.status) {
      400 || 422 => 'Thông tin chưa hợp lệ. Vui lòng kiểm tra và thử lại.',
      401 => 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.',
      403 => 'Tài khoản này chưa được phép thực hiện thao tác.',
      404 => 'Không tìm thấy thông báo cần xem.',
      409 => 'Thông tin đã thay đổi. Vui lòng làm mới và kiểm tra lại.',
      _ => 'Chưa xác nhận được kết quả. Vui lòng kiểm tra lịch sử trước khi thử lại.',
    };
    return RequestError(
      ApiFailure(
        message,
        status: failure.status,
        code: failure.code,
        correlationId: failure.correlationId,
      ),
      onRetry: onRetry,
    );
  }
}
