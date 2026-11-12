import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/data/prescription_request_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/edit_prescription_page.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/providers.dart';
import 'package:mediflow_mobile/features/pharmacy/presentation/prescription_page.dart';

import 'pharmacy_fixtures.dart';
import 'pharmacy_prescription_widget_test.dart' show host, tap, visible;

Prescription snapshot({int? revision = 3, bool paid = false}) {
  final json = prescriptionJson()
    ..['revision'] = revision
    ..['paymentConfirmed'] = paid;
  (json['lines'] as List).first['dosage'] = 'Uống sau ăn';
  return PharmacyDto.prescription(json);
}

class EditorRepository extends PharmacyStub implements PrescriptionEditor {
  final intents = <PrescriptionEditIntent>[];
  Future<Prescription> Function(PrescriptionEditIntent)? send;
  @override
  Future<Prescription> updatePrescription(
    String id,
    PrescriptionEditIntent intent,
  ) {
    intents.add(intent);
    return send?.call(intent) ?? Future.value(snapshot(revision: 4));
  }
}

void main() {
  test(
    'only unpaid active outpatient snapshots with revision are editable',
    () {
      expect(canEditPrescription(snapshot(), PharmacyMode.outpatient), isTrue);
      expect(
        canEditPrescription(snapshot(paid: true), PharmacyMode.outpatient),
        isFalse,
      );
      expect(
        canEditPrescription(snapshot(revision: null), PharmacyMode.outpatient),
        isFalse,
      );
      expect(canEditPrescription(snapshot(), PharmacyMode.legacy), isFalse);
    },
  );
  test('edit payload contains only revision and editable lines', () {
    final intent = PrescriptionEditIntent(
      UpdatePrescription(
        expectedRevision: 3,
        lines: [
          const RequestedPrescriptionLine(
            drugId: drugId,
            quantity: 2,
            dosage: 'Sau ăn',
          ),
        ],
      ),
    );
    expect(PrescriptionRequestDto.update(intent), {
      'expectedRevision': 3,
      'lines': [
        {'drugId': drugId, 'quantity': 2, 'dosage': 'Sau ăn'},
      ],
    });
    expect(
      intent.key,
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
    expect(
      () => UpdatePrescription(expectedRevision: 3, lines: []).validate(),
      throwsException,
    );
  });
  testWidgets('unknown outcome freezes draft and retries exact same intent', (
    tester,
  ) async {
    final repository = EditorRepository();
    repository.send = (_) => Future.error(const ApiFailure('Mất kết nối'));
    await tester.pumpWidget(
      host(
        repository,
        EditPrescriptionPage(
          prescription: snapshot(),
          repository: repository,
          sessionIdentity: 'one',
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tap(tester, 'Lưu đơn thuốc');
    expect(repository.intents, hasLength(1));
    final frozen = repository.intents.single;
    expect(tester.widget<PopScope>(find.byType(PopScope).last).canPop, isFalse);
    final pending = Completer<Prescription>();
    repository.send = (_) => pending.future;
    await visible(tester, find.text('Gửi lại đúng lần sửa'));
    await tester.tap(find.text('Gửi lại đúng lần sửa'));
    await tester.pump();
    expect(repository.intents, hasLength(2));
    expect(identical(repository.intents.last, frozen), isTrue);
    expect(
      PrescriptionRequestDto.update(repository.intents.last),
      PrescriptionRequestDto.update(frozen),
    );
    await tester.pumpWidget(const SizedBox());
    pending.complete(snapshot(revision: 4));
    await tester.pump();
  });
  testWidgets('payment conflict blocks further edits until detail refresh', (
    tester,
  ) async {
    final repository = EditorRepository()
      ..send = (_) => Future.error(
        const ApiFailure(
          'Đơn đã thanh toán',
          status: 422,
          code: 'BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS',
        ),
      );
    await tester.pumpWidget(
      host(
        repository,
        EditPrescriptionPage(
          prescription: snapshot(),
          repository: repository,
          sessionIdentity: 'one',
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tap(tester, 'Lưu đơn thuốc');
    expect(repository.intents, hasLength(1));
    await visible(tester, find.text('Lưu đơn thuốc'));
    final save = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Lưu đơn thuốc'),
    );
    expect(save.onPressed, isNull);
    await tester.drag(find.byType(ListView), const Offset(0, 2000));
    await tester.pumpAndSettle();
    expect(find.text('Làm mới đơn thuốc'), findsOneWidget);
    expect(tester.widget<PopScope>(find.byType(PopScope).last).canPop, isTrue);
  });
  testWidgets('old session cannot expose draft or issue mutation', (
    tester,
  ) async {
    final repository = EditorRepository();
    await tester.pumpWidget(
      host(
        repository,
        EditPrescriptionPage(
          prescription: snapshot(),
          repository: repository,
          sessionIdentity: 'old',
        ),
        identity: 'new',
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(TextFormField), findsNothing);
    expect(repository.intents, isEmpty);
  });
  testWidgets(
    'detail opens editor and returns to refreshed authoritative detail',
    (tester) async {
      final repository = EditorRepository();
      repository.prescription = (_) async =>
          snapshot(revision: repository.intents.isEmpty ? 3 : 4);
      await tester.pumpWidget(
        host(repository, const PrescriptionDetailPage(prescriptionId: rxId)),
      );
      await tester.pumpAndSettle();
      await tap(tester, 'Sửa đơn thuốc');
      expect(find.byType(EditPrescriptionPage), findsOneWidget);
      await tap(tester, 'Lưu đơn thuốc');
      expect(repository.intents, hasLength(1));
      expect(find.byType(EditPrescriptionPage), findsNothing);
      expect(repository.prescriptions, hasLength(2));
      expect(
        find.text('Đã cập nhật đơn thuốc. Khoản phải thu đang được đồng bộ.'),
        findsOneWidget,
      );
    },
  );
  for (final (name, paid, revision, mode, role) in [
    ('paid', true, 3, PharmacyMode.outpatient, 'DOCTOR'),
    ('old response', false, null, PharmacyMode.outpatient, 'DOCTOR'),
    ('legacy', false, 3, PharmacyMode.legacy, 'DOCTOR'),
    ('pharmacist', false, 3, PharmacyMode.outpatient, 'PHARMACIST'),
  ]) {
    testWidgets('$name detail has no edit action', (tester) async {
      final repository = EditorRepository()
        ..prescription = (_) async => snapshot(paid: paid, revision: revision);
      await tester.pumpWidget(
        host(
          repository,
          const PrescriptionDetailPage(prescriptionId: rxId),
          role: role,
          mode: mode,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Sửa đơn thuốc'), findsNothing);
    });
  }
  testWidgets('editor supports narrow screen and large text without overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = EditorRepository();
    await tester.pumpWidget(
      host(
        repository,
        EditPrescriptionPage(
          prescription: snapshot(),
          repository: repository,
          sessionIdentity: 'one',
        ),
        scale: 2,
        brightness: Brightness.dark,
      ),
    );
    await tester.pumpAndSettle();
    await visible(tester, find.text('Lưu đơn thuốc'));
    expect(tester.takeException(), isNull);
  });
}
