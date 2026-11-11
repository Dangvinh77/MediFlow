import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../domain/lab_review.dart';
import 'lab_detail_page.dart';

final labReviewRepositoryProvider = Provider<LabReviewRepository>((ref) {
  final workflow = ref.watch(labWorkflowProvider);
  if (workflow case final LabReviewRepository repository) return repository;
  throw StateError('LabReviewRepository must be bound in the Lab workflow.');
});

class CriticalNoticesPage extends ConsumerWidget {
  const CriticalNoticesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'DOCTOR') {
      return const Center(child: Text('Chức năng dành cho bác sĩ.'));
    }
    return _CriticalNoticesContent(key: ValueKey(access!.sessionIdentity));
  }
}

class _CriticalNoticesContent extends ConsumerStatefulWidget {
  const _CriticalNoticesContent({super.key});

  @override
  ConsumerState<_CriticalNoticesContent> createState() =>
      _CriticalNoticesContentState();
}

class _CriticalNoticesContentState
    extends ConsumerState<_CriticalNoticesContent> {
  List<LabCriticalNotice>? _notices;
  final Set<String> _acknowledging = {};
  final Map<String, String> _rowErrors = {};
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _rowErrors.clear();
    });
    try {
      final notices = await ref
          .read(labReviewRepositoryProvider)
          .criticalNotices(status: 'OPEN');
      if (!mounted) return;
      setState(() {
        _notices = notices;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _acknowledge(LabCriticalNotice notice) async {
    if (notice.isAcknowledged || _acknowledging.contains(notice.noticeId)) {
      return;
    }
    setState(() {
      _acknowledging.add(notice.noticeId);
      _rowErrors.remove(notice.noticeId);
    });
    try {
      final updated = await ref
          .read(labReviewRepositoryProvider)
          .acknowledge(notice.testId, notice.noticeId);
      if (!mounted) return;
      setState(() {
        _notices = [
          for (final item in _notices ?? const <LabCriticalNotice>[])
            if (item.noticeId == updated.noticeId) updated else item,
        ];
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        if (_isReviewDisabled(error)) {
          _error = const LabReviewUnavailable();
        } else if (error is ApiFailure &&
            error.status == 403 &&
            error.code == 'LAB_CRITICAL_NOT_RESPONSIBLE') {
          _rowErrors[notice.noticeId] =
              'Chỉ bác sĩ của khoa chỉ định được xác nhận';
        } else {
          _rowErrors[notice.noticeId] = error.toString();
        }
      });
    } finally {
      if (mounted) {
        setState(() => _acknowledging.remove(notice.noticeId));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: MediFlowLoader());
    if (_isReviewDisabled(_error)) {
      return const Center(child: Text('Chức năng chưa được bật'));
    }
    if (_error != null) {
      return _retryableError('Không tải được cảnh báo xét nghiệm.');
    }
    final notices = _notices ?? const <LabCriticalNotice>[];
    if (notices.isEmpty) {
      return const Center(
        child: Text('Không có cảnh báo nghiêm trọng đang mở.'),
      );
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        for (final notice in notices)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    notice.label,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '${notice.value}${notice.unit == null ? '' : ' ${notice.unit}'}',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      StatusPill(notice.flag, tone: StatusTone.danger),
                      StatusPill('Kết quả lần ${notice.resultVersion}'),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (notice.isAcknowledged)
                    const Text('Đã xác nhận')
                  else
                    FilledButton(
                      onPressed: _acknowledging.contains(notice.noticeId)
                          ? null
                          : () => _acknowledge(notice),
                      child: _acknowledging.contains(notice.noticeId)
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Xác nhận đã nhận'),
                    ),
                  if (_rowErrors[notice.noticeId] case final message?) ...[
                    const SizedBox(height: 8),
                    Text(
                      message,
                      key: ValueKey('critical-notice-error-${notice.noticeId}'),
                      style: TextStyle(color: context.medical.danger),
                    ),
                  ],
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _retryableError(String message) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message),
        TextButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Thử lại'),
        ),
      ],
    ),
  );
}

class LabWorklistPage extends ConsumerWidget {
  const LabWorklistPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'LAB_TECH') {
      return const Center(
        child: Text('Chức năng dành cho kỹ thuật viên xét nghiệm.'),
      );
    }
    return _LabWorklistContent(key: ValueKey(access!.sessionIdentity));
  }
}

class _LabWorklistContent extends ConsumerStatefulWidget {
  const _LabWorklistContent({super.key});

  @override
  ConsumerState<_LabWorklistContent> createState() =>
      _LabWorklistContentState();
}

class _LabWorklistContentState extends ConsumerState<_LabWorklistContent> {
  List<LabWorklistItem>? _items;
  String _group = '';
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final items = await ref.read(labReviewRepositoryProvider).worklistItems();
      if (!mounted) return;
      setState(() {
        _items = items;
        _loading = false;
        if (_group.isNotEmpty &&
            !items.any((item) => item.displayGroup == _group)) {
          _group = '';
        }
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: MediFlowLoader());
    if (_isReviewDisabled(_error)) {
      return const Center(child: Text('Chức năng chưa được bật'));
    }
    if (_error != null) return _retryableError();
    final items = _items ?? const <LabWorklistItem>[];
    final groups =
        items
            .map((item) => item.displayGroup)
            .whereType<String>()
            .where((group) => group.isNotEmpty)
            .toSet()
            .toList()
          ..sort();
    final visible = items
        .where((item) => _group.isEmpty || item.displayGroup == _group)
        .toList();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: DropdownButtonFormField<String>(
            key: const ValueKey('lab-worklist-group'),
            initialValue: _group,
            decoration: const InputDecoration(labelText: 'Nhóm xét nghiệm'),
            items: [
              const DropdownMenuItem(value: '', child: Text('Tất cả nhóm')),
              for (final group in groups)
                DropdownMenuItem(value: group, child: Text(group)),
            ],
            onChanged: (group) => setState(() => _group = group ?? ''),
          ),
        ),
        Expanded(
          child: visible.isEmpty
              ? const Center(child: Text('Không có xét nghiệm trong nhóm này.'))
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: visible.length,
                  itemBuilder: (context, index) =>
                      _worklistCard(context, visible[index]),
                ),
        ),
      ],
    );
  }

  Widget _worklistCard(BuildContext context, LabWorklistItem item) {
    final medical = context.medical;
    return Card(
      color: item.overdue ? medical.danger.withValues(alpha: 0.06) : null,
      child: ListTile(
        leading: Icon(
          item.overdue ? Icons.warning_amber_rounded : Icons.science_outlined,
          color: item.overdue ? medical.danger : null,
        ),
        title: Text(item.serviceName ?? item.labType),
        subtitle: Text(
          [
            item.labType,
            if (item.displayGroup != null) item.displayGroup!,
            'Trạng thái: ${item.testStatus}',
            if (item.specimenStatus != null) 'Mẫu: ${item.specimenStatus}',
            if (item.reportStatus != null) 'Báo cáo: ${item.reportStatus}',
          ].join('\n'),
        ),
        trailing: item.overdue
            ? const StatusPill('Quá hạn', tone: StatusTone.danger)
            : null,
      ),
    );
  }

  Widget _retryableError() => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text('Không tải được danh sách xét nghiệm.'),
        TextButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Thử lại'),
        ),
      ],
    ),
  );
}

bool _isReviewDisabled(Object? error) =>
    error is LabReviewUnavailable ||
    error is ApiFailure &&
        error.status == 404 &&
        error.code == 'LAB_REVIEW_DISABLED';
