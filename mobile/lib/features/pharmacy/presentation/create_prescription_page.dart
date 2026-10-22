import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/pharmacy_identity.dart';
import 'pharmacy_identity_label.dart';
import '../domain/prescription.dart';
import '../domain/prescription_commands.dart';
import 'pharmacy_boundary.dart';
import 'providers.dart';

class CreatePrescriptionPage extends ConsumerWidget {
  const CreatePrescriptionPage({super.key, this.onCreated});
  final ValueChanged<Prescription>? onCreated;
  @override
  Widget build(BuildContext context, WidgetRef ref) => PharmacyBoundary(
    builder: (repository) {
      if (ref.watch(pharmacyAccessProvider)?.canPrescribe != true) {
        return const Center(child: Text('Bạn không có quyền kê đơn.'));
      }
      if (ref.watch(pharmacyModeProvider) != PharmacyMode.outpatient) {
        return const Center(
          child: Text('Kê đơn ngoại trú V1 chưa được cấu hình.'),
        );
      }
      final writer = ref.watch(outpatientPrescriptionWriterProvider);
      return _CreatePrescription(
        key: ObjectKey(writer),
        repository: repository,
        writer: writer,
        onCreated: onCreated,
      );
    },
  );
}

class _DraftLine {
  _DraftLine(this.drug);
  final Drug drug;
  final quantity = TextEditingController(text: '1');
  final dosage = TextEditingController();
  final focus = FocusNode();
  final dosageFocus = FocusNode();
  void dispose() {
    quantity.dispose();
    dosage.dispose();
    focus.dispose();
    dosageFocus.dispose();
  }
}

class _CreatePrescription extends StatefulWidget {
  const _CreatePrescription({
    super.key,
    required this.repository,
    required this.writer,
    this.onCreated,
  });
  final PharmacyRepository repository;
  final OutpatientPrescriptionWriter? writer;
  final ValueChanged<Prescription>? onCreated;
  @override
  State<_CreatePrescription> createState() => _CreatePrescriptionState();
}

class _CreatePrescriptionState extends State<_CreatePrescription> {
  static const labels = {
    'recordId': 'Mã hồ sơ',
    'patientId': 'Mã bệnh nhân',
    'doctorId': 'Mã bác sĩ nhân sự',
    'departmentId': 'Mã khoa',
    'careEpisodeId': 'Mã lượt khám OUTPATIENT_VISIT',
    'priceCode': 'Mã giá Billing',
    'sourceReference': 'Nguồn đã đối chiếu',
    'prescribedDate': 'Ngày kê đơn (YYYY-MM-DD)',
  };
  final fields = {for (final key in labels.keys) key: TextEditingController()};
  final focuses = {for (final key in labels.keys) key: FocusNode()};
  final feedbackFocus = FocusNode();
  final keyword = TextEditingController();
  final keywordFocus = FocusNode();
  final lines = <_DraftLine>[];
  List<Drug> drugs = [];
  Map<String, String> errors = {};
  Object? searchError, commandError;
  PrescriptionIntent? intent;
  Prescription? created;
  bool searched = false,
      searching = false,
      busy = false,
      retryAllowed = false,
      rejected = false;
  int page = 0, pages = 0, searchGeneration = 0;
  String? submittedKeyword;
  DialogRoute<bool>? confirmation;
  NavigatorState? dialogNavigator;

