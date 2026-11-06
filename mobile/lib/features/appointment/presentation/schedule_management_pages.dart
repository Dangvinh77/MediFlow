import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/outpatient_schedule.dart';
import '../domain/schedule_management.dart';

final scheduleManagementRepositoryProvider =
    Provider<ScheduleManagementRepository>(
      (ref) =>
          throw StateError('Bind schedule management in app/dependencies.dart'),
    );

/// Whether the signed-in doctor heads a department; decides the shell menu.
final headsDepartmentProvider = FutureProvider.autoDispose<bool>((ref) async {
  final access = ref.watch(workspaceAccessProvider);
  if (access?.role != 'DOCTOR' ||
      !ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
    return false;
  }
  final departments = await ref
      .watch(scheduleManagementRepositoryProvider)
      .managementDepartments();
  return departments.isNotEmpty;
});

/// Runs one command at a time and keeps an unresolved one for an exact resend.
mixin ScheduleCommandRunner<T extends StatefulWidget> on State<T> {
  ScheduleCommand? pendingCommand;
  Object? commandError;
  bool commandBusy = false;

  bool get commandLocked => commandBusy || pendingCommand != null;

  Future<bool> runCommand(
    ScheduleManagementRepository repository,
    ScheduleCommand command,
  ) async {
    if (commandBusy) return false;
    setState(() {
      commandBusy = true;
      commandError = null;
    });
    try {
      await repository.execute(command);
      if (mounted) setState(() => pendingCommand = null);
      return true;
    } catch (error) {
      if (mounted) {
        setState(() {
          commandError = friendlyScheduleError(error);
          pendingCommand = scheduleOutcomeUnknown(error) ? command : null;
        });
      }
      return false;
    } finally {
      if (mounted) setState(() => commandBusy = false);
    }
  }

  List<Widget> commandFeedback({required VoidCallback onResend}) => [
    if (commandError != null) RequestError(commandError!),
    if (pendingCommand != null) ...[
      const Text(
        'Kết quả chưa xác định. Nội dung đã khóa; gửi lại đúng yêu cầu để đối soát.',
      ),
      const SizedBox(height: 8),
      FilledButton(
        onPressed: commandBusy ? null : onResend,
        child: const Text('Gửi lại cùng yêu cầu'),
      ),
    ],
  ];
}

class _Range {
  const _Range(this.from, this.to);
  final DateTime from, to;
  static _Range day(DateTime date) => _Range(date, date);
  static _Range week(DateTime date) {
    final monday = mondayOfWeek(date);
    return _Range(monday, monday.add(const Duration(days: 6)));
  }

  String get label => from == to
      ? displayDate(isoDate(from))
      : '${displayDate(isoDate(from))} – ${displayDate(isoDate(to))}';
}

class _RangeFilter extends StatelessWidget {
  const _RangeFilter({
    required this.week,
    required this.date,
    required this.enabled,
    required this.onChanged,
  });

  final bool week, enabled;
  final DateTime date;
  final void Function(bool week, DateTime date) onChanged;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 12,
    runSpacing: 8,
    crossAxisAlignment: WrapCrossAlignment.center,
    children: [
      SegmentedButton<bool>(
        segments: const [
          ButtonSegment(value: false, label: Text('Theo ngày')),
          ButtonSegment(value: true, label: Text('Theo tuần')),
        ],
        selected: {week},
        onSelectionChanged: enabled
            ? (value) => onChanged(value.single, date)
            : null,
      ),
      OutlinedButton.icon(
        onPressed: enabled
            ? () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: date,
                  firstDate: DateTime(date.year - 1),
                  lastDate: DateTime(date.year + 2),
                );
                if (picked != null) onChanged(week, picked);
              }
            : null,
        icon: const Icon(Icons.event),
        label: Text(displayDate(isoDate(date))),
      ),
    ],
  );
}

/// Lists and changes sessions. Doctors heading a department get a fixed one;
/// managers and admins pick a department and Core enforces their scope.
class SessionAdministrationPanel extends ConsumerStatefulWidget {
  const SessionAdministrationPanel({super.key, this.fixedDepartmentId});
  final String? fixedDepartmentId;

  @override
  ConsumerState<SessionAdministrationPanel> createState() =>
      _SessionAdministrationPanelState();
}

