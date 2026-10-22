import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/drug.dart';
import '../domain/drug_commands.dart';
import '../domain/pharmacy_repository.dart';
import 'pharmacy_boundary.dart';

class CreateDrugPage extends StatelessWidget {
  const CreateDrugPage({super.key, this.onCreated});
  final ValueChanged<Drug>? onCreated;
  @override
  Widget build(BuildContext context) => PharmacyBoundary(
    manageDrugs: true,
    builder: (repository) =>
        _DrugForm(repository: repository, onCreated: onCreated),
  );
}

class AdjustDrugStockPage extends StatelessWidget {
  const AdjustDrugStockPage({super.key, required this.drugId});
  final String drugId;
  @override
  Widget build(BuildContext context) => PharmacyBoundary(
    manageDrugs: true,
    builder: (repository) => _DrugForm(
      key: ValueKey(drugId),
      repository: repository,
      drugId: drugId,
    ),
  );
}

class _DrugForm extends StatefulWidget {
  const _DrugForm({
    super.key,
    required this.repository,
    this.drugId,
    this.onCreated,
  });
  final PharmacyRepository repository;
  final String? drugId;
  final ValueChanged<Drug>? onCreated;
  @override
  State<_DrugForm> createState() => _DrugFormState();
}

class _DrugFormState extends State<_DrugForm> {
  bool get stock => widget.drugId != null;
  late final Map<String, String> labels = stock
      ? {'quantity': 'Số lượng điều chỉnh (+/−)', 'reason': 'Lý do điều chỉnh'}
      : {
          'drugName': 'Tên thuốc',
          'unit': 'Đơn vị',
          'price': 'Giá (VND)',
          'stockQuantity': 'Tồn kho ban đầu',
          'expiryDate': 'Hạn dùng (YYYY-MM-DD)',
          'lowStockThreshold': 'Ngưỡng tồn thấp (tùy chọn)',
          'activeIngredient': 'Hoạt chất (tùy chọn)',
          'manufacturer': 'Nhà sản xuất (tùy chọn)',
        };
  late final controllers = {
    for (final key in labels.keys) key: TextEditingController(),
  };
  late final focus = {for (final key in labels.keys) key: FocusNode()};
  final errorFocus = FocusNode();
  DialogRoute<bool>? confirmationRoute;
  NavigatorState? confirmationNavigator;
  Map<String, String> fields = {};
  Object? error;
  Drug? snapshot, success;
  bool busy = false, loading = false, uncertain = false, forbidden = false;
  bool refreshed = false;
  int generation = 0;
  @override
  void initState() {
    super.initState();
    if (stock) _refresh();
  }

