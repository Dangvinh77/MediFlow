import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/l10n/app_l10n.dart';
import 'package:mediflow_mobile/core/l10n/app_preferences.dart';
import 'package:mediflow_mobile/core/l10n/appearance_settings.dart';
import 'package:mediflow_mobile/core/l10n/messages_en.dart';

class MemoryPreferences implements PreferenceStore {
  final values = <String, String>{};
  @override
  Future<String?> read(String key) async => values[key];
  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

void main() {
  test(
    'Vietnamese and system theme are defaults; both preferences persist',
    () async {
      final store = MemoryPreferences();
      final container = ProviderContainer(
        overrides: [preferenceStoreProvider.overrideWithValue(store)],
      );
      addTearDown(container.dispose);
      expect(container.read(appPreferencesProvider).language, 'vi');
      expect(container.read(appPreferencesProvider).theme, ThemeMode.system);
      await Future<void>.delayed(Duration.zero);
      final controller = container.read(appPreferencesProvider.notifier);
      controller.setLanguage('en');
      controller.setTheme(ThemeMode.dark);
      await Future<void>.delayed(Duration.zero);
      expect(store.values['mediflow.locale'], 'en');
      expect(store.values['mediflow.theme'], 'dark');
      final restored = ProviderContainer(
        overrides: [preferenceStoreProvider.overrideWithValue(store)],
      );
      addTearDown(restored.dispose);
      restored.read(appPreferencesProvider);
      await Future<void>.delayed(Duration.zero);
      expect(restored.read(appPreferencesProvider).language, 'en');
      expect(restored.read(appPreferencesProvider).theme, ThemeMode.dark);
    },
  );

  test('all messages retain their interpolation slots', () {
    final slots = RegExp(r'\{\d+\}');
    for (final entry in messagesEn.entries) {
      final source = slots.allMatches(entry.key).map((m) => m[0]).toList()
        ..sort();
      final translated = slots.allMatches(entry.value).map((m) => m[0]).toList()
        ..sort();
      expect(translated, source, reason: entry.key);
    }
    expect(translate('Nguyễn An', 'en'), 'Nguyễn An');
  });

  testWidgets(
    'changing language updates an existing screen without losing input',
    (tester) async {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: Consumer(
            builder: (context, ref, _) {
              final settings = ref.watch(appPreferencesProvider);
              return MaterialApp(
                themeMode: settings.theme,
                home: AppL10n(
                  language: settings.language,
                  child: const Scaffold(
                    body: Column(
                      children: [
                        AppearanceSettings(),
                        LText('Hồ sơ cá nhân'),
                        TextField(),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      );
      await tester.enterText(find.byType(TextField), 'Nguyễn An');
      container.read(appPreferencesProvider.notifier).setLanguage('en');
      await tester.pumpAndSettle();
      expect(find.text('Personal profile'), findsOneWidget);
      expect(find.text('Nguyễn An'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
