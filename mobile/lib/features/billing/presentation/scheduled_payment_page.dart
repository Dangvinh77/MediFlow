import '../../../core/l10n/app_l10n.dart';

import 'dart:math';

import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_models.dart';
import '../domain/scheduled_payments.dart';

String _newUuid() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

String _statusLabel(String status) => switch (status) {
  'PENDING' => 'Chờ thanh toán',
  'PARTIALLY_PAID' => 'Đã thanh toán một phần',
  'PAID' => 'Đã thanh toán',
  'EXPIRED' => 'Hết hạn',
  'CANCELLED' => 'Đã hủy',
  _ => 'Cần kiểm tra',
};

bool _definitivelyRejected(Object error) {
  if (error is! ApiFailure ||
      error.status == null ||
      error.status! < 400 ||
      error.status! >= 500 ||
      error.status == 408 ||
      error.code == 'PAYMENT_OPERATION_CLOSED') {
    return false;
  }
  return !error.code.contains('IDEMPOTENCY_CONFLICT') &&
      !error.code.endsWith('IDEMPOTENCY_KEY_REUSED');
}

bool _closedWithoutPayment(Object error) =>
    error is ApiFailure && error.code == 'PAYMENT_OPERATION_CLOSED';

const double _minQueueHeight = 300;

String _localTime(String? raw) {
  final value = raw == null ? null : DateTime.tryParse(raw)?.toLocal();
  if (value == null) return '—';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(value.hour)}:${two(value.minute)} '
      '${two(value.day)}/${two(value.month)}/${value.year}';
}

class ScheduledPaymentQueuePage extends StatefulWidget {
  const ScheduledPaymentQueuePage({
    super.key,
    required this.repository,
    required this.recoveryStore,
    required this.quoteEnabled,
    required this.zeroPayableEnabled,
  });

  final ScheduledPaymentRepository repository;
  final ScheduledPaymentRecoveryStore recoveryStore;
  final bool quoteEnabled, zeroPayableEnabled;

  @override
  State<ScheduledPaymentQueuePage> createState() =>
      _ScheduledPaymentQueuePageState();
}

class _ScheduledPaymentQueuePageState extends State<ScheduledPaymentQueuePage> {
  final _registrationId = TextEditingController();
  String? _patientFilter;
  int _revision = 0;
  bool _lookupBusy = false;
  Object? _lookupError;

  @override
  void dispose() {
    _registrationId.dispose();
    super.dispose();
  }

  Future<void> _lookupManual() async {
    final registrationId = _registrationId.text.trim();
    if (_lookupBusy) return;
    if (!isBillingUuid(registrationId)) {
      setState(
        () => _lookupError = const FormatException(
          'Mã đăng ký phải là UUID hợp lệ.',
        ),
      );
      return;
    }
    await _open(registrationId);
  }

