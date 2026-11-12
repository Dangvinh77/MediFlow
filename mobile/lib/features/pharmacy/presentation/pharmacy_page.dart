import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/domain/page_result.dart';
import '../../../core/network/api_failure.dart';
import 'pharmacy_request_error.dart';
import 'pharmacy_date_text.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import 'pharmacy_outbox_page.dart';
import 'providers.dart';
import 'pharmacy_boundary.dart';
import 'drug_forms.dart';
import 'prescription_page.dart';
import 'create_prescription_page.dart';

bool _canRetry(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status! >= 500 ||
        error.status == 429);

class PharmacyPage extends ConsumerWidget {
  const PharmacyPage({
    super.key,
    this.onOpenDrug,
    this.onCreateDrug,
    this.onLookupPrescription,
    this.onCreatePrescription,
  });
  final ValueChanged<String>? onOpenDrug;
  final VoidCallback? onCreateDrug, onLookupPrescription, onCreatePrescription;
  @override
  Widget build(BuildContext context, WidgetRef ref) => PharmacyBoundary(
    builder: (repository) => _DrugCatalog(
      repository: repository,
      onPrescribe:
          ref.watch(pharmacyAccessProvider)?.canPrescribe == true &&
              ref.watch(pharmacyModeProvider) == PharmacyMode.outpatient
          ? onCreatePrescription ??
                () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: AppBar(title: const LText('Kê đơn ngoại trú')),
                      body: CreatePrescriptionPage(
                        onCreated: (rx) =>
                            Navigator.of(context).pushReplacement(
                              MaterialPageRoute<void>(
                                builder: (_) => Scaffold(
                                  appBar: AppBar(
                                    title: const LText('Chi tiết đơn thuốc'),
                                  ),
                                  body: PrescriptionDetailPage(
                                    prescriptionId: rx.id,
                                  ),
                                ),
                              ),
                            ),
                      ),
                    ),
                  ),
                )
          : null,
      onOutbox:
          ref.watch(pharmacyAccessProvider)?.role == 'ADMIN' &&
              ref.watch(pharmacyOutboxRepositoryProvider) != null
          ? () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => Scaffold(
                  appBar: AppBar(title: const LText('Khôi phục đồng bộ')),
                  body: const PharmacyOutboxPage(),
                ),
              ),
            )
          : null,
      onLookup:
          onLookupPrescription ??
          () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => Scaffold(
                appBar: AppBar(title: const LText('Tra cứu đơn thuốc')),
                body: const PrescriptionLookupPage(),
              ),
            ),
          ),
      onCreate: ref.watch(pharmacyAccessProvider)?.canManageDrugs == true
          ? onCreateDrug ??
                () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: AppBar(title: const LText('Thêm thuốc')),
                      body: CreateDrugPage(
                        onCreated: (drug) {
                          Navigator.of(context).pushReplacement(
                            MaterialPageRoute<void>(
                              builder: (_) => Scaffold(
                                appBar: AppBar(
                                  title: const LText('Chi tiết thuốc'),
                                ),
                                body: DrugDetailPage(drugId: drug.id),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                )
          : null,
      onOpenDrug:
          onOpenDrug ??
          (id) {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => Scaffold(
                  appBar: AppBar(title: const LText('Chi tiết thuốc')),
                  body: DrugDetailPage(drugId: id),
                ),
              ),
            );
          },
    ),
  );
}

class _DrugCatalog extends StatefulWidget {
  const _DrugCatalog({
    required this.repository,
    required this.onOpenDrug,
    required this.onLookup,
    this.onCreate,
    this.onPrescribe,
    this.onOutbox,
  });
  final PharmacyRepository repository;
  final ValueChanged<String> onOpenDrug;
  final VoidCallback onLookup;
  final VoidCallback? onCreate;
  final VoidCallback? onPrescribe;
  final VoidCallback? onOutbox;
  @override
  State<_DrugCatalog> createState() => _DrugCatalogState();
}

