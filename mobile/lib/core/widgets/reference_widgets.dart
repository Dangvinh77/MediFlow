import '../l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../domain/reference.dart';
import '../network/gateway_reference_reader.dart';
import '../network/api_failure.dart';
import '../providers.dart';
export '../domain/reference.dart';

String keyDate(ReferenceOption item, ReferenceKind kind) {
  if (kind != ReferenceKind.patient || item.detail == null) return '';
  final date = DateTime.tryParse(item.detail!);
  if (date == null) return '';
  return ' · ${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
}

final referenceReaderProvider = Provider<ReferenceReader>(
  (ref) => GatewayReferenceReader(
    ref.watch(apiClientProvider),
    role: ref.watch(workspaceAccessProvider)?.role,
  ),
);
final referenceNameProvider = FutureProvider.autoDispose
    .family<ReferenceOption, (ReferenceKind, String)>((ref, key) {
      final access = ref.watch(workspaceAccessProvider);
      if (!canReadReference(access?.role, key.$1)) {
        throw const ApiFailure('Không có quyền tra cứu tên', status: 403);
      }
      return ref.watch(referenceReaderProvider).read(key.$1, key.$2);
    }, retry: (_, _) => null);

String referenceError(Object error) =>
    error is ApiFailure && error.status == 403
    ? 'Không có quyền tra cứu tên'
    : error is ApiFailure && error.status == 404
    ? 'Không tìm thấy thông tin'
    : 'Chưa tải được thông tin';

class ReferenceLabel extends ConsumerWidget {
  const ReferenceLabel({
    super.key,
    required this.kind,
    required this.id,
    required this.label,
  });
  final ReferenceKind kind;
  final String id, label;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (!canReadReference(access?.role, kind)) {
      return LText('$label: Không có quyền tra cứu tên');
    }
    final result = ref.watch(referenceNameProvider((kind, id)));
    return result.when(
      data: (item) => LText('$label: ${item.name}${keyDate(item, kind)}'),
      loading: () => LText('$label: Đang tải…'),
      error: (error, _) => Row(
        children: [
          Expanded(child: LText('$label: ${referenceError(error)}')),
          if (error is! ApiFailure ||
              !const [401, 403, 404].contains(error.status))
            IconButton(
              tooltip: AppL10n.text(context, 'Thử lại'),
              onPressed: () =>
                  ref.invalidate(referenceNameProvider((kind, id))),
              icon: const Icon(Icons.refresh),
            ),
        ],
      ),
    );
  }
}