  Future<void> _open(String registrationId) async {
    if (_lookupBusy) return;
    setState(() {
      _lookupBusy = true;
      _lookupError = null;
    });
    try {
      final request = await widget.repository
          .scheduledPaymentRequestByRegistration(registrationId);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) => Scaffold(
            appBar: AppBar(title: const LText('Thu phí khám')),
            body: ScheduledPaymentCollectionPage(
              repository: widget.repository,
              recoveryStore: widget.recoveryStore,
              request: request,
              quoteEnabled: widget.quoteEnabled,
              zeroPayableEnabled: widget.zeroPayableEnabled,
            ),
          ),
        ),
      );
      if (mounted) setState(() => _revision++);
    } catch (error) {
      if (mounted) setState(() => _lookupError = error);
    } finally {
      if (mounted) setState(() => _lookupBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final header = Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LText('Thu phí khám', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const LText('Chọn bệnh nhân trong hàng chờ để đối chiếu khoản phí.'),
          const SizedBox(height: 12),
          ReferencePicker(
            kind: ReferenceKind.patient,
            label: 'Tìm bệnh nhân',
            value: _patientFilter,
            onChanged: (id) => setState(() {
              _lookupError = null;
              _patientFilter = id;
              _revision++;
            }),
          ),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () => setState(() => _revision++),
              icon: const Icon(Icons.refresh),
              label: const LText('Cập nhật hàng chờ'),
            ),
          ),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            title: const LText('Tra cứu theo mã đăng ký'),
            children: [
              TextField(
                controller: _registrationId,
                keyboardType: TextInputType.text,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Mã đăng ký (UUID)'),
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _lookupManual(),
              ),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  onPressed: _lookupBusy ? null : _lookupManual,
                  icon: _lookupBusy
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.search),
                  label: const LText('Tra cứu phí khám'),
                ),
              ),
            ],
          ),
          if (_lookupError != null) ...[
            const SizedBox(height: 8),
            RequestError(_lookupError!),
          ],
        ],
      ),
    );
    final queue = PagedList<ScheduledPaymentQueueItem>(
      key: ValueKey((_patientFilter, _revision)),
      load: (page) => widget.repository.scheduledPaymentQueue(
        patientId: _patientFilter,
        page: page,
      ),
      itemBuilder: (context, item) => Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ReferenceLabel(
                kind: ReferenceKind.patient,
                id: item.patientId,
                label: 'Bệnh nhân',
              ),
              const SizedBox(height: 4),
              ReferenceLabel(
                kind: ReferenceKind.department,
                id: item.departmentId,
                label: 'Khoa',
              ),
              LText('Tạo lúc: ${_localTime(item.createdAt)}'),
              LText('Hạn giữ chỗ: ${_localTime(item.expiresAt)}'),
              const SizedBox(height: 8),
              FilledButton.icon(
                onPressed: _lookupBusy
                    ? null
                    : () => _open(item.registrationId),
                icon: const Icon(Icons.payments_outlined),
                label: const LText('Đối chiếu và thu phí'),
              ),
            ],
          ),
        ),
      ),
    );

    return LayoutBuilder(
      // One stable scroll layout: the Scaffold has already removed the keyboard
      // inset here, and swapping trees on keyboard changes would drop field focus.
      builder: (context, constraints) => SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            header,
            SizedBox(
              height: max(_minQueueHeight, constraints.maxHeight * 0.6),
              child: queue,
            ),
          ],
        ),
      ),
    );
  }
}

class ScheduledPaymentCollectionPage extends StatefulWidget {
  const ScheduledPaymentCollectionPage({
    super.key,
    required this.repository,
    required this.recoveryStore,
    required this.request,
    required this.quoteEnabled,
    required this.zeroPayableEnabled,
  });

  final ScheduledPaymentRepository repository;
  final ScheduledPaymentRecoveryStore recoveryStore;
  final RegistrationPaymentRequest request;
  final bool quoteEnabled, zeroPayableEnabled;

  @override
  State<ScheduledPaymentCollectionPage> createState() =>
      _ScheduledPaymentCollectionPageState();
}

