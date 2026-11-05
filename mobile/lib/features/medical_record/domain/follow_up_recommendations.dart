import 'dart:math';

import '../../../core/domain/follow_up_recommendation.dart';

String _newKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

class FollowUpDraft {
  FollowUpDraft({
    required this.recommendedFrom,
    required this.recommendedThrough,
    required this.reason,
    this.preparationInstructions,
  });

  final String recommendedFrom, recommendedThrough, reason;
  final String? preparationInstructions;

  /// Fixed per draft, so an unresolved command replays with the same key.
  final String idempotencyKey = _newKey(), correlationId = _newKey();

  String? validate() {
    final date = RegExp(r'^\d{4}-\d{2}-\d{2}$');
    if (!date.hasMatch(recommendedFrom) || !date.hasMatch(recommendedThrough)) {
      return 'Chọn ngày bắt đầu và ngày kết thúc khuyến nghị.';
    }
    if (recommendedThrough.compareTo(recommendedFrom) < 0) {
      return 'Ngày kết thúc không được trước ngày bắt đầu.';
    }
    if (reason.isEmpty) return 'Nhập lý do tái khám.';
    if (reason.length > 1000 || (preparationInstructions?.length ?? 0) > 2000) {
      return 'Lý do tối đa 1000 ký tự, hướng dẫn chuẩn bị tối đa 2000 ký tự.';
    }
    return null;
  }
}

abstract interface class FollowUpRecommendationRepository {
  /// Null when the record has no recommendation yet.
  Future<FollowUpRecommendation?> read(String recordId);
  Future<FollowUpRecommendation> create(String recordId, FollowUpDraft draft);
}
