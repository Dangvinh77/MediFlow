import '../../../core/l10n/app_l10n.dart';
import '../../../core/widgets/reference_widgets.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../domain/notification.dart' as domain;
import 'providers.dart';
import 'notification_display.dart';
import 'notification_request_error.dart';

class NotificationDetailPage extends ConsumerWidget {
  const NotificationDetailPage({super.key, required this.notificationId});
  final String notificationId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(notificationRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(child: LText('Phiên đăng nhập không còn hoạt động.'));
    }
    if (!const {'ADMIN', 'PATIENT'}.contains(access.role)) {
      return const Center(
        child: LText('Bạn không có quyền xem chi tiết thông báo.'),
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
      return Center(
        child: MediFlowLoader(
          semanticLabel: AppL10n.text(context, 'Đang tải thông báo'),
        ),
      );
    }
    if (_error != null) {
      return NotificationRequestError(
        _error!,
        onRetry: _canRetry ? _load : null,
      );
    }
    final item = _notification!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          LText(item.title, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          ReferenceLabel(
            kind: ReferenceKind.patient,
            id: item.patientId,
            label: 'Người nhận',
          ),
          LText('Kênh: ${notificationChannelLabel(item.channel)}'),
          LText('Trạng thái: ${notificationStatusLabel(item.status)}'),
          LText('Tạo lúc: ${notificationTimeLabel(item.createdAt)}'),
          if (item.sentAt != null)
            LText('Gửi lúc: ${notificationTimeLabel(item.sentAt!)}'),
          if (item.failureReason != null)
            LText(notificationFailureLabel(item.failureReason)),
          const SizedBox(height: 16),
          const LText('Nội dung'),
          SelectableText(item.content),
          const SizedBox(height: 16),
          ExpansionTile(
            title: const LText('Thông tin tra cứu'),
            children: [SelectableText('Mã thông báo: ${item.id}')],
          ),
          OutlinedButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const LText('Làm mới chi tiết'),
          ),
        ],
      ),
    );
  }
}
