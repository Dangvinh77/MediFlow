import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/pharmacy/data/prescription_request_dto.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/dosage_schedule.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';

const _id = '00000000-0000-4000-8000-000000000001';

DosageSchedule _morningEvening([String days = '']) =>
    DosageSchedule.empty().withPreset(['Sáng', 'Tối']).copyWith(days: days);

DosageSchedule _withFirst(
  DosageSchedule schedule,
  DoseRow Function(DoseRow) f,
) =>
    schedule.copyWith(rows: [f(schedule.rows.first), ...schedule.rows.skip(1)]);

CreateOutpatientPrescription _command(RequestedPrescriptionLine line) =>
    CreateOutpatientPrescription(
      context: const OutpatientPrescriptionContext(
        recordId: _id,
        patientId: _id,
        doctorId: _id,
        departmentId: _id,
        careEpisodeId: _id,
        priceCode: 'CODE',
        sourceReference: 'visit',
      ),
      prescribedDate: '2024-02-29',
      lines: [line],
    );

void main() {
  test('writes one phrase per selected session and the course length', () {
    expect(
      _morningEvening('7').format('Viên'),
      'Sáng 1 viên sau ăn; Tối 1 viên sau ăn (7 ngày)',
    );
  });

  test('keeps a separate timing and dose per session', () {
    final schedule = _withFirst(
      _morningEvening(),
      (row) => row.copyWith(dose: 0.5, timing: 'Trước ăn'),
    );
    expect(
      schedule.format('viên'),
      'Sáng 1/2 viên trước ăn; Tối 1 viên sau ăn',
    );
    final oneAndHalf = _withFirst(
      _morningEvening(),
      (row) => row.copyWith(dose: 1.5),
    );
    expect(oneAndHalf.format('viên'), startsWith('Sáng 1,5 viên'));
  });

  test('requires a session, a valid dose step and a valid day count', () {
    expect(DosageSchedule.empty().validate(), contains('ít nhất một buổi'));
    expect(
      _withFirst(
        _morningEvening(),
        (row) => row.copyWith(dose: 0.3),
      ).validate(),
      contains('Liều mỗi buổi'),
    );
    expect(_morningEvening('0').validate(), contains('Số ngày'));
    expect(_morningEvening('7').validate(), isNull);
  });

  test('suggests dose × days rounded up, only for per-dose units', () {
    expect(_morningEvening('7').suggestedQuantity('Viên'), 14);
    expect(
      _withFirst(
        _morningEvening('3'),
        (row) => row.copyWith(dose: 0.5),
      ).suggestedQuantity('viên'),
      5,
    );
    expect(_morningEvening('7').suggestedQuantity('Chai'), isNull);
    expect(_morningEvening().suggestedQuantity('viên'), isNull);
    expect(
      DosageSchedule.empty()
          .withPreset([asNeededSession])
          .copyWith(days: '5')
          .suggestedQuantity('viên'),
      isNull,
    );
  });

  test('a line needs a dosage and bounds its note', () {
    expect(
      _command(const RequestedPrescriptionLine(drugId: _id, quantity: 1))
          .errors(),
      contains('lines.0.dosage'),
    );
    expect(
      _command(
        RequestedPrescriptionLine(
          drugId: _id,
          quantity: 1,
          dosage: 'Sau ăn',
          note: 'a' * 501,
        ),
      ).errors(),
      contains('lines.0.note'),
    );
  });

  test('the wire line carries the note only when present', () {
    List<Object?> lines(RequestedPrescriptionLine line) =>
        PrescriptionRequestDto.outpatient(
              PrescriptionIntent(_command(line)),
            )['lines']!
            as List<Object?>;
    expect(
      lines(
        const RequestedPrescriptionLine(
          drugId: _id,
          quantity: 1,
          dosage: 'Sau ăn',
          note: 'Uống đủ liệu trình',
        ),
      ),
      [
        {
          'drugId': _id,
          'quantity': 1,
          'dosage': 'Sau ăn',
          'note': 'Uống đủ liệu trình',
        },
      ],
    );
    expect(
      lines(
        const RequestedPrescriptionLine(
          drugId: _id,
          quantity: 1,
          dosage: 'Sau ăn',
        ),
      ),
      [
        {'drugId': _id, 'quantity': 1, 'dosage': 'Sau ăn'},
      ],
    );
  });
}
