import '../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/medical_colors.dart';
import '../core/widgets/medical_ui.dart';
import 'modules.dart';

/// The hospital directory board: one tile per area, each in its own colour.
class ModuleDirectory extends StatelessWidget {
  const ModuleDirectory({super.key, required this.modules});
  final List<AppModule> modules;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final width = constraints.maxWidth;
      final enlarged = MediaQuery.textScalerOf(context).scale(10) > 14;
      final columns = width >= 1000
          ? 3
          : width >= 340 && !enlarged
          ? 2
          : 1;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var start = 0; start < modules.length; start += columns)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  spacing: 12,
                  children: [
                    for (final module in modules.skip(start).take(columns))
                      Expanded(child: _ModuleTile(module)),
                  ],
                ),
              ),
            ),
        ],
      );
    },
  );
}

class _ModuleTile extends StatelessWidget {
  const _ModuleTile(this.module);
  final AppModule module;

  @override
  Widget build(BuildContext context) {
    final medical = context.medical;
    final text = Theme.of(context).textTheme;
    final accent = medical.accent(module.accent);
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
      side: BorderSide(color: accent.withValues(alpha: 0.22)),
    );
    return Material(
      color: Theme.of(context).colorScheme.surface,
      shape: shape,
      clipBehavior: Clip.antiAlias,
      child: Ink(
        color: medical.wash(module.accent),
        child: InkWell(
          onTap: () => context.go(module.path),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: accent,
                    borderRadius: BorderRadius.circular(13),
                  ),
                  child: Icon(
                    module.icon,
                    size: 24,
                    color: medical.isDark
                        ? Theme.of(context).colorScheme.surface
                        : Colors.white,
                  ),
                ),
                const SizedBox(height: 12),
                LText(module.title, style: text.titleMedium),
                const SizedBox(height: 2),
                LText(
                  module.implemented ? module.description : 'Đang phát triển',
                  style: text.bodySmall?.copyWith(color: medical.mutedText),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// A plain list row for the drawer and wide side panel.
class ModuleNavTile extends StatelessWidget {
  const ModuleNavTile({
    super.key,
    required this.icon,
    required this.title,
    required this.color,
    required this.selected,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final medical = context.medical;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
      child: ListTile(
        dense: true,
        minLeadingWidth: 34,
        contentPadding: const EdgeInsets.symmetric(horizontal: 10),
        leading: IconBadge(icon, color: color, size: 34),
        title: LText(
          title,
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            color: selected ? medical.accent(color) : null,
          ),
        ),
        selected: selected,
        selectedTileColor: medical.wash(color),
        onTap: onTap,
      ),
    );
  }
}