  @override
  void dispose() {
    searchGeneration++;
    final route = confirmation;
    final navigator = dialogNavigator;
    if (route != null && navigator != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (route.isActive) navigator.removeRoute(route);
      });
    }
    for (final field in fields.values) {
      field.dispose();
    }
    for (final focus in focuses.values) {
      focus.dispose();
    }
    for (final line in lines) {
      line.dispose();
    }
    keyword.dispose();
    keywordFocus.dispose();
    feedbackFocus.dispose();
    super.dispose();
  }

  Future<void> _search({int target = 0, bool submit = true}) async {
    if (busy || intent != null) return;
    final ticket = ++searchGeneration;
    if (submit) submittedKeyword = keyword.text.trim();
    setState(() {
      searching = true;
      searchError = null;
      drugs = [];
    });
    try {
      final result = await FindDrugs(widget.repository)(
        keyword: submittedKeyword,
        page: target,
      );
      if (!mounted || ticket != searchGeneration) return;
      setState(() {
        drugs = result.items;
        page = result.number;
        pages = result.totalPages;
        searched = true;
      });
    } catch (failure) {
      if (mounted && ticket == searchGeneration) {
        setState(() => searchError = failure);
      }
    } finally {
      if (mounted && ticket == searchGeneration) {
        setState(() => searching = false);
      }
    }
  }

  CreateOutpatientPrescription _command() => CreateOutpatientPrescription(
    context: OutpatientPrescriptionContext(
      recordId: fields['recordId']!.text.trim(),
      patientId: fields['patientId']!.text.trim(),
      doctorId: fields['doctorId']!.text.trim(),
      departmentId: fields['departmentId']!.text.trim(),
      careEpisodeId: fields['careEpisodeId']!.text.trim(),
      priceCode: fields['priceCode']!.text,
      sourceReference: fields['sourceReference']!.text,
    ),
    prescribedDate: fields['prescribedDate']!.text,
    lines: lines
        .map(
          (line) => RequestedPrescriptionLine(
            drugId: line.drug.id,
            quantity: int.tryParse(line.quantity.text) ?? 0,
            dosage: line.dosage.text.isEmpty ? null : line.dosage.text,
          ),
        )
        .toList(),
  );

  Future<void> _submit({bool retry = false}) async {
    if (busy || widget.writer == null || created != null) return;
    if (retry) {
      if (!retryAllowed || intent == null) return;
    } else {
      if (intent != null) return;
      final command = _command();
      final validation = command.errors();
      setState(() => errors = validation);
      if (validation.isNotEmpty) {
        final key = labels.keys.where(validation.containsKey).firstOrNull;
        if (key != null) {
          focuses[key]!.requestFocus();
          final target = focuses[key]!.context;
          if (target != null) Scrollable.ensureVisible(target);
        } else {
          feedbackFocus.requestFocus();
        }
        return;
      }
      intent = PrescriptionIntent(command);
    }
    // Lock synchronously before confirmation, including in-flight catalog reads.
    searchGeneration++;
    setState(() {
      busy = true;
      searching = false;
      commandError = null;
    });
    final navigator = Navigator.of(context);
    final current = intent!;
    final route = DialogRoute<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(retry ? 'Gửi lại đúng ý định V1' : 'Xác nhận kê đơn V1'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Hồ sơ: ${current.command.context.recordId}'),
              PharmacyIdentityLabel(
                kind: PharmacyIdentityKind.patient,
                id: current.command.context.patientId,
                label: 'Bệnh nhân',
              ),
              PharmacyIdentityLabel(
                kind: PharmacyIdentityKind.doctor,
                id: current.command.context.doctorId,
                label: 'Bác sĩ kê đơn',
              ),
              PharmacyIdentityLabel(
                kind: PharmacyIdentityKind.department,
                id: current.command.context.departmentId,
                label: 'Khoa',
              ),
              Text(
                'Lượt khám: ${current.command.context.careEpisodeId}\n'
                '${current.command.lines.length} dòng thuốc. Backend kiểm tra nguồn, quyền và giá.',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận kê đơn'),
          ),
        ],
      ),
    );
    confirmation = route;
    dialogNavigator = navigator;
    final accepted = await navigator.push(route);
    await route.completed;
    if (!mounted) return;
    setState(() => confirmation = null);
    if (accepted != true) {
      setState(() {
        busy = false;
        if (!retry) intent = null;
      });
      return;
    }
    try {
      final result = await widget.writer!.createPrescription(current);
      if (!mounted) return;
      setState(() {
        created = result;
        retryAllowed = false;
      });
      widget.onCreated?.call(result);
    } catch (failure) {
      if (!mounted) return;
      setState(() {
        commandError = failure;
        retryAllowed =
            failure is! ApiFailure ||
            failure.status == null ||
            failure.status == 408 ||
            failure.status == 429 ||
            failure.status! >= 500;
        rejected =
            failure is ApiFailure &&
            (failure.status == 400 || failure.status == 422);
        if (failure is ApiFailure) {
          errors = {
            for (final entry in failure.fields.entries)
              entry.key.replaceAllMapped(
                RegExp(r'\[(\d+)\]'),
                (match) => '.${match[1]}',
              ): entry.value,
          };
        }
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) feedbackFocus.requestFocus();
      });
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final locked = busy || intent != null || created != null;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Kê đơn ngoại trú V1',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const Text(
            'Đối chiếu hồ sơ và nhân sự từ workflow public. Lượt khám phải có loại OUTPATIENT_VISIT. '
            'Mã giá do Billing xác nhận; không dùng mã tài khoản thay bác sĩ, không đoán ID.',
          ),
          if (widget.writer == null)
            const Text(
              'Chưa thể gửi đơn: đang chờ client Gateway hỗ trợ Idempotency-Key.',
            ),
          for (final field in labels.entries)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextField(
                    controller: fields[field.key],
                    focusNode: focuses[field.key],
                    enabled: !locked,
                    decoration: InputDecoration(
                      labelText: field.value,
                      errorText: errors[field.key],
                    ),
                  ),
                  if (const [
                    'patientId',
                    'doctorId',
                    'departmentId',
                  ].contains(field.key))
                    ValueListenableBuilder<TextEditingValue>(
                      valueListenable: fields[field.key]!,
                      builder: (_, value, _) => PharmacyIdentityLabel(
                        kind: switch (field.key) {
                          'patientId' => PharmacyIdentityKind.patient,
                          'doctorId' => PharmacyIdentityKind.doctor,
                          _ => PharmacyIdentityKind.department,
                        },
                        id: value.text.trim(),
                        label: field.value,
                        showId: false,
                      ),
                    ),
                ],
              ),
            ),
          TextField(
            controller: keyword,
            focusNode: keywordFocus,
            enabled: !locked,
            onSubmitted: (_) => _search(),
            decoration: const InputDecoration(labelText: 'Tìm thuốc để kê'),
          ),
          OutlinedButton(
            onPressed: locked || searching ? null : () => _search(),
            child: const Text('Tìm trong danh mục'),
          ),
          if (searching)
            const LinearProgressIndicator(semanticsLabel: 'Đang tìm thuốc'),
          if (searchError != null) RequestError(searchError!),
          if (!searching && searchError == null && searched && drugs.isEmpty)
            const Text('Không tìm thấy thuốc.'),
          for (final drug in drugs)
            Card(
              child: ListTile(
                title: Text(drug.name),
                subtitle: Text(
                  '${drug.unit} · Tồn: ${drug.stockQuantity} · Giá tham khảo: ${drug.price} VND',
                ),
                trailing: IconButton(
                  tooltip: 'Chọn ${drug.name}',
                  icon: const Icon(Icons.add),
                  onPressed:
                      locked || lines.any((line) => line.drug.id == drug.id)
                      ? null
                      : () {
                          final line = _DraftLine(drug);
                          setState(() => lines.add(line));
                          WidgetsBinding.instance.addPostFrameCallback((_) {
                            if (!mounted) return;
                            line.focus.requestFocus();
                            if (line.focus.context != null) {
                              Scrollable.ensureVisible(line.focus.context!);
                            }
                          });
                        },
                ),
              ),
            ),
          if (pages > 1)
            Wrap(
              spacing: 8,
              children: [
                TextButton(
                  onPressed: locked || searching || page == 0
                      ? null
                      : () => _search(target: page - 1, submit: false),
                  child: const Text('Trang thuốc trước'),
                ),
                TextButton(
                  onPressed: locked || searching || page + 1 >= pages
                      ? null
                      : () => _search(target: page + 1, submit: false),
                  child: const Text('Trang thuốc sau'),
                ),
              ],
            ),
          for (var i = 0; i < lines.length; i++)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(lines[i].drug.name),
                    TextField(
                      controller: lines[i].quantity,
                      focusNode: lines[i].focus,
                      enabled: !locked,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: 'Số lượng dòng ${i + 1}',
                        errorText:
                            errors['lines.$i.quantity'] ?? errors['lines.$i'],
                      ),
                    ),
                    TextField(
                      controller: lines[i].dosage,
                      focusNode: lines[i].dosageFocus,
                      enabled: !locked,
                      maxLength: 255,
                      decoration: InputDecoration(
                        labelText: 'Liều dùng dòng ${i + 1}',
                        errorText: errors['lines.$i.dosage'],
                      ),
                    ),
                    TextButton(
                      onPressed: locked
                          ? null
                          : () {
                              final removed = lines[i];
                              setState(() => lines.remove(removed));
                              WidgetsBinding.instance.addPostFrameCallback((_) {
                                removed.dispose();
                                if (!mounted) return;
                                final target = lines.isEmpty
                                    ? keywordFocus
                                    : lines.last.focus;
                                target.requestFocus();
                                if (target.context != null) {
                                  Scrollable.ensureVisible(target.context!);
                                }
                              });
                            },
                      child: Text('Bỏ dòng ${i + 1}'),
                    ),
                  ],
                ),
              ),
            ),
          Focus(
            focusNode: feedbackFocus,
            child: Semantics(
              liveRegion: true,
              child: Column(
                children: [
                  if (errors['lines'] != null) Text(errors['lines']!),
                  if (commandError != null) ...[
                    RequestError(commandError!),
                    const Text(
                      'Giữ nguyên nội dung và mã ý định. Lỗi kết nối không chứng minh đơn chưa được tạo; kiểm tra kết quả trước ý định khác.',
                    ),
                  ],
                  if (created != null)
                    SelectableText('Đã tạo đơn: ${created!.id}'),
                ],
              ),
            ),
          ),
          if (intent == null)
            FilledButton(
              onPressed: busy || widget.writer == null ? null : () => _submit(),
              child: const Text('Tạo đơn V1'),
            ),
          if (intent != null && retryAllowed && created == null)
            FilledButton(
              onPressed: busy ? null : () => _submit(retry: true),
              child: const Text('Thử lại cùng mã và nội dung'),
            ),
          if (intent != null && rejected && created == null)
            OutlinedButton(
              onPressed: busy
                  ? null
                  : () {
                      setState(() {
                        intent = null;
                        rejected = false;
                        retryAllowed = false;
                        commandError = null;
                      });
                      WidgetsBinding.instance.addPostFrameCallback((_) {
                        if (!mounted) return;
                        final key = labels.keys
                            .where(errors.containsKey)
                            .firstOrNull;
                        FocusNode target = focuses[key ?? 'recordId']!;
                        if (key == null) {
                          for (var i = 0; i < lines.length; i++) {
                            if (errors.containsKey('lines.$i.quantity') ||
                                errors.containsKey('lines.$i')) {
                              target = lines[i].focus;
                              break;
                            }
                            if (errors.containsKey('lines.$i.dosage')) {
                              target = lines[i].dosageFocus;
                              break;
                            }
                          }
                        }
                        target.requestFocus();
                        if (target.context != null) {
                          Scrollable.ensureVisible(target.context!);
                        }
                      });
                    },
              child: const Text('Sửa bản nháp bị từ chối'),
            ),
          if (busy && confirmation == null)
            const LinearProgressIndicator(semanticsLabel: 'Đang xử lý kê đơn'),
        ],
      ),
    );
  }
}
