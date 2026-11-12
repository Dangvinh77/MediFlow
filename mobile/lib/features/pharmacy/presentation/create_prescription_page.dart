import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import 'pharmacy_request_error.dart';
import '../domain/dosage_schedule.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/prescription.dart';
import '../domain/prescription_commands.dart';
import '../domain/prescribing_source.dart';
import 'dosage_schedule_sheet.dart';
import 'pharmacy_boundary.dart';
import 'pharmacy_date_text.dart';
import 'pharmacy_status_text.dart';
import 'prescription_page.dart';
import 'providers.dart';
import 'prescription_source_fields.dart';

/// Pharmacy-owned panel that the app root can place in the medical-record slot.
class RecordPrescriptionPanel extends ConsumerStatefulWidget {
  const RecordPrescriptionPanel({
    super.key,
    required this.recordId,
    required this.patientId,
    required this.canCreate,
    this.onCreated,
  });

  final String recordId, patientId;
  final bool canCreate;
  final ValueChanged<Prescription>? onCreated;

  @override
  ConsumerState<RecordPrescriptionPanel> createState() =>
      _RecordPrescriptionPanelState();
}

class _RecordPrescriptionPanelState
    extends ConsumerState<RecordPrescriptionPanel> {
  GlobalKey<_RecordPrescriptionResultsState> _resultsKey =
      GlobalKey<_RecordPrescriptionResultsState>();
  Object? _resultsIdentity;
  bool _hasObservedResultsIdentity = false;

  void _created(Prescription prescription) {
    _resultsKey.currentState?._created(prescription);
    widget.onCreated?.call(prescription);
  }

  @override
  Widget build(BuildContext context) {
    final currentIdentity = ref.watch(pharmacyAccessProvider)?.sessionIdentity;
    if (!_hasObservedResultsIdentity || currentIdentity != _resultsIdentity) {
      _hasObservedResultsIdentity = true;
      _resultsIdentity = currentIdentity;
      _resultsKey = GlobalKey<_RecordPrescriptionResultsState>();
    }
    return PharmacyBoundary(
      builder: (repository) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          CreatePrescriptionPage(
            key: ValueKey((widget.recordId, widget.patientId)),
            initialPatientId: widget.patientId,
            initialRecordId: widget.recordId,
            canCreate: widget.canCreate,
            embedded: true,
            onCreated: _created,
          ),
          _RecordPrescriptionResults(
            key: _resultsKey,
            repository: repository,
            recordId: widget.recordId,
            patientId: widget.patientId,
          ),
        ],
      ),
    );
  }
}

class _RecordPrescriptionResults extends ConsumerStatefulWidget {
  const _RecordPrescriptionResults({
    super.key,
    required this.repository,
    required this.recordId,
    required this.patientId,
  });

  final PharmacyRepository repository;
  final String recordId, patientId;

  @override
  ConsumerState<_RecordPrescriptionResults> createState() =>
      _RecordPrescriptionResultsState();
}

