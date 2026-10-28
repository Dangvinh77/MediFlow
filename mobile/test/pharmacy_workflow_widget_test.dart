import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/drug_forms.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/prescription_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';

Widget host(
  PharmacyStub repository,
  Widget page, {
  String? role = 'PHARMACIST',
  Object identity = 'one',
  PharmacyMode mode = PharmacyMode.unavailable,
  double scale = 1,
  Brightness brightness = Brightness.light,
}) => ProviderScope(
  overrides: [
    pharmacyRepositoryProvider.overrideWithValue(repository),
    pharmacyAccessProvider.overrideWithValue(
      role == null
          ? null
          : PharmacyAccess(role: role, sessionIdentity: identity),
    ),
    pharmacyModeProvider.overrideWithValue(mode),
  ],
  child: MaterialApp(
    theme: ThemeData(brightness: brightness),
    builder: (_, child) => MediaQuery(
      data: MediaQueryData(textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: Scaffold(body: page),
  ),
);

Future<void> enter(WidgetTester tester, String label, String value) async {
  final field = find.widgetWithText(TextField, label);
  await tester.ensureVisible(field);
  await tester.enterText(field, value);
  await tester.pump();
}

Future<void> tap(WidgetTester tester, String text) async {
  final button = find.ancestor(
    of: find.text(text),
    matching: find.byWidgetPredicate((widget) => widget is ButtonStyleButton),
  );
  await tester.ensureVisible(button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

Future<void> fillCreate(WidgetTester tester) async {
  for (final field in {
    'Tên thuốc': 'Thuốc mới',
    'Đơn vị': 'Viên',
    'Giá (đồng)': '2500.25',
    'Tồn kho ban đầu': '0',
    'Hạn dùng': '2099-12-31',
    'Báo sắp hết khi còn (tùy chọn)': '10',
  }.entries) {
    await enter(tester, field.key, field.value);
  }
}

void main() {
  for (final role in ['DOCTOR', 'PATIENT', 'MANAGER']) {
    testWidgets('$role cannot directly mount create or stock command', (
      tester,
    ) async {
      final repository = PharmacyStub();
      for (final page in [
        const CreateDrugPage(),
        const AdjustDrugStockPage(drugId: drugId),
      ]) {
        await tester.pumpWidget(host(repository, page, role: role));
        await tester.pumpAndSettle();
        expect(find.textContaining('không có quyền'), findsOneWidget);
        expect(find.byType(TextField), findsNothing);
      }
      expect(repository.details, isEmpty);
      expect(repository.creations, isEmpty);
      expect(repository.adjustments, isEmpty);
    });
  }
  testWidgets('create invalid fields focus first input without dispatch', (
    tester,
  ) async {
    final repository = PharmacyStub();
    await tester.pumpWidget(host(repository, const CreateDrugPage()));
    await tap(tester, 'Tạo thuốc');
    expect(repository.creations, isEmpty);
    expect(
      tester
          .widget<TextField>(find.widgetWithText(TextField, 'Tên thuốc'))
          .focusNode!
          .hasFocus,
      isTrue,
    );
    expect(find.textContaining('13 chữ số'), findsOneWidget);
  });
  for (final role in ['ADMIN', 'PHARMACIST']) {
    testWidgets(
      '$role create freezes one command while pending and opens server ID',
      (tester) async {
        final pending = Completer<Drug>();
        final repository = PharmacyStub()..create = (_) => pending.future;
        Drug? opened;
        await tester.pumpWidget(
          host(
            repository,
            CreateDrugPage(onCreated: (value) => opened = value),
            role: role,
          ),
        );
        await fillCreate(tester);
        await tap(tester, 'Tạo thuốc');
        await tap(tester, 'Xác nhận');
        expect(repository.creations, hasLength(1));
        expect(repository.creations.single.price, '2500.25');
        expect(
          tester
              .widget<FilledButton>(
                find.widgetWithText(FilledButton, 'Đang xử lý…'),
              )
              .onPressed,
          isNull,
        );
        pending.complete(drug(id: otherDrugId));
        await tester.pumpAndSettle();
        expect(opened!.id, otherDrugId);
        expect(repository.creations, hasLength(1));
        expect(find.textContaining('Đã tạo thuốc.'), findsOneWidget);
        expect(find.textContaining(otherDrugId), findsNothing);
      },
    );
  }
  testWidgets('400 field errors retain draft and focus server field', (
    tester,
  ) async {
    final repository = PharmacyStub()
      ..create = (_) async => throw const ApiFailure(
        'Invalid',
        status: 400,
        fields: {'price': 'Giá không hợp lệ'},
        correlationId: 'create-trace',
      );
    await tester.pumpWidget(host(repository, const CreateDrugPage()));
    await fillCreate(tester);
    await tap(tester, 'Tạo thuốc');
    await tap(tester, 'Xác nhận');
    expect(find.text('Vui lòng kiểm tra giá (đồng).'), findsOneWidget);
    expect(
      tester
          .widget<TextField>(find.widgetWithText(TextField, 'Giá (đồng)'))
          .focusNode!
          .hasFocus,
      isTrue,
    );
    expect(
      tester
          .widget<TextField>(find.widgetWithText(TextField, 'Tên thuốc'))
          .controller!
          .text,
      'Thuốc mới',
    );
    expect(repository.creations, hasLength(1));
  });
  for (final status in [403, 409, 503, null]) {
    testWidgets('create $status never automatically resends an unsafe POST', (
      tester,
    ) async {
      final repository = PharmacyStub()
        ..create = (_) async => throw ApiFailure(
          'Create failed',
          status: status,
          correlationId: 'trace',
        );
      await tester.pumpWidget(host(repository, const CreateDrugPage()));
      await fillCreate(tester);
      await tap(tester, 'Tạo thuốc');
      await tap(tester, 'Xác nhận');
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Tạo thuốc'),
            )
            .onPressed,
        isNull,
      );
      expect(repository.creations, hasLength(1));
      expect(find.widgetWithText(TextButton, 'Thử lại'), findsNothing);
      if (status != 403) {
        expect(
          find.textContaining('Chưa rõ thao tác đã hoàn tất'),
          findsOneWidget,
        );
      }
    });
  }
  testWidgets(
    'changing session removes confirmation and never sends prior draft',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(host(repository, const CreateDrugPage()));
      await fillCreate(tester);
      await tap(tester, 'Tạo thuốc');
      expect(find.byType(AlertDialog), findsOneWidget);
      await tester.pumpWidget(
        host(repository, const CreateDrugPage(), identity: 'two'),
      );
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
      expect(repository.creations, isEmpty);
      expect(
        tester
            .widget<TextField>(find.widgetWithText(TextField, 'Tên thuốc'))
            .controller!
            .text,
        isEmpty,
      );
    },
  );
  testWidgets(
    'logout during create drops late success and navigation callback',
    (tester) async {
      final pending = Completer<Drug>();
      final repository = PharmacyStub()..create = (_) => pending.future;
      var opened = false;
      final page = CreateDrugPage(onCreated: (_) => opened = true);
      await tester.pumpWidget(host(repository, page));
      await fillCreate(tester);
      await tap(tester, 'Tạo thuốc');
      await tap(tester, 'Xác nhận');
      await tester.pumpWidget(host(repository, page, role: null));
      await tester.pumpAndSettle();
      pending.complete(drug());
      await tester.pumpAndSettle();
      expect(opened, isFalse);
      expect(find.textContaining('Đã tạo thuốc'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'stock pending command locks a second submit and drops late result on ID change',
    (tester) async {
      final pending = Completer<Drug>();
      final repository = PharmacyStub()..adjust = (_, _) => pending.future;
      await tester.pumpWidget(
        host(repository, const AdjustDrugStockPage(drugId: drugId)),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'Số lượng điều chỉnh (+/−)', '2');
      await tap(tester, 'Điều chỉnh tồn kho');
      await tap(tester, 'Xác nhận');
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Đang xử lý…'),
            )
            .onPressed,
        isNull,
      );
      expect(repository.adjustments, hasLength(1));
      await tester.pumpWidget(
        host(repository, const AdjustDrugStockPage(drugId: otherDrugId)),
      );
      await tester.pumpAndSettle();
      pending.complete(drug(id: drugId, stock: 100));
      await tester.pumpAndSettle();
      expect(find.text('Tồn kho: 100'), findsNothing);
      expect(find.textContaining(otherDrugId), findsNothing);
      expect(find.text('Paracetamol 500 mg'), findsOneWidget);
      expect(find.text('Thông tin tra cứu'), findsOneWidget);
      await tester.ensureVisible(find.text('Thông tin tra cứu'));
      await tester.tap(find.text('Thông tin tra cứu'));
      await tester.pumpAndSettle();
      expect(find.text('Mã thuốc: $otherDrugId'), findsOneWidget);
      expect(repository.adjustments, hasLength(1));
    },
  );
  for (final delta in ['-2', '3']) {
    testWidgets(
      'stock $delta confirms sign, applies once and uses returned stock',
      (tester) async {
        final repository = PharmacyStub();
        await tester.pumpWidget(
          host(repository, const AdjustDrugStockPage(drugId: drugId)),
        );
        await tester.pumpAndSettle();
        await enter(tester, 'Số lượng điều chỉnh (+/−)', delta);
        await enter(tester, 'Lý do điều chỉnh', 'Kiểm kê');
        await tap(tester, 'Điều chỉnh tồn kho');
        expect(
          find.textContaining(delta.startsWith('-') ? 'giảm 2' : 'tăng 3'),
          findsOneWidget,
        );
        await tap(tester, 'Xác nhận');
        expect(repository.adjustments.single.$2.quantity, int.parse(delta));
        expect(find.text('Điều chỉnh thành công. Tồn kho: 11'), findsOneWidget);
        expect(
          tester
              .widget<FilledButton>(
                find.widgetWithText(FilledButton, 'Điều chỉnh tồn kho'),
              )
              .onPressed,
          isNull,
        );
      },
    );
  }
  testWidgets('zero stock delta cannot dispatch', (tester) async {
    final repository = PharmacyStub();
    await tester.pumpWidget(
      host(repository, const AdjustDrugStockPage(drugId: drugId)),
    );
    await tester.pumpAndSettle();
    await enter(tester, 'Số lượng điều chỉnh (+/−)', '0');
    await tap(tester, 'Điều chỉnh tồn kho');
    expect(repository.adjustments, isEmpty);
    expect(find.textContaining('khác 0'), findsOneWidget);
  });
  testWidgets(
    'unknown stock result requires successful read then a blank new intent',
    (tester) async {
      final repository = PharmacyStub()
        ..adjust = (_, _) async => throw const ApiFailure('Lost response');
      await tester.pumpWidget(
        host(repository, const AdjustDrugStockPage(drugId: drugId)),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'Số lượng điều chỉnh (+/−)', '2');
      await tap(tester, 'Điều chỉnh tồn kho');
      await tap(tester, 'Xác nhận');
      repository.detail = (_) async =>
          throw const ApiFailure('Refresh failed', status: 503);
      await tap(tester, 'Kiểm tra lại tồn kho');
      expect(find.text('Bắt đầu điều chỉnh mới'), findsNothing);
      repository.detail = (_) async => drug(stock: 8);
      await tap(tester, 'Kiểm tra lại tồn kho');
      expect(repository.adjustments, hasLength(1));
      await tap(tester, 'Bắt đầu điều chỉnh mới');
      expect(
        tester
            .widget<TextField>(
              find.widgetWithText(TextField, 'Số lượng điều chỉnh (+/−)'),
            )
            .controller!
            .text,
        isEmpty,
      );
      expect(find.text('Tồn kho: 8'), findsOneWidget);
    },
  );
  testWidgets(
    'reservation constraint retains server error, never subtracts stock',
    (tester) async {
      final repository = PharmacyStub()
        ..adjust = (_, _) async => throw const ApiFailure(
          'Reserved',
          status: 422,
          fields: {'quantity': 'Tồn kho đã được giữ chỗ'},
          correlationId: 'reserve-trace',
        );
      await tester.pumpWidget(
        host(repository, const AdjustDrugStockPage(drugId: drugId)),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'Số lượng điều chỉnh (+/−)', '-6');
      await tap(tester, 'Điều chỉnh tồn kho');
      await tap(tester, 'Xác nhận');
      expect(
        find.text('Vui lòng kiểm tra số lượng điều chỉnh (+/−).'),
        findsOneWidget,
      );
      expect(find.text('Tồn kho: 6'), findsOneWidget);
      expect(repository.adjustments, hasLength(1));
    },
  );
  testWidgets('failed initial stock read cannot enable mutation', (
    tester,
  ) async {
    final repository = PharmacyStub()
      ..detail = (_) async =>
          throw const ApiFailure('Unavailable', status: 503);
    await tester.pumpWidget(
      host(repository, const AdjustDrugStockPage(drugId: drugId)),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Điều chỉnh tồn kho'),
          )
          .onPressed,
      isNull,
    );
    expect(repository.adjustments, isEmpty);
  });
  for (final mode in PharmacyMode.values) {
    testWidgets(
      'Rx $mode keeps nullable record, false legacy flag and distinct states',
      (tester) async {
        final repository = PharmacyStub();
        await tester.pumpWidget(
          host(
            repository,
            const PrescriptionDetailPage(prescriptionId: rxId),
            mode: mode,
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Hồ sơ: Chưa cung cấp'), findsOneWidget);
        expect(find.text('Trạng thái đơn: Có thể sử dụng'), findsOneWidget);
        expect(find.text('Trạng thái xuất: Chờ xuất'), findsOneWidget);
        expect(
          find.text('Thanh toán đã ghi nhận: Chưa'),
          mode == PharmacyMode.legacy ? findsOneWidget : findsNothing,
        );
        expect(find.text('Tổng tiền: 5000.5 đồng'), findsOneWidget);
        if (mode == PharmacyMode.outpatient) {
          // V1 ignores the legacy flag; missing transport capability still
          // prevents sending an actual command from this unbound host.
          expect(
            tester
                .widget<FilledButton>(
                  find.widgetWithText(FilledButton, 'Xuất thuốc'),
                )
                .onPressed,
            isNull,
          );
          expect(
            find.textContaining('xuất thuốc hiện chưa sẵn sàng'),
            findsOneWidget,
          );
        } else {
          expect(find.widgetWithText(FilledButton, 'Xuất thuốc'), findsNothing);
        }
        if (mode == PharmacyMode.unavailable) {
          expect(find.textContaining('hiện chưa sẵn sàng'), findsOneWidget);
        }
      },
    );
  }
  for (final status in [
    'FULFILLED',
    'CANCELLED',
    'EXPIRED',
    'DISPENSE_FAILED',
  ]) {
    testWidgets('terminal Rx $status renders authoritative status and audit', (
      tester,
    ) async {
      final repository = PharmacyStub()
        ..prescription = (_) async => PharmacyDto.prescription({
          ...prescriptionJson(),
          'status': status,
          'dispenseStatus': 'CANCELLED',
          'cancelledAt': '2026-10-20T02:00:00Z',
          'cancelledBy': drugId,
          'cancellationReason': 'Lý do từ máy chủ',
        });
      await tester.pumpWidget(
        host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Trạng thái đơn: Có thể sử dụng'), findsNothing);
      expect(find.text('Lý do hủy: Lý do từ máy chủ'), findsOneWidget);
    });
  }
  for (final status in [401, 403, 404, 503]) {
    testWidgets(
      'Rx $status keeps error and correlation instead of empty success',
      (tester) async {
        final repository = PharmacyStub()
          ..prescription = (_) async => throw ApiFailure(
            'Rx error $status',
            status: status,
            correlationId: 'rx-trace',
          );
        await tester.pumpWidget(
          host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
        );
        await tester.pumpAndSettle();
        expect(find.text('Rx error $status'), findsNothing);
        expect(
          find.text(switch (status) {
            401 => 'Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.',
            403 => 'Tài khoản này chưa được phép thực hiện thao tác.',
            404 => 'Không tìm thấy thông tin cần tra cứu.',
            _ => 'Chưa kết nối được để xác nhận kết quả. Vui lòng kiểm tra lại trước khi thao tác tiếp.',
          }),
          findsOneWidget,
        );
        expect(find.text('Mã hỗ trợ: rx-trace'), findsOneWidget);
        expect(find.textContaining('Tổng tiền'), findsNothing);
        expect(
          find.text('Thử lại'),
          status == 503 ? findsOneWidget : findsNothing,
        );
      },
    );
  }
  testWidgets(
    'lookup validates UUID and drops stale response when switching ID',
    (tester) async {
      final pending = Completer<Prescription>();
      final repository = PharmacyStub()
        ..prescription = (id) => id == rxId
            ? pending.future
            : Future.value(
                PharmacyDto.prescription({
                  ...prescriptionJson(),
                  'prescriptionId': id,
                }),
              );
      await tester.pumpWidget(host(repository, const PrescriptionLookupPage()));
      await enter(tester, 'Mã đơn thuốc', 'bad');
      await tap(tester, 'Tra cứu đơn thuốc');
      expect(repository.prescriptions, isEmpty);
      await enter(tester, 'Mã đơn thuốc', rxId);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pump();
      await enter(tester, 'Mã đơn thuốc', otherDrugId);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      pending.completeError(const ApiFailure('Stale failure'));
      await tester.pumpAndSettle();
      expect(find.text('Tra cứu bằng mã'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('prescription-lookup-id')),
        findsNothing,
      );
      expect(find.text('Đơn thuốc: $otherDrugId'), findsNothing);
      await tester.tap(find.text('Tra cứu bằng mã'));
      await tester.pumpAndSettle();
      final lookup = tester.widget<TextField>(
        find.byKey(const ValueKey('prescription-lookup-id')),
      );
      expect(lookup.controller!.text, otherDrugId);
      final prescriptionScrollable = find.descendant(
        of: find.byType(ListView).last,
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text('Thông tin tra cứu'),
        200,
        scrollable: prescriptionScrollable.first,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Thông tin tra cứu'));
      await tester.pumpAndSettle();
      expect(find.text('Đơn thuốc: $otherDrugId'), findsOneWidget);
      expect(find.text('Stale failure'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('Rx session replacement clears facts and ignores late refresh', (
    tester,
  ) async {
    final repository = PharmacyStub();
    final pending = Completer<Prescription>();
    await tester.pumpWidget(
      host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
    );
    await tester.pumpAndSettle();
    repository.prescription = (_) => pending.future;
    await tester.ensureVisible(find.text('Làm mới đơn thuốc'));
    await tester.tap(find.text('Làm mới đơn thuốc'));
    await tester.pump();
    await tester.pumpWidget(
      host(
        repository,
        const PrescriptionDetailPage(prescriptionId: rxId),
        role: null,
      ),
    );
    pending.complete(PharmacyDto.prescription(prescriptionJson()));
    await tester.pumpAndSettle();
    expect(find.textContaining('Tổng tiền'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  for (final brightness in Brightness.values) {
    testWidgets('small viewport font scale 2 $brightness fits forms and Rx', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 700);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final repository = PharmacyStub();
      for (final page in [
        const CreateDrugPage(),
        const AdjustDrugStockPage(drugId: drugId),
        const PrescriptionDetailPage(prescriptionId: rxId),
      ]) {
        await tester.pumpWidget(
          host(repository, page, scale: 2, brightness: brightness),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      }
    });
  }
}
