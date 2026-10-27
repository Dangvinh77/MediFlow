import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';

/// Keep the server code and correlation ID for support, but explain the action
/// in ordinary language on Pharmacy screens.
class PharmacyRequestError extends StatelessWidget {
  const PharmacyRequestError(this.error, {super.key, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final failure = error;
    if (failure is! ApiFailure) {
      return RequestError(error, onRetry: onRetry);
    }
    return RequestError(
      ApiFailure(
        _message(failure),
        status: failure.status,
        code: failure.code,
        correlationId: failure.correlationId,
        fields: failure.fields,
      ),
      onRetry: onRetry,
    );
  }
}

String _message(ApiFailure failure) {
  switch (failure.code) {
    case 'PHARMACY_CLEARANCE_REQUIRED':
      return 'Đơn chưa có xác nhận thanh toán hợp lệ. Vui lòng kiểm tra thanh toán rồi làm mới đơn.';
    case 'PRESCRIPTION_NOT_ACTIVE':
      return 'Đơn thuốc không còn ở trạng thái có thể xuất.';
    case 'DRUG_OUT_OF_STOCK':
      return 'Không đủ thuốc trong kho để hoàn tất thao tác.';
    case 'DRUG_EXPIRED':
      return 'Thuốc đã hết hạn sử dụng.';
  }
  if (failure.status == 401) {
    return 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.';
  }
  if (failure.status == 403) {
    return 'Tài khoản này chưa được phép thực hiện thao tác.';
  }
  if (failure.status == 404) return 'Không tìm thấy thông tin cần tra cứu.';
  if (failure.status == 408 ||
      failure.status == 429 ||
      failure.status == null ||
      failure.status! >= 500) {
    return 'Chưa kết nối được để xác nhận kết quả. Vui lòng kiểm tra lại trước khi thao tác tiếp.';
  }
  if (failure.status == 400 || failure.status == 422) {
    return 'Thông tin chưa hợp lệ. Vui lòng kiểm tra các mục được đánh dấu.';
  }
  if (failure.status == 409) {
    return 'Thông tin đã thay đổi hoặc thao tác không còn phù hợp. Vui lòng làm mới để kiểm tra.';
  }
  return 'Chưa thực hiện được thao tác. Vui lòng thử lại hoặc liên hệ hỗ trợ.';
}
