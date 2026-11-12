import '../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/presentation/session_controller.dart';
import 'module_directory.dart';
import 'modules.dart';

class SupportPage extends ConsumerWidget {
  const SupportPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(sessionProvider).asData?.value?.role;
    final available = role == null
        ? <AppModule>[]
        : visibleSupportModules(role);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const LText('Báo cáo và thông báo cho công việc hằng ngày.'),
        const SizedBox(height: 16),
        if (available.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: LText(
                'Tài khoản này chưa có quyền xem Báo cáo hoặc Thông báo.',
              ),
            ),
          ),
        ModuleDirectory(modules: available),
      ],
    );
  }
}
