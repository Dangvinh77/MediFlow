import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_models.dart';
import '../domain/lab_quote_payment.dart';
import '../domain/scheduled_payments.dart';

bool _outcomeUnknown(Object error) {
  if (error is! ApiFailure ||
      error.status == null ||
      error.status! < 400 ||
      error.status! >= 500 ||
      error.status == 408) {
    return true;
  }
  return error.code.contains('IDEMPOTENCY_CONFLICT') ||
      error.code.endsWith('IDEMPOTENCY_KEY_REUSED');
}

/// Collects a lab fee from the current quote, so support policy decides the
/// amount. The cashier cannot enter an amount or a partial payment here.
class LabQuotePaymentPanel extends StatefulWidget {
  const LabQuotePaymentPanel({
    super.key,
    required this.paymentRequestId,
    required this.repository,
    required this.recoveryStore,
    required this.zeroEnabled,
    required this.onLockChange,
    required this.onPaid,
    required this.onZeroConfirmed,
  });

  final String paymentRequestId;
  final LabQuotePaymentRepository repository;
  final LabPaymentRecoveryStore recoveryStore;
  final bool zeroEnabled;
  final ValueChanged<bool> onLockChange;
  final ValueChanged<LedgerPayment> onPaid;
  final VoidCallback onZeroConfirmed;

  @override
  State<LabQuotePaymentPanel> createState() => _LabQuotePaymentPanelState();
}

class _LabQuotePaymentPanelState extends State<LabQuotePaymentPanel> {
  OutpatientQuote? _quote;
  LabPaymentRecovery? _recovery;
  Object? _quoteError, _recoveryError, _commandError;
  LedgerPayment? _pending;
  bool _quoteLoading = true,
      _recoveryLoading = true,
      _quoteUnavailable = false,
      _busy = false;
  String _method = 'CASH';
  int _quoteGeneration = 0;

  @override
  void initState() {
    super.initState();
    _readRecovery();
    _loadQuote();
  }

  @override
  void dispose() {
    _quoteGeneration++;
    super.dispose();
  }

