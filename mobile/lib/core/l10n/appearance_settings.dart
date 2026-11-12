import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_l10n.dart';
import 'app_preferences.dart';

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
