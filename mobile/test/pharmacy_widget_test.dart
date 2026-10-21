import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/pharmacy_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';

Widget host(
  PharmacyStub repository, {
  Widget child = const PharmacyPage(),
  String role = 'PHARMACIST',
  Object identity = 'session-one',
  bool signedIn = true,
  double scale = 1,
  Brightness brightness = Brightness.light,
}) => ProviderScope(
  overrides: [
    pharmacyRepositoryProvider.overrideWithValue(repository),
    pharmacyAccessProvider.overrideWithValue(
      signedIn ? PharmacyAccess(role: role, sessionIdentity: identity) : null,
    ),
  ],
  child: MaterialApp(
    theme: ThemeData(brightness: brightness),
    builder: (_, child) => MediaQuery(
      data: MediaQueryData(textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: Scaffold(body: child),
  ),
);

void main() {
  testWidgets(
    'logout clears visible drug fields and drops late detail errors',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(
        host(repository, child: const DrugDetailPage(drugId: drugId)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Tồn kho: 6'), findsOneWidget);
      final pending = Completer<Drug>();
      repository.detail = (_) => pending.future;
      await tester.tap(find.text('Làm mới thuốc'));
      await tester.pump();
      await tester.pumpWidget(
        host(
          repository,
          signedIn: false,
          child: const DrugDetailPage(drugId: drugId),
        ),
      );
      await tester.pumpAndSettle();
      pending.completeError(const ApiFailure('Old detail error'));
      await tester.pumpAndSettle();
      expect(find.text('Tồn kho: 6'), findsNothing);
      expect(find.text('Old detail error'), findsNothing);
      expect(repository.details, hasLength(2));
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'unbound page reports integration dependency without creating an adapter',
    (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(home: Scaffold(body: PharmacyPage())),
        ),
      );
      expect(find.textContaining('chưa sẵn sàng'), findsOneWidget);
    },
  );
  for (final role in ['ADMIN', 'DOCTOR', 'PHARMACIST']) {
    testWidgets('$role can read catalog and use the navigation callback', (
      tester,
    ) async {
      final repository = PharmacyStub();
      String? opened;
      await tester.pumpWidget(
        host(
          repository,
          role: role,
          child: PharmacyPage(onOpenDrug: (id) => opened = id),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Paracetamol 500 mg'), findsOneWidget);
      expect(find.text('Tồn thấp'), findsOneWidget);
      await tester.tap(find.text('Xem chi tiết'));
      expect(opened, drugId);
      expect(repository.searches, hasLength(1));
    });
  }
  testWidgets('forbidden role never loads data', (tester) async {
    final repository = PharmacyStub();
    await tester.pumpWidget(host(repository, role: 'NURSE'));
    expect(find.text('Bạn không có quyền xem Dược.'), findsOneWidget);
    expect(repository.searches, isEmpty);
  });
  testWidgets(
    'search resets page; refresh keeps submitted query despite changed draft',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(host(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Trang sau'));
      await tester.pumpAndSettle();
      expect(repository.searches.last.page, 1);
      await tester.enterText(find.byType(TextField), '  Vitamin C  ');
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      expect(repository.searches.last, (
        keyword: 'Vitamin C',
        page: 0,
        size: 20,
      ));
      await tester.enterText(find.byType(TextField), 'unsent');
      await tester.tap(find.text('Làm mới'));
      await tester.pumpAndSettle();
      expect(repository.searches.last.keyword, 'Vitamin C');
      expect(find.text('Kết quả cho: Vitamin C'), findsOneWidget);
    },
  );
  testWidgets('empty result can explicitly refresh and discover new data', (
    tester,
  ) async {
    final repository = PharmacyStub()
      ..search = (_, _, _) async => drugsPage(items: []);
    await tester.pumpWidget(host(repository));
    await tester.pumpAndSettle();
    expect(find.text('Không tìm thấy thuốc phù hợp.'), findsOneWidget);
    repository.search = null;
    await tester.tap(find.text('Làm mới'));
    await tester.pumpAndSettle();
    expect(find.text('Paracetamol 500 mg'), findsOneWidget);
  });
  testWidgets('retryable error retains requested page and correlation', (
    tester,
  ) async {
    final repository = PharmacyStub()
      ..search = (_, page, _) async {
        if (page == 1) {
          throw const ApiFailure(
            'Unavailable',
            status: 503,
            correlationId: 'page-trace',
          );
        }
        return drugsPage(page: page);
      };
    await tester.pumpWidget(host(repository));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Trang sau'));
    await tester.pumpAndSettle();
    expect(find.text('Mã hỗ trợ: page-trace'), findsOneWidget);
    expect(find.text('Paracetamol 500 mg'), findsNothing);
    repository.search = null;
    await tester.tap(find.text('Thử lại'));
    await tester.pumpAndSettle();
    expect(repository.searches.map((e) => e.page), [0, 1, 1]);
  });
  for (final status in [401, 403, 404]) {
    testWidgets('$status does not offer repeated GET retry', (tester) async {
      final repository = PharmacyStub()
        ..search = (_, _, _) async =>
            throw ApiFailure('Denied', status: status);
      await tester.pumpWidget(host(repository));
      await tester.pumpAndSettle();
      expect(find.text('Thử lại'), findsNothing);
      expect(repository.searches, hasLength(1));
    });
  }
  testWidgets('old query success cannot overwrite newer results', (
    tester,
  ) async {
    final pending = Completer<PageResult<Drug>>();
    final repository = PharmacyStub()
      ..search = (keyword, _, _) => keyword == null
          ? pending.future
          : Future.value(drugsPage(items: [drug(name: 'New query')]));
    await tester.pumpWidget(host(repository));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'new');
    await tester.tap(find.text('Tìm kiếm'));
    await tester.pumpAndSettle();
    pending.complete(drugsPage(items: [drug(name: 'Old query')]));
    await tester.pumpAndSettle();
    expect(find.text('New query'), findsOneWidget);
    expect(find.text('Old query'), findsNothing);
  });
  testWidgets(
    'session identity change discards old pending failure and search state',
    (tester) async {
      final pending = Completer<PageResult<Drug>>();
      final repository = PharmacyStub()..search = (_, _, _) => pending.future;
      await tester.pumpWidget(host(repository));
      await tester.pump();
      repository.search = (_, _, _) async =>
          drugsPage(items: [drug(name: 'New session')]);
      await tester.pumpWidget(host(repository, identity: 'new-session'));
      await tester.pumpAndSettle();
      pending.completeError(const ApiFailure('Old private failure'));
      await tester.pumpAndSettle();
      expect(find.text('New session'), findsOneWidget);
      expect(find.text('Old private failure'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'local detail navigation preserves catalog and exposes server fields',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(host(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Xem chi tiết'));
      await tester.pumpAndSettle();
      expect(find.text('Giá: 2500.25 VND'), findsOneWidget);
      expect(find.text('Nhà sản xuất: Chưa cung cấp'), findsOneWidget);
      expect(repository.details, [drugId]);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(repository.searches, hasLength(1));
    },
  );
  testWidgets(
    'detail refresh clears old fields and renders changed server stock',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(
        host(repository, child: const DrugDetailPage(drugId: drugId)),
      );
      await tester.pumpAndSettle();
      final pending = Completer<Drug>();
      repository.detail = (_) => pending.future;
      await tester.tap(find.text('Làm mới thuốc'));
      await tester.pump();
      expect(find.text('Tồn kho: 6'), findsNothing);
      pending.complete(drug(stock: 0));
      await tester.pumpAndSettle();
      expect(find.text('Tồn kho: 0'), findsOneWidget);
      expect(find.text('Hết hàng'), findsOneWidget);
    },
  );
  testWidgets('detail id change and disposal ignore old results', (
    tester,
  ) async {
    final pending = Completer<Drug>();
    final repository = PharmacyStub()
      ..detail = (id) => id == drugId
          ? pending.future
          : Future.value(drug(id: id, name: 'Other drug'));
    await tester.pumpWidget(
      host(repository, child: const DrugDetailPage(drugId: drugId)),
    );
    await tester.pump();
    await tester.pumpWidget(
      host(repository, child: const DrugDetailPage(drugId: otherDrugId)),
    );
    await tester.pumpAndSettle();
    pending.complete(drug(name: 'Stale drug'));
    await tester.pumpAndSettle();
    expect(find.text('Other drug'), findsOneWidget);
    expect(find.text('Stale drug'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets('invalid detail id never reaches repository or presents retry', (
    tester,
  ) async {
    final repository = PharmacyStub();
    await tester.pumpWidget(
      host(repository, child: const DrugDetailPage(drugId: 'invalid')),
    );
    await tester.pumpAndSettle();
    expect(repository.details, isEmpty);
    expect(find.text('Thử lại'), findsNothing);
  });
  for (final brightness in Brightness.values) {
    testWidgets('320px large text catalog/detail fits in $brightness theme', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 700);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = PharmacyStub();
      await tester.pumpWidget(
        host(repository, scale: 2, brightness: brightness),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(
        host(
          repository,
          scale: 2,
          brightness: brightness,
          child: const DrugDetailPage(drugId: drugId),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  }
}