  Future<void> _readRecovery() async {
    try {
      final recovery = await widget.recoveryStore.read(widget.paymentRequestId);
      if (!mounted) return;
      setState(() {
        _recovery = recovery;
        _recoveryLoading = false;
      });
      if (recovery != null) widget.onLockChange(true);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _recoveryError = error;
        _recoveryLoading = false;
      });
      widget.onLockChange(true);
    }
  }

  Future<void> _loadQuote() async {
    final generation = ++_quoteGeneration;
    setState(() {
      _quoteLoading = true;
      _quoteError = null;
      _quoteUnavailable = false;
    });
    try {
      final quote = await widget.repository.outpatientQuote(
        widget.paymentRequestId,
      );
      if (!mounted || generation != _quoteGeneration) return;
      if (quote.paymentRequestId.toLowerCase() !=
          widget.paymentRequestId.toLowerCase()) {
        throw const FormatException('Báo giá không khớp khoản thu.');
      }
      setState(() => _quote = quote);
    } catch (error) {
      if (!mounted || generation != _quoteGeneration) return;
      setState(() {
        if (error is ApiFailure && error.status == 404) {
          _quoteUnavailable = true;
        } else {
          _quoteError = error;
        }
      });
    } finally {
      if (mounted && generation == _quoteGeneration) {
        setState(() => _quoteLoading = false);
      }
    }
  }

  bool get _quoteCurrent {
    final expiresAt = DateTime.tryParse(_quote?.expiresAt ?? '');
    return expiresAt != null && expiresAt.isAfter(DateTime.now());
  }

  bool get _awaitingOutcome => _recovery != null || _pending != null;

  LabPaymentRecovery _freshRecovery(
    OutpatientQuote quote, {
    required bool zero,
  }) => LabPaymentRecovery(
    idempotencyKey: newLabCommandKey(),
    correlationId: newLabCommandKey(),
    quoteId: quote.quoteId,
    payment: zero
        ? null
        : LabPaymentCommand(
            quoteId: quote.quoteId,
            amount: quote.patientPayable.format(),
            currency: quote.currency,
            paymentMethod: _method,
          ),
  );

  Future<void> _send(LabPaymentRecovery selected) async {
    if (_busy || _recoveryLoading) return;
    final resend = _recovery != null;
    setState(() {
      _busy = true;
      _commandError = null;
    });
    widget.onLockChange(true);
    try {
      // Persist first: a lost response must resend the same key after a restart.
      await widget.recoveryStore.save(widget.paymentRequestId, selected);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _commandError = error;
        _busy = false;
      });
      widget.onLockChange(resend);
      return;
    }
    try {
      final payment = selected.payment;
      if (payment == null) {
        await widget.repository.confirmLabZeroLiability(
          widget.paymentRequestId,
          selected.quoteId,
          idempotencyKey: selected.idempotencyKey,
          correlationId: selected.correlationId,
        );
        await widget.recoveryStore.clear(widget.paymentRequestId);
        if (!mounted) return;
        setState(() => _recovery = null);
        widget.onLockChange(false);
        widget.onZeroConfirmed();
        return;
      }
      final result = await widget.repository.payLabQuote(
        widget.paymentRequestId,
        payment,
        idempotencyKey: selected.idempotencyKey,
        correlationId: selected.correlationId,
      );
      if (result.status == 'PENDING') {
        if (!mounted) return;
        setState(() {
          _recovery = selected;
          _pending = result;
        });
        return;
      }
      await widget.recoveryStore.clear(widget.paymentRequestId);
      if (!mounted) return;
      setState(() {
        _recovery = null;
        _pending = null;
        if (result.status != 'COMPLETED') {
          _commandError = const ApiFailure(
            'Giao dịch chưa hoàn tất. Tra cứu lại khoản thu trước khi tạo giao dịch khác.',
          );
        }
      });
      widget.onLockChange(false);
      if (result.status == 'COMPLETED') widget.onPaid(result);
    } catch (error) {
      if (!mounted) return;
      final unknown = _outcomeUnknown(error);
      if (unknown) {
        setState(() {
          _commandError = error;
          _recovery = selected;
        });
        return;
      }
      try {
        await widget.recoveryStore.clear(widget.paymentRequestId);
        if (!mounted) return;
        setState(() {
          _commandError = error;
          _recovery = null;
        });
        widget.onLockChange(false);
      } catch (_) {
        if (!mounted) return;
        setState(() {
          _commandError = error;
          _recovery = selected;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final quote = _quote;
    final recovery = _recovery;
    final zero = quote?.patientPayable.minorUnits == 0;
    final canCollect =
        quote != null &&
        !_recoveryLoading &&
        _recoveryError == null &&
        !_awaitingOutcome &&
        _quoteCurrent;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_quoteLoading) const Text('Đang tải báo giá Billing…'),
          if (quote != null) _QuoteBreakdown(quote),
          if (_quoteUnavailable)
            const Text(
              'Billing chưa có báo giá cho yêu cầu này. Không xem đây là khoản miễn phí; cần có báo giá trước khi thu xét nghiệm.',
            ),
          if (_quoteError != null) ...[
            RequestError(_quoteError!),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: _quoteLoading || _busy ? null : _loadQuote,
                child: const Text('Thử tải lại báo giá'),
              ),
            ),
          ],
          if (_recoveryLoading) const Text('Đang kiểm tra yêu cầu thu đã lưu.'),
          if (_recoveryError != null) RequestError(_recoveryError!),
          if (_commandError != null) RequestError(_commandError!),
          if (quote != null && !_quoteCurrent && recovery == null)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: Text(
                'Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.',
              ),
            ),
          if (recovery != null) ...[
            const SizedBox(height: 8),
            Text(
              recovery.payment == null
                  ? 'Có yêu cầu xác nhận chưa rõ kết quả. Báo giá ban đầu được giữ nguyên; không tạo yêu cầu khác.'
                  : 'Có giao dịch ${recovery.payment!.amount} ${recovery.payment!.currency} chưa rõ kết quả. Gửi lại đúng lệnh này để kiểm tra; không tạo giao dịch mới.',
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: _busy ? null : () => _send(recovery),
              child: Text(
                recovery.payment == null
                    ? 'Gửi lại cùng yêu cầu xác nhận 0 đồng'
                    : 'Gửi lại cùng yêu cầu thu',
              ),
            ),
          ],
          if (canCollect && zero) ..._zeroActions(quote),
          if (canCollect && !zero) ..._paymentActions(quote),
        ],
      ),
    );
  }

  List<Widget> _zeroActions(OutpatientQuote quote) => [
    const SizedBox(height: 8),
    if (widget.zeroEnabled) ...[
      const Text(
        'Báo giá xác nhận người bệnh không phải trả tiền. Xác nhận này không ghi nhận tiền mặt.',
      ),
      const SizedBox(height: 8),
      FilledButton(
        onPressed: _busy
            ? null
            : () => _send(_freshRecovery(quote, zero: true)),
        child: const Text('Xác nhận nghĩa vụ 0 đồng'),
      ),
    ] else
      const Text(
        'Báo giá là 0 đồng nhưng chức năng xác nhận nghĩa vụ 0 chưa được mở. Không thu tiền mặt.',
      ),
  ];

  List<Widget> _paymentActions(OutpatientQuote quote) => [
    const SizedBox(height: 12),
    InputDecorator(
      decoration: const InputDecoration(
        labelText: 'Số tiền theo báo giá',
        border: OutlineInputBorder(),
      ),
      child: Text('${quote.patientPayable.format()} ${quote.currency}'),
    ),
    const SizedBox(height: 12),
    DropdownButtonFormField<String>(
      isExpanded: true,
      initialValue: _method,
      decoration: const InputDecoration(labelText: 'Hình thức thanh toán'),
      items: const [
        DropdownMenuItem(value: 'CASH', child: Text('Tiền mặt')),
        DropdownMenuItem(value: 'BANK_TRANSFER', child: Text('Chuyển khoản')),
      ],
      onChanged: _busy ? null : (value) => setState(() => _method = value!),
    ),
    const SizedBox(height: 12),
    FilledButton(
      onPressed: _busy ? null : () => _send(_freshRecovery(quote, zero: false)),
      child: Text(_busy ? 'Đang thu…' : 'Xác nhận thu theo báo giá'),
    ),
  ];
}

class _QuoteBreakdown extends StatelessWidget {
  const _QuoteBreakdown(this.quote);
  final OutpatientQuote quote;

  @override
  Widget build(BuildContext context) => ExpansionTile(
    tilePadding: EdgeInsets.zero,
    title: const Text('Chi tiết giá và hỗ trợ mô phỏng'),
    subtitle: Text(
      'Người bệnh phải trả: ${quote.patientPayable.format()} ${quote.currency}',
    ),
    children: [
      for (final line in quote.lines)
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(line.description),
          subtitle: Text(
            '${line.grossAmount.format()} − hỗ trợ ${line.supportAmount.format()}',
          ),
          trailing: Text(line.patientPayable.format()),
        ),
    ],
  );
}
