import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';

import 'pharmacy_fixtures.dart';

void main() {
  test('drug mirrors nullable fields, date and unrounded server price', () {
    final result = PharmacyDto.drug(drugJson());
    expect(result.id, drugId);
    expect(result.price, 2500.25);
    expect(result.activeIngredient, isNull);
    expect(result.manufacturer, isNull);
    expect(result.expiryDate, '2027-06-30');
    expect(result.isLowStock, isTrue);
    expect(
      PharmacyDto.drug({...drugJson(), 'stockQuantity': 0}).isLowStock,
      isFalse,
    );
  });
  for (final key in drugJson().keys) {
    test('missing drug $key does not become a default', () {
      final json = drugJson()..remove(key);
      expect(() => PharmacyDto.drug(json), throwsFormatException);
    });
  }
  for (final entry in <String, Object?>{
    'drugId': 'not-an-id',
    'drugName': '',
    'price': '2500',
    'stockQuantity': 1.5,
    'lowStockThreshold': -1,
    'expiryDate': '2027-02-30',
    'createdAt': '2026-10-20',
    'updatedAt': 'yesterday',
    'manufacturer': 1,
  }.entries) {
    test('malformed ${entry.key} fails closed', () {
      expect(
        () => PharmacyDto.drug({...drugJson(), entry.key: entry.value}),
        throwsFormatException,
      );
    });
  }
  test('non-finite/negative monetary facts are rejected', () {
    for (final price in [double.nan, double.infinity, -1]) {
      expect(
        () => PharmacyDto.drug({...drugJson(), 'price': price}),
        throwsFormatException,
      );
    }
  });
  test('prescription keeps record nullable, false payment, enums and immutable lines', () {
    final result = PharmacyDto.prescription(prescriptionJson());
    expect(result.recordId, isNull);
    expect(result.paymentConfirmed, isFalse);
    expect(result.status, PrescriptionStatus.active);
    expect(result.dispenseStatus, DispenseStatus.pending);
    expect(result.totalAmount, 5000.50);
    expect(result.lines.single.drugName, isNull);
    expect(() => result.lines.clear(), throwsUnsupportedError);
  });
  test('all live prescription and dispense states decode without guessing', () {
    for (final status in [
      'ACTIVE',
      'FULFILLED',
      'CANCELLED',
      'EXPIRED',
      'DISPENSE_FAILED',
    ]) {
      expect(
        PharmacyDto.prescription({...prescriptionJson(), 'status': status}),
        isNotNull,
      );
    }
    for (final status in [
      'PENDING',
      'DISPENSED',
      'FAILED',
      'CANCELLED',
      'EXPIRED',
    ]) {
      expect(
        PharmacyDto.prescription({
          ...prescriptionJson(),
          'dispenseStatus': status,
        }),
        isNotNull,
      );
    }
  });
  test(
    'unknown states and invalid prescription facts cannot imply paid/active',
    () {
      for (final entry in <String, Object?>{
        'status': 'NEW',
        'dispenseStatus': 'READY',
        'paymentConfirmed': null,
        'lines': null,
        'recordId': 'bad',
        'totalAmount': -1,
      }.entries) {
        expect(
          () => PharmacyDto.prescription({
            ...prescriptionJson(),
            entry.key: entry.value,
          }),
          throwsFormatException,
        );
      }
      expect(
        () => PharmacyDto.prescription({
          ...prescriptionJson(),
          'lines': [null],
        }),
        throwsFormatException,
      );
    },
  );
  test('terminal command results preserve exact nullability and counters', () {
    final result = PharmacyDto.dispense({
      'dispenseId': drugId,
      'prescriptionId': rxId,
      'status': 'PENDING',
      'dispensedAt': null,
      'dispensedBy': null,
      'dispensedActorType': null,
      'failureReason': null,
    });
    expect(result.dispensedAt, isNull);
    expect(result.status, DispenseStatus.pending);
    final cancelled = PharmacyDto.cancellation({
      'prescriptionId': rxId,
      'status': 'CANCELLED',
      'releasedReservations': 2,
      'cancelledAt': '2026-10-20T01:00:00Z',
    });
    expect(cancelled.releasedReservations, 2);
  });
  test('use cases normalize search and reject invalid identifiers/pages before repository calls', () async {
    final repository = PharmacyStub();
    await FindDrugs(repository)(keyword: '  vitamin C  ', page: 1, size: 20);
    await FindDrugs(repository)(keyword: '   ');
    expect(repository.searches.first.keyword, 'vitamin C');
    expect(repository.searches.last.keyword, isNull);
    expect(() => FindDrugs(repository)(page: -1), throwsArgumentError);
    expect(() => FindDrugs(repository)(size: 101), throwsArgumentError);
    expect(() => ReadDrug(repository)('../x'), throwsArgumentError);
    expect(
      () => ReadPrescription(repository)('abbreviated'),
      throwsArgumentError,
    );
    expect(repository.details, isEmpty);
  });
}
