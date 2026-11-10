import '../../../core/network/api_client.dart';
import '../../../core/network/json_reader.dart';
import '../domain/open_visit.dart';

class GatewayOpenVisitRepository implements OpenVisitRepository {
  const GatewayOpenVisitRepository(this.client);
  final ApiClient client;

  @override
  Future<List<OpenVisit>> openVisits(String patientId) async => _visits(
    await client.get(
      '/v2/patients/${Uri.encodeComponent(patientId)}/open-visits',
    ),
  );

  @override
  Future<List<OpenVisit>> myOpenVisits() async =>
      _visits(await client.get('/v2/doctors/me/open-visits'));

  @override
  Future<List<ReturnSource>> returnSources(String patientId) async {
    final value = await client.get(
      '/v2/patients/${Uri.encodeComponent(patientId)}/return-sources',
    );
    return _maps(value)
        .where((item) => returnSourceLabels.containsKey(item['status']))
        .map(
          (item) => ReturnSource(
            recordId: JsonReader.string(item, 'recordId'),
            status: JsonReader.string(item, 'status'),
            examinationDate: JsonReader.string(item, 'examinationDate'),
          ),
        )
        .toList(growable: false);
  }

  List<OpenVisit> _visits(Object? value) => _maps(value)
      .map(
        (item) => OpenVisit(
          registrationId: JsonReader.string(item, 'registrationId'),
          recordId: JsonReader.string(item, 'recordId'),
          doctorId: JsonReader.string(item, 'doctorId'),
          departmentId: JsonReader.string(item, 'departmentId'),
          sessionDate: JsonReader.string(item, 'sessionDate'),
          startedAt: JsonReader.optionalString(item, 'startedAt'),
        ),
      )
      .toList(growable: false);

  Iterable<Map<String, dynamic>> _maps(Object? value) {
    if (value is! List) {
      throw const FormatException('Phản hồi danh sách không hợp lệ.');
    }
    return value.map((item) {
      if (item is! Map<String, dynamic>) {
        throw const FormatException('Dữ liệu danh sách không hợp lệ.');
      }
      return item;
    });
  }
}
