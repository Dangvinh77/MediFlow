import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/reference_widgets.dart';
import 'pharmacy_request_error.dart';
import 'pharmacy_date_text.dart';
import 'pharmacy_status_text.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/pharmacy_identity.dart';
import 'pharmacy_identity_label.dart';
import '../domain/prescription.dart';
import '../domain/prescription_commands.dart';
import '../domain/drug_commands.dart';
import 'pharmacy_boundary.dart';
import 'providers.dart';
import 'edit_prescription_page.dart';

class PrescriptionLookupPage extends StatelessWidget {
  const PrescriptionLookupPage({super.key});
  @override
  Widget build(BuildContext context) => PharmacyBoundary(
    builder: (repository) => _PrescriptionLookup(repository: repository),
  );
}

class _PrescriptionLookup extends StatefulWidget {
  const _PrescriptionLookup({required this.repository});
  final PharmacyRepository repository;
  @override
  State<_PrescriptionLookup> createState() => _PrescriptionLookupState();
}

class _PrescriptionLookupState extends State<_PrescriptionLookup> {
  final id = TextEditingController();
  final focus = FocusNode();
  String? submitted, validation;
  bool hasLoaded = false;
  int submission = 0;
  @override
  void dispose() {
    id.dispose();
    focus.dispose();
    super.dispose();
  }

  void _lookup() {
    final input = id.text.trim();
    if (!isPharmacyId(input)) {
      setState(() {
        validation = 'Mã đơn thuốc chưa đúng. Vui lòng kiểm tra lại.';
        submitted = null;
        hasLoaded = false;
      });
      focus.requestFocus();
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      submitted = input;
      validation = null;
      hasLoaded = false;
      submission++;
    });
  }

  void _markLoaded() {
    if (!mounted || hasLoaded) return;
    setState(() => hasLoaded = true);
  }

  Widget _lookupControls() => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          key: const ValueKey('prescription-lookup-id'),
          controller: id,
          focusNode: focus,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            labelText: AppL10n.text(context, 'Mã đơn thuốc'),
            errorText: AppL10n.text(context, validation),
            border: const OutlineInputBorder(),
          ),
          onSubmitted: (_) => _lookup(),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: _lookup,
          child: const LText('Tra cứu đơn thuốc'),
        ),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) => Column(
    children: [
      if (hasLoaded)
        ExpansionTile(
          key: ValueKey(('prescription-lookup-loaded', submission)),
          title: const LText('Tra cứu bằng mã'),
          children: [_lookupControls()],
        )
      else
        _lookupControls(),
      Expanded(
        child: submitted == null
            ? const Center(child: LText('Nhập mã để mở đơn thuốc.'))
            : _PrescriptionDetail(
                key: ValueKey((submitted, submission)),
                id: submitted!,
                repository: widget.repository,
                onLoaded: _markLoaded,
              ),
      ),
    ],
  );
}

class PrescriptionDetailPage extends StatelessWidget {
  const PrescriptionDetailPage({super.key, required this.prescriptionId});
  final String prescriptionId;
  @override
  Widget build(BuildContext context) => PharmacyBoundary(
    builder: (repository) => _PrescriptionDetail(
      key: ValueKey(prescriptionId),
      id: prescriptionId,
      repository: repository,
    ),
  );
}

class _PrescriptionDetail extends ConsumerStatefulWidget {
  const _PrescriptionDetail({
    super.key,
    required this.id,
    required this.repository,
    this.onLoaded,
  });
  final String id;
  final PharmacyRepository repository;
  final VoidCallback? onLoaded;
  @override
  ConsumerState<_PrescriptionDetail> createState() =>
      _PrescriptionDetailState();
}

class _PrescriptionDetailState extends ConsumerState<_PrescriptionDetail> {
  Prescription? rx;
  Object? error;
  bool loading = true;
  int generation = 0;
  final errorFocus = FocusNode();
  final reason = TextEditingController();
  final reasonFocus = FocusNode();
  final cancelFeedbackFocus = FocusNode();
  bool cancelling = false, cancelLocked = false;
  Object? cancelError;
  CancelPrescriptionResult? receipt;
  bool dispensing = false, dispenseLocked = false, dispenseRecheck = false;
  Object? dispenseError;
  DispenseResult? dispenseReceipt;
  bool editing = false;
  bool get commandBusy => cancelling || dispensing || editing;
  bool get otherCommandBlocked =>
      dispenseLocked || dispenseRecheck || dispenseReceipt != null;
  DialogRoute<bool>? confirmation;
  NavigatorState? dialogNavigator;

