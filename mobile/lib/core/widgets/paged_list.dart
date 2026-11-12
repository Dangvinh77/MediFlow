import '../l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../domain/page_result.dart';
import '../theme/medical_colors.dart';
import 'medical_ui.dart';
import 'request_state.dart';

// Receives a domain use case; widgets never call the API client directly.
class PagedList<T> extends StatefulWidget {
  const PagedList({
    super.key,
    required this.load,
    required this.itemBuilder,
    this.canRetry,
  });
  final Future<PageResult<T>> Function(int page) load;
  final Widget Function(BuildContext, T) itemBuilder;
  final bool Function(Object error)? canRetry;
  @override
  State<PagedList<T>> createState() => _PagedListState<T>();
}

class _PagedListState<T> extends State<PagedList<T>> {
  PageResult<T>? _page;
  Object? _error;
  bool _loading = true;
  int _generation = 0;
  int _requestedPage = 0;

  @override
  void initState() {
    super.initState();
    _load(0);
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _load(int page) async {
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
      _requestedPage = page;
    });
    try {
      final result = await widget.load(page);
      if (mounted && generation == _generation) setState(() => _page = result);
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
    if (_error != null) {
      return RequestError(
        _error!,
        onRetry: (widget.canRetry?.call(_error!) ?? true)
            ? () => _load(_requestedPage)
            : null,
      );
    }
    if (_loading) {
      return Center(
        child: MediFlowLoader(
          semanticLabel: AppL10n.text(context, 'Đang tải dữ liệu'),
        ),
      );
    }
    final page = _page!;
    return Column(
      children: [
        Expanded(
          child: RefreshIndicator(
            onRefresh: () => _load(page.number),
            child: page.items.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: const [
                      Padding(
                        padding: EdgeInsets.fromLTRB(32, 56, 32, 32),
                        child: StateMessage(
                          icon: Icons.inbox_outlined,
                          color: Wayfinding.home,
                          child: LText(
                            'Chưa có dữ liệu phù hợp.',
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ),
                    ],
                  )
                : ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(16),
                    itemCount: page.items.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) =>
                        widget.itemBuilder(context, page.items[index]),
                  ),
          ),
        ),
        const Divider(),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            child: Wrap(
              alignment: WrapAlignment.center,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12,
              children: [
                IconButton(
                  tooltip: AppL10n.text(context, 'Trang trước'),
                  onPressed: page.number > 0
                      ? () => _load(page.number - 1)
                      : null,
                  icon: const Icon(Icons.chevron_left),
                ),
                LText(
                  'Trang ${page.number + 1}/${page.totalPages == 0 ? 1 : page.totalPages} · ${page.totalElements} kết quả',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                IconButton(
                  tooltip: AppL10n.text(context, 'Trang sau'),
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
}
