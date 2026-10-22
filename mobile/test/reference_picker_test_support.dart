import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/reference.dart';

class ReferencePickerFake implements ReferenceReader {
  ReferencePickerFake(this.options);

  final Map<ReferenceKind, List<ReferenceOption>> options;
  final reads = <(ReferenceKind, String)>[];
  final searches =
      <({ReferenceKind kind, String query, int page, String? scope})>[];

  @override
  Future<ReferenceOption> read(ReferenceKind kind, String id) async {
    reads.add((kind, id));
    return options[kind]!.firstWhere((option) => option.id == id);
  }

  @override
  Future<ReferencePage> search(
    ReferenceKind kind, {
    String query = '',
    int page = 0,
    String? scope,
  }) async {
    searches.add((kind: kind, query: query, page: page, scope: scope));
    final matching = options[kind]!
        .where(
          (option) => '${option.name} ${option.detail ?? ''}'
              .toLowerCase()
              .contains(query.toLowerCase()),
        )
        .toList(growable: false);
    return ReferencePage(matching);
  }
}

Future<void> pickReference(
  WidgetTester tester, {
  required String label,
  required String name,
}) async {
  final button = find.byTooltip('Chọn $label');
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
  await tester.tap(find.text(name).last);
  await tester.pumpAndSettle();
}
