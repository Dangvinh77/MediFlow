import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/lab/data/gateway_lab_workflow.dart';
import 'package:mediflow_mobile/features/lab/domain/lab_catalog.dart';

import '../../pharmacy_fixtures.dart';

void main() {
  test(
    'catalogue parses the service wire fields and optional group query',
    () async {
      final adapter = PharmacyHttpAdapter()
        ..data = [
          {
            'serviceCode': 'CBC',
            'name': 'Công thức máu',
            'displayGroup': 'HEMATOLOGY',
            'specimenType': 'Máu toàn phần',
            'preparation': 'Nhịn ăn 8 giờ',
            'turnaroundHours': 4,
            'priceCode': 'PRICE-CBC',
            'templateCode': 'CBC_V1',
            'panel': true,
          },
        ];
      final client = ApiClient(
        AppConfig('http://localhost:8080/api'),
        dio: Dio()..httpClientAdapter = adapter,
      );
      addTearDown(client.close);

      final catalog = await GatewayLabWorkflow(client)
          .catalog(group: 'HEMATOLOGY');

      expect(catalog.enabled, isTrue);
      expect(catalog.services, hasLength(1));
      expect(catalog.services.single, isA<LabCatalogService>());
      expect(catalog.services.single.serviceCode, 'CBC');
      expect(catalog.services.single.panel, isTrue);
      expect(adapter.requests.single.uri.path, '/api/v2/lab/catalog');
      expect(adapter.requests.single.uri.queryParameters, {
        'group': 'HEMATOLOGY',
      });
    },
  );

  test('catalogue feature-disabled 404 is represented as disabled', () async {
    final adapter = PharmacyHttpAdapter()
      ..status = 404
      ..data = {
        'error': {
          'code': 'LAB_CATALOG_DISABLED',
          'message': 'Danh mục xét nghiệm chưa được bật.',
        },
      };
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    addTearDown(client.close);

    final catalog = await GatewayLabWorkflow(client).catalog();

    expect(catalog.enabled, isFalse);
    expect(catalog.services, isEmpty);
  });

  test('other catalogue failures propagate to the caller', () async {
    final adapter = PharmacyHttpAdapter()
      ..status = 503
      ..data = {
        'error': {'code': 'LAB_UNAVAILABLE', 'message': 'Unavailable'},
      };
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    addTearDown(client.close);

    await expectLater(
      GatewayLabWorkflow(client).catalog(),
      throwsA(isA<Exception>()),
    );
  });

  test('template parser mirrors field and range wire names', () async {
    final adapter = PharmacyHttpAdapter()
      ..data = {
        'testId': rxId,
        'serviceCode': 'CBC',
        'template': {
          'templateCode': 'CBC_V1',
          'version': 1,
          'name': 'Công thức máu',
          'fields': [
            {
              'fieldCode': 'WBC',
              'label': 'Bạch cầu',
              'position': 2,
              'required': true,
              'valueType': 'QUANTITATIVE',
              'unit': 'G/L',
              'allowedOptions': null,
              'allowsOperator': true,
              'ranges': [
                {
                  'sex': null,
                  'ageMinDays': null,
                  'ageMaxDays': null,
                  'low': 4,
                  'high': 10,
                  'criticalLow': null,
                  'criticalHigh': null,
                  'reviewed': false,
                },
              ],
            },
          ],
        },
      };
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    addTearDown(client.close);

    final template = await GatewayLabWorkflow(client).template(rxId);

    expect(template.testId, rxId);
    expect(template.serviceCode, 'CBC');
    expect(template.template!.fields.single.fieldCode, 'WBC');
    expect(template.template!.fields.single.ranges.single.low, '4');
    expect(adapter.requests.single.uri.path, '/api/v2/lab/$rxId/template');
  });

  test('template null and legacy-test 404 both return no template', () async {
    final adapter = PharmacyHttpAdapter()
      ..data = {'testId': rxId, 'serviceCode': null, 'template': null};
    final client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    addTearDown(client.close);
    final workflow = GatewayLabWorkflow(client);

    expect((await workflow.template(rxId)).template, isNull);

    adapter
      ..status = 404
      ..data = {
        'error': {'code': 'LAB_TEMPLATE_NOT_FOUND'},
      };
    expect((await workflow.template(rxId)).template, isNull);
  });
}
