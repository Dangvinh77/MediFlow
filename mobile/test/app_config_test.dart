import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';

void main() {
  test('scheduled billing UI flags default to disabled', () {
    final config = AppConfig('http://localhost:8080/api');

    expect(config.scheduledPaymentsEnabled, isFalse);
    expect(config.outpatientQuotesEnabled, isFalse);
    expect(config.zeroPayableEnabled, isFalse);
  });

  test('billing flags mirror web Dart define names and nested zero gate', () {
    const scheduling = bool.fromEnvironment('MEDIFLOW_SCHEDULING_UI_ENABLED');
    const quotes = bool.fromEnvironment(
      'MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED',
    );
    const zeroPayable = bool.fromEnvironment(
      'MEDIFLOW_ZERO_PAYABLE_UI_ENABLED',
    );
    final config = AppConfig.environment();

    expect(config.scheduledPaymentsEnabled, scheduling);
    expect(config.outpatientQuotesEnabled, quotes);
    expect(config.zeroPayableEnabled, scheduling && quotes && zeroPayable);
  });
}
