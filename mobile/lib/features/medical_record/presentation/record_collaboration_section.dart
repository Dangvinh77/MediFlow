import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../domain/exam_lifecycle.dart';
import '../domain/medical_record.dart';
import '../domain/record_collaboration.dart';
import 'medical_record_formatters.dart';

final recordCollaborationRepositoryProvider =
    Provider<RecordCollaborationRepository?>((ref) => null);

const _uuidPattern =
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

const _errorMessages = <String, String>{
  'HANDOVER_INVALID': 'Thông tin bàn giao không hợp lệ.',
  'HANDOVER_PENDING_EXISTS': 'Hồ sơ đang có một bàn giao chờ tiếp nhận.',
  'AMENDMENT_INVALID': 'Thông tin đính chính không hợp lệ.',
  'AMENDMENT_RECORD_OPEN':
      'Chỉ đính chính được hồ sơ đã kết thúc. Hồ sơ này vẫn đang mở.',
  'AMENDMENT_ALREADY_DECIDED': 'Đính chính này đã được quyết định trước đó.',
  'AMENDMENT_SELF_DECISION': 'Người đề nghị đính chính không được tự duyệt.',
  'AMENDMENT_AUTHOR_NOT_ELIGIBLE':
      'Người này không đủ điều kiện đề nghị đính chính hồ sơ.',
  'CONSULTATION_NOTE_INVALID': 'Ý kiến hội chẩn không hợp lệ.',
  'TREATMENT_COURSE_INVALID': 'Thông tin đợt điều trị không hợp lệ.',
};

String collaborationErrorMessage(Object error) {
  if (error is ApiFailure) {
    return _errorMessages[error.code] ??
        (error.status == 403
            ? 'Bạn không có quyền thực hiện thao tác này.'
            : error.message);
  }
  if (error is ArgumentError) return error.message.toString();
  if (error is CollaborationUnavailable) {
    return 'Tính năng này chưa được bật.';
  }
  return 'Không thể thực hiện thao tác. Vui lòng thử lại.';
}

bool _outcomeUnknown(Object error) {
  if (error is ApiFailure) {
    return error.status == null ||
        error.status == 408 ||
        (error.status ?? 0) >= 500;
  }
  return error is! ArgumentError && error is! CollaborationUnavailable;
}

class CommandField {
  const CommandField(
    this.id,
    this.label, {
    this.maxLength,
    this.maxLines = 1,
    this.choices,
    this.initialChoice,
  });
  final String id, label;
  final int? maxLength;
  final int maxLines;
  final Map<String, String>? choices;
  final String? initialChoice;
}

/// A command form that keeps one Idempotency-Key from the first attempt until
/// the outcome is known, and freezes the payload while the outcome is unknown.
class CommandForm extends StatefulWidget {
  const CommandForm({
    super.key,
    required this.prefix,
    required this.fields,
    required this.submitLabel,
    required this.validate,
    required this.onSubmit,
    required this.onDone,
    this.onCancel,
    this.submitKey,
  });

  final String prefix, submitLabel;
  final List<CommandField> fields;
  final String? Function(Map<String, String> values) validate;
  final Future<void> Function(Map<String, String> values, String key) onSubmit;
  final VoidCallback onDone;
  final VoidCallback? onCancel;
  final Key? submitKey;

  @override
  State<CommandForm> createState() => _CommandFormState();
}

class _CommandFormState extends State<CommandForm> {
  late final Map<String, TextEditingController> _controllers = {
    for (final field in widget.fields)
      if (field.choices == null) field.id: TextEditingController(),
  };
  late final Map<String, String?> _choices = {
    for (final field in widget.fields)
      if (field.choices != null) field.id: field.initialChoice,
  };
  String? _key, _message;
  bool _busy = false, _locked = false;

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Map<String, String> _values() => {
    for (final entry in _controllers.entries) entry.key: entry.value.text,
    for (final entry in _choices.entries) entry.key: entry.value ?? '',
  };

