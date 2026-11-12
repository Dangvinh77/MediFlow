import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/reference_widgets.dart';
import 'pharmacy_request_error.dart';
import '../domain/prescribing_source.dart';
import '../domain/prescription_commands.dart';

class PrescriptionSourceFields extends ConsumerStatefulWidget {
  const PrescriptionSourceFields({
    super.key,
    required this.reader,
    required this.locked,
    required this.onChanged,
    this.sourceLocked = false,
    this.initialPatientId,
    this.initialRecordId,
  }) : assert((initialPatientId == null) == (initialRecordId == null));
  final PrescribingSourceReader reader;

  /// Locks this context while leaving an allowed server-supplied price choice editable.
  final bool sourceLocked;
  final bool locked;
  final String? initialPatientId, initialRecordId;
  final ValueChanged<OutpatientPrescriptionContext?> onChanged;
  @override
  ConsumerState<PrescriptionSourceFields> createState() => _SourceFieldsState();
}

class _SourceFieldsState extends ConsumerState<PrescriptionSourceFields> {
  String? patientId, recordId, priceCode;
  PrescribingSource? source;
  List<ReferenceOption> records = [];
  int generation = 0, page = 0;
  bool loading = false, hasMore = false;
  Object? error;

  bool get _hasFixedSource => widget.initialRecordId != null;

  @override
  void initState() {
    super.initState();
    patientId = widget.initialPatientId;
    recordId = widget.initialRecordId;
    if (_hasFixedSource) {
      Future.microtask(_loadFixedSource);
    }
  }