class _ScheduledPaymentCollectionPageState
    extends State<ScheduledPaymentCollectionPage> {
  final _providerReference = TextEditingController();
  late RegistrationPaymentRequest _request;
  OutpatientQuote? _quote;
  ScheduledPaymentRecovery? _recovery;
  ScheduledPaymentResult? _result;
  final _form = GlobalKey<FormState>();
  String _method = 'CASH';
  Object? _quoteError, _recoveryLoadError, _error, _refreshError;
  bool _quoteUnavailable = false,
      _quoteLoading = false,
      _recoveryLoading = true,
      _recoveryReady = false,
      _busy = false,
      _checking = false,
      _completed = false,
      _closed = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _request = widget.request;
    _readRecovery();
    if (widget.quoteEnabled) _loadQuote();
  }

  @override
  void dispose() {
    _generation++;
    _providerReference.dispose();
    super.dispose();
  }

  bool get _quoteExpired {
    final expiresAt = _quote?.expiresAt;
    final instant = expiresAt == null ? null : DateTime.tryParse(expiresAt);
    return instant == null || !instant.isAfter(DateTime.now());
  }

  bool get _quoteIsZero => _quote?.patientPayable.minorUnits == 0;

  Future<void> _readRecovery() async {
    try {
      final recovery = await widget.recoveryStore.read(
        _request.paymentRequestId,
      );
      if (!mounted) return;
      if (recovery != null && !_recoveryMatchesRequest(recovery, _request)) {
        throw const FormatException(
          'Yêu cầu thu đã lưu không khớp khoản phí khám.',
        );
      }
      setState(() {
        _recovery = recovery;
        _recoveryReady = true;
        _recoveryLoadError = null;
        if (recovery?.paymentCommand != null) {
          _method = recovery!.paymentCommand!.paymentMethod;
          _providerReference.text =
              recovery.paymentCommand!.providerReference ?? '';
        }
      });
    } catch (error) {
      if (mounted) setState(() => _recoveryLoadError = error);
    } finally {
      if (mounted) setState(() => _recoveryLoading = false);
    }
  }

  Future<void> _retryRecoveryRead() async {
    setState(() {
      _recoveryLoading = true;
      _recoveryReady = false;
      _recoveryLoadError = null;
    });
    await _readRecovery();
  }

  bool _recoveryMatchesRequest(
    ScheduledPaymentRecovery recovery,
    RegistrationPaymentRequest request,
  ) {
    final payment = recovery.paymentCommand;
    final zero = recovery.zeroCommand;
    return switch (recovery.kind) {
      ScheduledPaymentRecoveryKind.payment =>
        payment != null &&
            zero == null &&
            payment.reservationId.toLowerCase() ==
                request.reservationId.toLowerCase(),
      ScheduledPaymentRecoveryKind.zeroLiability =>
        zero != null &&
            payment == null &&
            zero.reservationId.toLowerCase() ==
                request.reservationId.toLowerCase(),
    };
  }

  Future<void> _loadQuote() async {
    final requestId = _request.paymentRequestId;
    final generation = ++_generation;
    setState(() {
      _quoteLoading = true;
      _quoteError = null;
      _quoteUnavailable = false;
    });
    try {
      final quote = await widget.repository.outpatientQuote(requestId);
      if (!mounted || generation != _generation) return;
      if (quote.paymentRequestId.toLowerCase() != requestId.toLowerCase() ||
          quote.patientId.toLowerCase() != _request.patientId.toLowerCase() ||
          quote.careEpisodeId.toLowerCase() !=
              _request.careEpisodeId.toLowerCase() ||
          quote.currency != _request.currency) {
        throw const FormatException('Báo giá không khớp khoản phí khám.');
      }
      setState(() => _quote = quote);
    } catch (error) {
      if (!mounted || generation != _generation) return;
      if (error is ApiFailure && error.status == 404) {
        setState(() => _quoteUnavailable = true);
      } else {
        setState(() => _quoteError = error);
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _quoteLoading = false);
      }
    }
  }

  bool get _reservationReady =>
      const {'PENDING', 'PARTIALLY_PAID'}.contains(_request.status) &&
      _request.reservationRevision >= 1 &&
      _recoveryReady &&
      !_busy &&
      !_checking &&
      !_completed &&
      !_closed &&
      _recovery == null;

  bool get _baseReady =>
      _reservationReady && _request.remainingAmount.minorUnits > 0;

  bool get _zeroRequestEligible =>
      _request.status == 'PENDING' &&
      _request.requestedAmount.minorUnits == 0 &&
      _request.paidAmount.minorUnits == 0 &&
      _request.remainingAmount.minorUnits == 0;

  bool get _canPay =>
      _baseReady &&
      _request.currency.isNotEmpty &&
      !_quoteLoading &&
      _quoteError == null &&
      !(_quote != null && _quoteExpired) &&
      (_quote == null ||
          _quote!.patientPayable.minorUnits ==
              _request.remainingAmount.minorUnits) &&
      !(_quoteIsZero && widget.quoteEnabled);

  bool get _methodIsTransfer => _method == 'BANK_TRANSFER';

  Future<void> _submitPayment({ScheduledPaymentRecovery? retry}) async {
    if (retry != null &&
        (retry.kind != ScheduledPaymentRecoveryKind.payment ||
            retry.paymentCommand == null ||
            retry.paymentCommand!.reservationId.toLowerCase() !=
                _request.reservationId.toLowerCase())) {
      return;
    }
    if (retry == null && _form.currentState?.validate() != true) return;
    final request = _request;
    final recovery =
        retry ??
        ScheduledPaymentRecovery(
          kind: ScheduledPaymentRecoveryKind.payment,
          idempotencyKey: _newUuid(),
          correlationId: _newUuid(),
          paymentCommand: ScheduledPaymentCommand(
            reservationId: request.reservationId,
            expectedReservationRevision: request.reservationRevision,
            quoteId: _quote?.quoteId,
            amount: request.remainingAmount.format(),
            currency: request.currency,
            paymentMethod: _method,
            providerReference: _methodIsTransfer
                ? _providerReference.text.trim()
                : null,
          ),
          zeroCommand: null,
          operationId: null,
        );
    if (recovery.paymentCommand == null || _busy || _checking) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.recoveryStore.save(request.paymentRequestId, recovery);
      if (!mounted) return;
      setState(() => _recovery = recovery);
      final result = await widget.repository.payScheduledRequest(
        request.paymentRequestId,
        recovery.paymentCommand!,
        idempotencyKey: recovery.idempotencyKey,
        correlationId: recovery.correlationId,
      );
      await _acceptResult(result, recovery);
    } catch (error) {
      if (!mounted) return;
      final closedWithoutPayment = _closedWithoutPayment(error);
      if (_definitivelyRejected(error) || closedWithoutPayment) {
        try {
          await widget.recoveryStore.clear(request.paymentRequestId);
          if (mounted) {
            setState(() {
              _recovery = null;
              if (closedWithoutPayment) _closed = true;
            });
          }
        } catch (clearError) {
          if (mounted) setState(() => _error = clearError);
        }
      }
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmZeroLiability({ScheduledPaymentRecovery? retry}) async {
    final quote = _quote;
    if (retry == null) {
      if (quote == null ||
          !widget.zeroPayableEnabled ||
          !_quoteIsZero ||
          !_zeroRequestEligible ||
          _quoteLoading ||
          _quoteError != null ||
          _quoteExpired) {
        return;
      }
    } else if (retry.kind != ScheduledPaymentRecoveryKind.zeroLiability ||
        retry.zeroCommand == null ||
        retry.zeroCommand!.reservationId.toLowerCase() !=
            _request.reservationId.toLowerCase()) {
      return;
    }
    final request = _request;
    final recovery =
        retry ??
        ScheduledPaymentRecovery(
          kind: ScheduledPaymentRecoveryKind.zeroLiability,
          idempotencyKey: _newUuid(),
          correlationId: _newUuid(),
          paymentCommand: null,
          zeroCommand: ScheduledZeroLiabilityCommand(
            reservationId: request.reservationId,
            expectedReservationRevision: request.reservationRevision,
            quoteId: quote!.quoteId,
          ),
          operationId: null,
        );
    if (recovery.zeroCommand == null || _busy || _checking) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.recoveryStore.save(request.paymentRequestId, recovery);
      if (!mounted) return;
      setState(() => _recovery = recovery);
      final result = await widget.repository.confirmScheduledZeroLiability(
        request.paymentRequestId,
        recovery.zeroCommand!,
        idempotencyKey: recovery.idempotencyKey,
        correlationId: recovery.correlationId,
      );
      await _acceptResult(result, recovery);
    } catch (error) {
      if (!mounted) return;
      final closedWithoutPayment = _closedWithoutPayment(error);
      if (_definitivelyRejected(error) || closedWithoutPayment) {
        try {
          await widget.recoveryStore.clear(request.paymentRequestId);
          if (mounted) {
            setState(() {
              _recovery = null;
              if (closedWithoutPayment) _closed = true;
            });
          }
        } catch (clearError) {
          if (mounted) setState(() => _error = clearError);
        }
      }
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _acceptResult(
    ScheduledPaymentResult result,
    ScheduledPaymentRecovery recovery,
  ) async {
    if (!mounted) return;
    if (result.operation.paymentRequestId.toLowerCase() !=
            _request.paymentRequestId.toLowerCase() ||
        result.operation.registrationId.toLowerCase() !=
            _request.registrationId.toLowerCase() ||
        result.operation.reservationId.toLowerCase() !=
            _request.reservationId.toLowerCase()) {
      throw const FormatException('Kết quả không khớp với khoản phí khám.');
    }
    if (result.operation.status == 'PREPARING') {
      final saved = recovery.withOperation(result.operation.paymentOperationId);
      await widget.recoveryStore.save(_request.paymentRequestId, saved);
      if (mounted) {
        setState(() {
          _result = result;
          _recovery = saved;
        });
      }
      return;
    }
    setState(() {
      _result = result;
      _completed = result.operation.status == 'COMPLETED';
      _closed = result.operation.status == 'CLOSED_NOT_PAID';
    });
    if (_completed || _closed) {
      try {
        await widget.recoveryStore.clear(_request.paymentRequestId);
        if (mounted) setState(() => _recovery = null);
      } catch (error) {
        if (mounted) setState(() => _error = error);
      }
      if (_completed) await _refreshAfterCompletion();
    }
  }

  Future<void> _checkOperation() async {
    final recovery = _recovery;
    final operationId =
        recovery?.operationId ?? _result?.operation.paymentOperationId;
    if (recovery == null || operationId == null || _busy || _checking) return;
    setState(() {
      _checking = true;
      _error = null;
    });
    try {
      final result = await widget.repository.scheduledPaymentOperation(
        operationId,
      );
      if (result.operation.paymentRequestId.toLowerCase() !=
              _request.paymentRequestId.toLowerCase() ||
          result.operation.registrationId.toLowerCase() !=
              _request.registrationId.toLowerCase() ||
          result.operation.reservationId.toLowerCase() !=
              _request.reservationId.toLowerCase()) {
        throw const FormatException('Giao dịch không khớp khoản phí khám.');
      }
      await _acceptResult(result, recovery);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  Future<void> _retryFrozen() async {
    final recovery = _recovery;
    if (recovery == null || recovery.operationId != null) return;
    if (recovery.kind == ScheduledPaymentRecoveryKind.payment) {
      await _submitPayment(retry: recovery);
    } else {
      await _confirmZeroLiability(retry: recovery);
    }
  }

  Future<void> _refreshAfterCompletion() async {
    try {
      final updated = await widget.repository
          .scheduledPaymentRequestByRegistration(_request.registrationId);
      if (!mounted) return;
      if (updated.paymentRequestId.toLowerCase() !=
              _request.paymentRequestId.toLowerCase() ||
          updated.registrationId.toLowerCase() !=
              _request.registrationId.toLowerCase() ||
          updated.patientId.toLowerCase() != _request.patientId.toLowerCase() ||
          updated.reservationId.toLowerCase() !=
              _request.reservationId.toLowerCase() ||
          updated.currency != _request.currency ||
          updated.status != 'PAID' ||
          updated.remainingAmount.minorUnits != 0) {
        setState(
          () => _refreshError = const FormatException(
            'Billing chưa phản ánh trạng thái đã xử lý. Đã giữ biên nhận; không thu lại.',
          ),
        );
        return;
      }
      setState(() {
        _request = updated;
        _refreshError = null;
      });
    } catch (error) {
      if (mounted) setState(() => _refreshError = error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final request = _request;
    final canPay = _canPay;
    final canConfirmZero =
        _reservationReady &&
        widget.quoteEnabled &&
        widget.zeroPayableEnabled &&
        _zeroRequestEligible &&
        _quoteIsZero &&
        !_quoteLoading &&
        _quoteError == null &&
        !_quoteExpired;
    return Form(
      key: _form,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          LText('Phí khám', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: ReferenceLabel(
              kind: ReferenceKind.patient,
              id: request.patientId,
              label: 'Bệnh nhân',
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: ReferenceLabel(
              kind: ReferenceKind.department,
              id: request.departmentId,
              label: 'Khoa',
            ),
          ),
          _detail(
            'Số dư Billing',
            '${request.remainingAmount.format()} ${request.currency}',
          ),
          _detail(
            'Đã thu',
            '${request.paidAmount.format()} ${request.currency}',
          ),
          _detail('Hạn giữ chỗ', _localTime(request.expiresAt)),
          _detail('Trạng thái', _statusLabel(request.status)),
          if (widget.quoteEnabled) ...[
            const SizedBox(height: 12),
            if (_quoteLoading) const LinearProgressIndicator(),
            if (_quoteUnavailable)
              const LText(
                'Billing chưa có báo giá cho yêu cầu này. Có thể tiếp tục thu đúng số dư do Billing cung cấp.',
              ),
            if (_quoteError != null) ...[
              RequestError(_quoteError!),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: _quoteLoading ? null : _loadQuote,
                  child: const LText('Thử tải lại báo giá'),
                ),
              ),
            ],
            if (_quote != null)
              ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: const LText('Chi tiết giá và hỗ trợ mô phỏng'),
                subtitle: LText(
                  'Người bệnh phải trả: ${_quote!.patientPayable.format()} ${_quote!.currency}',
                ),
                children: [
                  for (final line in _quote!.lines)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: LText(line.description),
                      subtitle: LText(
                        '${line.grossAmount.format()} − hỗ trợ ${line.supportAmount.format()}',
                      ),
                      trailing: LText(line.patientPayable.format()),
                    ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const LText('Tổng cộng'),
                    subtitle: LText(
                      'Báo giá hết hạn: ${_localTime(_quote!.expiresAt)}',
                    ),
                    trailing: LText(_quote!.patientPayable.format()),
                  ),
                ],
              ),
            if (_quote != null && _quoteExpired)
              const LText(
                'Báo giá đã hết hạn. Hãy tải lại trước khi tiếp tục.',
              ),
            if (!_completed &&
                _quote != null &&
                _quote!.patientPayable.minorUnits !=
                    request.remainingAmount.minorUnits)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: LText(
                  'Số dư và báo giá Billing không khớp. Tra cứu lại khoản phí trước khi tiếp tục.',
                ),
              ),
          ],
          if (_result?.operation.status == 'COMPLETED') ...[
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: LText(
                  _result!.operation.basis == 'ZERO_LIABILITY'
                      ? 'Đã xác nhận nghĩa vụ 0 đồng. Mã xác nhận: ${_result!.clearanceId ?? '—'}'
                      : 'Đã thu ${_result!.payment!.amount} ${_result!.payment!.currency} · ${_result!.payment!.paymentMethod == 'CASH' ? 'Tiền mặt' : 'Chuyển khoản'}. Mã giao dịch: ${_result!.payment!.transactionId}',
                ),
              ),
            ),
            if (_refreshError != null) ...[
              RequestError(_refreshError!),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: _refreshAfterCompletion,
                  child: const LText('Thử kiểm tra lại'),
                ),
              ),
            ],
          ],
          if (_recovery?.operationId != null ||
              _result?.operation.status == 'PREPARING') ...[
            const SizedBox(height: 12),
            const LText(
              'Đang chờ Billing hoàn tất giao dịch. Giữ nguyên yêu cầu để tránh thu trùng.',
            ),
            FilledButton.icon(
              onPressed: _checking ? null : _checkOperation,
              icon: const Icon(Icons.refresh),
              label: LText(
                _checking ? 'Đang kiểm tra…' : 'Kiểm tra trạng thái giao dịch',
              ),
            ),
          ],
          if (_recovery != null && _recovery!.operationId == null) ...[
            const SizedBox(height: 12),
            const LText(
              'Kết quả yêu cầu trước chưa xác định. Gửi lại đúng lệnh đã khóa hoặc kiểm tra trạng thái trước khi xử lý khoản khác.',
            ),
            FilledButton(
              onPressed: _busy ? null : _retryFrozen,
              child: const LText('Gửi lại đúng yêu cầu đã khóa'),
            ),
          ],
          if (_recoveryLoading) ...[
            const SizedBox(height: 8),
            const LinearProgressIndicator(),
            const LText('Đang kiểm tra yêu cầu thu đã lưu.'),
          ],
          if (_recoveryLoadError != null) ...[
            RequestError(_recoveryLoadError!),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: _recoveryLoading ? null : _retryRecoveryRead,
                child: const LText('Thử kiểm tra yêu cầu đã lưu'),
              ),
            ),
          ],
          if (_closed)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: LText(
                'Billing đã xác nhận thao tác này được đóng mà chưa thu tiền. Hãy quay lại và tra cứu khoản thu trước khi thao tác tiếp.',
              ),
            ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            RequestError(_error!),
          ],
          if (_recovery == null &&
              !_completed &&
              !_closed &&
              request.remainingAmount.minorUnits > 0) ...[
            const SizedBox(height: 16),
            InputDecorator(
              decoration: InputDecoration(
                labelText: AppL10n.text(
                  context,
                  'Số tiền được Billing xác nhận',
                ),
                border: OutlineInputBorder(),
              ),
              child: LText(
                '${request.remainingAmount.format()} ${request.currency}',
              ),
            ),
            if (_quoteIsZero &&
                widget.quoteEnabled &&
                !widget.zeroPayableEnabled)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: LText(
                  'Báo giá là 0 đồng nhưng chức năng xác nhận nghĩa vụ 0 chưa được mở. Không thu tiền mặt.',
                ),
              ),
            if (!_quoteIsZero || !widget.quoteEnabled) ...[
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: _method,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Hình thức thu'),
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'CASH', child: LText('Tiền mặt')),
                  DropdownMenuItem(
                    value: 'BANK_TRANSFER',
                    child: LText(
                      'Chuyển khoản, đối chiếu thủ công',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
                onChanged: _busy
                    ? null
                    : (value) => setState(() => _method = value ?? 'CASH'),
              ),
              if (_methodIsTransfer) ...[
                const SizedBox(height: 12),
                TextFormField(
                  controller: _providerReference,
                  maxLength: 120,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(
                      context,
                      'Mã đối chiếu chuyển khoản *',
                    ),
                    border: OutlineInputBorder(),
                  ),
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'Nhập mã đối chiếu chuyển khoản.'
                      : value.trim().length > 120
                      ? 'Mã đối chiếu tối đa 120 ký tự.'
                      : null,
                  onChanged: (_) => setState(() {}),
                ),
              ],
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: canPay ? () => _submitPayment() : null,
                icon: _busy
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.payments_outlined),
                label: LText(_busy ? 'Đang thu…' : 'Thu phí khám'),
              ),
            ],
          ],
          // A zero-liability request has no remaining balance, so this cannot
          // live inside the amount block above.
          if (canConfirmZero) ...[
            const SizedBox(height: 12),
            const LText(
              'Báo giá xác nhận người bệnh không phải trả tiền. Xác nhận này không ghi nhận tiền mặt.',
            ),
            FilledButton(
              onPressed: _busy ? null : () => _confirmZeroLiability(),
              child: const LText('Xác nhận nghĩa vụ 0 đồng'),
            ),
          ],
          if (!_completed &&
              request.remainingAmount.minorUnits == 0 &&
              request.status != 'PAID' &&
              !_quoteIsZero)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: LText('Khoản này chưa có số dư dương để thu.'),
            ),
          if (request.status == 'PAID')
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: LText('Khoản phí đã được thanh toán.'),
            ),
          if (request.status == 'EXPIRED' || request.status == 'CANCELLED')
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: LText(
                'Khoản giữ chỗ đã hết hạn hoặc bị hủy; không thể thu.',
              ),
            ),
          const SizedBox(height: 16),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            title: const LText('Thông tin đối soát khoản thu'),
            children: [
              _detail('Mã đăng ký', request.registrationId),
              _detail('Mã yêu cầu', request.paymentRequestId),
              _detail('Mã hóa đơn', request.invoiceId),
              _detail('Mã tài khoản', request.accountId),
              _detail('Mã giữ chỗ', request.reservationId),
              _detail('Mã báo giá', _quote?.quoteId ?? '—'),
              _detail(
                'Mã thao tác',
                _result?.operation.paymentOperationId ??
                    _recovery?.operationId ??
                    '—',
              ),
              _detail(
                'Mã giao dịch',
                _result?.payment?.transactionId ??
                    _result?.operation.transactionId ??
                    '—',
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _detail(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 112,
          child: LText(label, maxLines: 2, overflow: TextOverflow.ellipsis),
        ),
        const SizedBox(width: 8),
        Expanded(child: SelectableText(value)),
      ],
    ),
  );
}