  Future<void> _submit() async {
    if (_busy) return;
    final values = _values();
    if (_key == null) {
      final error = widget.validate(values);
      if (error != null) {
        setState(() => _message = error);
        return;
      }
      _key = newExamCommandKey();
    }
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      await widget.onSubmit(values, _key!);
      _key = null;
      _locked = false;
      if (mounted) widget.onDone();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        if (_outcomeUnknown(error)) {
          _locked = true;
          _message =
              'Kết quả chưa rõ. Gửi lại cùng yêu cầu để kiểm tra kết quả.';
        } else {
          _key = null;
          _locked = false;
          _message = collaborationErrorMessage(error);
        }
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final enabled = !_busy && !_locked;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final field in widget.fields)
          if (field.choices case final choices?)
            DropdownButtonFormField<String>(
              key: Key('cf-${widget.prefix}-${field.id}'),
              initialValue: _choices[field.id],
              isExpanded: true,
              decoration: InputDecoration(labelText: field.label),
              items: [
                for (final entry in choices.entries)
                  DropdownMenuItem(value: entry.key, child: Text(entry.value)),
              ],
              onChanged: enabled
                  ? (value) => setState(() => _choices[field.id] = value)
                  : null,
            )
          else
            TextField(
              key: Key('cf-${widget.prefix}-${field.id}'),
              controller: _controllers[field.id],
              enabled: enabled,
              maxLength: field.maxLength,
              maxLines: field.maxLines,
              decoration: InputDecoration(labelText: field.label),
            ),
        if (_message != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Text(
              _message!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        Wrap(
          spacing: 8,
          children: [
            FilledButton(
              key: widget.submitKey ?? Key('cf-${widget.prefix}-submit'),
              onPressed: _busy ? null : _submit,
              child: Text(_locked ? 'Gửi lại cùng yêu cầu' : widget.submitLabel),
            ),
            if (widget.onCancel != null && !_locked)
              TextButton(
                onPressed: _busy ? null : widget.onCancel,
                child: const Text('Hủy'),
              ),
          ],
        ),
      ],
    );
  }
}

/// A titled list panel with loading, empty, denied and error states. The panel
/// disappears when the server reports the feature is off.
class LoadPanel<T> extends StatefulWidget {
  const LoadPanel({
    super.key,
    required this.id,
    required this.title,
    required this.noun,
    required this.deniedText,
    required this.emptyText,
    required this.load,
    required this.itemBuilder,
    this.footer,
    this.onLoaded,
    this.revision = 0,
  });

  final String id, title, noun, deniedText, emptyText;
  final Future<List<T>> Function() load;
  final Widget Function(BuildContext context, T item, VoidCallback reload)
  itemBuilder;
  final Widget Function(BuildContext context, VoidCallback reload)? footer;
  final void Function(List<T> items)? onLoaded;
  final int revision;

  @override
  State<LoadPanel<T>> createState() => _LoadPanelState<T>();
}

class _LoadPanelState<T> extends State<LoadPanel<T>> {
  List<T>? _items;
  Object? _error;
  bool _loading = true, _unavailable = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void didUpdateWidget(covariant LoadPanel<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.revision != widget.revision) _reload();
  }

  void _reload() {
    setState(() {
      _loading = true;
      _error = null;
    });
    _fetch();
  }

  Future<void> _fetch() async {
    final generation = ++_generation;
    try {
      final items = await widget.load();
      if (!mounted || generation != _generation) return;
      setState(() {
        _items = items;
        _error = null;
        _loading = false;
      });
      widget.onLoaded?.call(items);
    } on CollaborationUnavailable {
      if (!mounted || generation != _generation) return;
      setState(() {
        _unavailable = true;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error;
        _items = null;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_unavailable) return const SizedBox.shrink();
    final items = _items;
    final error = _error;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            if (_loading)
              const Center(child: MediFlowLoader())
            else if (error != null)
              if (error is ApiFailure && error.status == 403)
                Text(widget.deniedText)
              else ...[
                Text('Không tải được ${widget.noun}.'),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    key: Key('retry-${widget.id}'),
                    onPressed: _reload,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Thử lại'),
                  ),
                ),
              ]
            else if (items != null) ...[
              if (items.isEmpty)
                Text(widget.emptyText)
              else
                for (final item in items)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: widget.itemBuilder(context, item, _reload),
                  ),
              if (widget.footer case final footer?) footer(context, _reload),
            ],
          ],
        ),
      ),
    );
  }
}

