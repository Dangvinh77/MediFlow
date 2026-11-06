import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mediflow_loader/mediflow_loader.dart';

/// How long the full-screen loader holds after a successful sign-in.
const signInSplashDuration = Duration(seconds: 1);

final signInSplashProvider = NotifierProvider<SignInSplashController, bool>(
  SignInSplashController.new,
);

class SignInSplashController extends Notifier<bool> {
  Timer? _timer;

  @override
  bool build() {
    ref.onDispose(() => _timer?.cancel());
    return false;
  }

  void show() {
    _timer?.cancel();
    _timer = Timer(signInSplashDuration, () => state = false);
    state = true;
  }
}

/// Covers the whole app while a sign-in settles, so the first page opens
/// behind the loader. A restored session never shows it.
class SignInSplash extends ConsumerWidget {
  const SignInSplash({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) => Stack(
    fit: StackFit.expand,
    children: [
      child,
      if (ref.watch(signInSplashProvider))
        AbsorbPointer(
          child: BlockSemantics(
            child: Material(
              color: Theme.of(context).colorScheme.surface,
              child: const Center(
                child: MediFlowLoader(size: 96, label: 'Đang vào hệ thống…'),
              ),
            ),
          ),
        ),
    ],
  );
}