  Future<void> _edit() async {
    final access = ref.read(pharmacyAccessProvider);
    final snapshot = rx;
    if (commandBusy ||
        otherCommandBlocked ||
        cancelLocked ||
        snapshot == null ||
        access?.canPrescribe != true ||
        widget.repository is! PrescriptionEditor ||
        !canEditPrescription(snapshot, ref.read(pharmacyModeProvider))) {
      return;
    }
    setState(() => editing = true);
    final updated = await Navigator.of(context).push<Prescription>(
      MaterialPageRoute(
        builder: (_) => EditPrescriptionPage(
          prescription: snapshot,
          repository: widget.repository,
          sessionIdentity: access!.sessionIdentity,
        ),
      ),
    );
    if (!mounted ||
        ref.read(pharmacyAccessProvider)?.sessionIdentity !=
            access!.sessionIdentity) {
      return;
    }
    setState(() => editing = false);
    await _load();
    if (updated != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: LText(
            'Đã cập nhật đơn thuốc. Khoản phải thu đang được đồng bộ.',
          ),
        ),
      );
    }
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    generation++;
    final route = confirmation;
    final navigator = dialogNavigator;
    if (route != null && navigator != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (route.isActive) navigator.removeRoute(route);
      });
    }
    reason.dispose();
    reasonFocus.dispose();
    cancelFeedbackFocus.dispose();
    errorFocus.dispose();
    super.dispose();
  }

  Future<void> _cancel() async {
    final access = ref.read(pharmacyAccessProvider);
    if (commandBusy ||
        otherCommandBlocked ||
        cancelLocked ||
        rx == null ||
        access?.canPrescribe != true ||
        ref.read(pharmacyModeProvider) == PharmacyMode.unavailable ||
        !canCancelPrescription(rx!)) {
      return;
    }
    final command = CancelPrescription(reason.text);
    try {
      command.validate();
    } on PharmacyValidationException catch (failure) {
      setState(() => cancelError = failure.fields['reason']);
      reasonFocus.requestFocus();
      return;
    }
    setState(() {
      cancelling = true;
      cancelError = null;
    });
    final navigator = Navigator.of(context);
    final route = DialogRoute<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const LText('Xác nhận hủy đơn thuốc'),
        content: SingleChildScrollView(
          child: LText(
            'Bạn đang hủy đơn thuốc này.\nLý do: ${command.reason}\nThuốc đã giữ cho đơn sẽ được trả lại kho.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const LText('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const LText('Xác nhận hủy'),
          ),
        ],
      ),
    );
    confirmation = route;
    dialogNavigator = navigator;
    final accepted = await navigator.push(route);
    await route.completed;
    confirmation = null;
    if (!mounted) return;
    if (accepted != true) {
      setState(() => cancelling = false);
      return;
    }
    try {
      final result = await widget.repository.cancelPrescription(
        widget.id,
        command,
      );
      if (!mounted) return;
      setState(() {
        receipt = result;
        cancelLocked = true;
      });
      await _load();
    } catch (failure) {
      if (!mounted) return;
      setState(() {
        cancelError = failure;
        // Every ambiguous/conflicting result requires a read, never a resend.
        cancelLocked =
            failure is! ApiFailure ||
            failure.status == null ||
            failure.status == 401 ||
            failure.status == 403 ||
            failure.status == 408 ||
            failure.status == 409 ||
            failure.status! >= 500;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        if (!cancelLocked &&
            failure is ApiFailure &&
            failure.fields.containsKey('reason')) {
          reasonFocus.requestFocus();
        } else {
          cancelFeedbackFocus.requestFocus();
        }
      });
    } finally {
      if (mounted) setState(() => cancelling = false);
    }
  }

  Future<void> _dispense() async {
    final snapshot = rx;
    final mode = ref.read(pharmacyModeProvider);
    final dispenser = ref.read(prescriptionDispenserProvider);
    if (commandBusy ||
        otherCommandBlocked ||
        cancelLocked ||
        receipt != null ||
        snapshot == null ||
        dispenser == null ||
        ref.read(pharmacyAccessProvider)?.canManageDrugs != true ||
        !canDispensePrescription(snapshot, mode)) {
      return;
    }
    setState(() {
      dispensing = true;
      dispenseError = null;
    });
    final navigator = Navigator.of(context);
    final route = DialogRoute<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const LText('Xác nhận xuất thuốc'),
        content: SingleChildScrollView(
          child: LText(
            'Bạn đang xuất thuốc cho đơn này.\n'
            '${snapshot.lines.length} thuốc trong đơn. Hệ thống sẽ kiểm tra thanh toán và tồn kho trước khi xuất.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const LText('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const LText('Xác nhận xuất'),
          ),
        ],
      ),
    );
    confirmation = route;
    dialogNavigator = navigator;
    final accepted = await navigator.push(route);
    await route.completed;
    confirmation = null;
    if (!mounted) return;
    if (accepted != true) {
      setState(() => dispensing = false);
      return;
    }
    try {
      final result = await DispensePrescription(dispenser)(snapshot, mode);
      if (!mounted) return;
      setState(() {
        dispenseReceipt = result;
        dispenseLocked = true;
      });
      await _load();
    } catch (failure) {
      if (!mounted) return;
      setState(() {
        dispenseError = failure;
        dispenseRecheck = true;
        dispenseLocked =
            failure is! ApiFailure ||
            failure.status == null ||
            failure.status == 401 ||
            failure.status == 403 ||
            failure.status == 404 ||
            failure.status == 408 ||
            failure.status == 409 ||
            failure.status! >= 500;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) cancelFeedbackFocus.requestFocus();
      });
    } finally {
      if (mounted) setState(() => dispensing = false);
    }
  }

  Future<void> _load() async {
    final ticket = ++generation;
    setState(() {
      loading = true;
      rx = null;
      error = null;
    });
    try {
      final result = await ReadPrescription(widget.repository)(widget.id);
      if (mounted && ticket == generation) {
        setState(() {
          rx = result;
          if (!dispenseLocked) dispenseRecheck = false;
        });
        widget.onLoaded?.call();
      }
    } catch (failure) {
      if (mounted && ticket == generation) {
        setState(() => error = failure);
        errorFocus.requestFocus();
      }
    } finally {
      if (mounted && ticket == generation) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      if (receipt != null ||
          cancelError != null ||
          dispenseReceipt != null ||
          dispenseError != null)
        Flexible(
          flex: 2,
          child: SingleChildScrollView(
            child: Focus(
              focusNode: cancelFeedbackFocus,
              child: Column(
                children: [
                  if (dispenseReceipt != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Semantics(
                        liveRegion: true,
                        child: Column(
                          children: [
                            LText(
                              dispenseReceipt!.status ==
                                      DispenseStatus.dispensed
                                  ? 'Đã xuất thuốc thành công.'
                                  : 'Chưa xuất được thuốc. Vui lòng kiểm tra đơn và tồn kho.',
                            ),
                            if (dispenseReceipt!.dispensedAt != null)
                              LText(
                                'Xuất lúc: ${pharmacyDateTime(dispenseReceipt!.dispensedAt!)}',
                              ),
                            if (dispenseReceipt!.dispensedBy != null)
                              if (dispenseReceipt!.dispensedActorType ==
                                  DispenseActorType.staff)
                                ref
                                            .watch(pharmacyAccessProvider)
                                            ?.canPrescribe ==
                                        true
                                    ? ReferenceLabel(
                                        kind: ReferenceKind.staff,
                                        id: dispenseReceipt!.dispensedBy!,
                                        label: 'Người xuất',
                                      )
                                    : const LText(
                                        'Người xuất: Nhân viên phụ trách',
                                      )
                              else
                                LText(
                                  'Người xuất: ${switch (dispenseReceipt!.dispensedActorType) {
                                    DispenseActorType.account => 'Tài khoản',
                                    DispenseActorType.system => 'Hệ thống',
                                    _ => 'Chưa xác định',
                                  }}',
                                ),
                            if (dispenseReceipt!.dispensedActorType != null)
                              LText(
                                'Loại người xuất: ${switch (dispenseReceipt!.dispensedActorType!) {
                                  DispenseActorType.staff => 'Nhân sự',
                                  DispenseActorType.account => 'Tài khoản',
                                  DispenseActorType.system => 'Hệ thống',
                                  DispenseActorType.legacyUnknown => 'Chưa xác định',
                                }}',
                              ),
                          ],
                        ),
                      ),
                    ),
                  if (dispenseError != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Semantics(
                        liveRegion: true,
                        child: Column(
                          children: [
                            PharmacyRequestError(dispenseError!),
                            const LText(
                              'Chưa xác nhận đã xuất. Làm mới đơn để kiểm tra; không tự gửi lại hoặc đánh dấu đã thanh toán.',
                            ),
                            if (dispenseLocked)
                              const LText(
                                'Tạm dừng thao tác mới. Hãy kiểm tra lịch sử đơn hoặc liên hệ người phụ trách để xác nhận kết quả.',
                              ),
                          ],
                        ),
                      ),
                    ),
                  if (receipt != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Semantics(
                        liveRegion: true,
                        child: LText(
                          'Đã hủy đơn thuốc. Thuốc đã giữ cho đơn được trả lại kho.',
                        ),
                      ),
                    ),
                  if (cancelError != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Semantics(
                        liveRegion: true,
                        child: Column(
                          children: [
                            cancelError is String
                                ? LText(cancelError as String)
                                : PharmacyRequestError(cancelError!),
                            if (cancelLocked)
                              const LText(
                                'Hãy làm mới đơn để kiểm tra kết quả. Nếu vẫn chưa rõ, vui lòng liên hệ người phụ trách.',
                              ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      Expanded(flex: 3, child: _content(context)),
    ],
  );

  Widget _content(BuildContext context) {
    if (loading) {
      return Center(
        child: MediFlowLoader(
          semanticLabel: AppL10n.text(context, 'Đang tải đơn thuốc'),
        ),
      );
    }
    if (error != null) {
      final failure = error;
      final retry =
          failure is ApiFailure &&
          (failure.status == null ||
              failure.status! >= 500 ||
              failure.status == 429);
      return SingleChildScrollView(
        child: Focus(
          focusNode: errorFocus,
          child: Semantics(
            liveRegion: true,
            child: PharmacyRequestError(error!, onRetry: retry ? _load : null),
          ),
        ),
      );
    }
    final result = rx!;
    final mode = ref.watch(pharmacyModeProvider);
    return RefreshIndicator(
      onRefresh: () async {
        if (!commandBusy) await _load();
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          if (mode == PharmacyMode.unavailable)
            const LText('Chức năng đơn thuốc hiện chưa sẵn sàng.'),
          if (result.recordId != null &&
              ref.watch(pharmacyAccessProvider)?.canPrescribe == true)
            ReferenceLabel(
              kind: ReferenceKind.record,
              id: result.recordId!,
              label: 'Hồ sơ',
            )
          else if (result.recordId == null)
            const LText('Hồ sơ: Chưa cung cấp'),
          if (ref.watch(pharmacyAccessProvider)?.canPrescribe == true) ...[
            PharmacyIdentityLabel(
              kind: PharmacyIdentityKind.patient,
              id: result.patientId,
              label: 'Bệnh nhân',
            ),
            PharmacyIdentityLabel(
              kind: PharmacyIdentityKind.doctor,
              id: result.doctorId,
              label: 'Bác sĩ kê đơn',
            ),
            PharmacyIdentityLabel(
              kind: PharmacyIdentityKind.department,
              id: result.departmentId,
              label: 'Khoa',
            ),
          ] else
            const LText(
              'Thông tin người bệnh và bác sĩ được giới hạn theo quyền truy cập.',
            ),
          LText('Ngày kê đơn: ${pharmacyDate(result.prescribedDate)}'),
          LText('Trạng thái đơn: ${prescriptionStatusText(result.status)}'),
          LText(
            'Trạng thái xuất: ${switch (result.dispenseStatus) {
              DispenseStatus.pending => 'Chờ xuất',
              DispenseStatus.dispensed => 'Đã xuất',
              DispenseStatus.failed => 'Thất bại',
              DispenseStatus.cancelled => 'Đã hủy',
              DispenseStatus.expired => 'Hết hạn',
            }}',
          ),
          if (mode == PharmacyMode.legacy)
            LText(
              'Thanh toán đã ghi nhận: ${result.paymentConfirmed ? 'Có' : 'Chưa'}',
            ),
          if (mode == PharmacyMode.outpatient)
            const LText(
              'Việc xuất thuốc sẽ được kiểm tra theo thanh toán và tồn kho hiện tại.',
            ),
          LText('Tổng tiền: ${result.totalAmount} đồng'),
          if (ref.watch(pharmacyAccessProvider)?.canPrescribe == true &&
              widget.repository is PrescriptionEditor &&
              canEditPrescription(result, mode))
            OutlinedButton.icon(
              onPressed: commandBusy || otherCommandBlocked || cancelLocked
                  ? null
                  : _edit,
              icon: const Icon(Icons.edit_outlined),
              label: const LText('Sửa đơn thuốc'),
            ),
          for (final line in result.lines)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LText(line.drugName ?? 'Thuốc chưa có tên hiển thị'),
                    LText('Số lượng: ${line.quantity}'),
                    LText('Đơn giá: ${line.unitPrice} đồng'),
                    LText('Cách dùng: ${line.dosage ?? 'Chưa cung cấp'}'),
                    if (line.note?.trim().isNotEmpty ?? false)
                      LText('Ghi chú: ${line.note}'),
                    LText('Thành tiền: ${line.lineTotal} đồng'),
                  ],
                ),
              ),
            ),
          if (result.cancelledAt != null)
            LText('Hủy lúc: ${pharmacyDateTime(result.cancelledAt!)}'),
          if (result.cancelledBy != null &&
              ref.watch(pharmacyAccessProvider)?.canPrescribe == true)
            ReferenceLabel(
              kind: ReferenceKind.staff,
              id: result.cancelledBy!,
              label: 'Người hủy',
            ),
          if (result.cancellationReason != null)
            LText('Lý do hủy: ${result.cancellationReason}'),
          LText('Cập nhật: ${pharmacyDateTime(result.updatedAt)}'),
          ExpansionTile(
            title: const LText('Thông tin tra cứu'),
            children: [
              SelectableText('Đơn thuốc: ${result.id}'),
              if (result.recordId != null)
                SelectableText('Hồ sơ: ${result.recordId}'),
              SelectableText('Mã bệnh nhân: ${result.patientId}'),
              SelectableText('Mã bác sĩ: ${result.doctorId}'),
              SelectableText('Mã khoa: ${result.departmentId}'),
              for (var i = 0; i < result.lines.length; i++) ...[
                SelectableText(
                  'Mã thuốc trong đơn ${i + 1}: ${result.lines[i].id}',
                ),
                SelectableText('Mã thuốc: ${result.lines[i].drugId}'),
              ],
              if (result.cancelledBy != null)
                SelectableText('Mã người hủy: ${result.cancelledBy}'),
              if (dispenseReceipt != null) ...[
                SelectableText('Phiếu: ${dispenseReceipt!.id}'),
                SelectableText(
                  'Đơn thuốc của phiếu: ${dispenseReceipt!.prescriptionId}',
                ),
                if (dispenseReceipt!.dispensedBy != null)
                  SelectableText(
                    'Mã người xuất: ${dispenseReceipt!.dispensedBy}',
                  ),
              ],
              if (receipt != null)
                SelectableText(
                  'Đơn thuốc của phiếu hủy: ${receipt!.prescriptionId}',
                ),
            ],
          ),
          if (ref.watch(pharmacyAccessProvider)?.canPrescribe == true &&
              mode != PharmacyMode.unavailable &&
              canCancelPrescription(result) &&
              receipt == null) ...[
            TextField(
              controller: reason,
              focusNode: reasonFocus,
              enabled: !commandBusy && !cancelLocked && !otherCommandBlocked,
              maxLength: 500,
              decoration: InputDecoration(
                labelText: AppL10n.text(context, 'Lý do hủy đơn'),
                border: const OutlineInputBorder(),
                errorText: AppL10n.text(
                  context,
                  cancelError is ApiFailure
                      ? (cancelError as ApiFailure).fields.containsKey('reason')
                            ? 'Vui lòng nhập lý do hủy đơn, tối đa 500 ký tự.'
                            : null
                      : cancelError is String
                      ? cancelError as String
                      : null,
                ),
              ),
            ),
            FilledButton(
              onPressed: commandBusy || cancelLocked || otherCommandBlocked
                  ? null
                  : _cancel,
              child: LText(cancelling ? 'Đang xử lý hủy' : 'Hủy đơn thuốc'),
            ),
          ],
          if (ref.watch(pharmacyAccessProvider)?.canManageDrugs == true &&
              mode != PharmacyMode.unavailable) ...[
            if (mode == PharmacyMode.legacy && !result.paymentConfirmed)
              const LText('Cần xác nhận thanh toán trước khi xuất thuốc.'),
            if (canDispensePrescription(result, mode) &&
                dispenseReceipt == null &&
                receipt == null) ...[
              if (ref.watch(prescriptionDispenserProvider) == null)
                const LText(
                  'Chức năng xuất thuốc hiện chưa sẵn sàng. Vui lòng thử lại sau.',
                ),
              FilledButton(
                onPressed:
                    commandBusy ||
                        otherCommandBlocked ||
                        cancelLocked ||
                        ref.watch(prescriptionDispenserProvider) == null
                    ? null
                    : _dispense,
                child: LText(dispensing ? 'Đang xử lý xuất' : 'Xuất thuốc'),
              ),
            ],
          ],
          OutlinedButton.icon(
            onPressed: commandBusy ? null : _load,
            icon: const Icon(Icons.refresh),
            label: const LText('Làm mới đơn thuốc'),
          ),
        ],
      ),
    );
  }
}