class _SessionAdministrationPanelState
    extends ConsumerState<SessionAdministrationPanel>
    with ScheduleCommandRunner {
  final _statusReason = TextEditingController();
  late String? _departmentId = widget.fixedDepartmentId;
  bool _week = false;
  DateTime _date = clinicToday();
  List<ManagedSession>? _sessions;
  Object? _loadError;
  bool _loading = false;
  int _generation = 0;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    if (_departmentId != null) _load();
  }

  @override
  void dispose() {
    _generation++;
    _statusReason.dispose();
    super.dispose();
  }

  _Range get _range => _week ? _Range.week(_date) : _Range.day(_date);

  Future<void> _load() async {
    final departmentId = _departmentId;
    if (departmentId == null) return;
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _loadError = null;
      _sessions = null;
    });
    try {
      final rows = <ManagedSession>[];
      var page = 0;
      var pages = 1;
      while (page < pages) {
        final result = await _repository.sessions(
          departmentId,
          fromDate: isoDate(_range.from),
          toDate: isoDate(_range.to),
          page: page,
        );
        rows.addAll(result.items);
        pages = result.totalPages;
        page++;
      }
      if (mounted && generation == _generation) {
        setState(() => _sessions = rows);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _loadError = friendlyScheduleError(error));
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _openEditor([ManagedSession? session]) async {
    final departmentId = _departmentId;
    if (departmentId == null) return;
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => SessionEditorPage(
          departmentId: departmentId,
          session: session,
          initialDate: _date,
        ),
      ),
    );
    if (saved == true) _load();
  }

  Future<void> _changeStatus(ManagedSession session, String status) async {
    final reason = _statusReason.text.trim();
    if (reason.isEmpty) {
      setState(
        () => commandError = const FormatException(
          'Nhập lý do trước khi đổi trạng thái buổi khám.',
        ),
      );
      return;
    }
    if (await runCommand(
      _repository,
      ScheduleCommand.sessionStatus(session, status, reason),
    )) {
      _statusReason.clear();
      _load();
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        'Lập và quản lý buổi khám',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 8),
      if (widget.fixedDepartmentId == null) ...[
        ReferencePicker(
          kind: ReferenceKind.department,
          label: 'Khoa buổi khám',
          value: _departmentId,
          onChanged: commandLocked
              ? null
              : (id) {
                  setState(() {
                    _departmentId = id;
                    _sessions = null;
                  });
                  _load();
                },
        ),
        const SizedBox(height: 8),
      ],
      _RangeFilter(
        week: _week,
        date: _date,
        enabled: !commandLocked,
        onChanged: (week, date) {
          setState(() {
            _week = week;
            _date = date;
          });
          _load();
        },
      ),
      const SizedBox(height: 8),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          FilledButton.icon(
            onPressed: _departmentId == null || commandLocked
                ? null
                : () => _openEditor(),
            icon: const Icon(Icons.add),
            label: const Text('Tạo buổi khám'),
          ),
          OutlinedButton(
            onPressed: _departmentId == null || _loading ? null : _load,
            child: const Text('Tải buổi đã lập'),
          ),
        ],
      ),
      const SizedBox(height: 8),
      if (_departmentId == null)
        const Text('Chọn khoa để xem các buổi khám.')
      else if (_loading)
        const Center(child: MediFlowLoader())
      else if (_loadError != null)
        RequestError(_loadError!, onRetry: _load)
      else if (_sessions?.isEmpty == true)
        const Text('Chưa có buổi khám trong bộ lọc.'),
      if (_sessions?.isNotEmpty == true) ...[
        TextField(
          controller: _statusReason,
          enabled: !commandLocked,
          maxLength: 500,
          decoration: const InputDecoration(
            labelText: 'Lý do ngừng, mở lại hoặc hủy buổi',
            border: OutlineInputBorder(),
          ),
        ),
        for (final session in _sessions!) _sessionCard(session),
      ],
      ...commandFeedback(
        onResend: () async {
          if (await runCommand(_repository, pendingCommand!)) _load();
        },
      ),
    ],
  );

  Widget _sessionCard(ManagedSession session) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${displayDate(session.date)} · ${session.startTime}–${session.endTime}',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          Text(sessionStatusLabels[session.status] ?? session.status),
          ReferenceLabel(
            kind: ReferenceKind.doctor,
            id: session.doctorId,
            label: 'Bác sĩ',
          ),
          ReferenceLabel(
            kind: ReferenceKind.room,
            id: session.roomId,
            label: 'Phòng khám',
          ),
          Text(
            'Sức chứa ${session.capacity} · ${session.confirmedCount} đã xác nhận · ${session.availableCount} còn chỗ',
          ),
          if (session.status != 'CANCELLED') ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  onPressed: commandLocked ? null : () => _openEditor(session),
                  child: const Text('Sửa'),
                ),
                OutlinedButton(
                  onPressed: commandLocked
                      ? null
                      : () => _changeStatus(
                          session,
                          session.status == 'OPEN' ? 'STOPPED' : 'OPEN',
                        ),
                  child: Text(
                    session.status == 'OPEN' ? 'Ngừng nhận' : 'Mở lại',
                  ),
                ),
                OutlinedButton(
                  onPressed: commandLocked
                      ? null
                      : () => _changeStatus(session, 'CANCELLED'),
                  child: const Text('Hủy buổi'),
                ),
              ],
            ),
          ],
        ],
      ),
    ),
  );
}

class _BreakRows extends StatelessWidget {
  const _BreakRows({
    required this.breaks,
    required this.enabled,
    required this.onChanged,
  });

  final List<(TextEditingController, TextEditingController)> breaks;
  final bool enabled;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Text('Giờ nghỉ tùy chọn'),
      for (var index = 0; index < breaks.length; index++)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Row(
            children: [
              Expanded(
                child: _TimeField(
                  controller: breaks[index].$1,
                  label: 'Bắt đầu nghỉ',
                  enabled: enabled,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _TimeField(
                  controller: breaks[index].$2,
                  label: 'Kết thúc nghỉ',
                  enabled: enabled,
                ),
              ),
              IconButton(
                tooltip: 'Xóa giờ nghỉ',
                onPressed: enabled
                    ? () {
                        breaks.removeAt(index);
                        onChanged();
                      }
                    : null,
                icon: const Icon(Icons.close),
              ),
            ],
          ),
        ),
      TextButton(
        onPressed: enabled
            ? () {
                breaks.add((TextEditingController(), TextEditingController()));
                onChanged();
              }
            : null,
        child: const Text('Thêm giờ nghỉ'),
      ),
    ],
  );
}

class _TimeField extends StatelessWidget {
  const _TimeField({
    required this.controller,
    required this.label,
    this.enabled = true,
  });
  final TextEditingController controller;
  final String label;
  final bool enabled;

  @override
  Widget build(BuildContext context) => TextField(
    controller: controller,
    enabled: enabled,
    keyboardType: TextInputType.datetime,
    decoration: InputDecoration(
      labelText: label,
      hintText: 'HH:mm',
      border: const OutlineInputBorder(),
    ),
  );
}

