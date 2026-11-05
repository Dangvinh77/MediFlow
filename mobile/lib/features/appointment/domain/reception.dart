import 'dart:math';

import '../../../core/domain/page_result.dart';

bool isReceptionId(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
).hasMatch(value);

String newReceptionKey() {
  final random = Random.secure();
  final bytes = List.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final s = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-${s.substring(16, 20)}-${s.substring(20)}';
}

class ReceptionSession {
  const ReceptionSession({
    required this.id,
    required this.revision,
    required this.departmentId,
    required this.doctorId,
    required this.roomId,
    required this.date,
    required this.startTime,
    required this.endTime,
    required this.capacity,
    required this.availableCount,
    required this.status,
  });
  final String id,
      departmentId,
      doctorId,
      roomId,
      date,
      startTime,
      endTime,
      status;
  final int revision, capacity, availableCount;
  bool get canRegister => status == 'OPEN' && availableCount > 0;
}

class ReceptionFollowUp {
  const ReceptionFollowUp({
    required this.registrationId,
    required this.patientId,
    required this.departmentId,
    required this.doctorId,
    required this.completedAt,
    this.recommendationId,
    this.recommendedFrom,
    this.recommendedThrough,
    this.reason,
  });
  final String registrationId, patientId, departmentId, doctorId, completedAt;
  final String? recommendationId, recommendedFrom, recommendedThrough, reason;
}

class ReceptionReceipt {
  const ReceptionReceipt({
    required this.id,
    required this.patientId,
    required this.sessionId,
    required this.doctorId,
    required this.status,
    required this.expiresAt,
    this.queueNumber,
  });
  final String id, patientId, sessionId, doctorId, status, expiresAt;
  final int? queueNumber;
}

class ReceptionCommand {
  const ReceptionCommand({
    required this.patientId,
    required this.session,
    this.prebooked = false,
    this.followUp = false,
    this.source,
    this.useRecommendation = true,
    this.reason = '',
    this.replacementReason = '',
    this.agreedToReplacement = false,
  });
  final String patientId, reason, replacementReason;
  final ReceptionSession session;
  final bool prebooked, followUp, useRecommendation, agreedToReplacement;
  final ReceptionFollowUp? source;
  String? validate() {
    if (!isReceptionId(patientId)) return 'Chọn bệnh nhân trước khi đăng ký.';
    if (!isReceptionId(session.id) ||
        session.revision < 1 ||
        !session.canRegister) {
      return 'Buổi khám không còn nhận đăng ký. Hãy tải lại.';
    }
    if (reason.length > 1000 || replacementReason.length > 1000) {
      return 'Lý do tối đa 1000 ký tự.';
    }
    if (!followUp) return null;
    final prior = source;
    if (prior == null ||
        !isReceptionId(prior.registrationId) ||
        prior.patientId != patientId ||
        prior.departmentId != session.departmentId) {
      return 'Chọn lượt khám đã hoàn tất của bệnh nhân trong cùng khoa.';
    }
    if ((!useRecommendation || prior.recommendationId == null) &&
        reason.trim().isEmpty) {
      return 'Nhập lý do đăng ký tái khám.';
    }
    if (prior.doctorId != session.doctorId &&
        (!agreedToReplacement || replacementReason.trim().isEmpty)) {
      return 'Cần lý do đổi bác sĩ và sự đồng ý của bệnh nhân.';
    }
    return null;
  }

  Map<String, Object?> toJson() => {
    'patientId': patientId,
    'sessionId': session.id,
    'expectedSessionRevision': session.revision,
    'receptionMode': prebooked ? 'PREBOOKED' : 'AT_COUNTER',
    'visitType': followUp ? 'FOLLOW_UP' : 'NEW_VISIT',
    'previousRegistrationId': followUp ? source?.registrationId : null,
    'followUpRecommendationId': followUp && useRecommendation
        ? source?.recommendationId
        : null,
    'replacementReason': followUp && source?.doctorId != session.doctorId
        ? replacementReason.trim()
        : null,
    'patientAgreedToDoctorReplacement':
        followUp && source?.doctorId != session.doctorId
        ? agreedToReplacement
        : null,
    'reason': reason.trim().isEmpty ? null : reason.trim(),
  };
}

class ReceptionIntent {
  ReceptionIntent(this.command, {String? key, String? correlationId})
    : key = key ?? newReceptionKey(),
      correlationId = correlationId ?? newReceptionKey() {
    final issue = command.validate();
    if (issue != null) throw ArgumentError(issue);
    if (!isReceptionId(this.key) || !isReceptionId(this.correlationId)) {
      throw ArgumentError('Mã yêu cầu không hợp lệ.');
    }
  }
  final ReceptionCommand command;
  final String key, correlationId;
}

abstract interface class ReceptionRepository {
  Future<PageResult<ReceptionSession>> sessions({
    required String departmentId,
    required String fromDate,
    required String toDate,
    int page = 0,
  });
  Future<PageResult<ReceptionFollowUp>> followUpSources(
    String patientId, {
    int page = 0,
  });
  Future<ReceptionReceipt> register(ReceptionIntent intent);
}
