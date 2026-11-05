import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/medical_colors.dart';
import '../core/widgets/medical_ui.dart';
import '../features/auth/domain/session.dart';
import '../features/auth/presentation/session_controller.dart';
import '../core/providers.dart';
import '../features/appointment/presentation/schedule_management_pages.dart';
import 'module_directory.dart';
import 'modules.dart';

/// The bottom bar costs more than it gives on a landscape phone.
const _minHeightForBottomBar = 520.0;

class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key, required this.child, required this.location});
  final Widget child;
  final String location;

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  final _scaffold = GlobalKey<ScaffoldState>();

  @override
  Widget build(BuildContext context) {
    final location = widget.location;
    final session = ref.watch(sessionProvider).asData?.value;
    final accessible = session == null
        ? <AppModule>[]
        : visibleMainModules(
            session.role,
            schedulingEnabled: ref
                .watch(appConfigProvider)
                .scheduledPaymentsEnabled,
            headsDepartment:
                ref.watch(headsDepartmentProvider).asData?.value ?? false,
          );
    final selected = modules.where((m) => m.matches(location)).firstOrNull;
    final isHome = location == '/';
    final accent = selected?.accent ?? Wayfinding.home;
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 900;
        final showBottomBar =
            !wide && constraints.maxHeight >= _minHeightForBottomBar;
        return Scaffold(
          key: _scaffold,
          appBar: AppBar(
            backgroundColor: MediFlowBrand.deep,
            foregroundColor: Colors.white,
            systemOverlayStyle: SystemUiOverlayStyle.light,
            shape: const Border(),
            titleSpacing: wide ? 20 : 0,
            title: Text(
              location == '/account/password'
                  ? 'Đổi mật khẩu'
                  : location == '/profile'
                  ? 'Hồ sơ cá nhân'
                  : selected?.title ?? 'MediFlow',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleLarge
                  ?.copyWith(color: Colors.white, fontSize: 19),
            ),
            actions: [
              IconButton(
                tooltip: 'Hồ sơ cá nhân',
                onPressed: () => context.go('/profile'),
                icon: const Icon(Icons.account_circle_outlined),
              ),
              IconButton(
                tooltip: 'Đăng xuất',
                onPressed: () => ref.read(sessionProvider.notifier).signOut(),
                icon: const Icon(Icons.logout),
              ),
              const SizedBox(width: 4),
            ],
            // The floor line: which area of the hospital this page belongs to.
            bottom: isHome
                ? null
                : PreferredSize(
                    preferredSize: const Size.fromHeight(4),
                    child: ColoredBox(
                      color: context.medical.isDark
                          ? context.medical.accent(accent)
                          : Color.lerp(accent, Colors.white, 0.22)!,
                      child: const SizedBox(height: 4, width: double.infinity),
                    ),
                  ),
          ),
          drawer: wide
              ? null
              : Drawer(
                  child: Builder(
                    builder: (context) => _Navigation(
                      location: location,
                      accessible: accessible,
                      closeDrawer: true,
                      role: session?.role,
                    ),
                  ),
                ),
          bottomNavigationBar: showBottomBar
              ? _BottomBar(
                  selected: selected,
                  isHome: isHome,
                  tabs: bottomBarModules(accessible),
                  openMenu: () => _scaffold.currentState?.openDrawer(),
                )
              : null,
          body: Row(
            children: [
              if (wide)
                SizedBox(
                  width: 280,
                  child: Material(
                    color: Theme.of(context).colorScheme.surface,
                    child: _Navigation(
                      location: location,
                      accessible: accessible,
                      closeDrawer: false,
                      role: session?.role,
                    ),
                  ),
                ),
              if (wide) const VerticalDivider(width: 1),
              Expanded(child: SafeArea(top: false, child: widget.child)),
            ],
          ),
        );
      },
    );
  }
}

class _BottomBar extends StatelessWidget {
  const _BottomBar({
    required this.selected,
    required this.isHome,
    required this.tabs,
    required this.openMenu,
  });
  final AppModule? selected;
  final bool isHome;
  final List<AppModule> tabs;
  final VoidCallback openMenu;

