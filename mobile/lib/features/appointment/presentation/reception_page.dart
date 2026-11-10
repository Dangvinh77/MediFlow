import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/page_result.dart';
import '../../../core/providers.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../domain/clinic_queue.dart';
import '../domain/open_visit.dart';
import '../domain/reception.dart';
import 'clinic_queue_page.dart';
import 'open_visit_notice.dart';
import 'outpatient_schedule_pages.dart';
import 'appointment_page.dart';

final receptionRepositoryProvider = Provider<ReceptionRepository>(
  (ref) =>
      throw StateError('Bind ReceptionRepository in app/dependencies.dart'),
);

/// Retained on navigation, but replaced when the authenticated workspace changes.
final receptionPendingProvider = Provider((ref) {
  ref.watch(workspaceAccessProvider);
  return ReceptionPending();
});

class ReceptionPending {
  ReceptionIntent? intent;
  ReceptionReceipt? receipt;
}

class ReceptionPage extends ConsumerWidget {
  const ReceptionPage({
    super.key,
    this.initialPatientId,
    this.onCreatePatient,
    this.onOpenPatient,
  });
  final String? initialPatientId;
  final VoidCallback? onCreatePatient;
  final ValueChanged<String>? onOpenPatient;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    if (access == null || !const ['ADMIN', 'NURSE'].contains(access.role)) {
      return const Center(
        child: Text('Bạn không có quyền tiếp nhận bệnh nhân.'),
      );
    }
    if (!ref.watch(outpatientUiEnabledProvider)) {
      return const Center(
        child: Text('Tiếp nhận theo buổi khám chưa được mở.'),
      );
    }
    return _ReceptionWorkspace(
      key: ValueKey((access.sessionIdentity, initialPatientId)),
      initialPatientId: initialPatientId,
      onCreatePatient: onCreatePatient,
      onOpenPatient: onOpenPatient,
    );
  }
}

class _ReceptionWorkspace extends ConsumerStatefulWidget {
  const _ReceptionWorkspace({
    super.key,
    this.initialPatientId,
    this.onCreatePatient,
    this.onOpenPatient,
  });
  final String? initialPatientId;
  final VoidCallback? onCreatePatient;
  final ValueChanged<String>? onOpenPatient;
  @override
  ConsumerState<_ReceptionWorkspace> createState() =>
      _ReceptionWorkspaceState();
}

class _ReceptionWorkspaceState extends ConsumerState<_ReceptionWorkspace> {
  String? patient, department;
  late String fromDate, toDate;
  ReceptionSession? selected;
  PageResult<ReceptionSession>? sessions;
  PageResult<ReceptionFollowUp>? sources;
  ReceptionFollowUp? source;
  bool busy = false,
      loading = false,
      sourceLoading = false,
      followUp = false,
      prebooked = false,
      useRecommendation = true,
      agreed = false,
      refreshNeeded = false;
  Object? error;
  int generation = 0, sourceGeneration = 0;
  final reason = TextEditingController(), replacement = TextEditingController();
  final independent = TextEditingController(),
      returnReason = TextEditingController();
  bool sameDepartmentOpen = false,
      forceIndependent = false,
      returnVisit = false;
  List<ReturnSource>? returnSources;
  String? returnSourceId;
  Object? returnSourceError;
  int returnGeneration = 0;
  final scroll = ScrollController();
  late final ReceptionPending pending;
  late final Object identity;
  bool get locked => busy || pending.intent != null || pending.receipt != null;
  bool get current =>
      mounted && ref.read(workspaceAccessProvider)?.sessionIdentity == identity;
  @override
  void initState() {
    super.initState();
    identity = ref.read(workspaceAccessProvider)!.sessionIdentity;
    pending = ref.read(receptionPendingProvider);
    fromDate = _day(DateTime.now().toUtc().add(const Duration(hours: 7)));
    toDate = fromDate;
    patient = isReceptionId(widget.initialPatientId ?? '')
        ? widget.initialPatientId!.toLowerCase()
        : null;
    final command = pending.intent?.command;
    if (command != null) {
      patient = command.patientId;
      department = command.session.departmentId;
      selected = command.session;
      fromDate = command.session.date;
      toDate = fromDate;
      followUp = command.followUp;
      prebooked = command.prebooked;
      source = command.source;
      useRecommendation = command.useRecommendation;
      agreed = command.agreedToReplacement;
      reason.text = command.reason;
      replacement.text = command.replacementReason;
      refreshNeeded = pending.receipt != null;
    }
  }

