import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

abstract interface class PreferenceStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
}

final preferenceStoreProvider = Provider<PreferenceStore?>((ref) => null);

class AppPreferences {
  const AppPreferences({this.language = 'vi', this.theme = ThemeMode.system});
  final String language;
  final ThemeMode theme;
}

final appPreferencesProvider =
    NotifierProvider<AppPreferencesController, AppPreferences>(
      AppPreferencesController.new,
    );

class AppPreferencesController extends Notifier<AppPreferences> {
  int _revision = 0;
  Future<void> _writes = Future.value();

  @override
  AppPreferences build() {
    final store = ref.watch(preferenceStoreProvider);
    final revision = _revision;
    if (store != null) {
      unawaited(
        Future(() async {
          try {
            final language = await store.read('mediflow.locale');
            final theme = await store.read('mediflow.theme');
            if (!ref.mounted || revision != _revision) return;
            state = AppPreferences(
              language: language == 'en' ? 'en' : 'vi',
              theme:
                  ThemeMode.values
                      .where((mode) => mode.name == theme)
                      .firstOrNull ??
                  ThemeMode.system,
            );
          } catch (_) {
            /* Defaults remain usable when storage is unavailable. */
          }
        }),
      );
    }
    return const AppPreferences();
  }

  void setLanguage(String language) {
    if (language != 'vi' && language != 'en') return;
    _revision++;
    state = AppPreferences(language: language, theme: state.theme);
    _persist('mediflow.locale', language);
  }

  void setTheme(ThemeMode theme) {
    _revision++;
    state = AppPreferences(language: state.language, theme: theme);
    _persist('mediflow.theme', theme.name);
  }

  void _persist(String key, String value) {
    final store = ref.read(preferenceStoreProvider);
    if (store == null) return;
    _writes = _writes
        .then((_) => store.write(key, value))
        .catchError((Object _) {});
  }
}
