import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/reference_widgets.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/organization.dart';
import 'organization_ui.dart';
import '../domain/profile_account.dart';
import 'profile_account_panel.dart';

final accountDetailRepositoryProvider = Provider<AccountDetailRepository?>(
  (ref) => null,
);

String _localTime(String? raw) {
  final value = raw == null ? null : DateTime.tryParse(raw)?.toLocal();
  if (value == null) return 'Chưa có';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(value.hour)}:${two(value.minute)} '
      '${two(value.day)}/${two(value.month)}/${value.year}';
}

/// ADMIN view of one account: identity, access state and credential actions.
class AccountDetailPage extends ConsumerStatefulWidget {
  const AccountDetailPage({super.key, required this.accountId});

  final String accountId;

  @override
  ConsumerState<AccountDetailPage> createState() => _AccountDetailPageState();
}

class _AccountDetailPageState extends ConsumerState<AccountDetailPage> {
  Account? _account;
  Object? _loadError, _commandError;
  String? _confirmation, _notice;
  bool _busy = false, _unknown = false;

  AccountDetailRepository get _repository =>
      ref.read(accountDetailRepositoryProvider)!;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _account = null;
      _loadError = null;
      _commandError = null;
      _confirmation = null;
      _notice = null;
      _unknown = false;
    });
    try {
      final account = await _repository.account(widget.accountId);
      if (mounted) setState(() => _account = account);
    } catch (error) {
      if (mounted) setState(() => _loadError = error);
    }
  }

  Future<void> _setStatus(bool active, {bool replace = false}) async {
    if (_busy || _unknown) return;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
    });
    try {
      final account = await _repository.setAccountStatus(
        widget.accountId,
        active,
        replaceActiveAccount: replace,
      );
      if (mounted) {
        setState(() {
          _account = account;
          _confirmation = null;
          _notice = account.active
              ? 'Đã chuyển tài khoản sang đang hoạt động.'
              : 'Đã khóa tài khoản.';
        });
      }
    } catch (error) {
      if (!mounted) return;
      if (error is ApiFailure &&
          error.status == 409 &&
          error.code.endsWith('ACCOUNT_OWNER_ACTIVE')) {
        setState(() => _confirmation = error.message);
        return;
      }
      final unknown =
          error is! ApiFailure ||
          error.status == null ||
          error.status == 408 ||
          error.status! >= 500;
      setState(() {
        _confirmation = null;
        _unknown = unknown;
        _commandError = unknown
            ? const ApiFailure(
                'Chưa xác nhận được kết quả cập nhật. Hãy tải lại trạng thái trước khi thực hiện tiếp.',
              )
            : error;
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _field(String label, String value) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LText(label, style: Theme.of(context).textTheme.labelMedium),
        SelectableText(value),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) {
    final access = ref.watch(workspaceAccessProvider);
    if (access?.role != 'ADMIN') {
      return const Center(child: LText('Bạn không có quyền xem tài khoản.'));
    }
    final account = _account;
    if (_loadError != null) {
      return SingleChildScrollView(
        child: RequestError(_loadError!, onRetry: _load),
      );
    }
    if (account == null) {
      return const Center(child: MediFlowLoader());
    }
    final owner = account.staffId != null
        ? ProfileAccountOwner.staff(
            id: account.staffId!,
            jobTitle: null,
            active: true,
          )
        : account.patientId != null
        ? ProfileAccountOwner.patient(id: account.patientId!)
        : null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        LText(account.username, style: Theme.of(context).textTheme.titleLarge),
        LText(accountRoleLabel(account.role)),
        const SizedBox(height: 12),
        _field('Trạng thái', account.active ? 'Đang hoạt động' : 'Đã khóa'),
        if (account.staffId case final staffId?)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: ReferenceLabel(
              kind: ReferenceKind.staff,
              id: staffId,
              label: 'Nhân viên',
            ),
          )
        else if (account.patientId case final patientId?)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: ReferenceLabel(
              kind: ReferenceKind.patient,
              id: patientId,
              label: 'Bệnh nhân',
            ),
          )
        else
          _field('Hồ sơ liên kết', 'Chưa liên kết'),
        _field('Mã tài khoản', account.id),
        _field('Lần đăng nhập gần nhất', _localTime(account.lastLoginAt)),
        _field('Ngày tạo', _localTime(account.createdAt)),
        _field('Cập nhật lần cuối', _localTime(account.updatedAt)),
        const Divider(height: 24),
        LText(
          'Trạng thái truy cập',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        const LText(
          'Tài khoản bị khóa không thể đăng nhập hoặc gia hạn phiên. Phiên đã cấp sẽ bị từ chối ở yêu cầu tiếp theo.',
        ),
        const SizedBox(height: 8),
        if (_commandError != null) RequestError(_commandError!),
        if (_unknown)
          OutlinedButton(
            onPressed: _load,
            child: const LText('Tải lại trạng thái'),
          )
        else if (_confirmation != null) ...[
          LText(
            'Xác nhận mở lại tài khoản',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          LText(_confirmation!),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton(
                onPressed: _busy
                    ? null
                    : () => setState(() => _confirmation = null),
                child: const LText('Hủy'),
              ),
              FilledButton(
                onPressed: _busy ? null : () => _setStatus(true, replace: true),
                child: const LText('Tiếp tục mở lại tài khoản'),
              ),
            ],
          ),
        ] else
          FilledButton(
            onPressed: _busy ? null : () => _setStatus(!account.active),
            child: LText(
              account.active ? 'Khóa tài khoản' : 'Mở lại tài khoản',
            ),
          ),
        if (_notice != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: LText(_notice!),
          ),
        if (account.role != 'SYSTEM' &&
            owner != null &&
            access != null &&
            !_busy &&
            !_unknown &&
            _confirmation == null) ...[
          const Divider(height: 32),
          ProfileAccountPanel(
            key: ValueKey((account.id, account.username)),
            owner: owner,
            access: access,
            onChanged: _load,
          ),
        ],
      ],
    );
  }
}
