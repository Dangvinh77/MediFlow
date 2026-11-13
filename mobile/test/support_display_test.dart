import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/notification/presentation/notification_display.dart';
import 'package:mediflow_mobile/features/report/domain/report.dart';
import 'package:mediflow_mobile/features/report/presentation/report_page.dart';

void main() {
  test('notification text groups raw stored amounts only', () {
    expect(
      notificationText('Đã ghi nhận khoản thanh toán 36000 VND.'),
      'Đã ghi nhận khoản thanh toán 36.000 VND.',
    );
    expect(
      notificationText('Hoàn tiền 150000.00 VND'),
      'Hoàn tiền 150.000 VND',
    );
    expect(notificationText('Tổng 1250500.50 VNĐ'), 'Tổng 1.250.500,5 VNĐ');
    expect(
      notificationText('Đã thanh toán 36.000 VND, phí 950 VND, năm 2026'),
      'Đã thanh toán 36.000 VND, phí 950 VND, năm 2026',
    );
  });

  test('notification labels use patient-facing terms', () {
    expect(notificationChannelLabel('IN_APP'), 'Trong ứng dụng');
    expect(notificationStatusLabel('PENDING'), 'Đang chờ gửi');
    expect(notificationStatusLabel('SENT'), 'Đã gửi');
    expect(notificationStatusLabel('FAILED'), 'Gửi thất bại');
    expect(notificationTimeLabel('invalid'), 'Chưa rõ thời gian');
    expect(
      notificationTimeLabel('2026-10-21T10:00:00Z'),
      contains('21/10/2026'),
    );
  });

  test('outpatient report choices follow the mobile feature gate', () {
    expect(
      availableReportKinds(outpatientEnabled: false),
      isNot(contains(ReportKind.outpatientDaily)),
    );
    expect(
      availableReportKinds(outpatientEnabled: true),
      containsAll([ReportKind.outpatientDaily, ReportKind.outpatientMonthly]),
    );
  });

  test('missing accepted report coverage has a readable message', () {
    final error = reportDisplayError(
      const ApiFailure(
        'Requested projection does not have accepted coverage',
        status: 404,
        code: 'REPORT_NOT_FOUND',
        correlationId: 'request-1',
      ),
      ReportKind.outpatientDaily,
    ) as ApiFailure;
    expect(error.message, contains('Chưa có dữ liệu'));
    expect(error.correlationId, 'request-1');
  });
}
