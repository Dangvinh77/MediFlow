import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/drug.dart';
import '../domain/pharmacy_repository.dart';
import '../domain/prescription.dart';
import 'pharmacy_dto.dart';

class GatewayPharmacyRepository implements PharmacyRepository {
  const GatewayPharmacyRepository(this.client);
  final ApiClient client;
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
