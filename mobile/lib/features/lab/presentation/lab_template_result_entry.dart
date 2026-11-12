import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';

import '../domain/lab_catalog.dart';
import '../domain/lab_workflow.dart';

class LabTemplateResultEntry extends StatefulWidget {
  const LabTemplateResultEntry({
    super.key,
    required this.template,
    this.enabled = true,
    this.onDraftChanged,
  });

  final LabResultTemplate template;
  final bool enabled;
  final ValueChanged<bool>? onDraftChanged;

  @override
  State<LabTemplateResultEntry> createState() => LabTemplateResultEntryState();
}

class LabTemplateResultEntryState extends State<LabTemplateResultEntry> {
  static const _operators = ['<', '<=', '>', '>='];
  static const _noResultPrefix = 'Không có kết quả: ';
  late Map<String, _FieldControllers> _fields;
  String? _validationError;

  List<LabTemplateField> get _orderedFields =>
      [...widget.template.fields]..sort((a, b) {
        final order = a.position.compareTo(b.position);
        return order == 0 ? a.fieldCode.compareTo(b.fieldCode) : order;
      });

  bool get hasDraft => _fields.values.any((field) => field.hasDraft);

  @override
  void initState() {
    super.initState();
    _fields = _createFields(widget.template);
  }

  @override
  void didUpdateWidget(covariant LabTemplateResultEntry oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.template, widget.template)) {
      _disposeFields();
      _fields = _createFields(widget.template);
      _validationError = null;
    }
  }

  @override
  void dispose() {
    _disposeFields();
    super.dispose();
  }

  Map<String, _FieldControllers> _createFields(LabResultTemplate template) => {
    for (final field in template.fields) field.fieldCode: _FieldControllers(),
  };

  void _disposeFields() {
    for (final field in _fields.values) {
      field.dispose();
    }
  }

  /// Returns null and displays a message when required fields are incomplete.
  List<LabResultDraft>? buildResults() {
    String? error;
    final results = <LabResultDraft>[];
    for (final field in _orderedFields) {
      final input = _fields[field.fieldCode]!;
      final value = input.value.text.trim();
      final reason = input.reason.text.trim();
      if (value.isNotEmpty && reason.isNotEmpty) {
        error ??=
            'Nhập giá trị hoặc lý do không có kết quả, không nhập cả hai.';
        continue;
      }
      if (field.required && value.isEmpty && reason.isEmpty) {
        error ??= 'Nhập giá trị hoặc lý do không có kết quả.';
        continue;
      }
      if (value.isEmpty && reason.isEmpty) continue;

      final fieldValue = reason.isNotEmpty
          ? '$_noResultPrefix$reason'
          : '${field.allowsOperator ? input.operator : ''}$value';
      final range = _displayRange(field);
      if (field.label.length > 100) {
        error ??= 'Tên chỉ số vượt quá 100 ký tự.';
        continue;
      }
      if (fieldValue.length > 50) {
        error ??= 'Giá trị hoặc lý do vượt quá 50 ký tự.';
        continue;
      }
      if ((field.unit?.length ?? 0) > 20) {
        error ??= 'Đơn vị vượt quá 20 ký tự.';
        continue;
      }
      if ((range?.length ?? 0) > 50) {
        error ??= 'Khoảng tham chiếu vượt quá 50 ký tự.';
        continue;
      }
      results.add(
        LabResultDraft(
          indicator: field.label,
          value: fieldValue,
          unit: field.unit == null || field.unit!.isEmpty ? null : field.unit,
          referenceRange: range,
        ),
      );
    }
    if (error == null && results.isEmpty) {
      error = 'Nhập ít nhất một kết quả xét nghiệm.';
    }
    if (error != null) {
      setState(() => _validationError = error);
      return null;
    }
    setState(() => _validationError = null);
    return results;
  }

  @override
  Widget build(BuildContext context) {
    final shownRanges = [
      for (final field in _orderedFields)
        if (_displayRange(field) != null) ?_reference(field),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (shownRanges.any((range) => !range.reviewed))
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: LText('Khoảng tham chiếu demo, chưa được duyệt lâm sàng'),
          ),
        for (final field in _orderedFields) _fieldCard(field),
        if (_validationError case final error?)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: LText(
              error,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
      ],
    );
  }

  Widget _fieldCard(LabTemplateField field) {
    final input = _fields[field.fieldCode]!;
    final range = _displayRange(field);
    final usesOptions =
        (field.valueType == 'QUALITATIVE' ||
            field.valueType == 'SEMI_QUANTITATIVE') &&
        field.allowedOptions != null;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            LText(
              field.label,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            if (field.unit != null && field.unit!.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: InputDecorator(
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Đơn vị'),
                  ),
                  child: LText(field.unit!),
                ),
              ),
            if (range != null && range.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: InputDecorator(
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Khoảng tham chiếu'),
                  ),
                  child: LText(range),
                ),
              ),
            if (field.allowsOperator)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: DropdownButtonFormField<String>(
                  key: ValueKey('operator-${field.fieldCode}'),
                  initialValue: input.operator,
                  decoration: InputDecoration(
                    labelText: AppL10n.text(context, 'Toán tử'),
                  ),
                  items: [
                    const DropdownMenuItem(
                      value: '',
                      child: LText('Không dùng'),
                    ),
                    for (final operator in _operators)
                      DropdownMenuItem(value: operator, child: LText(operator)),
                  ],
                  onChanged: widget.enabled
                      ? (value) {
                          if (value == null) return;
                          setState(() => input.operator = value);
                          _notifyDraftChanged();
                        }
                      : null,
                ),
              ),
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: usesOptions
                  ? DropdownButtonFormField<String>(
                      key: ValueKey('value-${field.fieldCode}'),
                      initialValue: input.value.text,
                      decoration: InputDecoration(
                        labelText: AppL10n.text(
                          context,
                          'Kết quả${field.required ? ' *' : ''}',
                        ),
                      ),
                      items: [
                        if (!field.allowedOptions!.contains(''))
                          const DropdownMenuItem(
                            value: '',
                            child: LText('Chọn kết quả'),
                          ),
                        for (final option in field.allowedOptions!)
                          DropdownMenuItem(value: option, child: LText(option)),
                      ],
                      onChanged: widget.enabled
                          ? (value) {
                              input.value.text = value ?? '';
                              setState(() => _validationError = null);
                              _notifyDraftChanged();
                            }
                          : null,
                    )
                  : TextField(
                      key: ValueKey('value-${field.fieldCode}'),
                      controller: input.value,
                      enabled: widget.enabled,
                      maxLength: 50,
                      keyboardType: field.valueType == 'QUANTITATIVE'
                          ? const TextInputType.numberWithOptions(
                              decimal: true,
                              signed: true,
                            )
                          : TextInputType.text,
                      minLines: field.valueType == 'TEXT' ? 3 : 1,
                      maxLines: field.valueType == 'TEXT' ? null : 1,
                      decoration: InputDecoration(
                        labelText: AppL10n.text(
                          context,
                          'Kết quả${field.required ? ' *' : ''}',
                        ),
                        border: const OutlineInputBorder(),
                      ),
                      onChanged: (_) {
                        setState(() => _validationError = null);
                        _notifyDraftChanged();
                      },
                    ),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: TextField(
                key: ValueKey('reason-${field.fieldCode}'),
                controller: input.reason,
                enabled: widget.enabled,
                maxLength: 50 - _noResultPrefix.length,
                decoration: InputDecoration(
                  labelText: AppL10n.text(context, 'Lý do không có kết quả'),
                  border: OutlineInputBorder(),
                ),
                onChanged: (_) {
                  setState(() => _validationError = null);
                  _notifyDraftChanged();
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  LabReferenceRange? _reference(LabTemplateField field) {
    if (field.ranges.isEmpty) return null;
    for (final range in field.ranges) {
      if (range.sex == null &&
          range.ageMinDays == null &&
          range.ageMaxDays == null) {
        return range;
      }
    }
    return field.ranges.first;
  }

  String? _displayRange(LabTemplateField field) {
    final range = _reference(field);
    if (range == null || (range.low == null && range.high == null)) return null;
    return '${range.low ?? ''}–${range.high ?? ''}';
  }

  void _notifyDraftChanged() => widget.onDraftChanged?.call(hasDraft);
}

class _FieldControllers {
  final value = TextEditingController();
  final reason = TextEditingController();
  String operator = '';

  bool get hasDraft => value.text.isNotEmpty || reason.text.isNotEmpty;

  void dispose() {
    value.dispose();
    reason.dispose();
  }
}
