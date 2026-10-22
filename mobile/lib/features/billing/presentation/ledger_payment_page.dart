import 'dart:math';

import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_commands.dart';
import '../domain/billing_models.dart';

String _uuidKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

int? _minorUnits(String raw) {
  final value = raw.trim();
  if (!RegExp(r'^(0|[1-9]\d*)(?:\.\d{1,2})?$').hasMatch(value)) return null;
  final parts = value.split('.');
  final whole = int.tryParse(parts.first);
  if (whole == null || whole > 90071992547409) return null;
  final cents = parts.length == 1 ? 0 : int.parse(parts[1].padRight(2, '0'));
  final result = whole * 100 + cents;
  return result <= 9007199254740991 ? result : null;
}

class LedgerPaymentPage extends StatefulWidget {
  const LedgerPaymentPage({super.key, required this.repository});
  final BillingRepository repository;
  @override
  State<LedgerPaymentPage> createState() => _LedgerPaymentPageState();
}

class _LedgerPaymentPageState extends State<LedgerPaymentPage>
    with AutomaticKeepAliveClientMixin<LedgerPaymentPage> {
  final _sourceId = TextEditingController();
  final _patientId = TextEditingController();
  final _episodeId = TextEditingController();
  final _amount = TextEditingController();
  final _reference = TextEditingController();
  String _purpose = 'EXAM';
  String _method = 'CASH';
  PaymentDiscovery? _discovery;
  LedgerPayment? _receipt;
  CompletePaymentCommand? _frozen;
  Object? _error, _commandError;
  bool _loading = false,
      _busy = false,
      _confirmed = false,
      _locked = false,
      _reconciling = false;
  int _generation = 0, _paymentRevision = 0;

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _generation++;
    _paymentRevision++;
    _sourceId.dispose();
    _patientId.dispose();
    _episodeId.dispose();
    _amount.dispose();
    _reference.dispose();
    super.dispose();
  }

  Future<void> _discover() async {
    final source = _sourceId.text.trim();
    final patient = _patientId.text.trim();
    final episode = _episodeId.text.trim();
    if (_locked || _busy || _loading || _reconciling) return;
    if (![source, patient, episode].every(isBillingUuid)) {
      setState(
        () => _error = const FormatException(
          'Nhập UUID hợp lệ cho khoản thu, bệnh nhân và đợt khám.',
        ),
      );
      return;
    }
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _discovery = null;
      _receipt = null;
      _commandError = null;
    });
    try {
      final result = await widget.repository.discoverPaymentRequest(
        purpose: _purpose,
        sourceId: source,
        patientId: patient,
        careEpisodeId: episode,
      );
      if (mounted && generation == _generation) {
        _amount.text = result.remainingAmount.format();
        setState(() => _discovery = result);
      }
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _submit({CompletePaymentCommand? retry}) async {
    final discovery = _discovery;
    if (discovery == null ||
        _busy ||
        _reconciling ||
        (retry == null && _locked) ||
        !_confirmed) {
      return;
    }
    final command =
        retry ??
        (() {
          final amount = _minorUnits(_amount.text);
          if (amount == null ||
              amount <= 0 ||
              amount > discovery.remainingAmount.minorUnits ||
              (_reference.text.trim().length > 120)) {
            setState(
              () => _commandError = const FormatException(
                'Số tiền phải dương, tối đa 2 chữ số thập phân và không vượt số dư. Mã đối chiếu tối đa 120 ký tự.',
              ),
            );
            return null;
          }
          return CompletePaymentCommand(
            idempotencyKey: _uuidKey(),
            amountMinorUnits: amount,
            currency: discovery.currency,
            paymentMethod: _method,
            providerReference: _reference.text.trim().isEmpty
                ? null
                : _reference.text.trim(),
          );
        })();
    if (command == null) return;
    final paymentRevision = ++_paymentRevision;
    setState(() {
      _busy = true;
      _commandError = null;
      _reconciling = false;
      if (retry == null) _receipt = null;
      _frozen = retry ?? command;
    });
    try {
      final result = await widget.repository.completePayment(
        discovery.paymentRequestId,
        command,
      );
      if (!mounted) return;
      setState(() {
        _receipt = result;
        _locked = true;
        if (result.status == 'COMPLETED') _frozen = null;
      });
      if (result.status == 'COMPLETED') {
        _confirmed = false;
        _showMessage('Billing đã ghi nhận giao dịch.');
        _refreshAfterPayment(revision: paymentRevision);
      } else {
        _commandError = const ApiFailure(
          'Giao dịch chưa ở trạng thái COMPLETED. Đã khóa thao tác thu tiếp; cần đối soát trước.',
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
        _commandError = error;
        _locked = uncertain;
        if (!uncertain) _frozen = null;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _refreshAfterPayment({int? revision}) async {
    if (_reconciling) return;
    final paymentRevision = revision ?? _paymentRevision;
    if (paymentRevision != _paymentRevision ||
        _receipt?.status != 'COMPLETED') {
      return;
    }
    final old = _discovery;
    if (old == null) return;
    setState(() => _reconciling = true);
    try {
      final fresh = await widget.repository.discoverPaymentRequest(
        purpose: old.purpose,
        sourceId: old.sourceId,
        patientId: old.patientId,
        careEpisodeId: old.careEpisodeId,
      );
      if (!mounted ||
          paymentRevision != _paymentRevision ||
          _receipt?.status != 'COMPLETED') {
        return;
      }
      setState(() {
        _discovery = fresh;
        _amount.text = fresh.remainingAmount.format();
        _locked = false;
        _frozen = null;
      });
    } catch (error) {
      if (mounted && paymentRevision == _paymentRevision) {
        setState(() => _commandError = error);
      }
    } finally {
      if (mounted && paymentRevision == _paymentRevision) {
        setState(() => _reconciling = false);
      }
    }
  }

  void _showMessage(String value) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
  }

  void _invalidateDiscovery(String _) {
    if (_busy || _locked || _discovery == null) return;
    setState(() {
      _discovery = null;
      _receipt = null;
      _frozen = null;
      _commandError = null;
      _confirmed = false;
    });
  }

  void _changePurpose(String? value) {
    if (value == null || value == _purpose) return;
    setState(() {
      _purpose = value;
      _discovery = null;
      _receipt = null;
      _frozen = null;
      _commandError = null;
      _confirmed = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Tra cứu khoản thu',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          initialValue: _purpose,
          decoration: const InputDecoration(labelText: 'Loại khoản thu'),
          items: const [
            DropdownMenuItem(value: 'EXAM', child: Text('Khám')),
            DropdownMenuItem(value: 'LAB_TEST', child: Text('Xét nghiệm')),
            DropdownMenuItem(value: 'PRESCRIPTION', child: Text('Đơn thuốc')),
          ],
          onChanged: _locked || _loading || _busy ? null : _changePurpose,
        ),
        const SizedBox(height: 8),
        ExpansionTile(
          title: const Text('Tham chiếu khoản thu từ tiếp nhận'),
          children: [
            _uuidField(_sourceId, 'Mã nguồn (sourceId)'),
            _uuidField(_patientId, 'Mã bệnh nhân (patientId)'),
            _uuidField(_episodeId, 'Mã đợt khám (careEpisodeId)'),
          ],
        ),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _loading || _busy || _locked || _reconciling
              ? null
              : _discover,
          icon: const Icon(Icons.search),
          label: Text(_loading ? 'Đang tra cứu…' : 'Tra cứu từ Billing'),
        ),
        if (_loading)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          ),
        if (_error != null)
          RequestError(
            _error!,
            onRetry:
                _error is ApiFailure &&
                    ((_error as ApiFailure).status == null ||
                        (_error as ApiFailure).status! >= 500 ||
                        (_error as ApiFailure).status == 429)
                ? _discover
                : null,
          ),
        if (_discovery case final item?) ...[
          const Divider(height: 32),
          Text(
            'Khoản thu do Billing cung cấp',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          ExpansionTile(
            title: const Text('Chi tiết tham chiếu'),
            children: [SelectableText('Mã yêu cầu: ${item.paymentRequestId}')],
          ),
          Text('Trạng thái: ${item.status} · Tài khoản: ${item.accountStatus}'),
          Text('Đề nghị: ${item.requestedAmount.format()} ${item.currency}'),
          Text('Đã thu: ${item.paidAmount.format()} ${item.currency}'),
          Text('Còn lại: ${item.remainingAmount.format()} ${item.currency}'),
          Text('Quan sát lúc: ${item.observedAt}'),
          if (item.status == 'PENDING' || item.status == 'PARTIALLY_PAID') ...[
            const SizedBox(height: 16),
            TextField(
              controller: _amount,
              enabled: !_busy && !_locked,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              decoration: InputDecoration(
                labelText: 'Số tiền thực nhận (${item.currency})',
              ),
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              initialValue: _method,
              decoration: const InputDecoration(
                labelText: 'Hình thức nhận tiền',
              ),
              items: const [
                DropdownMenuItem(value: 'CASH', child: Text('Tiền mặt')),
                DropdownMenuItem(
                  value: 'TRANSFER',
                  child: Text('Chuyển khoản đã đối soát'),
                ),
              ],
              onChanged: _busy || _locked
                  ? null
                  : (value) => setState(() => _method = value!),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _reference,
              enabled: !_busy && !_locked,
              maxLength: 120,
              decoration: const InputDecoration(
                labelText: 'Mã đối chiếu (không bắt buộc)',
              ),
            ),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: _confirmed,
              onChanged: _busy || _locked
                  ? null
                  : (value) => setState(() => _confirmed = value ?? false),
              title: const Text('Tôi đã thực nhận/đối soát khoản tiền này.'),
            ),
            if (_receipt != null) _receiptCard(_receipt!),
            if (_commandError != null) RequestError(_commandError!),
            if (_locked)
              const Text(
                'Thao tác đang chờ đối soát. Không gửi khoản thu khác cho đến khi kết quả được kiểm tra.',
              ),
            if (_locked && _frozen != null && _receipt == null)
              OutlinedButton(
                onPressed: _busy || _reconciling
                    ? null
                    : () => _submit(retry: _frozen),
                child: const Text('Gửi lại đúng lệnh đã khóa'),
              ),
            if (_locked && _receipt?.status == 'COMPLETED')
              OutlinedButton.icon(
                onPressed: _busy || _reconciling ? null : _refreshAfterPayment,
                icon: const Icon(Icons.refresh),
                label: Text(
                  _reconciling
                      ? 'Đang đối soát…'
                      : 'Đối soát số dư mới từ Billing',
                ),
              ),
            if (!_locked)
              FilledButton(
                onPressed: _busy || !_confirmed ? null : () => _submit(),
                child: Text(_busy ? 'Đang ghi nhận…' : 'Ghi nhận khoản thu'),
              ),
          ],
          if (item.status == 'PAID') const Text('Khoản thu đã thanh toán.'),
          if (item.status == 'EXPIRED' || item.status == 'CANCELLED')
            const Text('Khoản thu này không còn nhận thanh toán.'),
        ],
      ],
    );
  }

  Widget _uuidField(TextEditingController controller, String label) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: TextField(
      controller: controller,
      enabled: !_locked && !_loading && !_busy,
      onChanged: _invalidateDiscovery,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
    ),
  );

  Widget _receiptCard(LedgerPayment result) => Card(
    child: ListTile(
      title: Text('Trạng thái giao dịch: ${result.status}'),
      subtitle: SelectableText('Mã giao dịch: ${result.transactionId}'),
      trailing: Text('${result.amount.format()} ${result.currency}'),
    ),
  );
}