  @override
  Widget build(BuildContext context) {
    final medical = context.medical;
    final tab = tabs.indexWhere((module) => module.path == selected?.path);
    final current = isHome
        ? 0
        : tab >= 0
        ? tab + 1
        : tabs.length + 1;
    final indicator = isHome || tab < 0 ? Wayfinding.home : tabs[tab].accent;
    final narrow = MediaQuery.sizeOf(context).width < 400;
    return MediaQuery.withClampedTextScaling(
      maxScaleFactor: narrow ? 1 : 1.15,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(top: BorderSide(color: medical.hairline)),
        ),
        child: NavigationBar(
          selectedIndex: current,
          indicatorColor: medical.accent(indicator).withValues(alpha: 0.16),
          onDestinationSelected: (index) {
            if (index == 0) {
              context.go('/');
            } else if (index <= tabs.length) {
              context.go(tabs[index - 1].path);
            } else {
              openMenu();
            }
          },
          destinations: [
            const NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home),
              label: 'Tổng quan',
            ),
            for (final module in tabs)
              NavigationDestination(
                icon: Icon(module.icon),
                selectedIcon: Icon(
                  module.icon,
                  color: medical.accent(module.accent),
                ),
                label: module.bottomLabel,
              ),
            const NavigationDestination(icon: Icon(Icons.menu), label: 'Thêm'),
          ],
        ),
      ),
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
  final String location;
  final List<AppModule> accessible;
  final bool closeDrawer;
  final UserRole? role;

  void _go(BuildContext context, String path) {
    if (closeDrawer) Navigator.pop(context);
    context.go(path);
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.only(bottom: 12),
    children: [
      if (closeDrawer)
        Container(
          color: MediFlowBrand.deep,
          padding: EdgeInsets.fromLTRB(
            20,
            MediaQuery.paddingOf(context).top + 20,
            20,
            20,
          ),
          child: Row(
            spacing: 14,
            children: [
              const MediFlowMark(size: 38),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'MediFlow',
                      style: Theme.of(context).textTheme.titleLarge
                          ?.copyWith(color: Colors.white),
                    ),
                    if (role != null)
                      Text(
                        roleLabel(role!),
                        style: Theme.of(context).textTheme.bodySmall
                            ?.copyWith(color: Colors.white70),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      const SizedBox(height: 10),
      ModuleNavTile(
        icon: Icons.home_outlined,
        title: 'Tổng quan',
        color: Wayfinding.home,
        selected: location == '/',
        onTap: () => _go(context, '/'),
      ),
      for (final module in accessible)
        ModuleNavTile(
          icon: module.icon,
          title: module.title,
          color: module.accent,
          selected:
              module.matches(location) ||
              (module.path == '/support' &&
                  (location == '/reports' || location == '/notifications')),
          onTap: () => _go(context, module.path),
        ),
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: 22, vertical: 8),
        child: Divider(),
      ),
      ModuleNavTile(
        icon: Icons.lock_outline,
        title: 'Đổi mật khẩu',
        color: Wayfinding.organization,
        selected: location == '/account/password',
        onTap: () => _go(context, '/account/password'),
      ),
    ],
  );
}

class HomePage extends ConsumerWidget {
  const HomePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(sessionProvider).asData?.value?.role;
    final available = role == null
        ? <AppModule>[]
        : visibleMainModules(
            role,
            schedulingEnabled: ref
                .watch(appConfigProvider)
                .scheduledPaymentsEnabled,
            headsDepartment:
                ref.watch(headsDepartmentProvider).asData?.value ?? false,
          );
    return ListView(
      padding: EdgeInsets.zero,
      children: [
        _HomeHeader(role: role),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
          child: Text(
            'Không gian làm việc',
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: ModuleDirectory(modules: available),
        ),
      ],
    );
  }
}

String _todayLabel(DateTime now) {
  const days = [
    'Thứ Hai',
    'Thứ Ba',
    'Thứ Tư',
    'Thứ Năm',
    'Thứ Sáu',
    'Thứ Bảy',
    'Chủ Nhật',
  ];
  String two(int value) => value.toString().padLeft(2, '0');
  return '${days[now.weekday - 1]}, ${two(now.day)}/${two(now.month)}/${now.year}';
}

class _HomeHeader extends StatelessWidget {
  const _HomeHeader({required this.role});
  final UserRole? role;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(28)),
      child: ColoredBox(
        color: MediFlowBrand.deep,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    role == null ? 'Xin chào' : 'Xin chào, ${roleLabel(role!)}',
                    style: text.headlineMedium?.copyWith(color: Colors.white),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _todayLabel(DateTime.now()),
                    style: text.bodyMedium?.copyWith(color: Colors.white70),
                  ),
                ],
              ),
            ),
            const PulseLine(height: 64),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
