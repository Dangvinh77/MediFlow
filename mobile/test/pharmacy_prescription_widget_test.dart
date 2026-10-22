import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/core/domain/workspace_access.dart';
import 'package:mediflow_mobile/core/providers.dart';
import 'package:mediflow_mobile/core/widgets/reference_widgets.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/data/prescription_request_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/create_prescription_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/prescription_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';
import 'reference_picker_test_support.dart';

Future<void> visible(WidgetTester tester, Finder finder) async {
  if (finder.evaluate().isEmpty) {
    final list = find.descendant(
      of: find.byType(ListView),
      matching: find.byType(Scrollable),
    );
    await tester.scrollUntilVisible(
      finder,
      200,
      scrollable: list.evaluate().isEmpty
          ? find.byType(Scrollable).first
          : list.first,
    );
  }
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
}

Future<void> enter(WidgetTester tester, String label, String value) async {
  final field = find.widgetWithText(TextField, label);
  await visible(tester, field);
  await tester.enterText(field, value);
  await tester.pump();
}

Future<void> tap(WidgetTester tester, String text) async {
  final button = find.ancestor(
    of: find.text(text),
    matching: find.byWidgetPredicate((widget) => widget is ButtonStyleButton),
  );
  await visible(tester, button);
  await tester.tap(button);
  await tester.pumpAndSettle();
}

Future<void> pumpCommand(WidgetTester tester) async {
  // Drive both exit-transition start and completion without settling a pending
  // network progress indicator. Assertions below still require exactly one call.
  await tester.pump();
  await tester.pump(const Duration(seconds: 1));
  await tester.pump();
}

class Writer implements OutpatientPrescriptionWriter {
  final intents = <PrescriptionIntent>[];
  Future<Prescription> Function(PrescriptionIntent)? send;
  @override
  Future<Prescription> createPrescription(PrescriptionIntent intent) {
    intents.add(intent);
    return send?.call(intent) ??
        Future.value(PharmacyDto.prescription(prescriptionJson()));
  }
}

