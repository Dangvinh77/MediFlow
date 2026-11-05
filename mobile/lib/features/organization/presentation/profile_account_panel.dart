import 'package:flutter/material.dart';

import '../../../core/theme/medical_colors.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/organization.dart';
import '../domain/profile_account.dart';
import 'providers.dart';

class ProfileAccountPanel extends ConsumerStatefulWidget {
  const ProfileAccountPanel({
    super.key,
    required this.owner,
    required this.access,
    this.profileEmail,
    this.onChanged,
  });

  final ProfileAccountOwner owner;
  final WorkspaceAccess access;
  final String? profileEmail;
  final VoidCallback? onChanged;

  @override
  ConsumerState<ProfileAccountPanel> createState() =>
      _ProfileAccountPanelState();
}

class _ProfileAccountPanelState extends ConsumerState<ProfileAccountPanel> {
  final _email = TextEditingController();
  final _emailReason = TextEditingController();
  ProfileAccountSnapshot? _profile;
  ProfileMailDelivery? _delivery;
  Object? _loadError;
  Object? _deliveryError;
  Object? _commandError;
  String? _notice;
  late ProfileAccountIntentScope _intentScope;
  late ProfileAccountPendingIntents _intents;
  bool _emailEdited = false;
  bool _loading = true;
  bool _busy = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _bindIntentScope();
    _load();
  }

  @override
  void didUpdateWidget(covariant ProfileAccountPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    final scopeChanged =
        oldWidget.owner.id != widget.owner.id ||
        oldWidget.owner.type != widget.owner.type ||
        oldWidget.access.sessionIdentity != widget.access.sessionIdentity;
    if (scopeChanged) _bindIntentScope();
    if (scopeChanged ||
        oldWidget.profileEmail != widget.profileEmail ||
        oldWidget.owner.active != widget.owner.active) {
      _emailEdited = false;
      _load();
    }
  }

  void _bindIntentScope() {
    _intentScope = ProfileAccountIntentScope(
      sessionIdentity: widget.access.sessionIdentity,
      ownerType: widget.owner.type,
      ownerId: widget.owner.id,
    );
    _intents = ref
        .read(profileAccountIntentStoreProvider)
        .forScope(_intentScope);
  }

  @override
  void dispose() {
    _generation++;
    _email.dispose();
    _emailReason.dispose();
    super.dispose();
  }

  bool get _allowed => _canManage(widget.owner, widget.access.role);

  bool _canIssue(ProfileAccountSnapshot profile) {
    final email = profile.profileEmail?.trim();
    return widget.owner.active &&
        profile.account == null &&
        !profile.emailSyncPending &&
        email != null &&
        isProfileAccountEmail(email);
  }

  bool _canManageCredentials(ProfileAccountSnapshot profile, Account account) {
    if (!widget.owner.active || !account.active || profile.emailSyncPending) {
      return false;
    }
    if (!widget.owner.isPatient) return true;
    final profileEmail = profile.profileEmail?.trim().toLowerCase();
    return profileEmail != null &&
        profileEmail.isNotEmpty &&
        profileEmail == account.username.trim().toLowerCase();
  }

  String? _credentialBlockReason(
    ProfileAccountSnapshot profile,
    Account account,
  ) {
    if (!widget.owner.active) {
      return widget.owner.isPatient
          ? 'Hồ sơ bệnh nhân đã lưu trữ. Thông tin tài khoản ở chế độ chỉ đọc.'
          : 'Nhân viên đã ngừng hoạt động. Không thể đổi mật khẩu.';
    }
    if (!account.active) return 'Tài khoản đã khóa. Không thể đổi mật khẩu.';
    if (profile.emailSyncPending) {
      return 'Email đăng nhập đang được đồng bộ. Hãy tải lại trước khi gửi thư.';
    }
    if (widget.owner.isPatient) {
      final profileEmail = profile.profileEmail?.trim().toLowerCase();
      if (profileEmail == null || profileEmail.isEmpty) {
        return 'Hồ sơ chưa có email. Cập nhật email rồi tải lại trước khi gửi thư.';
      }
      if (profileEmail != account.username.trim().toLowerCase()) {
        return 'Email hồ sơ và email đăng nhập chưa khớp. Cập nhật hồ sơ rồi tải lại trước khi gửi thư.';
      }
    }
    return null;
  }

  bool _isCurrent(Object sessionIdentity) {
    if (!mounted || widget.access.sessionIdentity != sessionIdentity) {
      return false;
    }
    final access = ref.read(workspaceAccessProvider);
    return access?.sessionIdentity == sessionIdentity &&
        access?.role == widget.access.role &&
        _canManage(widget.owner, access?.role);
  }

  Future<void> _load() async {
    if (!_allowed) return;
    final generation = ++_generation;
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _loading = true;
      _profile = null;
      _delivery = null;
      _loadError = null;
      _deliveryError = null;
      _commandError = null;
    });
    try {
      final repository = ref.read(profileAccountRepositoryProvider);
      final profile = widget.owner.isPatient
          ? await ReadProfileAccount(repository).patient(widget.owner.id)
          : await ReadProfileAccount(repository).staff(widget.owner.id);
      if (!_matches(generation, sessionIdentity)) return;
      setState(() {
        _profile = profile;
        if (!_emailEdited) {
          _email.text =
              _intents.emailOutcomeUnknown &&
                  _intents.pendingEmailTarget != null
              ? _intents.pendingEmailTarget!
              : profile.account?.username ?? profile.profileEmail ?? '';
          _emailEdited = _intents.emailOutcomeUnknown;
        }
        if (profile.account != null) {
          _intents.issueKey = null;
          _intents.issueOutcomeUnknown = false;
        }
        if (profile.account?.username.toLowerCase() ==
                _intents.pendingEmailTarget?.toLowerCase() &&
            !profile.emailSyncPending) {
          _intents.pendingEmailTarget = null;
          _intents.emailOutcomeUnknown = false;
        }
      });
      final account = profile.account;
      if (account == null) return;
      try {
        final delivery = widget.owner.isPatient
            ? await repository.latestPatientMailDelivery(widget.owner.id)
            : await repository.latestStaffMailDelivery(account.id);
        if (_matches(generation, sessionIdentity)) {
          setState(() => _delivery = delivery);
        }
      } catch (error) {
        if (_matches(generation, sessionIdentity)) {
          setState(() => _deliveryError = error);
        }
      }
    } catch (error) {
      if (_matches(generation, sessionIdentity)) {
        setState(() => _loadError = error);
      }
    } finally {
      if (_matches(generation, sessionIdentity)) {
        setState(() => _loading = false);
      }
    }
  }

  bool _matches(int generation, Object sessionIdentity) =>
      mounted && generation == _generation && _isCurrent(sessionIdentity);

  @override
  Widget build(BuildContext context) {
    if (!_allowed) return const SizedBox.shrink();
    final currentAccess = ref.watch(workspaceAccessProvider);
    if (currentAccess == null ||
        currentAccess.sessionIdentity != widget.access.sessionIdentity ||
        currentAccess.role != widget.access.role) {
      return const _AccountPanelCard(
        child: Text('Phiên đăng nhập đã thay đổi. Tải lại hồ sơ để tiếp tục.'),
      );
    }
    if (_loading && _profile == null) {
      return const _AccountPanelCard(
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_loadError != null && _profile == null) {
      return _AccountPanelCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Không tải được thông tin tài khoản.',
              style: TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            RequestError(
              _loadError!,
              onRetry: _canRetry(_loadError) ? _load : null,
            ),
          ],
        ),
      );
    }
    final profile = _profile;
    if (profile == null) return const SizedBox.shrink();
    final account = profile.account;
    return _AccountPanelCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Tài khoản đăng nhập',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              IconButton(
                tooltip: 'Tải lại thông tin tài khoản',
                onPressed: _busy ? null : _load,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_notice != null) ...[
            const SizedBox(height: 8),
            _StatusMessage(
              _notice!,
              color: Theme.of(context).colorScheme.primary,
            ),
          ],
          if (_commandError != null) ...[
            const SizedBox(height: 8),
            _StatusMessage(
              _errorText(_commandError!),
              color: Theme.of(context).colorScheme.error,
              isError: true,
            ),
          ],
          if (profile.emailSyncPending) ...[
            const SizedBox(height: 8),
            _StatusMessage(
              'Email hồ sơ đã đổi. Email đăng nhập đang được đồng bộ; tải lại để kiểm tra trạng thái mới nhất.',
              color: context.medical.warning,
            ),
          ],
          const SizedBox(height: 8),
          if (account == null)
            _buildNoAccount(context, profile)
          else
            _buildExistingAccount(context, profile, account),
          if (_busy) ...[
            const SizedBox(height: 12),
            const LinearProgressIndicator(),
          ],
        ],
      ),
    );
  }

  Widget _buildNoAccount(BuildContext context, ProfileAccountSnapshot profile) {
    final email = profile.profileEmail?.trim();
    final hasEmail = email != null && isProfileAccountEmail(email);
    final canIssue = _canIssue(profile);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Chưa có tài khoản đăng nhập',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 6),
        Text(
          'Email hồ sơ: ${email?.isNotEmpty == true ? email : 'Chưa có email'}',
        ),
        if (!widget.owner.active) ...[
          const SizedBox(height: 8),
          _StatusMessage(
            widget.owner.isPatient
                ? 'Hồ sơ bệnh nhân đã lưu trữ. Không thể cấp tài khoản mới.'
                : 'Nhân viên đã ngừng hoạt động. Không thể cấp tài khoản mới.',
            color: context.medical.warning,
          ),
        ] else if (!hasEmail) ...[
          const SizedBox(height: 8),
          const Text(
            'Cập nhật email hợp lệ trong hồ sơ trước khi cấp tài khoản.',
          ),
        ],
        if (_intents.issueOutcomeUnknown) ...[
          const SizedBox(height: 8),
          _StatusMessage(
            'Chưa xác nhận được lần cấp tài khoản vừa rồi. Thử lại cùng yêu cầu để đối soát, không tạo yêu cầu mới.',
            color: context.medical.warning,
          ),
        ],
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: _busy || !canIssue ? null : _issueAccount,
          icon: const Icon(Icons.mark_email_unread_outlined),
          label: Text(
            _intents.issueOutcomeUnknown
                ? 'Thử lại cùng yêu cầu cấp tài khoản'
                : 'Cấp tài khoản và gửi mật khẩu tạm',
          ),
        ),
      ],
    );
  }

  Widget _buildExistingAccount(
    BuildContext context,
    ProfileAccountSnapshot profile,
    Account account,
  ) {
    final canManageCredentials = _canManageCredentials(profile, account);
    final credentialBlockReason = _credentialBlockReason(profile, account);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _AccountValue(label: 'Email đăng nhập', value: account.username),
        _AccountValue(
          label: 'Trạng thái',
          value: account.active ? 'Đang hoạt động' : 'Đã khóa',
        ),
        _AccountValue(label: 'Vai trò', value: _roleName(account.role)),
        if (profile.profileEmail != null)
          _AccountValue(label: 'Email hồ sơ', value: profile.profileEmail!),
        if (_delivery != null)
          _StatusMessage(
            'Trạng thái thư gần nhất: ${_mailStatusName(_delivery!.status)}',
            color: _mailColor(context, _delivery!.status),
          ),
        if (_deliveryError != null) ...[
          _StatusMessage(
            'Chưa tải được trạng thái email: ${_errorText(_deliveryError!)}',
            color: Theme.of(context).colorScheme.error,
            isError: true,
          ),
          TextButton.icon(
            onPressed: _busy ? null : _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Kiểm tra trạng thái email'),
          ),
        ],
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: _busy || !canManageCredentials
                  ? null
                  : _startPasswordReset,
              icon: const Icon(Icons.lock_reset),
              label: Text(
                _intents.resetOutcomeUnknown
                    ? 'Đối soát lần cấp mật khẩu vừa rồi'
                    : 'Cấp mật khẩu tạm qua email',
              ),
            ),
            if (_delivery?.status == ProfileMailStatus.failed)
              OutlinedButton.icon(
                onPressed: _busy || !canManageCredentials ? null : _retryMail,
                icon: const Icon(Icons.mark_email_read_outlined),
                label: const Text('Gửi lại thư, không đặt lại mật khẩu'),
              ),
          ],
        ),
        if (credentialBlockReason != null) ...[
          const SizedBox(height: 8),
          _StatusMessage(credentialBlockReason, color: context.medical.warning),
        ],
        if (_intents.resetOutcomeUnknown) ...[
          const SizedBox(height: 6),
          _StatusMessage(
            'Kết quả chưa xác định. Thử lại cùng khóa để đối soát; mật khẩu không bị đặt lại thêm lần nữa.',
            color: context.medical.warning,
          ),
        ],
        if (widget.owner.active && _allowed) _buildEmailForm(context, account),
      ],
    );
  }

  Widget _buildEmailForm(BuildContext context, Account account) => Padding(
    padding: const EdgeInsets.only(top: 16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Divider(),
        Text(
          'Đổi email đăng nhập',
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 4),
        Text(
          widget.owner.isPatient
              ? 'Điều dưỡng hoặc quản trị viên có thể sửa email bệnh nhân sau khi xác minh. Email hồ sơ sẽ được đồng bộ.'
              : 'Chỉ quản trị viên được đổi email nhân viên. Email hồ sơ và tên đăng nhập sẽ được cập nhật cùng thao tác.',
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _email,
          enabled:
              !_busy &&
              !_intents.emailOutcomeUnknown &&
              !_intents.resetOutcomeUnknown,
          keyboardType: TextInputType.emailAddress,
          maxLength: 254,
          decoration: const InputDecoration(
            labelText: 'Email đăng nhập mới',
            border: OutlineInputBorder(),
          ),
          onChanged: (_) => setState(() => _emailEdited = true),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _emailReason,
          enabled:
              !_busy &&
              !_intents.emailOutcomeUnknown &&
              !_intents.resetOutcomeUnknown,
          maxLength: 500,
          decoration: const InputDecoration(
            labelText: 'Lý do thay đổi email',
            border: OutlineInputBorder(),
          ),
          onChanged: (_) => setState(() {}),
        ),
        if (_intents.emailOutcomeUnknown) ...[
          _StatusMessage(
            'Chưa xác nhận được kết quả đổi email. Kiểm tra tên đăng nhập trước khi thử lại.',
            color: context.medical.warning,
          ),
          TextButton(
            onPressed: _busy ? null : () => _checkEmailChange(account),
            child: const Text('Kiểm tra kết quả đổi email'),
          ),
        ],
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: FilledButton.tonal(
            onPressed:
                _busy ||
                    _intents.emailOutcomeUnknown ||
                    _intents.resetOutcomeUnknown ||
                    (_email.text.trim().toLowerCase() ==
                            account.username.toLowerCase() &&
                        !(widget.owner.isPatient &&
                            _profile?.emailSyncPending == true)) ||
                    !isProfileAccountEmail(_email.text) ||
                    _emailReason.text.trim().isEmpty
                ? null
                : () => _updateEmail(account),
            child: const Text('Lưu email mới'),
          ),
        ),
      ],
    ),
  );

  Future<void> _issueAccount() async {
    final profile = _profile;
    if (profile == null ||
        !_canIssue(profile) ||
        _busy ||
        !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final key = _intents.issueKey ??= ref.read(
      profileAccountIdempotencyKeyFactoryProvider,
    )();
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
      _intents.issueOutcomeUnknown = true;
    });
    try {
      final repository = ref.read(profileAccountRepositoryProvider);
      if (widget.owner.isPatient) {
        await IssuePatientProfileAccount(repository)
            .call(patientId: widget.owner.id, idempotencyKey: key);
      } else {
        final role = profileAccountRoleForJobTitle(widget.owner.jobTitle);
        final email = profile.profileEmail;
        if (role == null || email == null) {
          throw ArgumentError(
            'Không xác định được vai trò hoặc email nhân viên.',
          );
        }
        await IssueStaffProfileAccount(repository).call(
          staffId: widget.owner.id,
          username: email,
          role: role,
          idempotencyKey: key,
        );
      }
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _intents.issueKey = key;
        _intents.issueOutcomeUnknown = true;
        _notice = 'Tài khoản đã được cấp. Mật khẩu tạm được gửi qua email.';
      });
      widget.onChanged?.call();
      await _load();
    } catch (error) {
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _commandError = error;
        _intents.issueOutcomeUnknown = _outcomeMayBeUnknown(error);
        if (!_intents.issueOutcomeUnknown) _intents.issueKey = null;
      });
    } finally {
      if (_isCurrent(sessionIdentity)) setState(() => _busy = false);
    }
  }

  Future<void> _startPasswordReset() async {
    final profile = _profile;
    final account = profile?.account;
    if (profile == null ||
        account == null ||
        !_canManageCredentials(profile, account)) {
      return;
    }
    if (_intents.resetOutcomeUnknown) {
      await _resetPassword();
      return;
    }
    if (_busy || !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Consumer(
        builder: (context, ref, _) {
          final current = ref.watch(workspaceAccessProvider);
          if (current?.sessionIdentity != widget.access.sessionIdentity ||
              current?.role != widget.access.role ||
              !_canManage(widget.owner, current?.role)) {
            return AlertDialog(
              title: const Text('Phiên đăng nhập đã thay đổi'),
              content: const Text('Đóng hộp thoại này trước khi tiếp tục.'),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(dialogContext).pop(false),
                  child: const Text('Đóng'),
                ),
              ],
            );
          }
          return AlertDialog(
            title: const Text('Cấp mật khẩu tạm mới?'),
            content: Text(
              'Mật khẩu hiện tại của ${account.username} sẽ hết hiệu lực. Hướng dẫn đăng nhập sẽ được gửi đến email tài khoản.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('Hủy'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: const Text('Xác nhận đặt lại'),
              ),
            ],
          );
        },
      ),
    );
    if (confirmed == true) await _resetPassword();
  }

  Future<void> _resetPassword() async {
    final profile = _profile;
    final account = profile?.account;
    if (profile == null ||
        account == null ||
        !_canManageCredentials(profile, account) ||
        _busy ||
        !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final key = _intents.resetKey ??= ref.read(
      profileAccountIdempotencyKeyFactoryProvider,
    )();
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
      _intents.resetOutcomeUnknown = true;
    });
    try {
      final delivery = await ResetProfilePassword(
        ref.read(profileAccountRepositoryProvider),
      ).call(owner: widget.owner, accountId: account.id, idempotencyKey: key);
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _delivery = delivery;
        _intents.resetKey = null;
        _intents.resetOutcomeUnknown = false;
        _commandError = null;
        _notice = 'Đã gửi yêu cầu cấp mật khẩu tạm qua email.';
      });
    } catch (error) {
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _commandError = error;
        _intents.resetOutcomeUnknown = _outcomeMayBeUnknown(error);
        if (!_intents.resetOutcomeUnknown) _intents.resetKey = null;
      });
    } finally {
      if (_isCurrent(sessionIdentity)) setState(() => _busy = false);
    }
  }

  Future<void> _retryMail() async {
    final profile = _profile;
    final account = profile?.account;
    final delivery = _delivery;
    if (profile == null ||
        account == null ||
        !_canManageCredentials(profile, account) ||
        delivery == null ||
        delivery.status != ProfileMailStatus.failed ||
        _busy ||
        !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
    });
    try {
      final repository = ref.read(profileAccountRepositoryProvider);
      final result = widget.owner.isPatient
          ? await repository.retryPatientMailDelivery(
              widget.owner.id,
              delivery.messageId,
            )
          : await repository.retryStaffMailDelivery(
              account.id,
              delivery.messageId,
            );
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _delivery = result;
        _notice = 'Đã yêu cầu gửi lại đúng thư này. Mật khẩu không bị đặt lại lần nữa.';
      });
    } catch (error) {
      if (_isCurrent(sessionIdentity)) setState(() => _commandError = error);
    } finally {
      if (_isCurrent(sessionIdentity)) setState(() => _busy = false);
    }
  }

  Future<void> _updateEmail(Account account) async {
    if (_busy ||
        !widget.owner.active ||
        _intents.emailOutcomeUnknown ||
        _intents.resetOutcomeUnknown ||
        !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final target = _email.text.trim().toLowerCase();
    final reason = _emailReason.text.trim();
    if (!isProfileAccountEmail(target) ||
        reason.isEmpty ||
        reason.length > 500) {
      return;
    }
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
      _intents.emailOutcomeUnknown = true;
      _intents.pendingEmailTarget = target;
    });
    try {
      final repository = ref.read(profileAccountRepositoryProvider);
      if (widget.owner.isPatient) {
        await UpdatePatientLoginEmail(repository)
            .call(patientId: widget.owner.id, email: target, reason: reason);
      } else {
        await UpdateStaffLoginEmail(repository)
            .call(accountId: account.id, email: target, reason: reason);
      }
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _intents.emailOutcomeUnknown = false;
        _intents.pendingEmailTarget = null;
        _emailReason.clear();
        _emailEdited = false;
        _notice = target == account.username.toLowerCase()
            ? 'Đã yêu cầu đồng bộ email hồ sơ theo email đăng nhập hiện tại.'
            : 'Đã cập nhật email đăng nhập. Vui lòng dùng email mới ở lần đăng nhập sau.';
      });
      widget.onChanged?.call();
      await _load();
    } catch (error) {
      if (!_isCurrent(sessionIdentity)) return;
      setState(() {
        _commandError = error;
        _intents.emailOutcomeUnknown = _outcomeMayBeUnknown(error);
        if (_intents.emailOutcomeUnknown) _intents.pendingEmailTarget = target;
      });
    } finally {
      if (_isCurrent(sessionIdentity)) setState(() => _busy = false);
    }
  }

  Future<void> _checkEmailChange(Account account) async {
    final target = _intents.pendingEmailTarget;
    if (target == null || _busy || !_isCurrent(widget.access.sessionIdentity)) {
      return;
    }
    final sessionIdentity = widget.access.sessionIdentity;
    setState(() {
      _busy = true;
      _commandError = null;
      _notice = null;
    });
    try {
      await _load();
      if (!_isCurrent(sessionIdentity)) return;
      if (_loadError != null || _profile == null) return;
      final currentUsername = _profile?.account?.username;
      if (currentUsername?.toLowerCase() == target) {
        if (widget.owner.isPatient && _profile?.emailSyncPending == true) {
          setState(() {
            _intents.emailOutcomeUnknown = false;
            _intents.pendingEmailTarget = null;
            _emailEdited = true;
            _notice = 'Chưa xác nhận đồng bộ. Có thể gửi lại yêu cầu đồng bộ cùng email.';
          });
        } else {
          setState(() {
            _intents.emailOutcomeUnknown = false;
            _intents.pendingEmailTarget = null;
            _emailEdited = false;
            _emailReason.clear();
            _notice = 'Đã xác nhận email đăng nhập mới.';
          });
        }
        widget.onChanged?.call();
      } else {
        setState(() {
          _intents.emailOutcomeUnknown = true;
          _intents.pendingEmailTarget = target;
          _notice =
              'Email hiện tại vẫn là ${currentUsername ?? 'chưa tải được'}. Hãy kiểm tra lại trước khi gửi yêu cầu mới.';
          _email.text = currentUsername ?? '';
        });
      }
    } catch (error) {
      if (_isCurrent(sessionIdentity)) setState(() => _commandError = error);
    } finally {
      if (_isCurrent(sessionIdentity)) setState(() => _busy = false);
    }
  }
}

