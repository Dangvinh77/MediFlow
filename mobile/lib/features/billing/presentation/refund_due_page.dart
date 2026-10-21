import 'dart:math';

import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_commands.dart';
import '../domain/billing_models.dart';

String _refundKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

int? _refundMinor(String raw) {
  final value = raw.trim();
  if (!RegExp(r'^(0|[1-9]\d*)(?:\.\d{1,2})?$').hasMatch(value)) return null;
  final parts = value.split('.');
  final whole = int.tryParse(parts.first);
  if (whole == null || whole > 90071992547409) return null;
  final cents = parts.length == 1 ? 0 : int.parse(parts[1].padRight(2, '0'));
  final result = whole * 100 + cents;
  return result <= 9007199254740991 ? result : null;
}

class RefundDuePage extends StatefulWidget {
  const RefundDuePage({super.key, required this.repository});
  final BillingRepository repository;
  @override
  State<RefundDuePage> createState() => _RefundDuePageState();
}

class _RefundDuePageState extends State<RefundDuePage>
    with AutomaticKeepAliveClientMixin<RefundDuePage> {
  final _prescriptionId = TextEditingController();
  PrescriptionAdjustment? _result;
  Object? _error, _refreshError;
  bool _loading = false, _locked = false;
  bool _completionAwaitingRefresh = false;
  bool _writePending = false;
  int _generation = 0, _revision = 0;
  final List<LedgerRefund> _receipts = [];

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _generation++;
    _prescriptionId.dispose();
    super.dispose();
  }

  Future<void> _lookup({bool refreshing = false}) async {
    final id = _prescriptionId.text.trim();
    if (_writePending) return;
    if (_locked && !refreshing) return;
    if (!isBillingUuid(id)) {
      setState(
        () =>
            _error = const FormatException('Mã đơn thuốc phải là UUID hợp lệ.'),
      );
      return;
    }
    final generation = ++_generation;
    setState(() {
      _loading = true;
      if (refreshing) {
        _refreshError = null;
      } else {
        _error = null;
        _result = null;
      }
    });
    try {
      final result = await widget.repository.refundsDue(id);
      if (mounted && generation == _generation) {
        setState(() {
          _result = result;
          _revision++;
          _refreshError = null;
          if (refreshing && _completionAwaitingRefresh) {
            _locked = false;
            _completionAwaitingRefresh = false;
          }
        });
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() {
          if (refreshing) {
            _refreshError = error;
          } else {
            _error = error;
          }
        });
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  void _refundStarted() => setState(() {
    _locked = true;
    _writePending = true;
  });

  void _refundRejected(Object error) {
    if (error is ApiFailure &&
        error.status != null &&
        error.status! >= 400 &&
        error.status! < 500 &&
        error.status != 408) {
      setState(() {
        _locked = false;
        _writePending = false;
      });
    }
  }

  void _refunded(LedgerRefund refund) {
    if (refund.status != 'COMPLETED') return;
    setState(() {
      _receipts.add(refund);
      _locked = true;
      _writePending = false;
      _completionAwaitingRefresh = true;
    });
    _lookup(refreshing: true);
  }

  void _another() {
    if (_locked || _writePending || _loading) return;
    _generation++;
    setState(() {
      _prescriptionId.clear();
      _result = null;
      _error = null;
      _refreshError = null;
      _receipts.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Hoàn tiền đơn thuốc',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        const Text(
          'Tra cứu nghĩa vụ do Billing trả về. Chỉ ghi nhận tiền đã thực tế hoàn; thao tác này không chuyển tiền qua ngân hàng.',
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _prescriptionId,
          enabled: !_locked && !_loading && _result == null,
          decoration: const InputDecoration(
            labelText: 'Mã đơn thuốc (UUID)',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _loading || _locked || _result != null
              ? null
              : () => _lookup(),
          icon: const Icon(Icons.search),
          label: Text(_loading ? 'Đang tra cứu…' : 'Tra cứu nghĩa vụ'),
        ),
        if (_error != null)
          RequestError(
            _error!,
            onRetry:
                _error is ApiFailure &&
                    ((_error as ApiFailure).status == null ||
                        (_error as ApiFailure).status! >= 500 ||
                        (_error as ApiFailure).status == 429)
                ? () => _lookup()
                : null,
          ),
        for (final receipt in _receipts)
          Card(
            child: ListTile(
              title: Text('Biên nhận hoàn tiền · ${receipt.status}'),
              subtitle: SelectableText(
                'Mã giao dịch: ${receipt.refundTransactionId}',
              ),
              trailing: Text('${receipt.amount.format()} ${receipt.currency}'),
            ),
          ),
        if (_result case final result?) ...[
          const Divider(height: 28),
          Text(
            'Điều chỉnh: ${result.status}${result.rejectionCode == null ? '' : ' · ${result.rejectionCode}'}',
          ),
          if (_refreshError != null) ...[
            RequestError(_refreshError!),
            OutlinedButton.icon(
              onPressed: _loading ? null : () => _lookup(refreshing: true),
              icon: const Icon(Icons.refresh),
              label: const Text('Tải lại nghĩa vụ'),
            ),
          ],
          if (_loading) const LinearProgressIndicator(),
          if (_locked && !_writePending && !_loading && _refreshError == null)
            OutlinedButton.icon(
              onPressed: () => _lookup(refreshing: true),
              icon: const Icon(Icons.refresh),
              label: Text(
                _completionAwaitingRefresh
                    ? 'Đối soát nghĩa vụ sau hoàn tiền'
                    : 'Tải lại để kiểm tra trạng thái',
              ),
            ),
          if (result.refundsDue.isEmpty)
            const Text('Không có nghĩa vụ hoàn tiền trong kết quả này.'),
          for (final due in result.refundsDue)
            RefundDueForm(
              key: ValueKey('$_revision:${due.originalTransactionId}'),
              due: due,
              repository: widget.repository,
              blocked: _locked || _loading || _refreshError != null,
              onStart: () {
                if (_locked ||
                    _writePending ||
                    _loading ||
                    _refreshError != null) {
                  return false;
                }
                _refundStarted();
                return true;
              },
              onRejected: _refundRejected,
              onRefunded: _refunded,
            ),
          OutlinedButton(
            onPressed: _loading || _locked ? null : _another,
            child: const Text('Tra cứu đơn khác'),
          ),
          if (_locked)
            const Text(
              'Hoàn tất đối soát trước khi bắt đầu thao tác hoàn tiền khác.',
            ),
        ],
      ],
    );
  }
}

class RefundDueForm extends StatefulWidget {
  const RefundDueForm({
    super.key,
    required this.due,
    required this.repository,
    required this.blocked,
    required this.onStart,
    required this.onRejected,
    required this.onRefunded,
  });
  final RefundDue due;
  final BillingRepository repository;
  final bool blocked;
  final bool Function() onStart;
  final ValueChanged<Object> onRejected;
  final ValueChanged<LedgerRefund> onRefunded;
  @override
  State<RefundDueForm> createState() => _RefundDueFormState();
}

class _RefundDueFormState extends State<RefundDueForm> {
  final _amount = TextEditingController();
  final _reason = TextEditingController();
  String _method = 'CASH';
  RecordRefundCommand? _frozen;
  LedgerRefund? _receipt;
  Object? _error;
  bool _busy = false, _confirmed = false, _locallyLocked = false;

  @override
  void initState() {
    super.initState();
    _amount.text = widget.due.amount.format();
  }

  @override
  void dispose() {
    _amount.dispose();
    _reason.dispose();
    super.dispose();
  }

  Future<void> _submit({RecordRefundCommand? retry}) async {
    if ((retry == null && widget.blocked) ||
        _busy ||
        (retry == null && _locallyLocked) ||
        !_confirmed) {
      return;
    }
    final amount = retry?.amountMinorUnits ?? _refundMinor(_amount.text);
    final reason = retry?.reason ?? _reason.text.trim();
    if (amount == null ||
        amount <= 0 ||
        amount > widget.due.amount.minorUnits ||
        reason.isEmpty ||
        reason.length > 500) {
      setState(
        () => _error = const FormatException(
          'Số tiền phải dương, không vượt nghĩa vụ; lý do bắt buộc và tối đa 500 ký tự.',
        ),
      );
      return;
    }
    if (retry == null && !widget.onStart()) return;
    final command =
        retry ??
        RecordRefundCommand(
          idempotencyKey: _refundKey(),
          amountMinorUnits: amount,
          reason: reason,
          paymentMethod: _method,
        );
    setState(() {
      _busy = true;
      _error = null;
      _frozen = command;
    });
    try {
      final result = await widget.repository.recordRefund(
        widget.due.originalTransactionId,
        command,
      );
      if (!mounted) return;
      setState(() {
        _receipt = result;
        _locallyLocked = result.status != 'COMPLETED';
        if (!_locallyLocked) _frozen = null;
      });
      if (result.status == 'COMPLETED') {
        widget.onRefunded(result);
      } else {
        _error = const ApiFailure(
          'Billing chưa xác nhận trạng thái COMPLETED. Đã khóa thao tác để đối soát.',
        );
      }
    } catch (error) {
      if (!mounted) return;
      final uncertain =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          error.status! >= 500;
      setState(() {
        _error = error;
        _locallyLocked = uncertain;
        if (!uncertain) {
          _frozen = null;
          widget.onRejected(error);
        }
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Nghĩa vụ: ${widget.due.amount.format()} ${widget.due.currency}',
          ),
          SelectableText('Giao dịch gốc: ${widget.due.originalTransactionId}'),
          const Text(
            'Chỉ ghi nhận khoản đã hoàn thực tế; không gửi lệnh chuyển tiền.',
          ),
          const SizedBox(height: 8),
          if (_receipt != null)
            Text(
              'Biên nhận: ${_receipt!.refundTransactionId} · ${_receipt!.status}',
            ),
          if (_error != null) RequestError(_error!),
          TextField(
            controller: _amount,
            enabled:
                !widget.blocked &&
                !_busy &&
                !_locallyLocked &&
                _receipt == null,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: 'Số tiền đã hoàn (${widget.due.currency})',
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _reason,
            enabled:
                !widget.blocked &&
                !_busy &&
                !_locallyLocked &&
                _receipt == null,
            maxLength: 500,
            decoration: const InputDecoration(labelText: 'Lý do'),
          ),
          DropdownButtonFormField<String>(
            initialValue: _method,
            decoration: const InputDecoration(labelText: 'Hình thức hoàn'),
            items: const [
              DropdownMenuItem(value: 'CASH', child: Text('Tiền mặt')),
              DropdownMenuItem(
                value: 'TRANSFER',
                child: Text('Chuyển khoản đã đối soát'),
              ),
            ],
            onChanged:
                widget.blocked || _busy || _locallyLocked || _receipt != null
                ? null
                : (value) => setState(() => _method = value!),
          ),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            value: _confirmed,
            onChanged:
                widget.blocked || _busy || _locallyLocked || _receipt != null
                ? null
                : (value) => setState(() => _confirmed = value ?? false),
            title: const Text('Tôi đã hoàn tiền và đối soát giao dịch gốc.'),
          ),
          if (_locallyLocked && _frozen != null && _receipt == null)
            OutlinedButton(
              onPressed: _busy ? null : () => _submit(retry: _frozen),
              child: const Text('Gửi lại đúng lệnh hoàn tiền đã khóa'),
            )
          else if (_receipt == null)
            FilledButton(
              onPressed: widget.blocked || _busy || !_confirmed
                  ? null
                  : () => _submit(),
              child: Text(_busy ? 'Đang ghi nhận…' : 'Ghi nhận hoàn tiền'),
            ),
        ],
      ),
    ),
  );
}