class _RecordPrescriptionResultsState
    extends ConsumerState<_RecordPrescriptionResults> {
  final List<Prescription> prescriptions = [];
  Prescription? latestReceipt;
  Object? error;
  bool loading = false, hasMore = false;
  int page = 0, generation = 0;
  Object? identity;

  @override
  void initState() {
    super.initState();
    identity = ref.read(pharmacyAccessProvider)?.sessionIdentity;
    Future.microtask(_load);
  }

  Future<void> _load({bool more = false}) async {
    if (loading) return;
    final accessIdentity = ref.read(pharmacyAccessProvider)?.sessionIdentity;
    if (accessIdentity == null || accessIdentity != identity) return;
    final ticket = ++generation;
    final requestedPage = more ? page + 1 : 0;
    setState(() {
      loading = true;
      error = null;
      if (!more) {
        page = 0;
        hasMore = false;
      }
    });
    try {
      final result = await SearchPrescriptions(widget.repository)(
        patientId: widget.patientId,
        page: requestedPage,
      );
      if (!mounted ||
          ticket != generation ||
          ref.read(pharmacyAccessProvider)?.sessionIdentity != identity) {
        return;
      }
      final matches = result.items.where(
        (prescription) =>
            prescription.patientId.toLowerCase() ==
                widget.patientId.toLowerCase() &&
            prescription.recordId?.toLowerCase() ==
                widget.recordId.toLowerCase(),
      );
      final merged = <String, Prescription>{
        if (more)
          for (final item in prescriptions) item.id.toLowerCase(): item,
        for (final item in matches) item.id.toLowerCase(): item,
      };
      final receipt = latestReceipt;
      if (receipt != null &&
          receipt.patientId.toLowerCase() == widget.patientId.toLowerCase() &&
          receipt.recordId?.toLowerCase() == widget.recordId.toLowerCase()) {
        merged[receipt.id.toLowerCase()] = receipt;
      }
      setState(() {
        prescriptions
          ..clear()
          ..addAll(merged.values);
        page = result.number;
        hasMore = result.hasNext;
        loading = false;
      });
    } catch (failure) {
      if (mounted &&
          ticket == generation &&
          ref.read(pharmacyAccessProvider)?.sessionIdentity == identity) {
        setState(() {
          error = failure;
          loading = false;
        });
      }
    }
  }

  void _created(Prescription prescription) {
    if (!mounted ||
        ref.read(pharmacyAccessProvider)?.sessionIdentity != identity ||
        prescription.patientId.toLowerCase() !=
            widget.patientId.toLowerCase() ||
        prescription.recordId?.toLowerCase() != widget.recordId.toLowerCase()) {
      return;
    }
    setState(() {
      latestReceipt = prescription;
      prescriptions
        ..removeWhere(
          (item) => item.id.toLowerCase() == prescription.id.toLowerCase(),
        )
        ..insert(0, prescription);
    });
    _load();
  }

  /// Prescribers do not see money; other record readers keep the amount.
  bool get _showAmount =>
      ref.watch(pharmacyAccessProvider)?.canPrescribe != true;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      if (loading && prescriptions.isEmpty)
        const LinearProgressIndicator(
          semanticsLabel: 'Đang tải đơn thuốc của hồ sơ',
        ),
      if (error != null)
        PharmacyRequestError(error!, onRetry: loading ? null : _load),
      if (error != null && prescriptions.isEmpty)
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: loading ? null : _load,
            icon: const Icon(Icons.refresh),
            label: const LText('Tải lại đơn thuốc'),
          ),
        ),
      if (!loading && error == null && prescriptions.isEmpty)
        LText(
          hasMore
              ? 'Chưa thấy đơn thuốc của hồ sơ trong các trang đã tải.'
              : 'Chưa có đơn thuốc gắn với hồ sơ này.',
        ),
      for (final prescription in prescriptions)
        Card(
          child: ListTile(
            title: LText(
              'Đơn thuốc · ${pharmacyDate(prescription.prescribedDate)}',
            ),
            subtitle: LText(
              '${prescriptionStatusText(prescription.status)}${_showAmount ? ' · ${prescription.totalAmount} đồng' : ''}\nMã đơn: ${prescription.id}',
            ),
            trailing: IconButton(
              tooltip: AppL10n.text(context, 'Xem chi tiết đơn thuốc'),
              icon: const Icon(Icons.chevron_right),
              onPressed: loading
                  ? null
                  : () => Navigator.of(context).push<void>(
                      MaterialPageRoute(
                        builder: (_) => PrescriptionDetailPage(
                          prescriptionId: prescription.id,
                        ),
                      ),
                    ),
            ),
          ),
        ),
      if (hasMore)
        TextButton(
          onPressed: loading ? null : () => _load(more: true),
          child: const LText('Tải thêm đơn thuốc'),
        ),
    ],
  );
}

