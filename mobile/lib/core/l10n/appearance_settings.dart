import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_l10n.dart';
import 'app_preferences.dart';

/// Compact language and theme switches for app bars and the login header,
/// mirroring the web `LanguageToggle` and `ThemeToggle` that stay visible on
/// every screen. The profile page keeps the full [AppearanceSettings] card.
class AppearanceQuickActions extends ConsumerWidget {
  const AppearanceQuickActions({super.key, this.color});

  /// Icon colour; defaults to the surrounding [IconTheme].
  final Color? color;

  static const _themeIcons = {
    ThemeMode.light: Icons.light_mode_outlined,
    ThemeMode.dark: Icons.dark_mode_outlined,
    ThemeMode.system: Icons.brightness_auto_outlined,
  };
  static const _themeLabels = {
    ThemeMode.light: 'Sáng',
    ThemeMode.dark: 'Tối',
    ThemeMode.system: 'Theo hệ thống',
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preferences = ref.watch(appPreferencesProvider);
    final controller = ref.read(appPreferencesProvider.notifier);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        PopupMenuButton<String>(
          key: const ValueKey('quick-language'),
          tooltip: AppL10n.text(context, 'Ngôn ngữ'),
          initialValue: preferences.language,
          onSelected: controller.setLanguage,
          itemBuilder: (_) => [
            for (final (code, name) in const [
              ('vi', 'Tiếng Việt'),
              ('en', 'English'),
            ])
              CheckedPopupMenuItem<String>(
                value: code,
                checked: code == preferences.language,
                child: Text(name),
              ),
          ],
          // Code only (VI/EN) so the app bar keeps room for the page title
          // on narrow phones; the tooltip names the control.
          child: SizedBox(
            width: 40,
            height: 48,
            child: Center(
              child: Text(
                preferences.language.toUpperCase(),
                style: Theme.of(context).textTheme.labelLarge
                    ?.copyWith(color: color, fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ),
        PopupMenuButton<ThemeMode>(
          key: const ValueKey('quick-theme'),
          tooltip: AppL10n.text(context, 'Giao diện'),
          initialValue: preferences.theme,
          onSelected: controller.setTheme,
          icon: Icon(_themeIcons[preferences.theme], color: color),
          itemBuilder: (context) => [
            for (final mode in const [
              ThemeMode.light,
              ThemeMode.dark,
              ThemeMode.system,
            ])
              CheckedPopupMenuItem<ThemeMode>(
                value: mode,
                checked: mode == preferences.theme,
                child: LText(_themeLabels[mode]!),
              ),
          ],
        ),
      ],
    );
  }
}

class AppearanceSettings extends ConsumerWidget {
  const AppearanceSettings({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preferences = ref.watch(appPreferencesProvider);
    final controller = ref.read(appPreferencesProvider.notifier);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LText(
              'Tùy chỉnh ứng dụng',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            LayoutBuilder(
              builder: (context, constraints) {
                final width = constraints.maxWidth >= 300
                    ? (constraints.maxWidth - 12) / 2
                    : constraints.maxWidth;
                return Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    SizedBox(
                      width: width,
                      child: DropdownButtonFormField<String>(
                        key: ValueKey('language-${preferences.language}'),
                        initialValue: preferences.language,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          label: LText('Ngôn ngữ'),
                        ),
                        items: const [
                          DropdownMenuItem(
                            value: 'vi',
                            child: Text('Tiếng Việt'),
                          ),
                          DropdownMenuItem(value: 'en', child: Text('English')),
                        ],
                        onChanged: (value) {
                          if (value != null) controller.setLanguage(value);
                        },
                      ),
                    ),
                    SizedBox(
                      width: width,
                      child: DropdownButtonFormField<ThemeMode>(
                        key: ValueKey('theme-${preferences.theme.name}'),
                        initialValue: preferences.theme,
                        isExpanded: true,
                        decoration: const InputDecoration(
                          label: LText('Giao diện'),
                        ),
                        items: const [
                          DropdownMenuItem(
                            value: ThemeMode.light,
                            child: LText('Sáng'),
                          ),
                          DropdownMenuItem(
                            value: ThemeMode.dark,
                            child: LText('Tối'),
                          ),
                          DropdownMenuItem(
                            value: ThemeMode.system,
                            child: LText('Theo hệ thống'),
                          ),
                        ],
                        onChanged: (value) {
                          if (value != null) controller.setTheme(value);
                        },
                      ),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}