/// Handover, consultation notes and amendments of one record. Everything is
/// hidden while the server omits the capability flags (feature off).
class RecordCollaborationSection extends ConsumerStatefulWidget {
  const RecordCollaborationSection({
    super.key,
    required this.recordId,
    required this.capabilities,
  });

  final String recordId;
  final RecordCapabilities? capabilities;

  @override
  ConsumerState<RecordCollaborationSection> createState() =>
      _RecordCollaborationSectionState();
}

class _RecordCollaborationSectionState
    extends ConsumerState<RecordCollaborationSection> {
  bool _formerDoctor = false;
  bool _showHandoverForm = false, _showNoteForm = false, _showAmendForm = false;
  int _amendmentRevision = 0;

  void _onHandovers(List<RecordHandover> handovers, String? staffId) {
    final accepted = handovers.where((h) => h.status == 'ACCEPTED').toList()
      ..sort((a, b) => (a.acceptedAt ?? a.requestedAt).compareTo(
        b.acceptedAt ?? b.requestedAt,
      ));
    final former =
        staffId != null &&
        accepted.isNotEmpty &&
        accepted.last.fromDoctorId == staffId;
    if (former != _formerDoctor) setState(() => _formerDoctor = former);
  }

  @override
  Widget build(BuildContext context) {
    final capabilities = widget.capabilities;
    final repository = ref.watch(recordCollaborationRepositoryProvider);
    if (capabilities == null ||
        !capabilities.collaborationAvailable ||
        repository == null) {
      return const SizedBox.shrink();
    }
    final staffId = ref.watch(workspaceAccessProvider)?.staffId;
    final readOnly = _formerDoctor;
    final recordId = widget.recordId;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (readOnly)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Hồ sơ đã bàn giao cho bác sĩ khác. Bạn chỉ có thể xem.',
              ),
            ),
          ),
        LoadPanel<RecordHandover>(
          id: 'handovers',
          title: 'Bàn giao hồ sơ',
          noun: 'bàn giao',
          deniedText: 'Bạn không có quyền xem bàn giao của hồ sơ này.',
          emptyText: 'Chưa có bàn giao.',
          load: () => repository.handovers(recordId),
          onLoaded: (items) => _onHandovers(items, staffId),
          itemBuilder: (context, handover, reload) => _HandoverTile(
            handover: handover,
            repository: repository,
            recordId: recordId,
            canAccept:
                !readOnly &&
                capabilities.canAcceptHandover == true &&
                handover.status == 'PENDING' &&
                handover.toDoctorId == staffId,
            onChanged: reload,
          ),
          footer: (context, reload) =>
              readOnly || capabilities.canHandover != true
              ? const SizedBox.shrink()
              : _formToggle(
                  toggleKey: 'toggle-handover',
                  label: 'Bàn giao hồ sơ cho bác sĩ khác',
                  open: _showHandoverForm,
                  onToggle: (value) =>
                      setState(() => _showHandoverForm = value),
                  form: CommandForm(
                    prefix: 'handover',
                    submitLabel: 'Gửi bàn giao',
                    fields: const [
                      CommandField('toDoctorId', 'Mã bác sĩ nhận bàn giao *'),
                      CommandField(
                        'reason',
                        'Lý do bàn giao *',
                        maxLength: 500,
                        maxLines: 2,
                      ),
                      CommandField(
                        'pendingItems',
                        'Việc còn dang dở *',
                        maxLength: 2000,
                        maxLines: 3,
                      ),
                    ],
                    validate: (values) {
                      final doctor = values['toDoctorId']!.trim();
                      if (doctor.isEmpty) {
                        return 'Nhập mã bác sĩ nhận bàn giao.';
                      }
                      if (!RegExp(_uuidPattern).hasMatch(doctor)) {
                        return 'Mã bác sĩ nhận bàn giao không hợp lệ.';
                      }
                      if (values['reason']!.trim().isEmpty) {
                        return 'Nhập lý do bàn giao.';
                      }
                      if (values['pendingItems']!.trim().isEmpty) {
                        return 'Nhập các việc còn dang dở.';
                      }
                      return null;
                    },
                    onSubmit: (values, key) => repository.requestHandover(
                      recordId,
                      toDoctorId: values['toDoctorId']!.trim(),
                      reason: values['reason']!,
                      pendingItems: values['pendingItems']!,
                      idempotencyKey: key,
                    ),
                    onDone: () {
                      setState(() => _showHandoverForm = false);
                      reload();
                    },
                    onCancel: () => setState(() => _showHandoverForm = false),
                  ),
                ),
        ),
        LoadPanel<ConsultationNote>(
          id: 'notes',
          title: 'Ý kiến hội chẩn',
          noun: 'ý kiến hội chẩn',
          deniedText: 'Bạn không có quyền xem ý kiến hội chẩn của hồ sơ này.',
          emptyText: 'Chưa có ý kiến hội chẩn.',
          load: () async {
            final notes = [...await repository.notes(recordId)];
            notes.sort((a, b) => a.createdAt.compareTo(b.createdAt));
            return notes;
          },
          itemBuilder: (context, note, reload) => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${note.authorStaffId} · ${formatClinicalInstant(note.createdAt)}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              Text(note.opinion),
            ],
          ),
          footer: (context, reload) =>
              readOnly || capabilities.canAddConsultationNote != true
              ? const SizedBox.shrink()
              : _formToggle(
                  toggleKey: 'toggle-note',
                  label: 'Thêm ý kiến hội chẩn',
                  open: _showNoteForm,
                  onToggle: (value) => setState(() => _showNoteForm = value),
                  form: CommandForm(
                    prefix: 'note',
                    submitLabel: 'Gửi ý kiến',
                    fields: const [
                      CommandField(
                        'opinion',
                        'Ý kiến hội chẩn *',
                        maxLength: 4000,
                        maxLines: 4,
                      ),
                    ],
                    validate: (values) => values['opinion']!.trim().isEmpty
                        ? 'Nhập ý kiến hội chẩn.'
                        : null,
                    onSubmit: (values, key) => repository.addNote(
                      recordId,
                      opinion: values['opinion']!,
                      idempotencyKey: key,
                    ),
                    onDone: () {
                      setState(() => _showNoteForm = false);
                      reload();
                    },
                    onCancel: () => setState(() => _showNoteForm = false),
                  ),
                ),
        ),
        LoadPanel<RecordAmendment>(
          id: 'amendments',
          title: 'Đính chính hồ sơ',
          noun: 'đính chính',
          deniedText: 'Bạn không có quyền xem đính chính của hồ sơ này.',
          emptyText: 'Chưa có đính chính.',
          revision: _amendmentRevision,
          load: () => repository.amendments(recordId),
          itemBuilder: (context, amendment, reload) => AmendmentCard(
            amendment: amendment,
            repository: repository,
            keyPrefix: 'amendment',
            canDecide:
                !readOnly &&
                capabilities.canDecideAmendments == true &&
                amendment.status == 'PENDING' &&
                amendment.authorStaffId != staffId,
            onChanged: () => setState(() => _amendmentRevision++),
          ),
          footer: (context, reload) =>
              readOnly || capabilities.canAmend != true
              ? const SizedBox.shrink()
              : _formToggle(
                  toggleKey: 'toggle-amend',
                  label: 'Đề nghị đính chính',
                  open: _showAmendForm,
                  onToggle: (value) => setState(() => _showAmendForm = value),
                  form: CommandForm(
                    prefix: 'amend',
                    submitLabel: 'Gửi đề nghị đính chính',
                    fields: const [
                      CommandField(
                        'section',
                        'Mục cần đính chính *',
                        choices: amendmentSectionLabels,
                      ),
                      CommandField(
                        'amendedText',
                        'Nội dung đính chính *',
                        maxLength: 4000,
                        maxLines: 3,
                      ),
                      CommandField(
                        'reason',
                        'Lý do đính chính *',
                        maxLength: 1000,
                        maxLines: 2,
                      ),
                    ],
                    validate: (values) {
                      if (values['amendedText']!.trim().isEmpty) {
                        return 'Nhập nội dung đính chính.';
                      }
                      if (values['reason']!.trim().isEmpty) {
                        return 'Nhập lý do đính chính.';
                      }
                      if (values['section']!.isEmpty) {
                        return 'Chọn mục cần đính chính.';
                      }
                      return null;
                    },
                    onSubmit: (values, key) => repository.requestAmendment(
                      recordId,
                      section: values['section']!,
                      amendedText: values['amendedText']!,
                      reason: values['reason']!,
                      idempotencyKey: key,
                    ),
                    onDone: () {
                      setState(() {
                        _showAmendForm = false;
                        _amendmentRevision++;
                      });
                    },
                    onCancel: () => setState(() => _showAmendForm = false),
                  ),
                ),
        ),
        if (capabilities.canDecideAmendments == true)
          LoadPanel<RecordAmendment>(
            id: 'pending-amendments',
            title: 'Hàng chờ duyệt đính chính',
            noun: 'hàng chờ duyệt đính chính',
            deniedText: 'Bạn không có quyền xem hàng chờ duyệt đính chính.',
            emptyText: 'Không có đính chính chờ duyệt.',
            revision: _amendmentRevision,
            load: () => repository.pendingAmendments(),
            itemBuilder: (context, amendment, reload) => AmendmentCard(
              amendment: amendment,
              repository: repository,
              keyPrefix: 'pending',
              showRecord: true,
              canDecide:
                  !readOnly &&
                  amendment.status == 'PENDING' &&
                  amendment.authorStaffId != staffId,
              onChanged: () => setState(() => _amendmentRevision++),
            ),
          ),
      ],
    );
  }

  Widget _formToggle({
    required String toggleKey,
    required String label,
    required bool open,
    required ValueChanged<bool> onToggle,
    required Widget form,
  }) => open
      ? form
      : Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton(
            key: Key(toggleKey),
            onPressed: () => onToggle(true),
            child: Text(label),
          ),
        );
}

