import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:mediflow_mobile/core/domain/page_result.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/pharmacy_repository.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/drug_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/domain/prescription_commands.dart';
import 'package:mediflow_mobile/features/pharmacy/data/pharmacy_dto.dart';

const drugId = '10000000-0000-4000-8000-000000000001';
const otherDrugId = '10000000-0000-4000-8000-000000000002';
const rxId = '20000000-0000-4000-8000-000000000001';

Map<String, dynamic> drugJson() => {
  'drugId': drugId,
  'drugName': 'Paracetamol 500 mg',
  'activeIngredient': null,
  'unit': 'Viên',
  'price': 2500.25,
  'stockQuantity': 6,
  'expiryDate': '2027-06-30',
  'manufacturer': null,
  'lowStockThreshold': 10,
  'createdAt': '2026-10-20T00:00:00Z',
  'updatedAt': '2026-10-20T01:00:00Z',
};
Map<String, dynamic> prescriptionJson() => {
  'prescriptionId': rxId,
  'recordId': null,
  'patientId': drugId,
  'doctorId': otherDrugId,
  'departmentId': drugId,
  'prescribedDate': '2026-10-20',
  'totalAmount': 5000.50,
  'lines': [
    {
      'lineId': otherDrugId,
      'drugId': drugId,
      'drugName': null,
      'quantity': 2,
      'unitPrice': 2500.25,
      'dosage': null,
      'lineTotal': 5000.50,
    },
  ],
  'status': 'ACTIVE',
  'dispenseStatus': 'PENDING',
  'paymentConfirmed': false,
  'cancelledAt': null,
  'cancelledBy': null,
  'cancellationReason': null,
  'createdAt': '2026-10-20T00:00:00Z',
  'updatedAt': '2026-10-20T01:00:00Z',
};
Map<String, dynamic> pageJson() => {
  'content': [drugJson()],
  'totalElements': 21,
  'totalPages': 2,
  'number': 0,
  'size': 20,
};

Drug drug({
  String id = drugId,
  String name = 'Paracetamol 500 mg',
  int stock = 6,
}) => Drug(
  id: id,
  name: name,
  activeIngredient: null,
  unit: 'Viên',
  price: 2500.25,
  stockQuantity: stock,
  expiryDate: '2027-06-30',
  manufacturer: null,
  lowStockThreshold: 10,
  createdAt: DateTime.utc(2026, 10, 20),
  updatedAt: DateTime.utc(2026, 10, 20),
);
PageResult<Drug> drugsPage({int page = 0, List<Drug>? items}) => PageResult(
  items: items ?? [drug()],
  number: page,
  totalPages: 2,
  totalElements: 21,
);

class PharmacyStub implements PharmacyRepository {
  final cancellations = <(String, CancelPrescription)>[];
  Future<CancelPrescriptionResult> Function(String, CancelPrescription)? cancel;
  @override
  Future<CancelPrescriptionResult> cancelPrescription(
    String id,
    CancelPrescription command,
  ) {
    cancellations.add((id, command));
    return cancel?.call(id, command) ??
        Future.value(
          CancelPrescriptionResult(
            prescriptionId: id,
            status: PrescriptionStatus.cancelled,
            releasedReservations: 1,
            cancelledAt: DateTime.utc(2026, 10, 22),
          ),
        );
  }

  final searches = <({String? keyword, int page, int size})>[];
  final details = <String>[];
  Future<PageResult<Drug>> Function(String?, int, int)? search;
  Future<Drug> Function(String)? detail;
  Future<Prescription> Function(String)? prescription;
  Future<Drug> Function(CreateDrug)? create;
  Future<Drug> Function(String, AdjustDrugStock)? adjust;
  final prescriptions = <String>[];
  final creations = <CreateDrug>[];
  final adjustments = <(String, AdjustDrugStock)>[];
  @override
  Future<Drug> createDrug(CreateDrug command) {
    creations.add(command);
    return create?.call(command) ?? Future.value(drug());
  }

  @override
  Future<Drug> adjustStock(String id, AdjustDrugStock command) {
    adjustments.add((id, command));
    return adjust?.call(id, command) ?? Future.value(drug(id: id, stock: 11));
  }

  @override
  Future<PageResult<Drug>> searchDrugs({
    String? keyword,
    int page = 0,
    int size = 20,
  }) {
    searches.add((keyword: keyword, page: page, size: size));
    return search?.call(keyword, page, size) ??
        Future.value(drugsPage(page: page));
  }

  @override
  Future<Drug> getDrug(String id) {
    details.add(id);
    return detail?.call(id) ?? Future.value(drug(id: id));
  }

  @override
  Future<Prescription> getPrescription(String id) {
    prescriptions.add(id);
    return prescription?.call(id) ??
        Future.value(
          PharmacyDto.prescription({
            ...prescriptionJson(),
            'prescriptionId': id,
          }),
        );
  }
}

class PharmacyHttpAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? data = pageJson();
  int status = 200;
  bool networkFailure = false;
  bool wrapSuccess = true;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (networkFailure) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'Disconnected',
      );
    }
    return ResponseBody.fromString(
      jsonEncode(
        status >= 400 || !wrapSuccess
            ? data
            : {
                'success': true,
                'data': data,
                'error': null,
                'correlationId': 'trace-read',
              },
      ),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