List<SessionBreak>? _readBreaks(
  List<(TextEditingController, TextEditingController)> rows,
) {
  final breaks = <SessionBreak>[];
  for (final (start, end) in rows) {
    if (validateTimeRange(start.text.trim(), end.text.trim()) != null) {
      return null;
    }
    breaks.add(
      SessionBreak(startTime: start.text.trim(), endTime: end.text.trim()),
    );
  }
  return breaks;
}

/// Creates or edits a session. Editing needs a reason, as on the web.
class SessionEditorPage extends ConsumerStatefulWidget {
  const SessionEditorPage({
    super.key,
    required this.departmentId,
    required this.initialDate,
    this.session,
  });

  final String departmentId;
  final DateTime initialDate;
  final ManagedSession? session;

  @override
  ConsumerState<SessionEditorPage> createState() => _SessionEditorPageState();
}

class _SessionEditorPageState extends ConsumerState<SessionEditorPage>
    with ScheduleCommandRunner {
  late final _start = TextEditingController(
    text: widget.session?.startTime ?? '08:00',
  );
  late final _end = TextEditingController(
    text: widget.session?.endTime ?? '12:00',
  );
  late final _capacity = TextEditingController(
    text: '${widget.session?.capacity ?? 20}',
  );
  final _reason = TextEditingController();
  late final _breaks = [
    for (final item in widget.session?.breaks ?? const <SessionBreak>[])
      (
        TextEditingController(text: item.startTime),
        TextEditingController(text: item.endTime),
      ),
  ];
  late String? _doctorId = widget.session?.doctorId;
  late String? _roomId = widget.session?.roomId;
  late DateTime _date = widget.session == null
      ? widget.initialDate
      : DateTime.parse(widget.session!.date);
  List<ConsultationRoom>? _rooms;
  Object? _roomError;
  String? _issue;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _loadRooms();
  }

  @override
  void dispose() {
    for (final controller in [_start, _end, _capacity, _reason]) {
      controller.dispose();
    }
    for (final (start, end) in _breaks) {
      start.dispose();
      end.dispose();
    }
    super.dispose();
  }

  Future<void> _loadRooms() async {
    setState(() => _roomError = null);
    try {
      final rooms = await _repository.rooms(widget.departmentId);
      if (mounted) setState(() => _rooms = rooms);
    } catch (error) {
      if (mounted) setState(() => _roomError = friendlyScheduleError(error));
    }
  }

  Future<void> _submit() async {
    final session = widget.session;
    final start = _start.text.trim(), end = _end.text.trim();
    final reason = _reason.text.trim();
    final breaks = _readBreaks(_breaks);
    final issue = _doctorId == null || _roomId == null
        ? 'Chọn bác sĩ và phòng trước khi lưu.'
        : validateTimeRange(start, end) ??
              validateCapacity(_capacity.text) ??
              (breaks == null ? 'Giờ nghỉ chưa hợp lệ.' : null) ??
              (session != null && reason.isEmpty
                  ? 'Nhập lý do sửa buổi.'
                  : null);
    setState(() => _issue = issue);
    if (issue != null) return;
    final capacity = int.parse(_capacity.text.trim());
    final command = session == null
        ? ScheduleCommand.createSession(
            doctorId: _doctorId!,
            departmentId: widget.departmentId,
            roomId: _roomId!,
            date: isoDate(_date),
            startTime: start,
            endTime: end,
            breaks: breaks!,
            capacity: capacity,
          )
        : ScheduleCommand.updateSession(
            session,
            doctorId: _doctorId!,
            roomId: _roomId!,
            date: isoDate(_date),
            startTime: start,
            endTime: end,
            breaks: breaks!,
            capacity: capacity,
            reason: reason,
          );
    if (await runCommand(_repository, command) && mounted) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final rooms = _rooms;
    final enabled = !commandLocked;
    return PopScope(
      canPop: !commandLocked,
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.session == null ? 'Tạo buổi khám' : 'Sửa buổi khám',
          ),
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ReferencePicker(
              kind: ReferenceKind.doctor,
              label: 'Bác sĩ',
              value: _doctorId,
              scope: widget.departmentId,
              required: true,
              onChanged: enabled
                  ? (id) => setState(() => _doctorId = id)
                  : null,
            ),
            const SizedBox(height: 12),
            if (_roomError != null)
              RequestError(_roomError!, onRetry: _loadRooms)
            else if (rooms == null)
              const LinearProgressIndicator()
            else
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: rooms.any((room) => room.id == _roomId)
                    ? _roomId
                    : null,
                decoration: const InputDecoration(labelText: 'Phòng tư vấn'),
                items: [
                  for (final room in rooms)
                    DropdownMenuItem(
                      value: room.id,
                      child: Text(
                        room.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: enabled
                    ? (value) => setState(() => _roomId = value)
                    : null,
              ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: enabled
                  ? () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: _date,
                        firstDate: DateTime(_date.year - 1),
                        lastDate: DateTime(_date.year + 2),
                      );
                      if (picked != null) setState(() => _date = picked);
                    }
                  : null,
              icon: const Icon(Icons.event),
              label: Text('Ngày khám: ${displayDate(isoDate(_date))}'),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _TimeField(
                    controller: _start,
                    label: 'Bắt đầu',
                    enabled: enabled,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _TimeField(
                    controller: _end,
                    label: 'Kết thúc',
                    enabled: enabled,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _capacity,
              enabled: enabled,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Sức chứa',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            _BreakRows(
              breaks: _breaks,
              enabled: enabled,
              onChanged: () => setState(() {}),
            ),
            if (widget.session != null) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _reason,
                enabled: enabled,
                maxLength: 500,
                decoration: const InputDecoration(
                  labelText: 'Lý do sửa buổi',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
            if (_issue != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  _issue!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            ...commandFeedback(
              onResend: () async {
                if (await runCommand(_repository, pendingCommand!) &&
                    context.mounted) {
                  Navigator.of(context).pop(true);
                }
              },
            ),
            const SizedBox(height: 12),
            if (pendingCommand == null)
              FilledButton(
                onPressed: commandBusy ? null : _submit,
                child: Text(
                  widget.session == null
                      ? 'Tạo buổi khám'
                      : 'Lưu thay đổi buổi',
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// ADMIN grants a manager the departments they may schedule. The whole list is
/// sent on save, so an empty list revokes every department.
class ManagerScopePanel extends ConsumerStatefulWidget {
  const ManagerScopePanel({super.key, this.initialAccountId});
  final String? initialAccountId;

  @override
  ConsumerState<ManagerScopePanel> createState() => _ManagerScopePanelState();
}

class _ManagerScopePanelState extends ConsumerState<ManagerScopePanel>
    with ScheduleCommandRunner {
  final _reason = TextEditingController();
  List<ManagerAccount>? _managers;
  List<ManagedDepartment> _departments = const [];
  SchedulingScope? _scope;
  Set<String> _selected = {};
  String? _accountId;
  Object? _error;
  bool _loading = false;
  String? _notice;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _loadManagers();
    if (widget.initialAccountId case final accountId?) _loadScope(accountId);
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _loadManagers() async {
    setState(() => _error = null);
    try {
      final managers = await _repository.managerAccounts();
      if (mounted) setState(() => _managers = managers);
    } catch (error) {
      if (mounted) setState(() => _error = friendlyScheduleError(error));
    }
  }

  Future<void> _loadScope(String accountId) async {
    setState(() {
      _accountId = accountId;
      _loading = true;
      _error = null;
      _scope = null;
      _notice = null;
    });
    try {
      final results = await Future.wait([
        _repository.scope(accountId),
        _repository.activeDepartments(),
      ]);
      if (!mounted) return;
      final scope = results[0] as SchedulingScope;
      setState(() {
        _scope = scope;
        _selected = {...scope.departmentIds};
        _departments = results[1] as List<ManagedDepartment>;
      });
    } catch (error) {
      if (mounted) setState(() => _error = friendlyScheduleError(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _afterSave() async {
    _reason.clear();
    final accountId = _accountId!;
    await _loadScope(accountId);
    if (mounted) setState(() => _notice = 'Đã lưu phạm vi quản lý lịch.');
  }

  Future<void> _save() async {
    final scope = _scope;
    final reason = _reason.text.trim();
    if (scope == null || reason.isEmpty) return;
    if (await runCommand(
      _repository,
      ScheduleCommand.replaceScope(scope, _selected.toList(), reason),
    )) {
      await _afterSave();
    }
  }

  @override
  Widget build(BuildContext context) {
    final managers = _managers;
    final scope = _scope;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Phạm vi của quản lý lịch',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        const Text(
          'Chỉ quản lý đang hoạt động mới được cấp khoa. Danh sách khoa được gửi toàn bộ khi lưu.',
        ),
        const SizedBox(height: 8),
        if (managers == null && _error == null)
          const LinearProgressIndicator()
        else if (managers != null)
          DropdownButtonFormField<String>(
            isExpanded: true,
            initialValue: _accountId,
            decoration: const InputDecoration(labelText: 'Tài khoản quản lý'),
            items: [
              for (final manager in managers)
                DropdownMenuItem(
                  value: manager.id,
                  child: Text(
                    manager.username,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: commandLocked
                ? null
                : (value) {
                    if (value != null) _loadScope(value);
                  },
          ),
        if (_loading) const LinearProgressIndicator(),
        if (_error != null) RequestError(_error!),
        if (scope != null) ...[
          const SizedBox(height: 8),
          Text(
            'Phiên bản phạm vi: ${scope.revision} · Đã chọn ${_selected.length} khoa',
          ),
          for (final department in _departments)
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(department.name),
              value: _selected.contains(department.id),
              onChanged: commandLocked
                  ? null
                  : (checked) => setState(() {
                      if (checked == true) {
                        _selected.add(department.id);
                      } else {
                        _selected.remove(department.id);
                      }
                    }),
            ),
          TextField(
            controller: _reason,
            enabled: !commandLocked,
            maxLength: 500,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: 'Lý do cấp hoặc thu hồi quyền',
              border: OutlineInputBorder(),
            ),
          ),
          ...commandFeedback(
            onResend: () async {
              if (await runCommand(_repository, pendingCommand!)) {
                await _afterSave();
              }
            },
          ),
          if (pendingCommand == null)
            FilledButton(
              onPressed: commandBusy || _reason.text.trim().isEmpty
                  ? null
                  : _save,
              child: Text(
                _selected.isEmpty ? 'Thu hồi toàn bộ phạm vi' : 'Lưu phạm vi',
              ),
            ),
        ],
        if (_notice != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_notice!),
          ),
      ],
    );
  }
}

class ScheduleManagementPage extends ConsumerWidget {
  const ScheduleManagementPage({super.key, this.managerAccountId});
  final String? managerAccountId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (!const {'ADMIN', 'MANAGER'}.contains(access?.role)) {
      return const Center(child: Text('Bạn không có quyền quản lý buổi khám.'));
    }
    if (!ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
      return const Center(child: Text('Quản lý lịch khám V2 chưa được mở.'));
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const SessionAdministrationPanel(),
        if (access!.role == 'ADMIN') ...[
          const Divider(height: 32),
          ManagerScopePanel(initialAccountId: managerAccountId),
        ],
      ],
    );
  }
}

/// Workspace of a doctor appointed department head.
class DepartmentManagementPage extends ConsumerStatefulWidget {
  const DepartmentManagementPage({super.key});

  @override
  ConsumerState<DepartmentManagementPage> createState() =>
      _DepartmentManagementPageState();
}

class _DepartmentManagementPageState
    extends ConsumerState<DepartmentManagementPage> {
  late Future<List<ManagedDepartment>> _departments = _load();
  String? _departmentId;

  Future<List<ManagedDepartment>> _load() =>
      ref.read(scheduleManagementRepositoryProvider).managementDepartments();

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'DOCTOR') {
      return const Center(child: Text('Chức năng dành cho trưởng khoa.'));
    }
    if (!ref.watch(appConfigProvider).scheduledPaymentsEnabled) {
      return const Center(child: Text('Quản lý lịch chưa được bật.'));
    }
    return FutureBuilder<List<ManagedDepartment>>(
      future: _departments,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return RequestError(
            snapshot.error!,
            onRetry: () {
              final next = _load();
              setState(() {
                _departments = next;
              });
            },
          );
        }
        final departments = snapshot.data;
        if (departments == null) {
          return const Center(child: MediFlowLoader());
        }
        if (departments.isEmpty) {
          return const Center(
            child: Text(
              'Tài khoản không có bổ nhiệm trưởng khoa đang hiệu lực.',
            ),
          );
        }
        final departmentId = _departmentId ?? departments.first.id;
        return DefaultTabController(
          length: 3,
          child: Column(
            children: [
              if (departments.length > 1)
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: DropdownButtonFormField<String>(
                    isExpanded: true,
                    initialValue: departmentId,
                    decoration: const InputDecoration(
                      labelText: 'Khoa được bổ nhiệm',
                    ),
                    items: [
                      for (final department in departments)
                        DropdownMenuItem(
                          value: department.id,
                          child: Text(department.name),
                        ),
                    ],
                    onChanged: (value) => setState(() => _departmentId = value),
                  ),
                ),
              const TabBar(
                isScrollable: true,
                tabs: [
                  Tab(text: 'Tổng quan khoa'),
                  Tab(text: 'Buổi khám & phòng'),
                  Tab(text: 'Lịch làm việc bác sĩ'),
                ],
              ),
              Expanded(
                child: TabBarView(
                  children: [
                    ListView(
                      key: ValueKey('summary:$departmentId'),
                      padding: const EdgeInsets.all(16),
                      children: [
                        DepartmentSummaryPanel(departmentId: departmentId),
                      ],
                    ),
                    ListView(
                      key: ValueKey('sessions:$departmentId'),
                      padding: const EdgeInsets.all(16),
                      children: [
                        SessionAdministrationPanel(
                          fixedDepartmentId: departmentId,
                        ),
                      ],
                    ),
                    ListView(
                      key: ValueKey('shifts:$departmentId'),
                      padding: const EdgeInsets.all(16),
                      children: [
                        DepartmentShiftsPanel(departmentId: departmentId),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class DepartmentSummaryPanel extends ConsumerStatefulWidget {
  const DepartmentSummaryPanel({super.key, required this.departmentId});
  final String departmentId;

  @override
  ConsumerState<DepartmentSummaryPanel> createState() =>
      _DepartmentSummaryPanelState();
}

class _DepartmentSummaryPanelState
    extends ConsumerState<DepartmentSummaryPanel> {
  bool _week = false;
  DateTime _date = clinicToday();
  late Future<DepartmentSummary> _summary = _load();

  Future<DepartmentSummary> _load() {
    final range = _week ? _Range.week(_date) : _Range.day(_date);
    return ref
        .read(scheduleManagementRepositoryProvider)
        .summary(
          widget.departmentId,
          fromDate: isoDate(range.from),
          toDate: isoDate(range.to),
        );
  }

  void _reload() {
    final next = _load();
    setState(() {
      _summary = next;
    });
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text('Hoạt động khoa', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      _RangeFilter(
        week: _week,
        date: _date,
        enabled: true,
        onChanged: (week, date) {
          _week = week;
          _date = date;
          _reload();
        },
      ),
      const SizedBox(height: 12),
      FutureBuilder<DepartmentSummary>(
        future: _summary,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return RequestError(snapshot.error!, onRetry: _reload);
          }
          final data = snapshot.data;
          if (data == null ||
              snapshot.connectionState != ConnectionState.done) {
            return const Center(child: MediFlowLoader());
          }
          return Wrap(
            spacing: 16,
            runSpacing: 12,
            children: [
              for (final (label, value) in [
                ('Buổi đang mở', data.openSessions),
                ('Buổi ngừng nhận', data.stoppedSessions),
                ('Sức chứa', data.capacity),
                ('Còn trống', data.available),
                ('Giữ chỗ', data.held),
                ('Đang xử lý thu', data.paymentLocked),
                ('Đã xác nhận', data.confirmed),
                ('Đã phục vụ', data.consumed),
              ])
                SizedBox(
                  width: 130,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(label),
                      Text(
                        '$value',
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                    ],
                  ),
                ),
            ],
          );
        },
      ),
    ],
  );
}

/// Shift assignment for a department head.
class DepartmentShiftsPanel extends ConsumerStatefulWidget {
  const DepartmentShiftsPanel({super.key, required this.departmentId});
  final String departmentId;

  @override
  ConsumerState<DepartmentShiftsPanel> createState() =>
      _DepartmentShiftsPanelState();
}

class _DepartmentShiftsPanelState extends ConsumerState<DepartmentShiftsPanel>
    with ScheduleCommandRunner {
  final _reason = TextEditingController();
  bool _week = false;
  DateTime _date = clinicToday();
  List<ManagedShift>? _shifts;
  Object? _loadError;
  bool _loading = true;
  int _generation = 0;
  String? _notice;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _generation++;
    _reason.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final range = _week ? _Range.week(_date) : _Range.day(_date);
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _loadError = null;
      _shifts = null;
    });
    try {
      final rows = <ManagedShift>[];
      var page = 0;
      var pages = 1;
      while (page < pages) {
        final result = await _repository.shifts(
          widget.departmentId,
          fromDate: isoDate(range.from),
          toDate: isoDate(range.to),
          page: page,
        );
        rows.addAll(result.items);
        pages = result.totalPages;
        page++;
      }
      if (mounted && generation == _generation) {
        setState(() => _shifts = rows);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _loadError = friendlyScheduleError(error));
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _edit([ManagedShift? shift]) async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => ShiftEditorPage(
          departmentId: widget.departmentId,
          initialDate: _date,
          shift: shift,
        ),
      ),
    );
    if (saved == true) {
      setState(() => _notice = 'Đã lưu giờ làm việc.');
      _load();
    }
  }

  Future<void> _sessionFrom(ManagedShift shift) async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => ShiftSessionPage(shift: shift)),
    );
    if (saved == true) {
      setState(() => _notice = 'Đã tạo buổi khám.');
      _load();
    }
  }

  Future<void> _cancel(ManagedShift shift) async {
    final reason = _reason.text.trim();
    if (reason.isEmpty) {
      setState(
        () => commandError = const FormatException(
          'Nhập lý do trước khi hủy giờ làm việc.',
        ),
      );
      return;
    }
    if (await runCommand(
      _repository,
      ScheduleCommand.cancelShift(shift, reason),
    )) {
      _reason.clear();
      setState(() => _notice = 'Đã lưu giờ làm việc.');
      _load();
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        'Lịch làm việc bác sĩ',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 4),
      const Text('Phân công bác sĩ, giờ và phòng trong khoa.'),
      const SizedBox(height: 8),
      _RangeFilter(
        week: _week,
        date: _date,
        enabled: !commandLocked,
        onChanged: (week, date) {
          setState(() {
            _week = week;
            _date = date;
          });
          _load();
        },
      ),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: FilledButton.icon(
          onPressed: commandLocked ? null : () => _edit(),
          icon: const Icon(Icons.add),
          label: const Text('Phân công giờ làm việc'),
        ),
      ),
      if (_notice != null) Text(_notice!),
      const SizedBox(height: 8),
      if (_loading)
        const Center(child: MediFlowLoader())
      else if (_loadError != null)
        RequestError(_loadError!, onRetry: _load)
      else if (_shifts?.isEmpty == true)
        const Text('Không có giờ làm việc trong khoảng đã chọn.'),
      if (_shifts?.isNotEmpty == true) ...[
        TextField(
          controller: _reason,
          enabled: !commandLocked,
          maxLength: 500,
          decoration: const InputDecoration(
            labelText: 'Lý do hủy giờ làm việc',
            border: OutlineInputBorder(),
          ),
        ),
        for (final shift in _shifts!) _shiftCard(shift),
      ],
      ...commandFeedback(
        onResend: () async {
          if (await runCommand(_repository, pendingCommand!)) _load();
        },
      ),
    ],
  );

  Widget _shiftCard(ManagedShift shift) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${displayDate(shift.date)} · ${shift.startTime}–${shift.endTime}',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          ReferenceLabel(
            kind: ReferenceKind.doctor,
            id: shift.doctorId,
            label: 'Bác sĩ',
          ),
          ReferenceLabel(
            kind: ReferenceKind.room,
            id: shift.roomId,
            label: 'Phòng khám',
          ),
          Text(shift.status == 'ACTIVE' ? 'Đang hiệu lực' : 'Đã hủy'),
          if (shift.status == 'ACTIVE') ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  onPressed: commandLocked ? null : () => _edit(shift),
                  child: const Text('Sửa giờ làm việc'),
                ),
                FilledButton(
                  onPressed: commandLocked ? null : () => _sessionFrom(shift),
                  child: const Text('Tạo buổi khám từ giờ làm việc'),
                ),
                OutlinedButton(
                  onPressed: commandLocked ? null : () => _cancel(shift),
                  child: const Text('Hủy giờ làm việc'),
                ),
              ],
            ),
          ],
        ],
      ),
    ),
  );
}