class CreatePrescriptionPage extends ConsumerWidget {
  const CreatePrescriptionPage({
    super.key,
    this.onCreated,
    this.initialPatientId,
    this.initialRecordId,
    this.canCreate = true,
    this.embedded = false,
  }) : assert((initialPatientId == null) == (initialRecordId == null));

  final ValueChanged<Prescription>? onCreated;
  final String? initialPatientId, initialRecordId;
  final bool canCreate, embedded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if ((initialPatientId == null) != (initialRecordId == null)) {
      return const Center(
        child: LText('Không thể kê đơn khi thiếu bệnh nhân hoặc hồ sơ nguồn.'),
      );
    }
    return PharmacyBoundary(
      builder: (repository) {
        final access = ref.watch(pharmacyAccessProvider);
        if (access?.canPrescribe != true) {
          return const Center(child: LText('Bạn không có quyền kê đơn.'));
        }
        if (ref.watch(pharmacyModeProvider) != PharmacyMode.outpatient) {
          return const Center(
            child: LText('Chức năng kê đơn ngoại trú chưa sẵn sàng.'),
          );
        }
        if (initialRecordId != null && repository is! PrescribingSourceReader) {
          return const Center(
            child: LText('Chưa thể xác minh hồ sơ nguồn để kê đơn.'),
          );
        }
        final writer = ref.watch(outpatientPrescriptionWriterProvider);
        return _CreatePrescription(
          key: ValueKey((
            writer,
            access!.sessionIdentity,
            initialPatientId,
            initialRecordId,
          )),
          repository: repository,
          writer: writer,
          sourceReader: repository is PrescribingSourceReader
              ? repository as PrescribingSourceReader
              : null,
          onCreated: onCreated,
          initialPatientId: initialPatientId,
          initialRecordId: initialRecordId,
          canCreate: canCreate,
          embedded: embedded,
        );
      },
    );
  }
}

class _DraftLine {
  _DraftLine(this.drug);
  final Drug drug;
  final quantity = TextEditingController(text: '1');
  final dosage = TextEditingController();
  final note = TextEditingController();
  final focus = FocusNode();
  final dosageFocus = FocusNode();
  final noteFocus = FocusNode();
  DosageSchedule? schedule;
  void dispose() {
    quantity.dispose();
    dosage.dispose();
    note.dispose();
    focus.dispose();
    dosageFocus.dispose();
    noteFocus.dispose();
  }
}

class _CreatePrescription extends StatefulWidget {
  const _CreatePrescription({
    super.key,
    required this.repository,
    required this.writer,
    this.sourceReader,
    this.onCreated,
    this.initialPatientId,
    this.initialRecordId,
    this.canCreate = true,
    this.embedded = false,
  });
  final PharmacyRepository repository;
  final OutpatientPrescriptionWriter? writer;
  final PrescribingSourceReader? sourceReader;
  final ValueChanged<Prescription>? onCreated;
  final String? initialPatientId, initialRecordId;
  final bool canCreate, embedded;
  @override
  State<_CreatePrescription> createState() => _CreatePrescriptionState();
}

class _CreatePrescriptionState extends State<_CreatePrescription> {
  static String _fieldMessage(String key) {
    if (key == 'lines') return 'Vui lòng thêm ít nhất một thuốc vào đơn.';
    if (key.startsWith('lines.') || key.startsWith('lines[')) {
      return 'Vui lòng kiểm tra thuốc, số lượng và cách dùng ở dòng này.';
    }
    return switch (key) {
      'patientId' => 'Vui lòng chọn bệnh nhân.',
      'recordId' => 'Vui lòng chọn hồ sơ khám của bệnh nhân.',
      'doctorId' => 'Vui lòng kiểm tra bác sĩ kê đơn.',
      'departmentId' => 'Vui lòng kiểm tra khoa khám.',
      'careEpisodeId' => 'Vui lòng kiểm tra lượt khám.',
      'priceCode' => 'Vui lòng chọn nhóm giá thuốc.',
      'sourceReference' => 'Vui lòng kiểm tra thông tin từ hồ sơ.',
      'prescribedDate' => 'Vui lòng chọn ngày kê đơn hợp lệ.',
      _ => 'Vui lòng kiểm tra thông tin này.',
    };
  }

