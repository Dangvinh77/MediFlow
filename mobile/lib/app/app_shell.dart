import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/presentation/session_controller.dart';
import 'modules.dart';

class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.child, required this.location});
  final Widget child;
  final String location;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider).asData?.value;
    final accessible = session == null
        ? <AppModule>[]
        : visibleMainModules(session.role);
    final selected = modules.where((m) => m.matches(location)).firstOrNull;
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 900;
        return Scaffold(
          appBar: AppBar(
            title: Text(selected?.title ?? 'MediFlow'),
            actions: [
              IconButton(
                tooltip: 'Đăng xuất',
                onPressed: () => ref.read(sessionProvider.notifier).signOut(),
                icon: const Icon(Icons.logout),
              ),
              const SizedBox(width: 8),
            ],
          ),
          drawer: wide
              ? null
              : Drawer(
                  child: Builder(
                    builder: (context) => _Navigation(
                      location: location,
                      accessible: accessible,
                      closeDrawer: true,
                      role: session?.role.wire ?? '',
                    ),
                  ),
                ),
          body: Row(
            children: [
              if (wide)
                SizedBox(
                  width: 260,
                  child: _Navigation(
                    location: location,
                    accessible: accessible,
                    closeDrawer: false,
                    role: session?.role.wire ?? '',
                  ),
                ),
              if (wide) const VerticalDivider(width: 1),
              Expanded(child: child),
            ],
          ),
        );
      },
    );
  }
}

class _Navigation extends StatelessWidget {
  const _Navigation({
    required this.location,
    required this.accessible,
    required this.closeDrawer,
    required this.role,
  });
  final String location, role;
  final List<AppModule> accessible;
  final bool closeDrawer;
  @override
  Widget build(BuildContext context) => ListView(
    children: [
      DrawerHeader(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('MediFlow', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 8),
            Text(role),
          ],
        ),
      ),
      ListTile(
        leading: const Icon(Icons.home_outlined),
        title: const Text('Tổng quan'),
        selected: location == '/',
        onTap: () {
          if (closeDrawer) Navigator.pop(context);
          context.go('/');
        },
      ),
      for (final module in accessible)
        ListTile(
          leading: Icon(module.icon),
          title: Text(module.title),
          selected:
              module.matches(location) ||
              (module.path == '/support' &&
                  (location == '/reports' || location == '/notifications')),
          onTap: () {
            if (closeDrawer) Navigator.pop(context);
            context.go(module.path);
          },
        ),
    ],
  );
}

class HomePage extends ConsumerWidget {
  const HomePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(sessionProvider).asData?.value?.role;
    final available = role == null ? <AppModule>[] : visibleMainModules(role);
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Text(
          'Không gian làm việc',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        const SizedBox(height: 8),
        const Text('Chọn nghiệp vụ để bắt đầu công việc.'),
        const SizedBox(height: 24),
        for (final module in available)
          Card(
            child: ListTile(
              contentPadding: const EdgeInsets.all(16),
              leading: Icon(
                module.icon,
                color: Theme.of(context).colorScheme.primary,
              ),
              title: Text(module.title),
              subtitle: Text(
                module.implemented ? module.description : 'Đang phát triển',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.go(module.path),
            ),
          ),
      ],
    );
  }
}