bool _canManage(ProfileAccountOwner owner, String? role) =>
    owner.isPatient ? const {'ADMIN', 'NURSE'}.contains(role) : role == 'ADMIN';

bool _canRetry(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status == 408 ||
        error.status! >= 500 ||
        error.status == 429);

bool _outcomeMayBeUnknown(Object error) =>
    error is! ApiFailure ||
    error.status == null ||
    error.status == 408 ||
    error.status! >= 500;

String _errorText(Object error) =>
    error is ApiFailure ? error.message : error.toString();

String _mailStatusName(ProfileMailStatus status) => switch (status) {
  ProfileMailStatus.pending => 'Đang chờ gửi',
  ProfileMailStatus.sent => 'Đã gửi',
  ProfileMailStatus.failed => 'Gửi chưa thành công',
};

Color _mailColor(BuildContext context, ProfileMailStatus status) =>
    switch (status) {
      ProfileMailStatus.sent => Theme.of(context).colorScheme.primary,
      ProfileMailStatus.pending => context.medical.warning,
      ProfileMailStatus.failed => Theme.of(context).colorScheme.error,
    };

String _roleName(String role) => switch (role) {
  'ADMIN' => 'Quản trị viên',
  'DOCTOR' => 'Bác sĩ',
  'NURSE' => 'Điều dưỡng',
  'PHARMACIST' => 'Dược sĩ',
  'CASHIER' => 'Thu ngân',
  'LAB_TECH' => 'Kỹ thuật viên xét nghiệm',
  'MANAGER' => 'Quản lý',
  'PATIENT' => 'Bệnh nhân',
  'SYSTEM' => 'Hệ thống',
  _ => role,
};

class _AccountPanelCard extends StatelessWidget {
  const _AccountPanelCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(top: 16),
    child: Padding(padding: const EdgeInsets.all(16), child: child),
  );
}

class _AccountValue extends StatelessWidget {
  const _AccountValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 132, child: Text(label)),
        Expanded(child: SelectableText(value)),
      ],
    ),
  );
}

class _StatusMessage extends StatelessWidget {
  const _StatusMessage(
    this.message, {
    required this.color,
    this.isError = false,
  });

  final String message;
  final Color color;
  final bool isError;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5),
    child: Text(
      message,
      style: TextStyle(
        color: color,
        fontWeight: isError ? FontWeight.w600 : FontWeight.normal,
      ),
    ),
  );
}
