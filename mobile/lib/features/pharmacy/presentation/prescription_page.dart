import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/prescription.dart';
import '../domain/prescription_commands.dart';
import '../domain/drug_commands.dart';
import 'pharmacy_boundary.dart';
import 'providers.dart';

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
        validation = 'Mã đơn thuốc phải là UUID hợp lệ.';
        submitted = null;
      });
      focus.requestFocus();
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      submitted = input;
      validation = null;
      submission++;
    });
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: id,
              focusNode: focus,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                labelText: 'Mã đơn thuốc (UUID)',
                errorText: validation,
                border: const OutlineInputBorder(),
              ),
              onSubmitted: (_) => _lookup(),
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: _lookup,
              child: const Text('Tra cứu đơn thuốc'),
            ),
          ],
        ),
      ),
      Expanded(
        child: submitted == null
            ? const Center(child: Text('Nhập mã để mở đơn thuốc.'))
            : _PrescriptionDetail(
                key: ValueKey((submitted, submission)),
                id: submitted!,
                repository: widget.repository,
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
  });
  final String id;
  final PharmacyRepository repository;
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
  bool get commandBusy => cancelling || dispensing;
  bool get otherCommandBlocked =>
      dispenseLocked || dispenseRecheck || dispenseReceipt != null;
  DialogRoute<bool>? confirmation;
  NavigatorState? dialogNavigator;
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
        title: const Text('Xác nhận hủy đơn thuốc'),
        content: SingleChildScrollView(
          child: Text(
            'Đơn: ${widget.id}\nLý do: ${command.reason}\nBackend kiểm tra quyền bác sĩ và giải phóng reservation.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận hủy'),
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
        title: const Text('Xác nhận xuất thuốc'),
        content: SingleChildScrollView(
          child: Text(
            'Đơn: ${snapshot.id}\n'
            '${snapshot.lines.length} dòng thuốc. Backend kiểm tra trạng thái, tồn kho và '
            '${mode == PharmacyMode.outpatient ? 'clearance Billing hiện tại' : 'thanh toán V0'}. '
            'Chỉ phiếu máy chủ xác nhận mới là kết quả xuất.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Quay lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận xuất'),
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
                            Text(
                              dispenseReceipt!.status ==
                                      DispenseStatus.dispensed
                                  ? 'Máy chủ xác nhận đã xuất thuốc.'
                                  : 'Máy chủ ghi nhận xuất thất bại: ${dispenseReceipt!.failureReason}',
                            ),
                            SelectableText('Phiếu: ${dispenseReceipt!.id}'),
                            if (dispenseReceipt!.dispensedAt != null)
                              Text(
                                'Xuất lúc: ${dispenseReceipt!.dispensedAt!.toIso8601String()}',
                              ),
                            if (dispenseReceipt!.dispensedBy != null)
                              SelectableText(
                                'Người xuất: ${dispenseReceipt!.dispensedBy}',
                              ),
                            if (dispenseReceipt!.dispensedActorType != null)
                              Text(
                                'Loại người xuất: ${switch (dispenseReceipt!.dispensedActorType!) {
                                  DispenseActorType.staff => 'Nhân sự',
                                  DispenseActorType.account => 'Tài khoản',
                                  DispenseActorType.system => 'Hệ thống',
                                  DispenseActorType.legacyUnknown => 'Legacy chưa xác định',
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
                            RequestError(dispenseError!),
                            const Text(
                              'Chưa xác nhận đã xuất. Làm mới đơn để kiểm tra; không tự gửi lại hoặc đánh dấu đã thanh toán.',
                            ),
                            if (dispenseLocked)
                              const Text(
                                'Thao tác đang khóa để đối soát kết quả; tải lại không chứng minh lệnh trước thất bại.',
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
                        child: Text(
                          'Máy chủ đã hủy đơn ${receipt!.prescriptionId}. Reservation giải phóng: ${receipt!.releasedReservations}.',
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
                                ? Text(cancelError as String)
                                : RequestError(cancelError!),
                            if (cancelLocked)
                              const Text(
                                'Chưa gửi lại lệnh. Làm mới để kiểm tra kết quả; liên hệ đối soát nếu chưa rõ.',
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
      return const Center(
        child: CircularProgressIndicator(semanticsLabel: 'Đang tải đơn thuốc'),
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
            child: RequestError(error!, onRetry: retry ? _load : null),
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
          SelectableText('Đơn thuốc: ${result.id}'),
          Text(
            'Chế độ ứng dụng: ${switch (mode) {
              PharmacyMode.outpatient => 'Ngoại trú V1',
              PharmacyMode.legacy => 'Tương thích V0',
              PharmacyMode.unavailable => 'Chưa được cấu hình — thao tác đơn chưa sẵn sàng',
            }}',
          ),
          SelectableText('Hồ sơ: ${result.recordId ?? 'Chưa cung cấp'}'),
          SelectableText('Bệnh nhân: ${result.patientId}'),
          SelectableText('Bác sĩ: ${result.doctorId}'),
          SelectableText('Khoa: ${result.departmentId}'),
          Text('Ngày kê đơn: ${result.prescribedDate}'),
          Text(
            'Trạng thái đơn: ${switch (result.status) {
              PrescriptionStatus.active => 'Đang hoạt động',
              PrescriptionStatus.fulfilled => 'Đã hoàn tất',
              PrescriptionStatus.cancelled => 'Đã hủy',
              PrescriptionStatus.expired => 'Hết hạn',
              PrescriptionStatus.dispenseFailed => 'Xuất thuốc thất bại',
            }}',
          ),
          Text(
            'Trạng thái xuất: ${switch (result.dispenseStatus) {
              DispenseStatus.pending => 'Chờ xuất',
              DispenseStatus.dispensed => 'Đã xuất',
              DispenseStatus.failed => 'Thất bại',
              DispenseStatus.cancelled => 'Đã hủy',
              DispenseStatus.expired => 'Hết hạn',
            }}',
          ),
          Text(
            'Cờ thanh toán legacy: ${result.paymentConfirmed ? 'Đã xác nhận' : 'Chưa xác nhận'}',
          ),
          if (mode == PharmacyMode.outpatient)
            const Text(
              'Quyền xuất V1 và clearance do backend kiểm tra khi thực hiện lệnh. Cờ legacy không xác định quyền xuất.',
            ),
          Text('Tổng tiền máy chủ: ${result.totalAmount} VND'),
          for (final line in result.lines)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(line.drugName ?? 'Thuốc chưa có tên hiển thị'),
                    SelectableText('Mã thuốc: ${line.drugId}'),
                    Text('Số lượng: ${line.quantity}'),
                    Text('Đơn giá: ${line.unitPrice} VND'),
                    Text('Liều dùng: ${line.dosage ?? 'Chưa cung cấp'}'),
                    Text('Thành tiền: ${line.lineTotal} VND'),
                  ],
                ),
              ),
            ),
          if (result.cancelledAt != null)
            Text('Hủy lúc: ${result.cancelledAt!.toIso8601String()}'),
          if (result.cancelledBy != null)
            SelectableText('Người hủy: ${result.cancelledBy}'),
          if (result.cancellationReason != null)
            Text('Lý do hủy: ${result.cancellationReason}'),
          Text('Cập nhật: ${result.updatedAt.toIso8601String()}'),
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
                labelText: 'Lý do hủy đơn',
                border: const OutlineInputBorder(),
                errorText: cancelError is ApiFailure
                    ? (cancelError as ApiFailure).fields['reason']
                    : cancelError is String
                    ? cancelError as String
                    : null,
              ),
            ),
            FilledButton(
              onPressed: commandBusy || cancelLocked || otherCommandBlocked
                  ? null
                  : _cancel,
              child: Text(cancelling ? 'Đang xử lý hủy' : 'Hủy đơn thuốc'),
            ),
          ],
          if (ref.watch(pharmacyAccessProvider)?.canManageDrugs == true &&
              mode != PharmacyMode.unavailable) ...[
            if (mode == PharmacyMode.legacy && !result.paymentConfirmed)
              const Text(
                'Tương thích V0 yêu cầu xác nhận thanh toán trước khi xuất.',
              ),
            if (canDispensePrescription(result, mode) &&
                dispenseReceipt == null &&
                receipt == null) ...[
              if (ref.watch(prescriptionDispenserProvider) == null)
                const Text(
                  'Chưa thể xuất: đang chờ client Gateway hỗ trợ PUT không body.',
                ),
              FilledButton(
                onPressed:
                    commandBusy ||
                        otherCommandBlocked ||
                        cancelLocked ||
                        ref.watch(prescriptionDispenserProvider) == null
                    ? null
                    : _dispense,
                child: Text(dispensing ? 'Đang xử lý xuất' : 'Xuất thuốc'),
              ),
            ],
          ],
          OutlinedButton.icon(
            onPressed: commandBusy ? null : _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Làm mới đơn thuốc'),
          ),
        ],
      ),
    );
  }
}
