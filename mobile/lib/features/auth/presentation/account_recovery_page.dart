import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/widgets/request_state.dart';
import '../domain/account_recovery.dart';

final accountRecoveryProvider = Provider<AccountRecoveryRepository>(
  (ref) => throw StateError('AccountRecoveryRepository must be bound'),
);

class AccountRecoveryPage extends ConsumerStatefulWidget {
  const AccountRecoveryPage({super.key, this.reset = false, this.token = ''});
  final bool reset;
  final String token;

  @override
  ConsumerState<AccountRecoveryPage> createState() =>
      _AccountRecoveryPageState();
}

class _AccountRecoveryPageState extends ConsumerState<AccountRecoveryPage> {
  final email = TextEditingController();
  final password = TextEditingController();
  final confirmation = TextEditingController();
  late final token = TextEditingController(text: widget.token);
  bool busy = false, done = false;
  Object? error;

  @override
  void dispose() {
    for (final controller in [email, password, confirmation, token]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> submit() async {
    if (busy || done) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final recovery = RecoverAccount(ref.read(accountRecoveryProvider));
      if (widget.reset) {
        if (password.text != confirmation.text) {
          throw const FormatException('Mật khẩu xác nhận chưa khớp.');
        }
        await recovery.reset(token.text, password.text);
      } else {
        await recovery.forgot(email.text);
      }
      if (mounted) {
        password.clear();
        confirmation.clear();
        token.clear();
        setState(() => done = true);
      }
    } catch (e) {
      if (mounted) setState(() => error = e);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      leading: BackButton(onPressed: () => context.go('/login')),
      title: LText(widget.reset ? 'Đặt lại mật khẩu' : 'Quên mật khẩu'),
    ),
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: AutofillGroup(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (done) ...[
                    LText(
                      widget.reset
                          ? 'Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.'
                          : 'Nếu email đủ điều kiện, hệ thống sẽ gửi hướng dẫn khôi phục. Kiểm tra cả thư rác.',
                    ),
                    const SizedBox(height: 16),
                    if (!widget.reset)
                      OutlinedButton(
                        onPressed: () => context.go('/reset-password'),
                        child: const LText('Tôi đã có liên kết khôi phục'),
                      ),
                  ] else ...[
                    LText(
                      widget.reset
                          ? 'Dán liên kết trong email để đặt lại mật khẩu. Liên kết chỉ dùng một lần và có thời hạn.'
                          : 'Nhập email đăng nhập để nhận hướng dẫn khôi phục tài khoản.',
                    ),
                    const SizedBox(height: 24),
                    if (!widget.reset)
                      TextField(
                        controller: email,
                        enabled: !busy,
                        keyboardType: TextInputType.emailAddress,
                        autofillHints: const [AutofillHints.email],
                        maxLength: 254,
                        decoration: InputDecoration(
                          labelText: AppL10n.text(context, 'Email'),
                        ),
                      ),
                    if (widget.reset) ...[
                      TextField(
                        controller: token,
                        enabled: !busy,
                        autocorrect: false,
                        enableSuggestions: false,
                        decoration: InputDecoration(
                          labelText: AppL10n.text(
                            context,
                            'Liên kết hoặc mã khôi phục',
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: password,
                        enabled: !busy,
                        obscureText: true,
                        maxLength: 72,
                        autofillHints: const [AutofillHints.newPassword],
                        decoration: InputDecoration(
                          labelText: AppL10n.text(context, 'Mật khẩu mới'),
                        ),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: confirmation,
                        enabled: !busy,
                        obscureText: true,
                        maxLength: 72,
                        decoration: InputDecoration(
                          labelText: AppL10n.text(
                            context,
                            'Xác nhận mật khẩu mới',
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: busy ? null : submit,
                      child: LText(
                        busy
                            ? 'Đang gửi…'
                            : widget.reset
                            ? 'Đặt lại mật khẩu'
                            : 'Gửi hướng dẫn',
                      ),
                    ),
                    if (error != null) RequestError(error!),
                  ],
                  TextButton(
                    onPressed: busy ? null : () => context.go('/login'),
                    child: const LText('Về đăng nhập'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}
