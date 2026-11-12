import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';
import '../../../core/widgets/request_state.dart';
import 'session_controller.dart';

// Status bar only: SystemUiOverlayStyle.light/.dark also force a black navigation bar.
const _lightStatusIcons = SystemUiOverlayStyle(
  statusBarIconBrightness: Brightness.light,
  statusBarBrightness: Brightness.dark,
);
const _darkStatusIcons = SystemUiOverlayStyle(
  statusBarIconBrightness: Brightness.dark,
  statusBarBrightness: Brightness.light,
);

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});
  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _form = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  void _submit() {
    if (_form.currentState!.validate()) {
      ref.read(sessionProvider.notifier).signIn(_username.text, _password.text);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionProvider);
    final theme = Theme.of(context);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      // Applies once the dark header has scrolled out from under the status bar.
      value: theme.brightness == Brightness.dark
          ? _lightStatusIcons
          : _darkStatusIcons,
      child: Scaffold(
        backgroundColor: theme.colorScheme.surface,
        body: SingleChildScrollView(
          child: Column(
            children: [
              const _LoginHeader(),
              SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 420),
                      child: Form(
                        key: _form,
                        child: AutofillGroup(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              LText(
                                'Đăng nhập để tiếp tục công việc',
                                style: theme.textTheme.titleLarge,
                              ),
                              const SizedBox(height: 24),
                              TextFormField(
                                controller: _username,
                                enabled: !session.isLoading,
                                autofillHints: const [AutofillHints.email],
                                keyboardType: TextInputType.emailAddress,
                                maxLength: 254,
                                decoration: InputDecoration(
                                  counterText: '',
                                  labelText: AppL10n.text(context, 'Email'),
                                  prefixIcon: Icon(Icons.email_outlined),
                                ),
                                textInputAction: TextInputAction.next,
                                validator: (v) => v == null || v.trim().isEmpty
                                    ? 'Nhập email đăng nhập.'
                                    : null,
                              ),
                              const SizedBox(height: 16),
                              TextFormField(
                                controller: _password,
                                enabled: !session.isLoading,
                                obscureText: _obscure,
                                autofillHints: const [AutofillHints.password],
                                maxLength: 72,
                                decoration: InputDecoration(
                                  counterText: '',
                                  labelText: AppL10n.text(context, 'Mật khẩu'),
                                  prefixIcon: const Icon(Icons.lock_outline),
                                  suffixIcon: IconButton(
                                    tooltip: AppL10n.text(
                                      context,
                                      _obscure
                                          ? 'Hiện mật khẩu'
                                          : 'Ẩn mật khẩu',
                                    ),
                                    onPressed: () =>
                                        setState(() => _obscure = !_obscure),
                                    icon: Icon(
                                      _obscure
                                          ? Icons.visibility_outlined
                                          : Icons.visibility_off_outlined,
                                    ),
                                  ),
                                ),
                                onFieldSubmitted: (_) {
                                  if (!session.isLoading) _submit();
                                },
                                validator: (v) => v == null || v.isEmpty
                                    ? 'Nhập mật khẩu.'
                                    : null,
                              ),
                              const SizedBox(height: 24),
                              FilledButton(
                                onPressed: session.isLoading ? null : _submit,
                                child: Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: LText(
                                    session.isLoading
                                        ? 'Đang đăng nhập…'
                                        : 'Đăng nhập',
                                  ),
                                ),
                              ),
                              if (session.hasError)
                                RequestError(session.error!),
                              TextButton(
                                onPressed: session.isLoading
                                    ? null
                                    : () => context.go('/forgot-password'),
                                child: const LText('Quên mật khẩu?'),
                              ),
                              TextButton(
                                onPressed: session.isLoading
                                    ? null
                                    : () => context.go('/reset-password'),
                                child: const LText(
                                  'Tôi đã có liên kết khôi phục',
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LoginHeader extends StatelessWidget {
  const _LoginHeader();

  @override
  Widget build(BuildContext context) => AnnotatedRegion<SystemUiOverlayStyle>(
    value: _lightStatusIcons,
    child: ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(32)),
      child: ColoredBox(
        color: MediFlowBrand.deep,
        child: SafeArea(
          bottom: false,
          child: Column(
            children: [
              const SizedBox(height: 36),
              const MediFlowMark(size: 60),
              const SizedBox(height: 16),
              LText(
                'MediFlow',
                style: Theme.of(context).textTheme.headlineLarge
                    ?.copyWith(color: Colors.white),
              ),
              const SizedBox(height: 4),
              LText(
                'Quản lý khám ngoại trú',
                style: Theme.of(context).textTheme.bodyMedium
                    ?.copyWith(color: Colors.white70),
              ),
              const PulseLine(height: 72),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    ),
  );
}