class _DrugCatalogState extends State<_DrugCatalog> {
  final _keyword = TextEditingController();
  String? _submitted;
  PageResult<Drug>? _result;
  Object? _error;
  bool _loading = true;
  int _generation = 0, _requestedPage = 0;
  @override
  void initState() {
    super.initState();
    _load(0);
  }

  @override
  void dispose() {
    _generation++;
    _keyword.dispose();
    super.dispose();
  }

  Future<void> _load(int page) async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _result = null;
      _requestedPage = page;
    });
    try {
      final result = await FindDrugs(widget.repository)(
        keyword: _submitted,
        page: page,
      );
      if (mounted && generation == _generation) {
        setState(() => _result = result);
      }
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  void _search() {
    _submitted = _keyword.text.trim().isEmpty ? null : _keyword.text.trim();
    FocusScope.of(context).unfocus();
    _load(0);
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Flexible(
        flex: 2,
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  controller: _keyword,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Tên thuốc'),
                    border: OutlineInputBorder(),
                  ),
                  onSubmitted: (_) => _search(),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (widget.onCreate != null)
                      FilledButton.icon(
                        onPressed: widget.onCreate,
                        icon: const Icon(Icons.add),
                        label: const LText('Thêm thuốc'),
                      ),
                    if (widget.onPrescribe != null)
                      FilledButton.icon(
                        onPressed: widget.onPrescribe,
                        icon: const Icon(Icons.edit_note),
                        label: const LText('Kê đơn ngoại trú'),
                      ),
                    OutlinedButton(
                      onPressed: widget.onLookup,
                      child: const LText('Tra cứu đơn'),
                    ),
                    if (widget.onOutbox != null)
                      OutlinedButton(
                        onPressed: widget.onOutbox,
                        child: const LText('Khôi phục đồng bộ'),
                      ),
                    FilledButton.icon(
                      onPressed: _search,
                      icon: const Icon(Icons.search),
                      label: const LText('Tìm kiếm'),
                    ),
                    OutlinedButton.icon(
                      onPressed: _loading ? null : () => _load(_requestedPage),
                      icon: const Icon(Icons.refresh),
                      label: const LText('Làm mới'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                LText(
                  _submitted == null
                      ? 'Danh mục thuốc'
                      : 'Kết quả cho: $_submitted',
                ),
              ],
            ),
          ),
        ),
      ),
      Expanded(
        flex: 3,
        child: _loading
            ? Center(
                child: MediFlowLoader(
                  semanticLabel: AppL10n.text(
                    context,
                    'Đang tải danh mục thuốc',
                  ),
                ),
              )
            : _error != null
            ? SingleChildScrollView(
                child: PharmacyRequestError(
                  _error!,
                  onRetry: _canRetry(_error)
                      ? () => _load(_requestedPage)
                      : null,
                ),
              )
            : _results(_result!),
      ),
    ],
  );

  Widget _results(PageResult<Drug> page) => Column(
    children: [
      Expanded(
        child: RefreshIndicator(
          onRefresh: () => _load(page.number),
          child: page.items.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    Padding(
                      padding: EdgeInsets.all(24),
                      child: LText('Không tìm thấy thuốc phù hợp.'),
                    ),
                  ],
                )
              : ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(12),
                  itemCount: page.items.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final drug = page.items[index];
                    return Card(
                      child: InkWell(
                        onTap: () => widget.onOpenDrug(drug.id),
                        borderRadius: BorderRadius.circular(12),
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              LText(
                                drug.name,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 4),
                              LText('${drug.price} đồng / ${drug.unit}'),
                              LText('Tồn kho: ${drug.stockQuantity}'),
                              LText(
                                'Hạn dùng: ${pharmacyDate(drug.expiryDate)}',
                              ),
                              if (drug.stockQuantity == 0)
                                const LText('Hết hàng'),
                              if (drug.isLowStock) const LText('Sắp hết thuốc'),
                              const SizedBox(height: 4),
                              const Wrap(
                                crossAxisAlignment: WrapCrossAlignment.center,
                                children: [
                                  LText('Xem chi tiết'),
                                  Icon(Icons.chevron_right),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ),
      Flexible(
        child: SingleChildScrollView(
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Wrap(
                alignment: WrapAlignment.center,
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                children: [
                  IconButton(
                    tooltip: AppL10n.text(context, 'Trang trước'),
                    onPressed: page.number > 0
                        ? () => _load(page.number - 1)
                        : null,
                    icon: const Icon(Icons.chevron_left),
                  ),
                  LText(
                    'Trang ${page.number + 1}/${page.totalPages == 0 ? 1 : page.totalPages} · ${page.totalElements} thuốc',
                  ),
                  IconButton(
                    tooltip: AppL10n.text(context, 'Trang sau'),
                    onPressed: page.hasNext
                        ? () => _load(page.number + 1)
                        : null,
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ],
  );
}

class DrugDetailPage extends ConsumerWidget {
  const DrugDetailPage({super.key, required this.drugId});
  final String drugId;
  @override
  Widget build(BuildContext context, WidgetRef ref) => PharmacyBoundary(
    builder: (repository) => _DrugDetail(
      key: ValueKey(drugId),
      drugId: drugId,
      repository: repository,
      canManage: ref.watch(pharmacyAccessProvider)?.canManageDrugs == true,
    ),
  );
}

class _DrugDetail extends StatefulWidget {
  const _DrugDetail({
    super.key,
    required this.drugId,
    required this.repository,
    required this.canManage,
  });
  final String drugId;
  final PharmacyRepository repository;
  final bool canManage;
  @override
  State<_DrugDetail> createState() => _DrugDetailState();
}

class _DrugDetailState extends State<_DrugDetail> {
  Drug? _drug;
  Object? _error;
  bool _loading = true;
  int _generation = 0;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _drug = null;
    });
    try {
      final drug = await ReadDrug(widget.repository)(widget.drugId);
      if (mounted && generation == _generation) setState(() => _drug = drug);
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return Center(
        child: MediFlowLoader(
          semanticLabel: AppL10n.text(context, 'Đang tải thuốc'),
        ),
      );
    }
    if (_error != null) {
      return SingleChildScrollView(
        child: PharmacyRequestError(
          _error!,
          onRetry: _canRetry(_error) ? _load : null,
        ),
      );
    }
    final drug = _drug!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          LText(drug.name, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          LText('Hoạt chất: ${drug.activeIngredient ?? 'Chưa cung cấp'}'),
          LText('Đơn vị: ${drug.unit}'),
          LText('Giá: ${drug.price} đồng'),
          LText('Tồn kho: ${drug.stockQuantity}'),
          LText('Báo sắp hết khi còn: ${drug.lowStockThreshold}'),
          if (drug.stockQuantity == 0) const LText('Hết hàng'),
          if (drug.isLowStock) const LText('Sắp hết thuốc'),
          LText('Hạn dùng: ${pharmacyDate(drug.expiryDate)}'),
          LText('Nhà sản xuất: ${drug.manufacturer ?? 'Chưa cung cấp'}'),
          LText('Cập nhật: ${pharmacyDateTime(drug.updatedAt)}'),
          ExpansionTile(
            title: const LText('Thông tin tra cứu'),
            children: [SelectableText('Mã thuốc: ${drug.id}')],
          ),
          const SizedBox(height: 16),
          if (widget.canManage)
            FilledButton(
              onPressed: () async {
                await Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: AppBar(title: const LText('Điều chỉnh tồn kho')),
                      body: AdjustDrugStockPage(drugId: drug.id),
                    ),
                  ),
                );
                if (mounted) _load();
              },
              child: const LText('Điều chỉnh tồn kho'),
            ),
          OutlinedButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const LText('Làm mới thuốc'),
          ),
        ],
      ),
    );
  }
}