Widget host(
  PharmacyStub repository,
  Widget child, {
  Writer? writer,
  String? role = 'DOCTOR',
  Object identity = 'one',
  PharmacyMode mode = PharmacyMode.outpatient,
  Brightness brightness = Brightness.light,
  double scale = 1,
  ReferencePickerFake? referenceReader,
}) => ProviderScope(
  overrides: [
    pharmacyRepositoryProvider.overrideWithValue(repository),
    pharmacyAccessProvider.overrideWithValue(
      role == null
          ? null
          : PharmacyAccess(role: role, sessionIdentity: identity),
    ),
    pharmacyModeProvider.overrideWithValue(mode),
    outpatientPrescriptionWriterProvider.overrideWithValue(writer),
    workspaceAccessProvider.overrideWithValue(
      role == null
          ? null
          : WorkspaceAccess(sessionIdentity: identity, role: role),
    ),
    referenceReaderProvider.overrideWithValue(
      referenceReader ??
          ReferencePickerFake({
            ReferenceKind.patient: [
              ReferenceOption(id: otherDrugId, name: 'Nguyen An'),
            ],
            ReferenceKind.doctor: [
              ReferenceOption(id: drugId, name: 'Tran Binh'),
            ],
            ReferenceKind.staff: [
              ReferenceOption(id: drugId, name: 'Tran Binh'),
            ],
            ReferenceKind.department: [
              ReferenceOption(id: otherDrugId, name: 'Khoa Ngoai tru'),
            ],
            ReferenceKind.record: [
              ReferenceOption(id: drugId, name: 'Ho so An'),
            ],
            ReferenceKind.appointment: [
              ReferenceOption(id: rxId, name: 'Lich hen An'),
            ],
          }),
    ),
  ],
  child: MaterialApp(
    theme: ThemeData(useMaterial3: true, brightness: brightness),
    builder: (_, child) => MediaQuery(
      data: MediaQueryData(textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: Scaffold(body: child),
  ),
);

Future<void> fillRx(WidgetTester tester) async {
  for (final field in {
    'Bệnh nhân': 'Nguyen An',
    'Khoa kê đơn': 'Khoa Ngoai tru',
    'Bác sĩ kê đơn': 'Tran Binh',
    'Hồ sơ bệnh án': 'Ho so An',
    'Lượt khám ngoại trú': 'Lich hen An',
  }.entries) {
    await pickReference(tester, label: field.key, name: field.value);
  }
  for (final field in {
    'Mã giá Billing': 'CATALOG-CONFIRMED',
    'Nguồn đã đối chiếu': 'Public visit receipt + Billing policy',
    'Ngày kê đơn (YYYY-MM-DD)': '2026-10-20',
  }.entries) {
    await enter(tester, field.key, field.value);
  }
  await tap(tester, 'Tìm trong danh mục');
  final choose = find.byTooltip('Chọn Paracetamol 500 mg');
  await tester.ensureVisible(choose);
  await tester.tap(choose);
  await tester.pumpAndSettle();
  await enter(tester, 'Số lượng dòng 1', '2');
}

Future<void> cancel(WidgetTester tester) async {
  await enter(tester, 'Lý do hủy đơn', 'Đổi chỉ định');
  await tap(tester, 'Hủy đơn thuốc');
  await tap(tester, 'Xác nhận hủy');
}

void main() {
  testWidgets(
    'unbound writer keeps real creation disabled and performs no POST',
    (tester) async {
      final repository = PharmacyStub();
      await tester.pumpWidget(host(repository, const CreatePrescriptionPage()));
      await tester.pumpAndSettle();
      expect(find.textContaining('đang chờ client Gateway'), findsOneWidget);
      final button = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Tạo đơn V1'),
      );
      expect(button.onPressed, isNull);
      expect(repository.creations, isEmpty);
    },
  );
  for (final role in ['PHARMACIST', 'PATIENT', 'MANAGER']) {
    testWidgets('$role cannot open prescription creation', (tester) async {
      final writer = Writer();
      await tester.pumpWidget(
        host(
          PharmacyStub(),
          const CreatePrescriptionPage(),
          writer: writer,
          role: role,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsNothing);
      expect(writer.intents, isEmpty);
    });
  }
  for (final mode in [PharmacyMode.legacy, PharmacyMode.unavailable]) {
    testWidgets('$mode cannot silently select or fall back to V1', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(
          PharmacyStub(),
          const CreatePrescriptionPage(),
          writer: Writer(),
          mode: mode,
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Kê đơn ngoại trú V1 chưa được cấu hình.'),
        findsOneWidget,
      );
      expect(find.byType(TextField), findsNothing);
    });
  }
  testWidgets(
    'missing authority context focuses first name selector and sends nothing',
    (tester) async {
      final writer = Writer();
      await tester.pumpWidget(
        host(PharmacyStub(), const CreatePrescriptionPage(), writer: writer),
      );
      await tap(tester, 'Tạo đơn V1');
      expect(writer.intents, isEmpty);
      expect(find.text('Hồ sơ bệnh án'), findsOneWidget);
      expect(find.textContaining('UUID'), findsNothing);
      expect(
        tester
            .widget<Focus>(
              find.byKey(const ValueKey('prescription-reference-patientId')),
            )
            .focusNode!
            .hasFocus,
        isTrue,
      );
    },
  );
  testWidgets(
    'unknown creation freezes draft and retries exact same object, key and body',
    (tester) async {
      final writer = Writer()
        ..send = (_) async => throw const ApiFailure('Unknown', status: 503);
      final references = ReferencePickerFake({
        ReferenceKind.patient: [
          const ReferenceOption(id: otherDrugId, name: 'Nguyen An'),
        ],
        ReferenceKind.department: [
          const ReferenceOption(id: otherDrugId, name: 'Khoa Ngoai tru'),
        ],
        ReferenceKind.doctor: [
          const ReferenceOption(id: drugId, name: 'Tran Binh'),
        ],
        ReferenceKind.record: [
          const ReferenceOption(id: drugId, name: 'Ho so An'),
        ],
        ReferenceKind.appointment: [
          const ReferenceOption(id: rxId, name: 'Lich hen An'),
        ],
      });
      await tester.pumpWidget(
        host(
          PharmacyStub(),
          const CreatePrescriptionPage(),
          writer: writer,
          referenceReader: references,
        ),
      );
      await fillRx(tester);
      expect(references.searches.map((search) => (search.kind, search.scope)), [
        (ReferenceKind.patient, null),
        (ReferenceKind.department, null),
        (ReferenceKind.doctor, otherDrugId),
        (ReferenceKind.record, otherDrugId),
        (ReferenceKind.appointment, otherDrugId),
      ]);
      await tap(tester, 'Tạo đơn V1');
      expect(writer.intents, isEmpty);
      expect(find.text('Hồ sơ: Ho so An'), findsOneWidget);
      expect(find.text('Lượt khám: Lich hen An'), findsOneWidget);
      expect(find.textContaining(drugId), findsNothing);
      expect(find.textContaining(otherDrugId), findsNothing);
      expect(find.textContaining(rxId), findsNothing);
      await tap(tester, 'Xác nhận kê đơn');
      expect(writer.intents, hasLength(1));
      final original = writer.intents.single;
      final body = PrescriptionRequestDto.outpatient(original);
      expect(body['recordId'], drugId);
      expect(body['patientId'], otherDrugId);
      expect(body['departmentId'], otherDrugId);
      expect(body['careEpisodeId'], rxId);
      final recordPicker = find.ancestor(
        of: find.byTooltip('Chọn Hồ sơ bệnh án'),
        matching: find.byType(IconButton),
      );
      expect(tester.widget<IconButton>(recordPicker).onPressed, isNull);
      writer.send = null;
      await tap(tester, 'Thử lại cùng mã và nội dung');
      await tap(tester, 'Xác nhận kê đơn');
      expect(writer.intents, hasLength(2));
      expect(identical(writer.intents[0], writer.intents[1]), isTrue);
      expect(writer.intents.last.key, original.key);
      expect(PrescriptionRequestDto.outpatient(writer.intents.last), body);
      expect(find.text('Đã tạo đơn: $rxId'), findsOneWidget);
    },
  );
  for (final status in [400, 401, 403, 409, 422, 408, 503]) {
    testWidgets('creation $status preserves contract and never auto retries', (
      tester,
    ) async {
      final writer = Writer()
        ..send = (_) async => throw ApiFailure(
          'Denied',
          status: status,
          correlationId: 'rx-trace',
        );
      await tester.pumpWidget(
        host(PharmacyStub(), const CreatePrescriptionPage(), writer: writer),
      );
      await fillRx(tester);
      await tap(tester, 'Tạo đơn V1');
      await tap(tester, 'Xác nhận kê đơn');
      expect(writer.intents, hasLength(1));
      expect(find.text('Mã hỗ trợ: rx-trace'), findsOneWidget);
      expect(
        find.text('Thử lại cùng mã và nội dung'),
        status == 408 || status == 503 ? findsOneWidget : findsNothing,
      );
      expect(find.text('Đã tạo đơn: $rxId'), findsNothing);
    });
  }
  testWidgets(
    'pending creation blocks duplicate tap and drops late success on logout',
    (tester) async {
      final pending = Completer<Prescription>();
      final writer = Writer()..send = (_) => pending.future;
      var opened = 0;
      final page = CreatePrescriptionPage(onCreated: (_) => opened++);
      await tester.pumpWidget(host(PharmacyStub(), page, writer: writer));
      await fillRx(tester);
      await tap(tester, 'Tạo đơn V1');
      // pumpAndSettle cannot settle the pending request's progress indicator.
      await tester.tap(find.text('Xác nhận kê đơn'));
      await pumpCommand(tester);
      expect(writer.intents, hasLength(1));
      expect(find.widgetWithText(FilledButton, 'Tạo đơn V1'), findsNothing);
      await tester.pumpWidget(
        host(PharmacyStub(), page, writer: writer, role: null),
      );
      pending.complete(PharmacyDto.prescription(prescriptionJson()));
      await tester.pumpAndSettle();
      expect(opened, 0);
      expect(find.textContaining('Đã tạo đơn:'), findsNothing);
    },
  );
  testWidgets('prescription detail shows reference names and hides UUIDs', (
    tester,
  ) async {
    final repository = PharmacyStub()
      ..prescription = (_) async => PharmacyDto.prescription({
        ...prescriptionJson(),
        'recordId': otherDrugId,
        'cancelledBy': rxId,
        'status': 'CANCELLED',
        'cancellationReason': 'Đổi chỉ định',
        'cancelledAt': '2026-10-22T10:00:00Z',
      });
    final references = ReferencePickerFake({
      ReferenceKind.record: [
        const ReferenceOption(id: otherDrugId, name: 'Ho so An'),
      ],
      ReferenceKind.staff: [
        const ReferenceOption(id: rxId, name: 'Tran Thi Binh'),
      ],
    });
    await tester.pumpWidget(
      host(
        repository,
        const PrescriptionDetailPage(prescriptionId: rxId),
        role: 'ADMIN',
        referenceReader: references,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Hồ sơ: Ho so An'), findsOneWidget);
    expect(find.text('Người hủy: Tran Thi Binh'), findsOneWidget);
    expect(find.textContaining(drugId), findsNothing);
    expect(find.textContaining(otherDrugId), findsNothing);
    expect(find.textContaining(rxId), findsNothing);

    await visible(tester, find.text('Chi tiết kỹ thuật'));
    await tester.tap(find.text('Chi tiết kỹ thuật'));
    await tester.pumpAndSettle();
    expect(find.text('Đơn thuốc: $rxId'), findsOneWidget);
    expect(find.text('Hồ sơ: $otherDrugId'), findsOneWidget);
    expect(find.text('Mã thuốc: $drugId'), findsOneWidget);
    expect(find.text('Người hủy ID: $rxId'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'changing session dismisses creation confirmation and clears source values',
    (tester) async {
      final repository = PharmacyStub();
      final writer = Writer();
      await tester.pumpWidget(
        host(repository, const CreatePrescriptionPage(), writer: writer),
      );
      await fillRx(tester);
      await tap(tester, 'Tạo đơn V1');
      await tester.pumpWidget(
        host(
          repository,
          const CreatePrescriptionPage(),
          writer: writer,
          identity: 'two',
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
      expect(find.text('Chọn theo tên'), findsNWidgets(5));
      expect(writer.intents, isEmpty);
    },
  );
  for (final role in ['ADMIN', 'DOCTOR']) {
    testWidgets('$role cancels using receipt even when follow-up read fails', (
      tester,
    ) async {
      final repository = PharmacyStub();
      repository.cancel = (id, command) async {
        repository.prescription = (_) async =>
            throw const ApiFailure('Read unavailable', status: 503);
        return CancelPrescriptionResult(
          prescriptionId: id,
          status: PrescriptionStatus.cancelled,
          releasedReservations: 1,
          cancelledAt: DateTime.utc(2026, 10, 22),
        );
      };
      await tester.pumpWidget(
        host(
          repository,
          const PrescriptionDetailPage(prescriptionId: rxId),
          role: role,
        ),
      );
      await tester.pumpAndSettle();
      await cancel(tester);
      expect(repository.cancellations, hasLength(1));
      expect(repository.cancellations.single.$2.reason, 'Đổi chỉ định');
      expect(find.textContaining('Máy chủ đã hủy đơn'), findsOneWidget);
      expect(find.text('Read unavailable'), findsOneWidget);
      expect(find.text('Hủy đơn thuốc'), findsNothing);
    });
  }
  testWidgets('PHARMACIST cannot cancel, unknown mode cannot cancel', (
    tester,
  ) async {
    for (final config in [
      ('PHARMACIST', PharmacyMode.outpatient),
      ('DOCTOR', PharmacyMode.unavailable),
    ]) {
      final repository = PharmacyStub();
      await tester.pumpWidget(
        host(
          repository,
          const PrescriptionDetailPage(prescriptionId: rxId),
          role: config.$1,
          mode: config.$2,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Hủy đơn thuốc'), findsNothing);
      expect(repository.cancellations, isEmpty);
    }
  });
  for (final change in [
    {'status': 'CANCELLED'},
    {'status': 'FULFILLED'},
    {'status': 'EXPIRED'},
    {'status': 'DISPENSE_FAILED'},
    {'dispenseStatus': 'DISPENSED'},
    {'dispenseStatus': 'FAILED'},
    {'dispenseStatus': 'CANCELLED'},
    {'dispenseStatus': 'EXPIRED'},
  ]) {
    testWidgets('terminal $change never exposes cancel command', (
      tester,
    ) async {
      final repository = PharmacyStub()
        ..prescription = (_) async =>
            PharmacyDto.prescription({...prescriptionJson(), ...change});
      await tester.pumpWidget(
        host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Hủy đơn thuốc'), findsNothing);
    });
  }
  for (final status in [403, 409, 408, 503]) {
    testWidgets('cancel $status requires read and cannot auto resend', (
      tester,
    ) async {
      final repository = PharmacyStub()
        ..cancel = (_, _) async => throw ApiFailure(
          'Backend denied',
          status: status,
          correlationId: 'cancel-trace',
        );
      await tester.pumpWidget(
        host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
      );
      await tester.pumpAndSettle();
      await cancel(tester);
      await tap(tester, 'Làm mới đơn thuốc');
      expect(repository.cancellations, hasLength(1));
      expect(repository.prescriptions, hasLength(2));
      expect(find.text('Mã hỗ trợ: cancel-trace'), findsOneWidget);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Hủy đơn thuốc'),
            )
            .onPressed,
        isNull,
      );
    });
  }
  testWidgets('changing prescription ID ignores pending cancel receipt', (
    tester,
  ) async {
    final pending = Completer<CancelPrescriptionResult>();
    final repository = PharmacyStub()..cancel = (_, _) => pending.future;
    await tester.pumpWidget(
      host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
    );
    await tester.pumpAndSettle();
    await enter(tester, 'Lý do hủy đơn', 'Reason');
    await tap(tester, 'Hủy đơn thuốc');
    await tester.tap(find.text('Xác nhận hủy'));
    await pumpCommand(tester);
    expect(repository.cancellations, hasLength(1));
    await tester.pumpWidget(
      host(
        repository,
        const PrescriptionDetailPage(prescriptionId: otherDrugId),
      ),
    );
    pending.complete(
      CancelPrescriptionResult(
        prescriptionId: rxId,
        status: PrescriptionStatus.cancelled,
        releasedReservations: 1,
        cancelledAt: DateTime.utc(2026, 10, 22),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining(otherDrugId), findsNothing);
    await visible(tester, find.text('Chi tiết kỹ thuật'));
    await tester.tap(find.text('Chi tiết kỹ thuật'));
    await tester.pumpAndSettle();
    expect(find.text('Đơn thuốc: $otherDrugId'), findsOneWidget);
    expect(find.textContaining('Máy chủ đã hủy đơn'), findsNothing);
    expect(repository.cancellations, hasLength(1));
  });
  for (final brightness in Brightness.values) {
    testWidgets(
      'existing theme at 320px / font 2 supports creation and cancel $brightness',
      (tester) async {
        tester.view.physicalSize = const Size(320, 760);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final repository = PharmacyStub();
        await tester.pumpWidget(
          host(
            repository,
            const CreatePrescriptionPage(),
            brightness: brightness,
            scale: 2,
          ),
        );
        await tester.pumpAndSettle();
        await tap(tester, 'Tìm trong danh mục');
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(
          host(
            repository,
            const PrescriptionDetailPage(prescriptionId: rxId),
            brightness: brightness,
            scale: 2,
          ),
        );
        await tester.pumpAndSettle();
        await visible(tester, find.text('Hủy đơn thuốc'));
        expect(tester.takeException(), isNull);
      },
    );
  }
}
