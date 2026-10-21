import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/page_result.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import 'providers.dart';

bool _canRetry(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status! >= 500 ||
        error.status == 429);

class _PharmacyBoundary extends ConsumerWidget {
  const _PharmacyBoundary({required this.builder});
  final Widget Function(PharmacyRepository) builder;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(pharmacyAccessProvider);
    final repository = ref.watch(pharmacyRepositoryProvider);
    if (access == null || repository == null) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Chức năng Dược chưa sẵn sàng trên ứng dụng này.'),
        ),
      );
    }
    if (!access.canRead) {
      return const Center(child: Text('Bạn không có quyền xem Dược.'));
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, repository)),
      child: builder(repository),
    );
  }
}

class PharmacyPage extends StatelessWidget {
  const PharmacyPage({super.key, this.onOpenDrug});
  final ValueChanged<String>? onOpenDrug;
  @override
  Widget build(BuildContext context) => _PharmacyBoundary(
    builder: (repository) => _DrugCatalog(
      repository: repository,
      onOpenDrug:
          onOpenDrug ??
          (id) {
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => Scaffold(
                  appBar: AppBar(title: const Text('Chi tiết thuốc')),
                  body: DrugDetailPage(drugId: id),
                ),
              ),
            );
          },
    ),
  );
}

class _DrugCatalog extends StatefulWidget {
  const _DrugCatalog({required this.repository, required this.onOpenDrug});
  final PharmacyRepository repository;
  final ValueChanged<String> onOpenDrug;
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
      Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _keyword,
              textInputAction: TextInputAction.search,
              decoration: const InputDecoration(
                labelText: 'Tên thuốc',
                border: OutlineInputBorder(),
              ),
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                FilledButton.icon(
                  onPressed: _search,
                  icon: const Icon(Icons.search),
                  label: const Text('Tìm kiếm'),
                ),
                OutlinedButton.icon(
                  onPressed: _loading ? null : () => _load(_requestedPage),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Làm mới'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              _submitted == null
                  ? 'Danh mục thuốc'
                  : 'Kết quả cho: $_submitted',
            ),
          ],
        ),
      ),
      Expanded(
        child: _loading
            ? const Center(
                child: CircularProgressIndicator(
                  semanticsLabel: 'Đang tải danh mục thuốc',
                ),
              )
            : _error != null
            ? SingleChildScrollView(
                child: RequestError(
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
                      child: Text('Không tìm thấy thuốc phù hợp.'),
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
                              Text(
                                drug.name,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 4),
                              Text('${drug.price} VND / ${drug.unit}'),
                              Text('Tồn kho: ${drug.stockQuantity}'),
                              Text('Hạn dùng: ${drug.expiryDate}'),
                              if (drug.stockQuantity == 0)
                                const Text('Hết hàng'),
                              if (drug.isLowStock) const Text('Tồn thấp'),
                              const SizedBox(height: 4),
                              const Wrap(
                                crossAxisAlignment: WrapCrossAlignment.center,
                                children: [
                                  Text('Xem chi tiết'),
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
      SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Wrap(
            alignment: WrapAlignment.center,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            children: [
              IconButton(
                tooltip: 'Trang trước',
                onPressed: page.number > 0
                    ? () => _load(page.number - 1)
                    : null,
                icon: const Icon(Icons.chevron_left),
              ),
              Text(
                'Trang ${page.number + 1}/${page.totalPages == 0 ? 1 : page.totalPages} · ${page.totalElements} thuốc',
              ),
              IconButton(
                tooltip: 'Trang sau',
                onPressed: page.hasNext ? () => _load(page.number + 1) : null,
                icon: const Icon(Icons.chevron_right),
              ),
            ],
          ),
        ),
      ),
    ],
  );
}

class DrugDetailPage extends StatelessWidget {
  const DrugDetailPage({super.key, required this.drugId});
  final String drugId;
  @override
  Widget build(BuildContext context) => _PharmacyBoundary(
    builder: (repository) => _DrugDetail(
      key: ValueKey(drugId),
      drugId: drugId,
      repository: repository,
    ),
  );
}

class _DrugDetail extends StatefulWidget {
  const _DrugDetail({
    super.key,
    required this.drugId,
    required this.repository,
  });
  final String drugId;
  final PharmacyRepository repository;
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
      return const Center(
        child: CircularProgressIndicator(semanticsLabel: 'Đang tải thuốc'),
      );
    }
    if (_error != null) {
      return SingleChildScrollView(
        child: RequestError(_error!, onRetry: _canRetry(_error) ? _load : null),
      );
    }
    final drug = _drug!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Text(drug.name, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          SelectableText('Mã thuốc: ${drug.id}'),
          Text('Hoạt chất: ${drug.activeIngredient ?? 'Chưa cung cấp'}'),
          Text('Đơn vị: ${drug.unit}'),
          Text('Giá: ${drug.price} VND'),
          Text('Tồn kho: ${drug.stockQuantity}'),
          Text('Ngưỡng tồn thấp: ${drug.lowStockThreshold}'),
          if (drug.stockQuantity == 0) const Text('Hết hàng'),
          if (drug.isLowStock) const Text('Tồn thấp'),
          Text('Hạn dùng: ${drug.expiryDate}'),
          Text('Nhà sản xuất: ${drug.manufacturer ?? 'Chưa cung cấp'}'),
          Text('Cập nhật: ${drug.updatedAt.toIso8601String()}'),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Làm mới thuốc'),
          ),
        ],
      ),
    );
  }
}
