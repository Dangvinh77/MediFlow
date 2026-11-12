import 'package:flutter/material.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/medical_ui.dart';
import '../domain/exam_lifecycle.dart';
import '../domain/medical_record.dart';

Future<MedicalRecord?> showCloseRecordSheet(
  BuildContext context, {
  required MedicalRecord record,
  required ExamLifecycleRepository repository,
}) => showModalBottomSheet<MedicalRecord>(
  context: context,
  isScrollControlled: true,
  builder: (_) => CloseRecordSheet(record: record, repository: repository),
);

/// Ends a visit with a reason. A lost response resends the same key and body.
class CloseRecordSheet extends StatefulWidget {
  const CloseRecordSheet({
    super.key,
    required this.record,
    required this.repository,
  });
  final MedicalRecord record;
  final ExamLifecycleRepository repository;
  @override
  State<CloseRecordSheet> createState() => _CloseRecordSheetState();
}

class _CloseRecordSheetState extends State<CloseRecordSheet> {
  final _note = TextEditingController();
  String _reason = 'PATIENT_LEFT';
  String? _key, _message;
  bool _confirmed = false, _busy = false, _uncertain = false;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  bool get _ready => _note.text.trim().isNotEmpty && _confirmed && !_busy;

  Future<void> _submit() async {
    if (!_ready) return;
    final key = _key ??= newExamCommandKey();
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final updated = await widget.repository.closeWithoutConclusion(
        widget.record.id,
        reason: _reason,
        note: _note.text.trim(),
        idempotencyKey: key,
      );
      if (mounted) Navigator.of(context).pop(updated);
    } catch (cause) {
      if (!mounted) return;
      final unknown =
          cause is! ApiFailure ||
          cause.status == null ||
          cause.status == 408 ||
          cause.status! >= 500;
      setState(() {
        _busy = false;
        _uncertain = unknown;
        if (!unknown) _key = null;
        _message = unknown
            ? 'Kết quả chưa rõ. Gửi lại cùng yêu cầu, hoặc đóng và làm mới hồ sơ.'
            : cause.code == 'CLINICAL_LAB_ORDER_PENDING'
            ? 'Còn chỉ định xét nghiệm chưa xong. Hủy hoặc hoãn ở mục Chỉ định xét nghiệm rồi thử lại.'
            : cause.code == 'CLINICAL_LAB_ORDER_NOT_FOUND'
            ? 'Có chỉ định Lab chưa tiếp nhận. Rút chỉ định ở mục Chỉ định xét nghiệm rồi thử lại.'
            : cause.message;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final locked = _busy || _uncertain;
    return SafeArea(
      child: Padding(
        key: const Key('close-record-sheet'),
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          16 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Kết thúc lượt khám không kết luận',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: _reason,
                decoration: const InputDecoration(labelText: 'Lý do kết thúc'),
                items: [
                  for (final entry in recordClosureReasonLabels.entries)
                    DropdownMenuItem(
                      value: entry.key,
                      child: Text(entry.value),
                    ),
                ],
                onChanged: locked
                    ? null
                    : (value) => setState(() => _reason = value!),
              ),
              TextField(
                key: const Key('close-record-note'),
                controller: _note,
                enabled: !locked,
                maxLength: recordClosureNoteMax,
                maxLines: 3,
                decoration: const InputDecoration(
                  labelText: 'Ghi chú và dặn dò đã đưa',
                ),
                onChanged: (_) => setState(() {}),
              ),
              CheckboxListTile(
                key: const Key('close-record-confirm'),
                contentPadding: EdgeInsets.zero,
                value: _confirmed,
                title: const Text(
                  'Tôi xác nhận kết thúc lượt khám này. Lượt đã kết thúc không thể mở lại.',
                ),
                onChanged: locked
                    ? null
                    : (value) => setState(() => _confirmed = value!),
              ),
              if (_message != null) NoticeBox(_message!),
              if (_busy) const LinearProgressIndicator(),
              FilledButton(
                key: const Key('close-record-submit'),
                onPressed: _ready ? _submit : null,
                child: Text(
                  _uncertain ? 'Gửi lại cùng yêu cầu' : 'Kết thúc lượt khám',
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
