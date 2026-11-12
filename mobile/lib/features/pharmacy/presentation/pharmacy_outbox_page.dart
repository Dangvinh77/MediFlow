import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../domain/pharmacy_outbox.dart';
import 'providers.dart';

final pharmacyOutboxRepositoryProvider = Provider<PharmacyOutboxRepository?>(
  (ref) => null,
);

final _uuid = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

/// ADMIN-only recovery for one sync event, identified by technical support.
class PharmacyOutboxPage extends ConsumerStatefulWidget {
  const PharmacyOutboxPage({super.key});

  @override
  ConsumerState<PharmacyOutboxPage> createState() => _PharmacyOutboxPageState();
}

class _PharmacyOutboxPageState extends ConsumerState<PharmacyOutboxPage> {
  final _eventId = TextEditingController();
  PharmacyOutboxReplay? _result;
  String? _fieldError, _error;
  bool _busy = false, _forbidden = false;

  @override
  void dispose() {
    _eventId.dispose();
    super.dispose();
  }

  String _message(Object error) {
    if (error is! ApiFailure) {
      return 'Chưa xác định kết quả khôi phục; hãy kiểm tra trạng thái yêu cầu trước khi thử lại.';
    }
    if (error.status == 403) {
      return 'Bạn không có quyền khôi phục đồng bộ dữ liệu.';
    }
    if (error.status == 404 || error.code == 'OUTBOX_EVENT_NOT_FOUND') {
      return 'Không tìm thấy yêu cầu đồng bộ với mã đã nhập.';
    }
    final unknown =
        error.status == null || error.status == 408 || error.status! >= 500;
    return unknown
        ? '${error.message} Chưa xác định kết quả khôi phục; hãy kiểm tra trạng thái yêu cầu trước khi thử lại.'
        : error.message;
  }

  Future<void> _replay() async {
    final eventId = _eventId.text.trim();
    if (_busy || _forbidden) return;
    if (!_uuid.hasMatch(eventId)) {
      setState(() {
        _error = null;
        _fieldError = 'Mã yêu cầu không hợp lệ. Vui lòng kiểm tra mã do bộ phận kỹ thuật cung cấp.';
      });
      return;
    }
    setState(() => _fieldError = null);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const LText('Khôi phục yêu cầu đồng bộ?'),
        content: SelectableText('Mã yêu cầu: $eventId'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const LText('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const LText('Xác nhận khôi phục'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(pharmacyOutboxRepositoryProvider)!
          .replayOutbox(eventId);
      if (mounted) setState(() => _result = result);
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = _message(error);
          _forbidden = error is ApiFailure && error.status == 403;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _reset() => setState(() {
    _eventId.clear();
    _result = null;
    _error = null;
  });

  @override
  Widget build(BuildContext context) {
    if (ref.watch(pharmacyAccessProvider)?.role != 'ADMIN' ||
        ref.watch(pharmacyOutboxRepositoryProvider) == null) {
      return const Center(
        child: LText(
          'Tài khoản của bạn không có quyền khôi phục đồng bộ dữ liệu.',
        ),
      );
    }
    final result = _result;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (result != null) ...[
          LText(
            result.replayed
                ? 'Đã gửi yêu cầu đồng bộ lại'
                : 'Chưa gửi được yêu cầu đồng bộ lại',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          SelectableText('Mã yêu cầu: ${result.eventId}'),
          LText('Đã gửi lại: ${result.replayed ? 'Có' : 'Không'}'),
          const SizedBox(height: 8),
          const LText(
            'Kết quả nghiệp vụ có thể chưa cập nhật ngay; liên hệ bộ phận kỹ thuật để kiểm tra nếu dữ liệu vẫn chưa đồng bộ.',
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _reset,
            child: const LText('Khôi phục yêu cầu khác'),
          ),
        ] else ...[
          LText(
            'Khôi phục một yêu cầu đồng bộ',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const LText(
            'Chỉ dành cho quản trị viên khi xử lý sự cố đồng bộ. Nhập mã yêu cầu do bộ phận kỹ thuật cung cấp.',
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _eventId,
            enabled: !_busy && !_forbidden,
            onChanged: (_) => setState(() {
              _fieldError = null;
              _error = null;
            }),
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Mã yêu cầu'),
              hintText: AppL10n.text(context, 'Dán mã yêu cầu cần khôi phục'),
              errorText: AppL10n.text(context, _fieldError),
              errorMaxLines: 3,
              border: const OutlineInputBorder(),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: LText(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _busy || _forbidden ? null : _replay,
            child: LText(_busy ? 'Đang gửi…' : 'Xem xác nhận'),
          ),
        ],
      ],
    );
  }
}
