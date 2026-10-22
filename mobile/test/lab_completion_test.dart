import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/lab/data/gateway_lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_workflow.dart';

import 'pharmacy_fixtures.dart';

Map<String, dynamic> labJson() => {
  'testId': drugId,
  'recordId': otherDrugId,
  'patientId': otherDrugId,
  'requestingDepartmentId': otherDrugId,
  'labType': 'Công thức máu',
  'requestedDate': '2026-10-22',
  'status': 'READY',
  'paid': false,
  'careContractVersion': 1,
  'clearanceId': rxId,
  'results': [],
  'resultVersion': 0,
};
void main() {
  test(
    'Lab start and results preserve exact identity and wire contract',
    () async {
      final adapter = PharmacyHttpAdapter()..data = labJson();
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);
      final workflow = GatewayLabWorkflow(client);
      final result = await workflow.start(drugId);
      expect(result.clearanceId, rxId);
      expect(result.paid, false);
      expect(adapter.requests.last.method, 'PUT');
      expect(adapter.requests.last.data, {'emergencyOverride': null});
      await workflow.results(
        drugId,
        const [
          LabResultDraft(
            indicator: 'WBC',
            value: '7.2',
            unit: 'G/L',
            referenceRange: '4–10',
          ),
          LabResultDraft(indicator: 'RBC', value: '4.8', unit: 'T/L'),
        ],
        'Bình thường',
        '2026-10-22',
      );
      expect(adapter.requests.last.uri.path, '/api/v1/lab/$drugId/results');
      expect((adapter.requests.last.data as Map)['results'], [
        {
          'indicator': 'WBC',
          'value': '7.2',
          'unit': 'G/L',
          'referenceRange': '4–10',
        },
        {
          'indicator': 'RBC',
          'value': '4.8',
          'unit': 'T/L',
          'referenceRange': null,
        },
      ]);
      await workflow.cancel(drugId, ' Nhầm chỉ định ', otherDrugId);
      expect(adapter.requests.last.uri.path, '/api/v1/lab/$drugId/cancel');
      expect(adapter.requests.last.data, {
        'reason': 'Nhầm chỉ định',
        'cancelledBy': otherDrugId,
      });
      final before = adapter.requests.length;
      await expectLater(
        workflow.results(drugId, [], null, '2026-10-22'),
        throwsArgumentError,
      );
      await expectLater(
        workflow.cancel(drugId, '', 'bad'),
        throwsArgumentError,
      );
      expect(adapter.requests.length, before);
      adapter.data = {...labJson(), 'testId': rxId};
      await expectLater(workflow.detail(drugId), throwsFormatException);
    },
  );
  test(
    'result draft requires indicator and value and valid performed date',
    () {
      expect(
        () => const LabResultDraft(
          indicator: '',
          value: '',
        ).validate('2026-10-22'),
        throwsArgumentError,
      );
      expect(
        () => const LabResultDraft(
          indicator: 'WBC',
          value: '7',
        ).validate('2026-02-30'),
        throwsArgumentError,
      );
    },
  );
}
