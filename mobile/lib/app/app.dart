import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../core/l10n/app_l10n.dart';
import '../core/l10n/app_preferences.dart';

import '../core/theme/app_theme.dart';
import '../features/auth/presentation/session_controller.dart';
import '../features/auth/presentation/sign_in_splash.dart';
import 'router.dart';

class MediFlowApp extends ConsumerWidget {
  const MediFlowApp({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(sessionProvider);
    final preferences = ref.watch(appPreferencesProvider);
    return MaterialApp.router(
      title: 'MediFlow',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(Brightness.light),
      darkTheme: buildAppTheme(Brightness.dark),
      themeMode: preferences.theme,
      locale: Locale(preferences.language),
      supportedLocales: const [Locale('vi'), Locale('en')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      routerConfig: ref.watch(routerProvider),
      builder: (_, child) => AppL10n(
        language: preferences.language,
        child: SignInSplash(child: child!),
      ),
    );
  }
}
