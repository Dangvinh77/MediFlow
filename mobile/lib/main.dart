import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'app/dependencies.dart';
import 'core/config/app_config.dart';
import 'core/providers.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final config = AppConfig.environment();
    runApp(
      ProviderScope(
        overrides: [
          ...appOverrides,
          appConfigProvider.overrideWithValue(config),
        ],
        child: const MediFlowApp(),
      ),
    );
  } on ArgumentError catch (error) {
    runApp(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text('Cấu hình ứng dụng chưa hợp lệ. ${error.message}'),
            ),
          ),
        ),
      ),
    );
  }
}
