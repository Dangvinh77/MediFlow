import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/lab_test.dart';
import 'lab_test_dto.dart';

class GatewayLabRepository implements LabRepository {
  const GatewayLabRepository(this.client);
  final ApiClient client;
  @override
  Future<PageResult<LabTest>> search({int page = 0, String? status}) async =>
      decodePage(
        await client.get(
          '/v1/lab',
          query: {'page': page, 'size': 20, 'status': ?status},
        ),
        LabTestDto.fromJson,
      );
}