  @override
  void dispose() {
    generation++;
    final route = confirmationRoute;
    final navigator = confirmationNavigator;
    if (route != null && navigator != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (navigator.mounted && route.isActive) navigator.removeRoute(route);
      });
    }
    for (final value in controllers.values) {
      value.dispose();
    }
    for (final value in focus.values) {
      value.dispose();
    }
    errorFocus.dispose();
    super.dispose();
  }

  String value(String key) => controllers[key]!.text.trim();
  String? optional(String key) => value(key).isEmpty ? null : value(key);

  Future<void> _refresh() async {
    if (loading || busy) return;
    final ticket = ++generation;
    setState(() {
      loading = true;
      snapshot = null;
      error = null;
      refreshed = false;
    });
    try {
      final drug = await ReadDrug(widget.repository)(widget.drugId!);
      if (!mounted || ticket != generation) return;
      setState(() {
        snapshot = drug;
        refreshed = true;
      });
    } catch (failure) {
      if (!mounted || ticket != generation) return;
      setState(() => error = failure);
      errorFocus.requestFocus();
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
  }

  Future<void> _submit() async {
    if (busy ||
        loading ||
        uncertain ||
        forbidden ||
        success != null ||
        (stock && snapshot == null)) {
      return;
    }
    final create = stock
        ? null
        : CreateDrug(
            drugName: value('drugName'),
            unit: value('unit'),
            price: value('price'),
            stockQuantity: int.tryParse(value('stockQuantity')) ?? -1,
            expiryDate: value('expiryDate'),
            lowStockThreshold: value('lowStockThreshold').isEmpty
                ? null
                : int.tryParse(value('lowStockThreshold')) ?? -1,
            activeIngredient: optional('activeIngredient'),
            manufacturer: optional('manufacturer'),
          );
    final adjust = stock
        ? AdjustDrugStock(
            quantity: int.tryParse(value('quantity')) ?? 0,
            reason: optional('reason'),
          )
        : null;
    final validation = stock ? adjust!.errors() : create!.errors();
    setState(() {
      fields = validation;
      error = null;
    });
    if (validation.isNotEmpty) {
      final first = focus[validation.keys.first]!;
      first.requestFocus();
      if (first.context != null) Scrollable.ensureVisible(first.context!);
      return;
    }
    // Lock synchronously, including the confirmation dialog, to prevent double taps.
    setState(() => busy = true);
    final navigator = Navigator.of(context, rootNavigator: true);
    final route = DialogRoute<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          stock ? 'Xác nhận điều chỉnh tồn kho' : 'Xác nhận thêm thuốc',
        ),
        content: Text(
          stock
              ? '${snapshot!.name}: ${adjust!.quantity > 0 ? 'tăng' : 'giảm'} ${adjust.quantity.abs()} ${snapshot!.unit}. Backend kiểm tra tồn khả dụng và phần đã giữ chỗ.'
              : 'Thêm ${create!.drugName} vào danh mục? Giá và số lượng sẽ được máy chủ kiểm tra.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
    confirmationRoute = route;
    confirmationNavigator = navigator;
    final confirmed = await navigator.push<bool>(route);
    await route.completed;
    confirmationRoute = null;
    confirmationNavigator = null;
    if (!mounted) return;
    if (confirmed != true) {
      setState(() => busy = false);
      return;
    }
    final ticket = ++generation;
    try {
      final result = stock
          ? await widget.repository.adjustStock(widget.drugId!, adjust!)
          : await widget.repository.createDrug(create!);
      if (!mounted || ticket != generation) return;
      setState(() {
        success = result;
        snapshot = result;
      });
      if (!stock) widget.onCreated?.call(result);
    } catch (failure) {
      if (!mounted || ticket != generation) return;
      setState(() {
        error = failure;
        fields = failure is ApiFailure ? failure.fields : {};
        forbidden =
            failure is ApiFailure &&
            (failure.status == 401 || failure.status == 403);
        // No idempotency contract for either command: ambiguous responses are never resent.
        uncertain =
            failure is! ApiFailure ||
            failure.status == null ||
            failure.status! >= 500 ||
            failure.status == 409 ||
            failure.status == 408;
        refreshed = false;
      });
      final first = fields.keys.where(focus.containsKey).firstOrNull;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || ticket != generation) return;
        final target = first == null ? errorFocus : focus[first]!;
        target.requestFocus();
        if (target.context != null) Scrollable.ensureVisible(target.context!);
      });
    } finally {
      if (mounted && ticket == generation) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final locked = busy || loading || uncertain || forbidden || success != null;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (stock) ...[
            Text('Mã thuốc: ${widget.drugId}'),
            if (loading)
              const LinearProgressIndicator(
                semanticsLabel: 'Đang kiểm tra tồn kho',
              ),
            if (snapshot != null) ...[
              Text(snapshot!.name),
              Text('Tồn kho máy chủ: ${snapshot!.stockQuantity}'),
            ],
          ],
          if (success != null)
            Semantics(
              liveRegion: true,
              child: Text(
                stock
                    ? 'Điều chỉnh thành công. Tồn kho máy chủ: ${success!.stockQuantity}'
                    : 'Đã tạo thuốc. Mã thuốc: ${success!.id}',
              ),
            ),
          if (error != null)
            Focus(
              focusNode: errorFocus,
              child: Semantics(liveRegion: true, child: RequestError(error!)),
            ),
          if (uncertain)
            const Text(
              'Chưa xác định chắc chắn kết quả hoặc có xung đột. Không gửi lại lệnh cũ. Kiểm tra dữ liệu máy chủ trước khi tạo thao tác mới.',
            ),
          if (!stock && uncertain)
            OutlinedButton(
              onPressed: () => Navigator.of(context).maybePop(),
              child: const Text('Về danh mục để kiểm tra kết quả'),
            ),
          for (final entry in labels.entries)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: TextField(
                controller: controllers[entry.key],
                focusNode: focus[entry.key],
                enabled: !locked,
                keyboardType:
                    const {
                      'price',
                      'quantity',
                      'stockQuantity',
                      'lowStockThreshold',
                    }.contains(entry.key)
                    ? const TextInputType.numberWithOptions(
                        decimal: true,
                        signed: true,
                      )
                    : TextInputType.text,
                decoration: InputDecoration(
                  labelText: entry.value,
                  errorText: fields[entry.key],
                  border: const OutlineInputBorder(),
                ),
                onChanged: (_) {
                  if (fields.containsKey(entry.key)) {
                    setState(() => fields.remove(entry.key));
                  }
                },
              ),
            ),
          FilledButton(
            onPressed: locked || (stock && snapshot == null) ? null : _submit,
            child: Text(
              busy
                  ? 'Đang xử lý…'
                  : stock
                  ? 'Điều chỉnh tồn kho'
                  : 'Tạo thuốc',
            ),
          ),
          if (stock) ...[
            OutlinedButton(
              onPressed: busy || loading || forbidden ? null : _refresh,
              child: const Text('Kiểm tra lại tồn kho'),
            ),
            if ((uncertain && refreshed) || success != null)
              TextButton(
                onPressed: () {
                  setState(() {
                    uncertain = false;
                    success = null;
                    fields = {};
                    error = null;
                    refreshed = false;
                    for (final controller in controllers.values) {
                      controller.clear();
                    }
                  });
                },
                child: const Text('Bắt đầu điều chỉnh mới'),
              ),
          ],
        ],
      ),
    );
  }
}
