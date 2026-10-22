import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/prescription_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_widget_test.dart' show tap, pumpCommand, visible;

Map<String, dynamic> slip({String status = 'DISPENSED'}) => {
  'dispenseId': otherDrugId,
  'prescriptionId': rxId,
  'status': status,
  'dispensedAt': status == 'DISPENSED' ? '2026-10-22T01:00:00Z' : null,
  'dispensedBy': status == 'DISPENSED' ? drugId : null,
  'dispensedActorType': status == 'DISPENSED' ? 'STAFF' : null,
  'failureReason': status == 'FAILED' ? 'DRUG_OUT_OF_STOCK' : null,
};

class Dispenser implements PrescriptionDispenser {
  final calls = <String>[];
  Future<DispenseResult> Function(String)? send;
  @override
  Future<DispenseResult> dispensePrescription(String id) {
    calls.add(id);
    return send?.call(id) ?? Future.value(PharmacyDto.dispense(slip()));
  }
}

Widget host(
  PharmacyStub repository, {
  Dispenser? dispenser,
  String role = 'PHARMACIST',
  Object identity = 'one',
  PharmacyMode mode = PharmacyMode.outpatient,
  Brightness brightness = Brightness.light,
  double scale = 1,
}) => ProviderScope(
  overrides: [
    pharmacyRepositoryProvider.overrideWithValue(repository),
    pharmacyAccessProvider.overrideWithValue(
      PharmacyAccess(role: role, sessionIdentity: identity),
    ),
    pharmacyModeProvider.overrideWithValue(mode),
    prescriptionDispenserProvider.overrideWithValue(dispenser),
  ],
  child: MaterialApp(
    theme: ThemeData(useMaterial3: true, brightness: brightness),
    builder: (_, child) => MediaQuery(
      data: MediaQueryData(textScaler: TextScaler.linear(scale)),
      child: child!,
    ),
    home: const Scaffold(body: PrescriptionDetailPage(prescriptionId: rxId)),
  ),
);

Future<void> dispense(WidgetTester tester) async {
  await tap(tester, 'Xuất thuốc');
  await tap(tester, 'Xác nhận xuất');
}

