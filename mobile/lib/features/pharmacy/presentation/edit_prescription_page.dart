import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/l10n/app_l10n.dart';
import '../../../core/network/api_failure.dart';
import '../domain/drug.dart';
import '../domain/prescription.dart';
import '../domain/prescription_commands.dart';
import '../domain/pharmacy_repository.dart';
import 'pharmacy_request_error.dart';
import 'providers.dart';

class EditPrescriptionPage extends ConsumerWidget {
  const EditPrescriptionPage({
    super.key,
    required this.prescription,
    required this.repository,
    required this.sessionIdentity,
  });
  final Prescription prescription;
  final PharmacyRepository repository;
  final Object sessionIdentity;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(pharmacyAccessProvider);
    final mode = ref.watch(pharmacyModeProvider);
    final currentRepository = ref.watch(pharmacyRepositoryProvider);
    final allowed =
        access?.sessionIdentity == sessionIdentity &&
        access?.canPrescribe == true &&
        identical(currentRepository, repository) &&
        canEditPrescription(prescription, mode) &&
        repository is PrescriptionEditor;
    return Scaffold(
      appBar: AppBar(title: const LText('Sửa đơn thuốc')),
      body: allowed
          ? _Editor(
              prescription: prescription,
              repository: repository,
              isCurrent: () {
                final current = ref.read(pharmacyAccessProvider);
                return current?.sessionIdentity == sessionIdentity &&
                    current?.canPrescribe == true &&
                    identical(
                      ref.read(pharmacyRepositoryProvider),
                      repository,
                    ) &&
                    ref.read(pharmacyModeProvider) == PharmacyMode.outpatient;
              },
            )
          : const Center(
              child: LText('Bạn không có quyền thực hiện thao tác Dược này.'),
            ),
    );
  }
}

class _Line {
  _Line({
    required this.id,
    required this.name,
    String quantity = '1',
    String dosage = '',
    String note = '',
  }) : quantity = TextEditingController(text: quantity),
       dosage = TextEditingController(text: dosage),
       note = TextEditingController(text: note);
  final String id, name;
  final TextEditingController quantity, dosage, note;
  void dispose() {
    quantity.dispose();
    dosage.dispose();
    note.dispose();
  }
}

class _Editor extends StatefulWidget {
  const _Editor({
    required this.prescription,
    required this.repository,
    required this.isCurrent,
  });
  final Prescription prescription;
  final PharmacyRepository repository;
  final bool Function() isCurrent;
  @override
  State<_Editor> createState() => _EditorState();
}

class _EditorState extends State<_Editor> {
  final keyword = TextEditingController();
  final scroll = ScrollController();
  final form = GlobalKey<FormState>();
  final lines = <_Line>[];
  final removedLines = <_Line>[];
  List<Drug> drugs = [];
  int page = 0, totalPages = 0, generation = 0;
  String query = '';
  bool searching = false,
      busy = false,
      uncertain = false,
      rejected = false,
      completed = false;
  Object? searchError, error;
  PrescriptionEditIntent? intent;
  bool get locked => busy || uncertain || rejected || completed;

  @override
  void initState() {
    super.initState();
    for (final line in widget.prescription.lines) {
      lines.add(
        _Line(
          id: line.drugId,
          name: line.drugName ?? 'Thuốc chưa có tên hiển thị',
          quantity: '${line.quantity}',
          dosage: line.dosage ?? '',
          note: line.note ?? '',
        ),
      );
    }
    _search();
  }

  @override
  void dispose() {
    generation++;
    keyword.dispose();
    scroll.dispose();
    for (final line in lines) {
      line.dispose();
    }
    for (final line in removedLines) {
      line.dispose();
    }
    super.dispose();
  }

  Future<void> _search({int target = 0, bool newQuery = false}) async {
    if (!widget.isCurrent() || locked) return;
    if (newQuery) query = keyword.text.trim();
    final request = ++generation;
    setState(() {
      searching = true;
      searchError = null;
    });
    try {
      final result = await widget.repository.searchDrugs(
        keyword: query,
        page: target,
      );
      if (!mounted || request != generation || !widget.isCurrent()) return;
      setState(() {
        drugs = result.items;
        page = target;
        totalPages = result.totalPages;
      });
    } catch (failure) {
      if (mounted && request == generation && widget.isCurrent()) {
        setState(() => searchError = failure);
      }
    } finally {
      if (mounted && request == generation && widget.isCurrent()) {
        setState(() => searching = false);
      }
    }
  }

