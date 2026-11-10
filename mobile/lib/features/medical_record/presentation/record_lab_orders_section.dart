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
  bool _loading = true, _unavailable = false, _busy = false, _postpone = false;
  String? _activeTestId, _message;
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

  void _begin(String testId, {required bool postpone}) => setState(() {
    _activeTestId = testId;
    _postpone = postpone;
    _message = null;
    _reason.clear();
  });

  Future<void> _submit() async {
    final cancel = ref.read(recordLabCancelProvider);
    final testId = _activeTestId;
    final text = _reason.text.trim();
    if (cancel == null || testId == null || text.isEmpty || _busy) return;
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      await cancel(testId, _postpone ? '$labOrderPostponePrefix$text' : text);
      if (!mounted) return;
      setState(() {
        _busy = false;
        _activeTestId = null;
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
                for (final order in orders) _row(order, cancel != null),
          ],
        ),
      ),
    );
  }

  Widget _row(RecordLabOrder order, bool canAct) {
    final editing = _activeTestId != null && _activeTestId == order.testId;
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
                  onPressed: _busy
                      ? null
                      : () => _begin(order.testId!, postpone: false),
                  child: const Text('Hủy chỉ định'),
                ),
                TextButton(
                  onPressed: _busy
                      ? null
                      : () => _begin(order.testId!, postpone: true),
                  child: const Text('Hoãn sang lần sau'),
                ),
              ],
            ),
          if (editing) ...[
            TextField(
              key: const Key('lab-order-action-reason'),
              controller: _reason,
              enabled: !_busy,
              maxLength: 400,
              decoration: InputDecoration(
                labelText: _postpone ? 'Lý do hoãn' : 'Lý do hủy',
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
                      : _submit,
                  child: const Text('Xác nhận'),
                ),
                TextButton(
                  onPressed: _busy
                      ? null
                      : () => setState(() => _activeTestId = null),
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
