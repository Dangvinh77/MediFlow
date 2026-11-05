import 'package:flutter/material.dart';

import '../network/api_failure.dart';
import '../theme/medical_colors.dart';
import 'medical_ui.dart';

class RequestError extends StatelessWidget {
  const RequestError(this.error, {super.key, this.onRetry});
  final Object error;
  final VoidCallback? onRetry;
  @override
  Widget build(BuildContext context) {
    final failure = error is ApiFailure ? error as ApiFailure : null;
    final message =
        failure?.message ??
        (error is FormatException
            ? (error as FormatException).message
            : 'Không thể tải dữ liệu. Vui lòng thử lại.');
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: StateMessage(
          icon: Icons.error_outline,
          color: context.medical.danger,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(message, textAlign: TextAlign.center),
              if (failure?.correlationId != null) ...[
                const SizedBox(height: 8),
                SelectableText(
                  'Mã hỗ trợ: ${failure!.correlationId}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
              if (onRetry != null) ...[
                const SizedBox(height: 8),
                TextButton.icon(
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Thử lại'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
