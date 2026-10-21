import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/domain/workspace_access.dart';
import '../../../core/network/api_failure.dart';
import '../../../core/providers.dart';
import '../domain/organization.dart';
import 'providers.dart';

bool canRetryOrganization(Object? error) =>
    error is! FormatException &&
    error is! ArgumentError &&
    (error is! ApiFailure ||
        error.status == null ||
        error.status! >= 500 ||
        error.status == 429);

class OrganizationBoundary extends ConsumerWidget {
  const OrganizationBoundary({super.key, required this.builder});
  final Widget Function(OrganizationRepository, WorkspaceAccess) builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(workspaceAccessProvider);
    final repository = ref.watch(organizationRepositoryProvider);
    if (access == null) {
      return const Center(child: Text('Phiên đăng nhập chưa sẵn sàng.'));
    }
    if (!const {'ADMIN', 'MANAGER', 'DOCTOR', 'NURSE'}.contains(access.role)) {
      return const Center(
        child: Text('Bạn không có quyền xem thông tin tổ chức.'),
      );
    }
    return KeyedSubtree(
      key: ValueKey((access.sessionIdentity, repository)),
      child: builder(repository, access),
    );
  }
}

class OrganizationField extends StatelessWidget {
  const OrganizationField(this.label, this.value, {super.key});
  final String label;
  final String? value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(width: 128, child: Text(label)),
        Expanded(
          child: SelectableText(value?.isNotEmpty == true ? value! : '—'),
        ),
      ],
    ),
  );
}