void main() {
  test('V1 ignores legacy boolean; V0 requires it; unknown mode denies', () {
    final rx = PharmacyDto.prescription(prescriptionJson());
    expect(canDispensePrescription(rx, PharmacyMode.outpatient), isTrue);
    expect(canDispensePrescription(rx, PharmacyMode.legacy), isFalse);
    expect(canDispensePrescription(rx, PharmacyMode.unavailable), isFalse);
    final paid = PharmacyDto.prescription(
      prescriptionJson()..['paymentConfirmed'] = true,
    );
    expect(canDispensePrescription(paid, PharmacyMode.legacy), isTrue);
  });
  for (final actor in ['STAFF', 'ACCOUNT', 'SYSTEM', 'LEGACY_UNKNOWN', null]) {
    test('actor wire value $actor is decoded without inference', () {
      final result = PharmacyDto.dispense(
        slip()..['dispensedActorType'] = actor,
      );
      expect(result.dispensedActorType == null, actor == null);
    });
  }
  test('actor field must be present and known', () {
    expect(
      () => PharmacyDto.dispense(slip()..remove('dispensedActorType')),
      throwsFormatException,
    );
    expect(
      () => PharmacyDto.dispense(slip()..['dispensedActorType'] = 'DOCTOR'),
      throwsFormatException,
    );
  });
  for (final change in <String, Object?>{
    'prescriptionId': drugId,
    'dispensedAt': null,
    'dispensedBy': null,
    'dispensedActorType': null,
    'status': 'PENDING',
  }.entries) {
    test('command rejects ambiguous receipt ${change.key}', () async {
      final dispenser = Dispenser()
        ..send = (_) async =>
            PharmacyDto.dispense(slip()..[change.key] = change.value);
      await expectLater(
        DispensePrescription(dispenser)(
          PharmacyDto.prescription(prescriptionJson()),
          PharmacyMode.outpatient,
        ),
        throwsFormatException,
      );
      expect(dispenser.calls, [rxId]);
    });
  }
  test('server FAILED receipt is preserved as failure', () async {
    final dispenser = Dispenser()
      ..send = (_) async => PharmacyDto.dispense(slip(status: 'FAILED'));
    final result = await DispensePrescription(dispenser)(
      PharmacyDto.prescription(prescriptionJson()),
      PharmacyMode.outpatient,
    );
    expect(result.status, DispenseStatus.failed);
    expect(result.failureReason, 'DRUG_OUT_OF_STOCK');
  });
  testWidgets('missing shared capability keeps command disabled', (
    tester,
  ) async {
    await tester.pumpWidget(host(PharmacyStub()));
    await tester.pumpAndSettle();
    expect(find.textContaining('PUT không body'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(find.widgetWithText(FilledButton, 'Xuất thuốc'))
          .onPressed,
      isNull,
    );
  });
  for (final role in ['ADMIN', 'PHARMACIST']) {
    testWidgets(
      '$role can confirm V1 with legacy boolean false; receipt survives read failure',
      (tester) async {
        final repository = PharmacyStub();
        final dispenser = Dispenser()
          ..send = (_) async {
            repository.prescription = (_) async =>
                throw const ApiFailure('Refresh unavailable', status: 503);
            return PharmacyDto.dispense(slip());
          };
        await tester.pumpWidget(
          host(repository, dispenser: dispenser, role: role),
        );
        await tester.pumpAndSettle();
        await dispense(tester);
        expect(dispenser.calls, [rxId]);
        expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsOneWidget);
        expect(find.textContaining(otherDrugId), findsNothing);
        expect(find.textContaining('Refresh unavailable'), findsWidgets);
        expect(find.text('Xuất thuốc'), findsNothing);
        expect(repository.cancellations, isEmpty);
      },
    );
  }
  for (final role in ['DOCTOR', 'PATIENT', 'MANAGER']) {
    testWidgets('$role has no dispense action', (tester) async {
      final dispenser = Dispenser();
      await tester.pumpWidget(
        host(PharmacyStub(), dispenser: dispenser, role: role),
      );
      await tester.pumpAndSettle();
      expect(find.text('Xuất thuốc'), findsNothing);
      expect(dispenser.calls, isEmpty);
    });
  }
  for (final state in [
    'CANCELLED',
    'FULFILLED',
    'EXPIRED',
    'DISPENSE_FAILED',
  ]) {
    testWidgets('terminal prescription $state cannot dispense', (tester) async {
      final repository = PharmacyStub()
        ..prescription = (_) async =>
            PharmacyDto.prescription(prescriptionJson()..['status'] = state);
      await tester.pumpWidget(host(repository, dispenser: Dispenser()));
      await tester.pumpAndSettle();
      expect(find.text('Xuất thuốc'), findsNothing);
    });
  }
  for (final mode in [PharmacyMode.legacy, PharmacyMode.unavailable]) {
    testWidgets('$mode does not infer V1 from missing metadata', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(PharmacyStub(), dispenser: Dispenser(), mode: mode),
      );
      await tester.pumpAndSettle();
      expect(find.text('Xuất thuốc'), findsNothing);
    });
  }
  testWidgets('explicit paid V0 retains compatibility command', (tester) async {
    final repository = PharmacyStub()
      ..prescription = (_) async => PharmacyDto.prescription(
        prescriptionJson()..['paymentConfirmed'] = true,
      );
    final dispenser = Dispenser();
    await tester.pumpWidget(
      host(repository, dispenser: dispenser, mode: PharmacyMode.legacy),
    );
    await tester.pumpAndSettle();
    await dispense(tester);
    expect(dispenser.calls, [rxId]);
    expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsOneWidget);
  });
  for (final state in ['DISPENSED', 'FAILED', 'CANCELLED', 'EXPIRED']) {
    testWidgets('terminal dispense $state blocks active order command', (
      tester,
    ) async {
      final repository = PharmacyStub()
        ..prescription = (_) async => PharmacyDto.prescription(
          prescriptionJson()..['dispenseStatus'] = state,
        );
      await tester.pumpWidget(host(repository, dispenser: Dispenser()));
      await tester.pumpAndSettle();
      expect(find.text('Xuất thuốc'), findsNothing);
    });
  }
  for (final reason in ['Unpaid', 'Clearance expired', 'Clearance revoked']) {
    testWidgets('$reason preserves backend denial and correlation', (
      tester,
    ) async {
      final dispenser = Dispenser()
        ..send = (_) async => throw ApiFailure(
          reason,
          status: 422,
          code: 'PHARMACY_CLEARANCE_REQUIRED',
          correlationId: 'clearance-trace',
        );
      await tester.pumpWidget(host(PharmacyStub(), dispenser: dispenser));
      await tester.pumpAndSettle();
      await dispense(tester);
      expect(find.text(reason), findsOneWidget);
      expect(find.textContaining('clearance-trace'), findsOneWidget);
      expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsNothing);
      expect(dispenser.calls, [rxId]);
    });
  }
  for (final status in [null, 401, 403, 408, 409, 503]) {
    testWidgets('uncertain or forbidden $status never replays after refresh', (
      tester,
    ) async {
      final repository = PharmacyStub();
      final dispenser = Dispenser()
        ..send = (_) async => throw ApiFailure(
          'Clearance unavailable',
          status: status,
          correlationId: 'dispense-trace',
        );
      await tester.pumpWidget(host(repository, dispenser: dispenser));
      await tester.pumpAndSettle();
      await dispense(tester);
      await tap(tester, 'Làm mới đơn thuốc');
      expect(dispenser.calls, [rxId]);
      expect(repository.prescriptions.length, 2);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Xuất thuốc'),
            )
            .onPressed,
        isNull,
      );
      expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsNothing);
    });
  }
  testWidgets(
    'known unpaid rejection needs fresh GET and explicit new confirmation',
    (tester) async {
      final repository = PharmacyStub();
      final dispenser = Dispenser()
        ..send = (_) async => throw const ApiFailure(
          'Unpaid clearance',
          status: 422,
          code: 'PHARMACY_CLEARANCE_REQUIRED',
        );
      await tester.pumpWidget(host(repository, dispenser: dispenser));
      await tester.pumpAndSettle();
      await dispense(tester);
      await visible(tester, find.widgetWithText(FilledButton, 'Xuất thuốc'));
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Xuất thuốc'),
            )
            .onPressed,
        isNull,
      );
      await tap(tester, 'Làm mới đơn thuốc');
      expect(dispenser.calls, [rxId]);
      await dispense(tester);
      expect(dispenser.calls, [rxId, rxId]);
      expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsNothing);
    },
  );
  testWidgets('session change closes confirmation without a command', (
    tester,
  ) async {
    final repository = PharmacyStub();
    final dispenser = Dispenser();
    await tester.pumpWidget(host(repository, dispenser: dispenser));
    await tester.pumpAndSettle();
    await tap(tester, 'Xuất thuốc');
    await tester.pumpWidget(
      host(repository, dispenser: dispenser, identity: 'two'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Xác nhận xuất'), findsNothing);
    expect(dispenser.calls, isEmpty);
  });
  testWidgets(
    'in-flight dispense blocks duplicate and cancellation; logout drops late result',
    (tester) async {
      final pending = Completer<DispenseResult>();
      final repository = PharmacyStub();
      final dispenser = Dispenser()..send = (_) => pending.future;
      await tester.pumpWidget(
        host(repository, dispenser: dispenser, role: 'ADMIN'),
      );
      await tester.pumpAndSettle();
      await tap(tester, 'Xuất thuốc');
      await tester.tap(find.text('Xác nhận xuất'));
      await pumpCommand(tester);
      expect(dispenser.calls, [rxId]);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Đang xử lý xuất'),
            )
            .onPressed,
        isNull,
      );
      await tester.pumpWidget(
        host(repository, dispenser: dispenser, role: 'DOCTOR', identity: 'two'),
      );
      await tester.pumpAndSettle();
      pending.complete(PharmacyDto.dispense(slip()));
      await tester.pumpAndSettle();
      expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsNothing);
      expect(repository.cancellations, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );
  for (final brightness in Brightness.values) {
    testWidgets(
      '320px font 2 $brightness shows failed receipt without overflow',
      (tester) async {
        tester.view.physicalSize = const Size(320, 720);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final dispenser = Dispenser()
          ..send = (_) async => PharmacyDto.dispense(slip(status: 'FAILED'));
        await tester.pumpWidget(
          host(
            PharmacyStub(),
            dispenser: dispenser,
            brightness: brightness,
            scale: 2,
          ),
        );
        await tester.pumpAndSettle();
        await dispense(tester);
        expect(
          find.textContaining('Máy chủ ghi nhận xuất thất bại'),
          findsOneWidget,
        );
        expect(find.text('Máy chủ xác nhận đã xuất thuốc.'), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