class ShiftEditorPage extends ConsumerStatefulWidget {
  const ShiftEditorPage({
    super.key,
    required this.departmentId,
    required this.initialDate,
    this.shift,
  });

  final String departmentId;
  final DateTime initialDate;
  final ManagedShift? shift;

  @override
  ConsumerState<ShiftEditorPage> createState() => _ShiftEditorPageState();
}

class _ShiftEditorPageState extends ConsumerState<ShiftEditorPage>
    with ScheduleCommandRunner {
  late final _start = TextEditingController(
    text: widget.shift?.startTime ?? '08:00',
  );
  late final _end = TextEditingController(
    text: widget.shift?.endTime ?? '12:00',
  );
  final _reason = TextEditingController();
  late String? _doctorId = widget.shift?.doctorId;
  late String? _roomId = widget.shift?.roomId;
  late DateTime _date = widget.shift == null
      ? widget.initialDate
      : DateTime.parse(widget.shift!.date);
  List<ConsultationRoom>? _rooms;
  Object? _roomError;
  String? _issue;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _loadRooms();
  }

  @override
  void dispose() {
    _start.dispose();
    _end.dispose();
    _reason.dispose();
    super.dispose();
  }

  Future<void> _loadRooms() async {
    setState(() => _roomError = null);
    try {
      final rooms = await _repository.rooms(widget.departmentId);
      if (mounted) setState(() => _rooms = rooms);
    } catch (error) {
      if (mounted) setState(() => _roomError = friendlyScheduleError(error));
    }
  }

  Future<void> _submit() async {
    final shift = widget.shift;
    final start = _start.text.trim(), end = _end.text.trim();
    final reason = _reason.text.trim();
    final issue = _doctorId == null || _roomId == null
        ? 'Chọn bác sĩ và phòng trước khi lưu.'
        : validateTimeRange(start, end) ??
              (shift != null && reason.isEmpty
                  ? 'Nhập lý do sửa giờ làm việc.'
                  : null);
    setState(() => _issue = issue);
    if (issue != null) return;
    final command = shift == null
        ? ScheduleCommand.createShift(
            doctorId: _doctorId!,
            departmentId: widget.departmentId,
            roomId: _roomId!,
            date: isoDate(_date),
            startTime: start,
            endTime: end,
          )
        : ScheduleCommand.updateShift(
            shift,
            doctorId: _doctorId!,
            roomId: _roomId!,
            date: isoDate(_date),
            startTime: start,
            endTime: end,
            reason: reason,
          );
    if (await runCommand(_repository, command) && mounted) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final rooms = _rooms;
    final enabled = !commandLocked;
    return PopScope(
      canPop: !commandLocked,
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.shift == null
                ? 'Phân công giờ làm việc'
                : 'Sửa giờ làm việc',
          ),
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ReferencePicker(
              kind: ReferenceKind.doctor,
              label: 'Bác sĩ',
              value: _doctorId,
              scope: widget.departmentId,
              required: true,
              onChanged: enabled
                  ? (id) => setState(() => _doctorId = id)
                  : null,
            ),
            const SizedBox(height: 12),
            if (_roomError != null)
              RequestError(_roomError!, onRetry: _loadRooms)
            else if (rooms == null)
              const LinearProgressIndicator()
            else
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: rooms.any((room) => room.id == _roomId)
                    ? _roomId
                    : null,
                decoration: const InputDecoration(labelText: 'Phòng tư vấn'),
                items: [
                  for (final room in rooms)
                    DropdownMenuItem(
                      value: room.id,
                      child: Text(
                        room.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: enabled
                    ? (value) => setState(() => _roomId = value)
                    : null,
              ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: enabled
                  ? () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: _date,
                        firstDate: DateTime(_date.year - 1),
                        lastDate: DateTime(_date.year + 2),
                      );
                      if (picked != null) setState(() => _date = picked);
                    }
                  : null,
              icon: const Icon(Icons.event),
              label: Text('Ngày làm việc: ${displayDate(isoDate(_date))}'),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _TimeField(
                    controller: _start,
                    label: 'Bắt đầu',
                    enabled: enabled,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _TimeField(
                    controller: _end,
                    label: 'Kết thúc',
                    enabled: enabled,
                  ),
                ),
              ],
            ),
            if (widget.shift != null) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _reason,
                enabled: enabled,
                maxLength: 500,
                decoration: const InputDecoration(
                  labelText: 'Lý do sửa giờ làm việc',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
            if (_issue != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  _issue!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            ...commandFeedback(
              onResend: () async {
                if (await runCommand(_repository, pendingCommand!) &&
                    context.mounted) {
                  Navigator.of(context).pop(true);
                }
              },
            ),
            const SizedBox(height: 12),
            if (pendingCommand == null)
              FilledButton(
                onPressed: commandBusy ? null : _submit,
                child: const Text('Lưu giờ làm việc'),
              ),
          ],
        ),
      ),
    );
  }
}