  @override
  void dispose() {
    generation++;
    sourceGeneration++;
    reason.dispose();
    independent.dispose();
    returnReason.dispose();
    replacement.dispose();
    scroll.dispose();
    super.dispose();
  }

  void clearSource() {
    sourceGeneration++;
    sources = null;
    source = null;
    sourceLoading = false;
    reason.clear();
    replacement.clear();
    agreed = false;
    useRecommendation = true;
    returnGeneration++;
    returnVisit = false;
    returnSources = null;
    returnSourceId = null;
    returnSourceError = null;
    returnReason.clear();
  }

  void clearOpenVisitCheck() {
    sameDepartmentOpen = false;
    forceIndependent = false;
    independent.clear();
  }

  Future<void> loadReturnSources() async {
    final repository = ref.read(openVisitRepositoryProvider);
    if (repository == null || patient == null) return;
    final request = ++returnGeneration;
    setState(() {
      returnSources = null;
      returnSourceError = null;
    });
    try {
      final result = await repository.returnSources(patient!);
      if (current && request == returnGeneration) {
        setState(() => returnSources = result);
      }
    } catch (cause) {
      if (current && request == returnGeneration) {
        setState(() => returnSourceError = cause);
      }
    }
  }

  void clearSessions() {
    generation++;
    sessions = null;
    selected = null;
    error = null;
    clearSource();
    loading = false;
  }

  Future<void> loadSessions({int page = 0, bool afterReceipt = false}) async {
    if (loading ||
        busy ||
        department == null ||
        (!afterReceipt && pending.intent != null)) {
      return;
    }
    final request = ++generation;
    final saved = selected;
    setState(() {
      loading = true;
      error = null;
      if (!afterReceipt) {
        selected = null;
        sessions = null;
        clearSource();
      }
    });
    try {
      final value = await ref
          .read(receptionRepositoryProvider)
          .sessions(
            departmentId: department!,
            fromDate: fromDate,
            toDate: toDate,
            page: page,
          );
      if (!current || request != generation) return;
      ReceptionSession? updated;
      if (afterReceipt && saved != null) {
        for (final session in value.items) {
          if (session.id == saved.id) updated = session;
        }
      }
      setState(() {
        sessions = value;
        if (afterReceipt) {
          selected = updated;
          refreshNeeded = false;
        }
      });
    } catch (cause) {
      if (current && request == generation) setState(() => error = cause);
    } finally {
      if (current && request == generation) setState(() => loading = false);
    }
  }

  Future<void> loadSources({int page = 0}) async {
    if (patient == null || sourceLoading || locked) return;
    final request = ++sourceGeneration;
    setState(() {
      sourceLoading = true;
      error = null;
    });
    try {
      final result = await ref
          .read(receptionRepositoryProvider)
          .followUpSources(patient!, page: page);
      if (current && request == sourceGeneration) {
        setState(() => sources = result);
      }
    } catch (cause) {
      if (current && request == sourceGeneration) setState(() => error = cause);
    } finally {
      if (current && request == sourceGeneration) {
        setState(() => sourceLoading = false);
      }
    }
  }

