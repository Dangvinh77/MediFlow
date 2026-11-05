/// A doctor's immutable follow-up advice, shared by record and registration views.
class FollowUpRecommendation {
  const FollowUpRecommendation({
    required this.id,
    required this.sourceRegistrationId,
    required this.preferredDoctorId,
    required this.recommendedFrom,
    required this.recommendedThrough,
    required this.reason,
    required this.createdAt,
    this.preparationInstructions,
  });

  final String id,
      sourceRegistrationId,
      preferredDoctorId,
      recommendedFrom,
      recommendedThrough,
      reason,
      createdAt;
  final String? preparationInstructions;

  factory FollowUpRecommendation.fromJson(Map<String, dynamic> json) {
    String text(String key) {
      final value = json[key];
      if (value is! String || value.isEmpty) {
        throw const FormatException('Dữ liệu đề xuất tái khám không hợp lệ.');
      }
      return value;
    }

    final instructions = json['preparationInstructions'];
    return FollowUpRecommendation(
      id: text('recommendationId'),
      sourceRegistrationId: text('sourceRegistrationId'),
      preferredDoctorId: text('preferredDoctorId'),
      recommendedFrom: text('recommendedFrom'),
      recommendedThrough: text('recommendedThrough'),
      reason: text('reason'),
      createdAt: text('createdAt'),
      preparationInstructions: instructions is String ? instructions : null,
    );
  }
}