  static const labels = {
    'patientId': 'Bệnh nhân',
    'departmentId': 'Khoa khám',
    'doctorId': 'Bác sĩ kê đơn',
    'recordId': 'Hồ sơ khám',
    'careEpisodeId': 'Lượt khám',
    'priceCode': 'Nhóm giá thuốc',
    'sourceReference': 'Thông tin từ hồ sơ',
    'prescribedDate': 'Ngày kê đơn',
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
  bool sourceConfirmed = false;

  @override
  void initState() {
    super.initState();
    if (widget.sourceReader != null) {
      final now = DateTime.now();
      fields['prescribedDate']!.text =
          '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    }
  }

  void _sourceChanged(OutpatientPrescriptionContext? source) {
    if (busy || intent != null || created != null) return;
    setState(() {
      sourceConfirmed = source != null;
      final values = {
        'patientId': source?.patientId,
        'recordId': source?.recordId,
        'doctorId': source?.doctorId,
        'departmentId': source?.departmentId,
        'careEpisodeId': source?.careEpisodeId,
        'priceCode': source?.priceCode,
        'sourceReference': source?.sourceReference,
      };
      for (final entry in values.entries) {
        fields[entry.key]!.text = entry.value ?? '';
        errors.remove(entry.key);
      }
      commandError = null;
    });
  }

  void _changeReference(String key, String? id) {
    setState(() {
      final previous = fields[key]!.text;
      fields[key]!.text = id ?? '';
      errors.remove(key);
      if (previous == id) return;
      if (key == 'patientId') {
        for (final dependent in ['recordId', 'careEpisodeId']) {
          fields[dependent]!.clear();
          errors.remove(dependent);
        }
      } else if (key == 'departmentId') {
        fields['doctorId']!.clear();
        errors.remove('doctorId');
      }
    });
  }

  ReferenceKind _referenceKind(String key) => switch (key) {
    'patientId' => ReferenceKind.patient,
    'departmentId' => ReferenceKind.department,
    'doctorId' => ReferenceKind.doctor,
    'recordId' => ReferenceKind.record,
    'careEpisodeId' => ReferenceKind.appointment,
    _ => throw ArgumentError.value(key, 'key', 'Not a reference field'),
  };

  String? _referenceScope(String key) => switch (key) {
    'doctorId' =>
      fields['departmentId']!.text.isEmpty
          ? null
          : fields['departmentId']!.text,
    'recordId' || 'careEpisodeId' =>
      fields['patientId']!.text.isEmpty ? null : fields['patientId']!.text,
    _ => null,
  };

  bool _requiresPatientScope(String key) =>
      (key == 'recordId' || key == 'careEpisodeId') &&
      fields['patientId']!.text.isEmpty;

  Widget _referenceField(MapEntry<String, String> field, bool locked) {
    final key = field.key;
    final patientScoped = key == 'recordId' || key == 'careEpisodeId';
    final departmentScoped = key == 'doctorId';
    final needsScope = patientScoped
        ? _requiresPatientScope(key)
        : departmentScoped && fields['departmentId']!.text.isEmpty;
    return Focus(
      key: ValueKey('prescription-reference-$key'),
      focusNode: focuses[key],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ReferencePicker(
            kind: _referenceKind(key),
            label: switch (key) {
              'patientId' => 'Bệnh nhân',
              'departmentId' => 'Khoa kê đơn',
              'doctorId' => 'Bác sĩ kê đơn',
              'recordId' => 'Hồ sơ bệnh án',
              _ => 'Lượt khám ngoại trú',
            },
            value: fields[key]!.text.isEmpty ? null : fields[key]!.text,
            scope: _referenceScope(key),
            required: true,
            onChanged: locked || needsScope
                ? null
                : (id) => _changeReference(key, id),
          ),
          if (needsScope)
            Padding(
              padding: const EdgeInsets.only(left: 12, bottom: 4),
              child: LText(
                patientScoped
                    ? 'Chọn bệnh nhân trước để tìm hồ sơ hoặc lượt khám.'
                    : 'Chọn khoa trước để tìm bác sĩ.',
              ),
            ),
          if (errors[key] != null)
            Padding(
              padding: const EdgeInsets.only(left: 12, bottom: 4),
              child: LText(
                errors[key]!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
        ],
      ),
    );
  }

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

  Future<void> _chooseDosage(_DraftLine line) async {
    final chosen = await showDosageScheduleSheet(
      context,
      drugName: line.drug.name,
      unit: line.drug.unit,
      stock: line.drug.stockQuantity,
      initial: line.schedule,
    );
    if (chosen == null || !mounted || !lines.contains(line)) return;
    final unit = line.drug.unit;
    final previous = line.schedule?.suggestedQuantity(unit);
    final next = chosen.suggestedQuantity(unit);
    setState(() {
      line.schedule = chosen;
      line.dosage.text = chosen.format(unit);
      // A manual quantity survives reopening; only a changed calculation replaces it.
      if (next != null && next != previous) line.quantity.text = '$next';
      errors.removeWhere((key, _) => key.startsWith('lines'));
    });
  }

  Widget _lineRow(int i, bool locked) {
    final line = lines[i];
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: LText(
                  '${i + 1}. ${line.drug.name}',
                  style: theme.textTheme.titleSmall,
                ),
              ),
              IconButton(
                tooltip: AppL10n.text(context, 'Bỏ dòng ${i + 1}'),
                icon: const Icon(Icons.close),
                onPressed: locked
                    ? null
                    : () {
                        setState(() => lines.remove(line));
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          line.dispose();
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
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              spacing: 10,
              children: [
                TextField(
                  controller: line.quantity,
                  focusNode: line.focus,
                  enabled: !locked,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Số lượng dòng ${i + 1}'),
                    suffixText: line.drug.unit,
                    isDense: true,
                    errorText: AppL10n.text(
                      context,
                      errors['lines.$i.quantity'] ?? errors['lines.$i'],
                    ),
                  ),
                ),
                TextField(
                  controller: line.dosage,
                  focusNode: line.dosageFocus,
                  enabled: !locked,
                  readOnly: true,
                  minLines: 1,
                  maxLines: 3,
                  onTap: () => _chooseDosage(line),
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Cách dùng dòng ${i + 1}'),
                    hintText: AppL10n.text(context, 'Chạm để chọn cách dùng'),
                    prefixIcon: const Icon(Icons.lock_outline, size: 18),
                    isDense: true,
                    errorText: AppL10n.text(context, errors['lines.$i.dosage']),
                  ),
                ),
                TextField(
                  controller: line.note,
                  focusNode: line.noteFocus,
                  enabled: !locked,
                  maxLength: 500,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Ghi chú dòng ${i + 1}'),
                    isDense: true,
                    counterText: '',
                    errorText: AppL10n.text(context, errors['lines.$i.note']),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
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
            note: line.note.text.trim().isEmpty ? null : line.note.text.trim(),
          ),
        )
        .toList(),
  );

  Future<void> _submit({bool retry = false}) async {
    if ((!retry && !widget.canCreate) ||
        busy ||
        widget.writer == null ||
        created != null) {
      return;
    }
    if (retry) {
      if (!retryAllowed || intent == null) return;
    } else {
      if (intent != null) return;
      if (widget.sourceReader != null && !sourceConfirmed) {
        setState(
          () => commandError = const FormatException(
            'Chọn bệnh nhân và chờ xác nhận thông tin hồ sơ khám trước khi kê đơn.',
          ),
        );
        feedbackFocus.requestFocus();
        return;
      }
      final command = _command();
      final validation = command.errors();
      setState(
        () => errors = {
          for (final key in validation.keys) key: _fieldMessage(key),
        },
      );
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
        title: LText(retry ? 'Xác nhận gửi lại đơn' : 'Xác nhận kê đơn'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              ReferenceLabel(
                kind: ReferenceKind.record,
                id: current.command.context.recordId,
                label: 'Hồ sơ',
              ),
              ReferenceLabel(
                kind: ReferenceKind.patient,
                id: current.command.context.patientId,
                label: 'Bệnh nhân',
              ),
              ReferenceLabel(
                kind: ReferenceKind.doctor,
                id: current.command.context.doctorId,
                label: 'Bác sĩ kê đơn',
              ),
              ReferenceLabel(
                kind: ReferenceKind.department,
                id: current.command.context.departmentId,
                label: 'Khoa',
              ),
              if (widget.sourceReader != null)
                const LText('Đã liên kết lượt khám ngoại trú từ hồ sơ.')
              else
                ReferenceLabel(
                  kind: ReferenceKind.appointment,
                  id: current.command.context.careEpisodeId,
                  label: 'Lượt khám',
                ),
              LText(
                '${current.command.lines.length} thuốc trong đơn. Thông tin sẽ được kiểm tra khi tạo đơn.',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const LText('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const LText('Xác nhận kê đơn'),
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
    if (!retry && !widget.canCreate) {
      setState(() {
        busy = false;
        intent = null;
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
              ): _fieldMessage(
                entry.key,
              ),
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
    final locked =
        busy || intent != null || created != null || !widget.canCreate;
    final holdWorkspace =
        intent != null && created == null && !rejected && confirmation == null;
    if (!widget.canCreate && created == null && !holdWorkspace) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: LText('Hồ sơ đã hoàn tất; đơn thuốc chỉ được xem.'),
      );
    }
    final fixedSource = widget.initialRecordId != null;
    final form = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LText(
          'Kê đơn ngoại trú',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        LText(
          widget.sourceReader != null
              ? 'Chọn bệnh nhân và hồ sơ khám. Hệ thống tự lấy thông tin kê đơn liên quan.'
              : 'Chọn bệnh nhân, hồ sơ khám và bác sĩ theo thông tin đã được xác nhận. Kiểm tra đầy đủ lượt khám và nhóm thu trước khi tạo đơn.',
        ),
        if (widget.writer == null)
          const LText(
            'Chức năng tạo đơn hiện chưa sẵn sàng. Vui lòng thử lại sau.',
          ),
        if (widget.sourceReader != null)
          PrescriptionSourceFields(
            reader: widget.sourceReader!,
            locked: locked,
            sourceLocked: fixedSource,
            initialPatientId: widget.initialPatientId,
            initialRecordId: widget.initialRecordId,
            onChanged: _sourceChanged,
          ),
        for (final field in labels.entries.where(
          (field) =>
              widget.sourceReader == null || field.key == 'prescribedDate',
        ))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child:
                const {
                  'patientId',
                  'departmentId',
                  'doctorId',
                  'recordId',
                  'careEpisodeId',
                }.contains(field.key)
                ? _referenceField(field, locked)
                : TextField(
                    controller: fields[field.key],
                    focusNode: focuses[field.key],
                    enabled: !locked,
                    decoration: InputDecoration(
                      labelText: AppL10n.text(context, field.value),
                      helperText: AppL10n.text(
                        context,
                        field.key == 'prescribedDate'
                            ? 'Nhập theo dạng năm-tháng-ngày.'
                            : null,
                      ),
                      errorText: AppL10n.text(context, errors[field.key]),
                    ),
                  ),
          ),
        TextField(
          controller: keyword,
          focusNode: keywordFocus,
          enabled: !locked,
          onSubmitted: (_) => _search(),
          decoration: InputDecoration(
            labelText: AppL10n.text(context, 'Tìm thuốc để kê'),
          ),
        ),
        OutlinedButton(
          onPressed: locked || searching ? null : () => _search(),
          child: const LText('Tìm trong danh mục'),
        ),
        if (searching)
          const LinearProgressIndicator(semanticsLabel: 'Đang tìm thuốc'),
        if (searchError != null) PharmacyRequestError(searchError!),
        if (!searching && searchError == null && searched && drugs.isEmpty)
          const LText('Không tìm thấy thuốc.'),
        for (final drug in drugs)
          Card(
            child: ListTile(
              title: LText(drug.name),
              subtitle: LText('${drug.unit} · Còn: ${drug.stockQuantity}'),
              trailing: IconButton(
                tooltip: AppL10n.text(context, 'Chọn ${drug.name}'),
                icon: const Icon(Icons.add),
                onPressed:
                    locked ||
                        drug.stockQuantity == 0 ||
                        lines.any((line) => line.drug.id == drug.id)
                    ? null
                    : () {
                        final line = _DraftLine(drug);
                        setState(() => lines.add(line));
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          if (!mounted) return;
                          if (line.focus.context != null) {
                            Scrollable.ensureVisible(line.focus.context!);
                          }
                          _chooseDosage(line);
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
                child: const LText('Trang thuốc trước'),
              ),
              TextButton(
                onPressed: locked || searching || page + 1 >= pages
                    ? null
                    : () => _search(target: page + 1, submit: false),
                child: const LText('Trang thuốc sau'),
              ),
            ],
          ),
        if (lines.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Semantics(
              label: 'Toa thuốc',
              container: true,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border.all(color: Theme.of(context).dividerColor),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (var i = 0; i < lines.length; i++) ...[
                      if (i > 0) const Divider(height: 1),
                      _lineRow(i, locked),
                    ],
                  ],
                ),
              ),
            ),
          ),
        Focus(
          focusNode: feedbackFocus,
          child: Semantics(
            liveRegion: true,
            child: Column(
              children: [
                if (errors['lines'] != null) LText(errors['lines']!),
                if (commandError != null) ...[
                  PharmacyRequestError(commandError!),
                  if (intent != null)
                    const LText(
                      'Chưa rõ đơn đã được tạo hay chưa. Vui lòng kiểm tra đơn trước khi gửi lần nữa.',
                    ),
                ],
                if (created != null)
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const LText('Đã tạo đơn thuốc thành công.'),
                      if (created!.recordId case final recordId?)
                        ReferenceLabel(
                          kind: ReferenceKind.record,
                          id: recordId,
                          label: 'Hồ sơ đơn thuốc',
                        ),
                      LText('Mã đơn thuốc: ${created!.id}'),
                      LText(
                        'Trạng thái: ${prescriptionStatusText(created!.status)}',
                      ),
                      LText('Số loại thuốc: ${created!.lines.length}'),
                    ],
                  ),
              ],
            ),
          ),
        ),
        if (intent == null)
          FilledButton(
            onPressed: busy || widget.writer == null ? null : () => _submit(),
            child: const LText('Tạo đơn thuốc'),
          ),
        if (intent != null && retryAllowed && created == null)
          FilledButton(
            onPressed: busy ? null : () => _submit(retry: true),
            child: const LText('Thử gửi lại đơn này'),
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
                          if (errors.containsKey('lines.$i.note')) {
                            target = lines[i].noteFocus;
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
            child: const LText('Sửa bản nháp bị từ chối'),
          ),
        if (busy && confirmation == null)
          const LinearProgressIndicator(semanticsLabel: 'Đang xử lý kê đơn'),
      ],
    );
    final content = widget.embedded
        ? Padding(padding: const EdgeInsets.all(16), child: form)
        : SingleChildScrollView(padding: const EdgeInsets.all(16), child: form);
    return PopScope(
      canPop: !holdWorkspace,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && holdWorkspace) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: LText(
                'Kết quả kê đơn chưa rõ. Thử lại cùng yêu cầu trước khi rời hồ sơ.',
              ),
            ),
          );
        }
      },
      child: content,
    );
  }
}