class _HandoverTile extends StatelessWidget {
  const _HandoverTile({
    required this.handover,
    required this.repository,
    required this.recordId,
    required this.canAccept,
    required this.onChanged,
  });

  final RecordHandover handover;
  final RecordCollaborationRepository repository;
  final String recordId;
  final bool canAccept;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        '${handover.fromDoctorId} → ${handover.toDoctorId} · '
        '${switch (handover.status) {
          'ACCEPTED' => 'Đã tiếp nhận',
          'SUPERSEDED' => 'Đã thay bằng yêu cầu mới',
          _ => 'Chờ tiếp nhận',
        }}',
        style: Theme.of(context).textTheme.bodySmall,
      ),
      Text(handover.reason),
      Text(handover.pendingItems),
      Text(
        'Gửi lúc ${formatClinicalInstant(handover.requestedAt)}',
        style: Theme.of(context).textTheme.bodySmall,
      ),
      if (canAccept)
        CommandForm(
          prefix: 'accept-${handover.handoverId}',
          submitKey: Key('handover-accept-${handover.handoverId}'),
          submitLabel: 'Tiếp nhận bàn giao',
          fields: const [],
          validate: (_) => null,
          onSubmit: (_, key) => repository.acceptHandover(
            recordId,
            handover.handoverId,
            idempotencyKey: key,
          ),
          onDone: onChanged,
        ),
    ],
  );
}

