import '../../../core/l10n/app_l10n.dart';

import 'package:flutter/material.dart';

import '../../../core/theme/medical_colors.dart';
import '../../../core/widgets/medical_ui.dart';

/// Display labels shared with the web organization screens. The wire codes
/// stay unchanged; only what the reader sees is translated.
const departmentTypeLabels = {
  'CLINICAL': 'Lâm sàng',
  'PARACLINICAL': 'Cận lâm sàng',
  'ADMINISTRATIVE': 'Bộ phận chức năng',
};

const staffJobTitleLabels = {
  'DOCTOR': 'Bác sĩ',
  'NURSE': 'Điều dưỡng',
  'TECHNICIAN': 'Kỹ thuật viên',
  'PHARMACIST': 'Dược sĩ',
  'CASHIER': 'Thu ngân',
  'ADMINISTRATIVE': 'Hành chính',
};

const accountRoleLabels = {
  'ADMIN': 'Quản trị viên',
  'DOCTOR': 'Bác sĩ',
  'NURSE': 'Điều dưỡng',
  'PHARMACIST': 'Dược sĩ',
  'CASHIER': 'Thu ngân',
  'LAB_TECH': 'Kỹ thuật viên xét nghiệm',
  'PATIENT': 'Bệnh nhân',
  'SYSTEM': 'Hệ thống',
};

String accountRoleLabel(String code) => accountRoleLabels[code] ?? code;
String departmentTypeLabel(String code) => departmentTypeLabels[code] ?? code;
String staffJobTitleLabel(String code) => staffJobTitleLabels[code] ?? code;

/// The unit noun the web uses: a clinical unit is a "khoa", others a "bộ phận".
String departmentUnit(String type) => type == 'CLINICAL' ? 'khoa' : 'bộ phận';

String _initials(String name) {
  final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty);
  final tail = parts.length > 2 ? parts.skip(parts.length - 2) : parts;
  return tail.map((part) => part[0]).join().toUpperCase();
}

/// Mirrors the web `RecordIdentity` card: initials, name, subtitle, status
/// and the primary action.
class OrganizationIdentityCard extends StatelessWidget {
  const OrganizationIdentityCard({
    super.key,
    required this.name,
    required this.subtitle,
    required this.statusLabel,
    required this.active,
    this.action,
  });
  final String name, subtitle, statusLabel;
  final bool active;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 24,
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                  child: Text(
                    _initials(name),
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      LText(
                        name,
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 2),
                      LText(
                        subtitle,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: context.medical.mutedText,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                StatusPill(
                  statusLabel,
                  tone: active ? StatusTone.success : StatusTone.neutral,
                ),
                ?action,
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Mirrors the web `DetailSection`: a titled card holding label/value rows.
class OrganizationDetailSection extends StatelessWidget {
  const OrganizationDetailSection({
    super.key,
    required this.title,
    required this.children,
  });
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
          child: LText(
            title,
            style: Theme.of(context).textTheme.titleMedium
                ?.copyWith(fontWeight: FontWeight.w600),
          ),
        ),
        const Divider(height: 1),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: children,
          ),
        ),
      ],
    ),
  );
}

/// A list row for a department or staff member, laid out like the web table
/// row: name, secondary facts and a status marker.
class OrganizationListTile extends StatelessWidget {
  const OrganizationListTile({
    super.key,
    required this.title,
    required this.subtitle,
    required this.statusLabel,
    required this.active,
    required this.onTap,
  });
  final String title, subtitle, statusLabel;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    margin: EdgeInsets.zero,
    child: ListTile(
      contentPadding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
      title: LText(title, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            LText(subtitle),
            const SizedBox(height: 6),
            StatusPill(
              statusLabel,
              tone: active ? StatusTone.success : StatusTone.neutral,
            ),
          ],
        ),
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    ),
  );
}
