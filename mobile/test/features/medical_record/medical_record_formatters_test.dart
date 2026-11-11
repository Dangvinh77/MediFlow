import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/features/medical_record/presentation/medical_record_formatters.dart';

void main() {
  test('clinical Instants are displayed in the Ho Chi Minh time zone', () {
    expect(
      formatClinicalInstant('2026-11-11T10:00:00Z'),
      '2026-11-11 17:00 (GMT+7)',
    );
  });
}
