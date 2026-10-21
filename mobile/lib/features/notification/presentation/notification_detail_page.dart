import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/notification.dart' as domain;
import 'providers.dart';

class NotificationDetailPage extends ConsumerWidget {
  const NotificationDetailPage({super.key, required this.notificationId});
  final String notificationId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(notificationRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(child: Text('Phiên đăng nhập không còn hoạt động.'));
    }
    if (!const {'ADMIN', 'PATIENT'}.contains(access.role)) {
      return const Center(
        child: Text('Bạn không có quyền xem chi tiết thông báo.'),
      );
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, access.role, repository)),
      child: _NotificationDetail(
        key: ValueKey(notificationId),
        notificationId: notificationId,
        repository: repository,
      ),
    );
  }
}

class _NotificationDetail extends StatefulWidget {
  const _NotificationDetail({
    super.key,
    required this.notificationId,
    required this.repository,
  });
  final String notificationId;
  final domain.NotificationRepository repository;
  @override
  State<_NotificationDetail> createState() => _NotificationDetailState();
}

class _NotificationDetailState extends State<_NotificationDetail> {
  domain.PatientNotification? _notification;
  Object? _error;
  bool _loading = true;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _notification = null;
    });
    try {
      final result = await domain.ReadNotification(widget.repository)(
        widget.notificationId,
      );
      if (mounted && generation == _generation) {
        setState(() => _notification = result);
      }
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  bool get _canRetry =>
      _error is! FormatException &&
      _error is! ArgumentError &&
      (_error is! ApiFailure ||
          (_error as ApiFailure).status == null ||
          (_error as ApiFailure).status! >= 500 ||
          (_error as ApiFailure).status == 429);

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(
        child: CircularProgressIndicator(semanticsLabel: 'Đang tải thông báo'),
      );
    }
    if (_error != null) {
      return RequestError(_error!, onRetry: _canRetry ? _load : null);
    }
    final item = _notification!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(item.title, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          SelectableText('Mã thông báo: ${item.id}'),
          SelectableText('Mã bệnh nhân: ${item.patientId}'),
          Text('Kênh: ${item.channel}'),
          Text('Trạng thái: ${item.status}'),
          Text('Tạo lúc: ${item.createdAt}'),
          if (item.sentAt != null) Text('Gửi lúc: ${item.sentAt}'),
          if (item.failureReason != null)
            Text('Lý do thất bại: ${item.failureReason}'),
          const SizedBox(height: 16),
          const Text('Nội dung'),
          SelectableText(item.content),
          const SizedBox(height: 16),
          const Text(
            'Địa chỉ nhận và dữ liệu thử lại không được cung cấp bởi API này.',
          ),
          OutlinedButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Làm mới chi tiết'),
          ),
        ],
      ),
    );
  }
}