class AmendmentCard extends StatefulWidget {
  const AmendmentCard({
    super.key,
    required this.amendment,
    required this.repository,
    required this.keyPrefix,
    required this.canDecide,
    required this.onChanged,
    this.showRecord = false,
  });

  final RecordAmendment amendment;
  final RecordCollaborationRepository repository;
  final String keyPrefix;
  final bool canDecide, showRecord;
  final VoidCallback onChanged;

  @override
  State<AmendmentCard> createState() => _AmendmentCardState();
}

class _AmendmentCardState extends State<AmendmentCard> {
  bool _deciding = false;

  @override
  Widget build(BuildContext context) {
    final amendment = widget.amendment;
    final theme = Theme.of(context);
    final hash = amendment.contentHash;
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            [
              amendmentSectionLabels[amendment.section] ?? amendment.section,
              if (widget.showRecord) 'Hồ sơ ${amendment.recordId}',
              _statusLabel(amendment.status),
            ].join(' · '),
            style: theme.textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: _side(theme, 'Bản gốc', amendment.originalText)),
              const SizedBox(width: 12),
              Expanded(
                child: _side(theme, 'Bản đính chính', amendment.amendedText),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text('Lý do', style: theme.textTheme.bodySmall),
          Text(amendment.reason),
          if (amendment.decisionNote?.trim().isNotEmpty == true) ...[
            Text('Ghi chú quyết định', style: theme.textTheme.bodySmall),
            Text(amendment.decisionNote!),
          ],
          if (amendment.status == 'APPROVED') ...[
            const Text('Đã xác nhận nội bộ'),
            if (hash != null && hash.isNotEmpty)
              Text(
                'Mã băm nội dung: ${hash.length > 12 ? hash.substring(0, 12) : hash}',
                style: theme.textTheme.bodySmall,
              ),
          ],
          if (widget.canDecide)
            _deciding
                ? CommandForm(
                    prefix: widget.keyPrefix == 'amendment' ? 'decide' : 'pending-decide',
                    submitLabel: 'Gửi quyết định',
                    fields: const [
                      CommandField(
                        'decision',
                        'Quyết định *',
                        choices: amendmentDecisionLabels,
                      ),
                      CommandField(
                        'note',
                        'Ghi chú',
                        maxLength: 1000,
                        maxLines: 2,
                      ),
                    ],
                    validate: (values) {
                      if (values['decision']!.isEmpty) {
                        return 'Chọn duyệt hoặc từ chối đính chính.';
                      }
                      if (values['decision'] == 'REJECTED' &&
                          values['note']!.trim().isEmpty) {
                        return 'Nhập ghi chú khi từ chối đính chính.';
                      }
                      return null;
                    },
                    onSubmit: (values, key) => widget.repository
                        .decideAmendment(
                          amendment.recordId,
                          amendment.amendmentId,
                          decision: values['decision']!,
                          note: values['note']!,
                          idempotencyKey: key,
                        ),
                    onDone: () {
                      setState(() => _deciding = false);
                      widget.onChanged();
                    },
                    onCancel: () => setState(() => _deciding = false),
                  )
                : Align(
                    alignment: Alignment.centerLeft,
                    child: OutlinedButton(
                      key: Key(
                        '${widget.keyPrefix}-decide-${amendment.amendmentId}',
                      ),
                      onPressed: () => setState(() => _deciding = true),
                      child: const Text('Duyệt hoặc từ chối'),
                    ),
                  ),
        ],
      ),
    );
  }

  Widget _side(ThemeData theme, String title, String text) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(title, style: theme.textTheme.bodySmall),
      Text(text),
    ],
  );

  String _statusLabel(String status) => switch (status) {
    'PENDING' => 'Chờ duyệt',
    'APPROVED' => 'Đã duyệt',
    'REJECTED' => 'Đã từ chối',
    _ => status,
  };
}
