import 'package:flutter/material.dart';

import '../../../core/widgets/paged_list.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/billing_models.dart';
import '../domain/lab_quote_payment.dart';
import '../domain/payment_queue.dart';
import 'ledger_payment_page.dart';

class PaymentQueuePage extends StatefulWidget {
  const PaymentQueuePage({
    super.key,
    required this.repository,
    required this.queue,
    this.labQuotes,
    this.labRecovery,
    this.labZeroEnabled = false,
  });
  final BillingRepository repository;
  final PaymentQueueRepository queue;
  final LabQuotePaymentRepository? labQuotes;
  final LabPaymentRecoveryStore? labRecovery;
  final bool labZeroEnabled;
  @override
  State<PaymentQueuePage> createState() => _PaymentQueuePageState();
}

class _PaymentQueuePageState extends State<PaymentQueuePage> {
  String? _patientId, _purpose;
  int _revision = 0;

  Future<void> _open(PaymentDiscovery? request) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Thu tiền')),
          body: LedgerPaymentPage(
            repository: widget.repository,
            labQuotes: widget.labQuotes,
            labRecovery: widget.labRecovery,
            labZeroEnabled: widget.labZeroEnabled,
            source: request == null
                ? null
                : PaymentRequestSource(
                    purpose: request.purpose,
                    sourceId: request.sourceId,
                    patientId: request.patientId,
                    careEpisodeId: request.careEpisodeId,
                    paymentRequestId: request.paymentRequestId,
                  ),
          ),
        ),
      ),
    );
    if (mounted) setState(() => _revision++);
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            ReferencePicker(
              kind: ReferenceKind.patient,
              label: 'Tìm bệnh nhân chờ thu',
              value: _patientId,
              onChanged: (id) => setState(() => _patientId = id),
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              isExpanded: true,
              initialValue: _purpose ?? '',
              decoration: const InputDecoration(labelText: 'Loại khoản thu'),
              items: const [
                DropdownMenuItem(value: '', child: Text('Xét nghiệm và thuốc')),
                DropdownMenuItem(value: 'LAB_TEST', child: Text('Xét nghiệm')),
                DropdownMenuItem(
                  value: 'PRESCRIPTION',
                  child: Text('Đơn thuốc'),
                ),
              ],
              onChanged: (value) =>
                  setState(() => _purpose = value == '' ? null : value),
            ),
            Wrap(
              spacing: 12,
              children: [
                TextButton.icon(
                  onPressed: () => setState(() => _revision++),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Cập nhật hàng chờ'),
                ),
                TextButton(
                  onPressed: () => _open(null),
                  child: const Text('Tra cứu bằng mã bàn giao'),
                ),
              ],
            ),
          ],
        ),
      ),
      Expanded(
        child: PagedList<PaymentQueueItem>(
          key: ValueKey((_patientId, _purpose, _revision)),
          load: (page) => widget.queue.paymentQueue(
            patientId: _patientId,
            purpose: _purpose,
            page: page,
          ),
          itemBuilder: (context, item) {
            final request = item.request;
            return Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ReferenceLabel(
                      kind: ReferenceKind.patient,
                      id: request.patientId,
                      label: 'Bệnh nhân',
                    ),
                    Text(
                      request.purpose == 'LAB_TEST'
                          ? 'Xét nghiệm'
                          : 'Đơn thuốc',
                    ),
                    ReferenceLabel(
                      kind: ReferenceKind.department,
                      id: request.departmentId,
                      label: 'Khoa',
                    ),
                    Text(
                      'Còn phải thu: ${request.remainingAmount.format()} ${request.currency}',
                    ),
                    if (request.expiresAt != null)
                      Text('Hạn xử lý: ${request.expiresAt}'),
                    FilledButton(
                      onPressed: () => _open(request),
                      child: const Text('Đối chiếu và thu tiền'),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    ],
  );
}
