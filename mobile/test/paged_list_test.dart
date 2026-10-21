import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/widgets/paged_list.dart';

PageResult<String> result(int page) => PageResult(
  items: ['row-$page'],
  number: page,
  totalPages: 3,
  totalElements: 41,
);

void main() {
  testWidgets('feature policy suppresses retry after forbidden response', (
    tester,
  ) async {
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PagedList<String>(
            load: (_) async {
              calls++;
              throw const ApiFailure('Forbidden', status: 403);
            },
            canRetry: (error) => error is! ApiFailure || error.status != 403,
            itemBuilder: (_, item) => Text(item),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Forbidden'), findsOneWidget);
    expect(find.text('Thử lại'), findsNothing);
    expect(calls, 1);
  });
  testWidgets(
    'next page, error correlation and retry preserve requested page',
    (tester) async {
      final requested = <int>[];
      var fail = true;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PagedList<String>(
              load: (page) async {
                requested.add(page);
                if (page == 1 && fail) {
                  fail = false;
                  throw const ApiFailure(
                    'Unavailable',
                    correlationId: 'trace-page',
                  );
                }
                return result(page);
              },
              itemBuilder: (_, item) => Text(item),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('row-0'), findsOneWidget);
      await tester.tap(find.byTooltip('Trang sau'));
      await tester.pumpAndSettle();
      expect(find.text('Mã hỗ trợ: trace-page'), findsOneWidget);
      await tester.tap(find.text('Thử lại'));
      await tester.pumpAndSettle();
      expect(requested, [0, 1, 1]);
      expect(find.text('row-1'), findsOneWidget);
    },
  );
  testWidgets('late response after unmount never changes disposed state', (
    tester,
  ) async {
    final pending = Completer<PageResult<String>>();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PagedList<String>(
            load: (_) => pending.future,
            itemBuilder: (_, item) => Text(item),
          ),
        ),
      ),
    );
    await tester.pumpWidget(const SizedBox());
    pending.complete(result(0));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
  testWidgets('changing filter identity ignores old request failure', (
    tester,
  ) async {
    final pending = Completer<PageResult<String>>();
    Widget host(String id, Future<PageResult<String>> Function(int) load) =>
        MaterialApp(
          home: Scaffold(
            body: PagedList<String>(
              key: ValueKey(id),
              load: load,
              itemBuilder: (_, item) => Text(item),
            ),
          ),
        );
    await tester.pumpWidget(host('old', (_) => pending.future));
    await tester.pumpWidget(host('new', (_) async => result(2)));
    await tester.pumpAndSettle();
    pending.completeError(const ApiFailure('Old failure'));
    await tester.pumpAndSettle();
    expect(find.text('row-2'), findsOneWidget);
    expect(find.text('Old failure'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