  Future<void> _loadFixedSource() async {
    final id = widget.initialRecordId;
    final patient = widget.initialPatientId;
    if (id == null || patient == null || widget.locked) return;
    final ticket = ++generation;
    setState(() {
      loading = true;
      error = null;
      source = null;
      priceCode = null;
    });
    widget.onChanged(null);
    try {
      final result = await widget.reader.prescribingSource(id);
      if (!mounted || ticket != generation) return;
      if (result.recordId.toLowerCase() != id.toLowerCase() ||
          result.patientId.toLowerCase() != patient.toLowerCase()) {
        throw const FormatException('Hồ sơ không thuộc bệnh nhân đã chọn.');
      }
      setState(() {
        source = result;
        priceCode = result.priceCodes.length == 1
            ? result.priceCodes.single
            : null;
      });
      widget.onChanged(priceCode == null ? null : result.context(priceCode!));
    } catch (failure) {
      if (mounted && ticket == generation) setState(() => error = failure);
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
  }

  Future<void> _patient(String? id) async {
    if (widget.locked || widget.sourceLocked || id == patientId) return;
    generation++;
    setState(() {
      patientId = id;
      recordId = null;
      priceCode = null;
      source = null;
      records = [];
      page = 0;
      hasMore = false;
      error = null;
      loading = false;
    });
    widget.onChanged(null);
    if (id != null) await _records();
  }

  Future<void> _records({bool more = false}) async {
    final patient = patientId;
    if (patient == null || widget.locked || widget.sourceLocked || loading) {
      return;
    }
    final ticket = ++generation;
    final nextPage = more ? page + 1 : 0;
    setState(() {
      loading = true;
      error = null;
    });
    String? single;
    try {
      final result = await ref
          .read(referenceReaderProvider)
          .search(ReferenceKind.record, scope: patient, page: nextPage);
      if (!mounted || ticket != generation) return;
      setState(() {
        final merged = {
          for (final item in more ? records : <ReferenceOption>[])
            item.id: item,
          for (final item in result.items) item.id: item,
        };
        records = merged.values.toList();
        hasMore = result.hasMore;
        page = nextPage;
        if (records.length == 1 && !hasMore && recordId == null) {
          single = records.single.id;
        }
      });
    } catch (failure) {
      if (mounted && ticket == generation) setState(() => error = failure);
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
    if (single != null && mounted && ticket == generation) {
      await _record(single!);
    }
  }

  Future<void> _record(String id) async {
    if (widget.locked || widget.sourceLocked) return;
    final ticket = ++generation;
    final patient = patientId;
    setState(() {
      recordId = id;
      source = null;
      priceCode = null;
      error = null;
      loading = true;
    });
    widget.onChanged(null);
    try {
      final result = await widget.reader.prescribingSource(id);
      if (!mounted || ticket != generation) return;
      if (result.recordId != id || result.patientId != patient) {
        throw const FormatException('Hồ sơ không thuộc bệnh nhân đã chọn.');
      }
      setState(() {
        source = result;
        priceCode = result.priceCodes.length == 1
            ? result.priceCodes.single
            : null;
      });
      widget.onChanged(priceCode == null ? null : result.context(priceCode!));
    } catch (failure) {
      if (mounted && ticket == generation) setState(() => error = failure);
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
  }

  @override
  void dispose() {
    generation++;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      if (_hasFixedSource) ...[
        ReferenceLabel(
          kind: ReferenceKind.patient,
          id: patientId!,
          label: 'Bệnh nhân',
        ),
        ReferenceLabel(
          kind: ReferenceKind.record,
          id: recordId!,
          label: 'Hồ sơ khám',
        ),
      ] else
        ReferencePicker(
          kind: ReferenceKind.patient,
          label: 'Bệnh nhân',
          value: patientId,
          required: true,
          onChanged: widget.locked || widget.sourceLocked ? null : _patient,
        ),
      if (patientId == null) const LText('Chọn bệnh nhân để tải hồ sơ khám.'),
      if (!_hasFixedSource && records.isNotEmpty)
        InputDecorator(
          decoration: InputDecoration(
            labelText: AppL10n.text(context, 'Hồ sơ khám'),
            border: OutlineInputBorder(),
          ),
          child: records.length == 1 && !hasMore
              ? LText(records.single.name)
              : DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    isExpanded: true,
                    value: recordId,
                    hint: const LText('Chọn hồ sơ của bệnh nhân'),
                    items: records
                        .map(
                          (item) => DropdownMenuItem(
                            value: item.id,
                            child: LText(
                              item.name,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        )
                        .toList(),
                    onChanged: widget.locked || widget.sourceLocked || loading
                        ? null
                        : (id) {
                            if (id != null) _record(id);
                          },
                  ),
                ),
        ),
      if (hasMore)
        TextButton(
          onPressed: widget.locked || widget.sourceLocked || loading
              ? null
              : () => _records(more: true),
          child: const LText('Tải thêm hồ sơ'),
        ),
      if (loading)
        const LinearProgressIndicator(
          semanticsLabel: 'Đang lấy thông tin kê đơn',
        ),
      if (patientId != null && !loading && error == null && records.isEmpty)
        const LText('Bệnh nhân chưa có hồ sơ khám để kê đơn.'),
      if (error != null)
        PharmacyRequestError(
          error!,
          onRetry: widget.locked
              ? null
              : () {
                  if (_hasFixedSource) {
                    _loadFixedSource();
                  } else if (recordId != null) {
                    _record(recordId!);
                  } else {
                    _records();
                  }
                },
        ),
      if (source case final value?) ...[
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 8),
          child: LText(
            'Bác sĩ, khoa và lượt khám được lấy từ hồ sơ, không chọn riêng.',
          ),
        ),
        ReferenceLabel(
          kind: ReferenceKind.doctor,
          id: value.doctorId,
          label: 'Bác sĩ phụ trách',
        ),
        ReferenceLabel(
          kind: ReferenceKind.department,
          id: value.departmentId,
          label: 'Khoa khám',
        ),
        const LText('Đã liên kết lượt khám ngoại trú từ hồ sơ.'),
        if (value.priceCodes.length > 1)
          const LText(
            'Nếu chưa rõ nhóm giá, hãy hỏi bộ phận thu ngân trước khi chọn.',
          ),
        if (value.priceCodes.length > 1)
          DropdownButtonFormField<String>(
            initialValue: priceCode,
            decoration: InputDecoration(
              labelText: AppL10n.text(context, 'Nhóm giá thuốc'),
            ),
            items: [
              for (var index = 0; index < value.priceCodes.length; index++)
                DropdownMenuItem(
                  value: value.priceCodes[index],
                  child: LText(
                    value.priceCodes[index] == 'RX-DEFAULT'
                        ? 'Giá thuốc thông thường'
                        : 'Nhóm giá thuốc ${index + 1}',
                  ),
                ),
            ],
            onChanged: widget.locked
                ? null
                : (code) {
                    setState(() => priceCode = code);
                    widget.onChanged(code == null ? null : value.context(code));
                  },
          ),
      ],
    ],
  );
}
