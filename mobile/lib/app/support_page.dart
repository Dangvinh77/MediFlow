import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/presentation/session_controller.dart';
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
      padding: const EdgeInsets.all(24),
      children: [
        Text('Hỗ trợ', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 8),
        const Text('Báo cáo và thông báo cho công việc hằng ngày.'),
        const SizedBox(height: 24),
        if (available.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: Text(
                'Tài khoản này chưa có quyền xem Báo cáo hoặc Thông báo.',
              ),
            ),
          ),
        for (final module in available)
          Card(
            child: ListTile(
              contentPadding: const EdgeInsets.all(16),
              leading: Icon(module.icon),
              title: Text(module.title),
              subtitle: Text(module.description),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.go(module.path),
            ),
          ),
      ],
    );
  }
}
