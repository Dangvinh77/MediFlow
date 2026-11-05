import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mediflow_mobile/core/config/app_config.dart';
import 'package:mediflow_mobile/core/network/api_client.dart';
import 'package:mediflow_mobile/features/self_profile/data/gateway_self_profile_repository.dart';
import 'package:mediflow_mobile/features/self_profile/domain/self_profile.dart';

const staffId = '11111111-1111-4111-8111-111111111111';
const patientId = '33333333-3333-4333-8333-333333333333';
const departmentId = '44444444-4444-4444-8444-444444444444';

Map<String, dynamic> staffJson({
  String id = staffId,
  String phone = '0912345678',
  String address = 'Hanoi',
}) => {
  'staffId': id,
  'fullName': 'Nguyen Van An',
  'departmentId': departmentId,
  'jobTitle': 'DOCTOR',
  'specialization': 'Internal medicine',
  'licenseNumber': 'LIC-1',
  'phoneNumber': phone,
  'email': 'an@example.org',
  'active': true,
  'createdAt': '2020-01-02T03:04:05Z',
  'updatedAt': '2026-01-02T03:04:05Z',
  'identityNumber': '012345678901',
  'dateOfBirth': '1988-02-03',
  'gender': 'MALE',
  'address': address,
  'hireDate': '2020-01-02',
  'qualification': 'BSc',
};

Map<String, dynamic> patientJson() => {
  'maBenhNhan': patientId,
  'hoTen': 'Tran Thi Binh',
  'ngaySinh': '1990-07-08',
  'gioiTinh': 'F',
  'soCmnd': '123456789012',
  'diaChi': 'Hue',
  'soDienThoai': '0987654321',
  'email': 'binh@example.org',
  'bhytSo': '12-12345678-1',
  'createdAt': '2020-01-02T03:04:05Z',
  'updatedAt': '2026-01-02T03:04:05Z',
  'daLuuTru': false,
};

class SelfProfileHttpAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? responseOverride;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    Object response;
    if (responseOverride != null) {
      response = responseOverride!;
    } else if (options.method == 'PUT') {
      response = {
        ...staffJson(),
        'phoneNumber': options.data['phoneNumber'],
        'address': options.data['address'],
      };
    } else if (options.uri.path == '/api/v1/org/staff/$staffId/department') {
      response = {'departmentId': departmentId, 'departmentName': 'Khoa Nội'};
    } else if (options.uri.path == '/api/v1/org/staff/$staffId') {
      response = staffJson();
    } else {
      response = patientJson();
    }
    return ResponseBody.fromString(
      jsonEncode({'success': true, 'data': response}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late SelfProfileHttpAdapter adapter;
  late ApiClient client;
  late GatewaySelfProfileRepository repository;

  setUp(() {
    adapter = SelfProfileHttpAdapter();
    client = ApiClient(
      AppConfig('http://localhost:8080/api'),
      dio: Dio()..httpClientAdapter = adapter,
      token: () => 'self-profile-token',
    );
    repository = GatewaySelfProfileRepository(client);
  });
  tearDown(() => client.close());

  test('reads the full staff record through the own staff contract', () async {
    final profile = await repository.readStaffProfile(staffId);

    expect(profile.id, staffId);
    expect(profile.identityNumber, '012345678901');
    expect(profile.address, 'Hanoi');
    final request = adapter.requests.single;
    expect(request.method, 'GET');
    expect(request.uri.path, '/api/v1/org/staff/$staffId');
    expect(request.headers['Authorization'], 'Bearer self-profile-token');
  });

  test('reads the staff department from the signed-owner endpoint', () async {
    expect(await repository.readStaffDepartment(staffId), 'Khoa Nội');
    expect(adapter.requests.single.method, 'GET');
    expect(
      adapter.requests.single.uri.path,
      '/api/v1/org/staff/$staffId/department',
    );
  });

  test('updates only phone and address through own-contact endpoint', () async {
    final updated = await repository.updateOwnStaffContact(
      staffId,
      const StaffContactDraft(phoneNumber: '0912345678', address: 'Hanoi'),
    );

    expect(updated.phoneNumber, '0912345678');
    expect(updated.email, 'an@example.org');
    final request = adapter.requests.single;
    expect(request.method, 'PUT');
    expect(request.uri.path, '/api/v1/org/staff/$staffId/contact');
    expect(request.data, {'phoneNumber': '0912345678', 'address': 'Hanoi'});
    expect(request.data, isNot(contains('email')));
  });

  test(
    'reads the patient profile using the exact Vietnamese DTO fields',
    () async {
      final profile = await repository.readPatientProfile(patientId);

      expect(profile.id, patientId);
      expect(profile.fullName, 'Tran Thi Binh');
      expect(profile.insuranceNumber, '12-12345678-1');
      expect(adapter.requests.single.method, 'GET');
      expect(adapter.requests.single.uri.path, '/api/v1/patients/$patientId');
    },
  );

  test('rejects a returned profile with a different owner id', () async {
    adapter.responseOverride = staffJson(
      id: '22222222-2222-4222-8222-222222222222',
    );

    await expectLater(
      repository.readStaffProfile(staffId),
      throwsFormatException,
    );
  });

  test('does not send invalid owner ids to the Gateway', () async {
    await expectLater(
      repository.readPatientProfile('../patient'),
      throwsArgumentError,
    );
    expect(adapter.requests, isEmpty);
  });
}
