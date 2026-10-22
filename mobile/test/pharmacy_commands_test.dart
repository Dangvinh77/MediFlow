import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/core/network/api_failure.dart';
import 'package:mediflow_mobile/features/pharmacy/data/gateway_pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug_commands.dart';

import 'pharmacy_fixtures.dart';

CreateDrug command({
  String name = 'Thuốc A',
  String unit = 'Viên',
  String price = '2500.25',
  int stock = 0,
  int threshold = 10,
  String expiry = '2099-12-31',
  String? ingredient,
  String? manufacturer,
}) => CreateDrug(
  drugName: name,
  unit: unit,
  price: price,
  stockQuantity: stock,
  expiryDate: expiry,
  lowStockThreshold: threshold,
  activeIngredient: ingredient,
  manufacturer: manufacturer,
);

void main() {
  test('create mirrors text, decimal and Java Integer limits', () {
    expect(
      command(
        name: 'a' * 150,
        unit: 'v' * 20,
        price: '9999999999999.99',
        stock: 2147483647,
      ).errors(),
      isEmpty,
    );
    for (final invalid in [
      command(name: ''),
      command(name: 'a' * 151),
      command(unit: 'v' * 21),
      command(price: '-1'),
      command(price: '1.001'),
      command(price: '10000000000000'),
      command(price: 'NaN'),
      command(stock: -1),
      command(stock: 2147483648),
      command(threshold: -1),
      command(ingredient: 'a' * 151),
      command(manufacturer: 'a' * 151),
      command(expiry: '2099-02-30'),
    ]) {
      expect(invalid.errors(), isNotEmpty);
    }
  });
  test(
    'expiry today is accepted and yesterday is rejected without normalization',
    () {
      expect(
        command(expiry: '2026-10-22').errors(today: DateTime(2026, 10, 22)),
        isEmpty,
      );
      expect(
        command(expiry: '2026-10-21').errors(today: DateTime(2026, 10, 22)),
        contains('expiryDate'),
      );
    },
  );
  test(
    'stock delta permits either sign but forbids zero/overflow/long reason',
    () {
      for (final delta in [-2147483648, -2, 1, 2147483647]) {
        expect(AdjustDrugStock(quantity: delta).errors(), isEmpty);
      }
      for (final delta in [0, -2147483649, 2147483648]) {
        expect(AdjustDrugStock(quantity: delta).errors(), contains('quantity'));
      }
      expect(
        AdjustDrugStock(quantity: 1, reason: 'a' * 256).errors(),
        contains('reason'),
      );
      expect(AdjustDrugStock(quantity: 1, reason: 'a' * 255).errors(), isEmpty);
    },
  );
  late PharmacyHttpAdapter adapter;
  late ApiClient client;
  late GatewayPharmacyRepository repository;
  setUp(() {
    adapter = PharmacyHttpAdapter()..data = drugJson();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
    );
    repository = GatewayPharmacyRepository(client);
  });
  tearDown(() => client.close());
  test('POST exact DTO keeps server-created ID and never sends a client price total or key', () async {
    final result = await repository.createDrug(command());
    final request = adapter.requests.single;
    expect(request.method, 'POST');
    expect(request.uri.path, '/api/v1/pharmacy/drugs');
    expect(request.data, {
      'drugName': 'Thuốc A',
      'unit': 'Viên',
      'price': 2500.25,
      'stockQuantity': 0,
      'expiryDate': '2099-12-31',
      'lowStockThreshold': 10,
      'activeIngredient': null,
      'manufacturer': null,
    });
    expect(request.headers.containsKey('Idempotency-Key'), isFalse);
    expect(result.id, drugId);
  });
  test('PUT is a delta and server stock is preserved rather than calculated locally', () async {
    final result = await repository.adjustStock(
      drugId,
      const AdjustDrugStock(quantity: -2, reason: 'Kiểm kê'),
    );
    final request = adapter.requests.single;
    expect(request.method, 'PUT');
    expect(request.uri.path, '/api/v1/pharmacy/drugs/$drugId/stock');
    expect(request.data, {'quantity': -2, 'reason': 'Kiểm kê'});
    expect(result.stockQuantity, 6);
  });
  test('invalid commands cannot reach HTTP and mismatched stock identity fails closed', () async {
    await expectLater(
      repository.createDrug(command(price: '1.001')),
      throwsA(isA<PharmacyValidationException>()),
    );
    await expectLater(
      repository.adjustStock(drugId, const AdjustDrugStock(quantity: 0)),
      throwsA(isA<PharmacyValidationException>()),
    );
    await expectLater(
      repository.adjustStock('bad', const AdjustDrugStock(quantity: 1)),
      throwsArgumentError,
    );
    expect(adapter.requests, isEmpty);
    adapter.data = {...drugJson(), 'drugId': otherDrugId};
    await expectLater(
      repository.adjustStock(drugId, const AdjustDrugStock(quantity: 1)),
      throwsFormatException,
    );
  });
  for (final status in [400, 401, 403, 409, 422, 503]) {
    test(
      '$status create/adjust keeps errors and correlation, one request per command',
      () async {
        adapter.status = status;
        adapter.data = {
          'success': false,
          'error': {
            'code': 'STOCK_RESERVED',
            'message': 'Server denied',
            'details': [
              {'field': 'quantity', 'message': 'Reserved stock'},
            ],
          },
          'correlationId': 'stock-trace',
        };
        for (final action in [
          () => repository.createDrug(command()),
          () => repository.adjustStock(
            drugId,
            const AdjustDrugStock(quantity: -2),
          ),
        ]) {
          await expectLater(
            action(),
            throwsA(
              isA<ApiFailure>()
                  .having((e) => e.status, 'status', status)
                  .having((e) => e.code, 'code', 'STOCK_RESERVED')
                  .having((e) => e.correlationId, 'trace', 'stock-trace'),
            ),
          );
        }
        expect(adapter.requests, hasLength(2));
      },
    );
  }
  test(
    'network loss is returned once without automatic command retry',
    () async {
      adapter.networkFailure = true;
      await expectLater(
        repository.createDrug(command()),
        throwsA(isA<ApiFailure>()),
      );
      expect(adapter.requests, hasLength(1));
    },
  );
}