  Future<void> register() async {
    if (busy || pending.receipt != null || selected == null) return;
    final command = ReceptionCommand(
      patientId: patient ?? '',
      session: selected!,
      prebooked: prebooked,
      followUp: followUp,
      source: source,
      useRecommendation: useRecommendation,
      reason: reason.text,
      replacementReason: replacement.text,
      agreedToReplacement: agreed,
      requireIndependentReason: sameDepartmentOpen || forceIndependent,
      independentReason: independent.text,
      returnVisit: returnVisit,
      returnSourceRecordId: returnSourceId,
      returnReason: returnReason.text,
    );
    final issue = command.validate();
    if (pending.intent == null && issue != null) {
      setState(() => error = ArgumentError(issue));
      return;
    }
    pending.intent ??= ReceptionIntent(command);
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final receipt = await ref
          .read(receptionRepositoryProvider)
          .register(pending.intent!);
      if (!current) return;
      setState(() {
        pending.receipt = receipt;
        refreshNeeded = true;
      });
    } catch (cause) {
      if (!current) return;
      final ambiguous =
          cause is! ApiFailure ||
          cause.status == null ||
          cause.status! < 400 ||
          cause.status == 408 ||
          cause.status! >= 500;
      if (!ambiguous) pending.intent = null;
      setState(() {
        error = cause;
        if (cause is ApiFailure && cause.code == 'OPEN_VISIT_EXISTS') {
          forceIndependent = true;
        }
      });
    } finally {
      if (current) setState(() => busy = false);
    }
    if (current && pending.receipt != null) {
      await loadSessions(page: sessions?.number ?? 0, afterReceipt: true);
    }
  }

  void nextPatient() {
    if (busy || loading || pending.receipt == null || refreshNeeded) return;
    setState(() {
      pending.intent = null;
      pending.receipt = null;
      patient = null;
      followUp = false;
      prebooked = false;
      error = null;
      clearSource();
      clearOpenVisitCheck();
    });
    if (scroll.hasClients) scroll.jumpTo(0);
  }

  @override
  Widget build(BuildContext context) {
    final receipt = pending.receipt;
    final session = selected;
    return ListView(
      controller: scroll,
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Đăng ký khám tại quầy',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        Text('1. Bệnh nhân', style: Theme.of(context).textTheme.titleMedium),
        ReferencePicker(
          kind: ReferenceKind.patient,
          label: 'Bệnh nhân',
          value: patient,
          onChanged: locked
              ? null
              : (value) => setState(() {
                  patient = value;
                  clearSource();
                  clearOpenVisitCheck();
                  error = null;
                }),
        ),
        if (patient != null)
          ReferenceLabel(
            kind: ReferenceKind.patient,
            id: patient!,
            label: 'Bệnh nhân đã chọn',
          ),
        Wrap(
          spacing: 8,
          children: [
            if (patient != null && widget.onOpenPatient != null)
              TextButton(
                onPressed: locked
                    ? null
                    : () => widget.onOpenPatient!(patient!),
                child: const Text('Xem hồ sơ'),
              ),
            if (widget.onCreatePatient != null)
              TextButton(
                onPressed: locked ? null : widget.onCreatePatient,
                child: const Text('Tạo hồ sơ bệnh nhân'),
              ),
          ],
        ),
        const Divider(),
        Text('2. Buổi khám', style: Theme.of(context).textTheme.titleMedium),
        ReferencePicker(
          kind: ReferenceKind.department,
          label: 'Khoa',
          value: department,
          onChanged: locked
              ? null
              : (value) => setState(() {
                  department = value;
                  clearSessions();
                  clearOpenVisitCheck();
                }),
        ),
        ExpansionTile(
          title: Text(
            'Ngày khám: $fromDate${toDate == fromDate ? '' : ' – $toDate'}',
          ),
          children: [
            TextButton(
              onPressed: locked
                  ? null
                  : () async {
                      final range = await showDateRangePicker(
                        context: context,
                        firstDate: DateTime(2020),
                        lastDate: DateTime(2100),
                        initialDateRange: DateTimeRange(
                          start: DateTime.parse(fromDate),
                          end: DateTime.parse(toDate),
                        ),
                      );
                      if (range != null && current) {
                        setState(() {
                          fromDate = _day(range.start);
                          toDate = _day(range.end);
                          clearSessions();
                        });
                      }
                    },
              child: const Text('Chọn ngày khác'),
            ),
            TextButton(
              onPressed: locked
                  ? null
                  : () => setState(() {
                      fromDate = _day(
                        DateTime.now().toUtc().add(const Duration(hours: 7)),
                      );
                      toDate = fromDate;
                      clearSessions();
                    }),
              child: const Text('Hôm nay'),
            ),
          ],
        ),
        FilledButton.tonal(
          onPressed: department == null || locked || loading
              ? null
              : () => loadSessions(),
          child: const Text('Tải buổi khám'),
        ),
        if (loading) const LinearProgressIndicator(),
        if (sessions != null && sessions!.items.isEmpty)
          const Text('Không có buổi khám trong khoảng ngày đã chọn.'),
        if (sessions != null) ...[
          for (final item in sessions!.items)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${item.date} · ${item.startTime}–${item.endTime}',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    ReferenceLabel(
                      kind: ReferenceKind.doctor,
                      id: item.doctorId,
                      label: 'Bác sĩ',
                    ),
                    ReferenceLabel(
                      kind: ReferenceKind.room,
                      id: item.roomId,
                      label: 'Phòng khám',
                    ),
                    Text(
                      ref.read(clinicIntakeEnabledProvider) && !item.canRegister
                          ? sessionAvailabilityLabels[sessionAvailability(
                              item,
                              null,
                            )]!
                          : 'Còn ${item.availableCount} / ${item.capacity} chỗ',
                    ),
                    OutlinedButton(
                      onPressed: locked || !item.canRegister
                          ? null
                          : () => setState(() {
                              selected = item;
                              clearSource();
                              error = null;
                            }),
                      child: Text(
                        selected?.id == item.id ? 'Đã chọn buổi' : 'Chọn buổi',
                      ),
                    ),
                    TextButton(
                      onPressed: () => Navigator.of(context).push<void>(
                        MaterialPageRoute(
                          builder: (_) => SessionRegistrationsPage(
                            sessionId: item.id,
                            title:
                                '${item.date} · ${item.startTime}–${item.endTime}',
                            canMarkArrival: true,
                          ),
                        ),
                      ),
                      child: const Text('Xem phiếu của buổi'),
                    ),
                  ],
                ),
              ),
            ),
          if (sessions!.totalPages > 1)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: locked || loading || sessions!.number == 0
                      ? null
                      : () => loadSessions(page: sessions!.number - 1),
                  child: const Text('Trang trước'),
                ),
                Text('${sessions!.number + 1}/${sessions!.totalPages}'),
                TextButton(
                  onPressed: locked || loading || !sessions!.hasNext
                      ? null
                      : () => loadSessions(page: sessions!.number + 1),
                  child: const Text('Trang sau'),
                ),
              ],
            ),
        ],
        if (session != null) ...[
          const Divider(),
          Text(
            '3. Xác nhận đăng ký',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Text('${session.date} · ${session.startTime}–${session.endTime}'),
          DropdownButtonFormField<bool>(
            isExpanded: true,
            itemHeight: null,
            initialValue: followUp,
            decoration: const InputDecoration(labelText: 'Loại lượt khám'),
            items: const [
              DropdownMenuItem(value: false, child: Text('Khám đợt mới')),
              DropdownMenuItem(value: true, child: Text('Tái khám')),
            ],
            onChanged: locked
                ? null
                : (value) => setState(() {
                    followUp = value!;
                    clearSource();
                  }),
          ),
          ExpansionTile(
            title: Text(prebooked ? 'Đăng ký trước' : 'Có mặt tại quầy'),
            children: [
              SwitchListTile(
                title: const Text('Đăng ký trước, sẽ đến sau'),
                value: prebooked,
                onChanged: locked
                    ? null
                    : (value) => setState(() => prebooked = value),
              ),
            ],
          ),
          if (followUp) ...[
            OutlinedButton(
              onPressed: locked || sourceLoading || patient == null
                  ? null
                  : () => loadSources(),
              child: const Text('Tìm lượt khám trước'),
            ),
            if (sourceLoading) const LinearProgressIndicator(),
            if (sources != null && sources!.items.isEmpty)
              const Text('Chưa có lượt đã hoàn tất để tái khám.'),
            if (sources != null)
              for (final item in sources!.items)
                ListTile(
                  selected: source?.registrationId == item.registrationId,
                  leading: Icon(
                    source?.registrationId == item.registrationId
                        ? Icons.radio_button_checked
                        : Icons.radio_button_off,
                  ),
                  title: Text('Hoàn tất ${_instant(item.completedAt)}'),
                  subtitle: ReferenceLabel(
                    kind: ReferenceKind.doctor,
                    id: item.doctorId,
                    label: 'Bác sĩ lượt trước',
                  ),
                  onTap: locked || item.departmentId != session.departmentId
                      ? null
                      : () => setState(() {
                          source = item;
                          useRecommendation = item.recommendationId != null;
                          agreed = false;
                          replacement.clear();
                        }),
                ),
            if (sources != null && sources!.totalPages > 1)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  TextButton(
                    onPressed: locked || sourceLoading || sources!.number == 0
                        ? null
                        : () => loadSources(page: sources!.number - 1),
                    child: const Text('Lượt trước'),
                  ),
                  TextButton(
                    onPressed: locked || sourceLoading || !sources!.hasNext
                        ? null
                        : () => loadSources(page: sources!.number + 1),
                    child: const Text('Lượt sau'),
                  ),
                ],
              ),
            if (source?.recommendationId != null) ...[
              CheckboxListTile(
                title: const Text('Dùng đề xuất của bác sĩ'),
                value: useRecommendation,
                onChanged: locked
                    ? null
                    : (value) => setState(() => useRecommendation = value!),
              ),
              if (useRecommendation) ...[
                Text(
                  'Khuyến nghị: ${source!.recommendedFrom} – ${source!.recommendedThrough}',
                ),
                if (source!.reason != null) Text(source!.reason!),
                if (session.date.compareTo(source!.recommendedFrom!) < 0 ||
                    session.date.compareTo(source!.recommendedThrough!) > 0)
                  const Text(
                    'Ngày khám ngoài thời gian khuyến nghị. Có thể tiếp tục nếu bệnh nhân muốn đăng ký.',
                  ),
              ],
            ],
            if (source != null && source!.doctorId != session.doctorId) ...[
              TextField(
                controller: replacement,
                enabled: !locked,
                maxLength: 1000,
                decoration: const InputDecoration(
                  labelText: 'Lý do đổi bác sĩ',
                ),
              ),
              CheckboxListTile(
                title: const Text('Bệnh nhân đồng ý đổi bác sĩ'),
                value: agreed,
                onChanged: locked
                    ? null
                    : (value) => setState(() => agreed = value!),
              ),
            ],
          ],
          TextField(
            controller: reason,
            enabled: !locked,
            maxLength: 1000,
            decoration: InputDecoration(
              labelText: followUp ? 'Lý do tái khám' : 'Ghi chú (nếu có)',
            ),
          ),
          if (!followUp && ref.watch(openVisitRepositoryProvider) != null) ...[
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Bệnh nhân quay lại sau lượt đã kết thúc'),
              value: returnVisit,
              onChanged: locked || patient == null
                  ? null
                  : (value) {
                      setState(() {
                        returnGeneration++;
                        returnVisit = value!;
                        returnSources = null;
                        returnSourceId = null;
                        returnSourceError = null;
                        returnReason.clear();
                        error = null;
                      });
                      if (value!) loadReturnSources();
                    },
            ),
            if (returnVisit && returnSourceError != null)
              TextButton(
                onPressed: locked ? null : loadReturnSources,
                child: const Text('Không tải được lượt đã kết thúc. Thử lại'),
              ),
            if (returnVisit &&
                returnSources == null &&
                returnSourceError == null)
              const LinearProgressIndicator(),
            if (returnVisit && returnSources?.isEmpty == true)
              const Text('Bệnh nhân chưa có lượt nào đã kết thúc.'),
            if (returnVisit && returnSources?.isNotEmpty == true) ...[
              DropdownButtonFormField<String>(
                key: const Key('reception-return-source'),
                isExpanded: true,
                itemHeight: null,
                initialValue: returnSourceId,
                decoration: const InputDecoration(
                  labelText: 'Lượt đã kết thúc',
                ),
                items: [
                  for (final item in returnSources!)
                    DropdownMenuItem(
                      value: item.recordId,
                      child: Text(
                        '${item.examinationDate} · ${returnSourceLabels[item.status]}',
                      ),
                    ),
                ],
                onChanged: locked
                    ? null
                    : (value) => setState(() => returnSourceId = value),
              ),
              TextField(
                key: const Key('reception-return-reason'),
                controller: returnReason,
                enabled: !locked,
                maxLength: 500,
                decoration: const InputDecoration(labelText: 'Lý do quay lại'),
              ),
            ],
          ],
          if (patient != null && receipt == null)
            if (ref.watch(openVisitRepositoryProvider) case final repository?)
              OpenVisitNotice(
                key: ValueKey((patient, session.departmentId)),
                repository: repository,
                patientId: patient!,
                departmentId: session.departmentId,
                controller: independent,
                enabled: !locked,
                forceReason: forceIndependent,
                onSameDepartmentChanged: (value) =>
                    setState(() => sameDepartmentOpen = value),
              ),
          if (receipt == null)
            FilledButton(
              onPressed: busy || patient == null ? null : register,
              child: Text(
                pending.intent != null
                    ? 'Đối soát cùng yêu cầu'
                    : 'Đăng ký và giữ chỗ',
              ),
            ),
          if (pending.intent != null && receipt == null)
            const Text(
              'Chưa xác định kết quả. Đối soát cùng yêu cầu; không đăng ký lại ở màn hình khác.',
            ),
        ],
        if (receipt != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    receipt.status == 'AWAITING_PAYMENT'
                        ? 'Đã tạo phiếu · Chờ thu tiền'
                        : _receiptStatus(receipt.status),
                  ),
                  if (receipt.status == 'AWAITING_PAYMENT')
                    const Text(
                      'Hướng dẫn bệnh nhân đến thu ngân trước hạn giữ chỗ.',
                    ),
                  Text(
                    'Hạn giữ chỗ: ${_instant(receipt.expiresAt)} (giờ Việt Nam)',
                  ),
                  if (receipt.queueNumber != null)
                    Text(
                      '${queueNumberLabel(ref.read(clinicIntakeEnabledProvider))}: ${receipt.queueNumber}',
                    ),
                  ExpansionTile(
                    title: const Text('Thông tin bàn giao'),
                    children: [SelectableText('Mã phiếu: ${receipt.id}')],
                  ),
                  if (refreshNeeded)
                    OutlinedButton(
                      onPressed: loading
                          ? null
                          : () => loadSessions(
                              page: sessions?.number ?? 0,
                              afterReceipt: true,
                            ),
                      child: const Text('Cập nhật số chỗ'),
                    ),
                  FilledButton(
                    onPressed: refreshNeeded || loading ? null : nextPatient,
                    child: const Text('Tiếp nhận bệnh nhân tiếp theo'),
                  ),
                ],
              ),
            ),
          ),
        if (busy) const LinearProgressIndicator(),
        if (error != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(
              _errorText(error!),
              semanticsLabel: 'Lỗi tiếp nhận: ${_errorText(error!)}',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
      ],
    );
  }
}

String _day(DateTime date) => date.toIso8601String().substring(0, 10);
String _instant(String value) {
  final parsed = DateTime.tryParse(value);
  if (parsed == null) return 'Chưa có thông tin';
  final date = parsed.toUtc().add(const Duration(hours: 7));
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} ${two(date.hour)}:${two(date.minute)}';
}

String _errorText(Object error) => error is ApiFailure
    ? '${error.message}${error.correlationId == null ? '' : ' · Mã theo dõi: ${error.correlationId}'}'
    : error is ArgumentError
    ? '${error.message}'
    : error is FormatException
    ? error.message
    : 'Không thể cập nhật tiếp nhận. Hãy thử tải lại hoặc đối soát cùng yêu cầu.';
String _receiptStatus(String status) =>
    const {
      'CONFIRMED': 'Đã xác nhận',
      'IN_EXAM': 'Đang khám',
      'COMPLETED': 'Đã hoàn tất',
      'EXPIRED': 'Phiếu đã hết hạn',
      'CANCELLED': 'Phiếu đã hủy',
      'RECONCILIATION_REQUIRED': 'Cần đối soát',
    }[status] ??
    'Trạng thái chưa xác định';