class ShiftSessionPage extends ConsumerStatefulWidget {
  const ShiftSessionPage({super.key, required this.shift});
  final ManagedShift shift;

  @override
  ConsumerState<ShiftSessionPage> createState() => _ShiftSessionPageState();
}

class _ShiftSessionPageState extends ConsumerState<ShiftSessionPage>
    with ScheduleCommandRunner {
  late final _start = TextEditingController(text: widget.shift.startTime);
  late final _end = TextEditingController(text: widget.shift.endTime);
  final _capacity = TextEditingController(text: '20');
  final _breaks = <(TextEditingController, TextEditingController)>[];
  String? _issue;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void dispose() {
    _start.dispose();
    _end.dispose();
    _capacity.dispose();
    for (final (start, end) in _breaks) {
      start.dispose();
      end.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    final shift = widget.shift;
    final start = _start.text.trim(), end = _end.text.trim();
    final breaks = _readBreaks(_breaks);
    var issue =
        validateTimeRange(start, end) ??
        validateCapacity(_capacity.text) ??
        (breaks == null ? 'Giờ nghỉ chưa hợp lệ.' : null);
    if (issue == null &&
        (start.compareTo(shift.startTime) < 0 ||
            end.compareTo(shift.endTime) > 0)) {
      issue = 'Buổi khám phải nằm trong giờ làm việc đã phân công.';
    }
    setState(() => _issue = issue);
    if (issue != null) return;
    if (await runCommand(
          _repository,
          ScheduleCommand.sessionFromShift(
            shift,
            startTime: start,
            endTime: end,
            breaks: breaks!,
            capacity: int.parse(_capacity.text.trim()),
          ),
        ) &&
        mounted) {
      Navigator.of(context).pop(true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final shift = widget.shift;
    final enabled = !commandLocked;
    return PopScope(
      canPop: !commandLocked,
      child: Scaffold(
        appBar: AppBar(title: const Text('Tạo buổi khám')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Buổi khám nằm trong giờ làm việc ${displayDate(shift.date)} · ${shift.startTime}–${shift.endTime}. Bác sĩ và phòng giữ theo phân công.',
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _TimeField(
                    controller: _start,
                    label: 'Giờ bắt đầu',
                    enabled: enabled,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _TimeField(
                    controller: _end,
                    label: 'Giờ kết thúc',
                    enabled: enabled,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _capacity,
              enabled: enabled,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Sức chứa buổi khám',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            _BreakRows(
              breaks: _breaks,
              enabled: enabled,
              onChanged: () => setState(() {}),
            ),
            if (_issue != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  _issue!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            ...commandFeedback(
              onResend: () async {
                if (await runCommand(_repository, pendingCommand!) &&
                    context.mounted) {
                  Navigator.of(context).pop(true);
                }
              },
            ),
            const SizedBox(height: 12),
            if (pendingCommand == null)
              FilledButton(
                onPressed: commandBusy ? null : _submit,
                child: const Text('Tạo buổi khám'),
              ),
          ],
        ),
      ),
    );
  }
}

/// ADMIN appoints or dismisses the head of a clinical department.
class HeadAppointmentPanel extends ConsumerStatefulWidget {
  const HeadAppointmentPanel({super.key, required this.departmentId});
  final String departmentId;

  @override
  ConsumerState<HeadAppointmentPanel> createState() =>
      _HeadAppointmentPanelState();
}

class _HeadAppointmentPanelState extends ConsumerState<HeadAppointmentPanel>
    with ScheduleCommandRunner {
  final _reason = TextEditingController();
  HeadAppointment? _current;
  Object? _loadError;
  String? _staffId, _notice;

  ScheduleManagementRepository get _repository =>
      ref.read(scheduleManagementRepositoryProvider);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loadError = null);
    try {
      final current = await _repository.headAppointment(widget.departmentId);
      if (mounted) setState(() => _current = current);
    } catch (error) {
      if (mounted) setState(() => _loadError = friendlyScheduleError(error));
    }
  }

  Future<void> _save({required String? staffId}) async {
    final current = _current;
    final reason = _reason.text.trim();
    if (current == null || reason.isEmpty) return;
    final done = await runCommand(
      _repository,
      ScheduleCommand.replaceHead(current, staffId: staffId, reason: reason),
    );
    if (!mounted) return;
    if (done) {
      _reason.clear();
      setState(() {
        _staffId = null;
        _notice = staffId == null
            ? 'Đã miễn nhiệm trưởng khoa.'
            : 'Đã bổ nhiệm trưởng khoa.';
      });
    }
    // A stale revision must not stay on screen after the server refused it.
    if (done || pendingCommand == null) await _load();
  }

  @override
  Widget build(BuildContext context) {
    final current = _current;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Bổ nhiệm trưởng khoa',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (_loadError != null)
              RequestError(_loadError!, onRetry: _load)
            else if (current == null)
              const Center(
                child: MediFlowLoader(semanticLabel: 'Đang tải bổ nhiệm'),
              )
            else ...[
              if (current.staffId case final staffId?)
                ReferenceLabel(
                  kind: ReferenceKind.doctor,
                  id: staffId,
                  label: 'Trưởng khoa đương nhiệm',
                )
              else
                const Text('Chưa có trưởng khoa đương nhiệm.'),
              const SizedBox(height: 12),
              ReferencePicker(
                kind: ReferenceKind.doctor,
                label: 'Bác sĩ được đề cử',
                value: _staffId,
                scope: widget.departmentId,
                onChanged: commandLocked
                    ? null
                    : (id) => setState(() => _staffId = id),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _reason,
                enabled: !commandLocked,
                maxLength: 500,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  labelText: 'Lý do',
                  border: OutlineInputBorder(),
                ),
              ),
              ...commandFeedback(
                onResend: () async {
                  if (await runCommand(_repository, pendingCommand!)) {
                    await _load();
                  }
                },
              ),
              if (pendingCommand == null)
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilledButton(
                      onPressed:
                          commandBusy ||
                              _staffId == null ||
                              _reason.text.trim().isEmpty
                          ? null
                          : () => _save(staffId: _staffId),
                      child: const Text('Xác nhận bổ nhiệm'),
                    ),
                    if (current.staffId != null)
                      OutlinedButton(
                        onPressed: commandBusy || _reason.text.trim().isEmpty
                            ? null
                            : () => _save(staffId: null),
                        child: const Text('Miễn nhiệm trưởng khoa'),
                      ),
                  ],
                ),
              if (_notice != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_notice!),
                ),
              if (current.history.isNotEmpty)
                ExpansionTile(
                  tilePadding: EdgeInsets.zero,
                  title: Text('Lịch sử bổ nhiệm (${current.history.length})'),
                  children: [
                    for (final term in current.history)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: ReferenceLabel(
                          kind: ReferenceKind.doctor,
                          id: term.staffId,
                          label: 'Bác sĩ',
                        ),
                        subtitle: Text(
                          '${_localTime(term.startedAt)} – ${term.endedAt == null ? 'nay' : _localTime(term.endedAt!)}',
                        ),
                      ),
                  ],
                ),
            ],
          ],
        ),
      ),
    );
  }
}

String _localTime(String raw) {
  final value = DateTime.tryParse(raw)?.toLocal();
  if (value == null) return 'Thời gian chưa xác định';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(value.hour)}:${two(value.minute)} '
      '${two(value.day)}/${two(value.month)}/${value.year}';
}
