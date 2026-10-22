import '../../../core/widgets/reference_widgets.dart';

import 'package:flutter/material.dart';

import '../../../core/domain/page_result.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/billing_models.dart';

bool _retryableRead(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status! >= 500 ||
        error.status == 429);

class InvoicePage extends StatefulWidget {
  const InvoicePage({super.key, required this.repository});
  final BillingRepository repository;
  @override
  State<InvoicePage> createState() => _InvoicePageState();
}

class _InvoicePageState extends State<InvoicePage>
    with AutomaticKeepAliveClientMixin<InvoicePage> {
  final _patient = TextEditingController();
  final _createdDate = TextEditingController(
    text: DateTime.now().toIso8601String().substring(0, 10),
  );
  PageResult<Invoice>? _page;
  Object? _error, _commandError;
  bool _loading = false, _creating = false;
  int _generation = 0, _requestedPage = 0;
  String? _loadedPatient;
  final Set<String> _uncertainCreatePatients = {};
  final Set<String> _reviewedCreatePatients = {};
  final Set<String> _paymentUnknown = {};
  final Map<String, String> _paymentMethods = {};

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _generation++;
    _patient.dispose();
    _createdDate.dispose();
    super.dispose();
  }

  Future<void> _load(int page) async {
    if (_creating) return;
    final patientId = _patient.text.trim();
    if (!isBillingUuid(patientId)) {
      setState(
        () => _error = const FormatException('Nhập mã bệnh nhân UUID hợp lệ.'),
      );
      return;
    }
    if (_loadedPatient != patientId) {
      _page = null;
      _loadedPatient = patientId;
      _requestedPage = 0;
    }
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _requestedPage = page;
    });
    try {
      final result = await widget.repository.invoicesForPatient(
        patientId,
        page: page,
      );
      if (mounted && generation == _generation) {
        setState(() {
          _page = result;
          if (_uncertainCreatePatients.contains(patientId)) {
            _commandError = const ApiFailure(
              'Đã tải lại lịch sử. Kiểm tra hóa đơn theo ngày vừa lập trước khi xác nhận mở lại thao tác lập.',
            );
          }
        });
      }
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _create() async {
    final patientId = _patient.text.trim();
    final date = _createdDate.text.trim();
    if (!isBillingUuid(patientId) || !isValidDate(date)) {
      setState(
        () => _commandError = const FormatException(
          'Nhập mã bệnh nhân và ngày hợp lệ.',
        ),
      );
      return;
    }
    if (_creating || _createBlocked) return;
    setState(() {
      _creating = true;
      _commandError = null;
    });
    try {
      final invoice = await widget.repository.createInvoice(patientId, date);
      if (!mounted) return;
      if (_patient.text.trim() != patientId) {
        setState(() {
          _commandError = const ApiFailure(
            'Hóa đơn đã được tạo cho mã bệnh nhân đã gửi. Tải lại hồ sơ đó để xem kết quả.',
          );
        });
        return;
      }
      setState(() {
        _page = _page == null
            ? null
            : PageResult(
                items: [invoice, ..._page!.items],
                number: _page!.number,
                totalPages: _page!.totalPages,
                totalElements: _page!.totalElements + 1,
              );
      });
      _showMessage('Đã lập hóa đơn. Số tiền do Billing tính.');
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _commandError = error;
        if (_isUncertain(error)) {
          _uncertainCreatePatients.add(patientId);
          _reviewedCreatePatients.remove(patientId);
        }
      });
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _pay(Invoice invoice) async {
    if (_creating || _paymentUnknown.contains(invoice.id) || invoice.isPaid) {
      return;
    }
    final paymentMethod = _paymentMethods[invoice.id] ?? 'CASH';
    setState(() {
      _creating = true;
      _commandError = null;
    });
    try {
      final result = await widget.repository.payInvoice(
        invoice.id,
        paymentMethod,
      );
      if (!mounted) return;
      _showMessage(
        result.success
            ? 'Đã ghi nhận thanh toán hóa đơn.'
            : 'Billing chưa xác nhận thanh toán hóa đơn.',
      );
      if (result.success) await _refreshInvoice(invoice.id);
    } catch (error) {
      if (!mounted) return;
      if (_isUncertain(error)) _paymentUnknown.add(invoice.id);
      setState(() => _commandError = error);
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  bool _isUncertain(Object error) =>
      error is! ApiFailure ||
      error.status == null ||
      error.status == 408 ||
      error.status! >= 500;

  bool get _createBlocked =>
      _uncertainCreatePatients.contains(_patient.text.trim());

  void _unlockCreateAfterReview() {
    final patientId = _patient.text.trim();
    if (!_reviewedCreatePatients.contains(patientId) ||
        _loadedPatient != patientId) {
      return;
    }
    setState(() {
      _uncertainCreatePatients.remove(patientId);
      _reviewedCreatePatients.remove(patientId);
      _commandError = null;
    });
  }

  Future<void> _refreshInvoice(String invoiceId) async {
    try {
      final fresh = await widget.repository.invoice(invoiceId);
      if (!mounted) return;
      setState(() {
        if (fresh.isPaid) _paymentUnknown.remove(invoiceId);
        final page = _page;
        if (page != null) {
          final rows = [
            for (final item in page.items)
              if (item.id == invoiceId) fresh else item,
          ];
          _page = PageResult(
            items: rows,
            number: page.number,
            totalPages: page.totalPages,
            totalElements: page.totalElements,
          );
        }
      });
    } catch (error) {
      if (mounted) setState(() => _commandError = error);
    }
  }

  void _showMessage(String value) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
  }

  void _patientChanged(String value) {
    if (_loadedPatient == value.trim() &&
        (_page == null || _loadedPatient == value.trim())) {
      return;
    }
    _generation++;
    setState(() {
      _page = null;
      _error = null;
      _loading = false;
      _loadedPatient = null;
      _requestedPage = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Tra cứu hóa đơn', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 12),
        ExpansionTile(
          title: const Text('Mã tham chiếu do tiếp nhận cung cấp'),
          children: [
            TextField(
              controller: _patient,
              enabled: !_creating,
              decoration: const InputDecoration(
                labelText: 'Mã bệnh nhân (UUID)',
                border: OutlineInputBorder(),
              ),
              textInputAction: TextInputAction.search,
              onChanged: _patientChanged,
              onSubmitted: (_) => _load(0),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          children: [
            FilledButton.icon(
              onPressed: _loading || _creating ? null : () => _load(0),
              icon: const Icon(Icons.search),
              label: const Text('Tải lịch sử'),
            ),
            OutlinedButton.icon(
              onPressed: _loading || _creating || _page == null
                  ? null
                  : () => _load(_requestedPage),
              icon: const Icon(Icons.refresh),
              label: const Text('Làm mới'),
            ),
          ],
        ),
        const SizedBox(height: 24),
        Text('Lập hóa đơn', style: Theme.of(context).textTheme.titleMedium),
        const Text('Billing tự tính tổng từ các khoản phí chưa thanh toán.'),
        const SizedBox(height: 8),
        TextField(
          controller: _createdDate,
          decoration: const InputDecoration(
            labelText: 'Ngày lập (YYYY-MM-DD)',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: _creating || _createBlocked ? null : _create,
          child: Text(_creating ? 'Đang xử lý…' : 'Lập hóa đơn'),
        ),
        if (_createBlocked)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Kết quả lập hóa đơn chưa xác định. Đã khóa lệnh cho bệnh nhân này để tránh tạo trùng. Tải lịch sử và đối chiếu trước khi xử lý tiếp.',
                ),
                if (_loadedPatient == _patient.text.trim()) ...[
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _reviewedCreatePatients.contains(
                      _patient.text.trim(),
                    ),
                    onChanged: (value) => setState(() {
                      if (value ?? false) {
                        _reviewedCreatePatients.add(_patient.text.trim());
                      } else {
                        _reviewedCreatePatients.remove(_patient.text.trim());
                      }
                    }),
                    title: const Text(
                      'Tôi đã kiểm tra lịch sử và xác nhận có thể lập hóa đơn mới.',
                    ),
                  ),
                  OutlinedButton(
                    onPressed:
                        _reviewedCreatePatients.contains(_patient.text.trim())
                        ? _unlockCreateAfterReview
                        : null,
                    child: const Text('Mở khóa sau đối soát'),
                  ),
                ],
              ],
            ),
          ),
        if (_commandError != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: RequestError(_commandError!),
          ),
        const SizedBox(height: 20),
        if (_loading) const Center(child: CircularProgressIndicator()),
        if (_error != null)
          RequestError(
            _error!,
            onRetry: _retryableRead(_error)
                ? () => _load(_requestedPage)
                : null,
          ),
        if (_page != null && !_loading) ...[
          Text('Hóa đơn: ${_page!.totalElements}'),
          if (_page!.items.isEmpty)
            const Text('Chưa có hóa đơn cho mã bệnh nhân này.'),
          for (final invoice in _page!.items) _invoiceCard(invoice),
          Wrap(
            alignment: WrapAlignment.center,
            children: [
              IconButton(
                tooltip: 'Trang trước',
                onPressed: _page!.number > 0 && !_loading
                    ? () => _load(_page!.number - 1)
                    : null,
                icon: const Icon(Icons.chevron_left),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(
                  'Trang ${_page!.number + 1}/${_page!.totalPages == 0 ? 1 : _page!.totalPages}',
                ),
              ),
              IconButton(
                tooltip: 'Trang sau',
                onPressed: _page!.hasNext && !_loading
                    ? () => _load(_page!.number + 1)
                    : null,
                icon: const Icon(Icons.chevron_right),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _invoiceCard(Invoice invoice) => Card(
    child: ExpansionTile(
      key: ValueKey('invoice-${invoice.id}'),
      title: Text('Hóa đơn · ${invoice.createdDate}'),
      subtitle: Text(
        '${invoice.totalAmount.format()} · ${invoice.isPaid ? 'Đã thanh toán' : 'Chưa thanh toán'}',
      ),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      children: [
        SelectableText('Mã hóa đơn: ${invoice.id}'),
        Text('Trạng thái saga: ${invoice.sagaStatus}'),
        for (final fee in invoice.fees)
          ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            title: Text('${fee.type} · ${fee.incurredDate}'),
            subtitle: Column(crossAxisAlignment:CrossAxisAlignment.start,children:[ReferenceLabel(kind:ReferenceKind.department,id:fee.departmentId,label:'Khoa'),Text(fee.isPaid ? 'Đã thu' : 'Chưa thu')]),
            trailing: Text(fee.amount.format()),
          ),
        if (!invoice.isPaid) ...[
          DropdownButtonFormField<String>(
            key: ValueKey('payment-method-${invoice.id}'),
            initialValue: _paymentMethods[invoice.id] ?? 'CASH',
            decoration: const InputDecoration(
              labelText: 'Hình thức thanh toán',
            ),
            items: const [
              DropdownMenuItem(value: 'CASH', child: Text('Tiền mặt')),
              DropdownMenuItem(value: 'TRANSFER', child: Text('Chuyển khoản')),
              DropdownMenuItem(value: 'INSURANCE', child: Text('Bảo hiểm')),
            ],
            onChanged: _creating
                ? null
                : (value) => setState(() {
                    if (value != null) _paymentMethods[invoice.id] = value;
                  }),
          ),
          const SizedBox(height: 8),
          if (_paymentUnknown.contains(invoice.id))
            OutlinedButton.icon(
              onPressed: _creating ? null : () => _refreshInvoice(invoice.id),
              icon: const Icon(Icons.refresh),
              label: const Text('Tra cứu lại hóa đơn trước khi thanh toán lại'),
            )
          else
            FilledButton(
              onPressed: _creating ? null : () => _pay(invoice),
              child: const Text('Ghi nhận thanh toán'),
            ),
        ],
      ],
    ),
  );
}
