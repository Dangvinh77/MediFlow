import '../../../core/domain/follow_up_recommendation.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_failure.dart';
import '../domain/follow_up_recommendations.dart';

class GatewayFollowUpRecommendationRepository
    implements FollowUpRecommendationRepository {
  const GatewayFollowUpRecommendationRepository(this.client);
  final ApiClient client;

  @override
  Future<FollowUpRecommendation?> read(String recordId) async {
    try {
      return _parse(
        await client.get(
          '/v2/records/${Uri.encodeComponent(recordId)}/follow-up-recommendation',
        ),
      );
    } on ApiFailure catch (error) {
      if (error.status == 404 && error.code == 'RESOURCE_NOT_FOUND') {
        return null;
      }
      rethrow;
    }
  }

  @override
  Future<FollowUpRecommendation> create(
    String recordId,
    FollowUpDraft draft,
  ) async => _parse(
    await client.post(
      '/v2/records/${Uri.encodeComponent(recordId)}/follow-up-recommendations',
      {
        'recommendedFrom': draft.recommendedFrom,
        'recommendedThrough': draft.recommendedThrough,
        'reason': draft.reason,
        'preparationInstructions': draft.preparationInstructions,
      },
      idempotencyKey: draft.idempotencyKey,
      correlationId: draft.correlationId,
    ),
  );

  static FollowUpRecommendation _parse(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Phản hồi đề xuất tái khám không hợp lệ.');
    }
    return FollowUpRecommendation.fromJson(value);
  }
}