class ReferencePicker extends ConsumerWidget {
  const ReferencePicker({
    super.key,
    required this.kind,
    required this.label,
    required this.value,
    required this.onChanged,
    this.scope,
    this.required = false,
  });
  final ReferenceKind kind;
  final String label;
  final String? value, scope;
  final ValueChanged<String?>? onChanged;
  final bool required;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final allowed = canSearchReference(access?.role, kind);
    final name = value != null && allowed
        ? ref.watch(referenceNameProvider((kind, value!)))
        : null;
    return FormField<String>(
      initialValue: value,
      validator: (_) =>
          required && value == null ? 'Vui lòng chọn $label' : null,
      builder: (field) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: InputDecorator(
          decoration: InputDecoration(
            labelText: AppL10n.text(context, label),
            border: const OutlineInputBorder(),
            errorText: AppL10n.text(context, field.errorText),
          ),
          child: Row(
            children: [
              Expanded(
                child: LText(
                  !allowed
                      ? 'Không có quyền tra cứu tên'
                      : value == null
                      ? 'Chọn theo tên'
                      : name?.when(
                              data: (item) => item.name,
                              loading: () => 'Đang tải…',
                              error: (e, _) => referenceError(e),
                            ) ??
                            'Không có quyền tra cứu tên',
                ),
              ),
              IconButton(
                tooltip: AppL10n.text(context, 'Chọn $label'),
                onPressed: !allowed || onChanged == null
                    ? null
                    : () async {
                        final selected = await showDialog<ReferenceOption>(
                          context: context,
                          builder: (_) => _ReferenceDialog(
                            kind: kind,
                            label: label,
                            scope: scope,
                          ),
                        );
                        if (selected != null &&
                            context.mounted &&
                            ref
                                    .read(workspaceAccessProvider)
                                    ?.sessionIdentity ==
                                access?.sessionIdentity) {
                          field.didChange(selected.id);
                          onChanged!(selected.id);
                        }
                      },
                icon: const Icon(Icons.search),
              ),
              if (value != null && onChanged != null)
                IconButton(
                  tooltip: AppL10n.text(context, 'Bỏ chọn'),
                  onPressed: () {
                    field.didChange(null);
                    onChanged!(null);
                  },
                  icon: const Icon(Icons.close),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ReferenceDialog extends ConsumerStatefulWidget {
  const _ReferenceDialog({required this.kind, required this.label, this.scope});
  final ReferenceKind kind;
  final String label;
  final String? scope;
  @override
  ConsumerState<_ReferenceDialog> createState() => _ReferenceDialogState();
}

class _ReferenceDialogState extends ConsumerState<_ReferenceDialog> {
  final query = TextEditingController();
  int page = 0, generation = 0;
  ReferencePage? result;
  Object? error;
  bool loading = true;
  Object? identity;
  @override
  void initState() {
    super.initState();
    identity = ref.read(workspaceAccessProvider)?.sessionIdentity;
    Future.microtask(load);
  }

  @override
  void dispose() {
    query.dispose();
    super.dispose();
  }

  Future<void> load() async {
    final current = ++generation;
    setState(() {
      loading = true;
      error = null;
      result = null;
    });
    try {
      final value = await ref
          .read(referenceReaderProvider)
          .search(
            widget.kind,
            query: query.text,
            page: page,
            scope: widget.scope,
          );
      if (mounted &&
          current == generation &&
          ref.read(workspaceAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          result = value;
          loading = false;
        });
      }
    } catch (e) {
      if (mounted && current == generation) {
        setState(() {
          error = e;
          loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.sessionIdentity != identity) {
      return const AlertDialog(content: LText('Phiên đăng nhập đã thay đổi.'));
    }
    return AlertDialog(
      scrollable: true,
      title: LText('Chọn ${widget.label}'),
      content: SizedBox(
        width: 480,
        height: 480,
        child: Column(
          children: [
            TextField(
              controller: query,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Tìm theo tên'),
                prefixIcon: Icon(Icons.search),
              ),
              onSubmitted: (_) {
                page = 0;
                load();
              },
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: loading
                    ? null
                    : () {
                        page = 0;
                        load();
                      },
                child: const LText('Tìm kiếm'),
              ),
            ),
            Expanded(
              child: loading
                  ? const Center(child: MediFlowLoader())
                  : error != null
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          LText(
                            error is ArgumentError
                                ? (error as ArgumentError).message.toString()
                                : referenceError(error!),
                          ),
                          TextButton(
                            onPressed: load,
                            child: const LText('Thử lại'),
                          ),
                        ],
                      ),
                    )
                  : result!.items.isEmpty
                  ? const Center(
                      child: LText('Không có kết quả trên trang này.'),
                    )
                  : ListView(
                      children: [
                        for (final item in result!.items)
                          ListTile(
                            title: LText(item.name),
                            subtitle: item.detail == null
                                ? null
                                : LText(item.detail!),
                            onTap: () => Navigator.pop(context, item),
                          ),
                      ],
                    ),
            ),
            const LText(
              'Tên được lọc theo từng trang. Dùng trang sau để xem thêm.',
              style: TextStyle(fontSize: 12),
            ),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                TextButton(
                  onPressed: loading || page == 0
                      ? null
                      : () {
                          page--;
                          load();
                        },
                  child: const LText('Trước'),
                ),
                LText('Trang ${page + 1}'),
                TextButton(
                  onPressed: loading || result?.hasMore != true
                      ? null
                      : () {
                          page++;
                          load();
                        },
                  child: const LText('Sau'),
                ),
              ],
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const LText('Đóng'),
        ),
      ],
    );
  }
}
