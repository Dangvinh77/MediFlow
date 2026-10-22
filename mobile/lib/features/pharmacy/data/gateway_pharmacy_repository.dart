import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/prescription.dart';
import '../domain/drug_commands.dart';
import '../domain/prescription_commands.dart';
import 'pharmacy_dto.dart';
import 'prescription_request_dto.dart';

class GatewayPharmacyRepository
    implements
        PharmacyRepository,
        OutpatientPrescriptionWriter,
        PrescriptionDispenser {
  const GatewayPharmacyRepository(this.client);
  final ApiClient client;
  @override
  Future<Prescription> createPrescription(PrescriptionIntent intent) async {
    final result = PharmacyDto.prescription(
      await client.post(
        '/v1/pharmacy/prescriptions',
        PrescriptionRequestDto.outpatient(intent),
        idempotencyKey: intent.key,
      ),
    );
    final context = intent.command.context;
    if (result.recordId?.toLowerCase() != context.recordId.toLowerCase() ||
        result.patientId.toLowerCase() != context.patientId.toLowerCase() ||
        result.doctorId.toLowerCase() != context.doctorId.toLowerCase() ||
        result.departmentId.toLowerCase() !=
            context.departmentId.toLowerCase()) {
      throw const FormatException('Đơn trả về không khớp nguồn kê đơn.');
    }
    return result;
  }

  @override
  Future<DispenseResult> dispensePrescription(String id) async {
    if (!isPharmacyId(id)) throw ArgumentError('Mã đơn thuốc không hợp lệ.');
    return PharmacyDto.dispense(
      await client.put('/v1/pharmacy/prescriptions/$id/dispense'),
    );
  }

  @override
  Future<CancelPrescriptionResult> cancelPrescription(
    String id,
    CancelPrescription command,
  ) async {
    if (!isPharmacyId(id)) throw ArgumentError('Mã đơn thuốc không hợp lệ.');
    command.validate();
    final result = PharmacyDto.cancellation(
      await client.put('/v1/pharmacy/prescriptions/$id/cancel', {
        'reason': command.reason,
      }),
    );
    if (result.prescriptionId.toLowerCase() != id.toLowerCase() ||
        result.status != PrescriptionStatus.cancelled) {
      throw const FormatException('Kết quả hủy đơn không khớp.');
    }
    return result;
  }

  @override
  Future<Drug> createDrug(CreateDrug command) async {
    command.validate();
    return PharmacyDto.drug(
      await client.post('/v1/pharmacy/drugs', {
        'drugName': command.drugName,
        'activeIngredient': command.activeIngredient,
        'unit': command.unit,
        'price': num.parse(command.price),
        'stockQuantity': command.stockQuantity,
        'expiryDate': command.expiryDate,
        'manufacturer': command.manufacturer,
        'lowStockThreshold': command.lowStockThreshold,
      }),
    );
  }

  @override
  Future<Drug> adjustStock(String id, AdjustDrugStock command) async {
    if (!isPharmacyId(id)) throw ArgumentError('Mã thuốc không hợp lệ.');
    command.validate();
    final drug = PharmacyDto.drug(
      await client.put('/v1/pharmacy/drugs/$id/stock', {
        'quantity': command.quantity,
        'reason': command.reason,
      }),
    );
    if (drug.id.toLowerCase() != id.toLowerCase()) {
      throw const FormatException('Mã thuốc trả về không khớp.');
    }
    return drug;
  }

  @override
  Future<PageResult<Drug>> searchDrugs({
    String? keyword,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Trang không hợp lệ.');
    }
    final result = await client.get(
      '/v1/pharmacy/drugs',
      query: {
        'page': page,
        'size': size,
        if (keyword != null && keyword.trim().isNotEmpty)
          'keyword': keyword.trim(),
      },
    );
    final json = PharmacyDto.object(result);
    // Pharmacy PageResult includes size even though the shared domain page omits it.
    PharmacyDto.integer(json, 'size', minimum: 1);
    return decodePage(json, PharmacyDto.drug);
  }

  @override
  Future<Drug> getDrug(String id) async {
    if (!isPharmacyId(id)) throw ArgumentError('Mã thuốc không hợp lệ.');
    final drug = PharmacyDto.drug(await client.get('/v1/pharmacy/drugs/$id'));
    if (drug.id.toLowerCase() != id.toLowerCase()) {
      throw const FormatException('Mã thuốc trả về không khớp.');
    }
    return drug;
  }

  @override
  Future<Prescription> getPrescription(String id) async {
    if (!isPharmacyId(id)) throw ArgumentError('Mã đơn thuốc không hợp lệ.');
    final rx = PharmacyDto.prescription(
      await client.get('/v1/pharmacy/prescriptions/$id'),
    );
    if (rx.id.toLowerCase() != id.toLowerCase()) {
      throw const FormatException('Mã đơn trả về không khớp.');
    }
    return rx;
  }
}
