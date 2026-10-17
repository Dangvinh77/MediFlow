import '../../../core/domain/page_result.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/page_decoder.dart';
import '../domain/appointment.dart';
import 'appointment_dto.dart';

class GatewayAppointmentRepository implements AppointmentRepository {
  const GatewayAppointmentRepository(this.client);
  final ApiClient client;
  @override
  Future<PageResult<Appointment>> search({int page = 0, String? date}) async =>
      decodePage(
        await client.get(
          '/v1/appointments',
          query: {'page': page, 'size': 20, 'appointmentDate': ?date},
        ),
        AppointmentDto.fromJson,
      );
}
