import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_failure.dart';
import '../../../core/widgets/request_state.dart';
import '../domain/change_password.dart';
import 'session_controller.dart';

final passwordRepositoryProvider = Provider<PasswordRepository>(
  (ref) => throw StateError('Bind PasswordRepository in app/dependencies.dart'),
);

class ChangePasswordPage extends ConsumerWidget {
  const ChangePasswordPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider).asData?.value;
    if (session == null) return const SizedBox.shrink();
    return _PasswordForm(key: ObjectKey(session));
  }
}

class _PasswordForm extends ConsumerStatefulWidget {
  const _PasswordForm({super.key});
  @override
  ConsumerState<_PasswordForm> createState() => _PasswordFormState();
}

class _PasswordFormState extends ConsumerState<_PasswordForm> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirmation = TextEditingController();
  bool _busy = false, _uncertain = false;
  Object? _error;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || _uncertain || !_form.currentState!.validate()) return;
    final session = ref.read(sessionProvider).asData?.value;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ChangeMyPassword(ref.read(passwordRepositoryProvider))(
        _current.text,
        _next.text,
      );
      if (!mounted ||
          !identical(ref.read(sessionProvider).asData?.value, session)) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.',
          ),
        ),
      );
      await ref.read(sessionProvider.notifier).signOut();
    } catch (error) {
      if (!mounted ||
          !identical(ref.read(sessionProvider).asData?.value, session)) {
        return;
      }
      setState(() {
        _error = error;
        _uncertain =
            error is! ApiFailure ||
            error.status == null ||
            error.status == 408 ||
            error.status! >= 500;
      });
    } finally {
      if (mounted) {
        _current.clear();
        _next.clear();
        _confirmation.clear();
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    padding: const EdgeInsets.all(24),
    child: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 460),
        child: Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Đổi mật khẩu',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 12),
              const Text(
                'Nếu đây là lần đăng nhập đầu tiên, bạn cần đổi mật khẩu ban đầu để sử dụng hệ thống. Sau khi đổi, hãy đăng nhập lại.',
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _current,
                enabled: !_busy && !_uncertain,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                decoration: const InputDecoration(
                  labelText: 'Mật khẩu hiện tại',
                ),
                validator: (value) =>
                    value == null || value.trim().isEmpty || value.length > 72
                    ? 'Nhập mật khẩu hiện tại (tối đa 72 ký tự).'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _next,
                enabled: !_busy && !_uncertain,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                decoration: const InputDecoration(labelText: 'Mật khẩu mới'),
                validator: (value) =>
                    value == null ||
                        value.trim().isEmpty ||
                        value.length < 8 ||
                        value.length > 72
                    ? 'Mật khẩu mới từ 8 đến 72 ký tự.'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _confirmation,
                enabled: !_busy && !_uncertain,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                decoration: const InputDecoration(
                  labelText: 'Nhập lại mật khẩu mới',
                ),
                validator: (value) => value != _next.text
                    ? 'Mật khẩu xác nhận không khớp.'
                    : null,
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _busy || _uncertain ? null : _submit,
                child: Text(_busy ? 'Đang đổi mật khẩu…' : 'Lưu mật khẩu mới'),
              ),
              if (_error != null) RequestError(_error!),
              if (_uncertain)
                const Text(
                  'Chưa xác định kết quả. Đăng nhập lại bằng mật khẩu mới để kiểm tra trước khi thử đổi tiếp.',
                ),
              TextButton(
                onPressed: _busy
                    ? null
                    : () => ref.read(sessionProvider.notifier).signOut(),
                child: const Text('Đăng xuất và đăng nhập lại'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
