import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/appointment/data/appointment_dto.dart';
import 'package:mediflow_mobile/features/lab/data/lab_test_dto.dart';
import 'package:mediflow_mobile/app/modules.dart';
import 'package:mediflow_mobile/features/auth/domain/session.dart';

void main() {
  test(
    'appointment preserves exact source identities and financial lifecycle',
    () {
      final item = AppointmentDto.fromJson({
        'appointmentId': 'a',
        'patientId': 'p',
        'doctorId': 'd',
        'departmentId': 'k',
        'appointmentDate': '2026-10-17',
        'appointmentTime': '09:00',
        'status': 'AWAITING_PAYMENT',
        'reason': null,
      });
      expect(item.id, 'a');
      expect(item.status, 'AWAITING_PAYMENT');
      expect(item.reason, isNull);
    },
  );
  test('Lab lifecycle and paid remain independent', () {
    final item = LabTestDto.fromJson({
      'testId': 't',
      'patientId': 'p',
      'recordId': 'r',
      'labType': 'CBC',
      'requestedDate': '2026-10-17',
      'status': 'AWAITING_PAYMENT',
      'paid': true,
    });
    expect(item.status, 'AWAITING_PAYMENT');
    expect(item.paid, isTrue);
  });
  test('malformed DTO fails rather than guessing required identities', () {
    expect(
      () => AppointmentDto.fromJson({'appointmentId': 'a'}),
      throwsFormatException,
    );
    expect(() => LabTestDto.fromJson({'testId': 't'}), throwsFormatException);
  });
  test('menu allows only each route role and records four owners', () {
    expect(
      modules.where((m) => m.allows(UserRole.cashier)).map((m) => m.path),
      ['/billing'],
    );
    expect(
      modules.where((m) => m.allows(UserRole.labTech)).map((m) => m.path),
      ['/lab'],
    );
    expect(modules.map((m) => m.owner).toSet(), {
      'Vinh',
      'Huy',
      'Hoàng Anh',
      'Lộc',
    });
    expect(
      modules.any((m) => m.path == '/inpatient' || m.path == '/surgery'),
      isFalse,
    );
  });
}
