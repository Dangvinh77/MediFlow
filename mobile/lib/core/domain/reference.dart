enum ReferenceKind { patient, staff, doctor, department, appointment, record }

class ReferenceOption {
  const ReferenceOption({required this.id, required this.name, this.detail});
  final String id, name;
  final String? detail;
}

class ReferencePage {
  const ReferencePage(this.items, {this.hasMore = false});
  final List<ReferenceOption> items;
  final bool hasMore;
}

abstract interface class ReferenceReader {
  Future<ReferenceOption> read(ReferenceKind kind, String id);
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  });
}

bool isReferenceId(String id) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(id);

bool canReadReference(String? role, ReferenceKind kind) => switch (kind) {
  ReferenceKind.patient ||
  ReferenceKind.record => const ['ADMIN', 'DOCTOR', 'NURSE'].contains(role),
  _ => const ['ADMIN', 'MANAGER', 'DOCTOR', 'NURSE'].contains(role),
};
