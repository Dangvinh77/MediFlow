import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/medical_ui.dart';
import '../domain/exam_lifecycle.dart';

final examLifecycleRepositoryProvider = Provider<ExamLifecycleRepository?>(
  (ref) => null,
);

/// Lab owns the cancel command; the app composes it in without this feature
/// importing Lab.
final recordLabCancelProvider =
    Provider<Future<void> Function(String testId, String reason)?>(
      (ref) => null,
    );

const _statusLabels = <String, String>{
  'NOT_RECEIVED': 'Lab chưa tiếp nhận',
  'PENDING': 'Chờ xử lý',
  'AWAITING_PAYMENT': 'Chờ thanh toán',
  'READY': 'Sẵn sàng thực hiện',
  'IN_PROGRESS': 'Đang thực hiện',
  'COMPLETED': 'Đã có kết quả',
  'CANCELLED': 'Đã hủy',
};

class RecordLabOrdersSection extends ConsumerStatefulWidget {
  const RecordLabOrdersSection({
    super.key,
    required this.recordId,
    required this.canChange,
  });
  final String recordId;
  final bool canChange;
  @override
  ConsumerState<RecordLabOrdersSection> createState() =>
      _RecordLabOrdersSectionState();
}

class _RecordLabOrdersSectionState
    extends ConsumerState<RecordLabOrdersSection> {
  final _reason = TextEditingController();
  List<RecordLabOrder>? _orders;
  bool _loading = true, _unavailable = false, _busy = false;
  // Which order is being acted on and how: cancel, postpone or withdraw.
  String? _activeOrderId, _mode, _withdrawKey, _message;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final repository = ref.read(examLifecycleRepositoryProvider);
    if (repository == null) return;
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _unavailable = false;
    });
    try {
      final orders = await repository.labOrders(widget.recordId);
      if (mounted && generation == _generation) {
        setState(() {
          _orders = orders;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(() {
          _orders = null;
          _unavailable = true;
          _loading = false;
        });
      }
    }
  }

  void _begin(RecordLabOrder order, String mode) => setState(() {
    _activeOrderId = order.sourceOrderId;
    _mode = mode;
    // One key per withdrawal attempt, kept across retries of the same order.
    _withdrawKey = mode == 'withdraw' ? newExamCommandKey() : null;
    _message = null;
    _reason.clear();
  });

  Future<void> _send(RecordLabOrder order, String text) async {
    if (_mode == 'withdraw') {
      final repository = ref.read(examLifecycleRepositoryProvider);
      if (repository == null) return;
      await repository.withdrawLabOrder(
        widget.recordId,
        order.sourceOrderId,
        reason: text,
        idempotencyKey: _withdrawKey!,
      );
      return;
    }
    final cancel = ref.read(recordLabCancelProvider);
    if (cancel == null || order.testId == null) return;
    await cancel(
      order.testId!,
      _mode == 'postpone' ? '$labOrderPostponePrefix$text' : text,
    );
  }

  Future<void> _submit(RecordLabOrder order) async {
    final text = _reason.text.trim();
    if (text.isEmpty || _busy) return;
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      await _send(order, text);
      if (!mounted) return;
      setState(() {
        _busy = false;
        _activeOrderId = null;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _message = 'Chưa thực hiện được. Đã tải lại trạng thái từ Lab.';
      });
    }
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final cancel = widget.canChange ? ref.watch(recordLabCancelProvider) : null;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Chỉ định xét nghiệm',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            if (_loading) const LinearProgressIndicator(),
            if (_message != null) NoticeBox(_message!),
            if (_unavailable) ...[
              const NoticeBox(
                'Không kiểm tra được trạng thái xét nghiệm lúc này. Danh sách chỉ định có thể chưa đầy đủ.',
              ),
              OutlinedButton(onPressed: _load, child: const Text('Thử lại')),
            ],
            if (_orders case final orders?)
              if (orders.isEmpty)
                const Text('Chưa có chỉ định xét nghiệm.')
              else
                for (final order in orders)
                  _row(order, cancel != null, widget.canChange),
          ],
        ),
      ),
    );
  }

  Widget _row(RecordLabOrder order, bool canAct, bool canWithdraw) {
    final editing = _activeOrderId == order.sourceOrderId;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            spacing: 8,
            children: [
              Text(order.labType),
              StatusPill(
                _statusLabels[order.status] ?? order.status,
                tone: statusToneOf(order.status),
              ),
            ],
          ),
          if (canAct && order.inProgress && !editing)
            Wrap(
              spacing: 8,
              children: [
                TextButton(
                  onPressed: _busy ? null : () => _begin(order, 'cancel'),
                  child: const Text('Hủy chỉ định'),
                ),
                TextButton(
                  onPressed: _busy ? null : () => _begin(order, 'postpone'),
                  child: const Text('Hoãn sang lần sau'),
                ),
              ],
            ),
          if (canWithdraw && order.canWithdraw && !editing)
            Wrap(
              spacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                TextButton(
                  onPressed: _busy ? null : () => _begin(order, 'withdraw'),
                  child: const Text('Rút chỉ định'),
                ),
                const Text('Lab chưa nhận chỉ định này nên có thể rút lại.'),
              ],
            ),
          if (editing) ...[
            TextField(
              key: const Key('lab-order-action-reason'),
              controller: _reason,
              enabled: !_busy,
              maxLength: 400,
              decoration: InputDecoration(
                labelText: switch (_mode) {
                  'postpone' => 'Lý do hoãn',
                  'withdraw' => 'Lý do rút chỉ định',
                  _ => 'Lý do hủy',
                },
              ),
              onChanged: (_) => setState(() {}),
            ),
            Wrap(
              spacing: 8,
              children: [
                FilledButton(
                  key: const Key('lab-order-action-confirm'),
                  onPressed: _busy || _reason.text.trim().isEmpty
                      ? null
                      : () => _submit(order),
                  child: const Text('Xác nhận'),
                ),
                TextButton(
                  onPressed: _busy
                      ? null
                      : () => setState(() => _activeOrderId = null),
                  child: const Text('Bỏ qua'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
