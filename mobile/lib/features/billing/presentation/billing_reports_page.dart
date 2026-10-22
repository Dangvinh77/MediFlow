import '../../../core/widgets/reference_widgets.dart';

import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_models.dart';

class BillingReportsPage extends StatefulWidget {
  const BillingReportsPage({
    super.key,
    required this.repository,
    required this.role,
  });
  final BillingRepository repository;
  final String role;
  @override
  State<BillingReportsPage> createState() => _BillingReportsPageState();
}

class _BillingReportsPageState extends State<BillingReportsPage>
    with AutomaticKeepAliveClientMixin<BillingReportsPage> {
  final _from = TextEditingController(
    text: DateTime.now().toIso8601String().substring(0, 10),
  );
  final _to = TextEditingController(
    text: DateTime.now().toIso8601String().substring(0, 10),
  );
  final _department = TextEditingController();
  final _eventId = TextEditingController();
  List<RevenueRow>? _rows;
  Object? _error, _replayError;
  OutboxReplayResult? _replayResult;
  bool _loading = false, _replaying = false, _replayUncertain = false;
  int _generation = 0;

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _generation++;
    _from.dispose();
    _to.dispose();
    _department.dispose();
    _eventId.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final from = _from.text.trim(), to = _to.text.trim();
    final department = _department.text.trim();
    if (!isValidDate(from) ||
        !isValidDate(to) ||
        from.compareTo(to) > 0 ||
        (department.isNotEmpty && !isBillingUuid(department))) {
      setState(
        () => _error = const FormatException(
          'Nhập khoảng ngày hợp lệ; mã khoa phải là UUID hợp lệ.',
        ),
      );
      return;
    }
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final rows = await widget.repository.revenue(
        fromDate: from,
        toDate: to,
        departmentId: department.isEmpty ? null : department,
      );
      if (mounted && generation == _generation) setState(() => _rows = rows);
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _replay() async {
    final eventId = _eventId.text.trim();
    if (!isBillingUuid(eventId)) {
      setState(
        () => _replayError = const FormatException(
          'Mã sự kiện phải là UUID hợp lệ.',
        ),
      );
      return;
    }
    if (_replaying) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Phát lại sự kiện Billing?'),
        content: const Text(
          'Chỉ tiếp tục nếu bạn đã kiểm tra sự kiện cần đưa lại vào xử lý.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Phát lại'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _replaying = true;
      _replayError = null;
      _replayResult = null;
    });
    try {
      final result = await widget.repository.replayOutboxEvent(eventId);
      if (mounted) {
        setState(() {
          _replayResult = result;
          _replayUncertain = false;
        });
      }
    } catch (error) {
      if (mounted) {
        final uncertain =
            error is! ApiFailure ||
            error.status == null ||
            error.status == 408 ||
            error.status! >= 500;
        setState(() {
          _replayError = error;
          _replayUncertain = uncertain;
        });
      }
    } finally {
      if (mounted) setState(() => _replaying = false);
    }
  }

  Future<void> _confirmReplaySameEvent() async {
    if (!_replayUncertain) return;
    setState(() => _replayUncertain = false);
    await _replay();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Doanh thu theo khoa',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _from,
          decoration: const InputDecoration(labelText: 'Từ ngày (YYYY-MM-DD)'),
        ),
        TextField(
          controller: _to,
          decoration: const InputDecoration(labelText: 'Đến ngày (YYYY-MM-DD)'),
        ),
        ReferencePicker(
          kind: ReferenceKind.department,
          label: 'Khoa phòng',
          value: _department.text.isEmpty ? null : _department.text,
          onChanged: _loading
              ? null
              : (id) => setState(() => _department.text = id ?? ''),
        ),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _loading ? null : _load,
          icon: const Icon(Icons.query_stats),
          label: Text(_loading ? 'Đang tải…' : 'Xem báo cáo'),
        ),
        if (_loading) const Center(child: CircularProgressIndicator()),
        if (_error != null)
          RequestError(
            _error!,
            onRetry:
                _error is ApiFailure &&
                    ((_error as ApiFailure).status == null ||
                        (_error as ApiFailure).status! >= 500 ||
                        (_error as ApiFailure).status == 429)
                ? _load
                : null,
          ),
        if (_rows != null && !_loading) ...[
          if (_rows!.isEmpty)
            const Text('Không có doanh thu trong khoảng thời gian này.'),
          for (final row in _rows!)
            Card(
              child: ListTile(
                title: ReferenceLabel(
                  kind: ReferenceKind.department,
                  id: row.departmentId,
                  label: 'Khoa',
                ),
                subtitle: Text('Số hóa đơn: ${row.invoiceCount}'),
                trailing: Text(row.totalRevenue.format()),
              ),
            ),
        ],
        if (widget.role == 'ADMIN') ...[
          const Divider(height: 36),
          Text(
            'Quản trị outbox',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const Text(
            'Chỉ phát lại sự kiện đã được kiểm tra; lệnh này không gửi thông báo cho người bệnh.',
          ),
          TextField(
            controller: _eventId,
            enabled: !_replaying && !_replayUncertain,
            decoration: const InputDecoration(labelText: 'Mã sự kiện (UUID)'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _replaying || _replayUncertain ? null : _replay,
            child: Text(_replaying ? 'Đang phát lại…' : 'Phát lại sự kiện'),
          ),
          if (_replayResult != null)
            Text(
              'Đã phát lại: ${_replayResult!.eventId} · ${_replayResult!.replayed ? 'Có' : 'Không'}',
            ),
          if (_replayError != null) RequestError(_replayError!),
          if (_replayUncertain)
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Kết quả chưa xác định. Đã khóa lệnh với cùng mã sự kiện để đối soát.',
                ),
                OutlinedButton(
                  onPressed: _replaying ? null : _confirmReplaySameEvent,
                  child: const Text('Xác nhận thử lại cùng mã sự kiện'),
                ),
              ],
            ),
        ],
      ],
    );
  }
}