  Future<void> _save() async {
    if (busy || rejected || completed || !widget.isCurrent()) return;
    if (intent == null) {
      if (!form.currentState!.validate()) return;
      try {
        intent = PrescriptionEditIntent(
          UpdatePrescription(
            expectedRevision: widget.prescription.revision!,
            lines: lines
                .map(
                  (line) => RequestedPrescriptionLine(
                    drugId: line.id,
                    quantity: int.tryParse(line.quantity.text.trim()) ?? 0,
                    dosage: line.dosage.text.trim(),
                    note: line.note.text.trim().isEmpty
                        ? null
                        : line.note.text.trim(),
                  ),
                )
                .toList(),
          ),
        );
      } catch (failure) {
        setState(
          () => error =
              'Vui lòng kiểm tra thuốc, số lượng và cách dùng ở dòng này.',
        );
        return;
      }
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final result = await (widget.repository as PrescriptionEditor)
          .updatePrescription(widget.prescription.id, intent!);
      if (!mounted || !widget.isCurrent()) return;
      if (result.id != widget.prescription.id ||
          result.revision == null ||
          result.revision! <= widget.prescription.revision!) {
        throw const FormatException('Unknown edit result');
      }
      // Release the pop guard after receiving the authoritative receipt.
      setState(() {
        busy = false;
        uncertain = false;
        completed = true;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && widget.isCurrent()) Navigator.of(context).pop(result);
      });
    } catch (failure) {
      if (!mounted || !widget.isCurrent()) return;
      setState(() {
        error = failure;
        uncertain =
            failure is! ApiFailure ||
            failure.status == null ||
            failure.status! < 400 ||
            failure.status == 408 ||
            failure.status == 429 ||
            failure.status! >= 500;
        rejected =
            failure is ApiFailure &&
            (const [401, 403, 404, 409].contains(failure.status) ||
                failure.code.startsWith('BILLING_PRESCRIPTION_REVISION_') ||
                const [
                  'PRESCRIPTION_ALREADY_PAID',
                  'PRESCRIPTION_NOT_EDITABLE',
                  'PRESCRIPTION_DISPENSE_NOT_PENDING',
                  'PHARMACY_OUTPATIENT_V1_REQUIRED',
                ].contains(failure.code));
        if (!uncertain) intent = null;
        busy = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && scroll.hasClients) {
          scroll.animateTo(
            0,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !busy && !uncertain,
    child: Form(
      key: form,
      child: ListView(
        controller: scroll,
        padding: const EdgeInsets.all(16),
        children: [
          const LText(
            'Chỉ sửa khi chưa thanh toán. Hệ thống kiểm tra lại quyền, tồn kho và khoản phải thu khi lưu.',
          ),
          if (error != null) ...[
            if (error is String)
              LText(error as String)
            else
              PharmacyRequestError(error!),
            if (uncertain)
              const LText(
                'Chưa xác định được kết quả sửa đơn thuốc. Hãy gửi lại đúng lần sửa để kiểm tra.',
              ),
          ],
          if (rejected)
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const LText('Làm mới đơn thuốc'),
            ),
          for (final line in lines)
            Card(
              key: ObjectKey(line),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(line.name),
                    TextFormField(
                      controller: line.quantity,
                      enabled: !locked,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: AppL10n.text(context, 'Số lượng'),
                      ),
                      validator: (value) {
                        final quantity = int.tryParse(value?.trim() ?? '');
                        return quantity == null ||
                                quantity < 1 ||
                                quantity > 2147483647
                            ? AppL10n.text(
                                context,
                                'Số lượng phải là số nguyên dương trong giới hạn cho phép.',
                              )
                            : null;
                      },
                    ),
                    TextFormField(
                      controller: line.dosage,
                      enabled: !locked,
                      maxLength: 255,
                      decoration: InputDecoration(
                        labelText: AppL10n.text(context, 'Cách dùng'),
                      ),
                      validator: (value) => (value?.trim().isEmpty ?? true)
                          ? AppL10n.text(
                              context,
                              'Chọn cách dùng cho thuốc này.',
                            )
                          : null,
                    ),
                    TextFormField(
                      controller: line.note,
                      enabled: !locked,
                      maxLength: 500,
                      decoration: InputDecoration(
                        labelText: AppL10n.text(context, 'Ghi chú'),
                      ),
                    ),
                    TextButton(
                      onPressed: locked
                          ? null
                          : () {
                              setState(() {
                                lines.remove(line);
                                removedLines.add(line);
                              });
                            },
                      child: const LText('Xóa dòng'),
                    ),
                  ],
                ),
              ),
            ),
          TextField(
            controller: keyword,
            enabled: !locked,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Tìm thuốc'),
            ),
            onSubmitted: (_) => _search(newQuery: true),
          ),
          OutlinedButton(
            onPressed: locked || searching
                ? null
                : () => _search(newQuery: true),
            child: const LText('Tìm kiếm'),
          ),
          if (searching)
            const SizedBox(height: 48, child: Center(child: MediFlowLoader())),
          if (searchError != null)
            PharmacyRequestError(
              searchError!,
              onRetry: locked ? null : () => _search(target: page),
            ),
          if (!searching && searchError == null) ...[
            for (final drug in drugs)
              ListTile(
                title: Text(drug.name),
                subtitle: Text('${drug.price} / ${drug.unit}'),
                trailing: IconButton(
                  tooltip: AppL10n.text(context, 'Thêm thuốc'),
                  icon: const Icon(Icons.add),
                  onPressed:
                      locked ||
                          lines.any((line) => line.id == drug.id) ||
                          drug.stockQuantity <= 0 ||
                          (DateTime.tryParse(
                                drug.expiryDate,
                              )?.isBefore(DateUtils.dateOnly(DateTime.now())) ??
                              true)
                      ? null
                      : () => setState(
                          () => lines.add(_Line(id: drug.id, name: drug.name)),
                        ),
                ),
              ),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: locked || page == 0
                      ? null
                      : () => _search(target: page - 1),
                  child: const LText('Trang trước'),
                ),
                TextButton(
                  onPressed: locked || page + 1 >= totalPages
                      ? null
                      : () => _search(target: page + 1),
                  child: const LText('Trang sau'),
                ),
              ],
            ),
          ],
          FilledButton(
            onPressed: busy || rejected || completed ? null : _save,
            child: busy
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : LText(uncertain ? 'Gửi lại đúng lần sửa' : 'Lưu đơn thuốc'),
          ),
        ],
      ),
    ),
  );
}
